@file:Suppress("TooManyFunctions", "LargeClass", "LongMethod", "LongParameterList", "MagicNumber")

package com.music.spotui.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import com.music.spotui.audio.UiSoundEngine
import com.music.spotui.data.api.SpotifySync
import com.music.spotui.util.DevConsoleManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

// ═══════════════════════════════════════════════════════════════════════════
// Official Spotify OAuth 2.0 — Authorization Code + PKCE, loopback redirect
//
// No cookies, no TOTP scraping, no embedded-WebView anti-bot problems:
//   1. Opens accounts.spotify.com/authorize in Chrome Custom Tabs (real browser engine).
//   2. Spotify redirects to http://127.0.0.1:<port>/callback, served by a tiny one-shot
//      server bound to the loopback interface only.
//   3. The code is exchanged (with the PKCE verifier) for access + refresh tokens.
//   4. The library is imported through the official Web API (api.spotify.com/v1).
//
// One-time setup (developer.spotify.com/dashboard → your app → Settings):
//   • Redirect URI:  http://127.0.0.1/callback   (no port — Spotify allows the port to vary
//                    for loopback IP literals; https and 127.0.0.1 are the only accepted forms)
//   • APIs used:     Web API
//   • User Management: add the Spotify account(s) that will sign in (Development Mode: ≤ 5 users,
//     app owner needs Premium).
// ═══════════════════════════════════════════════════════════════════════════

internal class SpotifyOAuthException(
    val status: Int,
    val error: String?,
    message: String,
) : Exception(message)

@Serializable
internal data class OAuthTrack(
    val id: String? = null,
    val uri: String? = null,
    val name: String,
    val artists: List<String> = emptyList(),
    val album: String? = null,
    val imageUrl: String? = null,
    val durationMs: Long = 0,
    val addedAt: String? = null,
)

@Serializable
internal data class OAuthPlaylist(
    val id: String,
    val name: String,
    val owner: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val trackCount: Int = 0,
    val tracks: List<OAuthTrack> = emptyList(),
)

@Serializable
internal data class OAuthAlbum(
    val id: String,
    val name: String,
    val artists: List<String> = emptyList(),
    val imageUrl: String? = null,
    val totalTracks: Int = 0,
    val addedAt: String? = null,
)

@Serializable
internal data class OAuthArtist(
    val id: String,
    val name: String,
    val imageUrl: String? = null,
)

@Serializable
internal data class OAuthLibrary(
    val accountId: String? = null,
    val accountName: String? = null,
    val product: String? = null,
    val syncedAtMs: Long = 0,
    val playlists: List<OAuthPlaylist> = emptyList(),
    val likedTracks: List<OAuthTrack> = emptyList(),
    val albums: List<OAuthAlbum> = emptyList(),
    val artists: List<OAuthArtist> = emptyList(),
    val warnings: List<String> = emptyList(),
) {
    val detail: String
        get() {
            val playlistTracks = playlists.sumOf { it.tracks.size }
            return "Imported ${playlists.size} playlists ($playlistTracks tracks), " +
                "${likedTracks.size} liked songs, ${albums.size} albums, ${artists.size} artists" +
                if (warnings.isEmpty()) "" else " · ${warnings.size} warning(s)"
        }
}

/** Latest library imported through OAuth. Other screens can collect this or call [SpotifyOAuth.loadCachedLibrary]. */
internal object SpotifyOAuthLibraryCache {
    private val _latest = MutableStateFlow<OAuthLibrary?>(null)
    val latest: StateFlow<OAuthLibrary?> = _latest

    fun publish(library: OAuthLibrary?) {
        _latest.value = library
    }
}

private data class OAuthCallback(val code: String?, val error: String?)

private data class RawResponse(val status: Int, val body: String, val retryAfterSec: Long?)

internal object SpotifyOAuth {
    /** Register EXACTLY this in the Spotify dashboard (no port). */
    const val REGISTERED_REDIRECT_URI = "http://127.0.0.1/callback"

