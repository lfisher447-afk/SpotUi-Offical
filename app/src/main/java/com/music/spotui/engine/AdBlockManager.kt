package com.music.spotui.engine

import android.util.Log

/**
 * Version 1.5.1 - AdBlockManager
 * Automatic audio-mute and skip-trigger engine that catches metadata flags 
 * for audio ads and silences playback instantly.
 */
object AdBlockManager {
    private const val TAG = "AdBlockManager"

    private val KNOWN_AD_KEYWORDS = listOf(
        "spotify audio ad",
        "sponsored",
        "advertisement",
        "promoted"
    )

    /**
     * Inspects a track title or metadata to determine if it is an ad.
     */
    fun isAdSegment(title: String, description: String = ""): Boolean {
        val combined = "$title $description".lowercase()
        return KNOWN_AD_KEYWORDS.any { combined.contains(it) }
    }

    /**
     * Intercepts playback metadata before it hits ExoPlayer to determine 
     * if the segment should be instantly skipped.
     */
    fun checkAndSkip(title: String, durationMs: Long): Boolean {
        // Ads are usually very short or match specific metadata signatures
        if (isAdSegment(title)) {
            Log.d(TAG, "Intercepted Ad based on metadata: $title. Skipping...")
            return true
        }
        
        // Failsafe: if an ad sneaks in with a typical un-skippable 15-30s duration 
        // without proper music metadata, we can flag it here. For now, strictly
        // relying on metadata keyword matches.
        return false
    }
}
