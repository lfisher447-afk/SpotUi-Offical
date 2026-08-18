package com.music.spotui.engine

/**
 * Version 1.5.1 - IsrcMatcherUtils
 * Algorithm comparing song length, title text similarity, and ISRC codes to verify accurate audio stream matches.
 */
object IsrcMatcherUtils {
    fun calculateMatchScore(
        targetIsrc: String, candidateIsrc: String,
        targetTitle: String, candidateTitle: String,
        targetDurationMs: Long, candidateDurationMs: Long
    ): Float {
        if (targetIsrc.isNotBlank() && targetIsrc == candidateIsrc) return 1.0f // Perfect match
        
        var score = 0.0f
        
        // Title similarity (basic)
        if (targetTitle.equals(candidateTitle, ignoreCase = true)) {
            score += 0.5f
        } else if (candidateTitle.contains(targetTitle, ignoreCase = true) || targetTitle.contains(candidateTitle, ignoreCase = true)) {
            score += 0.3f
        }
        
        // Duration similarity
        val durationDiff = Math.abs(targetDurationMs - candidateDurationMs)
        if (durationDiff < 2000) { // within 2 seconds
            score += 0.5f
        } else if (durationDiff < 5000) { // within 5 seconds
            score += 0.2f
        }
        
        return score
    }
}
