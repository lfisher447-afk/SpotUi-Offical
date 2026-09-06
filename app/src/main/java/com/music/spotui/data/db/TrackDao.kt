// File: app/src/main/java/com/music/spotui/data/db/TrackDao.kt
package com.music.spotui.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.music.spotui.data.models.TrackModel
import kotlinx.coroutines.flow.Flow

/** Room access for the local track catalog. */
@Dao
interface TrackDao {
    /** Streams all saved tracks in most-recently-updated order. */
    @Query("SELECT * FROM tracks ORDER BY updatedAtEpochMs DESC")
    fun observeAll(): Flow<List<TrackModel>>

    /** Streams the track with [trackId], or null when it is absent. */
    @Query("SELECT * FROM tracks WHERE id = :trackId LIMIT 1")
    fun observeById(trackId: String): Flow<TrackModel?>

    /** Returns the track with [trackId], or null when it is absent. */
    @Query("SELECT * FROM tracks WHERE id = :trackId LIMIT 1")
    suspend fun getById(trackId: String): TrackModel?

    /** Streams tracks matching a title, artist, or album fragment. */
    @Query(
        "SELECT * FROM tracks WHERE title LIKE '%' || :query || '%' " +
            "OR artistNames LIKE '%' || :query || '%' " +
            "OR albumName LIKE '%' || :query || '%' ORDER BY updatedAtEpochMs DESC",
    )
    fun search(query: String): Flow<List<TrackModel>>

    /** Inserts or replaces each track by its stable provider-neutral identifier. */
    @Upsert
    suspend fun upsertAll(tracks: List<TrackModel>)

    /** Inserts or replaces [track] by its stable provider-neutral identifier. */
    @Upsert
    suspend fun upsert(track: TrackModel)

    /** Removes the local catalog entry for [trackId]. */
    @Query("DELETE FROM tracks WHERE id = :trackId")
    suspend fun deleteById(trackId: String)

    /** Removes every track from the local catalog. */
    @Query("DELETE FROM tracks")
    suspend fun clear()
}
