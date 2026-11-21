package com.music.spotui.data.api

import android.content.Context
import android.util.Log
import com.metrolist.spotify.Spotify
import com.metrolist.spotify.SpotifyAuth
import com.music.spotui.util.DevConsoleManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Ensures the [Spotify] singleton holds a valid web access token before any
 * metadata call. Mirrors how Meld authenticates: a logged-in `sp_dc` cookie is
 * exchanged for a short-lived bearer token via [SpotifyAuth.fetchAccessToken].
 *
 * The `sp_dc` cookie is read from [SpotifySession]; authenticate via
 * [com.music.spotui.ui.screens.SpotifyLoginScreen] to enable real Spotify data.
 * Tokens are cached until ~1 minute before expiry and refreshed on demand.
 */
object SpotifyTokenProvider {
    private const val TAG = "SpotifyTokenProvider"
    private val mutex = Mutex()
    private var expiresAtMs: Long = 0L

    @Volatile
    private var loggedMissingCookieOnce = false

    suspend fun ensureToken(context: Context): Boolean = mutex.withLock {
        val now = System.currentTimeMillis()
        if (Spotify.accessToken != null && now < expiresAtMs - 60_000L) {
            return@withLock true
        }
        val spDc = SpotifySession.spDc(context)
        if (spDc.isBlank()) {
            Log.w(TAG, "No sp_dc cookie set — Spotify data unavailable")
            if (!loggedMissingCookieOnce) {
                loggedMissingCookieOnce = true
                DevConsoleManager.logWarning(
                    TAG,
                    "No sp_dc cookie stored (running in Guest/unauthenticated mode)",
                )
            }
            return@withLock false
        }
        loggedMissingCookieOnce = false
        DevConsoleManager.logInfo(
            TAG,
            "Refreshing Spotify web bearer token (sp_dc len=${spDc.length})…",
        )
        SpotifyAuth.fetchAccessToken(spDc).fold(
            onSuccess = { token ->
                Spotify.accessToken = token.accessToken
                expiresAtMs = token.accessTokenExpirationTimestampMs
                val ttlSec = ((expiresAtMs - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
                Log.d(TAG, "Spotify token refreshed (ttl=${ttlSec}s)")
                DevConsoleManager.logInfo(
                    TAG,
                    "Spotify token refreshed cleanly (expires in ${ttlSec}s)",
                )
                true
            },
            onFailure = { err ->
                val msg = err.message ?: err.javaClass.simpleName
                Log.e(TAG, "Failed to fetch Spotify token: $msg", err)
                DevConsoleManager.logError(
                    TAG,
                    "Token exchange failed: $msg",
                    err,
                )
                DevConsoleManager.reportComponentFault(
                    component = "SpotifyAuth",
                    action = "EnsureToken",
                    reason = "Spotify session token exchange failed: $msg",
                    autoFixHint = "Open Sign In -> Web Browser or re-import sp_dc cookie if expired.",
                    exception = err,
                )
                false
            },
        )
    }
}
