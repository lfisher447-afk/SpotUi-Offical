// File: app/src/main/java/com/music/spotui/util/TimeFormatter.kt
package com.music.spotui.util

import java.util.Locale

/** Formats playback durations without leaking time-math into composables. */
object TimeFormatter {
    /** Formats [milliseconds] as m:ss or h:mm:ss, clamping negative values to zero. */
    fun duration(milliseconds: Long): String {
        val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1_000L)
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
        }
    }

    /** Formats an elapsed position and known duration as a compact playback label. */
    fun progress(positionMs: Long, durationMs: Long): String = "${duration(positionMs)} / ${duration(durationMs)}"
}
