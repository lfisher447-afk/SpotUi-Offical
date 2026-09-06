// File: app/src/main/java/com/music/spotui/data/repository/DownloadRepository.kt
package com.music.spotui.data.repository

import com.music.spotui.data.db.DownloadDao
import com.music.spotui.data.models.DownloadModel
import com.music.spotui.data.models.DownloadState
import com.music.spotui.util.Result
import com.music.spotui.util.runSuspendCatching
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Source of truth for offline download state. */
interface DownloadRepository {
    /** Streams all download records. */
    fun observeDownloads(): Flow<List<DownloadModel>>

    /** Streams state for [trackId]. */
    fun observeDownload(trackId: String): Flow<DownloadModel?>

    /** Persists [download]. */
    suspend fun save(download: DownloadModel): Result<Unit>

    /** Updates state and optional failure details for [trackId]. */
    suspend fun updateState(
        trackId: String,
        state: DownloadState,
        progress: Float,
        localUri: String? = null,
        errorMessage: String? = null,
    ): Result<Unit>

    /** Removes the database record for [trackId]. */
    suspend fun remove(trackId: String): Result<Unit>
}

/** Room-backed implementation of [DownloadRepository]. */
@Singleton
class DownloadRepositoryImpl @Inject constructor(
    private val downloadDao: DownloadDao,
) : DownloadRepository {
    override fun observeDownloads(): Flow<List<DownloadModel>> = downloadDao.observeAll()

    override fun observeDownload(trackId: String): Flow<DownloadModel?> = downloadDao.observeByTrackId(trackId)

    override suspend fun save(download: DownloadModel): Result<Unit> =
        runSuspendCatching("Unable to save download state") { downloadDao.upsert(download) }

    override suspend fun updateState(
        trackId: String,
        state: DownloadState,
        progress: Float,
        localUri: String?,
        errorMessage: String?,
    ): Result<Unit> = runSuspendCatching("Unable to update download state") {
        val previous = downloadDao.getByTrackId(trackId)
        downloadDao.upsert(
            DownloadModel(
                trackId = trackId,
                state = state,
                progress = progress.coerceIn(0F, 1F),
                localUri = localUri ?: previous?.localUri,
                bytesDownloaded = previous?.bytesDownloaded ?: 0L,
                totalBytes = previous?.totalBytes ?: 0L,
                errorMessage = errorMessage,
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun remove(trackId: String): Result<Unit> =
        runSuspendCatching("Unable to remove download state") { downloadDao.deleteByTrackId(trackId) }
}
