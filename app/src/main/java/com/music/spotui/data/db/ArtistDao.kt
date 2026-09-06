// File: app/src/main/java/com/music/spotui/data/db/ArtistDao.kt
package com.music.spotui.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.music.spotui.data.models.ArtistModel
import kotlinx.coroutines.flow.Flow

/** Room access for artist profiles. */
@Dao
interface ArtistDao {
    /** Streams artists alphabetically by display name. */
    @Query("SELECT * FROM artists ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ArtistModel>>

    /** Streams the artist identified by [artistId]. */
    @Query("SELECT * FROM artists WHERE id = :artistId LIMIT 1")
    fun observeById(artistId: String): Flow<ArtistModel?>

    /** Finds locally cached artists whose names contain [query]. */
    @Query("SELECT * FROM artists WHERE name LIKE '%' || :query || '%' ORDER BY name COLLATE NOCASE ASC")
    fun search(query: String): Flow<List<ArtistModel>>

    /** Inserts or replaces [artist]. */
    @Upsert
    suspend fun upsert(artist: ArtistModel)

    /** Inserts or replaces every value in [artists]. */
    @Upsert
    suspend fun upsertAll(artists: List<ArtistModel>)

    /** Removes the artist identified by [artistId]. */
    @Query("DELETE FROM artists WHERE id = :artistId")
    suspend fun deleteById(artistId: String)
}
