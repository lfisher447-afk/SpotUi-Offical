// File: app/src/main/java/com/music/spotui/engine/download/DownloadCoordinator.kt
package com.music.spotui.engine.download

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.music.spotui.data.models.DownloadState
import com.music.spotui.data.repository.DownloadRepository
import com.music.spotui.util.Result
import com.music.spotui.util.runSuspendCatching
import javax.inject.Inject
import javax.inject.Singleton

/** Appends download work to one durable WorkManager chain so only one file transfers at once. */
@Singleton
class DownloadCoordinator @Inject constructor(
    private val downloadRepository: DownloadRepository,
    private val workManager: WorkManager,
) {
    /** Queues [trackId] for sequential offline download. */
    suspend fun enqueue(trackId: String): Result<Unit> = runSuspendCatching("Unable to queue download") {
        require(trackId.isNotBlank()) { "A track id is required" }
        downloadRepository.updateState(trackId, DownloadState.QUEUED, 0F)
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_TRACK_ID to trackId))
            .addTag("spotui_download")
            .addTag("spotui_download_$trackId")
            .build()
        workManager.enqueueUniqueWork(
            WORK_CHAIN_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
        Unit
    }

    /** Cancels queued or running work for [trackId]. */
    fun cancel(trackId: String) {
        workManager.cancelAllWorkByTag("spotui_download_$trackId")
    }

    private companion object {
        const val WORK_CHAIN_NAME = "spotui_sequential_downloads"
    }
}
