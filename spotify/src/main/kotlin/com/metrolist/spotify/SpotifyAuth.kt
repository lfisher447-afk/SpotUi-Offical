package com.metrolist.spotify

import com.metrolist.spotify.models.SpotifyInternalToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.floor

/**
 * Handles Spotify authentication using the web player's internal session & token endpoints.
 *
 * Strategy order (v2.5.4):
 * 1. Direct authenticated `https://open.spotify.com/` HTML `<script id="session">` extraction
 *    using the user's `sp_dc` (+ `sp_key`) cookie — zero external TOTP dependency when Spotify
 *    serves a live non-anonymous session JSON inside the WebPlayer document.
 * 2. TOTP-signed `/api/token` exchange using mirrored TOTP secret sources:
 *    - `xyloflake/spot-secrets-go` (`secretDict.json` XOR cipher bytes decoded to Base32)
 *    - Raw GitHub Gist mirror & GitHub Gist API (`NUANCE_GIST_ID`)
 *    - Multiple versions + server/client time alignment (`sTime`/`cTime`, `transport`/`init`)
 * 3. Legacy `/get_access_token` fallback with & without TOTP query parameters.
 */
object SpotifyAuth {
    private const val WEB_PLAYER_HOME_URL = "https://open.spotify.com/"
    private const val TOKEN_URL = "https://open.spotify.com/api/token"
    private const val LEGACY_TOKEN_URL = "https://open.spotify.com/get_access_token"
    private const val SERVER_TIME_URL = "https://open.spotify.com/api/server-time"

    private const val SECRET_DICT_URL =
        "https://raw.githubusercontent.com/xyloflake/spot-secrets-go/main/secrets/secretDict.json"
    private const val NUANCE_GIST_ID = "22ed9c6ba463899e933427f7de1f0eef"
    private const val NUANCE_GIST_URL =
        "https://api.github.com/gists/$NUANCE_GIST_ID"
    private const val NUANCE_GIST_RAW_FALLBACK =
        "https://gist.githubusercontent.com/raw/$NUANCE_GIST_ID"

    private const val DESKTOP_CHROME_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36"

    // Clean Spotify accounts entry URL redirecting cleanly back to open.spotify.com after login.
    const val LOGIN_URL =
        "https://accounts.spotify.com/en/login?continue=https%3A%2F%2Fopen.spotify.com%2F"

    const val SIGNUP_URL = "https://www.spotify.com/signup"

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    @Volatile
    private var cachedNuances: List<Nuance> = emptyList()

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
     * Fetches an internal web-player access token using `sp_dc` (+ optional `sp_key`).
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

        // Strategy 1: Direct HTML `#session` extraction from open.spotify.com (zero TOTP required)
        val htmlResult = runCatching {
            withContext(Dispatchers.IO) { fetchTokenFromWebPlayerHtml(cookieHeader) }
        }
        htmlResult.getOrNull()?.let { htmlToken ->
            if (!htmlToken.isAnonymous && htmlToken.accessToken.isNotBlank()) {
                return@runCatching htmlToken
            }
        }

        val webPlayerHeaders = mapOf(
            "Cookie" to cookieHeader,
            "Origin" to "https://open.spotify.com",
            "Referer" to "https://open.spotify.com/",
            "app-platform" to "WebPlayer",
            "spotify-app-version" to "1.2.68.420.g8a4e32f0",
        )

        // Strategy 2: TOTP-signed /api/token across top secret versions
        val nuanceCandidates = runCatching { fetchNuanceCandidates() }.getOrDefault(emptyList())
        val serverTimeSec = runCatching { fetchServerTime() }.getOrElse { System.currentTimeMillis() / 1000L }

