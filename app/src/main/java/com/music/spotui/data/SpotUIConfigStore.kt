package com.music.spotui.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Version 1.5.1 - SpotUIConfigStore
 * Key-value storage repository persisting user preferences for ads, shuffle mode, and lyrics.
 */
class SpotUIConfigStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("spotui_config_store", Context.MODE_PRIVATE)

    var isPodcastFilterEnabled: Boolean
        get() = prefs.getBoolean("podcast_filter", false)
        set(value) = prefs.edit().putBoolean("podcast_filter", value).apply()
        
    var isExplicitContentBlocked: Boolean
        get() = prefs.getBoolean("block_explicit", false)
        set(value) = prefs.edit().putBoolean("block_explicit", value).apply()

    var crossfadeDurationSeconds: Int
        get() = prefs.getInt("crossfade_sec", 0)
        set(value) = prefs.edit().putInt("crossfade_sec", value).apply()
        
    var forceMaxBitrate: Boolean
        get() = prefs.getBoolean("max_bitrate", true)
        set(value) = prefs.edit().putBoolean("max_bitrate", value).apply()
}
