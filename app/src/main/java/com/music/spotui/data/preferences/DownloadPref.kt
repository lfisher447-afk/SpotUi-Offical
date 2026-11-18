package com.music.spotui.data.preferences

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.util.AppDiagnostics
import org.json.JSONObject
import java.io.File

/**
 * Tracks downloaded (offline) tracks. Each entry is keyed by the song id and stores
 * the full [SongsModel] plus the local file path the audio was saved to, so the
 * Downloads screen can list them offline and [com.music.spotui.di.SongPlayer]
 * can play the local file instead of re-resolving + streaming from YouTube.
 */
private const val PREF = "Downloads"
private const val KEY_DL_SORT = "download_sort_option"
private const val KEY_DL_DESC = "download_sort_desc"
private const val EXPORT_TAG = "DownloadExport"
const val MUSIC_EXPORT_SUBFOLDER = "spotui"

enum class DownloadSortOption(val label: String) {
    DATE("Added"),
    TITLE("Title"),
    ARTIST("Artist"),
}

data class ExportReport(
    val exportedCount: Int,
    val failedCount: Int,
    val destinationLabel: String,
    val lastError: String? = null,
    val exportedNames: List<String> = emptyList(),
)

fun getDownloadsSortOption(context: Context): DownloadSortOption {
    val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    val saved = prefs.getString(KEY_DL_SORT, DownloadSortOption.DATE.name)
    return saved?.let { runCatching { DownloadSortOption.valueOf(it) }.getOrNull() } ?: DownloadSortOption.DATE
}

fun isDownloadsSortDescending(context: Context): Boolean {
    return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        .getBoolean(KEY_DL_DESC, true) // Default true for DATE (recent)
}

fun setDownloadsSortOption(context: Context, option: DownloadSortOption, desc: Boolean) {
    context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
        .putString(KEY_DL_SORT, option.name)
        .putBoolean(KEY_DL_DESC, desc)
        .apply()
}

private fun SongsModel.toJson(filePath: String): String = JSONObject().apply {
    put("id", id)
    put("title", title)
    put("album", album)
    put("singer", singer)
    put("coverUri", coverUri)
    put("url", url)
    put("spotifyTrackId", spotifyTrackId)
    put("explicit", explicit)
    put("durationMs", durationMs)
    put("artistIds", artistIds)
    put("filePath", filePath)
}.toString()

private fun parse(json: String): Pair<SongsModel, String>? = runCatching {
    val o = JSONObject(json)
    SongsModel(
        id = o.getInt("id"),
        title = o.getString("title"),
        album = o.optString("album"),
        singer = o.getString("singer"),
        coverUri = o.optString("coverUri"),
        url = o.getString("url"),
        spotifyTrackId = o.optString("spotifyTrackId"),
        explicit = o.optBoolean("explicit", false),
        durationMs = o.optInt("durationMs", 0),
        artistIds = o.optString("artistIds", ""),
    ) to o.optString("filePath")
}.getOrNull()

fun addDownload(context: Context, song: SongsModel, filePath: String) {
    context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
        .putString(song.id.toString(), song.toJson(filePath))
        .apply()
}

fun removeDownload(context: Context, songId: String) {
    val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    prefs.getString(songId, null)?.let { json ->
        parse(json)?.second?.let { path -> runCatching { File(path).delete() } }
    }
    val dir = File(context.filesDir, "downloads")
    runCatching { File(dir, "${songId}_cover.jpg").delete() }
    runCatching { File(dir, "${songId}.jpg").delete() }
    prefs.edit().remove(songId).apply()
}

fun isDownloaded(context: Context, songId: String): Boolean =
    context.getSharedPreferences(PREF, Context.MODE_PRIVATE).contains(songId)

fun getDownloadedSongs(context: Context): List<SongsModel> {
    val entries = getDownloadedEntries(context)
    entries.forEach { (song, _) ->
        com.music.spotui.util.ArtworkHelper.ensureDownloadedCover(context, song)
    }
    val sortOption = getDownloadsSortOption(context)
    val descending = isDownloadsSortDescending(context)
    return when (sortOption) {
        DownloadSortOption.TITLE -> if (descending) entries.map { it.first }.sortedByDescending { it.title.lowercase() } else entries.map { it.first }.sortedBy { it.title.lowercase() }
        DownloadSortOption.ARTIST -> if (descending) entries.map { it.first }.sortedByDescending { it.singer.lowercase() } else entries.map { it.first }.sortedBy { it.singer.lowercase() }
        DownloadSortOption.DATE -> if (descending) entries.sortedByDescending { (_, path) -> File(path).lastModified() }.map { it.first } else entries.sortedBy { (_, path) -> File(path).lastModified() }.map { it.first }
    }
}

