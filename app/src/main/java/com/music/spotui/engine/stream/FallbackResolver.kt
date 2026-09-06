// File: app/src/main/java/com/music/spotui/engine/stream/FallbackResolver.kt
package com.music.spotui.engine.stream

import com.music.spotui.data.models.TrackModel
import com.music.spotui.data.remote.YouTubeService
import com.music.spotui.data.remote.YouTubeTrackDto
import com.music.spotui.util.Result
import javax.inject.Inject
import javax.inject.Singleton

/** Finds a ranked YouTube Music fallback when a preferred provider cannot resolve a stream. */
@Singleton
class FallbackResolver @Inject constructor(
    private val isrcMatcher: ISRCMatcher,
    private val youTubeService: YouTubeService,
) {
    /** Returns candidates ordered from best metadata match to weakest match. */
    suspend fun candidatesFor(track: TrackModel): Result<List<YouTubeTrackDto>> = when (
        val result = youTubeService.searchTracks(listOf(track.title, track.artistNames.firstOrNull().orEmpty()).joinToString(" "))
    ) {
        is Result.Failure -> result
        Result.Loading -> Result.Loading
        is Result.Success -> Result.Success(
            result.value.sortedByDescending { candidate ->
                isrcMatcher.score(
                    targetIsrc = track.isrc,
                    candidateIsrc = null,
                    targetTitle = track.title,
                    candidateTitle = candidate.title,
                    targetArtist = track.artistNames.joinToString(" "),
                    candidateArtist = candidate.artistNames.joinToString(" "),
                    targetDurationMs = track.durationMs,
                    candidateDurationMs = candidate.durationMs,
                )
            },
        )
    }
}
