// File: app/src/main/java/com/music/spotui/data/db/AlbumDao.kt
package com.music.spotui.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.music.spotui.data.models.AlbumModel
import kotlinx.coroutines.flow.Flow

/** Room access for album metadata. */
@Dao
interface AlbumDao {
    /** Streams albums ordered by their most recent update. */
    @Query("SELECT * FROM albums ORDER BY updatedAtEpochMs DESC")
    fun observeAll(): Flow<List<AlbumModel>>

    /** Streams the album identified by [albumId]. */
    @Query("SELECT * FROM albums WHERE id = :albumId LIMIT 1")
    fun observeById(albumId: String): Flow<AlbumModel?>

    /** Inserts or replaces [album]. */
    @Upsert
    suspend fun upsert(album: AlbumModel)

    /** Inserts or replaces every value in [albums]. */
    @Upsert
    suspend fun upsertAll(albums: List<AlbumModel>)

    /** Removes the album identified by [albumId]. */
    @Query("DELETE FROM albums WHERE id = :albumId")
    suspend fun deleteById(albumId: String)
}
