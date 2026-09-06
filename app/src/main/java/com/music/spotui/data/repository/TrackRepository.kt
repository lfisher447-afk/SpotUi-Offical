// File: app/src/main/java/com/music/spotui/data/repository/TrackRepository.kt
package com.music.spotui.data.repository

import com.music.spotui.data.db.TrackDao
import com.music.spotui.data.models.TrackModel
import com.music.spotui.util.Result
import com.music.spotui.util.runSuspendCatching
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Source of truth for the local track catalog. */
interface TrackRepository {
    /** Streams every locally saved track. */
    fun observeTracks(): Flow<List<TrackModel>>

    /** Streams a track by its stable [trackId]. */
    fun observeTrack(trackId: String): Flow<TrackModel?>

    /** Searches tracks already cached on device. */
    fun searchCached(query: String): Flow<List<TrackModel>>

    /** Persists [track] and returns a typed outcome. */
    suspend fun save(track: TrackModel): Result<Unit>

    /** Persists [tracks] and returns a typed outcome. */
    suspend fun saveAll(tracks: List<TrackModel>): Result<Unit>

    /** Removes [trackId] from the local catalog. */
    suspend fun remove(trackId: String): Result<Unit>
}

/** Room-backed implementation of [TrackRepository]. */
@Singleton
class TrackRepositoryImpl @Inject constructor(
    private val trackDao: TrackDao,
) : TrackRepository {
    override fun observeTracks(): Flow<List<TrackModel>> = trackDao.observeAll()

    override fun observeTrack(trackId: String): Flow<TrackModel?> = trackDao.observeById(trackId)

    override fun searchCached(query: String): Flow<List<TrackModel>> = trackDao.search(query.trim())

    override suspend fun save(track: TrackModel): Result<Unit> =
        runSuspendCatching("Unable to save track") { trackDao.upsert(track) }

    override suspend fun saveAll(tracks: List<TrackModel>): Result<Unit> =
        runSuspendCatching("Unable to save tracks") { trackDao.upsertAll(tracks) }

    override suspend fun remove(trackId: String): Result<Unit> =
        runSuspendCatching("Unable to remove track") { trackDao.deleteById(trackId) }
}
