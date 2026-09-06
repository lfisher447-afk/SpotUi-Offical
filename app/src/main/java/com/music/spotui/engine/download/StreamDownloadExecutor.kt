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
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Copies a resolved, app-authorized stream into the app's private offline storage. */
@Singleton
class StreamDownloadExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository,
    private val httpClient: OkHttpClient,
    private val streamResolver: StreamResolver,
    private val trackDao: TrackDao,
) {
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
            withContext(Dispatchers.IO) {
                val request = Request.Builder().url(stream.url).build()
                httpClient.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "Download failed with HTTP ${response.code}" }
                    val body = requireNotNull(response.body) { "Download response had no body" }
                    val downloadDirectory = File(context.filesDir, "offline").apply { mkdirs() }
                    val target = File(downloadDirectory, "${track.id.hashCode().toUInt().toString(16)}.media")
                    val temporary = File(downloadDirectory, "${target.name}.part")
                    body.byteStream().use { input ->
                        FileOutputStream(temporary).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            val totalBytes = body.contentLength().coerceAtLeast(0L)
                            var copiedBytes = 0L
                            var count = input.read(buffer)
                            while (count >= 0) {
                                output.write(buffer, 0, count)
                                copiedBytes += count
                                val progress = if (totalBytes > 0L) copiedBytes.toFloat() / totalBytes else 0F
                                downloadRepository.updateState(trackId, DownloadState.DOWNLOADING, progress)
                                count = input.read(buffer)
                            }
                        }
                    }
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
            }
        }
        when (outcome) {
            is Result.Success -> downloadRepository.save(outcome.value)
            is Result.Failure -> downloadRepository.updateState(trackId, DownloadState.FAILED, 0F, errorMessage = outcome.userMessage)
            Result.Loading -> Unit
        }
        return outcome
    }
}
