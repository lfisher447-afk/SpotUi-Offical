// File: app/src/main/java/com/music/spotui/data/repository/OfflineRepository.kt
package com.music.spotui.data.repository

import com.music.spotui.data.db.DownloadDao
import com.music.spotui.data.db.TrackDao
import com.music.spotui.data.models.DownloadState
import com.music.spotui.data.models.TrackModel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Exposes only tracks whose app-owned download has completed. */
interface OfflineRepository {
    /** Streams playable offline tracks. */
    fun observeOfflineTracks(): Flow<List<TrackModel>>
}

/** Room-backed implementation of [OfflineRepository]. */
@Singleton
class OfflineRepositoryImpl @Inject constructor(
    private val downloadDao: DownloadDao,
    private val trackDao: TrackDao,
) : OfflineRepository {
    override fun observeOfflineTracks(): Flow<List<TrackModel>> = combine(
        downloadDao.observeByState(DownloadState.COMPLETED),
        trackDao.observeAll(),
    ) { downloads, tracks ->
        val completedIds = downloads.map { it.trackId }.toSet()
        tracks.filter { it.id in completedIds }
    }
}