/** Local file path for a track query (SongsModel.url), if downloaded and present on disk. */
fun downloadedPathForQuery(context: Context, query: String): String? {
    val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    for (v in prefs.all.values) {
        val (song, path) = (v as? String)?.let(::parse) ?: continue
        if (song.url == query && path.isNotBlank() && File(path).exists()) {
            com.music.spotui.util.ArtworkHelper.ensureDownloadedCover(context, song)
            return path
        }
    }
    return null
}

/** Every downloaded track paired with its on-disk file path. */
fun getDownloadedEntries(context: Context): List<Pair<SongsModel, String>> =
    context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        .all.values.mapNotNull { (it as? String)?.let(::parse) }

/** Total bytes consumed on internal storage by completed downloads. */
fun getTotalDownloadsBytes(context: Context): Long =
    getDownloadedEntries(context)
        .map { it.second }
        .distinct()
        .sumOf { path -> runCatching { File(path).takeIf { it.exists() }?.length() ?: 0L }.getOrDefault(0L) }

/** Delete every downloaded file and forget all download entries. Returns count removed. */
fun clearAllDownloads(context: Context): Int {
    val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    val entries = getDownloadedEntries(context)
    entries.forEach { (song, path) ->
        if (path.isNotBlank()) runCatching { File(path).delete() }
        val dir = File(context.filesDir, "downloads")
        runCatching { File(dir, "${song.id}_cover.jpg").delete() }
        runCatching { File(dir, "${song.id}.jpg").delete() }
    }
    val currentSort = getDownloadsSortOption(context)
    val currentDesc = isDownloadsSortDescending(context)
    prefs.edit().clear().apply()
    setDownloadsSortOption(context, currentSort, currentDesc)
    OfflineCollectionsPref.clearAll(context)
    return entries.size
}

private fun sanitizeFileName(name: String): String =
    name.replace(Regex("[/\\\\:*?\"<>|]"), "_")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(120)
        .ifBlank { "track" }

/**
 * Sniffs the container magic bytes (with extension fallback) and returns a strict,
 * concrete `(extension, mimeType)` pair accepted by Android 10–17 `MediaStore` /
 * `MediaProvider`.
 *
 * CRITICAL: Never returns a wildcard MIME (like audio-star) — Android 11–17 `MediaProvider` rejects wildcard
 * MIME types with `IllegalArgumentException`, which previously caused `Music/spotui`
 * exports to silently fail and leave no folder or files.
 */
fun detectAudioContainerAndMime(header: ByteArray, fileExtension: String): Pair<String, String> {
    if (header.size >= 4) {
        // "fLaC" -> native FLAC
        if (header[0] == 0x66.toByte() && header[1] == 0x4C.toByte() &&
            header[2] == 0x61.toByte() && header[3] == 0x43.toByte()
        ) {
            return "flac" to "audio/flac"
        }
        // "ID3" or raw MPEG ADTS sync word (0xFF 0xE*) -> MP3
        if ((header[0] == 0x49.toByte() && header[1] == 0x44.toByte() && header[2] == 0x33.toByte()) ||
            (header[0] == 0xFF.toByte() && (header[1].toInt() and 0xE0) == 0xE0)
        ) {
            return "mp3" to "audio/mpeg"
        }
        // "OggS" -> OGG container (Vorbis/Opus inside Ogg)
        if (header[0] == 0x4F.toByte() && header[1] == 0x67.toByte() &&
            header[2] == 0x67.toByte() && header[3] == 0x53.toByte()
        ) {
            return if (fileExtension.equals("opus", ignoreCase = true)) {
                "opus" to "audio/ogg"
            } else {
                "ogg" to "audio/ogg"
            }
        }
        // EBML header (\x1A\x45\xDF\xA3) -> WebM / Matroska audio (common from YouTube)
        if (header[0] == 0x1A.toByte() && header[1] == 0x45.toByte() &&
            header[2] == 0xDF.toByte() && header[3] == 0xA3.toByte()
        ) {
            return "webm" to "audio/webm"
        }
        // ISO-BMFF "ftyp" box at offset +4 -> M4A / MP4 audio
        if (header.size >= 8 &&
            header[4] == 0x66.toByte() && header[5] == 0x74.toByte() &&
            header[6] == 0x79.toByte() && header[7] == 0x70.toByte()
        ) {
            return "m4a" to "audio/mp4"
        }
        // "RIFF....WAVE" -> WAV
        if (header.size >= 12 &&
            header[0] == 0x52.toByte() && header[1] == 0x49.toByte() &&
            header[2] == 0x46.toByte() && header[3] == 0x46.toByte() &&
            header[8] == 0x57.toByte() && header[9] == 0x41.toByte() &&
            header[10] == 0x56.toByte() && header[11] == 0x45.toByte()
        ) {
            return "wav" to "audio/wav"
        }
    }

    return when (fileExtension.lowercase().trim()) {
        "flac" -> "flac" to "audio/flac"
        "m4a", "mp4", "aac" -> "m4a" to "audio/mp4"
        "mp3" -> "mp3" to "audio/mpeg"
        "opus" -> "opus" to "audio/ogg"
        "ogg" -> "ogg" to "audio/ogg"
        "webm", "mka" -> "webm" to "audio/webm"
        "wav" -> "wav" to "audio/wav"
        else -> "m4a" to "audio/mp4"
    }
}

