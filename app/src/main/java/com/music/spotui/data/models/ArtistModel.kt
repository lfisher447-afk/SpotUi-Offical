// File: app/src/main/java/com/music/spotui/data/models/ArtistModel.kt
package com.music.spotui.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Artist profile metadata for library and discovery surfaces. */
@Serializable
@Entity(tableName = "artists")
data class ArtistModel(
    @PrimaryKey val id: String,
    val name: String,
    val artworkUrl: String? = null,
    val biography: String? = null,
    val followerCount: Long? = null,
    val genres: List<String> = emptyList(),
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
)
