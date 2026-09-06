// File: app/src/main/java/com/music/spotui/data/db/DownloadDao.kt
package com.music.spotui.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.music.spotui.data.models.DownloadModel
import com.music.spotui.data.models.DownloadState
import kotlinx.coroutines.flow.Flow

/** Room access for app-owned download state. */
@Dao
interface DownloadDao {
    /** Streams every persisted download with newest state first. */
    @Query("SELECT * FROM downloads ORDER BY updatedAtEpochMs DESC")
    fun observeAll(): Flow<List<DownloadModel>>

    /** Streams every download currently in [state]. */
    @Query("SELECT * FROM downloads WHERE state = :state ORDER BY updatedAtEpochMs DESC")
    fun observeByState(state: DownloadState): Flow<List<DownloadModel>>

    /** Streams the download state for [trackId]. */
    @Query("SELECT * FROM downloads WHERE trackId = :trackId LIMIT 1")
    fun observeByTrackId(trackId: String): Flow<DownloadModel?>

    /** Returns the download state for [trackId]. */
    @Query("SELECT * FROM downloads WHERE trackId = :trackId LIMIT 1")
    suspend fun getByTrackId(trackId: String): DownloadModel?

    /** Inserts or replaces [download]. */
    @Upsert
    suspend fun upsert(download: DownloadModel)

    /** Removes the persisted state for [trackId]. */
    @Query("DELETE FROM downloads WHERE trackId = :trackId")
    suspend fun deleteByTrackId(trackId: String)
}
