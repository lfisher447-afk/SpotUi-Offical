package com.metrolist.spotify

/**
 * SpotifyStreamSanitizer
 * Dedicated URL, header, and parameter sanitizer within the Spotify module.
 * Cleans tracking parameters, handles URL escaping, and validates audio URLs.
 */
object SpotifyStreamSanitizer {

    private val TRACKING_PARAMS = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "fbclid", "gclid", "msclkid", "si", "feature", "app_id", "client_version"
    )

    fun sanitizeUrl(rawUrl: String?): String {
        if (rawUrl.isNullOrBlank()) return ""
        try {
            var url = rawUrl.trim()
            if (url.startsWith("http://")) {
                url = url.replaceFirst("http://", "https://")
            } else if (url.startsWith("//")) {
                url = "https:$url"
            }

            if (url.contains("&amp;")) {
                url = url.replace("&amp;", "&")
            }

            // If query params exist, strip known tracking parameters
            if (url.contains("?") && !url.startsWith("data:")) {
                val base = url.substringBefore("?")
                val queryString = url.substringAfter("?")
                val cleanParams = queryString.split("&")
                    .filter { param ->
                        val key = param.substringBefore("=").lowercase()
                        key !in TRACKING_PARAMS
                    }
                return if (cleanParams.isNotEmpty()) {
                    "$base?${cleanParams.joinToString("&")}"
                } else {
                    base
                }
            }
            return url
        } catch (e: Exception) {
            return rawUrl ?: ""
        }
    }

    fun sanitizeTitle(title: String?): String {
        if (title.isNullOrBlank()) return "Unknown Track"
        return title.trim()
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("(?i)\\s*[\\[(](official video|official audio|lyric video|audio|hd|4k)[\\])]"), "")
            .trim()
    }
}
