// File: app/src/main/java/com/music/spotui/data/mapper/EntityToUIMapper.kt
package com.music.spotui.data.mapper

import com.music.spotui.data.entity.SongsModel
import com.music.spotui.data.models.TrackModel
import com.music.spotui.util.toLegacySong

/** Bridges clean persisted track models into the existing Compose player contract. */
object EntityToUIMapper {
    /** Converts [track] into the existing immutable player model. */
    fun trackToSong(track: TrackModel): SongsModel = track.toLegacySong()
}