    private const val AUTHORIZE_URL = "https://accounts.spotify.com/authorize"
    private const val TOKEN_URL = "https://accounts.spotify.com/api/token"
    private const val API_BASE = "https://api.spotify.com/v1/"
    private const val CALLBACK_PATH = "/callback"
    private const val LOGIN_TIMEOUT_MS = 5 * 60 * 1000L
    private const val HTTP_TIMEOUT_MS = 20_000
    private const val MAX_PAGES = 400
    private const val LIBRARY_FILE = "spotify_oauth_library.json"

    private const val PREFS = "spotui_spotify_oauth"
    private const val KEY_CLIENT_ID = "client_id"
    private const val KEY_ACCESS = "access_token"
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_EXPIRES_AT = "expires_at_ms"
    private const val KEY_SCOPE = "scope"

    private val SCOPES = listOf(
        "user-read-private",
        "user-read-email",
        "user-library-read",
        "playlist-read-private",
        "playlist-read-collaborative",
        "user-follow-read",
        "user-top-read",
    )

    private val CLIENT_ID_REGEX = Regex("^[0-9a-f]{32}$")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private val refreshMutex = Mutex()

    @Volatile
    private var activeReceiver: LoopbackReceiver? = null

    // ── configuration & stored session ───────────────────────────────────────

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isValidClientId(value: String): Boolean = CLIENT_ID_REGEX.matches(value.trim().lowercase())

    fun clientId(context: Context): String = prefs(context).getString(KEY_CLIENT_ID, "").orEmpty()

    fun saveClientId(context: Context, value: String): Boolean {
        val id = value.trim().lowercase()
        if (!CLIENT_ID_REGEX.matches(id)) return false
        val previous = clientId(context)
        prefs(context).edit().apply {
            putString(KEY_CLIENT_ID, id)
            // Tokens belong to one Client ID; switching IDs invalidates them.
            if (previous.isNotEmpty() && previous != id) {
                remove(KEY_ACCESS)
                remove(KEY_REFRESH)
                remove(KEY_EXPIRES_AT)
                remove(KEY_SCOPE)
            }
        }.apply()
        return true
    }

    fun isSignedIn(context: Context): Boolean = !prefs(context).getString(KEY_REFRESH, null).isNullOrBlank()

    fun signOut(context: Context) {
        cancelLogin()
        val app = context.applicationContext
        prefs(app).edit()
            .remove(KEY_ACCESS)
            .remove(KEY_REFRESH)
            .remove(KEY_EXPIRES_AT)
            .remove(KEY_SCOPE)
            .apply()
        runCatching { File(app.filesDir, LIBRARY_FILE).delete() }
        SpotifyOAuthLibraryCache.publish(null)
        DevConsoleManager.logAuth("Spotify OAuth session cleared")
    }

    fun loadCachedLibrary(context: Context): OAuthLibrary? {
        SpotifyOAuthLibraryCache.latest.value?.let { return it }
        val file = File(context.applicationContext.filesDir, LIBRARY_FILE)
        if (!file.exists()) return null
        return runCatching { json.decodeFromString<OAuthLibrary>(file.readText()) }
            .getOrNull()
            ?.also { SpotifyOAuthLibraryCache.publish(it) }
    }

    /** Aborts a sign-in that is waiting for the browser. Safe to call at any time. */
    fun cancelLogin() {
        activeReceiver?.close()
    }

    // ── sign-in ──────────────────────────────────────────────────────────────

