package com.music.spotui.playback

import com.music.spotui.data.entity.SongsModel

/**
 * Pure logic for deriving the "Up Next" / Queue list shown in the UI.
 *
 * Background stream resolution may only partially populate ExoPlayer's internal timeline
 * (or skip unresolved items silently), whereas the user expects to see the logical queue they
 * chose whenever the actively playing song belongs to it — falling back to [timelineFallback]
 * only for single-track/restored sessions created outside normal queue setup.
 */
object LogicalQueueDisplay {

    fun compute(
        logicalQueue: List<SongsModel>,
        currentSongId: Int,
        currentSongUrl: String = "",
        timelineFallback: List<SongsModel> = emptyList(),
    ): List<SongsModel> {
        if (logicalQueue.isEmpty()) return timelineFallback

        val activeInLogical = logicalQueue.any { track ->
            (currentSongId > 0 && track.id == currentSongId) ||
                (currentSongUrl.isNotBlank() && track.url == currentSongUrl)
        }
        return if (activeInLogical) logicalQueue else timelineFallback.ifEmpty { logicalQueue }
    }
}
