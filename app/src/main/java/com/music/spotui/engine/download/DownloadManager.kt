// File: app/src/main/java/com/music/spotui/engine/download/DownloadManager.kt
package com.music.spotui.engine.download

import com.music.spotui.data.models.DownloadState
import com.music.spotui.data.models.TrackModel
import com.music.spotui.data.repository.DownloadRepository
import com.music.spotui.data.repository.TrackRepository
import com.music.spotui.util.Result
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Coordinates download requests and exposes their state alongside cached track metadata. */
@Singleton
class DownloadManager @Inject constructor(
    private val downloadCoordinator: DownloadCoordinator,
    private val downloadRepository: DownloadRepository,
    private val trackRepository: TrackRepository,
) {
    /** Streams tracks paired with their current download state. */
    fun observeDownloads(): Flow<List<Pair<TrackModel, DownloadState>>> = combine(
        downloadRepository.observeDownloads(),
        trackRepository.observeTracks(),
    ) { downloads, tracks ->
        val tracksById = tracks.associateBy(TrackModel::id)
        downloads.mapNotNull { download -> tracksById[download.trackId]?.let { it to download.state } }
    }

    /** Persists [track] then queues it for sequential download. */
    suspend fun enqueue(track: TrackModel): Result<Unit> = when (val saved = trackRepository.save(track)) {
        is Result.Failure -> saved
        Result.Loading -> Result.Loading
        is Result.Success -> downloadCoordinator.enqueue(track.id)
    }

    /** Cancels queued or running work and marks [trackId] as cancelled. */
    suspend fun cancel(trackId: String): Result<Unit> {
        downloadCoordinator.cancel(trackId)
        return downloadRepository.updateState(trackId, DownloadState.CANCELLED, 0F)
    }
}
