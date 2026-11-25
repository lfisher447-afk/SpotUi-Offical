package com.metrolist.spotify

import com.metrolist.spotify.models.SpotifyInternalToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Canonical Spotify Web-Player authentication engine inside `:spotify`.
 *
 * Unifies all cookie-to-token exchange strategies into one thread-safe pipeline:
 * 1. Direct `open.spotify.com` HTML `<script id="session">` extraction (no TOTP needed).
 * 2. TOTP (`spot-secrets` / Nuance Gist + `open.spotify.com/api/server-time`) -> `/api/token`
 *    with Mutex-guarded secret leasing, rotation recovery (400/401/403), and `?reason=init` fallback.
 * 3. Legacy `/get_access_token` compatibility fallback.
 */
object SpotifyAuth {
    private const val OPEN_URL = "https://open.spotify.com/"
    private const val TOKEN_URL = "https://open.spotify.com/api/token"
    private const val LEGACY_TOKEN_URL = "https://open.spotify.com/get_access_token"
    private const val SERVER_TIME_URL = "https://open.spotify.com/api/server-time"
    private const val NUANCE_GIST_URL =
        "https://api.github.com/gists/22ed9c6ba463899e933427f7de1f0eef"
    private const val NUANCE_RAW_FALLBACK_URL =
        "https://gist.githubusercontent.com/22ed9c6ba463899e933427f7de1f0eef/raw"

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36"

    // method=password&allow_password=1 forces Spotify's email+password form to be
    // the default (instead of the passwordless "magic link" flow); allow_signup=1
    // keeps the "Sign up" link so the full web signup works from the same page.
    const val LOGIN_URL =
        "https://accounts.spotify.com/en/login?continue=https%3A%2F%2Fopen.spotify.com%2F" +
            "&method=password&allow_password=1&allow_signup=1"

    const val SIGNUP_URL = "https://www.spotify.com/signup"

    private const val HTTP_TIMEOUT_MS = 15_000
    private const val TOTP_INTERVAL_SEC = 30L
    private const val TOTP_DIGITS_MODULUS = 1_000_000
    private const val NUANCE_TTL_MS = 6 * 60 * 60 * 1000L
    private const val NUANCE_MIN_REFRESH_INTERVAL_MS = 60_000L
    private val SECRET_ROTATION_STATUS_CODES = setOf(400, 401, 403)

