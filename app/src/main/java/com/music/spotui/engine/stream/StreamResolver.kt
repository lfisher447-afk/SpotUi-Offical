// File: app/src/main/java/com/music/spotui/engine/stream/StreamResolver.kt
package com.music.spotui.engine.stream

import android.content.Context
import com.music.spotui.data.models.TrackModel
import com.music.spotui.di.SongPlayer as LegacySongPlayer
import com.music.spotui.util.Result
import com.music.spotui.util.UrlValidator
import com.music.spotui.util.runSuspendCatching
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** A validated direct stream and the provider strategy that produced it. */
data class ResolvedStream(
    val url: String,
    val source: String,
    val quality: StreamQuality,
)

/** Resolves user-authorized Spotify metadata through the bundled YouTube-capable media pipeline. */
@Singleton
class StreamResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fallbackResolver: FallbackResolver,
) {
    /** Resolves [track] to a short-lived, safe direct HTTPS stream URL. */
    suspend fun resolve(track: TrackModel, quality: StreamQuality = StreamQuality.BALANCED): Result<ResolvedStream> =
        runSuspendCatching("Unable to resolve a playable stream") {
            val query = LegacySongPlayer.buildSpotifyPlayQuery(
                spotifyTrackId = track.providerId,
                title = track.title,
                artist = track.artistNames.joinToString(" "),
            )
            val resolved = LegacySongPlayer.resolveStreamUrl(query, context, forPlayback = false)
                ?: fallbackResolver.candidatesFor(track).let { candidates ->
                    val fallback = (candidates as? Result.Success)?.value?.firstOrNull()
                    fallback?.let {
                        LegacySongPlayer.resolveStreamUrl(
                            LegacySongPlayer.buildSpotifyPlayQuery("", it.title, it.artistNames.joinToString(" ")),
                            context,
                            forPlayback = false,
                        )
                    }
                }
            val safeUrl = requireNotNull(UrlValidator.sanitizeRemoteStreamUrl(resolved)) { "Provider returned an unsafe stream URL" }
            ResolvedStream(safeUrl, source = "Resolved provider", quality = quality)
        }
}
