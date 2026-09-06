// File: app/src/main/java/com/music/spotui/util/Constants.kt
package com.music.spotui.util

/** Stable app-wide configuration values that do not belong in UI code. */
object Constants {
    /** Room database filename for the clean data layer. */
    const val DATABASE_NAME = "spotui.db"

    /** Shared-preferences filename for user-controlled settings. */
    const val PREFERENCES_NAME = "spotui_preferences"

    /** Maximum local search-history records retained on device. */
    const val MAX_SEARCH_HISTORY = 50

    /** Default network request timeout in milliseconds. */
    const val NETWORK_TIMEOUT_MS = 20_000L

    /** Bounded number of download retries requested from WorkManager. */
    const val MAX_DOWNLOAD_ATTEMPTS = 3

    /** Canonical LRCLIB endpoint used by the lyrics provider. */
    const val LRCLIB_BASE_URL = "https://lrclib.net/"
}