    /** Opens Spotify's consent page in Chrome and suspends until the redirect arrives and tokens are stored. */
    suspend fun signIn(context: Context) {
        val app = context.applicationContext
        val clientId = clientId(app)
        if (!CLIENT_ID_REGEX.matches(clientId)) {
            throw SpotifyOAuthException(0, "no_client_id", "Enter your Spotify Client ID first.")
        }

        val verifier = randomUrlSafe(64)
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        val state = randomUrlSafe(24)

        activeReceiver?.close()
        val receiver = try {
            LoopbackReceiver()
        } catch (e: IOException) {
            throw SpotifyOAuthException(0, "no_loopback", "Couldn't open a local port for the Spotify redirect: ${e.message}")
        }
        activeReceiver = receiver

        try {
            val redirectUri = "http://127.0.0.1:${receiver.port}$CALLBACK_PATH"
            val authorizeUrl = Uri.parse(AUTHORIZE_URL).buildUpon()
                .appendQueryParameter("client_id", clientId)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("redirect_uri", redirectUri)
                .appendQueryParameter("scope", SCOPES.joinToString(" "))
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("code_challenge", challenge)
                .appendQueryParameter("state", state)
                .build()
                .toString()

            DevConsoleManager.logAuth("Spotify OAuth: opening browser (redirect $redirectUri)")
            val opened = withContext(Dispatchers.Main) { openSpotifyInChromeCustomTab(app, authorizeUrl) }
            if (!opened) throw SpotifyOAuthException(0, "no_browser", "No browser available to sign in with Spotify.")

            val callback = receiver.await(state, returnToAppUrl(app), app)
            if (callback.error != null) {
                throw SpotifyOAuthException(0, callback.error, describeAuthorizeError(callback.error))
            }
            val code = callback.code
                ?: throw SpotifyOAuthException(0, "no_code", "Spotify did not return an authorization code.")

            DevConsoleManager.logAuth("Spotify OAuth: exchanging authorization code")
            val tokens = postForm(
                TOKEN_URL,
                mapOf(
                    "grant_type" to "authorization_code",
                    "code" to code,
                    "redirect_uri" to redirectUri,
                    "client_id" to clientId,
                    "code_verifier" to verifier,
                ),
            )
            storeTokens(app, tokens, previousRefresh = null)
        } finally {
            receiver.close()
            if (activeReceiver === receiver) activeReceiver = null
        }
    }

    private fun returnToAppUrl(app: Context): String =
        "intent:#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;" +
            "package=${app.packageName};end"

    private fun describeAuthorizeError(error: String): String = when (error) {
        "access_denied" -> "You declined the Spotify permission request."
        else -> "Spotify returned an error: $error"
    }

    // ── tokens ───────────────────────────────────────────────────────────────

