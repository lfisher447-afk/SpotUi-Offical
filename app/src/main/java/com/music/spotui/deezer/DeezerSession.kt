package com.music.spotui.deezer

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Lightweight Deezer session client using the user's ARL cookie.
 * Supports session authorization, license token retrieval, quality-aware
 * `media.deezer.com/v1/get_url` resolution (FLAC -> MP3 320 -> MP3 128),
 * ISRC lookup, and fallback text search.
 */
object DeezerSession {
    private const val TAG = "DeezerSession"
    private const val GW_URL = "https://www.deezer.com/ajax/gw-light.php"
    private const val MEDIA_URL = "https://media.deezer.com/v1/get_url"
    private const val API_URL = "https://api.deezer.com/2.0"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36"

    const val QUALITY_MP3_128 = 1
    const val QUALITY_MP3_320 = 3
    const val QUALITY_FLAC = 9

    data class TrackTokens(
        val id: String,
        val trackToken: String,
        val title: String,
        val durationSec: Int,
    )

    data class SessionInfo(
        val authorized: Boolean,
        val userId: Long,
        val userName: String,
        val entitledQuality: Int,
        val detail: String,
    )

    @Volatile private var arl: String = ""
    @Volatile private var licenseToken: String = ""
    @Volatile private var checkForm: String = ""
    @Volatile private var sidCookie: String = ""
    @Volatile private var authorizedForArl: String = ""
    @Volatile private var cachedUserId: Long = 0L
    @Volatile private var cachedUserName: String = ""

    @Volatile var entitledQuality: Int = QUALITY_MP3_128
        private set

    private val client: OkHttpClient by lazy { httpClient() }