        var lastError: Throwable? = htmlResult.exceptionOrNull()
        if (nuanceCandidates.isNotEmpty()) {
            val reasons = listOf("transport", "init")
            val timeOffsets = listOf(0L, 30L)
            for (nuance in nuanceCandidates.take(2)) {
                var stopVersion = false
                for (offset in timeOffsets) {
                    if (stopVersion) break
                    val targetTime = serverTimeSec + offset
                    val totp = generateTotp(nuance.s, targetTime)
                    val clientTotp = generateTotp(nuance.s, (System.currentTimeMillis() / 1000L) + offset)
                    for (reason in reasons) {
                        val tokenUrl = buildString {
                            append(TOKEN_URL)
                            append("?reason=$reason")
                            append("&productType=web-player")
                            append("&totp=$clientTotp")
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
                        val err = attempt.exceptionOrNull()
                        lastError = err
                        val statusCode = (err as? Spotify.SpotifyException)?.statusCode ?: 0
                        if (statusCode == 401) {
                            stopVersion = true
                            break
                        }
                    }
                }
            }
        }

        // Strategy 3: Legacy /get_access_token fallback (with & without TOTP)
        val topNuance = nuanceCandidates.firstOrNull()
        val legacyUrls = buildList {
            if (topNuance != null) {
                val totp = generateTotp(topNuance.s, serverTimeSec)
                add("$LEGACY_TOKEN_URL?reason=transport&productType=web-player&totp=$totp&totpServer=$totp&totpVer=${topNuance.v}")
            }
            add("$LEGACY_TOKEN_URL?reason=transport&productType=web-player")
            add("$LEGACY_TOKEN_URL?reason=init&productType=web-player")
        }
        for (legacyUrl in legacyUrls) {
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

    /**
     * Parses a `<script id="session" ...>{...}</script>` JSON payload (obtained either from
     * direct HTML fetch or live inside WebView) into a [SpotifyInternalToken].
     */
    fun parseSessionScriptJson(rawJson: String): SpotifyInternalToken? {
        val trimmed = rawJson.trim()
        if (trimmed.isBlank()) return null
        return runCatching {
            val parsed = json.decodeFromString<SpotifyInternalToken>(trimmed)
            if (parsed.accessToken.isBlank() || parsed.isAnonymous) return null
            val exp = if (parsed.accessTokenExpirationTimestampMs > System.currentTimeMillis()) {
                parsed.accessTokenExpirationTimestampMs
            } else {
                System.currentTimeMillis() + 30 * 60_000L
            }
            parsed.copy(accessTokenExpirationTimestampMs = exp)
        }.getOrNull()
    }

    private fun fetchTokenFromWebPlayerHtml(cookieHeader: String): SpotifyInternalToken? {
        val html = httpGet(
            WEB_PLAYER_HOME_URL,
            mapOf(
                "Cookie" to cookieHeader,
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "Sec-Fetch-Dest" to "document",
                "Sec-Fetch-Mode" to "navigate",
                "Sec-Fetch-Site" to "none",
                "Upgrade-Insecure-Requests" to "1",
            ),
        )
        // Extract <script id="session" ...>JSON</script>
        val scriptMatch = Regex(
            "<script[^>]+id=[\"']session[\"'][^>]*>(.*?)</script>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(html)
        val rawJson = scriptMatch?.groupValues?.getOrNull(1)?.trim()
        if (!rawJson.isNullOrBlank()) {
            parseSessionScriptJson(rawJson)?.let { return it }
        }
        // Fallback regex if script attribute ordering differs
        val tokenMatch = Regex("\"accessToken\"\\s*:\\s*\"([^\"]+)\"").find(html)
            ?.groupValues?.getOrNull(1)
        val expMatch = Regex("\"accessTokenExpirationTimestampMs\"\\s*:\\s*(\\d+)").find(html)
            ?.groupValues?.getOrNull(1)?.toLongOrNull()
        val anonMatch = Regex("\"isAnonymous\"\\s*:\\s*(true|false)").find(html)
            ?.groupValues?.getOrNull(1)?.toBooleanStrictOrNull() ?: true
        if (!anonMatch && !tokenMatch.isNullOrBlank()) {
            return SpotifyInternalToken(
                accessToken = tokenMatch,
                accessTokenExpirationTimestampMs = expMatch ?: (System.currentTimeMillis() + 30 * 60_000L),
                isAnonymous = false,
            )
        }
        return null
    }

    private suspend fun fetchNuanceCandidates(): List<Nuance> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = cachedNuances
        if (cached.isNotEmpty() && (now - cachedNuanceAtMs) in 0 until NUANCE_CACHE_TTL_MS) {
            return@withContext cached
        }

        val collected = LinkedHashMap<Int, Nuance>()

        // 1. xyloflake/spot-secrets-go secretDict.json (XOR cipher byte arrays)
        runCatching {
            val rawDict = httpGet(SECRET_DICT_URL, emptyMap())
            val obj = json.parseToJsonElement(rawDict) as? JsonObject ?: return@runCatching
            for ((verKey, arrElem) in obj) {
                val ver = verKey.toIntOrNull() ?: continue
                val arr = (arrElem as? JsonArray) ?: continue
                val cipherBytes = arr.mapNotNull { it.jsonPrimitive.intOrNull }
                if (cipherBytes.isEmpty()) continue
                val decodedBase32 = decodeSecretCipherToBase32(cipherBytes)
                if (decodedBase32.isNotBlank()) {
                    collected[ver] = Nuance(s = decodedBase32, v = ver)
                }
            }
        }

        // 2. Raw GitHub Gist mirror (avoids api.github.com unauthenticated 60 req/hr rate limit)
        runCatching {
            val rawBody = httpGet(NUANCE_GIST_RAW_FALLBACK, emptyMap())
            json.decodeFromString<List<Nuance>>(rawBody).forEach { n ->
                if (n.s.isNotBlank()) collected.putIfAbsent(n.v, n)
            }
        }

        // 3. Primary GitHub Gist API fallback
        if (collected.isEmpty()) {
            runCatching {
                val body = httpGet(NUANCE_GIST_URL, emptyMap())
                val gist = json.decodeFromString<GistFiles>(body)
                val nuancesJson = gist.files.values.firstOrNull()?.content.orEmpty()
                if (nuancesJson.isNotBlank()) {
                    json.decodeFromString<List<Nuance>>(nuancesJson).forEach { n ->
                        if (n.s.isNotBlank()) collected.putIfAbsent(n.v, n)
                    }
                }
            }
        }

        val sorted = collected.values.sortedByDescending { it.v }
        if (sorted.isNotEmpty()) {
            cachedNuances = sorted
            cachedNuanceAtMs = now
            return@withContext sorted
        }
        if (cached.isNotEmpty()) return@withContext cached
        emptyList()
    }

    /**
     * Decodes Spotify's WebPlayer TOTP cipher integer array:
     * `transformed[i] = cipher[i] ^ ((i % 33) + 9)` -> concatenated decimal ASCII string -> Base32.
     */
    private fun decodeSecretCipherToBase32(cipher: List<Int>): String {
        val joinedDigits = buildString(cipher.size * 2) {
            for (i in cipher.indices) {
                append(cipher[i] xor ((i % 33) + 9))
            }
        }
        return base32Encode(joinedDigits.toByteArray(Charsets.UTF_8))
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

    private fun generateTotp(secret: String, serverTimeSec: Long): String {
        val key = base32Decode(secret)
        if (key.isEmpty()) return "000000"
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

    private fun base32Encode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val out = StringBuilder((bytes.size * 8 + 4) / 5)
        var buffer = 0
        var bitsLeft = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                bitsLeft -= 5
                out.append(alphabet[(buffer shr bitsLeft) and 0x1F])
            }
        }
        if (bitsLeft > 0) {
            out.append(alphabet[(buffer shl (5 - bitsLeft)) and 0x1F])
        }
        return out.toString()
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
            .header("User-Agent", DESKTOP_CHROME_UA)
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
