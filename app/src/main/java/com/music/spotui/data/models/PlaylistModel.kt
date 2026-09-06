// File: app/src/main/java/com/music/spotui/data/models/PlaylistModel.kt
package com.music.spotui.data.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** A user-visible playlist and its stable ordered list of track identifiers. */
@Serializable
@Entity(
    tableName = "playlists",
    indices = [Index(value = ["ownerId"]), Index(value = ["updatedAtEpochMs"])],
)
data class PlaylistModel(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val ownerId: String? = null,
    val artworkUrl: String? = null,
    val trackIds: List<String> = emptyList(),
    val isCollaborative: Boolean = false,
    val isPinned: Boolean = false,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
)
