package com.music.spotui.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.music.spotui.data.entity.TrashBinEntity
import kotlinx.coroutines.flow.Flow

/**
 * Version 1.5.1 - TrashBinDao
 * Data access object handling CRUD operations for blacklisted track IDs and artist metadata.
 */
@Dao
interface TrashBinDao {
    @Query("SELECT * FROM trash_bin_table WHERE type = :type")
    fun getBlacklistedByType(type: String): Flow<List<TrashBinEntity>>

    @Query("SELECT * FROM trash_bin_table WHERE id = :id LIMIT 1")
    suspend fun getBlacklistedItem(id: String): TrashBinEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: TrashBinEntity)

    @Query("DELETE FROM trash_bin_table WHERE id = :id")
    suspend fun deleteItem(id: String)
    
    @Query("SELECT id FROM trash_bin_table WHERE type = 'track'")
    suspend fun getAllBlacklistedTrackIds(): List<String>
    
    @Query("SELECT id FROM trash_bin_table WHERE type = 'artist'")
    suspend fun getAllBlacklistedArtistIds(): List<String>
}
