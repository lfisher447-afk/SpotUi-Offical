package com.music.spotui.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Looper
import android.util.LruCache
import androidx.media3.common.MediaMetadata
import com.music.spotui.data.preferences.getDownloadedEntries
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object ArtworkHelper {

    private val artScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Bounded 8 MB in-memory LRU cache for extracted embedded cover bytes so main-thread
    // metadata building never blocks on MediaMetadataRetriever disk I/O.
    private val embeddedArtCache = object : LruCache<String, ByteArray>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size.coerceAtLeast(1)
    }

    private val inFlightExtractions = ConcurrentHashMap.newKeySet<String>()

    private fun cleanSongId(songId: String?): String? =
        songId?.removePrefix("song/")?.removePrefix("playlist/")?.removePrefix("album/")?.trim()?.takeIf { it.isNotBlank() }

    /**
     * Canonical [MediaMetadata] factory shared by both [com.music.spotui.di.SongPlayer]
     * and [com.music.spotui.ui.notification.PlaybackService] so Frame-0 MediaItems and
     * service updates produce identical metadata objects.
     */
    fun buildTrackMetadata(
        context: Context,
        title: String,
        artist: String,
        album: String = "",
        coverUri: String = "",
        durationMs: Long = 0L,
        songId: String? = null,
        streamUrl: String? = null,
        onAsyncArtReady: (() -> Unit)? = null,
    ): MediaMetadata {
        val builder = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setIsPlayable(true)
        if (album.isNotBlank()) {
            builder.setAlbumTitle(album)
        }
        if (durationMs > 0L) {
            builder.setDurationMs(durationMs)
        }
        attachArtwork(
            builder = builder,
            context = context,
            coverUri = coverUri,
            songId = songId,
            streamUrl = streamUrl,
            onAsyncArtReady = onAsyncArtReady,
        )
        return builder.build()
    }

    /**
     * Attaches the best available artwork (local downloaded cover file, memory-cached embedded ID3 picture,
     * Glide disk cache, or remote URI) to a [MediaMetadata.Builder] for system notifications without
     * running blocking I/O on the Main thread.
     */
    fun attachArtwork(
        builder: MediaMetadata.Builder,
        context: Context,
        coverUri: String,
        songId: String? = null,
        streamUrl: String? = null,
        onAsyncArtReady: (() -> Unit)? = null,
    ): MediaMetadata.Builder {
        val appContext = context.applicationContext
        val cleanCover = coverUri.trim()
        val cleanStream = streamUrl?.trim().orEmpty()
        val rawPath = if (cleanStream.startsWith("file://")) Uri.parse(cleanStream).path.orEmpty() else cleanStream
        val rawId = cleanSongId(songId)

        val dir = File(appContext.filesDir, "downloads")

        // 1. Check for a saved local download cover file by songId
        if (!rawId.isNullOrBlank()) {
            val localCover = File(dir, "${rawId}_cover.jpg")
            if (localCover.exists() && localCover.length() > 0) {
                return builder.setArtworkUri(Uri.fromFile(localCover))
            }
            val localCoverAlt = File(dir, "${rawId}.jpg")
            if (localCoverAlt.exists() && localCoverAlt.length() > 0) {
                return builder.setArtworkUri(Uri.fromFile(localCoverAlt))
            }
        }

        // 2. Match by streamUrl / query / path in downloaded entries
        if (cleanStream.isNotBlank()) {
            val dlEntries = runCatching { getDownloadedEntries(appContext) }.getOrDefault(emptyList())
            val matchedEntry = dlEntries.firstOrNull { (song, path) ->
                song.url == cleanStream || path == cleanStream || (rawPath.isNotBlank() && path == rawPath)
            }
            if (matchedEntry != null) {
                val localCover = File(dir, "${matchedEntry.first.id}_cover.jpg")
                if (localCover.exists() && localCover.length() > 0) {
                    return builder.setArtworkUri(Uri.fromFile(localCover))
                }
                ensureDownloadedCover(appContext, matchedEntry.first)
            }
        }

        // 3. Local audio / content URI embedded cover art (never run MediaMetadataRetriever on Main Thread)
        val isLocalMedia = rawPath.isNotBlank() && (rawPath.startsWith("/") || cleanStream.startsWith("content:"))
        if (isLocalMedia) {
            val cacheKey = cleanStream.ifBlank { rawPath }
            embeddedArtCache.get(cacheKey)?.let { cachedBytes ->
                return builder.setArtworkData(cachedBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            }
            if (Looper.myLooper() != Looper.getMainLooper()) {
                extractEmbeddedArtSync(appContext, rawPath, cleanStream, cacheKey, rawId)?.let { extracted ->
                    return builder.setArtworkData(extracted, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                }
            } else if (inFlightExtractions.add(cacheKey)) {
                artScope.launch {
                    try {
                        val extracted = extractEmbeddedArtSync(appContext, rawPath, cleanStream, cacheKey, rawId)
                        if (extracted != null) {
                            onAsyncArtReady?.invoke()
                        }
                    } finally {
                        inFlightExtractions.remove(cacheKey)
                    }
                }
            }
        }

        // 4. Check if coverUri is already a local file or content URI
        if (cleanCover.startsWith("file:") || cleanCover.startsWith("content:")) {
            return builder.setArtworkUri(Uri.parse(cleanCover))
        }

        // 5. Check Glide disk cache safely off Main thread
        if (cleanCover.isNotBlank()) {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                val cachedFile = runCatching {
                    com.bumptech.glide.Glide.with(appContext)
                        .downloadOnly()
                        .load(cleanCover)
                        .onlyRetrieveFromCache(true)
                        .submit()
                        .get()
                }.getOrNull()

                if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 0) {
                    if (!rawId.isNullOrBlank()) {
                        val dest = File(dir, "${rawId}_cover.jpg")
                        if (!dest.exists()) runCatching { cachedFile.copyTo(dest, overwrite = true) }
                    }
                    return builder.setArtworkUri(Uri.fromFile(cachedFile))
                }
            }

            // 6. Fallback to remote URI (DefaultMediaNotificationProvider's BitmapLoader resolves it async)
            return builder.setArtworkUri(Uri.parse(cleanCover))
        }

        return builder
    }

    private fun extractEmbeddedArtSync(
        context: Context,
        rawPath: String,
        cleanStream: String,
        cacheKey: String,
        rawId: String?,
    ): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            if (cleanStream.startsWith("content:")) {
                retriever.setDataSource(context, Uri.parse(cleanStream))
            } else {
                retriever.setDataSource(rawPath)
            }
            retriever.embeddedPicture?.takeIf { it.isNotEmpty() }?.also { bytes ->
                embeddedArtCache.put(cacheKey, bytes)
                if (!rawId.isNullOrBlank()) {
                    val dir = File(context.filesDir, "downloads").apply { mkdirs() }
                    val dest = File(dir, "${rawId}_cover.jpg")
                    if (!dest.exists()) {
                        runCatching { dest.writeBytes(bytes) }
                    }
                }
            }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** Ensure a downloaded song has its cover image cached/saved locally to disk. */
    fun ensureDownloadedCover(context: Context, song: com.music.spotui.data.entity.SongsModel) {
        if (song.coverUri.isBlank()) return
        val dir = File(context.filesDir, "downloads").apply { mkdirs() }
        val coverFile = File(dir, "${song.id}_cover.jpg")
        if (coverFile.exists() && coverFile.length() > 0) return

        val appContext = context.applicationContext
        artScope.launch {
            val file = runCatching {
                com.bumptech.glide.Glide.with(appContext)
                    .downloadOnly()
                    .load(song.coverUri)
                    .submit()
                    .get()
            }.getOrNull()
            if (file != null && file.exists()) {
                runCatching { file.copyTo(coverFile, overwrite = true) }
            } else {
                org.eclipse.Mrbean.client.MrbeanDownloadClient.downloadSmallFile(appContext, song.coverUri, coverFile)
            }
        }
    }
}
