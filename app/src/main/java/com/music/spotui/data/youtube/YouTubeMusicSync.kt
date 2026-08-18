package com.music.spotui.data.youtube

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.data.preferences.OfflineCollectionsPref
import com.music.spotui.data.preferences.signalUiSettingsChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Official YouTube Data API playlist import/synchronization path.
 *
 * The user supplies their own Google OAuth Android client ID in Settings, completes
 * the visible Google consent flow, and can revoke it at any time. Tokens are stored
 * encrypted under an Android Keystore AES key. This component imports metadata and
 * video references only; it never extracts protected media or captures passwords.
 */
object YouTubeMusicSync {
    private const val PREF = "youtube_music_sync"
    private const val CLIENT_ID = "oauth_client_id"
    private const val AUTO = "auto_sync"
    private const val LAST_STATUS = "last_status"
    private const val LAST_SYNC = "last_sync"
    private const val LAST_ERROR = "last_error"
    private const val PKCE_VERIFIER = "pkce_verifier"
    private const val REDIRECT_URI = "com.music.spotui:/oauth2redirect"
    private const val SCOPE = "https://www.googleapis.com/auth/youtube.readonly"
    private const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
    private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
    private const val PLAYLIST_ENDPOINT = "https://www.googleapis.com/youtube/v3/playlists"
    private const val PLAYLIST_ITEMS_ENDPOINT = "https://www.googleapis.com/youtube/v3/playlistItems"
    private const val UNIQUE_WORK = "spotui_youtube_music_sync"

    data class Status(
        val configured: Boolean,
        val connected: Boolean,
        val autoSync: Boolean,
        val lastSync: String,
        val detail: String,
    )

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun clientId(context: Context): String = prefs(context).getString(CLIENT_ID, "").orEmpty().trim()
    fun setClientId(context: Context, value: String) {
        prefs(context).edit().putString(CLIENT_ID, value.trim()).apply()
        signalUiSettingsChanged()
    }

    fun isConfigured(context: Context): Boolean = clientId(context).contains(".apps.googleusercontent.com")
    fun isConnected(context: Context): Boolean = TokenVault.read(context) != null

    fun status(context: Context): Status = Status(
        configured = isConfigured(context),
        connected = isConnected(context),
        autoSync = prefs(context).getBoolean(AUTO, false),
        lastSync = prefs(context).getString(LAST_SYNC, "Never").orEmpty(),
        detail = prefs(context).getString(LAST_STATUS, "OAuth client ID is required to enable official playlist sync.").orEmpty(),
    )

    /** Starts the official Google OAuth PKCE consent flow. */
    fun authorizationUrl(context: Context): Result<String> = runCatching {
        require(isConfigured(context)) { "Enter your Google OAuth Android client ID first." }
        val verifier = randomVerifier()
        prefs(context).edit().putString(PKCE_VERIFIER, verifier).apply()
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        listOf(
            "client_id" to clientId(context),
            "redirect_uri" to REDIRECT_URI,
            "response_type" to "code",
            "scope" to SCOPE,
            "code_challenge" to challenge,
            "code_challenge_method" to "S256",
            "access_type" to "offline",
            "prompt" to "consent",
        ).joinToString("&", prefix = "$AUTH_ENDPOINT?") { (k, v) -> "${encode(k)}=${encode(v)}" }
    }

    suspend fun completeAuthorization(context: Context, code: String): Result<Status> = withContext(Dispatchers.IO) {
        runCatching {
            val verifier = prefs(context).getString(PKCE_VERIFIER, "").orEmpty()
            require(verifier.isNotBlank()) { "The authorization session expired. Start the connection again." }
            val token = postForm(
                TOKEN_ENDPOINT,
                mapOf(
                    "code" to code,
                    "client_id" to clientId(context),
                    "redirect_uri" to REDIRECT_URI,
                    "grant_type" to "authorization_code",
                    "code_verifier" to verifier,
                ),
            )
            val access = token.optString("access_token")
            require(access.isNotBlank()) { token.optString("error_description", "Google did not return an access token.") }
            val refresh = token.optString("refresh_token")
            val expiry = System.currentTimeMillis() + token.optLong("expires_in", 3600L) * 1000L
            TokenVault.write(context, OAuthToken(access, refresh, expiry))
            prefs(context).edit().remove(PKCE_VERIFIER).putString(LAST_STATUS, "Connected with Google OAuth. Ready to import your YouTube playlists.").apply()
            signalUiSettingsChanged()
            status(context)
        }.onFailure { saveFailure(context, it) }
    }

