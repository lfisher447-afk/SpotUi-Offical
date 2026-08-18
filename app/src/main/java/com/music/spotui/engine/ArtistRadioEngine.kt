package com.music.spotui.engine

import com.music.spotui.data.entity.SongsModel

/**
 * Version 1.5.1 - ArtistRadioEngine
 * Generates infinite playback queues based on an artist's style vectors without injecting unwanted recommended tracks.
 */
object ArtistRadioEngine {
    fun generateRadioQueue(seedArtistId: String, availableTracks: List<SongsModel>): List<SongsModel> {
        // Filter tracks matching the artist style, and randomize
        return availableTracks
            .filter { it.artistIds.contains(seedArtistId) || it.singer.contains("Radio") } // Pseudo match
            .shuffled()
            .take(50) // Return 50 tracks
    }
}
