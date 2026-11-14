package com.music.spotui.ui.notification

import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.FlagSet
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
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
 * Maintains Android 17 / One UI 9 foreground continuity and consistent Media3
 * session state across initial track resolution and track-to-track advances:
 * - Never exposes `STATE_IDLE` or `STATE_ENDED` between queued tracks; transitions
 *   straight from `STATE_READY` (track N) to `STATE_BUFFERING` (track N+1) with a
 *   non-empty [PlaceholderTimeline] and `playWhenReady = true`.
 * - Owns its [Player.Listener] dispatch so `MediaSession` and System UI never see
 *   contradictory raw ExoPlayer events and always receive `onIsPlayingChanged` +
 *   the required `Player.Events` flags.
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

    private var lastReportedTimeline: Timeline = currentTimeline
    private var lastReportedMediaItem: MediaItem? = currentMediaItem
    private var lastReportedMetadata: MediaMetadata = mediaMetadata
    private var lastReportedPlaybackState: Int = playbackState
    private var lastReportedPlayWhenReady: Boolean = playWhenReady
    private var lastReportedIsPlaying: Boolean = isPlaying

    private val baseListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncState(forwardedBaseEvents = if (isUsingPlaceholder()) null else events)
        }

        override fun onPlayerError(error: PlaybackException) {
            listeners.forEach { it.onPlayerError(error) }
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            listeners.forEach { it.onPlaybackParametersChanged(playbackParameters) }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            listeners.forEach { it.onRepeatModeChanged(repeatMode) }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            listeners.forEach { it.onShuffleModeEnabledChanged(shuffleModeEnabled) }
        }

        override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
            listeners.forEach { it.onAvailableCommandsChanged(this@PlaybackSessionPlayer.availableCommands) }
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            listeners.forEach { it.onAudioSessionIdChanged(audioSessionId) }
        }

        override fun onAudioAttributesChanged(audioAttributes: AudioAttributes) {
            listeners.forEach { it.onAudioAttributesChanged(audioAttributes) }
        }

        override fun onVolumeChanged(volume: Float) {
            listeners.forEach { it.onVolumeChanged(volume) }
        }

        override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) {
            listeners.forEach { it.onDeviceInfoChanged(deviceInfo) }
        }

        override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) {
            listeners.forEach { it.onDeviceVolumeChanged(volume, muted) }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (!isUsingPlaceholder()) {
                listeners.forEach { it.onPositionDiscontinuity(oldPosition, newPosition, reason) }
            }
        }
    }

    init {
        base.addListener(baseListener)
    }

    fun releaseTracker() {
        runCatching { wrappedPlayer.removeListener(baseListener) }
        listeners.clear()
    }

    override fun addListener(listener: Player.Listener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        listeners.remove(listener)
    }

    private fun wrappedPlayerMatchesCurrentSong(): Boolean {
        val activeSongId = currentSongState.songId.value
        if (activeSongId <= 0) return true
        val wrappedId = wrappedPlayer.currentMediaItem?.mediaId ?: return false
        return wrappedId == "song/$activeSongId"
    }

    private fun isTransitioningBetweenTracks(): Boolean =
        SongPlayer.isResolvingTrack ||
            (wrappedPlayer.playbackState == Player.STATE_ENDED &&
                SongPlayer.shouldPlayWhenResolved() &&
                SongPlayer.hasUpcomingQueueTrack())

    private fun isUsingPlaceholder(): Boolean {
        if (SongPlayer.webPlaybackActive()) return false
        val emptyTimeline = wrappedPlayer.currentTimeline.isEmpty
        if (emptyTimeline && (SongPlayer.isResolvingTrack || SongPlayer.isPlaying())) {
            return true
        }
        return isTransitioningBetweenTracks() && (
            emptyTimeline ||
                wrappedPlayer.playbackState == Player.STATE_IDLE ||
                wrappedPlayer.playbackState == Player.STATE_ENDED ||
                !wrappedPlayerMatchesCurrentSong()
            )
    }

    private fun activeTrackTitle(): String {
        val song = currentSongState.queue.value.firstOrNull { it.id == currentSongState.songId.value }
            ?: currentSongState.queue.value.getOrNull(currentSongState.songIndex.value)
        val snap = SongPlayer.nowPlayingMetaSnapshot()
        return song?.title?.takeIf { it.isNotBlank() }
            ?: currentSongState.title.value.takeIf { it.isNotBlank() }
            ?: snap.title
    }

    private fun freshServiceMetadata(): MediaMetadata? {
        val cached = metadataProvider() ?: return null
        val expectedTitle = activeTrackTitle()
        if (expectedTitle.isBlank()) return cached
        return if (cached.title?.toString() == expectedTitle) cached else null
    }

    private fun placeholderMetadata(): MediaMetadata {
        freshServiceMetadata()?.let { return it }
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
        return freshServiceMetadata()?.durationMs?.takeIf { it > 0L }
            ?: SongPlayer.nowPlayingMetaSnapshot().durationMs.takeIf { it > 0L }
            ?: 0L
    }

    fun syncState(forwardedBaseEvents: Player.Events? = null) {
        val newTimeline = currentTimeline
        val newMediaItem = currentMediaItem
        val newMetadata = mediaMetadata
        val newPlaybackState = playbackState
        val newPlayWhenReady = playWhenReady
        val newIsPlaying = isPlaying

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
        if (newIsPlaying != lastReportedIsPlaying) {
            lastReportedIsPlaying = newIsPlaying
            hasEvents = true
            flagsBuilder.add(Player.EVENT_IS_PLAYING_CHANGED)
            listeners.forEach { it.onIsPlayingChanged(newIsPlaying) }
        }

        if (forwardedBaseEvents != null) {
            for (i in 0 until forwardedBaseEvents.size()) {
                val flag = forwardedBaseEvents.get(i)
                if (flag !in MANAGED_EVENT_FLAGS) {
                    flagsBuilder.add(flag)
                    hasEvents = true
                }
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
        if (isUsingPlaceholder()) {
            val meta = placeholderMetadata()
            return PlaceholderTimeline(placeholderMediaItem(meta), placeholderDurationMs())
        }
        return super.getCurrentTimeline()
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
        isTransitioningBetweenTracks() -> STATE_BUFFERING
        isUsingPlaceholder() -> STATE_BUFFERING
        else -> super.getPlaybackState()
    }

    override fun getPlayWhenReady(): Boolean =
        if (isTransitioningBetweenTracks() || isUsingPlaceholder()) {
            SongPlayer.shouldPlayWhenResolved()
        } else {
            super.getPlayWhenReady()
        }

    override fun isPlaying(): Boolean =
        playbackState == Player.STATE_READY &&
            playWhenReady &&
            playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE

    override fun getMediaMetadata(): MediaMetadata =
        if (isUsingPlaceholder()) {
            placeholderMetadata()
        } else {
            freshServiceMetadata() ?: super.getMediaMetadata()
        }

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
        val MANAGED_EVENT_FLAGS = intArrayOf(
            Player.EVENT_TIMELINE_CHANGED,
            Player.EVENT_MEDIA_ITEM_TRANSITION,
            Player.EVENT_MEDIA_METADATA_CHANGED,
            Player.EVENT_PLAYBACK_STATE_CHANGED,
            Player.EVENT_PLAY_WHEN_READY_CHANGED,
            Player.EVENT_IS_PLAYING_CHANGED,
        )
    }
}