    suspend fun syncNow(context: Context): Result<Status> = withContext(Dispatchers.IO) {
        runCatching {
            require(isConfigured(context)) { "YouTube sync needs a Google OAuth Android client ID in Settings." }
            val token = freshToken(context)
            val playlists = fetchAllPlaylists(token.accessToken)
            playlists.forEach { playlist ->
                val tracks = fetchPlaylistItems(token.accessToken, playlist.id)
                OfflineCollectionsPref.saveCollection(
                    context = context,
                    id = "youtube_api_${playlist.id}",
                    name = playlist.title,
                    coverUri = playlist.coverUri,
                    artists = playlist.channelTitle,
                    isPlaylist = true,
                    songs = tracks,
                )
            }
            val now = Instant.now().toString()
            prefs(context).edit()
                .putString(LAST_SYNC, now)
                .putString(LAST_STATUS, "Synced ${playlists.size} YouTube playlist(s) at $now.")
                .remove(LAST_ERROR)
                .apply()
            signalUiSettingsChanged()
            status(context)
        }.onFailure { saveFailure(context, it) }
    }

    fun setAutoSync(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(AUTO, enabled).apply()
        val manager = WorkManager.getInstance(context.applicationContext)
        if (enabled && isConfigured(context) && isConnected(context)) {
            val request = PeriodicWorkRequestBuilder<YouTubeMusicSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .addTag(UNIQUE_WORK)
                .build()
            manager.enqueueUniquePeriodicWork(UNIQUE_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
            prefs(context).edit().putString(LAST_STATUS, "Automatic sync scheduled when network is available.").apply()
        } else {
            manager.cancelUniqueWork(UNIQUE_WORK)
            if (enabled) prefs(context).edit().putString(LAST_STATUS, "Auto-sync will start after Google OAuth is connected.").apply()
        }
        signalUiSettingsChanged()
    }

    fun requestImmediateSync(context: Context) {
        val request = OneTimeWorkRequestBuilder<YouTubeMusicSyncWorker>()
            .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork("${UNIQUE_WORK}_manual", ExistingWorkPolicy.REPLACE, request)
    }

    fun revoke(context: Context) {
        TokenVault.clear(context)
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK)
        prefs(context).edit().putBoolean(AUTO, false).putString(LAST_STATUS, "YouTube OAuth access revoked locally.").apply()
        signalUiSettingsChanged()
    }

    private suspend fun freshToken(context: Context): OAuthToken {
        val saved = TokenVault.read(context) ?: error("Connect Google OAuth before syncing YouTube playlists.")
        if (saved.expiresAtMs > System.currentTimeMillis() + 60_000L) return saved
        require(saved.refreshToken.isNotBlank()) { "This OAuth session has no refresh token. Reconnect Google OAuth." }
        val refreshed = postForm(
            TOKEN_ENDPOINT,
            mapOf("client_id" to clientId(context), "grant_type" to "refresh_token", "refresh_token" to saved.refreshToken),
        )
        val access = refreshed.optString("access_token")
        require(access.isNotBlank()) { refreshed.optString("error_description", "Unable to refresh Google authorization.") }
        val token = saved.copy(accessToken = access, expiresAtMs = System.currentTimeMillis() + refreshed.optLong("expires_in", 3600L) * 1000L)
        TokenVault.write(context, token)
        return token
    }

    private data class Playlist(val id: String, val title: String, val channelTitle: String, val coverUri: String)

    private fun fetchAllPlaylists(accessToken: String): List<Playlist> {
        val output = mutableListOf<Playlist>()
        var pageToken: String? = null
        do {
            val url = "$PLAYLIST_ENDPOINT?part=snippet,contentDetails&mine=true&maxResults=50" + (pageToken?.let { "&pageToken=${encode(it)}" } ?: "")
            val json = getJson(url, accessToken)
            val items = json.optJSONArray("items")
            for (index in 0 until (items?.length() ?: 0)) {
                val item = items!!.getJSONObject(index)
                val snippet = item.optJSONObject("snippet") ?: continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                val thumb = snippet.optJSONObject("thumbnails")?.optJSONObject("medium")?.optString("url")
                    ?: snippet.optJSONObject("thumbnails")?.optJSONObject("default")?.optString("url").orEmpty()
                output += Playlist(id, snippet.optString("title", "YouTube playlist"), snippet.optString("channelTitle"), thumb)
            }
            pageToken = json.optString("nextPageToken").ifBlank { null }
        } while (pageToken != null)
        return output
    }

