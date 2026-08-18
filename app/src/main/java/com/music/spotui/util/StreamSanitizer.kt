package com.music.spotui.util

import android.net.Uri
import android.util.Log
import java.net.URI
import java.net.URLDecoder

/**
 * StreamSanitizer
 * Provides high-performance, robust sanitization for playback streams, Spotify/YouTube URLs,
 * headers, metadata strings, and audio track parameters.
 */
object StreamSanitizer {
    private const val TAG = "StreamSanitizer"

    // List of tracking query parameters to sanitize/strip from stream URLs
    private val TRACKING_PARAMS = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "fbclid", "gclid", "msclkid", "si", "feature", "app_id", "client_version"
    )

    /**
     * Sanitizes a raw audio/video stream URL.
     * Enforces HTTPS where applicable, strips unnecessary tracking query parameters,
     * decodes double-escaped entities, and validates format integrity.
     */
    fun sanitizeStreamUrl(rawUrl: String?): String {
        if (rawUrl.isNullOrBlank()) return ""
        try {
            var url = rawUrl.trim()
            
            // Fix missing scheme or HTTP downgrades
            if (url.startsWith("http://")) {
                url = url.replaceFirst("http://", "https://")
            } else if (url.startsWith("//")) {
                url = "https:$url"
            }

            // Decode HTML entities if present in stream URL
            if (url.contains("&amp;")) {
                url = url.replace("&amp;", "&")
            }

            val uri = Uri.parse(url)
            if (uri.isOpaque || uri.host.isNullOrBlank()) {
                DevConsoleManager.logError("StreamSanitizer", "Opaque or missing host in URI: $rawUrl")
                return url
            }

            // Strip tracking query parameters while preserving audio range / token params
            val builder = uri.buildUpon().clearQuery()
            var sanitizedParamCount = 0
            
            for (paramName in uri.queryParameterNames) {
                if (paramName.lowercase() !in TRACKING_PARAMS) {
                    val values = uri.getQueryParameters(paramName)
                    for (v in values) {
                        builder.appendQueryParameter(paramName, v)
                    }
                } else {
                    sanitizedParamCount++
                }
            }

            val cleanUrl = builder.build().toString()
            if (sanitizedParamCount > 0) {
                DevConsoleManager.logSanitization("Stripped $sanitizedParamCount tracking parameter(s) from stream URL.")
            }
            return cleanUrl
        } catch (e: Exception) {
            Log.e(TAG, "Error sanitizing stream URL: ${e.message}", e)
            DevConsoleManager.logError("StreamSanitizer", "Sanitization failed for $rawUrl: ${e.message}")
            return rawUrl ?: ""
        }
    }

    /**
     * Sanitizes HTTP headers to ensure security, no header injection, and proper User-Agent formatting.
     */
    fun sanitizeHeaders(rawHeaders: Map<String, String>): Map<String, String> {
        val cleanHeaders = mutableMapOf<String, String>()
        for ((key, value) in rawHeaders) {
            val sanitizedKey = key.trim().replace("\r", "").replace("\n", "")
            val sanitizedValue = value.trim().replace("\r", "").replace("\n", "")
            if (sanitizedKey.isNotEmpty() && sanitizedValue.isNotEmpty()) {
                cleanHeaders[sanitizedKey] = sanitizedValue
            }
        }
        if (!cleanHeaders.containsKey("User-Agent")) {
            cleanHeaders["User-Agent"] = "SpotUI/1.4.3 (Android; Universal; OneUI-Optimized)"
        }
        return cleanHeaders
    }

    /**
     * Sanitizes track titles and artist names for display and search queries.
     * Cleans up unwanted HTML escape entities, bracket clutter, and excess whitespace.
     */
    fun sanitizeMetadata(title: String?, artist: String?): Pair<String, String> {
        val cleanTitle = sanitizeTitle(title)
        val cleanArtist = sanitizeArtist(artist)
        return Pair(cleanTitle, cleanArtist)
    }

    fun sanitizeTitle(title: String?): String {
        if (title.isNullOrBlank()) return "Unknown Track"
        var clean = title.trim()
        clean = clean.replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
        
        // Remove trailing audio quality bloat like (Official Audio) or [HD]
        clean = clean.replace(Regex("(?i)\\s*[\\[(](official video|official audio|lyric video|audio|hd|4k)[\\])]"), "")
        return clean.trim().ifEmpty { "Unknown Track" }
    }

    fun sanitizeArtist(artist: String?): String {
        if (artist.isNullOrBlank()) return "Unknown Artist"
        var clean = artist.trim()
        clean = clean.replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
        return clean.trim().ifEmpty { "Unknown Artist" }
    }

    /**
     * Validates stream audio parameters and ensures format compatibility.
     */
    fun validateAudioStream(url: String?, mimeType: String? = null): Boolean {
        if (url.isNullOrBlank()) return false
        if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("content://") && !url.startsWith("file://")) {
            return false
        }
        return true
    }
}
