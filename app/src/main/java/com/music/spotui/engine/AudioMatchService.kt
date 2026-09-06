package com.music.spotui.engine

import com.music.spotui.MyApplication
import com.music.spotui.di.SongPlayer
import com.metrolist.spotify.SpotiFlac
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Version 1.5.1 - AudioMatchService
 * Takes Spotify track metadata (ISRC, title, artist, track ID) and resolves
 * high-fidelity lossless (FLAC) or high-quality (320kbps) audio streams.
 */
object AudioMatchService {
    suspend fun resolveBestStream(
        title: String,
        artist: String,
        isrc: String = "",
        durationMs: Long = 0L,
        spotifyTrackId: String = ""
    ): String? = withContext(Dispatchers.IO) {
        // 1. First attempt: resolve lossless stream via SpotiFlac (Tidal/Qobuz/Deezer mirrors)
        if (spotifyTrackId.isNotBlank() || isrc.isNotBlank()) {
            val flacResult = runCatching {
                SpotiFlac.resolve(
                    spotifyTrackId = spotifyTrackId,
                    isrc = isrc.takeIf { it.isNotBlank() }
                )
            }.getOrNull()

            if (flacResult is SpotiFlac.Result.Success) {
                return@withContext flacResult.track.url
            }
        }

        // 2. Second attempt: resolve stream via SongPlayer's candidate resolver and YouTube/InnerTube
        val query = "$title $artist".trim()
        val appContext = runCatching { MyApplication.instance }.getOrNull()
        if (appContext != null && query.isNotBlank()) {
            val resolved = runCatching {
                SongPlayer.resolveStreamUrl(query, appContext, forPlayback = false)
            }.getOrNull()

            if (!resolved.isNullOrBlank()) {
                return@withContext resolved
            }
        }

        null
    }
}
