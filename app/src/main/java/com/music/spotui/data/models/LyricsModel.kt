// File: app/src/main/java/com/music/spotui/data/models/LyricsModel.kt
package com.music.spotui.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Cached plain and time-synchronised lyrics for a single track. */
@Serializable
@Entity(tableName = "lyrics")
data class LyricsModel(
    @PrimaryKey val trackId: String,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
    val source: String = "LRCLIB",
    val languageCode: String? = null,
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
)