    private fun storeTokens(app: Context, tokens: JsonObject, previousRefresh: String?) {
        val access = tokens.str("access_token")
            ?: throw SpotifyOAuthException(500, "bad_token_response", "Spotify returned no access token.")
        val refresh = tokens.str("refresh_token") ?: previousRefresh
            ?: throw SpotifyOAuthException(500, "bad_token_response", "Spotify returned no refresh token.")
        val expiresInSec = tokens.long("expires_in") ?: 3600L
        prefs(app).edit()
            .putString(KEY_ACCESS, access)
            .putString(KEY_REFRESH, refresh)
            .putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + expiresInSec * 1000)
            .putString(KEY_SCOPE, tokens.str("scope").orEmpty())
            .apply()
    }

    private fun clearTokens(app: Context) {
        prefs(app).edit().remove(KEY_ACCESS).remove(KEY_REFRESH).remove(KEY_EXPIRES_AT).apply()
    }

    /**
     * A valid access token, refreshed single-flight when it is about to expire. When [staleToken] is
     * given and another caller already replaced it, that newer token is returned without a second refresh.
     */
    suspend fun accessToken(context: Context, forceRefresh: Boolean = false, staleToken: String? = null): String {
        val app = context.applicationContext
        return refreshMutex.withLock {
            val p = prefs(app)
            val access = p.getString(KEY_ACCESS, null)
            val expiresAt = p.getLong(KEY_EXPIRES_AT, 0L)
            val fresh = !access.isNullOrBlank() && System.currentTimeMillis() < expiresAt - 60_000
            if (fresh && !forceRefresh) return@withLock access!!
            if (forceRefresh && !access.isNullOrBlank() && staleToken != null && access != staleToken) {
                return@withLock access
            }

            val refresh = p.getString(KEY_REFRESH, null)
            if (refresh.isNullOrBlank()) {
                throw SpotifyOAuthException(401, "not_signed_in", "Not signed in with Spotify.")
            }
            val clientId = clientId(app)
            val tokens = try {
                postForm(
                    TOKEN_URL,
                    mapOf("grant_type" to "refresh_token", "refresh_token" to refresh, "client_id" to clientId),
                )
            } catch (e: SpotifyOAuthException) {
                if (e.error == "invalid_grant") clearTokens(app)
                throw e
            }
            storeTokens(app, tokens, previousRefresh = refresh)
            prefs(app).getString(KEY_ACCESS, null).orEmpty()
        }
    }

    // ── library import (official Web API) ────────────────────────────────────

    /**
     * Imports playlists (with tracks), liked songs, saved albums and followed artists. A section that
     * fails (for example an endpoint Spotify disabled for Development Mode apps) is reported in
     * [OAuthLibrary.warnings]; the import only fails when nothing at all could be read.
     */
    suspend fun syncLibrary(
        context: Context,
        includePlaylistTracks: Boolean = true,
        onProgress: (String) -> Unit = {},
    ): OAuthLibrary {
        val app = context.applicationContext
        val warnings = mutableListOf<String>()
        var sectionsOk = 0

        val profile = apiGet(app, "me")
        val accountId = profile.str("id")
        val accountName = profile.str("display_name")?.takeIf { it.isNotBlank() } ?: accountId
        val product = profile.str("product")

        suspend fun <T> section(label: String, block: suspend () -> List<T>): List<T> =
            try {
                block().also { sectionsOk++ }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = describeFailure(e)
                DevConsoleManager.logWarning("SpotifyOAuth", "$label failed: $message")
                warnings += "$label: $message"
                emptyList()
            }

        onProgress("Importing liked songs…")
        val liked = section("Liked songs") {
            collectPages(app, "me/tracks?limit=50").mapNotNull { item ->
                parseTrack(item.obj("track"), item.str("added_at"))
            }.also { onProgress("Liked songs: ${it.size}") }
        }

        onProgress("Importing saved albums…")
        val albums = section("Albums") {
            collectPages(app, "me/albums?limit=50").mapNotNull { item ->
                val album = item.obj("album") ?: return@mapNotNull null
                val id = album.str("id") ?: return@mapNotNull null
                OAuthAlbum(
                    id = id,
                    name = album.str("name").orEmpty(),
                    artists = artistNames(album),
                    imageUrl = firstImage(album),
                    totalTracks = album.long("total_tracks")?.toInt() ?: 0,
                    addedAt = item.str("added_at"),
                )
            }.also { onProgress("Albums: ${it.size}") }
        }

        onProgress("Importing followed artists…")
        val artists = section("Artists") {
            collectPages(app, "me/following?type=artist&limit=50", rootKey = "artists").mapNotNull { artist ->
                val id = artist.str("id") ?: return@mapNotNull null
                OAuthArtist(id = id, name = artist.str("name").orEmpty(), imageUrl = firstImage(artist))
            }.also { onProgress("Artists: ${it.size}") }
        }

        onProgress("Importing playlists…")
        val playlistHeaders = section("Playlists") {
            collectPages(app, "me/playlists?limit=50")
        }
        val playlists = mutableListOf<OAuthPlaylist>()
        playlistHeaders.forEachIndexed { index, header ->
            val id = header.str("id") ?: return@forEachIndexed
            val name = header.str("name").orEmpty().ifBlank { "Untitled playlist" }
            val declaredCount = header.obj("tracks")?.long("total")?.toInt()
                ?: header.obj("items")?.long("total")?.toInt()
                ?: 0
            var tracks = emptyList<OAuthTrack>()
            if (includePlaylistTracks && declaredCount > 0) {
                onProgress("Playlist ${index + 1}/${playlistHeaders.size}: $name")
                tracks = try {
                    fetchPlaylistTracks(app, id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    warnings += "Playlist \"$name\": ${describeFailure(e)}"
                    emptyList()
                }
            }
            playlists += OAuthPlaylist(
                id = id,
                name = name,
                owner = header.obj("owner")?.str("display_name") ?: header.obj("owner")?.str("id"),
                description = header.str("description")?.takeIf { it.isNotBlank() },
                imageUrl = firstImage(header),
                trackCount = declaredCount,
                tracks = tracks,
            )
        }

        if (sectionsOk == 0) {
            throw SpotifyOAuthException(
                403,
                "nothing_readable",
                warnings.firstOrNull() ?: "Spotify did not allow reading any library data.",
            )
        }

        val library = OAuthLibrary(
            accountId = accountId,
            accountName = accountName,
            product = product,
            syncedAtMs = System.currentTimeMillis(),
            playlists = playlists,
            likedTracks = liked,
            albums = albums,
            artists = artists,
            warnings = warnings,
        )
        runCatching {
            File(app.filesDir, LIBRARY_FILE).writeText(json.encodeToString(library))
        }.onFailure { DevConsoleManager.logWarning("SpotifyOAuth", "Couldn't cache library: ${it.message}") }
        SpotifyOAuthLibraryCache.publish(library)
        return library
    }

    suspend fun fetchProfileName(context: Context): String? {
        val me = apiGet(context.applicationContext, "me")
        return me.str("display_name")?.takeIf { it.isNotBlank() } ?: me.str("id")
    }

    private suspend fun fetchPlaylistTracks(app: Context, playlistId: String): List<OAuthTrack> {
        val fields = "next,items(added_at,track(id,uri,name,type,duration_ms,artists(name),album(name,images(url))))"
        val candidates = listOf(
            "playlists/$playlistId/tracks?limit=100&additional_types=track,episode&fields=$fields",
            "playlists/$playlistId/items?limit=100&additional_types=track,episode",
        )
        var lastError: SpotifyOAuthException? = null
        for (path in candidates) {
            try {
                return collectPages(app, path).mapNotNull { item ->
                    // `track` on the classic endpoint; `item` on the renamed one.
                    parseTrack(item.obj("track") ?: item.obj("item"), item.str("added_at"))
                }
            } catch (e: SpotifyOAuthException) {
                lastError = e
                if (e.status != 400 && e.status != 403 && e.status != 404) throw e
            }
        }
        throw lastError ?: SpotifyOAuthException(404, "not_found", "Playlist tracks unavailable.")
    }

    // ── parsing helpers ──────────────────────────────────────────────────────

    private fun parseTrack(track: JsonObject?, addedAt: String?): OAuthTrack? {
        track ?: return null
        val name = track.str("name")?.takeIf { it.isNotBlank() } ?: return null
        val album = track.obj("album")
        return OAuthTrack(
            id = track.str("id"),
            uri = track.str("uri"),
            name = name,
            artists = artistNames(track),
            album = album?.str("name"),
            imageUrl = album?.let { firstImage(it) },
            durationMs = track.long("duration_ms") ?: 0L,
            addedAt = addedAt,
        )
    }

    private fun artistNames(owner: JsonObject): List<String> =
        owner.arr("artists")?.mapNotNull { (it as? JsonObject)?.str("name") }.orEmpty()

    private fun firstImage(owner: JsonObject): String? =
        owner.arr("images")?.firstNotNullOfOrNull { (it as? JsonObject)?.str("url") }

    /** Follows `next` links until exhausted. [rootKey] selects the paging object (e.g. `artists`). */
    private suspend fun collectPages(app: Context, firstPath: String, rootKey: String? = null): List<JsonObject> {
        val out = mutableListOf<JsonObject>()
        var next: String? = firstPath
        var pages = 0
        while (next != null && pages < MAX_PAGES) {
            val response = apiGet(app, next)
            val container = if (rootKey != null) response.obj(rootKey) else response
            container?.arr("items")?.forEach { (it as? JsonObject)?.let(out::add) }
            next = container?.str("next")
            pages++
        }
        return out
    }

    // ── HTTP ─────────────────────────────────────────────────────────────────

    private suspend fun apiGet(app: Context, pathOrUrl: String): JsonObject {
        val url = if (pathOrUrl.startsWith("https://")) pathOrUrl else API_BASE + pathOrUrl.trimStart('/')
        // Never send the bearer token anywhere but Spotify's API host (a `next` URL is server-supplied data).
        if (!url.startsWith("https://api.spotify.com/")) {
            throw SpotifyOAuthException(0, "bad_url", "Refusing to call a non-Spotify URL.")
        }

        var token = accessToken(app)
        var refreshed = false
        var rateLimitRetries = 0
        while (true) {
            val response = withContext(Dispatchers.IO) { httpRequest(url, token) }
            when {
                response.status in 200..299 -> {
                    if (response.body.isBlank()) return JsonObject(emptyMap())
                    return json.parseToJsonElement(response.body) as? JsonObject ?: JsonObject(emptyMap())
                }
                response.status == 401 && !refreshed -> {
                    token = accessToken(app, forceRefresh = true, staleToken = token)
                    refreshed = true
                }
                response.status == 429 && rateLimitRetries < 3 -> {
                    rateLimitRetries++
                    delay((response.retryAfterSec ?: 2L).coerceIn(1L, 10L) * 1000)
                }
                else -> throw apiError(response)
            }
        }
    }

    private fun apiError(response: RawResponse): SpotifyOAuthException {
        val error = runCatching { json.parseToJsonElement(response.body) as? JsonObject }.getOrNull()
        val apiMessage = error?.obj("error")?.str("message") ?: error?.str("error_description")
        return SpotifyOAuthException(
            response.status,
            error?.str("error") ?: "http_${response.status}",
            apiMessage ?: "HTTP ${response.status}",
        )
    }

    private fun httpRequest(url: String, bearer: String): RawResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = HTTP_TIMEOUT_MS
            connection.readTimeout = HTTP_TIMEOUT_MS
            connection.setRequestProperty("Authorization", "Bearer $bearer")
            connection.setRequestProperty("Accept", "application/json")
            return readResponse(connection)
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun postForm(url: String, params: Map<String, String>): JsonObject {
        val response = withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = HTTP_TIMEOUT_MS
                connection.readTimeout = HTTP_TIMEOUT_MS
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.setRequestProperty("Accept", "application/json")
                val body = params.entries.joinToString("&") { (k, v) ->
                    "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
                }
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                readResponse(connection)
            } finally {
                connection.disconnect()
            }
        }
        if (response.status !in 200..299) throw apiError(response)
        return json.parseToJsonElement(response.body) as? JsonObject
            ?: throw SpotifyOAuthException(500, "bad_token_response", "Unexpected response from Spotify.")
    }

    private fun readResponse(connection: HttpURLConnection): RawResponse {
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        val retryAfter = connection.getHeaderField("Retry-After")?.trim()?.toLongOrNull()
        return RawResponse(status, body, retryAfter)
    }

    // ── errors ───────────────────────────────────────────────────────────────

    fun isRetryable(error: Throwable?): Boolean = when (error) {
        is SpotifyOAuthException -> error.status == 408 || error.status == 429 || error.status in 500..599
        is IOException -> true
        else -> false
    }

    fun describeFailure(error: Throwable?): String = when (error) {
        null -> "Unknown error"
        is SpotifyOAuthException -> when {
            error.error == "invalid_client" -> "Spotify doesn't recognise this Client ID. Re-copy it from the dashboard."
            error.error == "invalid_grant" && error.message?.contains("redirect", ignoreCase = true) == true ->
                "Redirect URI mismatch. Register exactly $REGISTERED_REDIRECT_URI in the Spotify dashboard."
            error.error == "invalid_grant" -> "The Spotify session expired or was revoked. Sign in again."
            error.status == 401 -> "Spotify rejected the session. Sign in again."
            error.status == 403 -> "Spotify blocked this request (${error.message}). In Development Mode your account must be " +
                "added under User Management, and some endpoints are unavailable."
            error.status == 429 -> "Spotify is rate limiting this app. Wait a minute, then retry."
            error.status == 408 -> "Sign-in timed out. Try again."
            else -> error.message ?: "Spotify error ${error.status}"
        }
        is java.net.UnknownHostException -> "No internet connection."
        is SocketTimeoutException -> "The connection timed out."
        else -> error.message ?: error.javaClass.simpleName
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun randomUrlSafe(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull

    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

    // ── loopback redirect receiver ───────────────────────────────────────────

    /**
     * One-shot HTTP server bound to 127.0.0.1 only. It answers the browser's redirect from Spotify and
     * hands the `code` back. The `state` parameter is checked, and the code is useless to anyone without
     * the PKCE verifier, which never leaves this process.
     */
    private class LoopbackReceiver : AutoCloseable {
        private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))

        @Volatile
        private var closed = false

        val port: Int get() = server.localPort

        suspend fun await(expectedState: String, returnUrl: String, app: Context): OAuthCallback {
            val job = currentCoroutineContext()[Job]
            return withContext(Dispatchers.IO) {
                awaitBlocking(expectedState, returnUrl, app) { job?.isActive != false }
            }
        }

        private fun awaitBlocking(
            expectedState: String,
            returnUrl: String,
            app: Context,
            stillActive: () -> Boolean,
        ): OAuthCallback {
            val deadline = System.currentTimeMillis() + LOGIN_TIMEOUT_MS
            server.soTimeout = 1000
            while (true) {
                if (closed || !stillActive()) {
                    throw SpotifyOAuthException(0, "cancelled", "Sign-in cancelled.")
                }
                if (System.currentTimeMillis() > deadline) {
                    throw SpotifyOAuthException(408, "timeout", "Sign-in timed out. Try again.")
                }
                val socket = try {
                    server.accept()
                } catch (e: SocketTimeoutException) {
                    continue
                } catch (e: IOException) {
                    if (closed) throw SpotifyOAuthException(0, "cancelled", "Sign-in cancelled.")
                    throw e
                }
                val result = socket.use { handle(it, expectedState, returnUrl) }
                if (result != null) {
                    bringAppToFront(app)
                    return result
                }
            }
        }

        private fun handle(socket: Socket, expectedState: String, returnUrl: String): OAuthCallback? {
            socket.soTimeout = 5000
            val requestLine = try {
                BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.US_ASCII)).readLine()
            } catch (e: IOException) {
                null
            } ?: return null

            val parts = requestLine.split(' ')
            if (parts.size < 2 || parts[0] != "GET") {
                respond(socket, 405, "Method Not Allowed", page("Not allowed", "", null))
                return null
            }
            val uri = Uri.parse("http://127.0.0.1${parts[1]}")
            if (uri.path != CALLBACK_PATH) {
                respond(socket, 404, "Not Found", page("Not found", "", null))
                return null
            }

            val state = uri.getQueryParameter("state")
            val code = uri.getQueryParameter("code")
            val error = uri.getQueryParameter("error")
            if (state != expectedState || (code.isNullOrBlank() && error.isNullOrBlank())) {
                respond(socket, 400, "Bad Request", page("Sign-in failed", "This link is not valid. Go back to SpotUI and try again.", null))
                return null
            }
            return if (!error.isNullOrBlank()) {
                respond(socket, 200, "OK", page("Sign-in cancelled", "Spotify did not grant access. You can return to SpotUI.", returnUrl))
                OAuthCallback(code = null, error = error)
            } else {
                respond(socket, 200, "OK", page("You're signed in", "Return to SpotUI — your library is importing.", returnUrl))
                OAuthCallback(code = code, error = null)
            }
        }

        private fun respond(socket: Socket, status: Int, reason: String, html: String) {
            runCatching {
                val body = html.toByteArray(Charsets.UTF_8)
                val head = "HTTP/1.1 $status $reason\r\n" +
                    "Content-Type: text/html; charset=utf-8\r\n" +
                    "Content-Length: ${body.size}\r\n" +
                    "Cache-Control: no-store\r\n" +
                    "Referrer-Policy: no-referrer\r\n" +
                    "Connection: close\r\n\r\n"
                socket.getOutputStream().apply {
                    write(head.toByteArray(Charsets.US_ASCII))
                    write(body)
                    flush()
                }
            }
        }

        private fun page(title: String, message: String, returnUrl: String?): String {
            val link = returnUrl?.let {
                "<a class=\"btn\" href=\"${escape(it)}\">Return to SpotUI</a>"
            }.orEmpty()
            return "<!doctype html><html><head><meta charset=\"utf-8\">" +
                "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
                "<title>${escape(title)}</title><style>" +
                "body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;" +
                "background:#0e1016;color:#fff;font-family:system-ui,sans-serif;text-align:center;padding:24px}" +
                "h1{font-size:24px;margin:0 0 10px}p{color:#aab2c4;margin:0 0 24px}" +
                ".btn{display:inline-block;background:#1ed760;color:#000;font-weight:700;padding:14px 28px;" +
                "border-radius:999px;text-decoration:none}" +
                "</style></head><body><div><h1>${escape(title)}</h1><p>${escape(message)}</p>$link</div></body></html>"
        }

        private fun escape(text: String): String =
            text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

        /** Best effort: Android may refuse background activity starts, in which case the page's button does it. */
        private fun bringAppToFront(app: Context) {
            runCatching {
                app.packageManager.getLaunchIntentForPackage(app.packageName)?.let { intent ->
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    app.startActivity(intent)
                }
            }
        }

        override fun close() {
            closed = true
            runCatching { server.close() }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// OAuth sign-in pipeline (same UI contract as processLoginSession)
// ═══════════════════════════════════════════════════════════════════════════

/** browser consent → token exchange → profile → full library import. */
internal suspend fun runOAuthPipeline(
    context: Context,
    onStage: (LoginStage) -> Unit,
    onProgress: (String) -> Unit,
): LoginOutcome {
    val app = context.applicationContext
    var stage = LoginStage.VERIFY
    return try {
        onStage(stage)
        SpotifyOAuth.signIn(app)
        DevConsoleManager.logAuth("Spotify OAuth: tokens stored")

        stage = LoginStage.TOKEN
        onStage(stage)

        stage = LoginStage.PROFILE
        onStage(stage)
        val accountName = runCatching { SpotifyOAuth.fetchProfileName(app) }.getOrNull()
        DevConsoleManager.logAuth("Spotify OAuth: profile resolved=${accountName != null}")

        stage = LoginStage.LIBRARY
        onStage(stage)
        val library = SpotifyOAuth.syncLibrary(app, includePlaylistTracks = true, onProgress = onProgress)
        SpotifySync.notifyAuthStateChanged("Signed in!")

        LoginOutcome.Success(
            signInLabel = "Signed in with Spotify!",
            accountName = accountName ?: library.accountName,
            syncDetail = library.detail,
            syncWarning = library.warnings.firstOrNull()?.let { "Some data couldn't be imported: $it" },
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val message = SpotifyOAuth.describeFailure(e)
        DevConsoleManager.logError("SpotifyOAuth", "Failed at $stage: ${e.message}", e)
        LoginOutcome.Failure(stage, message, e)
    }
}

internal fun processOAuthLogin(
    context: Context,
    scope: CoroutineScope,
    setProcessing: (Boolean) -> Unit,
    setStatus: (String) -> Unit,
    setError: (Boolean) -> Unit,
    onComplete: (Boolean) -> Unit,
    onSuccess: (String?) -> Unit,
    onStage: (LoginStage) -> Unit = {},
    onAccount: (String?) -> Unit = {},
) {
    val app = context.applicationContext
    setProcessing(true)
    setError(false)
    setStatus("Opening Spotify in your browser…")

    fun onMain(block: () -> Unit) {
        scope.launch(Dispatchers.Main) { block() }
    }

    launchLoginTask {
        val outcome = runOAuthPipeline(
            context = app,
            onStage = { stage ->
                onMain {
                    onStage(stage)
                    setStatus(
                        when (stage) {
                            LoginStage.VERIFY -> "Approve access in the Spotify page, then return here…"
                            LoginStage.TOKEN -> "Securing your session…"
                            LoginStage.PROFILE -> "Loading your account…"
                            LoginStage.LIBRARY -> "Importing playlists, liked songs & artists…"
                        },
                    )
                }
            },
            onProgress = { message -> onMain { setStatus(message) } },
        )

        when (outcome) {
            is LoginOutcome.Success -> {
                onMain {
                    UiSoundEngine.playToggleSound(app, true)
                    onAccount(outcome.accountName)
                    setStatus(outcome.syncWarning ?: outcome.syncDetail ?: outcome.signInLabel)
                    setProcessing(false)
                    setError(false)
                    onComplete(true)
                }
                delay(900)
                onMain { onSuccess(outcome.syncDetail) }
            }
            is LoginOutcome.Failure -> onMain {
                onStage(outcome.stage)
                setStatus("Sign-in failed: ${outcome.message}")
                setError(true)
                setProcessing(false)
                onComplete(false)
            }
        }
    }
}
