package com.music.spotui.data.api

import android.content.Context

/**
 * Stores the logged-in Spotify `sp_dc` cookie used to mint web access tokens.
 *
 * Set it once (e.g. from a settings screen) with [setSpDc]. To get yours: log in
 * at open.spotify.com, open devtools → Application → Cookies → copy `sp_dc`.
 */
object SpotifySession {
    private const val PREFS = "spotify_session"
    private const val KEY_SP_DC = "sp_dc"
    private const val KEY_SP_KEY = "sp_key"

    // Optional compile-time default so the app has data on first launch.
    // Leave blank to require runtime configuration.
    private const val DEFAULT_SP_DC = ""

    fun spDc(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SP_DC, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_SP_DC
    }

    fun spKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SP_KEY, null)?.takeIf { it.isNotBlank() }.orEmpty()
    }

    fun hasSession(context: Context): Boolean = spDc(context).isNotBlank()

    fun setSpDc(context: Context, value: String, spKey: String = "") {
        val cleanedDc = value.trim()
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        editor.putString(KEY_SP_DC, cleanedDc)
        if (spKey.isNotBlank()) {
            editor.putString(KEY_SP_KEY, spKey.trim())
        } else if (cleanedDc.isBlank()) {
            editor.remove(KEY_SP_KEY)
        }
        editor.apply()
        if (cleanedDc.isBlank()) {
            SpotifyTokenProvider.clearSession()
        }
    }
}
