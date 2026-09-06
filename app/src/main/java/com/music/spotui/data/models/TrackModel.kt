// File: app/src/main/java/com/music/spotui/data/models/TrackModel.kt
package com.music.spotui.data.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** A persistable, provider-neutral music track. */
@Serializable
@Entity(
    tableName = "tracks",
    indices = [Index(value = ["albumId"]), Index(value = ["updatedAtEpochMs"])],
)
data class TrackModel(
    @PrimaryKey val id: String,
    val providerId: String = "",
    val title: String,
    val artistIds: List<String> = emptyList(),
    val artistNames: List<String> = emptyList(),
    val albumId: String? = null,
    val albumName: String = "",
    val artworkUrl: String? = null,
    val streamUrl: String? = null,
    val durationMs: Long = 0L,
    val explicit: Boolean = false,
    val isrc: String? = null,
    val addedAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
)
