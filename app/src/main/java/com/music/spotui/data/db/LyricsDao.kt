// File: app/src/main/java/com/music/spotui/data/db/LyricsDao.kt
package com.music.spotui.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.music.spotui.data.models.LyricsModel
import kotlinx.coroutines.flow.Flow

/** Room access for cached lyrics. */
@Dao
interface LyricsDao {
    /** Streams cached lyrics for [trackId]. */
    @Query("SELECT * FROM lyrics WHERE trackId = :trackId LIMIT 1")
    fun observeByTrackId(trackId: String): Flow<LyricsModel?>

    /** Returns cached lyrics for [trackId]. */
    @Query("SELECT * FROM lyrics WHERE trackId = :trackId LIMIT 1")
    suspend fun getByTrackId(trackId: String): LyricsModel?

    /** Inserts or replaces [lyrics]. */
    @Upsert
    suspend fun upsert(lyrics: LyricsModel)

    /** Removes cached lyrics for [trackId]. */
    @Query("DELETE FROM lyrics WHERE trackId = :trackId")
    suspend fun deleteByTrackId(trackId: String)
}
