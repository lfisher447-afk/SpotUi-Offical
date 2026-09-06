// File: app/src/main/java/com/music/spotui/engine/audio/SpatialProcessor.kt
package com.music.spotui.engine.audio

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** Performs conservative stereo widening over interleaved PCM-16 audio frames. */
@Singleton
class SpatialProcessor @Inject constructor() {
    /** Widens stereo PCM data by [width] while preserving mono compatibility. */
    fun widenPcm16(interleavedStereo: ShortArray, width: Float): ShortArray {
        val amount = width.coerceIn(0F, 1F)
        if (amount == 0F || interleavedStereo.size < 2) return interleavedStereo.copyOf()
        return interleavedStereo.copyOf().also { output ->
            var index = 0
            while (index + 1 < output.size) {
                val left = output[index].toFloat() / Short.MAX_VALUE
                val right = output[index + 1].toFloat() / Short.MAX_VALUE
                val mid = (left + right) * 0.5F
                val side = (left - right) * 0.5F * (1F + amount * 0.8F)
                output[index] = ((mid + side).coerceIn(-1F, 1F) * Short.MAX_VALUE).roundToInt().toShort()
                output[index + 1] = ((mid - side).coerceIn(-1F, 1F) * Short.MAX_VALUE).roundToInt().toShort()
                index += 2
            }
        }
    }
}
