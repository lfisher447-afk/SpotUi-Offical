package com.music.spotui.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Version 1.5.1 - TrashBinEntity
 * Room database entity representing user-blacklisted tracks, artists, and album IDs.
 */
@Entity(tableName = "trash_bin_table")
data class TrashBinEntity(
    @PrimaryKey val id: String, // Can be trackId or artistId
    val type: String, // "track" or "artist"
    val addedAtMillis: Long = System.currentTimeMillis(),
    val name: String = "" // Optional human-readable name for UI
)
