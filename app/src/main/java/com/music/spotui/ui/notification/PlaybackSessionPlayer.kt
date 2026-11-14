package com.music.spotui.ui.notification

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.FlagSet
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import com.music.spotui.di.CurrentSongState
import com.music.spotui.di.RepeatMode
import com.music.spotui.di.SongPlayer
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Session-facing [ForwardingPlayer] over [SongPlayer]'s active [ExoPlayer].
 *
 * Keeps `session.player` in a valid, non-empty Media3 state while a track's stream
 * URL is resolving (`isResolvingTrack == true`, before ExoPlayer has a `MediaItem`):
 * exposes a single-item [PlaceholderTimeline] carrying the track's [MediaMetadata]
 * alongside `STATE_BUFFERING` + `playWhenReady = true`, and notifies all registered
 * [Player.Listener]s (including `MediaSession` and `MediaNotificationManager`) on
 * state/metadata changes so System UI and One UI never collapse to "Tap to resume".
 */
@OptIn(UnstableApi::class)
internal class PlaybackSessionPlayer(
    base: Player,
    private val currentSongState: CurrentSongState,
    private val metadataProvider: () -> MediaMetadata?,
    private val isLikedProvider: (Int) -> Boolean,
    private val customLayoutUpdater: (Boolean, Boolean) -> Unit,
    private val onAdvance: (Boolean) -> Unit,
) : ForwardingPlayer(base) {

    private val listeners = CopyOnWriteArraySet<Player.Listener>()

    private var lastReportedTimeline: Timeline = super.getCurrentTimeline()
    private var lastReportedMediaItem: MediaItem? = super.getCurrentMediaItem()
    private var lastReportedMetadata: MediaMetadata = super.getMediaMetadata()
    private var lastReportedPlaybackState: Int = super.getPlaybackState()
    private var lastReportedPlayWhenReady: Boolean = super.getPlayWhenReady()

    private val baseStateTracker = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (!isUsingPlaceholder()) {
                lastReportedTimeline = currentTimeline
                lastReportedMediaItem = currentMediaItem
                lastReportedMetadata = mediaMetadata
                lastReportedPlaybackState = playbackState
                lastReportedPlayWhenReady = playWhenReady
            }
        }
    }

    init {
        base.addListener(baseStateTracker)
    }

    fun releaseTracker() {
        runCatching { wrappedPlayer.removeListener(baseStateTracker) }
        listeners.clear()
    }

    override fun addListener(listener: Player.Listener) {
        listeners.add(listener)
        super.addListener(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        listeners.remove(listener)
        super.removeListener(listener)
    }

    private fun isUsingPlaceholder(): Boolean {
        if (!wrappedPlayer.currentTimeline.isEmpty) return false
        return SongPlayer.isResolvingTrack || (SongPlayer.isPlaying() && !SongPlayer.webPlaybackActive())
    }

    private fun placeholderMetadata(): MediaMetadata {
        metadataProvider()?.let { return it }
        val song = currentSongState.queue.value.firstOrNull { it.id == currentSongState.songId.value }
            ?: currentSongState.queue.value.getOrNull(currentSongState.songIndex.value)
        val snap = SongPlayer.nowPlayingMetaSnapshot()
        val title = song?.title?.takeIf { it.isNotBlank() }
            ?: currentSongState.title.value.takeIf { it.isNotBlank() }
            ?: snap.title
        val artist = song?.singer?.takeIf { it.isNotBlank() }
            ?: currentSongState.singer.value.takeIf { it.isNotBlank() }
            ?: snap.artist
        val album = song?.album?.takeIf { it.isNotBlank() }
            ?: currentSongState.album.value.takeIf { it.isNotBlank() }
            ?: snap.album
        val builder = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setIsPlayable(true)
        val durationMs = (song?.durationMs?.toLong() ?: 0L).takeIf { it > 0L } ?: snap.durationMs
        if (durationMs > 0L) {
            builder.setDurationMs(durationMs)
        }
        return builder.build()
    }

    private fun placeholderMediaItem(meta: MediaMetadata): MediaItem {
        val songId = currentSongState.songId.value
        val mediaId = if (songId > 0) "song/$songId" else PLACEHOLDER_MEDIA_ID
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(meta)
            .build()
    }

    private fun placeholderDurationMs(): Long {
        val song = currentSongState.queue.value.firstOrNull { it.id == currentSongState.songId.value }
            ?: currentSongState.queue.value.getOrNull(currentSongState.songIndex.value)
        val queueDur = song?.durationMs?.toLong() ?: 0L
        if (queueDur > 0L) return queueDur
        return metadataProvider()?.durationMs?.takeIf { it > 0L }
            ?: SongPlayer.nowPlayingMetaSnapshot().durationMs.takeIf { it > 0L }
            ?: 0L
    }

    fun syncState() {
        val newTimeline = currentTimeline
        val newMediaItem = currentMediaItem
        val newMetadata = mediaMetadata
        val newPlaybackState = playbackState
        val newPlayWhenReady = playWhenReady

        val flagsBuilder = FlagSet.Builder()
        var hasEvents = false

        if (newTimeline != lastReportedTimeline) {
            lastReportedTimeline = newTimeline
            hasEvents = true
            flagsBuilder.add(Player.EVENT_TIMELINE_CHANGED)
            listeners.forEach {
                it.onTimelineChanged(newTimeline, Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED)
            }
        }
        if (newMediaItem != lastReportedMediaItem) {
            lastReportedMediaItem = newMediaItem
            hasEvents = true
            flagsBuilder.add(Player.EVENT_MEDIA_ITEM_TRANSITION)
            listeners.forEach {
                it.onMediaItemTransition(newMediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
            }
        }
        if (newMetadata != lastReportedMetadata) {
            lastReportedMetadata = newMetadata
            hasEvents = true
            flagsBuilder.add(Player.EVENT_MEDIA_METADATA_CHANGED)
            listeners.forEach { it.onMediaMetadataChanged(newMetadata) }
        }
        if (newPlaybackState != lastReportedPlaybackState) {
            lastReportedPlaybackState = newPlaybackState
            hasEvents = true
            flagsBuilder.add(Player.EVENT_PLAYBACK_STATE_CHANGED)
            listeners.forEach { it.onPlaybackStateChanged(newPlaybackState) }
        }
        if (newPlayWhenReady != lastReportedPlayWhenReady) {
            lastReportedPlayWhenReady = newPlayWhenReady
            hasEvents = true
            flagsBuilder.add(Player.EVENT_PLAY_WHEN_READY_CHANGED)
            listeners.forEach {
                it.onPlayWhenReadyChanged(newPlayWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            }
        }

        if (hasEvents) {
            val events = Player.Events(flagsBuilder.build())
            listeners.forEach { it.onEvents(this, events) }
        }
    }

    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon()
            .add(COMMAND_PLAY_PAUSE)
            .add(COMMAND_PREPARE)
            .add(COMMAND_STOP)
            .add(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            .add(COMMAND_SEEK_TO_DEFAULT_POSITION)
            .add(COMMAND_SEEK_TO_NEXT)
            .add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(COMMAND_SEEK_TO_PREVIOUS)
            .add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .add(COMMAND_SET_REPEAT_MODE)
            .add(COMMAND_SET_SHUFFLE_MODE)
            .add(COMMAND_GET_CURRENT_MEDIA_ITEM)
            .add(COMMAND_GET_TIMELINE)
            .add(COMMAND_GET_METADATA)
            .build()

    override fun isCommandAvailable(command: Int): Boolean = when (command) {
        COMMAND_PLAY_PAUSE,
        COMMAND_PREPARE,
        COMMAND_STOP,
        COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
        COMMAND_SEEK_TO_DEFAULT_POSITION,
        COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        COMMAND_SET_REPEAT_MODE,
        COMMAND_SET_SHUFFLE_MODE,
        COMMAND_GET_CURRENT_MEDIA_ITEM,
        COMMAND_GET_TIMELINE,
        COMMAND_GET_METADATA -> true

        else -> super.isCommandAvailable(command)
    }

    override fun getCurrentTimeline(): Timeline {
        val baseTimeline = super.getCurrentTimeline()
        if (!baseTimeline.isEmpty) return baseTimeline
        if (!isUsingPlaceholder()) return baseTimeline
        val meta = placeholderMetadata()
        return PlaceholderTimeline(placeholderMediaItem(meta), placeholderDurationMs())
    }

    override fun getMediaItemCount(): Int =
        if (isUsingPlaceholder()) 1 else super.getMediaItemCount()

    override fun getMediaItemAt(index: Int): MediaItem {
        if (isUsingPlaceholder() && index == 0) {
            return placeholderMediaItem(placeholderMetadata())
        }
        return super.getMediaItemAt(index)
    }

    override fun getCurrentMediaItem(): MediaItem? =
        if (isUsingPlaceholder()) placeholderMediaItem(placeholderMetadata()) else super.getCurrentMediaItem()

    override fun getCurrentMediaItemIndex(): Int =
        if (isUsingPlaceholder()) 0 else super.getCurrentMediaItemIndex()

    override fun getCurrentPeriodIndex(): Int =
        if (isUsingPlaceholder()) 0 else super.getCurrentPeriodIndex()

    override fun getDuration(): Long =
        if (isUsingPlaceholder()) {
            placeholderDurationMs().takeIf { it > 0L } ?: C.TIME_UNSET
        } else {
            super.getDuration()
        }

    override fun getCurrentPosition(): Long =
        if (isUsingPlaceholder()) 0L else super.getCurrentPosition()

    override fun getBufferedPosition(): Long =
        if (isUsingPlaceholder()) 0L else super.getBufferedPosition()

    override fun getContentPosition(): Long =
        if (isUsingPlaceholder()) 0L else super.getContentPosition()

    override fun getPlaybackState(): Int = when {
        SongPlayer.isResolvingTrack -> STATE_BUFFERING
        isUsingPlaceholder() -> STATE_BUFFERING
        else -> super.getPlaybackState()
    }

    override fun getPlayWhenReady(): Boolean =
        if (SongPlayer.isResolvingTrack || isUsingPlaceholder()) {
            SongPlayer.shouldPlayWhenResolved()
        } else {
            super.getPlayWhenReady()
        }

    override fun isPlaying(): Boolean =
        if (isUsingPlaceholder()) {
            false
        } else {
            super.isPlaying()
        }

    override fun getMediaMetadata(): MediaMetadata =
        metadataProvider() ?: if (isUsingPlaceholder()) placeholderMetadata() else super.getMediaMetadata()

    override fun play() {
        SongPlayer.play()
        currentSongState.setPlaying(true)
    }

    override fun pause() {
        SongPlayer.pause()
        currentSongState.setPlaying(false)
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (playWhenReady) play() else pause()
    }

    override fun stop() {
        SongPlayer.stop()
        currentSongState.setPlaying(false)
    }

    override fun getRepeatMode(): Int = when (currentSongState.repeat.value) {
        RepeatMode.OFF -> Player.REPEAT_MODE_OFF
        RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        RepeatMode.ALL -> Player.REPEAT_MODE_ALL
    }

    override fun setRepeatMode(repeatMode: Int) {
        val mapped = when (repeatMode) {
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            else -> RepeatMode.OFF
        }
        currentSongState.updateRepeatState(mapped)
    }

    override fun getShuffleModeEnabled(): Boolean = currentSongState.shuffle.value

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        currentSongState.updateShuffleState(shuffleModeEnabled)
        customLayoutUpdater(
            isLikedProvider(currentSongState.songId.value),
            shuffleModeEnabled,
        )
    }

    override fun hasNextMediaItem(): Boolean = currentSongState.queue.value.isNotEmpty()
    override fun hasPreviousMediaItem(): Boolean = currentSongState.queue.value.isNotEmpty()
    override fun seekToNext() = onAdvance(true)
    override fun seekToNextMediaItem() = onAdvance(true)
    override fun seekToPrevious() = onAdvance(false)
    override fun seekToPreviousMediaItem() = onAdvance(false)

    private class PlaceholderTimeline(
        private val mediaItem: MediaItem,
        private val durationMs: Long,
    ) : Timeline() {
        override fun getWindowCount(): Int = 1

        override fun getPeriodCount(): Int = 1

        override fun getWindow(
            windowIndex: Int,
            window: Window,
            defaultPositionProjectionUs: Long,
        ): Window {
            val durUs = if (durationMs > 0L) durationMs * 1000L else C.TIME_UNSET
            return window.set(
                PLACEHOLDER_WINDOW_UID,
                mediaItem,
                null,
                C.TIME_UNSET,
                C.TIME_UNSET,
                C.TIME_UNSET,
                false,
                false,
                null,
                0L,
                durUs,
                0,
                0,
                0L,
            )
        }

        override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period {
            val durUs = if (durationMs > 0L) durationMs * 1000L else C.TIME_UNSET
            val id = if (setIds) PLACEHOLDER_PERIOD_UID else null
            val uid = if (setIds) PLACEHOLDER_PERIOD_UID else null
            return period.set(id, uid, 0, durUs, 0L)
        }

        override fun getIndexOfPeriod(uid: Any): Int =
            if (uid == PLACEHOLDER_PERIOD_UID) 0 else C.INDEX_UNSET

        override fun getUidOfPeriod(periodIndex: Int): Any = PLACEHOLDER_PERIOD_UID

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is PlaceholderTimeline) return false
            return mediaItem == other.mediaItem && durationMs == other.durationMs
        }

        override fun hashCode(): Int {
            var result = mediaItem.hashCode()
            result = 31 * result + durationMs.hashCode()
            return result
        }
    }

    private companion object {
        const val PLACEHOLDER_MEDIA_ID = "spotui_resolving_track"
        val PLACEHOLDER_WINDOW_UID = Any()
        val PLACEHOLDER_PERIOD_UID = Any()
    }
}
