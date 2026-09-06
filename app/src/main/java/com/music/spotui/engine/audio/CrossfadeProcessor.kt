// File: app/src/main/java/com/music/spotui/engine/audio/CrossfadeProcessor.kt
package com.music.spotui.engine.audio

import javax.inject.Inject
import javax.inject.Singleton

/** Computes equal-power gain envelopes for a transition between two tracks. */
@Singleton
class CrossfadeProcessor @Inject constructor() {
    /** Returns outgoing and incoming gains for [elapsedMs] of a [durationMs] crossfade. */
    fun gains(elapsedMs: Long, durationMs: Long): Pair<Float, Float> {
        if (durationMs <= 0L) return 0F to 1F
        val fraction = (elapsedMs.toDouble() / durationMs.toDouble()).coerceIn(0.0, 1.0)
        val incoming = kotlin.math.sin((fraction * Math.PI / 2.0)).toFloat()
        val outgoing = kotlin.math.cos((fraction * Math.PI / 2.0)).toFloat()
        return outgoing to incoming
    }
}
