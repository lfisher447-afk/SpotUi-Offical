// File: app/src/main/java/com/music/spotui/data/models/SearchHistoryModel.kt
package com.music.spotui.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** One normalized query in the user's local search history. */
@Serializable
@Entity(tableName = "search_history")
data class SearchHistoryModel(
    @PrimaryKey val normalizedQuery: String,
    val query: String,
    val lastSearchedAtEpochMs: Long = System.currentTimeMillis(),
    val useCount: Int = 1,
)
