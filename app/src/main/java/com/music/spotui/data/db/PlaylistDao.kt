// File: app/src/main/java/com/music/spotui/data/db/PlaylistDao.kt
package com.music.spotui.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.music.spotui.data.models.PlaylistModel
import kotlinx.coroutines.flow.Flow

/** Room access for local and remotely synchronized playlists. */
@Dao
interface PlaylistDao {
    /** Streams playlists with pinned collections first. */
    @Query("SELECT * FROM playlists ORDER BY isPinned DESC, updatedAtEpochMs DESC")
    fun observeAll(): Flow<List<PlaylistModel>>

    /** Streams the playlist identified by [playlistId]. */
    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    fun observeById(playlistId: String): Flow<PlaylistModel?>

    /** Returns the playlist identified by [playlistId]. */
    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    suspend fun getById(playlistId: String): PlaylistModel?

    /** Inserts or replaces [playlist]. */
    @Upsert
    suspend fun upsert(playlist: PlaylistModel)

    /** Inserts or replaces every value in [playlists]. */
    @Upsert
    suspend fun upsertAll(playlists: List<PlaylistModel>)

    /** Removes the playlist identified by [playlistId]. */
    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deleteById(playlistId: String)
}
