// File: app/src/main/java/com/music/spotui/data/repository/ArtistRepository.kt
package com.music.spotui.data.repository

import com.music.spotui.data.db.ArtistDao
import com.music.spotui.data.models.ArtistModel
import com.music.spotui.util.Result
import com.music.spotui.util.runSuspendCatching
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Source of truth for locally cached artist profiles. */
interface ArtistRepository {
    /** Streams cached artists. */
    fun observeArtists(): Flow<List<ArtistModel>>

    /** Searches cached artist names. */
    fun searchCached(query: String): Flow<List<ArtistModel>>

    /** Persists [artist]. */
    suspend fun save(artist: ArtistModel): Result<Unit>

    /** Persists [artists]. */
    suspend fun saveAll(artists: List<ArtistModel>): Result<Unit>
}

/** Room-backed implementation of [ArtistRepository]. */
@Singleton
class ArtistRepositoryImpl @Inject constructor(
    private val artistDao: ArtistDao,
) : ArtistRepository {
    override fun observeArtists(): Flow<List<ArtistModel>> = artistDao.observeAll()

    override fun searchCached(query: String): Flow<List<ArtistModel>> = artistDao.search(query.trim())

    override suspend fun save(artist: ArtistModel): Result<Unit> =
        runSuspendCatching("Unable to save artist") { artistDao.upsert(artist) }

    override suspend fun saveAll(artists: List<ArtistModel>): Result<Unit> =
        runSuspendCatching("Unable to save artists") { artistDao.upsertAll(artists) }
}
