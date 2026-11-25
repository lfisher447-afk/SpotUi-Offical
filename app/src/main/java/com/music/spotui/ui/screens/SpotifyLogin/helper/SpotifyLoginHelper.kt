package com.music.spotui.ui.screens.SpotifyLogin.helper

import android.content.Context

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
