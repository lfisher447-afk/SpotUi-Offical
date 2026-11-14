package com.music.spotui.ui.notification

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.concurrent.futures.await
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.music.spotui.playback.PlaybackSessionBus
import com.music.spotui.util.AppDiagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MediaControllerSnapshot(
    val connected: Boolean = false,
    val isPlaying: Boolean = false,
    val playbackState: Int = Player.STATE_IDLE,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val title: String = "",
    val artist: String = "",
    val albumTitle: String = "",
    val artworkUri: String = "",
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
)

/**
 * UI-safe Media3 session bridge backed strictly by [SessionToken] and
 * [MediaController.Builder.buildAsync], synchronized with [PlaybackSessionBus] so idle-stopped
 * services aren't pinned alive by a dangling controller and background-started sessions reconnect.
 */
object MediaSessionController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var controllerFuture: ListenableFuture<MediaController>? = null
    @Volatile
    private var controller: MediaController? = null
    private var sessionObserverJob: Job? = null
    private var observedAppContext: Context? = null

    private val _state = MutableStateFlow(MediaControllerSnapshot())
    val state: StateFlow<MediaControllerSnapshot> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            refreshSnapshot(player)
        }
    }

    fun connect(context: Context) {
        val appContext = context.applicationContext
        ensureSessionObserver(appContext)
        connectInternal(appContext)
    }

    private fun ensureSessionObserver(appContext: Context) {
        observedAppContext = appContext
        if (sessionObserverJob?.isActive == true) return
        sessionObserverJob = scope.launch {
            PlaybackSessionBus.sessionAlive.collect { alive ->
                val ctx = observedAppContext ?: return@collect
                if (alive) {
                    connectInternal(ctx)
                } else {
                    disconnect()
                }
            }
        }
    }

    private fun connectInternal(appContext: Context) {
        if (controllerFuture != null || controller != null) return
        scope.launch {
            runCatching {
                val token = SessionToken(
                    appContext,
                    ComponentName(appContext, PlaybackService::class.java),
                )
                val future = MediaController.Builder(appContext, token).buildAsync()
                controllerFuture = future
                val resolved = future.await()
                controller = resolved
                resolved.addListener(listener)
                refreshSnapshot(resolved)
            }.onFailure { err ->
                AppDiagnostics.warning("MediaSessionController", "MediaController connection failed", err)
                controllerFuture = null
                controller = null
            }
        }
    }

    fun disconnect() {
        runCatching { controller?.removeListener(listener) }
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        _state.value = MediaControllerSnapshot()
    }

    fun playPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun play() {
        controller?.play()
    }

    fun pause() {
        controller?.pause()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun seekToNext() {
        controller?.seekToNextMediaItem()
    }

    fun seekToPrevious() {
        controller?.seekToPreviousMediaItem()
    }

    fun toggleFavorite() {
        sendCustomCommand("ACTION_FAVORITE")
    }

    fun toggleShuffle() {
        sendCustomCommand("ACTION_SHUFFLE")
    }

    fun cycleRepeat() {
        sendCustomCommand("ACTION_REPEAT")
    }

    fun sendCustomCommand(action: String, extras: Bundle = Bundle.EMPTY) {
        controller?.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), extras)
    }

    private fun refreshSnapshot(player: Player) {
        val meta: MediaMetadata = player.mediaMetadata
        _state.value = MediaControllerSnapshot(
            connected = true,
            isPlaying = player.isPlaying,
            playbackState = player.playbackState,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.coerceAtLeast(0L),
            title = meta.title?.toString().orEmpty(),
            artist = meta.artist?.toString().orEmpty(),
            albumTitle = meta.albumTitle?.toString().orEmpty(),
            artworkUri = meta.artworkUri?.toString().orEmpty(),
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
        )
    }
}
