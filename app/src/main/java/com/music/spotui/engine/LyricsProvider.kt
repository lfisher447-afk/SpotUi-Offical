// File: app/src/main/java/com/music/spotui/engine/LyricsProvider.kt
package com.music.spotui.engine

import com.music.spotui.data.models.LyricsModel
import com.music.spotui.data.models.TrackModel
import com.music.spotui.data.repository.LyricsRepository
import com.music.spotui.util.Constants
import com.music.spotui.util.Result
import com.music.spotui.util.runSuspendCatching
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/** Fetches and caches LRCLIB lyrics for the active track. */
@Singleton
class LyricsProvider @Inject constructor(
    private val client: HttpClient,
    private val lyricsRepository: LyricsRepository,
) {
    /** Requests a fresh LRCLIB result for [track] and stores a successful match locally. */
    suspend fun fetch(track: TrackModel): Result<LyricsModel?> = runSuspendCatching("Unable to load lyrics") {
        val response = client.get("${Constants.LRCLIB_BASE_URL}api/get") {
            parameter("track_name", track.title)
            parameter("artist_name", track.artistNames.firstOrNull().orEmpty())
            parameter("duration", track.durationMs / 1_000L)
        }
        if (!response.status.isSuccess()) return@runSuspendCatching null
        val remote = response.body<LrclibLyricsDto>()
        val lyrics = LyricsModel(
            trackId = track.id,
            plainLyrics = remote.plainLyrics?.takeIf(String::isNotBlank),
            syncedLyrics = remote.syncedLyrics?.takeIf(String::isNotBlank),
            source = "LRCLIB",
            languageCode = remote.language,
        ).takeIf { !it.plainLyrics.isNullOrBlank() || !it.syncedLyrics.isNullOrBlank() }
        lyrics?.let { saved ->
            when (val result = lyricsRepository.save(saved)) {
                is Result.Success -> Unit
                is Result.Failure -> throw result.throwable
                Result.Loading -> error("Lyrics cache did not complete")
            }
        }
        lyrics
    }
}

/** LRCLIB response subset used by the app. */
@Serializable
data class LrclibLyricsDto(
    @SerialName("plainLyrics") val plainLyrics: String? = null,
    @SerialName("syncedLyrics") val syncedLyrics: String? = null,
    @SerialName("language") val language: String? = null,
)
