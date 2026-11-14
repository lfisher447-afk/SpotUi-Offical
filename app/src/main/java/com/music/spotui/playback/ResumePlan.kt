package com.music.spotui.playback

import com.music.spotui.data.entity.SongsModel

/**
 * Pure JVM-testable decision plan returned by [ResumePlanBuilder] for
 * `MediaLibrarySession.Callback.onPlaybackResumption` and widget playback start.
 */
data class ResumePlan(
    val queue: List<SongsModel>,
    val startIndex: Int,
    val positionMs: Long,
) {
    val startSong: SongsModel
        get() = queue[startIndex]
}

/**
 * Builds the playback queue to hand back to Media3's `onPlaybackResumption`
 * contract when a controller (Bluetooth, Android Auto, Media button, or Widget)
 * asks to resume playback after the process or service was stopped.
 *
 * Returns `null` from [buildFromPersistedQueue] when no restorable queue was
 * persisted so callers can decide whether to fall back to single-track resume.
 */
object ResumePlanBuilder {

    /**
     * Builds a [ResumePlan] strictly from a persisted [savedQueue].
     *
     * Returns `null` when [savedQueue] has no playable tracks so callers can fall back
     * to single-track resume via [buildWithFallback].
     */
    fun buildFromPersistedQueue(
        savedQueue: List<SongsModel>,
        lastSong: SongsModel? = null,
        lastPositionMs: Long = 0L,
        skipFirst: Boolean = false,
    ): ResumePlan? {
        val validQueue = savedQueue.filter { it.title.isNotBlank() && it.url.isNotBlank() }
        if (validQueue.isEmpty()) return null

        val matchedIndex = resolveMatchedIndex(validQueue, lastSong)
        val effectiveIndex = if (skipFirst) {
            (matchedIndex + 1) % validQueue.size
        } else {
            matchedIndex
        }
        val effectivePosition = if (skipFirst || (lastSong != null && effectiveIndex != matchedIndex)) {
            0L
        } else {
            lastPositionMs.coerceAtLeast(0L)
        }

        return ResumePlan(
            queue = validQueue,
            startIndex = effectiveIndex,
            positionMs = effectivePosition,
        )
    }

    /**
     * Resolves a full [ResumePlan] using [buildFromPersistedQueue] first and falling
     * back to single-track [lastSong] resume if no persisted queue exists.
     */
    fun buildWithFallback(
        savedQueue: List<SongsModel>,
        lastSong: SongsModel?,
        lastPositionMs: Long = 0L,
        skipFirst: Boolean = false,
    ): ResumePlan? {
        buildFromPersistedQueue(
            savedQueue = savedQueue,
            lastSong = lastSong,
            lastPositionMs = lastPositionMs,
            skipFirst = skipFirst,
        )?.let { return it }

        val validLast = lastSong?.takeIf { it.title.isNotBlank() && it.url.isNotBlank() } ?: return null
        return ResumePlan(
            queue = listOf(validLast),
            startIndex = 0,
            positionMs = if (skipFirst) 0L else lastPositionMs.coerceAtLeast(0L),
        )
    }

    private fun resolveMatchedIndex(queue: List<SongsModel>, lastSong: SongsModel?): Int {
        if (lastSong == null) return 0
        val byId = queue.indexOfFirst { lastSong.id > 0 && it.id == lastSong.id }
        if (byId >= 0) return byId
        val bySpotifyId = if (lastSong.spotifyTrackId.isNotBlank()) {
            queue.indexOfFirst { it.spotifyTrackId == lastSong.spotifyTrackId }
        } else -1
        if (bySpotifyId >= 0) return bySpotifyId
        val byUrl = queue.indexOfFirst { it.url == lastSong.url }
        return if (byUrl >= 0) byUrl else 0
    }
}
