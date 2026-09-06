// File: app/src/main/java/com/music/spotui/data/repository/LyricsRepository.kt
package com.music.spotui.data.repository

import com.music.spotui.data.db.LyricsDao
import com.music.spotui.data.models.LyricsModel
import com.music.spotui.util.Result
import com.music.spotui.util.runSuspendCatching
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Source of truth for cached plain and synchronised lyrics. */
interface LyricsRepository {
    /** Streams cached lyrics for [trackId]. */
    fun observeLyrics(trackId: String): Flow<LyricsModel?>

    /** Persists [lyrics]. */
    suspend fun save(lyrics: LyricsModel): Result<Unit>

    /** Removes cached lyrics for [trackId]. */
    suspend fun remove(trackId: String): Result<Unit>
}

/** Room-backed implementation of [LyricsRepository]. */
@Singleton
class LyricsRepositoryImpl @Inject constructor(
    private val lyricsDao: LyricsDao,
) : LyricsRepository {
    override fun observeLyrics(trackId: String): Flow<LyricsModel?> = lyricsDao.observeByTrackId(trackId)

    override suspend fun save(lyrics: LyricsModel): Result<Unit> =
        runSuspendCatching("Unable to cache lyrics") { lyricsDao.upsert(lyrics) }

    override suspend fun remove(trackId: String): Result<Unit> =
        runSuspendCatching("Unable to remove cached lyrics") { lyricsDao.deleteByTrackId(trackId) }
}