    private fun fetchPlaylistItems(accessToken: String, playlistId: String): List<SongsModel> {
        val output = mutableListOf<SongsModel>()
        var pageToken: String? = null
        do {
            val url = "$PLAYLIST_ITEMS_ENDPOINT?part=snippet,contentDetails&playlistId=${encode(playlistId)}&maxResults=50" + (pageToken?.let { "&pageToken=${encode(it)}" } ?: "")
            val json = getJson(url, accessToken)
            val items = json.optJSONArray("items")
            for (index in 0 until (items?.length() ?: 0)) {
                val item = items!!.getJSONObject(index)
                val snippet = item.optJSONObject("snippet") ?: continue
                val videoId = snippet.optJSONObject("resourceId")?.optString("videoId").orEmpty()
                if (videoId.isBlank()) continue
                val thumb = snippet.optJSONObject("thumbnails")?.optJSONObject("medium")?.optString("url")
                    ?: snippet.optJSONObject("thumbnails")?.optJSONObject("default")?.optString("url").orEmpty()
                output += SongsModel(
                    id = ("yt:$playlistId:$videoId").hashCode() and 0x7fffffff,
                    title = snippet.optString("title", "YouTube item"),
                    album = "YouTube Music",
                    singer = snippet.optString("videoOwnerChannelTitle", snippet.optString("channelTitle", "YouTube")),
                    coverUri = thumb,
                    url = "https://music.youtube.com/watch?v=$videoId",
                    durationMs = 0,
                )
            }
            pageToken = json.optString("nextPageToken").ifBlank { null }
        } while (pageToken != null)
        return output
    }

    private fun getJson(url: String, accessToken: String): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
        }
        val text = connection.useResponse()
        return JSONObject(text)
    }

    private fun postForm(endpoint: String, values: Map<String, String>): JSONObject {
        val body = values.entries.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("Accept", "application/json")
        }
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body) }
        return JSONObject(connection.useResponse())
    }

    private fun HttpURLConnection.useResponse(): String {
        val code = responseCode
        val stream = if (code in 200..299) inputStream else errorStream
        val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
        if (code !in 200..299) {
            val detail = runCatching { JSONObject(text).optString("error_description", JSONObject(text).optString("error")) }.getOrDefault(text)
            error("YouTube API HTTP $code: ${detail.ifBlank { "request failed" }}")
        }
        return text
    }

    private fun saveFailure(context: Context, error: Throwable) {
        prefs(context).edit().putString(LAST_ERROR, error.message.orEmpty()).putString(LAST_STATUS, "Sync error: ${error.message.orEmpty()}").apply()
        signalUiSettingsChanged()
    }

    private fun randomVerifier(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(48).also(SecureRandom()::nextBytes))
    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private data class OAuthToken(val accessToken: String, val refreshToken: String, val expiresAtMs: Long)

    private object TokenVault {
        private const val KEY_ALIAS = "spotui_youtube_oauth_v1"
        private const val KEY_TOKEN = "encrypted_token"

        fun read(context: Context): OAuthToken? = runCatching {
            val raw = prefs(context).getString(KEY_TOKEN, "").orEmpty()
            if (raw.isBlank()) return null
            val parts = raw.split(':', limit = 2)
            require(parts.size == 2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])))
            val plain = String(cipher.doFinal(Base64.getDecoder().decode(parts[1])))
            val json = JSONObject(plain)
            OAuthToken(json.getString("access"), json.optString("refresh"), json.getLong("expiry"))
        }.getOrNull()

        fun write(context: Context, token: OAuthToken) {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val plain = JSONObject().put("access", token.accessToken).put("refresh", token.refreshToken).put("expiry", token.expiresAtMs).toString().toByteArray()
            val encoded = Base64.getEncoder().encodeToString(cipher.iv) + ":" + Base64.getEncoder().encodeToString(cipher.doFinal(plain))
            prefs(context).edit().putString(KEY_TOKEN, encoded).apply()
        }

        fun clear(context: Context) { prefs(context).edit().remove(KEY_TOKEN).apply() }

        private fun key(): SecretKey {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            return generator.generateKey()
        }
    }
}

class YouTubeMusicSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = YouTubeMusicSync.syncNow(applicationContext).fold(
        onSuccess = { Result.success() },
        onFailure = { error -> if (runAttemptCount < 3) Result.retry() else Result.failure() },
    )
}
