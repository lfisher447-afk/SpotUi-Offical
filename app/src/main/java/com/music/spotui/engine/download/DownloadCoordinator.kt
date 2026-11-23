// File: app/src/main/java/com/music/spotui/engine/download/DownloadCoordinator.kt
package com.music.spotui.engine.download

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.music.spotui.data.models.DownloadState
import com.music.spotui.data.repository.DownloadRepository
import com.music.spotui.util.Result
import com.music.spotui.util.runSuspendCatching
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.eclipse.Mrbean.client.MrbeanNetworkSettings

/** Dispatches durable WorkManager downloads either sequentially (1 thread) or concurrently (up to 10) per MrBean config. */
@Singleton
class DownloadCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository,
    private val workManager: WorkManager,
) {
    /** Queues [trackId] honoring the configured Eclipse MrBean concurrency limit. */
    suspend fun enqueue(trackId: String): Result<Unit> = runSuspendCatching("Unable to queue download") {
        require(trackId.isNotBlank()) { "A track id is required" }
        downloadRepository.updateState(trackId, DownloadState.QUEUED, 0F)
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_TRACK_ID to trackId))
            .addTag("spotui_download")
            .addTag("spotui_download_$trackId")
            .build()
        val concurrency = MrbeanNetworkSettings.maxConcurrentDownloads(context)
        if (concurrency <= 1) {
            workManager.enqueueUniqueWork(
                WORK_CHAIN_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request,
            )
        } else {
            workManager.enqueueUniqueWork(
                "spotui_dl_$trackId",
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
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
