package com.music.spotui.ui.screens.SpotifyLogin.helper

import android.content.Context
import com.metrolist.spotify.Spotify
import com.music.spotui.data.api.SpotifySession
import com.music.spotui.data.preferences.getDeezerArl
import com.music.spotui.data.preferences.isYoutubeLoggedIn
import com.music.spotui.ui.screens.SpotifyOAuth

data class AuthEcosystemStatus(
    val spotifyCookiePresent: Boolean = false,
    val spotifyCookiePreview: String = "",
    val spotifyBearerReady: Boolean = false,
    val oauthSignedIn: Boolean = false,
    val oauthClientIdSet: Boolean = false,
    val youtubeLoggedIn: Boolean = false,
    val deezerArlSet: Boolean = false,
)

enum class CookieValidationResult {
    VALID,
    EMPTY,
    TOO_SHORT,
    WRONG_FIELD_TYPE,
}

/**
 * Inspects multi-platform authentication health (Spotify Cookie, Spotify OAuth PKCE,
 * YouTube Music cookies, and Deezer ARL) and validates pasted cookie payloads cleanly.
 */
object LoginSessionInspector {

    fun inspect(context: Context): AuthEcosystemStatus {
        val app = context.applicationContext
        val spDc = SpotifySession.spDc(app)
        val hasCookie = spDc.isNotBlank()
        val preview = if (!hasCookie) {
            "Not signed in"
        } else if (spDc.length > 14) {
            "${spDc.take(6)}••••${spDc.takeLast(4)}"
        } else {
            "Session saved"
        }

        return AuthEcosystemStatus(
            spotifyCookiePresent = hasCookie,
            spotifyCookiePreview = preview,
            spotifyBearerReady = !Spotify.accessToken.isNullOrBlank(),
            oauthSignedIn = SpotifyOAuth.isSignedIn(app),
            oauthClientIdSet = SpotifyOAuth.isValidClientId(SpotifyOAuth.clientId(app)),
            youtubeLoggedIn = isYoutubeLoggedIn(app),
            deezerArlSet = getDeezerArl(app).isNotBlank(),
        )
    }

    fun validateSpotifySpDc(rawInput: String): CookieValidationResult {
        val (spDc, _) = parseSpotifyCookiesFromText(rawInput)
        val clean = spDc.trim()
        if (clean.isEmpty()) return CookieValidationResult.EMPTY
        if (clean.startsWith("http://") || clean.startsWith("https://")) {
            return CookieValidationResult.WRONG_FIELD_TYPE
        }
        if (clean.length < 24) return CookieValidationResult.TOO_SHORT
        return CookieValidationResult.VALID
    }

    fun validationHint(result: CookieValidationResult): String = when (result) {
        CookieValidationResult.VALID -> "Valid sp_dc format detected"
        CookieValidationResult.EMPTY -> "Paste sp_dc value, Cookie header, or Cookie-Editor JSON"
        CookieValidationResult.TOO_SHORT -> "sp_dc cookie is too short — make sure you copied the full value"
        CookieValidationResult.WRONG_FIELD_TYPE -> "That looks like a URL rather than a Spotify sp_dc cookie"
    }
}