    private val SESSION_SCRIPT_REGEX = Regex(
        """<script\s+id=["']session["'][^>]*>(.*?)</script>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    @Serializable
    private data class Nuance(val s: String, val v: Int)

    @Serializable
    private data class GistFile(val content: String)

    @Serializable
    private data class GistFiles(val files: Map<String, GistFile>)

    @Serializable
    private data class ServerTimeResponse(val serverTime: Long)

    private class NuanceLease(val nuance: Nuance, val wasCached: Boolean)

    private val nuanceLock = Mutex()

    @Volatile
    private var nuanceCache: Nuance? = null

    @Volatile
    private var nuanceFetchedAtMs = 0L

    /**
     * Exchanges `sp_dc` (+ optional `sp_key`) for a non-anonymous web-player access token.
     * Rethrows [CancellationException] so calling scopes cancel deterministically.
     */
    suspend fun fetchAccessToken(
        spDc: String,
        spKey: String = "",
    ): Result<SpotifyInternalToken> {
        return try {
            val cookie = sanitizeCookieValue(spDc)
            if (cookie.isEmpty()) {
                throw Spotify.SpotifyException(401, "No sp_dc cookie provided")
            }
            val key = sanitizeCookieValue(spKey)
            val cookieHeader = buildCookieHeader(cookie, key)

            // Strategy 1: Direct open.spotify.com #session extraction (immune to TOTP secret rotations)
            val htmlSessionToken = runCatching { fetchTokenFromWebHtml(cookieHeader) }.getOrNull()
            if (htmlSessionToken != null && !htmlSessionToken.isAnonymous && htmlSessionToken.accessToken.isNotBlank()) {
                return Result.success(htmlSessionToken)
            }

            // Strategy 2: TOTP-authenticated /api/token with single-flight nuance lease + rotation retry
            var primaryError: Throwable? = null
            val totpToken = try {
                val lease = leaseNuance(forceRefresh = false)
                try {
                    requestTotpToken(cookieHeader, lease.nuance, reason = "transport")
                } catch (e: Spotify.SpotifyException) {
                    val rotated = lease.wasCached && e.statusCode in SECRET_ROTATION_STATUS_CODES
                    if (rotated) {
                        requestTotpToken(cookieHeader, leaseNuance(forceRefresh = true).nuance, reason = "transport")
                    } else if (e.statusCode == 400) {
                        requestTotpToken(cookieHeader, lease.nuance, reason = "init")
                    } else {
                        throw e
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                primaryError = e
                null
            }
            if (totpToken != null) {
                return Result.success(totpToken)
            }

            // Strategy 3: Legacy /get_access_token fallback before surfacing failure
            val legacyToken = runCatching { requestLegacyToken(cookieHeader) }.getOrNull()
            if (legacyToken != null && !legacyToken.isAnonymous && legacyToken.accessToken.isNotBlank()) {
                return Result.success(legacyToken)
            }

            throw primaryError ?: Spotify.SpotifyException(401, "Failed to verify Spotify sp_dc session")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isRetryable(error: Throwable?): Boolean = when (error) {
        null -> false
        is Spotify.SpotifyException ->
            error.statusCode == 408 || error.statusCode == 425 || error.statusCode in 500..599
        is IOException -> true
        else -> false
    }

    fun describeFailure(error: Throwable?): String = when (error) {
        null -> "Unknown error"
        is Spotify.SpotifyException -> when (error.statusCode) {
            401, 403 -> "Spotify rejected the sp_dc cookie — it expired or you signed out. Log in again."
            429 -> "Spotify is rate limiting this device. Wait a minute, then retry."
            503 -> "A required Spotify helper service is unreachable (${error.message})."
            else -> error.message
        }
        is UnknownHostException -> "No internet connection."
        is SocketTimeoutException -> "The connection timed out."
        else -> error.message ?: error.javaClass.simpleName
    }

    fun parseSessionScriptJson(jsonStr: String): SpotifyInternalToken? {
        return runCatching {
            val cleaned = jsonStr.trim().removeSurrounding("\"")
                .replace("\\\"", "\"")
            val token = json.decodeFromString<SpotifyInternalToken>(cleaned)
            token.takeIf { it.accessToken.isNotBlank() && !it.isAnonymous }
        }.getOrNull()
    }

    private suspend fun fetchTokenFromWebHtml(cookieHeader: String): SpotifyInternalToken? {
        val html = withContext(Dispatchers.IO) {
            httpGet(
                urlString = OPEN_URL,
                extraHeaders = mapOf(
                    "Cookie" to cookieHeader,
                    "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                ),
            )
        }
        val scriptBody = SESSION_SCRIPT_REGEX.find(html)?.groupValues?.getOrNull(1)?.trim()
            ?: return null
        return parseSessionScriptJson(scriptBody)
    }

    private suspend fun requestTotpToken(
        cookieHeader: String,
        nuance: Nuance,
        reason: String,
    ): SpotifyInternalToken {
        val totp = generateTotp(nuance.s, fetchServerTime())
        val tokenUrl = buildString {
            append(TOKEN_URL)
            append("?reason=$reason")
            append("&productType=web-player")
            append("&totp=$totp")
            append("&totpServer=$totp")
            append("&totpVer=${nuance.v}")
        }
        val body = withContext(Dispatchers.IO) {
            httpGet(
                urlString = tokenUrl,
                extraHeaders = mapOf(
                    "Cookie" to cookieHeader,
                    "Origin" to "https://open.spotify.com",
                    "Referer" to "https://open.spotify.com/",
                ),
            )
        }
        val token = json.decodeFromString<SpotifyInternalToken>(body)
        if (token.isAnonymous || token.accessToken.isBlank()) {
            throw Spotify.SpotifyException(
                401,
                "Received anonymous token — sp_dc cookie is invalid or expired",
            )
        }
        return token
    }

    private suspend fun requestLegacyToken(cookieHeader: String): SpotifyInternalToken? {
        val url = "$LEGACY_TOKEN_URL?reason=transport&productType=web-player"
        val body = withContext(Dispatchers.IO) {
            httpGet(
                urlString = url,
                extraHeaders = mapOf(
                    "Cookie" to cookieHeader,
                    "Origin" to "https://open.spotify.com",
                    "Referer" to "https://open.spotify.com/",
                    "app-platform" to "WebPlayer",
                ),
            )
        }
        return json.decodeFromString<SpotifyInternalToken>(body)
    }

    private suspend fun leaseNuance(forceRefresh: Boolean): NuanceLease = nuanceLock.withLock {
        val now = System.currentTimeMillis()
        val cached = nuanceCache
        val cacheIsFresh = now - nuanceFetchedAtMs < NUANCE_TTL_MS
        val refreshAllowed = now - nuanceFetchedAtMs >= NUANCE_MIN_REFRESH_INTERVAL_MS
        val reuseCache = if (forceRefresh) !refreshAllowed else cacheIsFresh
        if (cached != null && reuseCache) return@withLock NuanceLease(cached, wasCached = true)

        try {
            val fetched = fetchNuance()
            nuanceCache = fetched
            nuanceFetchedAtMs = System.currentTimeMillis()
            NuanceLease(fetched, wasCached = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (cached != null) NuanceLease(cached, wasCached = true) else throw e
        }
    }

    private suspend fun fetchNuance(): Nuance = withContext(Dispatchers.IO) {
        val gistError: Throwable?
        try {
            val body = httpGet(NUANCE_GIST_URL, emptyMap())
            val gist = json.decodeFromString<GistFiles>(body)
            val nuancesJson = gist.files.values.firstOrNull()?.content
                ?: throw Spotify.SpotifyException(500, "Gist has no files")
            val nuances = json.decodeFromString<List<Nuance>>(nuancesJson)
            return@withContext nuances.maxByOrNull { it.v }
                ?: throw Spotify.SpotifyException(500, "No nuance data found in gist")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            gistError = e
        }

        // Fallback to raw gist content endpoint if GitHub API rate-limits unauthenticated callers
        try {
            val rawBody = httpGet(NUANCE_RAW_FALLBACK_URL, emptyMap())
            val nuances = json.decodeFromString<List<Nuance>>(rawBody)
            nuances.maxByOrNull { it.v }
                ?: throw Spotify.SpotifyException(500, "No nuance data found in raw mirror")
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            throw Spotify.SpotifyException(
                503,
                "Failed to fetch TOTP secret: ${gistError?.message ?: "unavailable"}",
            )
        }
    }

    private suspend fun fetchServerTime(): Long = withContext(Dispatchers.IO) {
        val body = try {
            httpGet(SERVER_TIME_URL, emptyMap())
        } catch (e: Exception) {
            throw Spotify.SpotifyException(
                503,
                "Failed to fetch Spotify server time: ${e.message}",
            )
        }
        json.decodeFromString<ServerTimeResponse>(body).serverTime
    }

    internal fun generateTotp(secret: String, serverTimeSec: Long): String {
        val key = base32Decode(secret)
        val timeStep = serverTimeSec / TOTP_INTERVAL_SEC

        val timeBytes = ByteArray(8)
        var value = timeStep
        for (i in 7 downTo 0) {
            timeBytes[i] = (value and 0xFF).toByte()
            value = value shr 8
        }

        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val hash = mac.doFinal(timeBytes)

        val offset = hash[hash.size - 1].toInt() and 0x0F
        val code = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)

        return (code % TOTP_DIGITS_MODULUS).toString().padStart(6, '0')
    }

    internal fun base32Decode(input: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val cleaned = input.uppercase().replace("=", "")

        val output = mutableListOf<Byte>()
        var buffer = 0
        var bitsLeft = 0

        for (c in cleaned) {
            val value = alphabet.indexOf(c)
            if (value < 0) continue
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                output.add(((buffer shr bitsLeft) and 0xFF).toByte())
            }
        }
        return output.toByteArray()
    }

    private fun sanitizeCookieValue(value: String): String =
        value.filterNot { it.isWhitespace() || it.isISOControl() || it == ';' }

    private fun buildCookieHeader(spDc: String, spKey: String): String = buildString {
        append("sp_dc=$spDc")
        if (spKey.isNotEmpty()) append("; sp_key=$spKey")
    }

    private fun httpGet(urlString: String, extraHeaders: Map<String, String>): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            connection.connectTimeout = HTTP_TIMEOUT_MS
            connection.readTimeout = HTTP_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json, text/plain, */*")
            connection.setRequestProperty("Accept-Language", "en")
            for ((key, value) in extraHeaders) {
                connection.setRequestProperty(key, value)
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                throw Spotify.SpotifyException(
                    responseCode,
                    "HTTP $responseCode: ${errorBody.take(300)}",
                )
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