    fun httpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)

        runCatching {
            com.metrolist.innertube.network.AuthSessionStore.applyOkHttpSettings(builder, forStream = true)
        }
        return builder.build()
    }

    @Synchronized
    fun setArl(value: String?) {
        val cleaned = value?.trim().orEmpty()
        if (cleaned != arl) {
            arl = cleaned
            licenseToken = ""
            checkForm = ""
            sidCookie = ""
            authorizedForArl = ""
            cachedUserId = 0L
            cachedUserName = ""
            entitledQuality = QUALITY_MP3_128
        }
    }

    fun hasArl(): Boolean = arl.isNotBlank() && licenseToken.isNotBlank()

    fun hasConfiguredArl(): Boolean = arl.isNotBlank()

    private fun cookieHeader(): String = buildString {
        if (arl.isNotBlank()) append("arl=").append(arl)
        if (sidCookie.isNotBlank()) {
            if (isNotEmpty()) append("; ")
            append("sid=").append(sidCookie)
        }
    }

    @Synchronized
    fun authorize(forceRefresh: Boolean = false): SessionInfo {
        if (arl.isBlank()) {
            return SessionInfo(false, 0L, "", QUALITY_MP3_128, "No Deezer ARL configured")
        }
        if (!forceRefresh && authorizedForArl == arl && licenseToken.isNotBlank() && cachedUserId > 0L) {
            return SessionInfo(
                authorized = true,
                userId = cachedUserId,
                userName = cachedUserName,
                entitledQuality = entitledQuality,
                detail = tierDescription(cachedUserName, entitledQuality),
            )
        }

        return runCatching {
            val req = Request.Builder()
                .url("$GW_URL?method=deezer.getUserData&input=3&api_version=1.0&api_token=")
                .header("User-Agent", UA)
                .header("Cookie", "arl=$arl")
                .header("Accept", "application/json")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@runCatching SessionInfo(false, 0L, "", QUALITY_MP3_128, "Deezer HTTP ${resp.code}")
                }
                resp.headers("Set-Cookie").forEach { rawCookie ->
                    if (rawCookie.startsWith("sid=", ignoreCase = true)) {
                        sidCookie = rawCookie.substringAfter("sid=").substringBefore(';').trim()
                    }
                }
                val body = resp.body?.string().orEmpty()
                val root = JSONObject(body)
                val results = root.optJSONObject("results") ?: JSONObject()
                val user = results.optJSONObject("USER") ?: JSONObject()
                val uid = user.optLong("USER_ID", 0L)
                if (uid <= 0L) {
                    licenseToken = ""
                    authorizedForArl = ""
                    return@runCatching SessionInfo(false, 0L, "", QUALITY_MP3_128, "ARL invalid or expired")
                }
                val options = user.optJSONObject("OPTIONS") ?: JSONObject()
                licenseToken = options.optString("license_token", "")
                checkForm = results.optString("checkForm", "")
                cachedUserId = uid
                cachedUserName = user.optString("BLOG_NAME", "").ifBlank { "Deezer #$uid" }
                val webLossless = options.optBoolean("web_lossless", false) ||
                    options.optBoolean("mobile_lossless", false)
                val webHq = options.optBoolean("web_hq", false) ||
                    options.optBoolean("mobile_hq", false)
                entitledQuality = when {
                    webLossless -> QUALITY_FLAC
                    webHq -> QUALITY_MP3_320
                    else -> QUALITY_MP3_128
                }
                authorizedForArl = arl
                SessionInfo(
                    authorized = true,
                    userId = uid,
                    userName = cachedUserName,
                    entitledQuality = entitledQuality,
                    detail = tierDescription(cachedUserName, entitledQuality),
                )
            }
        }.getOrElse { e ->
            Log.w(TAG, "Deezer authorize failed: ${e.message}")
            SessionInfo(false, 0L, "", QUALITY_MP3_128, e.message ?: "Network error")
        }
    }

    fun deezerIdForIsrc(isrc: String): String? = runCatching {
        val clean = isrc.trim()
        if (clean.isEmpty()) return@runCatching null
        val req = Request.Builder()
            .url("$API_URL/track/isrc:${URLEncoder.encode(clean, "UTF-8")}")
            .header("User-Agent", UA)
            .header("Accept", "application/json")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val obj = JSONObject(resp.body?.string().orEmpty())
            val id = obj.optLong("id", 0L)
            if (id > 0L) id.toString() else null
        }
    }.getOrNull()

    fun searchTrackId(query: String): String? = runCatching {
        val clean = query.trim()
        if (clean.isEmpty()) return@runCatching null
        val encoded = URLEncoder.encode(clean, "UTF-8")
        val req = Request.Builder()
            .url("$API_URL/search/track?q=$encoded&limit=3")
            .header("User-Agent", UA)
            .header("Accept", "application/json")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("data") ?: return@use null
            if (arr.length() == 0) return@use null
            val first = arr.optJSONObject(0) ?: return@use null
            val id = first.optLong("id", 0L)
            if (id > 0L) id.toString() else null
        }
    }.getOrNull()

    fun trackTokens(deezerTrackId: String): TrackTokens? {
        if (deezerTrackId.isBlank() || arl.isBlank()) return null
        if (checkForm.isBlank()) authorize()
        val token = checkForm
        val payload = JSONObject().apply { put("sng_id", deezerTrackId) }.toString()
        return runCatching {
            val req = Request.Builder()
                .url("$GW_URL?method=song.getData&input=3&api_version=1.0&api_token=${URLEncoder.encode(token, "UTF-8")}")
                .header("User-Agent", UA)
                .header("Cookie", cookieHeader())
                .header("Accept", "application/json")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val root = JSONObject(resp.body?.string().orEmpty())
                val results = root.optJSONObject("results") ?: return@use null
                val trackToken = results.optString("TRACK_TOKEN", "")
                if (trackToken.isBlank()) return@use null
                TrackTokens(
                    id = results.optString("SNG_ID", deezerTrackId).ifBlank { deezerTrackId },
                    trackToken = trackToken,
                    title = results.optString("SNG_TITLE", ""),
                    durationSec = results.optInt("DURATION", 0),
                )
            }
        }.getOrNull()
    }

    /** Returns Pair(cdnUrl, isBlowfishEncrypted) or Pair(null, false). */
    fun getTrackUrl(tokens: TrackTokens, quality: Int): Pair<String?, Boolean> {
        if (licenseToken.isBlank() || tokens.trackToken.isBlank()) return null to false
        val formatName = when (quality) {
            QUALITY_FLAC -> "FLAC"
            QUALITY_MP3_320 -> "MP3_320"
            else -> "MP3_128"
        }
        val bodyJson = JSONObject().apply {
            put("license_token", licenseToken)
            put(
                "media",
                JSONArray().put(
                    JSONObject().apply {
                        put("type", "FULL")
                        put("formats", JSONArray().put(JSONObject().apply {
                            put("cipher", "BF_CBC_STRIPE")
                            put("format", formatName)
                        }))
                    },
                ),
            )
            put("track_tokens", JSONArray().put(tokens.trackToken))
        }.toString()

        return runCatching {
            val req = Request.Builder()
                .url(MEDIA_URL)
                .header("User-Agent", UA)
                .header("Accept", "application/json")
                .post(bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null to false
                val root = JSONObject(resp.body?.string().orEmpty())
                val dataArr = root.optJSONArray("data") ?: return@use null to false
                val firstData = dataArr.optJSONObject(0) ?: return@use null to false
                val mediaArr = firstData.optJSONArray("media") ?: return@use null to false
                for (i in 0 until mediaArr.length()) {
                    val mediaObj = mediaArr.optJSONObject(i) ?: continue
                    val sources = mediaObj.optJSONArray("sources") ?: continue
                    val cipherType = mediaObj.optJSONObject("cipher")?.optString("type", "").orEmpty()
                    val encrypted = cipherType.contains("BF_CBC", ignoreCase = true)
                    for (j in 0 until sources.length()) {
                        val url = sources.optJSONObject(j)?.optString("url", "")
                        if (!url.isNullOrBlank() && url.startsWith("http")) {
                            return@use url to encrypted
                        }
                    }
                }
                null to false
            }
        }.getOrDefault(null to false)
    }

    private fun tierDescription(userName: String, quality: Int): String = when (quality) {
        QUALITY_FLAC -> "$userName · HiFi FLAC 16-bit"
        QUALITY_MP3_320 -> "$userName · High MP3 320 kbps"
        else -> "$userName · Free MP3 128 kbps"
    }
}
