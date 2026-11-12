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
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.network.AuthSessionStore
import com.metrolist.innertube.utils.YouTubeUrlParser
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.data.preferences.OfflineCollectionsPref
import com.music.spotui.data.preferences.signalUiSettingsChanged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import java.net.URLDecoder
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
 * YouTube / YouTube Music playlist synchronization & import engine.
 *
 * Supports three independent sync paths so users are never stuck on Google OAuth client mismatches:
 * 1. **Zero-Config YouTube Music Session Sync** ([syncFromSignedInSession]): uses the user's
 *    signed-in `YouTubeMusicWebActivity` cookies via `:innertube` (`FEmusic_liked_playlists`).
 * 2. **Public/Unlisted Playlist URL Import** ([importPlaylistUrl]): imports any YouTube or
 *    YouTube Music playlist link or ID with zero login or Google Cloud OAuth setup required.
 * 3. **Official Google OAuth PKCE Sync** ([authorizationUrl] / [completeAuthorization] / [syncNow]):
 *    supports both **Android App Scheme** (`com.music.spotui:/oauth2redirect`) and **Loopback HTTP**
 *    (`http://127.0.0.1:8765/oauth2redirect`, with optional Client Secret for Web/Desktop clients)
 *    to eliminate `Error 400: invalid_request` / "Custom scheme URIs are not allowed for WEB client".
 */
object YouTubeMusicSync {
    private const val PREF = "youtube_music_sync"
    private const val CLIENT_ID = "oauth_client_id"
    private const val REDIRECT_MODE = "oauth_redirect_mode"
    private const val ENCRYPTED_SECRET = "encrypted_client_secret"
    private const val AUTO = "auto_sync"
    private const val LAST_STATUS = "last_status"
    private const val LAST_SYNC = "last_sync"
    private const val LAST_ERROR = "last_error"
    private const val PKCE_VERIFIER = "pkce_verifier"

    const val ANDROID_REDIRECT_URI = "com.music.spotui:/oauth2redirect"
    const val LOOPBACK_PORT = 8765
    const val LOOPBACK_REDIRECT_URI = "http://127.0.0.1:$LOOPBACK_PORT/oauth2redirect"

    private const val SCOPE = "https://www.googleapis.com/auth/youtube.readonly"
    private const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
    private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
    private const val PLAYLIST_ENDPOINT = "https://www.googleapis.com/youtube/v3/playlists"
    private const val PLAYLIST_ITEMS_ENDPOINT = "https://www.googleapis.com/youtube/v3/playlistItems"
    private const val UNIQUE_WORK = "spotui_youtube_music_sync"

    private val loopbackScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var activeLoopbackJob: Job? = null

    @Volatile
    private var activeServerSocket: ServerSocket? = null

    enum class OAuthRedirectMode(val label: String, val redirectUri: String, val detail: String) {
        LOOPBACK_HTTP(
            label = "Loopback HTTP (Web / Desktop)",
            redirectUri = LOOPBACK_REDIRECT_URI,
            detail = "Works with Desktop & Web OAuth clients ($LOOPBACK_REDIRECT_URI)",
        ),
        ANDROID_SCHEME(
            label = "Android Custom Scheme",
            redirectUri = ANDROID_REDIRECT_URI,
            detail = "Works with Android OAuth clients ($ANDROID_REDIRECT_URI)",
        );

        companion object {
            fun fromName(raw: String?): OAuthRedirectMode =
                entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: ANDROID_SCHEME
        }
    }

    data class Status(
        val configured: Boolean,
        val connected: Boolean,
        val autoSync: Boolean,
        val lastSync: String,
        val detail: String,
        val sessionReady: Boolean = false,
        val redirectMode: OAuthRedirectMode = OAuthRedirectMode.ANDROID_SCHEME,
        val hasClientSecret: Boolean = false,
    )

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun clientId(context: Context): String = prefs(context).getString(CLIENT_ID, "").orEmpty().trim()
    fun setClientId(context: Context, value: String) {
        prefs(context).edit().putString(CLIENT_ID, value.trim()).apply()
        signalUiSettingsChanged()
    }

    fun redirectMode(context: Context): OAuthRedirectMode =
        OAuthRedirectMode.fromName(prefs(context).getString(REDIRECT_MODE, OAuthRedirectMode.ANDROID_SCHEME.name))

