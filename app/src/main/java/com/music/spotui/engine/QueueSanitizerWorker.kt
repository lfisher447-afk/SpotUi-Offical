package com.music.spotui.engine

import com.music.spotui.data.entity.SongsModel

/**
 * Version 1.5.1 - QueueSanitizerWorker
 * Background worker cleaning upcoming play queues by removing blacklisted songs before they reach the playback engine.
 */
object QueueSanitizerWorker {
    fun sanitizeQueue(queue: List<SongsModel>): List<SongsModel> {
        return queue.filterNot { song ->
            val trackIdentifier = song.spotifyTrackId.ifBlank { song.id.toString() }
            val firstArtistId = song.artistIds.split(",").firstOrNull()?.trim() ?: ""
            
            TrashBinManager.shouldSkip(trackIdentifier, firstArtistId) || 
            AdBlockManager.isAdSegment(song.title, song.singer)
        }
    }
}
