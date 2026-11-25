package com.music.spotui.ui.screens.SpotifyLogin.helper

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import com.metrolist.spotify.SpotifyAuth
import com.music.spotui.util.DevConsoleManager

internal const val LOGIN_SPOTIFY_GREEN = 0xFF1ED760
internal const val LOGIN_BACKGROUND_DARK = 0xFF0B0E14
internal const val LOGIN_CARD_DARK = 0xFF151924
internal const val LOGIN_TEXT_SECONDARY = 0xFFAAB2C4

enum class LoginUaProfile {
    SYSTEM_DEFAULT,
    MOBILE_CHROME,
    DESKTOP_CHROME
}

fun resolveLoginUserAgent(context: Context, profile: LoginUaProfile): String {
    return when (profile) {
        LoginUaProfile.SYSTEM_DEFAULT -> System.getProperty("http.agent") ?: "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36"
        LoginUaProfile.MOBILE_CHROME -> "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        LoginUaProfile.DESKTOP_CHROME -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }
}

fun parseSpotifyCookiesFromText(raw: String): Pair<String, String> {
    val trimmed = raw.trim()
    if (!trimmed.contains("=") && !trimmed.startsWith("[")) {
        return trimmed to ""
    }
    if (trimmed.startsWith("[")) {
        return runCatching {
            val arr = org.json.JSONArray(trimmed)
            var dc = ""
            var key = ""
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val name = o.optString("name")
                val value = o.optString("value")
                if (name == "sp_dc") dc = value
                if (name == "sp_key") key = value
            }
            dc to key
        }.getOrDefault("" to "")
    }
    if (trimmed.startsWith("{")) {
        return runCatching {
            val o = org.json.JSONObject(trimmed)
            val dc = o.optString("sp_dc", "")
            val key = o.optString("sp_key", "")
            dc to key
        }.getOrDefault("" to "")
    }

    val decoded = runCatching { java.net.URLDecoder.decode(trimmed, "UTF-8") }.getOrDefault(trimmed)
    var spDc = ""
    var spKey = ""
    decoded.split(";", "\n", "\t").forEach { part ->
        val kv = part.trim().split("=", limit = 2)
        if (kv.size == 2) {
            val k = kv[0].trim().substringAfterLast(' ')
            val v = kv[1].trim()
            if (k.equals("sp_dc", ignoreCase = true)) spDc = v
            if (k.equals("sp_key", ignoreCase = true)) spKey = v
        }
    }
    return spDc to spKey
}

fun redactSecrets(text: String): String {
    var result = text
    result = result.replace(Regex("sp_dc=[^;\\s]+"), "sp_dc=<redacted>")
    result = result.replace(Regex("sp_key=[^;\\s]+"), "sp_key=<redacted>")
    result = result.replace(Regex("Bearer\\s+[A-Za-z0-9._-]+"), "Bearer <redacted>")
    result = result.replace(Regex("AQ[A-Za-z0-9._-]{50,}"), "<redacted>")
    return result
}

fun looksLikeSpotifyCookiePayload(text: String): Boolean {
    val t = text.trim()
    if (t.contains("sp_dc=")) return true
    if (t.startsWith("AQ") && t.length >= 50) return true
    if (t.startsWith("[{") || t.startsWith("{\"")) return true
    return false
}

/**
 * Launches Spotify authentication in Chrome Custom Tabs (`androidx.browser:browser`) so
 * `document.requestStorageAccess()`, Google/Apple SSO, passkeys, and reCAPTCHA run natively
 * inside the real browser engine without WebView's `HTTP 400` block.
 * Safely falls back to `Intent.ACTION_VIEW` if no Custom Tabs provider is installed.
 */
internal fun openSpotifyInChromeCustomTab(
    context: Context,
    url: String = SpotifyAuth.LOGIN_URL,
): Boolean {
    val uri = Uri.parse(url)
    val darkToolbar = (0xFF0E1016).toInt()
    val customTabLaunched = runCatching {
        val colorScheme = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(darkToolbar)
            .setNavigationBarColor(darkToolbar)
            .build()
        val builder = CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(colorScheme)
            .setShowTitle(true)
            .setUrlBarHidingEnabled(false)
            .setShareState(CustomTabsIntent.SHARE_STATE_ON)
        val customTabsIntent = builder.build()
        if (context !is Activity) {
            customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        customTabsIntent.launchUrl(context, uri)
        DevConsoleManager.logAuth("Launched Chrome Custom Tabs for Spotify login")
        true
    }.getOrDefault(false)

    if (customTabLaunched) return true

    return runCatching {
        val viewIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(viewIntent)
        DevConsoleManager.logAuth("Launched external browser fallback for Spotify login")
        true
    }.getOrElse { err ->
        DevConsoleManager.logError("SpotifyLogin", "No browser available for Spotify login", err)
        Toast.makeText(context, "Unable to open Chrome or browser", Toast.LENGTH_SHORT).show()
        false
    }
}
