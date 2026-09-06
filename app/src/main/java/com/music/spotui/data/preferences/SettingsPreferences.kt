// File: app/src/main/java/com/music/spotui/data/preferences/SettingsPreferences.kt
package com.music.spotui.data.preferences

import android.content.SharedPreferences
import com.music.spotui.engine.stream.StreamQuality
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Typed SharedPreferences wrapper for settings owned by the clean architecture layer. */
@Singleton
class SettingsPreferences @Inject constructor(
    private val preferences: SharedPreferences,
) {
    /** Streams the selected stream-quality tier. */
    fun observeStreamQuality(): Flow<StreamQuality> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_STREAM_QUALITY) trySend(streamQuality())
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(streamQuality())
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /** Returns the current stream-quality tier. */
    fun streamQuality(): StreamQuality = preferences.getString(KEY_STREAM_QUALITY, StreamQuality.BALANCED.name)
        ?.let { value -> StreamQuality.entries.firstOrNull { it.name == value } }
        ?: StreamQuality.BALANCED

    /** Persists the requested stream [quality]. */
    fun setStreamQuality(quality: StreamQuality) {
        preferences.edit().putString(KEY_STREAM_QUALITY, quality.name).apply()
    }

    /** Returns whether explicit music is allowed. */
    fun isExplicitContentAllowed(): Boolean = preferences.getBoolean(KEY_EXPLICIT_CONTENT, true)

    /** Persists the explicit-content preference. */
    fun setExplicitContentAllowed(allowed: Boolean) {
        preferences.edit().putBoolean(KEY_EXPLICIT_CONTENT, allowed).apply()
    }

    private companion object {
        const val KEY_EXPLICIT_CONTENT = "explicit_content_allowed"
        const val KEY_STREAM_QUALITY = "stream_quality"
    }
}
