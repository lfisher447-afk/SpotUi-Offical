package com.music.spotui.engine

/**
 * Version 1.5.1 - VolumeNormalizationInterceptor
 * Normalizes volume levels across tracks using ReplayGain headers to prevent sudden volume spikes.
 */
object VolumeNormalizationInterceptor {
    fun calculateNormalizedVolume(trackReplayGainDb: Float, targetLoudnessDb: Float = -14.0f): Float {
        val gainDiff = targetLoudnessDb - trackReplayGainDb
        // Convert dB diff to linear multiplier (10 ^ (dB / 20))
        return Math.pow(10.0, gainDiff / 20.0).toFloat().coerceIn(0.1f, 2.0f)
    }
}
