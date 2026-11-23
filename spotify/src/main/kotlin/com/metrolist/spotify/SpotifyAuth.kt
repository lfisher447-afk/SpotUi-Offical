package com.metrolist.spotify

import com.metrolist.spotify.models.SpotifyInternalToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.floor

/**
 * Handles Spotify authentication using the web player's internal token endpoint.
 * Uses sp_dc cookies (extracted from WebView login) to obtain access tokens
 * without requiring a Spotify Developer Client ID.
 *
 * Token acquisition requires a TOTP (Time-based One-Time Password) generated
 * from a shared secret that Spotify rotates periodically. The secret and its
 * version are fetched from a community-maintained GitHub Gist.
 *
 * Reference: https://github.com/sonic-liberation/spotube-plugin-spotify
 */
object SpotifyAuth {
    private const val TOKEN_URL = "https://open.spotify.com/api/token"
    private const val LEGACY_TOKEN_URL = "https://open.spotify.com/get_access_token"
    private const val SERVER_TIME_URL = "https://open.spotify.com/api/server-time"
    private const val NUANCE_GIST_ID = "22ed9c6ba463899e933427f7de1f0eef"
    private const val NUANCE_GIST_URL =
        "https://api.github.com/gists/$NUANCE_GIST_ID"
    private const val NUANCE_GIST_RAW_FALLBACK =
        "https://gist.githubusercontent.com/raw/$NUANCE_GIST_ID"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36"

    // Standard Spotify login entry avoids deprecated `&method=password&allow_password=1`
    // parameters that frequently trigger HTTP 400/402 challenges in embedded WebViews.
    const val LOGIN_URL =
        "https://accounts.spotify.com/en/login?continue=https%3A%2F%2Fopen.spotify.com%2F"

    // Direct entry to Spotify's web signup flow.
    const val SIGNUP_URL = "https://www.spotify.com/signup"

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    @Volatile
    private var cachedNuance: Nuance? = null

    @Volatile
    private var cachedNuanceAtMs: Long = 0L

    private const val NUANCE_CACHE_TTL_MS = 30 * 60 * 1000L

    @Serializable
    private data class Nuance(val s: String, val v: Int)

    @Serializable
    private data class GistFile(val content: String)

    @Serializable
    private data class GistFiles(val files: Map<String, GistFile>)

    @Serializable
    private data class ServerTimeResponse(val serverTime: Long)

    /**
     * Fetches an internal web-player access token using session cookies and TOTP,
     * with cached/mirrored TOTP secrets, multi-step reason fallback, and WebPlayer headers.
     */
    suspend fun fetchAccessToken(
        spDc: String,
        spKey: String = "",
    ): Result<SpotifyInternalToken> = runCatching {
        val cleanDc = spDc.trim()
        val cleanKey = spKey.trim()
        if (cleanDc.isBlank()) {
            throw Spotify.SpotifyException(401, "Missing sp_dc session cookie")
        }

        val cookieHeader = buildString {
            append("sp_dc=$cleanDc")
            if (cleanKey.isNotEmpty()) {
                append("; sp_key=$cleanKey")
            }
        }

        val webPlayerHeaders = mapOf(
            "Cookie" to cookieHeader,
            "Origin" to "https://open.spotify.com",
            "Referer" to "https://open.spotify.com/",
            "app-platform" to "WebPlayer",
            "spotify-app-version" to "1.2.62.580.g5b2f8f82",
        )

        val nuanceResult = runCatching { fetchNuance() }
        val serverTimeSec = runCatching { fetchServerTime() }.getOrElse { System.currentTimeMillis() / 1000L }

        var lastError: Throwable? = null
        val nuance = nuanceResult.getOrNull()
        if (nuance != null) {
            val reasons = listOf("transport", "init")
            val timeOffsets = listOf(0L, 30L)
            for (offset in timeOffsets) {
                val totp = generateTotp(nuance.s, serverTimeSec + offset)
                for (reason in reasons) {
                    val tokenUrl = buildString {
                        append(TOKEN_URL)
                        append("?reason=$reason")
                        append("&productType=web-player")
                        append("&totp=$totp")
                        append("&totpServer=$totp")
                        append("&totpVer=${nuance.v}")
                    }
                    val attempt = runCatching {
                        val body = withContext(Dispatchers.IO) { httpGet(tokenUrl, webPlayerHeaders) }
                        val parsed = json.decodeFromString<SpotifyInternalToken>(body)
                        if (parsed.isAnonymous || parsed.accessToken.isBlank()) {
                            throw Spotify.SpotifyException(
                                401,
                                "Received anonymous token — sp_dc cookie is invalid or expired",
                            )
                        }
                        parsed
                    }
                    attempt.onSuccess { return@runCatching it }
                    lastError = attempt.exceptionOrNull()
                    // Don't burn retries on hard 401 expired cookie; try legacy fallback once instead.
                    if ((lastError as? Spotify.SpotifyException)?.statusCode == 401) break
                }
            }
        } else {
            lastError = nuanceResult.exceptionOrNull()
        }

        // Secondary fallback: legacy get_access_token endpoint
        for (reason in listOf("transport", "init")) {
            val legacyUrl = "$LEGACY_TOKEN_URL?reason=$reason&productType=web-player"
            val attempt = runCatching {
                val body = withContext(Dispatchers.IO) { httpGet(legacyUrl, webPlayerHeaders) }
                val parsed = json.decodeFromString<SpotifyInternalToken>(body)
                if (parsed.isAnonymous || parsed.accessToken.isBlank()) {
                    throw Spotify.SpotifyException(
                        401,
                        "Received anonymous token — sp_dc cookie is invalid or expired",
                    )
                }
                parsed
            }
            attempt.onSuccess { return@runCatching it }
            if (lastError == null) lastError = attempt.exceptionOrNull()
        }

        throw lastError ?: Spotify.SpotifyException(503, "Unable to mint Spotify web token")
    }