    fun setRedirectMode(context: Context, mode: OAuthRedirectMode) {
        prefs(context).edit().putString(REDIRECT_MODE, mode.name).apply()
        signalUiSettingsChanged()
    }

    fun hasClientSecret(context: Context): Boolean = !readEncryptedString(context, ENCRYPTED_SECRET).isNullOrBlank()
    fun setClientSecret(context: Context, secret: String) {
        val trimmed = secret.trim()
        if (trimmed.isEmpty()) {
            prefs(context).edit().remove(ENCRYPTED_SECRET).apply()
        } else {
            writeEncryptedString(context, ENCRYPTED_SECRET, trimmed)
        }
        signalUiSettingsChanged()
    }

    fun isConfigured(context: Context): Boolean = clientId(context).contains(".apps.googleusercontent.com")
    fun isConnected(context: Context): Boolean = TokenVault.read(context) != null
    fun isSessionReady(): Boolean = AuthSessionStore.current().hasLoginCookie

    fun status(context: Context): Status {
        val mode = redirectMode(context)
        val defaultDetail = when {
            isConnected(context) -> "Connected via Google OAuth (${mode.label})."
            isSessionReady() -> "YouTube Music account cookie active. Tap Sync from YTM Session anytime."
            else -> "Use 1-Tap YTM Session Sync, Playlist URL Import, or Google OAuth (${mode.label})."
        }
        return Status(
            configured = isConfigured(context),
            connected = isConnected(context),
            autoSync = prefs(context).getBoolean(AUTO, false),
            lastSync = prefs(context).getString(LAST_SYNC, "Never").orEmpty(),
            detail = prefs(context).getString(LAST_STATUS, defaultDetail).orEmpty(),
            sessionReady = isSessionReady(),
            redirectMode = mode,
            hasClientSecret = hasClientSecret(context),
        )
    }

    /**
     * Syncs all user playlists directly from the active YouTube Music session
     * (from YouTubeMusicWebActivity cookies) via :innertube without requiring a Google Cloud Client ID.
     */
    suspend fun syncFromSignedInSession(context: Context): Result<Status> = withContext(Dispatchers.IO) {
        runCatching {
            require(AuthSessionStore.current().hasLoginCookie) {
                "Sign in to YouTube Music Web Companion first (Settings -> Open YouTube Music playlists), or import a playlist URL directly."
            }
            val playlistItems = mutableListOf<PlaylistItem>()
            var libraryPage = YouTube.library("FEmusic_liked_playlists").getOrThrow()
            playlistItems += libraryPage.items.filterIsInstance<PlaylistItem>()
            var continuation = libraryPage.continuation
            var guard = 0
            while (!continuation.isNullOrBlank() && guard < 10) {
                val nextPage = YouTube.libraryContinuation(continuation).getOrNull() ?: break
                playlistItems += nextPage.items.filterIsInstance<PlaylistItem>()
                continuation = nextPage.continuation
                guard++
            }
            val uniquePlaylists = playlistItems.distinctBy { it.id.removePrefix("VL") }
            require(uniquePlaylists.isNotEmpty()) {
                "No YouTube Music playlists returned for the current session. Re-open YouTube Music Web Companion to refresh cookies, or paste a playlist URL."
            }

            var syncedCount = 0
            uniquePlaylists.forEach { playlistSummary ->
                val cleanId = playlistSummary.id.removePrefix("VL")
                val fullTracks = fetchInnertubePlaylistTracks(cleanId, playlistSummary.title)
                OfflineCollectionsPref.saveCollection(
                    context = context,
                    id = "youtube_api_$cleanId",
                    name = playlistSummary.title,
                    coverUri = playlistSummary.thumbnail.orEmpty(),
                    artists = playlistSummary.author?.name?.takeIf { it.isNotBlank() } ?: "YouTube Music",
                    isPlaylist = true,
                    songs = fullTracks,
                )
                syncedCount++
            }

            val now = Instant.now().toString()
            prefs(context).edit()
                .putString(LAST_SYNC, now)
                .putString(LAST_STATUS, "Synced $syncedCount YouTube Music playlist(s) via signed-in session.")
                .remove(LAST_ERROR)
                .apply()
            signalUiSettingsChanged()
            status(context)
        }.onFailure { saveFailure(context, it) }
    }

