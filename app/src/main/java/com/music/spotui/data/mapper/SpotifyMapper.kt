// File: app/src/main/java/com/music/spotui/data/mapper/SpotifyMapper.kt
package com.music.spotui.data.mapper

import com.music.spotui.data.models.TrackModel
import com.music.spotui.data.remote.SpotifyTrackDto

/** Maps Spotify network DTOs to immutable local models. */
object SpotifyMapper {
    /** Converts this provider DTO to a local track suitable for Room persistence. */
    fun SpotifyTrackDto.toModel(nowEpochMs: Long = System.currentTimeMillis()): TrackModel = TrackModel(
        id = "spotify:$id",
        providerId = id,
        title = name,
        artistIds = artists.map { it.id },
        artistNames = artists.map { it.name },
        albumId = album?.id,
        albumName = album?.name.orEmpty(),
        artworkUrl = album?.images?.firstOrNull()?.url,
        durationMs = durationMs,
        explicit = explicit,
        isrc = externalIds["isrc"],
        addedAtEpochMs = nowEpochMs,
        updatedAtEpochMs = nowEpochMs,
    )
}
