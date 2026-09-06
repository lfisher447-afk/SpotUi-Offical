package com.music.spotui.engine

import com.music.spotui.data.entity.SongsModel

/**
 * Version 1.5.1 - ArtistRadioEngine
 * Generates an engaging playback queue based on an artist's catalog and collaborative tracks.
 */
object ArtistRadioEngine {
    fun generateRadioQueue(
        seedArtistId: String,
        availableTracks: List<SongsModel>,
        seedArtistName: String = ""
    ): List<SongsModel> {
        if (availableTracks.isEmpty()) return emptyList()

        val normalizedArtistName = seedArtistName.trim().lowercase()

        // 1. Direct matches by artist ID or name
        val directMatches = availableTracks.filter { track ->
            (seedArtistId.isNotBlank() && track.artistIds.contains(seedArtistId)) ||
            (normalizedArtistName.isNotBlank() && track.singer.lowercase().contains(normalizedArtistName))
        }

        // 2. Discover related/collaborator tracks from the same album or co-artists
        val seedCollaboratorArtists = directMatches
            .flatMap { it.singer.split(",", "&", "feat.", "ft.").map { name -> name.trim().lowercase() } }
            .filter { it.isNotBlank() && it != normalizedArtistName }
            .toSet()

        val relatedMatches = if (seedCollaboratorArtists.isNotEmpty()) {
            availableTracks.filter { track ->
                !directMatches.contains(track) &&
                seedCollaboratorArtists.any { track.singer.lowercase().contains(it) }
            }
        } else {
            emptyList()
        }

        val combined = (directMatches + relatedMatches).distinctBy { it.id }
        val pool = if (combined.isNotEmpty()) combined else availableTracks

        val sanitized = QueueSanitizerWorker.sanitizeQueue(pool)
        val shuffled = TrueShuffleEngine.applyTrueShuffle(sanitized, 0)
        return shuffled.take(50)
    }
}
