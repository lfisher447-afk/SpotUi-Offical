// File: app/src/main/java/com/music/spotui/data/repository/PlaylistRepository.kt
package com.music.spotui.data.repository

import com.music.spotui.data.db.PlaylistDao
import com.music.spotui.data.db.TrackDao
import com.music.spotui.data.models.PlaylistModel
import com.music.spotui.data.models.TrackModel
import com.music.spotui.util.Result
import com.music.spotui.util.runSuspendCatching
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Playlist with its tracks ordered exactly as the user sees them. */
data class PlaylistDetails(
    val playlist: PlaylistModel,
    val tracks: List<TrackModel>,
)

/** Source of truth for locally persisted playlists and their ordering. */
interface PlaylistRepository {
    /** Streams every playlist. */
    fun observePlaylists(): Flow<List<PlaylistModel>>

    /** Streams [playlistId] together with any cached tracks it references. */
    fun observeDetails(playlistId: String): Flow<PlaylistDetails?>

    /** Creates or updates [playlist]. */
    suspend fun save(playlist: PlaylistModel): Result<Unit>

    /** Replaces the ordered track ids of [playlistId]. */
    suspend fun updateTrackOrder(playlistId: String, trackIds: List<String>): Result<Unit>

    /** Removes [playlistId]. */
    suspend fun remove(playlistId: String): Result<Unit>
}

/** Room-backed implementation of [PlaylistRepository]. */
@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val trackDao: TrackDao,
) : PlaylistRepository {
    override fun observePlaylists(): Flow<List<PlaylistModel>> = playlistDao.observeAll()

    override fun observeDetails(playlistId: String): Flow<PlaylistDetails?> = combine(
        playlistDao.observeById(playlistId),
        trackDao.observeAll(),
    ) { playlist, tracks ->
        playlist?.let { value ->
            val tracksById = tracks.associateBy(TrackModel::id)
            PlaylistDetails(value, value.trackIds.mapNotNull(tracksById::get))
        }
    }

    override suspend fun save(playlist: PlaylistModel): Result<Unit> =
        runSuspendCatching("Unable to save playlist") { playlistDao.upsert(playlist) }

    override suspend fun updateTrackOrder(playlistId: String, trackIds: List<String>): Result<Unit> =
        runSuspendCatching("Unable to update playlist") {
            val playlist = requireNotNull(playlistDao.getById(playlistId)) { "Playlist no longer exists" }
            playlistDao.upsert(
                playlist.copy(
                    trackIds = trackIds.distinct(),
                    updatedAtEpochMs = System.currentTimeMillis(),
                ),
            )
        }

    override suspend fun remove(playlistId: String): Result<Unit> =
        runSuspendCatching("Unable to remove playlist") { playlistDao.deleteById(playlistId) }
}
