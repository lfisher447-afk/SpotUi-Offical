// File: app/src/main/java/com/music/spotui/util/UrlValidator.kt
package com.music.spotui.util

import android.net.Uri

/** Validates remote stream URLs before handing them to the media engine. */
object UrlValidator {
    /** Returns true only for absolute HTTPS URLs with a non-local host. */
    fun isSafeRemoteStreamUrl(value: String): Boolean {
        val uri = Uri.parse(value.trim())
        val host = uri.host?.lowercase().orEmpty()
        return uri.scheme.equals("https", ignoreCase = true) &&
            host.isNotBlank() &&
            host != "localhost" &&
            host != "127.0.0.1" &&
            host != "::1" &&
            uri.userInfo.isNullOrBlank()
    }

    /** Returns a trimmed safe URL, or null after logging an unsafe provider result. */
    fun sanitizeRemoteStreamUrl(value: String?): String? {
        val normalized = value?.trim().orEmpty()
        return normalized.takeIf(::isSafeRemoteStreamUrl) ?: run {
            if (normalized.isNotEmpty()) Logger.warning("UrlValidator", "Rejected unsafe stream URL")
            null
        }
    }
}
