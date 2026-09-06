// File: app/src/main/java/com/music/spotui/engine/archive/NovaAcExportManager.kt
package com.music.spotui.engine.archive

import android.content.Context
import android.net.Uri
import com.music.spotui.data.models.TrackModel
import com.music.spotui.util.Result
import com.music.spotui.util.runSuspendCatching
import com.music.spotui.util.toLegacySong
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Immutable prepared archive that can be delivered through Android's user-selected destination URI. */
data class PreparedArchive(
    val fileName: String,
    val trackCount: Int,
    val bytes: Long,
    internal val legacy: com.music.spotui.data.export.NovaAcExportManager.PreparedExport,
)

/** Clean facade for metadata-only NovaAc export using the established archive implementation. */
@Singleton
class NovaAcExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Prepares a metadata archive from [tracks] off the calling UI path. */
    suspend fun prepare(
        collectionName: String,
        sourceType: String,
        tracks: List<TrackModel>,
    ): Result<PreparedArchive> = runSuspendCatching("Unable to prepare archive") {
        val export = com.music.spotui.data.export.NovaAcExportManager.prepareExport(
            context = context,
            collectionName = collectionName,
            sourceType = sourceType,
            selectedSongs = tracks.map(TrackModel::toLegacySong),
            includeAudioPayload = false,
        )
        PreparedArchive(export.fileName, export.trackCount, export.bytes.size.toLong(), export)
    }

    /** Writes [archive] to a URI chosen by the user through the storage access framework. */
    suspend fun write(destination: Uri, archive: PreparedArchive): Result<Unit> =
        runSuspendCatching("Unable to write archive") {
            com.music.spotui.data.export.NovaAcExportManager.writePreparedExport(context, destination, archive.legacy)
                .getOrElse { throw it }
        }
}
