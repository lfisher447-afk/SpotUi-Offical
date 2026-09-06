// File: app/src/main/java/com/music/spotui/data/models/AlbumModel.kt
package com.music.spotui.data.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Album metadata stored independently from its tracks. */
@Serializable
@Entity(
    tableName = "albums",
    indices = [Index(value = ["releaseDate"]), Index(value = ["updatedAtEpochMs"])],
)
data class AlbumModel(
    @PrimaryKey val id: String,
    val title: String,
    val artistIds: List<String> = emptyList(),
    val artistNames: List<String> = emptyList(),
    val artworkUrl: String? = null,
    val releaseDate: String? = null,
    val totalTracks: Int = 0,
    val albumType: String = "album",
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
)
