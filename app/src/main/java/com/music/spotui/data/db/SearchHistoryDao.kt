// File: app/src/main/java/com/music/spotui/data/db/SearchHistoryDao.kt
package com.music.spotui.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.music.spotui.data.models.SearchHistoryModel
import kotlinx.coroutines.flow.Flow

/** Room access for bounded local search history. */
@Dao
interface SearchHistoryDao {
    /** Streams the most recent [limit] normalized queries. */
    @Query("SELECT * FROM search_history ORDER BY lastSearchedAtEpochMs DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SearchHistoryModel>>

    /** Inserts or updates one search-history item. */
    @Upsert
    suspend fun upsert(item: SearchHistoryModel)

    /** Removes every persisted search query. */
    @Query("DELETE FROM search_history")
    suspend fun clear()

    /** Keeps only the most recent [limit] search-history records. */
    @Query(
        "DELETE FROM search_history WHERE normalizedQuery NOT IN " +
            "(SELECT normalizedQuery FROM search_history ORDER BY lastSearchedAtEpochMs DESC LIMIT :limit)",
    )
    suspend fun trimTo(limit: Int)
}
