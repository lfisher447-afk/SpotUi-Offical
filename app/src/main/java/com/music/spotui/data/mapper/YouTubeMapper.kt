// File: app/src/main/java/com/music/spotui/data/mapper/YouTubeMapper.kt
package com.music.spotui.data.mapper

import com.music.spotui.data.models.TrackModel
import com.music.spotui.data.remote.YouTubeTrackDto

/** Maps InnerTube candidates to provider-neutral local track models. */
object YouTubeMapper {
    /** Converts this candidate to a local track with a safe stable playback link. */
    fun YouTubeTrackDto.toModel(nowEpochMs: Long = System.currentTimeMillis()): TrackModel = TrackModel(
        id = "youtube:$id",
        providerId = id,
        title = title,
        artistNames = artistNames,
        albumName = albumName,
        artworkUrl = artworkUrl,
        streamUrl = "https://music.youtube.com/watch?v=$id",
        durationMs = durationMs,
        explicit = explicit,
        addedAtEpochMs = nowEpochMs,
        updatedAtEpochMs = nowEpochMs,
    )
}
