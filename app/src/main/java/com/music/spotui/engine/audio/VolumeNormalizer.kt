// File: app/src/main/java/com/music/spotui/engine/audio/VolumeNormalizer.kt
package com.music.spotui.engine.audio

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow
import kotlin.math.roundToInt

/** Applies bounded ReplayGain-style gain correction without clipping PCM samples. */
@Singleton
class VolumeNormalizer @Inject constructor() {
    /** Calculates gain needed to move [trackLufs] toward [targetLufs]. */
    fun replayGainDb(trackLufs: Float, targetLufs: Float = -14F): Float =
        (targetLufs - trackLufs).coerceIn(-12F, 12F)

    /** Applies a gain expressed in decibels to PCM-16 samples with hard clip protection. */
    fun applyPcm16(samples: ShortArray, gainDb: Float): ShortArray {
        val multiplier = 10.0.pow(gainDb.coerceIn(-12F, 12F) / 20.0).toFloat()
        return samples.map { sample ->
            (sample * multiplier).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }.toShortArray()
    }
}
