// File: app/src/main/java/com/music/spotui/data/remote/YouTubeService.kt
package com.music.spotui.data.remote

import com.music.spotui.util.Result
import javax.inject.Inject
import javax.inject.Singleton

/** Public YouTube Music search facade used by repositories and stream resolution. */
@Singleton
class YouTubeService @Inject constructor(
    private val innerTubeService: InnerTubeService,
) {
    /** Searches song candidates without exposing InnerTube implementation details. */
    suspend fun searchTracks(query: String): Result<List<YouTubeTrackDto>> =
        innerTubeService.searchSongs(query)
}