fun detectAudioContainerAndMime(file: File): Pair<String, String> {
    val header = runCatching {
        file.inputStream().use { input ->
            val buf = ByteArray(16)
            val read = input.read(buf)
            if (read > 0) buf.copyOf(read) else ByteArray(0)
        }
    }.getOrDefault(ByteArray(0))
    return detectAudioContainerAndMime(header, file.extension)
}

/**
 * Copy every downloaded track out of private app storage into public **Music/spotui**
 * as `Artist - Title.<ext>`. Preserves existing `(count, label)` return contract.
 */
fun exportDownloads(context: Context): Pair<Int, String> {
    val report = exportDownloadsDetailed(context)
    return if (report.exportedCount > 0) {
        report.exportedCount to report.destinationLabel
    } else {
        0 to (report.lastError ?: report.destinationLabel)
    }
}

/** Detailed bulk export reporting per-file success, failures, and actionable error messages. */
fun exportDownloadsDetailed(context: Context): ExportReport {
    val entries = getDownloadedEntries(context).filter { it.second.isNotBlank() && File(it.second).exists() }
    if (entries.isEmpty()) {
        return ExportReport(0, 0, "No downloaded files to export", lastError = "No offline files found")
    }
    var okCount = 0
    var failCount = 0
    var lastError: String? = null
    val names = mutableListOf<String>()

    for ((song, path) in entries) {
        val res = exportFileDetailed(context, song, path)
        res.onSuccess { displayName ->
            okCount++
            names.add(displayName)
        }.onFailure { err ->
            failCount++
            lastError = err.message ?: err.javaClass.simpleName
            AppDiagnostics.warning(EXPORT_TAG, "Export failed for ${song.title}: $lastError", err)
        }
    }

    return ExportReport(
        exportedCount = okCount,
        failedCount = failCount,
        destinationLabel = "${Environment.DIRECTORY_MUSIC}/$MUSIC_EXPORT_SUBFOLDER",
        lastError = lastError,
        exportedNames = names,
    )
}

/** Export a single downloaded track to public Music/spotui. Returns true on success. */
fun exportDownload(context: Context, song: SongsModel): Boolean =
    exportDownloadDetailed(context, song).isSuccess

/** Export a single downloaded track with detailed [Result] carrying the saved filename or reason. */
fun exportDownloadDetailed(context: Context, song: SongsModel): Result<String> {
    val path = getDownloadedEntries(context).firstOrNull { it.first.id == song.id }?.second
        ?.takeIf { it.isNotBlank() && File(it).exists() }
        ?: return Result.failure(IllegalStateException("Downloaded audio file not found on device"))
    return exportFileDetailed(context, song, path)
}

