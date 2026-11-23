package com.music.spotui.ui.notification

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.runtime.snapshotFlow
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.music.spotui.MainActivity
import com.music.spotui.R
import com.music.spotui.data.api.Api
import com.music.spotui.data.api.Response
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.di.CurrentSongState
import com.music.spotui.di.RepeatMode
import com.music.spotui.di.SongPlayer
import com.music.spotui.di.SpotifyWebPlayer
import com.music.spotui.playback.MediaCommand
import com.music.spotui.playback.MediaCommandDispatcher
import com.music.spotui.playback.PlaybackSessionBus
import com.music.spotui.playback.QueueWindow
import com.music.spotui.playback.ResumePlanBuilder
import com.music.spotui.ui.repository.AppRepository
import com.music.spotui.util.AppDiagnostics
import com.music.spotui.widget.GlanceWidgetSink
import com.music.spotui.widget.NowPlayingWidgetSink
import com.music.spotui.widget.NowPlayingWidgetState
import com.music.spotui.widget.WidgetActionRouter
import com.music.spotui.widget.WidgetMediaIntents
import com.music.spotui.widget.WidgetUpdateDebouncer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Hosts a [MediaSession] over the app's single ExoPlayer (owned by [SongPlayer]).
 * This is what surfaces the track in the system notification center / lock screen
 * and routes the notification's transport controls (play/pause/seek/next/prev)
 * back into playback. Next/previous are wired to the in-app queue because our
 * player only ever holds one resolved stream at a time (YouTube URLs are resolved
 * lazily per track), so we advance the queue ourselves rather than via a playlist.
 *
 * It is a [MediaLibraryService] (not just a session service) so Android Auto can
 * browse the library — Liked Songs, Downloads, playlists and albums — and start
 * playback from the car.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    @Inject
    lateinit var currentSongState: CurrentSongState

    @Inject
    lateinit var repository: AppRepository

    private var mediaSession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Android Auto browse cache: mediaId → track, and mediaId → the list it was
    // browsed from (so playing a track queues its whole playlist/album).
    private val trackById = java.util.concurrent.ConcurrentHashMap<String, SongsModel>()
    private val queueByTrackId = java.util.concurrent.ConcurrentHashMap<String, List<SongsModel>>()
    private val searchCache = java.util.concurrent.ConcurrentHashMap<String, List<SongsModel>>()
    private var webPlayer: WebMediaPlayer? = null
    private var showingWeb = false
    @Volatile
    internal var hasPromotedForeground = false
        private set
    // Retry a transient stream/player failure once before skipping. The cap prevents an
    // unrecoverable source from looping forever while preserving playback on short URL/network hiccups.
    private val retryCountByQuery = java.util.concurrent.ConcurrentHashMap<String, Int>()

    @Volatile private var latestServiceMetadata: MediaMetadata? = null
    @Volatile private var radioExtending = false

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
                SongPlayer.onPlayerStateSettled(playbackState)
            }
            currentSongState.updateBufferingState(playbackState == Player.STATE_BUFFERING || SongPlayer.isResolvingTrack)
            if (playbackState == Player.STATE_ENDED ||
                playbackState == Player.STATE_IDLE ||
                playbackState == Player.STATE_READY
            ) {
                scheduleIdleStopCheck()
            }
            if (playbackState == Player.STATE_ENDED) {
                if (SongPlayer.isCrossfadeActive() || SongPlayer.isResolvingTrack) {
                    return
                }
                val p = SongPlayer.exoPlayer
                if (p != null) {
                    val duration = p.duration
                    val position = p.currentPosition
                    // Ignore only obviously bogus 0ms endings on long known-duration tracks;
                    // allow unknown durations (C.TIME_UNSET <= 0) and VBR header drift to advance.
                    if (duration > 15_000L && position <= 500L) {
                        AppDiagnostics.warning(
                            "PlaybackService",
                            "Ignoring premature STATE_ENDED at ${position}ms/${duration}ms",
                        )
                        return
                    }
                }
                when (currentSongState.repeat.value) {
                    RepeatMode.ONE -> {
                        val queue = currentSongState.queue.value
                        if (queue.isNotEmpty()) {
                            val curId = currentSongState.songId.value
                            val cur = queue.indexOfFirst { it.id == curId }
                                .let { if (it >= 0) it else currentSongState.songIndex.value }
                                .coerceIn(0, queue.size - 1)
                            val song = queue[cur]
                            SongPlayer.playSong(song.url, applicationContext, "song/${song.id}")
                        } else {
                            SongPlayer.exoPlayer?.seekTo(0)
                            SongPlayer.exoPlayer?.play()
                        }
                    }

                    RepeatMode.ALL -> {
                        advance(forward = true)
                    }

                    RepeatMode.OFF -> {
                        val queue = currentSongState.queue.value
                        if (queue.isNotEmpty()) {
                            val curId = currentSongState.songId.value
                            val cur = queue.indexOfFirst { it.id == curId }
                                .let { if (it >= 0) it else currentSongState.songIndex.value }
                                .coerceIn(0, queue.size - 1)
                            if (cur < queue.size - 1) {
                                advance(forward = true)
                            } else if (com.music.spotui.data.preferences.isAutoplayRadioEnabled(applicationContext) && !radioExtending) {
                                val seeds = queue.takeLast(5)
                                    .mapNotNull { it.spotifyTrackId.ifBlank { null } }
                                    .distinct()
                                if (seeds.isNotEmpty()) {
                                    radioExtending = true
                                    serviceScope.launch {
                                        try {
                                            val recs = kotlinx.coroutines.withContext(Dispatchers.IO) {
                                                repository.provideRecommendations(seeds)
                                            }
                                            val existing = currentSongState.queue.value
                                            val existingIds = existing.map { it.id }.toSet()
                                            val fresh = recs.filter { it.id !in existingIds }
                                            if (fresh.isNotEmpty()) {
                                                currentSongState.updateQueue(existing + fresh)
                                                advance(forward = true)
                                            }
                                        } catch (t: Throwable) {
                                            AppDiagnostics.warning("PlaybackService", "Background autoplay radio failed", t)
                                        } finally {
                                            radioExtending = false
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                val queue = currentSongState.queue.value
                val mediaId = mediaItem?.mediaId
                if (queue.isNotEmpty() && !mediaId.isNullOrBlank()) {
                    val cleanMediaId = mediaId.removePrefix("song/").trim()
                    val nextIdx = queue.indexOfFirst { it.id.toString() == cleanMediaId || "song/${it.id}" == mediaId }
                    if (nextIdx >= 0) {
                        val nextSong = queue[nextIdx]
                        SongPlayer.onAutoAdvanced(
                            songUrl = nextSong.url,
                            mediaId = "song/${nextSong.id}",
                            title = nextSong.title,
                            artist = nextSong.singer,
                            coverUri = nextSong.coverUri,
                            album = nextSong.album,
                            durationMs = nextSong.durationMs.toLong(),
                        )
                        currentSongState.updateSongState(
                            nextSong.coverUri, nextSong.title, nextSong.singer, true,
                            nextSong.id, nextIdx, nextSong.album
                        )
                        // Trigger background pre-resolution for the following track (N+2)
                        SongPlayer.preResolveAndQueueNextTrack(applicationContext)
                        syncSessionStateFromApp()
                    }
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                currentSongState.queue.value.getOrNull(currentSongState.songIndex.value)?.url?.let { retryCountByQuery.remove(it) }
            }
            // Mirror ExoPlayer's real play/pause state into the in-app UI so the
            // on-screen play button stays in sync when the notification controls are used.
            // updatePlayingState internally guards against stale callbacks: when ExoPlayer
            // reports "playing" after the user already paused, it's dropped.
            if (!showingWeb) currentSongState.updatePlayingState(isPlaying)
            com.music.spotui.engine.MediaNotificationAdapter.syncExitMiniIfShowing(
                applicationContext,
                isPlayingOverride = isPlaying,
            )
            scheduleIdleStopCheck()
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            val isDashRedirect = generateSequence<Throwable>(error) { it.cause }
                .any { it is SongPlayer.DashRedirectException }
            if (isDashRedirect) {
                android.util.Log.d("PlaybackService", "Ignoring synthetic DashRedirectException during MPD handoff")
                return
            }
            android.util.Log.e("PlaybackService", "Player error during playback: ${error.message}", error)
            val queue = currentSongState.queue.value
            val curId = currentSongState.songId.value
            val cur = queue.indexOfFirst { it.id == curId }
            val current = queue.getOrNull(cur)
            if (current == null) {
                currentSongState.updatePlayingState(false)
                return
            }
            SongPlayer.invalidateResolvedStream(current.url)
            val attempts = (retryCountByQuery[current.url] ?: 0) + 1
            retryCountByQuery[current.url] = attempts
            if (attempts == 1 && com.music.spotui.data.preferences.downloadedPathForQuery(applicationContext, current.url) != null) {
                AppDiagnostics.warning(
                    "PlaybackService",
                    "Local download failed to decode for '${current.title}'; removing corrupt file and streaming fresh source",
                )
                com.music.spotui.data.preferences.removeDownload(applicationContext, current.id.toString())
            }
            if (attempts <= 1) {
                serviceScope.launch {
                    kotlinx.coroutines.delay(650)
                    if (currentSongState.songId.value == current.id) {
                        SongPlayer.playSong(current.url, applicationContext, "song/${current.id}")
                    }
                }
            } else {
                retryCountByQuery.remove(current.url)
                val autoSkip = com.music.spotui.data.preferences.isAutoSkipUnavailable(applicationContext)
                val hasNext = cur >= 0 && (cur < queue.lastIndex || currentSongState.repeat.value == RepeatMode.ALL)
                if (autoSkip && hasNext) {
                    advance(forward = true)
                } else {
                    currentSongState.updateResolveError(error.message ?: "Playback error")
                    currentSongState.updatePlayingState(false)
                }
            }
        }
    }

    private var smartUnplugHandler: com.music.spotui.engine.SmartUnplugHandler? = null
    private var widgetSink: NowPlayingWidgetSink? = null
    private var widgetDebouncer: WidgetUpdateDebouncer? = null
    private var idleStopJob: Job? = null

    private data class MetaSyncKey(
        val songId: Int,
        val title: String,
        val artist: String,
        val album: String,
        val artworkUrl: String,
    )

    private data class WidgetStateKey(
        val songId: Int,
        val title: String,
        val artist: String,
        val album: String,
        val artworkUrl: String,
        val isPlaying: Boolean,
        val isLiked: Boolean,
    )

    override fun onCreate() {
        super.onCreate()
        try {
            val appCtx = applicationContext
            val sink = GlanceWidgetSink(appCtx).also { widgetSink = it }
            widgetDebouncer = WidgetUpdateDebouncer(
                scope = serviceScope,
                clock = { SystemClock.elapsedRealtime() },
                publish = { state -> sink.publish(state) },
            )

            MediaCommandDispatcher.start(
                scope = serviceScope,
                windowMs = MediaCommandDispatcher.DEFAULT_DEBOUNCE_WINDOW_MS,
            ) { command ->
                when (command) {
                    MediaCommand.Play -> {
                        if (currentSongState.queue.value.isEmpty() || (SongPlayer.exoPlayer?.mediaItemCount ?: 0) == 0) {
                            resumeFromPersistence(skipFirst = false)
                        } else {
                            SongPlayer.play()
                        }
                    }
                    MediaCommand.Pause, MediaCommand.Stop -> SongPlayer.pause()
                    MediaCommand.PlayPause -> {
                        if (currentSongState.queue.value.isEmpty() || (SongPlayer.exoPlayer?.mediaItemCount ?: 0) == 0) {
                            resumeFromPersistence(skipFirst = false)
                        } else {
                            SongPlayer.togglePlay()
                        }
                    }
                    MediaCommand.Next -> {
                        if (currentSongState.queue.value.isEmpty()) {
                            resumeFromPersistence(skipFirst = true)
                        } else {
                            SongPlayer.next(appCtx)
                        }
                    }
                    MediaCommand.Previous -> {
                        if (currentSongState.queue.value.isEmpty()) {
                            resumeFromPersistence(skipFirst = false)
                        } else {
                            SongPlayer.previous(appCtx)
                        }
                    }
                    is MediaCommand.Seek -> SongPlayer.seekTo(command.positionMs)
                }
                scheduleIdleStopCheck()
            }

            SongPlayer.ensureCreated(this)
            smartUnplugHandler = com.music.spotui.engine.SmartUnplugHandler(this).apply {
                register()
            }

            com.music.spotui.engine.MediaNotificationAdapter.ensureChannel(this)
            setListener(object : Listener {
                override fun onForegroundServiceStartNotAllowedException() {
                    AppDiagnostics.warning(
                        "PlaybackService",
                        "OS blocked foreground promotion from background; session stays bound",
                    )
                }
            })
            setShowNotificationForIdlePlayer(SHOW_NOTIFICATION_FOR_IDLE_PLAYER_AFTER_STOP_OR_ERROR)
            setMediaNotificationProvider(SpotuiMediaNotificationProvider(this))

            // Let the player advance the in-app queue itself during a crossfade.
            SongPlayer.bindState(currentSongState)
            val base = SongPlayer.exoPlayer ?: return
            base.repeatMode = if (currentSongState.repeat.value == RepeatMode.ONE) {
                Player.REPEAT_MODE_ONE
            } else {
                Player.REPEAT_MODE_OFF
            }
            base.addListener(playerListener)

            // Tapping the notification opens the app (back on the Now Playing screen).
            val activityIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val sessionActivity = PendingIntent.getActivity(
                this, 0, activityIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            webPlayer = WebMediaPlayer(mainLooper, currentSongState) { forward -> advance(forward) }

            val session = MediaLibrarySession.Builder(this, base, LibraryCallback())
                .setSessionActivity(sessionActivity)
                .build()
            mediaSession = session
            activeInstance = this
            ServiceSessionHolder.attach(this, session)
            addSession(session)
            syncSessionPlayer()
            PlaybackSessionBus.onServiceCreated()

            // Sync current song metadata to the MediaSession when track or metadata changes
            serviceScope.launch {
                snapshotFlow {
                    MetaSyncKey(
                        songId = currentSongState.songId.value,
                        title = currentSongState.title.value,
                        artist = currentSongState.singer.value,
                        album = currentSongState.album.value,
                        artworkUrl = currentSongState.coverUri.value,
                    )
                }
                    .distinctUntilChanged()
                    .collect { _ ->
                        runCatching { updateCurrentMetadata() }
                            .onFailure { AppDiagnostics.warning("PlaybackService", "Metadata sync failed", it) }
                    }
            }

            // Keep native ExoPlayer single-track loop synchronized with RepeatMode.ONE so
            // background single-track repeats never hit STATE_ENDED or drop WIU privileges.
            serviceScope.launch {
                snapshotFlow { currentSongState.repeat.value }
                    .distinctUntilChanged()
                    .collect { mode ->
                        if (!showingWeb) {
                            SongPlayer.exoPlayer?.repeatMode = if (mode == RepeatMode.ONE) {
                                Player.REPEAT_MODE_ONE
                            } else {
                                Player.REPEAT_MODE_OFF
                            }
                        }
                    }
            }

            // When a crossfade promotes a new ExoPlayer instance, re-bind the session to it
            // (runs on the main thread; setPlayer is the supported way to swap a session's player).
            SongPlayer.onPlayerSwapped = { newPlayer ->
                if (!showingWeb) {
                    newPlayer.repeatMode = if (currentSongState.repeat.value == RepeatMode.ONE) {
                        Player.REPEAT_MODE_ONE
                    } else {
                        Player.REPEAT_MODE_OFF
                    }
                    mediaSession?.player = newPlayer
                }
                newPlayer.addListener(playerListener)
            }

            // Keep the One UI notification custom slots (Favorite & Shuffle) synchronized
            // whenever the active song, liked status, or shuffle state changes.
            serviceScope.launch {
                snapshotFlow {
                    Triple(
                        currentSongState.songId.value,
                        currentSongState.likeState.value,
                        currentSongState.shuffle.value,
                    )
                }
                    .distinctUntilChanged()
                    .collect { (songId, _, shuffled) ->
                        val liked = isCurrentSongLiked(songId)
                        runCatching { mediaSession?.setCustomLayout(buildCustomLayout(liked, shuffled)) }
                            .onFailure { AppDiagnostics.warning("PlaybackService", "Custom layout sync failed", it) }
                    }
            }

            // Sync widget likes back into CurrentSongState + MediaSession custom layout.
            WidgetActionRouter.onToggleLike = { songId, isLikedNow ->
                if (currentSongState.songId.value == songId) {
                    currentSongState.updateLikeState(isLikedNow)
                    runCatching {
                        mediaSession?.setCustomLayout(
                            buildCustomLayout(isLiked = isLikedNow, isShuffled = currentSongState.shuffle.value)
                        )
                    }
                }
            }

            // Observe settled playback state -> debounced Glance widget updates + 5-min idle watchdog.
            serviceScope.launch {
                snapshotFlow {
                    val songId = currentSongState.songId.value
                    WidgetStateKey(
                        songId = songId,
                        title = currentSongState.title.value,
                        artist = currentSongState.singer.value,
                        album = currentSongState.album.value,
                        artworkUrl = currentSongState.coverUri.value,
                        isPlaying = currentSongState.playingState.value,
                        isLiked = isCurrentSongLiked(songId),
                    )
                }
                    .distinctUntilChanged()
                    .collect { key ->
                        if (key.title.isNotBlank()) {
                            widgetDebouncer?.submit(
                                NowPlayingWidgetState(
                                    songId = key.songId,
                                    title = key.title,
                                    artist = key.artist,
                                    album = key.album,
                                    artworkUrl = key.artworkUrl,
                                    isPlaying = key.isPlaying,
                                    isLiked = key.isLiked,
                                    updatedAtMs = System.currentTimeMillis(),
                                )
                            )
                        }
                        scheduleIdleStopCheck()
                    }
            }

            // As the hidden web player streams, keep the notification in sync and swap
            // the session between the web player (during web playback) and the ExoPlayer.
            SpotifyWebPlayer.onStateChanged = {
                syncSessionPlayer()
                if (showingWeb) {
                    webPlayer?.refresh()
                    // Reflect the web player's real play/pause state into the in-app UI
                    // so the on-screen icon matches after the notification's pause.
                    currentSongState.updatePlayingState(SpotifyWebPlayer.isPlaying)
                    runCatching { triggerNotificationUpdate() }
                }
            }

            // Register a receiver for legacy music control broadcasts used by
            // automation apps (MacroDroid, Tasker, etc.).
            val musicFilter = IntentFilter().apply {
                addAction(Intent.ACTION_MEDIA_BUTTON)
                addAction("com.android.music.musicservicecommand")
                addAction("com.android.music.play")
                addAction("com.android.music.pause")
                addAction("com.android.music.next")
                addAction("com.android.music.previous")
                addAction("com.android.music.togglepause")
                addAction("com.android.music.stop")
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(mediaControlReceiver, musicFilter, Context.RECEIVER_EXPORTED)
                } else {
                    registerReceiver(mediaControlReceiver, musicFilter)
                }
            }.onFailure { AppDiagnostics.warning("PlaybackService", "Media control receiver registration failed", it) }
        } catch (t: Throwable) {
            AppDiagnostics.error("PlaybackService", "PlaybackService initialization failed", t)
        }
    }

    /**
     * Updates the cached MediaMetadata surfaced through [wrap] without calling
     * `player.replaceMediaItem(...)` on live playing streams (which interrupts playback
     * and causes Samsung One UI Now Bar to flash "Resume playback").
     */
    private fun updateCurrentMetadata() {
        val song = currentSongState.queue.value.firstOrNull { it.id == currentSongState.songId.value }
            ?: currentSongState.queue.value.getOrNull(currentSongState.songIndex.value)
        val title = song?.title?.takeIf { it.isNotBlank() } ?: currentSongState.title.value
        val artist = song?.singer?.takeIf { it.isNotBlank() } ?: currentSongState.singer.value
        val album = song?.album?.takeIf { it.isNotBlank() } ?: currentSongState.album.value
        val coverUri = song?.coverUri?.takeIf { it.isNotBlank() } ?: currentSongState.coverUri.value
        val durationMs = song?.durationMs ?: 0
        if (title.isBlank() && song == null) return

        val songId = (song?.id ?: currentSongState.songId.value).toString()
        val meta = com.music.spotui.util.ArtworkHelper.buildTrackMetadata(
            context = this,
            title = title,
            artist = artist,
            album = album,
            coverUri = coverUri,
            durationMs = durationMs.toLong(),
            songId = songId,
            streamUrl = song?.url ?: currentSongState.songUrl.value,
            onAsyncArtReady = {
                mainHandler.post { refreshNotification(applicationContext) }
            },
        )
        latestServiceMetadata = meta
        ServiceSessionHolder.updateMetadata(meta)

        // Propagate metadata onto the active ExoPlayer MediaItem only when a real
        // resolved stream URI (not a pending spotui:// resolve item) is loaded so Media3
        // fires onMediaMetadataChanged without disturbing stream resolution.
        val player = SongPlayer.exoPlayer
        if (!showingWeb && player != null && player.mediaItemCount > 0) {
            val idx = player.currentMediaItemIndex
            val currentItem = if (idx in 0 until player.mediaItemCount) player.getMediaItemAt(idx) else null
            val scheme = currentItem?.localConfiguration?.uri?.scheme
            if (currentItem?.localConfiguration != null && scheme != "spotui" && currentItem.mediaMetadata != meta) {
                player.replaceMediaItem(idx, currentItem.buildUpon().setMediaMetadata(meta).build())
            }
        }
        syncSessionStateFromApp()
        com.music.spotui.engine.MediaNotificationAdapter.syncExitMiniIfShowing(applicationContext)
        android.util.Log.d(
            "PlaybackService",
            "Updated MediaMetadata: title='$title', artist='$artist', album='$album', durationMs=$durationMs"
        )
    }

    private fun syncSessionStateFromApp() {
        syncSessionPlayer()
        if (showingWeb) {
            webPlayer?.refresh()
        }
        val activePlayer = mediaSession?.player
        if (showingWeb || (activePlayer != null && activePlayer.mediaItemCount > 0)) {
            runCatching { triggerNotificationUpdate() }
        }
    }

    /** Point the media session at whichever engine is currently producing audio. */
    private fun syncSessionPlayer() {
        val wantWeb = SongPlayer.webPlaybackActive()
        if (wantWeb == showingWeb) return
        showingWeb = wantWeb
        val session = mediaSession ?: return
        if (wantWeb) {
            val wp = webPlayer ?: return
            session.player = wp
            wp.refresh()
        } else {
            val exo = SongPlayer.exoPlayer ?: return
            session.player = exo
        }
        runCatching { triggerNotificationUpdate() }
    }

    /**
     * Receives legacy music control broadcasts from automation apps (MacroDroid,
     * Tasker) that use the "com.android.music.musicservicecommand" pattern or
     * direct action intents to control media playback.
     */
    private val mediaControlReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            android.util.Log.d(
                "PlaybackService",
                "BroadcastReceiver: action=${intent.action} extras=${intent.extras?.keySet()}"
            )
            when (intent.action) {
                Intent.ACTION_MEDIA_BUTTON -> {
                    val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                    android.util.Log.d(
                        "PlaybackService",
                        "BroadcastReceiver: ACTION_MEDIA_BUTTON keyCode=${keyEvent?.keyCode}"
                    )
                    if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                        when (keyEvent.keyCode) {
                            KeyEvent.KEYCODE_MEDIA_NEXT -> SongPlayer.next(context)
                            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> SongPlayer.previous(context)
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> SongPlayer.togglePlay()
                            KeyEvent.KEYCODE_MEDIA_PLAY -> SongPlayer.play()
                            KeyEvent.KEYCODE_MEDIA_PAUSE -> SongPlayer.pause()
                            KeyEvent.KEYCODE_MEDIA_STOP -> SongPlayer.pause()
                        }
                    }
                    if (isOrderedBroadcast) abortBroadcast()
                }

                "com.android.music.musicservicecommand" -> {
                    when (intent.getStringExtra("command")) {
                        "play" -> SongPlayer.play()
                        "pause" -> SongPlayer.pause()
                        "togglepause", "toggle" -> SongPlayer.togglePlay()
                        "next" -> SongPlayer.next(context)
                        "previous" -> SongPlayer.previous(context)
                        "stop" -> SongPlayer.pause()
                    }
                }

                "com.android.music.play" -> SongPlayer.play()
                "com.android.music.pause" -> SongPlayer.pause()
                "com.android.music.next" -> SongPlayer.next(context)
                "com.android.music.previous" -> SongPlayer.previous(context)
                "com.android.music.togglepause" -> SongPlayer.togglePlay()
                "com.android.music.stop" -> SongPlayer.pause()
            }
        }
    }



    /** Advance the in-app queue one step in the given direction and start it. */
    private fun advance(forward: Boolean) {
        if (forward) SongPlayer.next(applicationContext) else SongPlayer.previous(applicationContext)
    }

    private val localBinder = LocalBinder()

    inner class LocalBinder : android.os.Binder() {
        fun getService(): PlaybackService = this@PlaybackService
    }

    override fun onBind(intent: Intent?): android.os.IBinder? {
        // Always let MediaSessionService/MediaLibraryService handle MediaSession and MediaBrowser bindings first
        val superBinder = super.onBind(intent)
        if (superBinder != null) {
            return superBinder
        }
        return if (intent?.action == ACTION_LOCAL_BIND) {
            localBinder
        } else {
            null
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaSession

    // ── Android Auto browse tree ──────────────────────────────────────────

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = com.music.spotui.engine.MediaNotificationAdapter.CHANNEL_ID
        const val ACTION_ENSURE_FOREGROUND = "com.music.spotui.action.ENSURE_FOREGROUND"
        const val ACTION_REFRESH_NOTIFICATION = "com.music.spotui.action.REFRESH_NOTIFICATION"
        const val ACTION_LOCAL_BIND = "com.music.spotui.action.LOCAL_BIND"
        const val ACTION_MEDIA_NOTIFICATION_DISMISSED = "com.music.spotui.action.MEDIA_NOTIFICATION_DISMISSED"
        const val ACTION_BACKGROUND_PLAY_PAUSE = "com.music.spotui.action.BACKGROUND_PLAY_PAUSE"
        const val ACTION_BACKGROUND_NEXT = "com.music.spotui.action.BACKGROUND_NEXT"
        const val ACTION_BACKGROUND_PREV = "com.music.spotui.action.BACKGROUND_PREV"

        private const val ROOT = "root"
        private const val NODE_LIKED = "liked"
        private const val NODE_DOWNLOADS = "downloads"
        private const val NODE_PLAYLISTS = "playlists"
        private const val NODE_ALBUMS = "albums"
        internal const val ACTION_FAVORITE = "ACTION_FAVORITE"
        internal const val ACTION_SHUFFLE = "ACTION_SHUFFLE"
        internal const val ACTION_REPEAT = "ACTION_REPEAT"
        internal const val ACTION_CLOSE = "ACTION_CLOSE"
        private const val IDLE_STOP_TIMEOUT_MS = 5 * 60_000L

        @Volatile
        internal var activeInstance: PlaybackService? = null
            private set

        private val mainHandler = Handler(Looper.getMainLooper())

        fun ensureStarted(context: Context) {
            val appContext = context.applicationContext
            val existing = activeInstance
            if (existing != null) {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    existing.syncSessionStateFromApp()
                } else {
                    mainHandler.post { existing.syncSessionStateFromApp() }
                }
                if (existing.hasPromotedForeground) {
                    return
                }
            }
            val intent = Intent(appContext, PlaybackService::class.java).apply {
                action = ACTION_ENSURE_FOREGROUND
            }
            runCatching {
                ContextCompat.startForegroundService(appContext, intent)
            }.onFailure {
                runCatching { appContext.startService(intent) }
            }
        }

        fun triggerBackgroundCommand(context: Context, commandAction: String) {
            val appContext = context.applicationContext
            val existing = activeInstance
            if (existing != null) {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    existing.handleBackgroundCommand(commandAction)
                } else {
                    mainHandler.post { existing.handleBackgroundCommand(commandAction) }
                }
                return
            }
            val intent = Intent(appContext, PlaybackService::class.java).apply {
                action = commandAction
            }
            runCatching {
                ContextCompat.startForegroundService(appContext, intent)
            }.onFailure {
                runCatching { appContext.startService(intent) }
            }
        }

        fun notifyPlaybackStateChanged(context: Context) {
            val service = activeInstance
            if (service != null) {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    service.syncSessionStateFromApp()
                } else {
                    mainHandler.post { service.syncSessionStateFromApp() }
                }
                return
            }
            if (SongPlayer.isPlaying() || SongPlayer.isResolvingTrack) {
                ensureStarted(context)
            }
        }

        fun refreshNotification(context: Context) {
            notifyPlaybackStateChanged(context)
        }
    }

    internal fun handleBackgroundCommand(commandAction: String) {
        com.music.spotui.util.DevConsoleManager.logInfo(
            "PlaybackService",
            "handleBackgroundCommand($commandAction) playing=${SongPlayer.isPlaying()} pwr=${SongPlayer.exoPlayer?.playWhenReady} resolving=${SongPlayer.isResolvingTrack}",
        )
        when (commandAction) {
            ACTION_BACKGROUND_PLAY_PAUSE -> {
                val p = SongPlayer.exoPlayer
                val isCurrentlyActive = SongPlayer.isPlaying() ||
                    (p?.playWhenReady == true && p.playbackState != Player.STATE_ENDED) ||
                    (SongPlayer.isResolvingTrack && currentSongState.playingState.value)
                if (isCurrentlyActive) {
                    SongPlayer.pause()
                } else if (currentSongState.queue.value.isEmpty() || (p?.mediaItemCount ?: 0) == 0) {
                    val resumed = resumeFromPersistence(skipFirst = false)
                    if (resumed == null) {
                        SongPlayer.play()
                    }
                } else {
                    SongPlayer.play()
                }
            }
            ACTION_BACKGROUND_NEXT -> {
                if (currentSongState.queue.value.isEmpty()) {
                    resumeFromPersistence(skipFirst = true)
                } else {
                    SongPlayer.next(applicationContext)
                }
            }
            ACTION_BACKGROUND_PREV -> {
                if (currentSongState.queue.value.isEmpty()) {
                    resumeFromPersistence(skipFirst = false)
                } else {
                    SongPlayer.previous(applicationContext)
                }
            }
        }
        syncSessionStateFromApp()
        scheduleIdleStopCheck()
        com.music.spotui.engine.MediaNotificationAdapter.syncExitMiniIfShowing(applicationContext)
    }

    private fun scheduleIdleStopCheck() {
        idleStopJob?.cancel()
        val isActive = SongPlayer.isPlaying() ||
            SongPlayer.isResolvingTrack ||
            SpotifyWebPlayer.isPlaying ||
            currentSongState.playingState.value
        if (isActive || currentSongState.title.value.isBlank()) {
            idleStopJob = null
            return
        }
        idleStopJob = serviceScope.launch {
            delay(IDLE_STOP_TIMEOUT_MS)
            val stillIdle = !SongPlayer.isPlaying() &&
                !SongPlayer.isResolvingTrack &&
                !SpotifyWebPlayer.isPlaying &&
                !currentSongState.playingState.value
            if (stillIdle) {
                widgetDebouncer?.reset()
                widgetSink?.serviceStopped()
                PlaybackSessionBus.onServiceStopping()
                runCatching { ServiceCompat.stopForeground(this@PlaybackService, ServiceCompat.STOP_FOREGROUND_REMOVE) }
                stopSelf()
            }
        }
    }

    private fun resumeFromPersistence(skipFirst: Boolean): Pair<List<SongsModel>, Int>? {
        val savedQueue = com.music.spotui.data.preferences.loadLastQueue(applicationContext)
        val savedLast = com.music.spotui.data.preferences.loadLastPlayback(applicationContext)
        val plan = ResumePlanBuilder.buildWithFallback(
            savedQueue = savedQueue,
            lastSong = savedLast?.first,
            lastPositionMs = savedLast?.second ?: 0L,
            skipFirst = skipFirst,
        ) ?: return null

        currentSongState.updateQueue(plan.queue)
        val targetSong = plan.startSong
        currentSongState.updateSongState(
            targetSong.coverUri,
            targetSong.title,
            targetSong.singer,
            true,
            targetSong.id,
            plan.startIndex,
            targetSong.album,
        )
        if (plan.positionMs > 0L) {
            SongPlayer.setRestorePoint(targetSong.url, plan.positionMs)
        }
        SongPlayer.playSong(targetSong.url, applicationContext, "song/${targetSong.id}")
        return plan.queue to plan.startIndex
    }

    private fun isCurrentSongLiked(songId: Int = currentSongState.songId.value): Boolean =
        songId > 0 && com.music.spotui.data.preferences.isSongLiked(applicationContext, songId.toString())

    /**
     * Builds the One UI & notification custom layout (max 2 custom slots):
     * Slot 1: [ACTION_FAVORITE] — toggles liked status for the playing track.
     * Slot 2: [ACTION_SHUFFLE]  — toggles shuffle state for the active queue.
     */
    private fun buildCustomLayout(
        isLiked: Boolean = isCurrentSongLiked(),
        isShuffled: Boolean = currentSongState.shuffle.value,
    ): ImmutableList<CommandButton> {
        val favoriteButton = CommandButton.Builder()
            .setDisplayName(if (isLiked) "Favorited" else "Favorite")
            .setSessionCommand(SessionCommand(ACTION_FAVORITE, Bundle.EMPTY))
            .setIconResId(if (isLiked) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border)
            .build()

        val shuffleButton = CommandButton.Builder()
            .setDisplayName(if (isShuffled) "Shuffle On" else "Shuffle")
            .setSessionCommand(SessionCommand(ACTION_SHUFFLE, Bundle.EMPTY))
            .setIconResId(if (isShuffled) R.drawable.ic_player_shuffle else R.drawable.ic_shuffle_off)
            .build()

        return ImmutableList.of(favoriteButton, shuffleButton)
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        /**
         * Intercept media button events at the earliest point — BEFORE the session
         * checks command availability or calls onPlayerCommandRequest. This ensures
         * next/previous work even when MacroDroid dispatches a key event without
         * being a connected MediaController.
         */
        override fun onMediaButtonEvent(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            intent: Intent,
        ): Boolean {
            if (WidgetMediaIntents.isSkipFirst(intent)) {
                if (currentSongState.queue.value.isNotEmpty() && SongPlayer.exoPlayer != null) {
                    SongPlayer.next(applicationContext)
                } else {
                    resumeFromPersistence(skipFirst = true)
                }
                return true
            }
            val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                ?: return false
            if (keyEvent.action != KeyEvent.ACTION_DOWN) return false
            val command = when (keyEvent.keyCode) {
                KeyEvent.KEYCODE_MEDIA_NEXT -> MediaCommand.Next
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> MediaCommand.Previous
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> MediaCommand.PlayPause
                KeyEvent.KEYCODE_MEDIA_PLAY -> MediaCommand.Play
                KeyEvent.KEYCODE_MEDIA_PAUSE -> MediaCommand.Pause
                KeyEvent.KEYCODE_MEDIA_STOP -> MediaCommand.Stop
                else -> null
            } ?: return false

            MediaCommandDispatcher.dispatchMediaCommand(command)
            return true
        }

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val baseResult = super.onConnect(session, controller)
            val sessionCommands = baseResult.availableSessionCommands.buildUpon()
                .add(SessionCommand(ACTION_FAVORITE, Bundle.EMPTY))
                .add(SessionCommand(ACTION_SHUFFLE, Bundle.EMPTY))
                .add(SessionCommand(ACTION_REPEAT, Bundle.EMPTY))
                .add(SessionCommand(ACTION_CLOSE, Bundle.EMPTY))
                .build()
            val playerCommands = baseResult.availablePlayerCommands.buildUpon()
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(playerCommands)
                .setCustomLayout(buildCustomLayout())
                .build()
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onPlayerCommandRequest(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            playerCommand: Int,
        ): Int {
            when (playerCommand) {
                Player.COMMAND_PLAY_PAUSE -> {
                    val p = SongPlayer.exoPlayer
                    val isCurrentlyActive = SongPlayer.isPlaying() ||
                        (p?.playWhenReady == true && p.playbackState != Player.STATE_ENDED) ||
                        currentSongState.playingState.value
                    if (isCurrentlyActive) {
                        SongPlayer.pause()
                    } else if (currentSongState.queue.value.isEmpty() || (p?.mediaItemCount ?: 0) == 0) {
                        resumeFromPersistence(skipFirst = false)
                    } else {
                        SongPlayer.play()
                    }
                    syncSessionStateFromApp()
                    scheduleIdleStopCheck()
                    return SessionResult.RESULT_INFO_SKIPPED
                }
                Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                    if (currentSongState.queue.value.isNotEmpty() && (SongPlayer.exoPlayer?.mediaItemCount ?: 0) > 0) {
                        SongPlayer.next(applicationContext)
                    } else {
                        resumeFromPersistence(skipFirst = true)
                    }
                    scheduleIdleStopCheck()
                    return SessionResult.RESULT_INFO_SKIPPED
                }
                Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                    if (currentSongState.queue.value.isEmpty() || (SongPlayer.exoPlayer?.mediaItemCount ?: 0) == 0) {
                        resumeFromPersistence(skipFirst = false)
                        scheduleIdleStopCheck()
                        return SessionResult.RESULT_INFO_SKIPPED
                    }
                    val posMs = runCatching { session.player.currentPosition }.getOrDefault(0L)
                    if (posMs <= 3_500L && currentSongState.queue.value.size > 1) {
                        SongPlayer.previous(applicationContext)
                        scheduleIdleStopCheck()
                        return SessionResult.RESULT_INFO_SKIPPED
                    }
                }
            }
            return super.onPlayerCommandRequest(session, controller, playerCommand)
        }


        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_FAVORITE -> {
                    val songId = currentSongState.songId.value
                    if (songId > 0) {
                        val key = songId.toString()
                        val likedNow = com.music.spotui.data.preferences.isSongLiked(applicationContext, key)
                        if (likedNow) {
                            com.music.spotui.data.preferences.removeLikedSongId(applicationContext, key)
                        } else {
                            com.music.spotui.data.preferences.addLikedSongId(applicationContext, key)
                        }
                        val nextLiked = !likedNow
                        currentSongState.updateLikeState(nextLiked)
                        mediaSession?.setCustomLayout(
                            buildCustomLayout(isLiked = nextLiked, isShuffled = currentSongState.shuffle.value)
                        )
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                ACTION_SHUFFLE -> {
                    val nextShuffle = !currentSongState.shuffle.value
                    currentSongState.updateShuffleState(nextShuffle)
                    mediaSession?.setCustomLayout(
                        buildCustomLayout(isLiked = isCurrentSongLiked(), isShuffled = nextShuffle)
                    )
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                ACTION_CLOSE -> {
                    // End playback through the service lifecycle. Killing the whole process here
                    // is indistinguishable from a crash and can interrupt writes or state cleanup.
                    SongPlayer.stop()
                    runCatching { ServiceCompat.stopForeground(this@PlaybackService, ServiceCompat.STOP_FOREGROUND_REMOVE) }
                    stopSelf()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                ACTION_REPEAT -> {
                    // Cycle OFF → ALL → ONE → OFF (matches full-screen UI)
                    val next = when (currentSongState.repeat.value) {
                        RepeatMode.OFF -> RepeatMode.ALL
                        RepeatMode.ALL -> RepeatMode.ONE
                        RepeatMode.ONE -> RepeatMode.OFF
                    }
                    currentSongState.updateRepeatState(next)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(folder(ROOT, "spotui"), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
            LibraryResult.ofItemList(ImmutableList.copyOf(childrenOf(parentId)), params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(
            trackById[mediaId]?.let { LibraryResult.ofItem(playable(it), null) }
                ?: LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE),
        )

        // A browsed track was tapped in the car: queue the list it came from and
        // play through our own engine (streams are resolved lazily per track, so
        // we never hand the session a playlist of URIs).
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val requested = mediaItems.getOrNull(startIndex) ?: mediaItems.firstOrNull()
            val song = requested?.let { trackById[it.mediaId] }
            if (song != null) {
                val queue = queueByTrackId[requested.mediaId] ?: listOf(song)
                currentSongState.updateQueue(queue)
                val idx = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                currentSongState.updateSongState(
                    song.coverUri, song.title, song.singer, true,
                    song.id, idx, song.album,
                )
                SongPlayer.playSong(song.url, applicationContext, "song/${song.id}")
            }
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(emptyList(), C.INDEX_UNSET, C.TIME_UNSET),
            )
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val resumed = resumeFromPersistence(skipFirst = false)
                ?: return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(emptyList(), C.INDEX_UNSET, C.TIME_UNSET)
                )
            val slice = QueueWindow.slice(resumed.first, resumed.second)
            val windowItems = slice.items.map { playable(it) }
            val startPosMs = com.music.spotui.data.preferences.loadLastPlayback(applicationContext)?.second ?: 0L
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(
                    windowItems,
                    slice.activeIndexInWindow,
                    startPosMs.coerceAtLeast(0L),
                )
            )
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> = future {
            if (query.isNotBlank()) {
                try {
                    val songs = lastSuccess(repository.searchSongs(query)).orEmpty()
                    searchCache[query] = songs
                    registerTracks("search/$query", songs)
                    session.notifySearchResultChanged(browser, query, songs.size, params)
                } catch (e: Exception) {
                    android.util.Log.e("PlaybackService", "Error in onSearch for query: $query", e)
                }
            }
            LibraryResult.ofVoid()
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val songs = searchCache[query].orEmpty()
            val items = songs.map { playable(it) }
            return Futures.immediateFuture(
                LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
            )
        }
    }

    private suspend fun childrenOf(parentId: String): List<MediaItem> = try {
        // Enforce a strict fallback timeout for Android Auto responsiveness
        kotlinx.coroutines.withTimeoutOrNull(8000) {
            when {
                parentId == ROOT -> listOf(
                    folder(
                        NODE_LIKED,
                        "Liked Songs",
                        mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
                    ),
                    folder(
                        NODE_DOWNLOADS,
                        "Downloads",
                        mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
                    ),
                    folder(
                        NODE_PLAYLISTS,
                        "Playlists",
                        mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
                    ),
                    folder(
                        NODE_ALBUMS,
                        "Albums",
                        mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
                    ),
                )

                parentId == NODE_LIKED ->
                    registerTracks(
                        NODE_LIKED,
                        lastSuccess(repository.provideLikedSongs()).orEmpty()
                    )

                parentId == NODE_DOWNLOADS ->
                    registerTracks(
                        NODE_DOWNLOADS,
                        com.music.spotui.data.preferences.getDownloadedSongs(applicationContext),
                    )

                parentId == NODE_PLAYLISTS ->
                    libraryEntries().filter {
                        it.isPlaylist && it.spotifyId != Api.LIKED_SONGS_ID && it.spotifyId != Api.DOWNLOADS_ID
                    }.map {
                        folder(
                            "playlist/${it.spotifyId}",
                            it.name,
                            it.coverUri,
                            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
                        )
                    }

                parentId == NODE_ALBUMS ->
                    libraryEntries().filter { !it.isPlaylist }.map {
                        folder(
                            "album/${android.net.Uri.encode(it.name)}/${android.net.Uri.encode(it.artists)}",
                            it.name,
                            it.coverUri,
                            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
                        )
                    }

                parentId.startsWith("playlist/") -> {
                    val songs =
                        lastSuccess(repository.providePlaylistSongs(parentId.removePrefix("playlist/")))
                    registerTracks(parentId, songs.orEmpty())
                }

                parentId.startsWith("album/") -> {
                    val parts = parentId.removePrefix("album/").split('/')
                    val name = android.net.Uri.decode(parts.getOrElse(0) { "" })
                    val artist = android.net.Uri.decode(parts.getOrElse(1) { "" })
                    registerTracks(
                        parentId,
                        lastSuccess(repository.provideAlbumSongs(name, artist)).orEmpty()
                    )
                }

                else -> emptyList()
            }
        } ?: emptyList()
    } catch (e: Exception) {
        android.util.Log.e("PlaybackService", "Error retrieving children for $parentId", e)
        emptyList()
    }

    private suspend fun libraryEntries() =
        lastSuccess(repository.provideLibrary()).orEmpty()

    /** Runs a paged/cached response flow to completion and keeps the final data. */
    private suspend fun <T> lastSuccess(flow: Flow<Response<T>>): T? {
        var result: T? = null
        try {
            // Android Auto requires quick responses to avoid infinite loading screens.
            // An 8000ms timeout ensures we never hang the media browser thread.
            kotlinx.coroutines.withTimeoutOrNull(8000) {
                flow.collect { response ->
                    if (response is Response.Success) {
                        result = response.data
                        // Found a success emission (cached or fresh). Cancel immediately to return fast.
                        throw kotlinx.coroutines.CancellationException("Success received")
                    } else if (response is Response.Error) {
                        throw kotlinx.coroutines.CancellationException("Error received")
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Expected cancellation to stop collecting
        } catch (e: Exception) {
            android.util.Log.e("PlaybackService", "Error in lastSuccess flow collection", e)
        }
        return result
    }

    private fun registerTracks(parentId: String, songs: List<SongsModel>): List<MediaItem> {
        songs.forEach { song ->
            trackById["song/${song.id}"] = song
            queueByTrackId["song/${song.id}"] = songs
        }
        return songs.map { playable(it) }
    }

    private fun folder(
        id: String,
        title: String,
        coverUri: String = "",
        mediaType: Int = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
    ): MediaItem {
        val builder = MediaMetadata.Builder()
            .setTitle(title)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(mediaType)
        if (coverUri.isNotBlank()) {
            com.music.spotui.util.ArtworkHelper.attachArtwork(
                builder, this, coverUri, id.removePrefix("song/").removePrefix("playlist/").removePrefix("album/")
            )
        }
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(builder.build())
            .build()
    }

    private fun playable(song: SongsModel): MediaItem {
        val meta = com.music.spotui.util.ArtworkHelper.buildTrackMetadata(
            context = this,
            title = song.title,
            artist = song.singer,
            album = song.album,
            coverUri = song.coverUri,
            durationMs = song.durationMs.toLong(),
            songId = song.id.toString(),
            streamUrl = song.url,
        ).buildUpon()
            .setIsBrowsable(false)
            .build()
        return MediaItem.Builder()
            .setMediaId("song/${song.id}")
            .setMediaMetadata(meta)
            .build()
    }

    private fun <T> future(block: suspend () -> T): ListenableFuture<T> {
        val f = SettableFuture.create<T>()
        serviceScope.launch {
            try {
                f.set(block())
            } catch (e: Exception) {
                f.setException(e)
            }
        }
        return f
    }

    private fun promoteForegroundNowIfNeeded() {
        com.music.spotui.engine.MediaNotificationAdapter.ensureChannel(this)
        val existingLiveNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                nm.activeNotifications.firstOrNull { it.id == NOTIFICATION_ID }?.notification
            }.getOrNull()
        } else {
            null
        }
        val notification = existingLiveNotification ?: run {
            val snap = SongPlayer.nowPlayingMetaSnapshot()
            val title = snap.title.ifBlank { currentSongState.title.value }.ifBlank { "SpotUI" }
            val artist = snap.artist.ifBlank { currentSongState.singer.value }
            val isActive = SongPlayer.isPlaying() || currentSongState.playingState.value || SongPlayer.isResolvingTrack
            com.music.spotui.engine.MediaNotificationAdapter.buildNotification(
                context = this,
                title = title,
                artist = artist,
                coverBitmap = null,
                isPlaying = isActive,
            )
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            hasPromotedForeground = true
        }.onFailure { err ->
            AppDiagnostics.warning("PlaybackService", "Immediate foreground promotion failed", err)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_MEDIA_NOTIFICATION_DISMISSED) {
            val shouldKeepAlive = SongPlayer.isPlaying() ||
                SpotifyWebPlayer.isPlaying ||
                SongPlayer.isResolvingTrack
            if (!shouldKeepAlive) {
                hasPromotedForeground = false
                idleStopJob?.cancel()
                SongPlayer.pause()
                widgetDebouncer?.reset()
                widgetSink?.serviceStopped()
                PlaybackSessionBus.onServiceStopping()
                runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
                stopSelf()
                return START_NOT_STICKY
            }
            promoteForegroundNowIfNeeded()
            syncSessionStateFromApp()
            super.onStartCommand(intent, flags, startId)
            return START_STICKY
        }

        // Satisfy Android 12+/17 startForegroundService() within 5s for every non-dismiss launch
        // (Media3 DefaultActionFactory PendingIntents, WidgetKeyRelay, or ACTION_ENSURE_FOREGROUND).
        // When already in foreground with a live notification, promoteForegroundNowIfNeeded reuses it
        // seamlessly with zero visual flicker.
        promoteForegroundNowIfNeeded()

        if (action == Intent.ACTION_MEDIA_BUTTON) {
            if (WidgetMediaIntents.isSkipFirst(intent)) {
                if (currentSongState.queue.value.isNotEmpty() && (SongPlayer.exoPlayer?.mediaItemCount ?: 0) > 0) {
                    SongPlayer.next(applicationContext)
                } else {
                    resumeFromPersistence(skipFirst = true)
                }
            } else {
                val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                if (intent.getBooleanExtra(WidgetMediaIntents.EXTRA_FROM_WIDGET_RELAY, false)) {
                    if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                        when (keyEvent.keyCode) {
                            KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP -> SongPlayer.pause()
                            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                                if (currentSongState.queue.value.isEmpty() || (SongPlayer.exoPlayer?.mediaItemCount ?: 0) == 0) {
                                    resumeFromPersistence(skipFirst = false)
                                } else {
                                    SongPlayer.play()
                                }
                            }
                            KeyEvent.KEYCODE_MEDIA_NEXT -> SongPlayer.next(applicationContext)
                            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> SongPlayer.previous(applicationContext)
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> SongPlayer.togglePlay()
                        }
                        scheduleIdleStopCheck()
                    }
                } else if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN &&
                    (keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY || keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                ) {
                    if (currentSongState.queue.value.isEmpty() || (SongPlayer.exoPlayer?.mediaItemCount ?: 0) == 0) {
                        resumeFromPersistence(skipFirst = false)
                    } else {
                        SongPlayer.play()
                    }
                    scheduleIdleStopCheck()
                }
            }
        } else if (action == ACTION_BACKGROUND_PLAY_PAUSE ||
            action == ACTION_BACKGROUND_NEXT ||
            action == ACTION_BACKGROUND_PREV
        ) {
            handleBackgroundCommand(action)
        } else if (action == ACTION_ENSURE_FOREGROUND || action == ACTION_REFRESH_NOTIFICATION) {
            scheduleIdleStopCheck()
        }

        syncSessionStateFromApp()
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep playing seamlessly in background if playback is active or resolving a track.
        // Stop cleanly if playback is paused/idle so the notification isn't left orphaned,
        // and show the quick Play/Pause mini-notification if enabled.
        val p = mediaSession?.player
        val keepAlive = ServiceSessionHolder.isResumable(p) &&
            (p?.playWhenReady == true || SongPlayer.isResolvingTrack || SpotifyWebPlayer.isPlaying)
        if (!keepAlive) {
            hasPromotedForeground = false
            widgetDebouncer?.reset()
            widgetSink?.serviceStopped()
            PlaybackSessionBus.onServiceStopping()
            runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
            if (com.music.spotui.data.preferences.isBackgroundExitNotificationEnabled(applicationContext)) {
                com.music.spotui.engine.MediaNotificationAdapter.showOrUpdateExitMiniNotification(
                    applicationContext,
                    isPlayingOverride = false,
                )
            }
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        if (activeInstance === this) activeInstance = null
        hasPromotedForeground = false
        ServiceSessionHolder.detach(this)
        idleStopJob?.cancel()
        idleStopJob = null
        MediaCommandDispatcher.stop()
        WidgetActionRouter.onToggleLike = null
        widgetDebouncer?.reset()
        widgetDebouncer = null
        widgetSink?.serviceStopped()
        PlaybackSessionBus.onServiceStopping()

        runCatching { smartUnplugHandler?.unregister() }
        smartUnplugHandler = null
        runCatching { serviceScope.cancel() }
        runCatching { unregisterReceiver(mediaControlReceiver) }
        runCatching { SongPlayer.exoPlayer?.removeListener(playerListener) }
        SongPlayer.onPlayerSwapped = null
        SpotifyWebPlayer.onStateChanged = null

        runCatching { webPlayer?.release() }
        webPlayer = null
        // The player belongs to this MediaLibraryService, not the UI activity. Release it only
        // when the service is genuinely being destroyed, after notification/session teardown.
        runCatching { SongPlayer.release() }
        runCatching { SpotifyWebPlayer.release() }
        searchCache.clear()
        runCatching { mediaSession?.release() }
        mediaSession = null
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
        super.onDestroy()
    }
}
