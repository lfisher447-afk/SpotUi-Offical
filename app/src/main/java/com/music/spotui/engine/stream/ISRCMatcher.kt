// File: app/src/main/java/com/music/spotui/engine/stream/ISRCMatcher.kt
package com.music.spotui.engine.stream

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** Scores provider candidates against immutable track metadata without requiring an exact title match. */
@Singleton
class ISRCMatcher @Inject constructor() {
    /** Returns a 0..1 confidence score for a candidate track. */
    fun score(
        targetIsrc: String?,
        candidateIsrc: String?,
        targetTitle: String,
        candidateTitle: String,
        targetArtist: String,
        candidateArtist: String,
        targetDurationMs: Long,
        candidateDurationMs: Long,
    ): Float {
        if (!targetIsrc.isNullOrBlank() && targetIsrc.equals(candidateIsrc, ignoreCase = true)) return 1F
        val titleScore = similarity(targetTitle, candidateTitle)
        val artistScore = similarity(targetArtist, candidateArtist)
        val durationScore = when {
            targetDurationMs <= 0L || candidateDurationMs <= 0L -> 0.5F
            abs(targetDurationMs - candidateDurationMs) <= 2_000L -> 1F
            abs(targetDurationMs - candidateDurationMs) <= 5_000L -> 0.65F
            abs(targetDurationMs - candidateDurationMs) <= 12_000L -> 0.25F
            else -> 0F
        }
        return (titleScore * 0.5F + artistScore * 0.3F + durationScore * 0.2F).coerceIn(0F, 1F)
    }

    private fun similarity(first: String, second: String): Float {
        val left = first.normalize()
        val right = second.normalize()
        if (left.isBlank() || right.isBlank()) return 0F
        if (left == right) return 1F
        val shared = left.split(' ').intersect(right.split(' ').toSet()).size
        return (shared.toFloat() / maxOf(left.split(' ').size, right.split(' ').size)).coerceIn(0F, 1F)
    }

    private fun String.normalize(): String = lowercase()
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