    /**
     * Imports any public/unlisted YouTube or YouTube Music playlist from URL or playlist ID
     * without requiring OAuth credentials or sign-in.
     */
    suspend fun importPlaylistUrl(context: Context, input: String): Result<Status> = withContext(Dispatchers.IO) {
        runCatching {
            val playlistId = extractPlaylistIdOrRaw(input)
                ?: throw IllegalArgumentException("Enter a valid YouTube / YouTube Music playlist URL or playlist ID (e.g. PL…).")
            val page = YouTube.playlist(playlistId).getOrThrow()
            val tracks = collectPlaylistSongs(playlistId, page.songs, page.songsContinuation ?: page.continuation, page.playlist.title)
            require(tracks.isNotEmpty()) { "That YouTube playlist returned zero playable tracks (it may be private)." }

            val cleanId = playlistId.removePrefix("VL")
            OfflineCollectionsPref.saveCollection(
                context = context,
                id = "youtube_api_$cleanId",
                name = page.playlist.title.ifBlank { "YouTube Playlist" },
                coverUri = page.playlist.thumbnail.orEmpty(),
                artists = page.playlist.author?.name?.takeIf { it.isNotBlank() } ?: "YouTube Music",
                isPlaylist = true,
                songs = tracks,
            )
            val now = Instant.now().toString()
            prefs(context).edit()
                .putString(LAST_SYNC, now)
                .putString(LAST_STATUS, "Imported '${page.playlist.title.ifBlank { cleanId }}' (${tracks.size} tracks).")
                .remove(LAST_ERROR)
                .apply()
            signalUiSettingsChanged()
            status(context)
        }.onFailure { saveFailure(context, it) }
    }

    internal fun extractPlaylistIdOrRaw(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null
        YouTubeUrlParser.extractPlaylistId(trimmed)?.let { return it.removePrefix("VL") }
        val queryList = Regex("""[?&]list=([a-zA-Z0-9_-]+)""").find(trimmed)?.groupValues?.getOrNull(1)
        if (!queryList.isNullOrBlank()) return queryList.removePrefix("VL")
        if (Regex("""^(VL)?(PL|OLAK5uy_|RDCLAK5uy_|UU|LL|FL|LM)[a-zA-Z0-9_-]{4,}$""").matches(trimmed)) {
            return trimmed.removePrefix("VL")
        }
        return null
    }

    private suspend fun fetchInnertubePlaylistTracks(playlistId: String, fallbackAlbum: String): List<SongsModel> {
        val page = YouTube.playlist(playlistId).getOrNull() ?: return emptyList()
        return collectPlaylistSongs(playlistId, page.songs, page.songsContinuation ?: page.continuation, page.playlist.title.ifBlank { fallbackAlbum })
    }

    private suspend fun collectPlaylistSongs(
        playlistId: String,
        initial: List<com.metrolist.innertube.models.SongItem>,
        initialContinuation: String?,
        albumTitle: String,
    ): List<SongsModel> {
        val collected = initial.toMutableList()
        var next = initialContinuation
        var guard = 0
        while (!next.isNullOrBlank() && guard < 12) {
            val cont = YouTube.playlistContinuation(next).getOrNull() ?: break
            collected += cont.songs
            next = cont.continuation
            guard++
        }
        return collected.distinctBy { it.id }.map { item ->
            SongsModel(
                id = ("yt:$playlistId:${item.id}").hashCode() and 0x7fffffff,
                title = item.title,
                album = item.album?.name ?: albumTitle.ifBlank { "YouTube Music" },
                singer = item.artists.joinToString(", ") { it.name }.ifBlank { "YouTube Music" },
                coverUri = item.thumbnail,
                url = "https://music.youtube.com/watch?v=${item.id}",
                durationMs = (item.duration ?: 0) * 1000,
            )
        }
    }

    internal fun buildAuthorizationUri(
        clientId: String,
        mode: OAuthRedirectMode,
        codeChallenge: String,
    ): String = listOf(
        "client_id" to clientId,
        "redirect_uri" to mode.redirectUri,
        "response_type" to "code",
        "scope" to SCOPE,
        "code_challenge" to codeChallenge,
        "code_challenge_method" to "S256",
        "access_type" to "offline",
        "prompt" to "consent",
    ).joinToString("&", prefix = "$AUTH_ENDPOINT?") { (k, v) -> "${encode(k)}=${encode(v)}" }

