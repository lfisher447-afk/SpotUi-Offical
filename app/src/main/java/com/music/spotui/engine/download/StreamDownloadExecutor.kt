// File: app/src/main/java/com/music/spotui/engine/download/StreamDownloadExecutor.kt
package com.music.spotui.engine.download

import android.content.Context
import android.net.Uri
import com.music.spotui.data.db.TrackDao
import com.music.spotui.data.models.DownloadModel
import com.music.spotui.data.models.DownloadState
import com.music.spotui.data.repository.DownloadRepository
import com.music.spotui.engine.stream.StreamResolver
import com.music.spotui.util.Result
import com.music.spotui.util.runSuspendCatching
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.eclipse.Mrbean.client.MrbeanDownloadClient

/** Copies a resolved, app-authorized stream into offline storage using the high-speed MrBean HTTP/2 multi-range engine. */
@Singleton
class StreamDownloadExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository,
    private val streamResolver: StreamResolver,
    private val trackDao: TrackDao,
) {
    private val progressScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Resolves and saves [trackId], returning the completed persisted download record. */
    suspend fun download(trackId: String): Result<DownloadModel> {
        val outcome = runSuspendCatching("Unable to download track") {
            val track = requireNotNull(trackDao.getById(trackId)) { "Track is no longer available" }
            downloadRepository.updateState(trackId, DownloadState.DOWNLOADING, 0F)
            val stream = when (val resolved = streamResolver.resolve(track)) {
                is Result.Success -> resolved.value
                is Result.Failure -> throw resolved.throwable
                Result.Loading -> error("Stream resolution did not complete")
            }
            val downloadDirectory = File(context.filesDir, "downloads_offline").apply { mkdirs() }
            val target = File(downloadDirectory, "${track.id.hashCode().toUInt().toString(16)}.media")
            val temporary = File(downloadDirectory, "${target.name}.part")
            runCatching { temporary.delete() }

            var lastPct = -1
            val transfer = MrbeanDownloadClient.downloadRangedToFile(
                context = context,
                url = stream.url,
                destFile = temporary,
            ) { pct ->
                if (pct - lastPct >= 3 || pct == 100) {
                    lastPct = pct
                    progressScope.launch {
                        runCatching {
                            downloadRepository.updateState(trackId, DownloadState.DOWNLOADING, (pct / 100f).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            check(transfer.success) { transfer.error ?: "Download failed" }
            check(temporary.renameTo(target)) { "Unable to finalize downloaded file" }
            DownloadModel(
                trackId = trackId,
                state = DownloadState.COMPLETED,
                progress = 1F,
                localUri = Uri.fromFile(target).toString(),
                bytesDownloaded = target.length(),
                totalBytes = target.length(),
                updatedAtEpochMs = System.currentTimeMillis(),
            )
        }
        when (outcome) {
            is Result.Success -> downloadRepository.save(outcome.value)
            is Result.Failure -> downloadRepository.updateState(trackId, DownloadState.FAILED, 0F, errorMessage = outcome.userMessage)
            Result.Loading -> Unit
        }
        return outcome
    }
}
