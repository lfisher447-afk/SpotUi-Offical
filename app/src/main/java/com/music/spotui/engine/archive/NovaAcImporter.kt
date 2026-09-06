// File: app/src/main/java/com/music/spotui/engine/archive/NovaAcImporter.kt
package com.music.spotui.engine.archive

import android.content.Context
import android.net.Uri
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.data.models.TrackModel
import com.music.spotui.data.repository.TrackRepository
import com.music.spotui.util.Result
import com.music.spotui.util.runSuspendCatching
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Imports NovaAc metadata and any application-owned audio payloads into the local catalog. */
@Singleton
class NovaAcImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackRepository: TrackRepository,
) {
    /** Imports [source] and persists all restored track metadata. */
    suspend fun import(source: Uri, browserPassphrase: String? = null): Result<List<TrackModel>> =
        runSuspendCatching("Unable to import archive") {
            val imported = com.music.spotui.data.export.NovaAcExportManager.importExport(
                context,
                source,
                saveToOfflineLibrary = true,
                browserPassphrase = browserPassphrase,
            ).getOrElse { throw it }
            val tracks = imported.songs.map { it.toTrackModel() }
            when (val saved = trackRepository.saveAll(tracks)) {
                is Result.Success -> tracks
                is Result.Failure -> throw saved.throwable
                Result.Loading -> error("Track persistence did not complete")
            }
        }

    private fun SongsModel.toTrackModel(): TrackModel = TrackModel(
        id = spotifyTrackId.takeIf(String::isNotBlank)?.let { "spotify:$it" } ?: "local:$id",
        providerId = spotifyTrackId,
        title = title,
        artistIds = artistIds.split(',').map(String::trim).filter(String::isNotBlank),
        artistNames = singer.split(',').map(String::trim).filter(String::isNotBlank),
        albumName = album,
        artworkUrl = coverUri.ifBlank { null },
        streamUrl = url.ifBlank { null },
        durationMs = durationMs.toLong(),
        explicit = explicit,
    )
}
