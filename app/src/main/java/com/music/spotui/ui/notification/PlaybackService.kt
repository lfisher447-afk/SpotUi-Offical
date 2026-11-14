package com.music.spotui.ui.notification

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.runtime.snapshotFlow
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
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
    // Retry a transient stream/player failure once before skipping. The cap prevents an
    // unrecoverable source from looping forever while preserving playback on short URL/network hiccups.
    private val retryCountByQuery = java.util.concurrent.ConcurrentHashMap<String, Int>()

    @Volatile private var latestServiceMetadata: MediaMetadata? = null
    @Volatile private var radioExtending = false

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            currentSongState.updateBufferingState(playbackState == Player.STATE_BUFFERING || SongPlayer.isResolvingTrack)
            if (playbackState == Player.STATE_ENDED) {
                if (SongPlayer.isCrossfadeActive() || SongPlayer.isResolvingTrack) {
                    return
                }
                val p = SongPlayer.exoPlayer
                if (p != null) {
                    val duration = p.duration
                    val position = p.currentPosition
                    if (duration <= 0 || position < duration - 6000) {
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
                        currentSongState.updateSongState(
                            nextSong.coverUri, nextSong.title, nextSong.singer, true,
                            nextSong.id, nextIdx, nextSong.album
                        )
                        // Trigger background pre-resolution for the following track (N+2)
                        SongPlayer.preResolveAndQueueNextTrack(applicationContext)
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
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
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
            setShowNotificationForIdlePlayer(SHOW_NOTIFICATION_FOR_IDLE_PLAYER_AFTER_STOP_OR_ERROR)

            // Order notification buttons: [favorite | prev | play/pause | next | shuffle]
            val notificationProvider = object : DefaultMediaNotificationProvider(
                this,
                DefaultMediaNotificationProvider.NotificationIdProvider { NOTIFICATION_ID },
                CHANNEL_ID,
                R.string.playback_notification_channel_name,
            ) {
                init {
                    setSmallIcon(R.drawable.ic_spotui_notification)
                }

                override fun getMediaButtons(
                    session: MediaSession,
                    playerCommands: Player.Commands,
                    customLayout: ImmutableList<CommandButton>,
                    showPauseButton: Boolean
                ): ImmutableList<CommandButton> {
                    return runCatching {
                        val base =
                            super.getMediaButtons(session, playerCommands, customLayout, showPauseButton)
                        val favoriteBtn = base.find { it.sessionCommand?.customAction == ACTION_FAVORITE }
                        val shuffleBtn = base.find { it.sessionCommand?.customAction == ACTION_SHUFFLE }
                        val transport = base.filterNotNull().filter {
                            val act = it.sessionCommand?.customAction
                            act != ACTION_FAVORITE &&
                                act != ACTION_SHUFFLE &&
                                act != ACTION_REPEAT &&
                                act != ACTION_CLOSE &&
                                act != "ACTION_NONE"
                        }
                        val builder = ImmutableList.builder<CommandButton>()
                        favoriteBtn?.let { builder.add(it) }
                        builder.addAll(transport)
                        shuffleBtn?.let { builder.add(it) }
                        builder.build()
                    }.getOrElse {
                        runCatching { super.getMediaButtons(session, playerCommands, customLayout, showPauseButton) }
                            .getOrDefault(ImmutableList.of())
                    }
                }
            }
            setMediaNotificationProvider(notificationProvider)

            // Let the player advance the in-app queue itself during a crossfade.
            SongPlayer.bindState(currentSongState)
            val base = SongPlayer.exoPlayer ?: return
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

            val session = MediaLibrarySession.Builder(this, wrap(base), LibraryCallback())
                .setSessionActivity(sessionActivity)
                .build()
            mediaSession = session
            addSession(session)
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

            // When a crossfade promotes a new ExoPlayer instance, re-bind the session to it
            // (runs on the main thread; setPlayer is the supported way to swap a session's player).
            SongPlayer.onPlayerSwapped = { newPlayer ->
                if (!showingWeb) mediaSession?.player = wrap(newPlayer)
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

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setIsPlayable(true)
        if (durationMs > 0) {
            metadataBuilder.setDurationMs(durationMs.toLong())
        }

        com.music.spotui.util.ArtworkHelper.attachArtwork(
            metadataBuilder,
            this,
            coverUri,
            (song?.id ?: currentSongState.songId.value).toString(),
            song?.url ?: currentSongState.songUrl.value,
        )

        val meta = metadataBuilder.build()
        latestServiceMetadata = meta

        // Propagate metadata onto the active ExoPlayer MediaItem only when a real
        // stream URI (localConfiguration != null) is already loaded so Media3 fires
        // onMediaMetadataChanged and updates the system media card without touching
        // URI-less items.
        val player = SongPlayer.exoPlayer
        if (!showingWeb && player != null && player.mediaItemCount > 0) {
            val idx = player.currentMediaItemIndex
            val currentItem = if (idx in 0 until player.mediaItemCount) player.getMediaItemAt(idx) else null
            if (currentItem?.localConfiguration != null && currentItem.mediaMetadata != meta) {
                player.replaceMediaItem(idx, currentItem.buildUpon().setMediaMetadata(meta).build())
            }
        }
        runCatching { triggerNotificationUpdate() }
        android.util.Log.d(
            "PlaybackService",
            "Updated MediaMetadata: title='$title', artist='$artist', album='$album', durationMs=$durationMs"
        )
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        val awaitingResolvedItem = session.player.currentTimeline.isEmpty &&
            (SongPlayer.isResolvingTrack || SongPlayer.isPlaying() || SpotifyWebPlayer.isPlaying)
        if (awaitingResolvedItem) {
            promoteToForegroundWithMediaStyle(session)
            return
        }
        super.onUpdateNotification(session, startInForegroundRequired)
    }

    private fun promoteToForegroundWithMediaStyle(session: MediaSession) {
        com.music.spotui.engine.MediaNotificationAdapter.ensureChannel(this)
        val title = currentSongState.title.value.ifBlank { getString(R.string.app_name) }
        val artist = currentSongState.singer.value
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_spotui_notification)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(session.sessionActivity)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setStyle(MediaStyleNotificationHelper.MediaStyle(session))
            .build()
        runCatching {
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            }
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
        }.onFailure {
            AppDiagnostics.warning("PlaybackService", "Foreground promotion skipped/deferred", it)
        }
    }

    /** Point the media session at whichever engine is currently producing audio. */
    private fun syncSessionPlayer() {
        val wantWeb = SongPlayer.webPlaybackActive()
        if (wantWeb == showingWeb) return
        showingWeb = wantWeb
        val session = mediaSession ?: return
        session.player = if (wantWeb) {
            webPlayer ?: return
        } else {
            wrap(SongPlayer.exoPlayer ?: return)
        }
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

    private var activeForwardingPlayer: ForwardingPlayer? = null

    private fun wrap(base: Player): ForwardingPlayer {
        return object : ForwardingPlayer(base) {
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

            // Samsung S24 One UI Now Bar fix: never report a transient pause/idle during async stream resolution.
            override fun getPlaybackState(): Int =
                if (SongPlayer.isResolvingTrack) STATE_BUFFERING else super.getPlaybackState()

            override fun getPlayWhenReady(): Boolean =
                if (SongPlayer.isResolvingTrack) SongPlayer.shouldPlayWhenResolved() else super.getPlayWhenReady()

            override fun isPlaying(): Boolean =
                super.isPlaying() || (SongPlayer.isResolvingTrack && SongPlayer.shouldPlayWhenResolved())

            override fun getMediaMetadata(): MediaMetadata =
                latestServiceMetadata ?: super.getMediaMetadata()

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
                mediaSession?.setCustomLayout(
                    buildCustomLayout(
                        isLiked = isCurrentSongLiked(currentSongState.songId.value),
                        isShuffled = shuffleModeEnabled,
                    )
                )
            }

            override fun hasNextMediaItem() = currentSongState.queue.value.isNotEmpty()
            override fun hasPreviousMediaItem() = currentSongState.queue.value.isNotEmpty()
            override fun seekToNext() = advance(forward = true)
            override fun seekToNextMediaItem() = advance(forward = true)
            override fun seekToPrevious() = advance(forward = false)
            override fun seekToPreviousMediaItem() = advance(forward = false)
        }.also { activeForwardingPlayer = it }
    }

    /** Advance the in-app queue one step in the given direction and start it. */
    private fun advance(forward: Boolean) {
        if (forward) SongPlayer.next(applicationContext) else SongPlayer.previous(applicationContext)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaSession

    // ── Android Auto browse tree ──────────────────────────────────────────

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = com.music.spotui.engine.MediaNotificationAdapter.CHANNEL_ID
        const val ACTION_ENSURE_FOREGROUND = "com.music.spotui.action.ENSURE_FOREGROUND"
        const val ACTION_REFRESH_NOTIFICATION = "com.music.spotui.action.REFRESH_NOTIFICATION"
        private const val EXTRA_STARTED_IN_FOREGROUND = "com.music.spotui.extra.STARTED_IN_FOREGROUND"

        private const val ROOT = "root"
        private const val NODE_LIKED = "liked"
        private const val NODE_DOWNLOADS = "downloads"
        private const val NODE_PLAYLISTS = "playlists"
        private const val NODE_ALBUMS = "albums"
        private const val ACTION_FAVORITE = "ACTION_FAVORITE"
        private const val ACTION_SHUFFLE = "ACTION_SHUFFLE"
        private const val ACTION_REPEAT = "ACTION_REPEAT"
        private const val ACTION_CLOSE = "ACTION_CLOSE"
        private const val IDLE_STOP_TIMEOUT_MS = 5 * 60_000L

        fun ensureStarted(context: Context) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, PlaybackService::class.java).apply {
                action = ACTION_ENSURE_FOREGROUND
                putExtra(EXTRA_STARTED_IN_FOREGROUND, true)
            }
            runCatching {
                ContextCompat.startForegroundService(appContext, intent)
            }.onFailure {
                runCatching { appContext.startService(intent) }
            }
        }

        fun refreshNotification(context: Context) {
            val appContext = context.applicationContext
            if (!PlaybackSessionBus.sessionAlive.value && !SongPlayer.isPlaying() && !SongPlayer.isResolvingTrack) {
                return
            }
            val useForegroundStart = SongPlayer.isPlaying() || SongPlayer.isResolvingTrack
            val intent = Intent(appContext, PlaybackService::class.java).apply {
                action = ACTION_REFRESH_NOTIFICATION
                putExtra(EXTRA_STARTED_IN_FOREGROUND, useForegroundStart)
            }
            runCatching {
                if (useForegroundStart) {
                    ContextCompat.startForegroundService(appContext, intent)
                } else {
                    appContext.startService(intent)
                }
            }.onFailure {
                runCatching { appContext.startService(intent) }
            }
        }
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

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(baseResult.availablePlayerCommands)
                .setCustomLayout(buildCustomLayout())
                .build()
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
        val builder = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.singer)
            .setAlbumTitle(song.album)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        if (song.durationMs > 0) {
            builder.setDurationMs(song.durationMs.toLong())
        }
        com.music.spotui.util.ArtworkHelper.attachArtwork(
            builder, this, song.coverUri, song.id.toString(), song.url
        )
        return MediaItem.Builder()
            .setMediaId("song/${song.id}")
            .setMediaMetadata(builder.build())
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val forceForegroundExtra = intent?.getBooleanExtra(EXTRA_STARTED_IN_FOREGROUND, false) == true
        val requiresForegroundSafety = action == ACTION_ENSURE_FOREGROUND ||
            action == Intent.ACTION_MEDIA_BUTTON ||
            forceForegroundExtra

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
        } else if (action == ACTION_ENSURE_FOREGROUND || action == ACTION_REFRESH_NOTIFICATION) {
            scheduleIdleStopCheck()
        }

        // Always satisfy startForegroundService()'s 5s contract even while stream URLs
        // are still resolving (Timeline.isEmpty() suppresses Media3's internal notification).
        mediaSession?.let { session ->
            if (requiresForegroundSafety && session.player.currentTimeline.isEmpty) {
                promoteToForegroundWithMediaStyle(session)
            } else {
                runCatching { triggerNotificationUpdate() }
            }
        }

        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep playing seamlessly in background if playback is active or resolving a track.
        // Stop cleanly if playback is paused/idle so the notification isn't left orphaned.
        val p = mediaSession?.player
        val keepAlive = p?.playWhenReady == true ||
            SongPlayer.isResolvingTrack ||
            SpotifyWebPlayer.isPlaying
        if (!keepAlive) {
            widgetDebouncer?.reset()
            widgetSink?.serviceStopped()
            PlaybackSessionBus.onServiceStopping()
            runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
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