    /** Starts the official Google OAuth PKCE consent flow (starting a local loopback server when LOOPBACK_HTTP is selected). */
    fun authorizationUrl(context: Context): Result<String> = runCatching {
        val id = clientId(context)
        require(isConfigured(context)) { "Enter a valid Google OAuth client ID (…apps.googleusercontent.com) first." }
        val mode = redirectMode(context)
        val verifier = randomVerifier()
        prefs(context).edit().putString(PKCE_VERIFIER, verifier).apply()
        val challenge = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        if (mode == OAuthRedirectMode.LOOPBACK_HTTP) {
            startLoopbackListener(context.applicationContext)
        }
        buildAuthorizationUri(id, mode, challenge)
    }

    private fun startLoopbackListener(appContext: Context) {
        activeLoopbackJob?.cancel()
        runCatching { activeServerSocket?.close() }
        activeServerSocket = null

        activeLoopbackJob = loopbackScope.launch {
            var server: ServerSocket? = null
            try {
                server = ServerSocket(LOOPBACK_PORT, 2, InetAddress.getByName("127.0.0.1")).apply {
                    soTimeout = 180_000
                }
                activeServerSocket = server
                prefs(appContext).edit()
                    .putString(LAST_STATUS, "Waiting for Google sign-in callback on $LOOPBACK_REDIRECT_URI…")
                    .apply()
                signalUiSettingsChanged()

                val socket = server.accept()
                socket.use { client ->
                    val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                    val requestLine = reader.readLine().orEmpty()
                    val query = requestLine.substringAfter("GET ", "").substringBefore(" HTTP/").substringAfter('?', "")
                    val params = parseQueryParams(query)
                    val code = params["code"]
                    val err = params["error"]

                    val htmlBody = if (!code.isNullOrBlank()) {
                        "<html><body style='font-family:sans-serif;background:#111;color:#fff;padding:32px;text-align:center'><h2>SpotUI Connected</h2><p>Authorization code received. You can return to SpotUI now.</p></body></html>"
                    } else {
                        "<html><body style='font-family:sans-serif;background:#111;color:#ff8b94;padding:32px;text-align:center'><h2>Authorization Not Completed</h2><p>${err ?: "Missing authorization code"}</p></body></html>"
                    }
                    val responseBytes = htmlBody.toByteArray(Charsets.UTF_8)
                    val headers = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${responseBytes.size}\r\nConnection: close\r\n\r\n"
                    client.getOutputStream().apply {
                        write(headers.toByteArray(Charsets.US_ASCII))
                        write(responseBytes)
                        flush()
                    }

                    if (!err.isNullOrBlank()) {
                        saveFailure(appContext, IllegalStateException("Google OAuth error: $err"))
                    } else if (!code.isNullOrBlank()) {
                        completeAuthorization(appContext, code)
                    }
                }
            } catch (t: Throwable) {
                if (t !is kotlinx.coroutines.CancellationException && t !is java.net.SocketException) {
                    saveFailure(appContext, t)
                }
            } finally {
                runCatching { server?.close() }
                if (activeServerSocket == server) activeServerSocket = null
            }
        }
    }

    internal fun parseQueryParams(query: String): Map<String, String> =
        query.split('&')
            .asSequence()
            .filter { it.contains('=') }
            .associate { pair ->
                val key = URLDecoder.decode(pair.substringBefore('='), "UTF-8")
                val value = URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8")
                key to value
            }

    suspend fun completeAuthorization(context: Context, code: String): Result<Status> = withContext(Dispatchers.IO) {
        runCatching {
            val verifier = prefs(context).getString(PKCE_VERIFIER, "").orEmpty()
            require(verifier.isNotBlank()) { "The authorization session expired. Start the connection again." }
            val mode = redirectMode(context)
            val form = linkedMapOf(
                "code" to code,
                "client_id" to clientId(context),
                "redirect_uri" to mode.redirectUri,
                "grant_type" to "authorization_code",
                "code_verifier" to verifier,
            )
            readEncryptedString(context, ENCRYPTED_SECRET)?.takeIf { it.isNotBlank() }?.let { secret ->
                form["client_secret"] = secret
            }
            val token = postForm(TOKEN_ENDPOINT, form)
            val access = token.optString("access_token")
            require(access.isNotBlank()) { token.optString("error_description", "Google did not return an access token.") }
            val refresh = token.optString("refresh_token")
            val expiry = System.currentTimeMillis() + token.optLong("expires_in", 3600L) * 1000L
            TokenVault.write(context, OAuthToken(access, refresh, expiry))
            prefs(context).edit()
                .remove(PKCE_VERIFIER)
                .putString(LAST_STATUS, "Connected with Google OAuth (${mode.label}). Ready to sync YouTube playlists.")
                .remove(LAST_ERROR)
                .apply()
            signalUiSettingsChanged()
            status(context)
        }.onFailure { saveFailure(context, it) }
    }

