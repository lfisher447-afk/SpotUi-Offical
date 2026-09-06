// File: app/src/main/java/com/music/spotui/engine/playback/PlaybackController.kt
package com.music.spotui.engine.playback

import com.music.spotui.data.models.TrackModel
import com.music.spotui.engine.stream.StreamResolver
import com.music.spotui.util.Result
import javax.inject.Inject
import javax.inject.Singleton

/** Coordinates queue intent, stream resolution, and the service-owned Media3 player. */
@Singleton
class PlaybackController @Inject constructor(
    private val queueManager: QueueManager,
    private val songPlayer: SongPlayer,
    private val streamResolver: StreamResolver,
) {
    /** Replaces the queue and resolves then plays its selected track. */
    suspend fun playQueue(tracks: List<TrackModel>, startIndex: Int = 0): Result<Unit> {
        queueManager.replace(tracks, startIndex)
        return queueManager.state.value.current()?.let { play(it) } ?: Result.Success(Unit)
    }

    /** Resolves and starts [track], selecting it in the queue when present. */
    suspend fun play(track: TrackModel): Result<Unit> = when (val resolved = streamResolver.resolve(track)) {
        is Result.Failure -> resolved
        Result.Loading -> Result.Loading
        is Result.Success -> {
            songPlayer.play(track, resolved.value.url)
            Result.Success(Unit)
        }
    }

    /** Resolves and starts the next queued item. */
    suspend fun playNext(): Result<Unit> = queueManager.advance()?.let { play(it) } ?: Result.Success(Unit)

    /** Resolves and starts the previous queued item. */
    suspend fun playPrevious(): Result<Unit> = queueManager.rewind()?.let { play(it) } ?: Result.Success(Unit)

    /** Pauses the active track. */
    fun pause() = songPlayer.pause()

    /** Resumes the active track. */
    fun resume() = songPlayer.resume()

    /** Toggles active playback. */
    fun toggle() = songPlayer.toggle()

    /** Seeks active playback to [positionMs]. */
    fun seekTo(positionMs: Long) = songPlayer.seekTo(positionMs)
}