/** Copy one private download file into shared `Music/spotui` with scoped-storage safety. */
private fun exportFileDetailed(context: Context, song: SongsModel, path: String): Result<String> {
    val src = File(path)
    if (!src.exists() || src.length() <= 0L) {
        return Result.failure(IllegalStateException("Source audio file is missing or empty"))
    }
    val (ext, mime) = detectAudioContainerAndMime(src)
    val baseTitle = sanitizeFileName("${song.singer} - ${song.title}")
    val displayName = "$baseTitle.$ext"

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val relPath = "${Environment.DIRECTORY_MUSIC}/$MUSIC_EXPORT_SUBFOLDER"

        // Clean any prior row owned by this app with the same display name in Music/spotui
        // to avoid "(1)" clutter or stale zero-byte pending entries.
        deleteExistingScopedRow(resolver, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, displayName, relPath)
        deleteExistingScopedRow(resolver, MediaStore.Files.getContentUri("external"), displayName, relPath)

        val audioValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
            put(MediaStore.Audio.AudioColumns.IS_MUSIC, 1)
            if (song.title.isNotBlank()) put(MediaStore.Audio.AudioColumns.TITLE, song.title)
            if (song.singer.isNotBlank()) put(MediaStore.Audio.AudioColumns.ARTIST, song.singer)
            if (song.album.isNotBlank()) put(MediaStore.Audio.AudioColumns.ALBUM, song.album)
            if (song.durationMs > 0) put(MediaStore.Audio.AudioColumns.DURATION, song.durationMs.toLong())
        }

        // Attempt 1: Standard MediaStore Audio collection.
        val audioAttempt = runCatching {
            writeMediaStoreEntry(resolver, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audioValues, src)
        }
        if (audioAttempt.isSuccess) {
            AppDiagnostics.info(EXPORT_TAG, "Exported $displayName ($mime) via MediaStore.Audio -> $relPath")
            return Result.success(displayName)
        }

        // Attempt 2: Some OEM MediaProviders reject webm/opus in Audio.Media; fallback to MediaStore.Files
        // with the exact same Music/spotui RELATIVE_PATH.
        val fileValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val filesAttempt = runCatching {
            writeMediaStoreEntry(resolver, MediaStore.Files.getContentUri("external"), fileValues, src)
        }
        if (filesAttempt.isSuccess) {
            AppDiagnostics.info(EXPORT_TAG, "Exported $displayName ($mime) via MediaStore.Files -> $relPath")
            return Result.success(displayName)
        }

        val err = filesAttempt.exceptionOrNull() ?: audioAttempt.exceptionOrNull()
        return Result.failure(err ?: IllegalStateException("MediaStore rejected $displayName ($mime)"))
    } else {
        return runCatching {
            @Suppress("DEPRECATION")
            val musicRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val dir = File(musicRoot, MUSIC_EXPORT_SUBFOLDER).apply { mkdirs() }
            val dest = File(dir, displayName)
            src.inputStream().use { input ->
                dest.outputStream().use { output ->
                    val copied = input.copyTo(output, bufferSize = 64 * 1024)
                    output.flush()
                    check(copied > 0L) { "Zero bytes copied" }
                }
            }
            MediaScannerConnection.scanFile(
                context.applicationContext,
                arrayOf(dest.absolutePath),
                arrayOf(mime),
                null,
            )
            displayName
        }
    }
}

@androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
private fun deleteExistingScopedRow(
    resolver: android.content.ContentResolver,
    collectionUri: Uri,
    displayName: String,
    relPath: String,
) {
    runCatching {
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val args = arrayOf(displayName, "$relPath%")
        resolver.query(
            collectionUri,
            arrayOf(MediaStore.MediaColumns._ID),
            selection,
            args,
            null,
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
            if (idIdx >= 0) {
                while (cursor.moveToNext()) {
                    val rowId = cursor.getLong(idIdx)
                    val itemUri = ContentUris.withAppendedId(collectionUri, rowId)
                    runCatching { resolver.delete(itemUri, null, null) }
                }
            }
        }
    }
}

@androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
private fun writeMediaStoreEntry(
    resolver: android.content.ContentResolver,
    collectionUri: Uri,
    values: ContentValues,
    src: File,
) {
    val uri = resolver.insert(collectionUri, values)
        ?: error("ContentResolver.insert returned null for $collectionUri")
    var completed = false
    try {
        val copied = resolver.openOutputStream(uri)?.use { out ->
            src.inputStream().use { input ->
                val bytes = input.copyTo(out, bufferSize = 64 * 1024)
                out.flush()
                bytes
            }
        } ?: error("Unable to open output stream for $uri")
        check(copied > 0L) { "Copied 0 bytes to MediaStore" }

        val finalizeValues = ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }
        resolver.update(uri, finalizeValues, null, null)
        completed = true
    } finally {
        if (!completed) {
            runCatching { resolver.delete(uri, null, null) }
        }
    }
}
