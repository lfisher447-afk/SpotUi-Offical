package com.music.spotui.data.api

import android.content.Context
import android.util.Log
import com.metrolist.spotify.Spotify
import com.metrolist.spotify.SpotifyAuth
import com.music.spotui.util.DevConsoleManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Ensures the [Spotify] singleton holds a valid internal web-player access token before
 * any `api-partner.spotify.com` GraphQL or `spclient.wg.spotify.com` metadata call.
 *
 * Exchanges the stored `sp_dc` (+ optional `sp_key`) cookie via [SpotifyAuth.fetchAccessToken]
 * (`open.spotify.com` `#session` extraction + `/api/server-time` TOTP `/api/token?reason=init`).
 * Exposes [isAuthenticated] so Guest/Authenticated transitions immediately update sync & UI state.
 */
object SpotifyTokenProvider {
    private const val TAG = "SpotifyTokenProvider"
    private val mutex = Mutex()

    @Volatile
    private var expiresAtMs: Long = 0L

    @Volatile
    private var loggedMissingCookieOnce = false

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    fun syncAuthState(context: Context): Boolean {
        val linked = SpotifySession.spDc(context.applicationContext).isNotBlank() ||
            (Spotify.accessToken != null && System.currentTimeMillis() < expiresAtMs)
        _isAuthenticated.value = linked
        return linked
    }

    fun seedVerifiedToken(accessToken: String, expiresAtTimestampMs: Long) {
        if (accessToken.isBlank()) return
        val wasGuest = !_isAuthenticated.value
        Spotify.accessToken = accessToken
        expiresAtMs = if (expiresAtTimestampMs > System.currentTimeMillis()) {
            expiresAtTimestampMs
        } else {
            System.currentTimeMillis() + 30 * 60_000L
        }
        loggedMissingCookieOnce = false
        _isAuthenticated.value = true
        if (wasGuest) {
            DevConsoleManager.logAuth("SpotifyTokenProvider transitioned from Guest -> Authenticated")
        }
    }

    fun clearSession() {
        Spotify.accessToken = null
        expiresAtMs = 0L
        loggedMissingCookieOnce = false
        _isAuthenticated.value = false
    }

    suspend fun ensureToken(context: Context): Boolean = mutex.withLock {
        val appCtx = context.applicationContext
        val now = System.currentTimeMillis()
        if (Spotify.accessToken != null && now < expiresAtMs - 60_000L) {
            _isAuthenticated.value = true
            return@withLock true
        }
        val spDc = SpotifySession.spDc(appCtx)
        val spKey = SpotifySession.spKey(appCtx)
        if (spDc.isBlank()) {
            Spotify.accessToken = null
            expiresAtMs = 0L
            _isAuthenticated.value = false
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
            "Refreshing Spotify web bearer token (sp_dc len=${spDc.length}, sp_key=${if (spKey.isNotBlank()) "present" else "none"})…",
        )
        SpotifyAuth.fetchAccessToken(spDc, spKey).fold(
            onSuccess = { token ->
                seedVerifiedToken(token.accessToken, token.accessTokenExpirationTimestampMs)
                val ttlSec = ((expiresAtMs - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
                Log.d(TAG, "Spotify token refreshed (ttl=${ttlSec}s)")
                DevConsoleManager.logInfo(
                    TAG,
                    "Spotify token refreshed cleanly (expires in ${ttlSec}s)",
                )
                true
            },
            onFailure = { err ->
                val statusCode = (err as? Spotify.SpotifyException)?.statusCode ?: 0
                if (statusCode == 401) {
                    Spotify.accessToken = null
                    expiresAtMs = 0L
                    _isAuthenticated.value = false
                }
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
                    autoFixHint = "Use Open Spotify in Chrome + Paste Cookie to refresh sp_dc.",
                    exception = err,
                )
                false
            },
        )
    }
}
