// File: app/src/main/java/com/music/spotui/engine/playback/SongPlayer.kt
package com.music.spotui.engine.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.music.spotui.data.models.TrackModel
import com.music.spotui.util.Logger
import com.music.spotui.util.UrlValidator
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Reactive snapshot of the active Media3 player. */
data class PlaybackState(
    val currentTrack: TrackModel? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val errorMessage: String? = null,
)

/** Hilt-managed Media3 wrapper with a StateFlow suitable for view-model consumption. */
@Singleton
class SongPlayer @Inject constructor(
    private val exoPlayer: ExoPlayer,
) : Player.Listener {
    private val mutableState = MutableStateFlow(PlaybackState())
    private val tracksByMediaId = mutableMapOf<String, TrackModel>()

    /** Streams the active playback state. */
    val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

    /** Exposes the service-owned Media3 player to the MediaSession service. */
    val media3Player: Player get() = exoPlayer

    init {
        exoPlayer.addListener(this)
    }

    /** Replaces the current item with [track] playing from the trusted [streamUrl]. */
    @UnstableApi
    fun play(track: TrackModel, streamUrl: String) {
        val safeUrl = requireNotNull(UrlValidator.sanitizeRemoteStreamUrl(streamUrl)) { "Unsafe stream URL" }
        tracksByMediaId[track.id] = track
        exoPlayer.setMediaItem(track.asMediaItem(safeUrl))
        exoPlayer.prepare()
        exoPlayer.play()
        mutableState.value = mutableState.value.copy(currentTrack = track, errorMessage = null)
    }

    /** Resumes the active item if Media3 has one. */
    fun resume() {
        exoPlayer.play()
    }

    /** Pauses the active item while retaining its position. */
    fun pause() {
        exoPlayer.pause()
    }

    /** Toggles the active playback state. */
    fun toggle() {
        if (exoPlayer.isPlaying) pause() else resume()
    }

    /** Seeks the active stream to [positionMs], clamping negative values to zero. */
    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs.coerceAtLeast(0L))
        refreshProgress()
    }

    /** Refreshes elapsed and total duration values for a UI progress ticker. */
    fun refreshProgress() {
        mutableState.value = mutableState.value.copy(
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
            durationMs = exoPlayer.duration.coerceAtLeast(0L),
        )
    }

    /** Stops audio and releases this process-scoped Media3 instance. */
    fun release() {
        exoPlayer.removeListener(this)
        exoPlayer.release()
        mutableState.value = PlaybackState()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        mutableState.value = mutableState.value.copy(isPlaying = isPlaying)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        mutableState.value = mutableState.value.copy(
            isBuffering = playbackState == Player.STATE_BUFFERING,
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
            durationMs = exoPlayer.duration.coerceAtLeast(0L),
        )
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val track = mediaItem?.mediaId?.let(tracksByMediaId::get)
        mutableState.value = mutableState.value.copy(currentTrack = track, errorMessage = null)
    }

    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
        Logger.error("SongPlayer", "Media3 playback failed", error)
        mutableState.value = mutableState.value.copy(errorMessage = error.message)
    }

    @UnstableApi
    private fun TrackModel.asMediaItem(streamUrl: String): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setUri(streamUrl)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artistNames.joinToString(", "))
                .setAlbumTitle(albumName)
                .setArtworkUri(artworkUrl?.let(android.net.Uri::parse))
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build(),
        )
        .build()
}
