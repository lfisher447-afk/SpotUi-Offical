// File: app/src/main/java/com/music/spotui/data/remote/InnerTubeService.kt
package com.music.spotui.data.remote

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.music.spotui.util.Result
import com.music.spotui.util.runSuspendCatching
import javax.inject.Inject
import javax.inject.Singleton

/** Hilt-owned adapter around the bundled InnerTube song-search client. */
@Singleton
class InnerTubeService @Inject constructor() {
    /** Searches music results and returns provider-neutral candidate metadata. */
    suspend fun searchSongs(query: String): Result<List<YouTubeTrackDto>> =
        runSuspendCatching("Unable to search YouTube Music right now") {
            check(query.isNotBlank()) { "A search query is required" }
            val result = YouTube.search(query.trim(), YouTube.SearchFilter.FILTER_SONG)
                .getOrElse { throw it }
            result.items.filterIsInstance<SongItem>().map { item ->
                YouTubeTrackDto(
                    id = item.id,
                    title = item.title,
                    artistNames = item.artists.map { it.name },
                    albumName = item.album?.name.orEmpty(),
                    artworkUrl = item.thumbnail,
                    durationMs = (item.duration ?: 0) * 1_000L,
                    explicit = item.explicit,
                )
            }
        }
}

/** A playable music-search candidate returned by the bundled resolver. */
data class YouTubeTrackDto(
    val id: String,
    val title: String,
    val artistNames: List<String>,
    val albumName: String,
    val artworkUrl: String?,
    val durationMs: Long,
    val explicit: Boolean,
)