    private suspend fun fetchNuance(): Nuance = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = cachedNuance
        if (cached != null && (now - cachedNuanceAtMs) in 0 until NUANCE_CACHE_TTL_MS) {
            return@withContext cached
        }

        // 1. Primary GitHub Gist API
        val apiParsed = runCatching {
            val body = httpGet(NUANCE_GIST_URL, emptyMap())
            val gist = json.decodeFromString<GistFiles>(body)
            val nuancesJson = gist.files.values.firstOrNull()?.content
                ?: error("Gist has no files")
            json.decodeFromString<List<Nuance>>(nuancesJson).maxByOrNull { it.v }
                ?: error("Empty nuance list")
        }.getOrNull()

        if (apiParsed != null) {
            cachedNuance = apiParsed
            cachedNuanceAtMs = now
            return@withContext apiParsed
        }

        // 2. Fallback: GitHub raw gist endpoint (avoids api.github.com 60 req/hr unauthenticated limit)
        val rawParsed = runCatching {
            val rawBody = httpGet(NUANCE_GIST_RAW_FALLBACK, emptyMap())
            json.decodeFromString<List<Nuance>>(rawBody).maxByOrNull { it.v }
                ?: error("Empty raw nuance list")
        }.getOrNull()

        if (rawParsed != null) {
            cachedNuance = rawParsed
            cachedNuanceAtMs = now
            return@withContext rawParsed
        }

        if (cached != null) return@withContext cached

        throw Spotify.SpotifyException(
            503,
            "Failed to fetch Spotify TOTP config from GitHub Gist mirror",
        )
    }

    private suspend fun fetchServerTime(): Long = withContext(Dispatchers.IO) {
        val body = try {
            httpGet(
                SERVER_TIME_URL,
                mapOf(
                    "Origin" to "https://open.spotify.com",
                    "Referer" to "https://open.spotify.com/",
                ),
            )
        } catch (e: Exception) {
            throw Spotify.SpotifyException(
                503,
                "Failed to fetch Spotify server time: ${e.message}",
            )
        }
        val response = json.decodeFromString<ServerTimeResponse>(body)
        response.serverTime
    }

    /**
     * Generates a 6-digit TOTP using HMAC-SHA1 (RFC 6238).
     * @param secret Base32-encoded shared secret
     * @param serverTimeSec Spotify server time in seconds since epoch
     */
    private fun generateTotp(secret: String, serverTimeSec: Long): String {
        val key = base32Decode(secret)
        val interval = 30L
        val timeStep = floor(serverTimeSec.toDouble() / interval).toLong()

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

        val otp = code % 1_000_000
        return otp.toString().padStart(6, '0')
    }

    private fun base32Decode(input: String): ByteArray {
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

    private val authHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(8, 3, TimeUnit.MINUTES))
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    private fun httpGet(urlString: String, extraHeaders: Map<String, String>): String {
        val reqBuilder = Request.Builder()
            .url(urlString)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "en-US,en;q=0.9")
        for ((key, value) in extraHeaders) {
            reqBuilder.header(key, value)
        }
        authHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw Spotify.SpotifyException(
                    resp.code,
                    "HTTP ${resp.code}: ${bodyStr.take(200)}",
                )
            }
            return bodyStr
        }
    }
}