    suspend fun syncNow(context: Context): Result<Status> = withContext(Dispatchers.IO) {
        // If user has an active YouTube Music WebView session and hasn't connected OAuth, use session sync automatically.
        if (!isConnected(context) && isSessionReady()) {
            return@withContext syncFromSignedInSession(context)
        }
        runCatching {
            require(isConfigured(context)) { "Choose Sync from YTM Session, Playlist URL Import, or configure a Google OAuth client ID." }
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
        val canRun = (isConfigured(context) && isConnected(context)) || isSessionReady()
        if (enabled && canRun) {
            val request = PeriodicWorkRequestBuilder<YouTubeMusicSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .addTag(UNIQUE_WORK)
                .build()
            manager.enqueueUniquePeriodicWork(UNIQUE_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
            prefs(context).edit().putString(LAST_STATUS, "Automatic sync scheduled when network is available.").apply()
        } else {
            manager.cancelUniqueWork(UNIQUE_WORK)
            if (enabled) prefs(context).edit().putString(LAST_STATUS, "Auto-sync will start once OAuth or YouTube Music Web Companion is connected.").apply()
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
        activeLoopbackJob?.cancel()
        runCatching { activeServerSocket?.close() }
        activeServerSocket = null
        TokenVault.clear(context)
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK)
        prefs(context).edit().putBoolean(AUTO, false).putString(LAST_STATUS, "YouTube OAuth access revoked locally.").apply()
        signalUiSettingsChanged()
    }

    private suspend fun freshToken(context: Context): OAuthToken {
        val saved = TokenVault.read(context) ?: error("Connect Google OAuth or sign in to YouTube Music Web Companion first.")
        if (saved.expiresAtMs > System.currentTimeMillis() + 60_000L) return saved
        require(saved.refreshToken.isNotBlank()) { "This OAuth session has no refresh token. Reconnect Google OAuth." }
        val form = linkedMapOf(
            "client_id" to clientId(context),
            "grant_type" to "refresh_token",
            "refresh_token" to saved.refreshToken,
        )
        readEncryptedString(context, ENCRYPTED_SECRET)?.takeIf { it.isNotBlank() }?.let { secret ->
            form["client_secret"] = secret
        }
        val refreshed = postForm(TOKEN_ENDPOINT, form)
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
                val item = items?.optJSONObject(index) ?: continue
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
                val item = items?.optJSONObject(index) ?: continue
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
        const val KEY_ALIAS = "spotui_youtube_oauth_v1"
        private const val KEY_TOKEN = "encrypted_token"

        fun read(context: Context): OAuthToken? = runCatching {
            val plain = readEncryptedString(context, KEY_TOKEN) ?: return null
            val json = JSONObject(plain)
            OAuthToken(json.getString("access"), json.optString("refresh"), json.getLong("expiry"))
        }.getOrNull()

        fun write(context: Context, token: OAuthToken) {
            val plain = JSONObject().put("access", token.accessToken).put("refresh", token.refreshToken).put("expiry", token.expiresAtMs).toString()
            writeEncryptedString(context, KEY_TOKEN, plain)
        }

        fun clear(context: Context) { prefs(context).edit().remove(KEY_TOKEN).apply() }

        fun key(): SecretKey {
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

    private fun readEncryptedString(context: Context, prefKey: String): String? = runCatching {
        val raw = prefs(context).getString(prefKey, "").orEmpty()
        if (raw.isBlank()) return null
        val parts = raw.split(':', limit = 2)
        require(parts.size == 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, TokenVault.key(), GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])))
        String(cipher.doFinal(Base64.getDecoder().decode(parts[1])))
    }.getOrNull()

    private fun writeEncryptedString(context: Context, prefKey: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, TokenVault.key())
        val plain = value.toByteArray()
        val encoded = Base64.getEncoder().encodeToString(cipher.iv) + ":" + Base64.getEncoder().encodeToString(cipher.doFinal(plain))
        prefs(context).edit().putString(prefKey, encoded).apply()
    }
}

class YouTubeMusicSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = YouTubeMusicSync.syncNow(applicationContext).fold(
        onSuccess = { Result.success() },
        onFailure = { if (runAttemptCount < 3) Result.retry() else Result.failure() },
    )
}
