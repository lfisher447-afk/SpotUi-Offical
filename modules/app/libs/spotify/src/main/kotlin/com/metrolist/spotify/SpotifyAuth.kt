package com.metrolist.spotify

import com.metrolist.spotify.models.SpotifyInternalToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
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
    private const val SERVER_TIME_URL = "https://open.spotify.com/api/server-time"
    private const val NUANCE_GIST_URL =
        "https://api.github.com/gists/22ed9c6ba463899e933427f7de1f0eef"
    private const val NUANCE_RAW_FALLBACK_URL =
        "https://gist.githubusercontent.com/22ed9c6ba463899e933427f7de1f0eef/raw"
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    // Clean modern Spotify login supports Password, 6-digit Email Code, Passkey, and Google Sign-In
    // without forcing legacy ?method=password anti-bot heuristics.
    const val LOGIN_URL =
        "https://accounts.spotify.com/en/login?continue=https%3A%2F%2Fopen.spotify.com%2F"

    // Explicit password-prompt entry when preferred.
    const val LOGIN_PASSWORD_URL =
        "https://accounts.spotify.com/en/login?continue=https%3A%2F%2Fopen.spotify.com%2F" +
            "&method=password&allow_password=1&allow_signup=1"

    // Direct entry to Spotify's web signup flow.
    const val SIGNUP_URL = "https://www.spotify.com/signup"

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    @Volatile
    private var cachedNuance: Nuance? = null

    @Serializable
    private data class Nuance(val s: String, val v: Int)

    @Serializable
    private data class GistFile(val content: String)

    @Serializable
    private data class GistFiles(val files: Map<String, GistFile>)

    @Serializable
    private data class ServerTimeResponse(val serverTime: Long)

    /**
     * Fetches an internal web-player access token using session cookies and TOTP.
     *
     * 1. Fetches the TOTP secret from cache / community Gist / raw fallback
     * 2. Gets the server time from Spotify (falling back to device UTC if unreachable)
     * 3. Generates a 6-digit TOTP (SHA1, 30s interval)
     * 4. Calls /api/token with the TOTP and sp_dc cookie
     */
    suspend fun fetchAccessToken(
        spDc: String,
        spKey: String = "",
    ): Result<SpotifyInternalToken> = runCatching {
        val cleanedSpDc = spDc.trim()
        if (cleanedSpDc.isBlank()) {
            throw Spotify.SpotifyException(400, "sp_dc cookie is empty")
        }

        val nuance = fetchNuance()
        val serverTimeSec = fetchServerTime()
        val totp = generateTotp(nuance.s, serverTimeSec)

        val cookieHeader = buildString {
            append("sp_dc=$cleanedSpDc")
            if (spKey.isNotBlank()) {
                append("; sp_key=${spKey.trim()}")
            }
        }

        val extraHeaders = mapOf(
            "Cookie" to cookieHeader,
            "Origin" to "https://open.spotify.com",
            "Referer" to "https://open.spotify.com/",
        )

        suspend fun requestToken(productType: String): SpotifyInternalToken {
            val tokenUrl = buildString {
                append(TOKEN_URL)
                append("?reason=transport")
                append("&productType=$productType")
                append("&totp=$totp")
                append("&totpServer=$totp")
                append("&totpVer=${nuance.v}")
            }
            val body = withContext(Dispatchers.IO) {
                httpGet(tokenUrl, extraHeaders)
            }
            return json.decodeFromString<SpotifyInternalToken>(body)
        }

        val token = try {
            requestToken("web-player")
        } catch (primaryErr: Exception) {
            // If cached nuance rejected TOTP or web-player rejected, refresh nuance once & retry
            cachedNuance = null
            val freshNuance = runCatching { fetchNuance(forceRefresh = true) }.getOrNull()
            if (freshNuance != null && freshNuance.v != nuance.v) {
                val freshTotp = generateTotp(freshNuance.s, fetchServerTime())
                val retryUrl = buildString {
                    append(TOKEN_URL)
                    append("?reason=transport")
                    append("&productType=web-player")
                    append("&totp=$freshTotp")
                    append("&totpServer=$freshTotp")
                    append("&totpVer=${freshNuance.v}")
                }
                val retryBody = withContext(Dispatchers.IO) {
                    httpGet(retryUrl, extraHeaders)
                }
                json.decodeFromString<SpotifyInternalToken>(retryBody)
            } else {
                // Secondary productType fallback if web-player returned an HTTP/parse error
                runCatching { requestToken("mobile-web-player") }.getOrElse {
                    throw primaryErr
                }
            }
        }

        if (token.isAnonymous || token.accessToken.isBlank()) {
            throw Spotify.SpotifyException(
                401,
                "Received anonymous token (isAnonymous=${token.isAnonymous}) — sp_dc cookie is invalid, incomplete, or expired",
            )
        }

        token
    }

    private suspend fun fetchNuance(forceRefresh: Boolean = false): Nuance = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            cachedNuance?.let { return@withContext it }
        }
        var lastErr: Exception? = null
        // 1. Primary GitHub Gist API
        try {
            val body = httpGet(NUANCE_GIST_URL, emptyMap())
            val gist = json.decodeFromString<GistFiles>(body)
            val nuancesJson = gist.files.values.firstOrNull()?.content
                ?: throw Spotify.SpotifyException(500, "Gist has no files")
            val nuances = json.decodeFromString<List<Nuance>>(nuancesJson)
            val best = nuances.maxByOrNull { it.v }
                ?: throw Spotify.SpotifyException(500, "No nuance data found in gist")
            cachedNuance = best
            return@withContext best
        } catch (e: Exception) {
            lastErr = e
        }

        // 2. Fallback: raw gist CDN (bypasses GitHub REST API 403 rate limits on mobile CGNAT IPs)
        try {
            val rawBody = httpGet(NUANCE_RAW_FALLBACK_URL, emptyMap())
            val nuances = json.decodeFromString<List<Nuance>>(rawBody)
            val best = nuances.maxByOrNull { it.v }
                ?: throw Spotify.SpotifyException(500, "No nuance data found in raw gist")
            cachedNuance = best
            return@withContext best
        } catch (e: Exception) {
            throw Spotify.SpotifyException(
                503,
                "Failed to fetch TOTP secret (primary=${lastErr?.message}; fallback=${e.message})",
            )
        }
    }

    private suspend fun fetchServerTime(): Long = withContext(Dispatchers.IO) {
        try {
            val body = httpGet(SERVER_TIME_URL, emptyMap())
            val response = json.decodeFromString<ServerTimeResponse>(body)
            response.serverTime
        } catch (_: Exception) {
            // Device UTC fallback if /api/server-time experiences transient CDN failure
            System.currentTimeMillis() / 1000L
        }
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

    private fun httpGet(urlString: String, extraHeaders: Map<String, String>): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
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
                    "HTTP $responseCode: $errorBody",
                )
            }

            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
