package com.music.spotui.deezer

import android.content.Context
import android.net.Uri
import android.util.Log
import com.metrolist.spotify.Spotify
import com.music.spotui.data.preferences.getDeezerArl
import com.music.spotui.data.preferences.setDeezerTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Resolves a Spotify track to a playable or downloadable Deezer stream via ISRC
 * (with clean title/artist search fallback). Uses Blowfish-encrypted CDN URLs wrapped
 * as `deezer://` URIs for streaming playback and direct chunked decryption for downloads.
 */
object DeezerSource {
    private const val TAG = "DeezerSource"

    sealed interface Result {
        data class Success(val uri: String, val mimeFlac: Boolean, val qualityLabel: String) : Result
        data object NotLoggedIn : Result
        data object NotFound : Result
        data class Error(val message: String) : Result
    }

    data class Resolved(
        val url: String,
        val encrypted: Boolean,
        val trackId: String,
        val isFlac: Boolean,
        val qualityLabel: String,
    )

    fun isConfigured(context: Context): Boolean = getDeezerArl(context).isNotBlank()

    suspend fun resolveRaw(
        context: Context,
        spotifyId: String?,
        isrc: String? = null,
        searchQuery: String? = null,
    ): Resolved? = withContext(Dispatchers.IO) {
        val arl = getDeezerArl(context).takeIf { it.isNotBlank() } ?: return@withContext null
        DeezerSession.setArl(arl)
        DeezerSession.authorize()
        if (!DeezerSession.hasArl()) return@withContext null

        val effectiveIsrc = isrc?.takeIf { it.isNotBlank() }
            ?: spotifyId?.let { runCatching { Spotify.track(it).getOrNull()?.isrc }.getOrNull() }
        val deezerId = effectiveIsrc?.let { DeezerSession.deezerIdForIsrc(it) }
            ?: searchQuery?.takeIf { it.isNotBlank() }?.let { DeezerSession.searchTrackId(it) }
            ?: return@withContext null

        val tokens = DeezerSession.trackTokens(deezerId) ?: return@withContext null

        val candidates = when (DeezerSession.entitledQuality) {
            DeezerSession.QUALITY_FLAC -> listOf(DeezerSession.QUALITY_FLAC, DeezerSession.QUALITY_MP3_320, DeezerSession.QUALITY_MP3_128)
            DeezerSession.QUALITY_MP3_320 -> listOf(DeezerSession.QUALITY_MP3_320, DeezerSession.QUALITY_MP3_128)
            else -> listOf(DeezerSession.QUALITY_MP3_128)
        }
        for (q in candidates) {
            val (url, encrypted) = DeezerSession.getTrackUrl(tokens, q)
            if (url != null) {
                runCatching { setDeezerTier(context, tierLabel(DeezerSession.entitledQuality)) }
                Log.d(TAG, "Deezer resolved id=${tokens.id} q=$q for isrc=$effectiveIsrc")
                return@withContext Resolved(
                    url = url,
                    encrypted = encrypted,
                    trackId = tokens.id,
                    isFlac = q == DeezerSession.QUALITY_FLAC,
                    qualityLabel = qualityLabel(q),
                )
            }
        }
        null
    }

    suspend fun resolve(
        context: Context,
        spotifyId: String?,
        isrc: String? = null,
        searchQuery: String? = null,
    ): Result {
        if (!isConfigured(context)) return Result.NotLoggedIn
        return runCatching {
            val resolved = resolveRaw(context, spotifyId, isrc, searchQuery) ?: return@runCatching Result.NotFound
            val deezerUri = Uri.Builder()
                .scheme("deezer")
                .authority(resolved.trackId)
                .appendQueryParameter("enc", if (resolved.encrypted) "1" else "0")
                .appendQueryParameter("u", resolved.url)
                .build()
                .toString()
            Result.Success(
                uri = deezerUri,
                mimeFlac = resolved.isFlac,
                qualityLabel = resolved.qualityLabel,
            )
        }.getOrElse { e ->
            Result.Error(e.message ?: "Deezer resolution failed")
        }
    }

    /** Downloads and decrypts a resolved Deezer stream into [outFile] for offline storage. */
    suspend fun downloadDecryptedToFile(
        resolved: Resolved,
        outFile: File,
        onProgress: (Int) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val req = okhttp3.Request.Builder().url(resolved.url).get().build()
            DeezerSession.httpClient().newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use false
                val body = resp.body ?: return@use false
                val total = body.contentLength()
                val keyBytes = if (resolved.encrypted) DeezerCrypto.trackKeyBytes(resolved.trackId) else null
                val chunkBuf = ByteArray(DeezerCrypto.CHUNK_SIZE)
                var chunkIndex = 0L
                var written = 0L

                var lastPct = -1
                BufferedOutputStream(FileOutputStream(outFile), 256 * 1024).use { out ->
                    body.byteStream().use { input ->
                        var fill = 0
                        while (true) {
                            val r = input.read(chunkBuf, fill, DeezerCrypto.CHUNK_SIZE - fill)
                            if (r < 0) {
                                if (fill > 0) {
                                    out.write(chunkBuf, 0, fill)
                                    written += fill
                                }
                                break
                            }
                            fill += r
                            if (fill == DeezerCrypto.CHUNK_SIZE) {
                                if (keyBytes != null) {
                                    DeezerCrypto.decryptChunkInPlace(chunkBuf, fill, chunkIndex, keyBytes)
                                }
                                out.write(chunkBuf, 0, fill)
                                written += fill
                                chunkIndex++
                                fill = 0
                                if (total > 0) {
                                    val pct = ((written * 100) / total).toInt().coerceIn(0, 100)
                                    if (pct != lastPct) {
                                        lastPct = pct
                                        onProgress(pct)
                                    }
                                }
                            }
                        }
                    }
                }
                onProgress(100)
                written > 0L
            }
        }.onFailure {
            Log.w(TAG, "Deezer download failed: ${it.message}")
            runCatching { outFile.delete() }
        }.getOrDefault(false)
    }

    private fun qualityLabel(q: Int): String = when (q) {
        DeezerSession.QUALITY_FLAC -> "FLAC 16-bit"
        DeezerSession.QUALITY_MP3_320 -> "MP3 320 kbps"
        else -> "MP3 128 kbps"
    }

    private fun tierLabel(q: Int): String = when (q) {
        DeezerSession.QUALITY_FLAC -> "HiFi FLAC"
        DeezerSession.QUALITY_MP3_320 -> "MP3 320"
        else -> "Free (MP3 128)"
    }
}
