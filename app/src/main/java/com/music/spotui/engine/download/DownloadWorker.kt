// File: app/src/main/java/com/music/spotui/engine/download/DownloadWorker.kt
package com.music.spotui.engine.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.music.spotui.util.Result as AppResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** WorkManager task that transfers one app-owned offline media file. */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted parameters: WorkerParameters,
    private val streamDownloadExecutor: StreamDownloadExecutor,
) : CoroutineWorker(appContext, parameters) {
    /** Performs the download and returns a retryable result for transient failures. */
    override suspend fun doWork(): Result {
        val trackId = inputData.getString(KEY_TRACK_ID) ?: return Result.failure()
        return when (streamDownloadExecutor.download(trackId)) {
            is AppResult.Success -> Result.success()
            AppResult.Loading -> Result.retry()
            is AppResult.Failure -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        /** Input-data key for the stable local track identifier. */
        const val KEY_TRACK_ID = "track_id"
        private const val MAX_ATTEMPTS = 3
    }
}
