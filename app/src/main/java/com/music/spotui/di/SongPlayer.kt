package com.music.spotui.di

import android.content.Context
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.utils.YouTubeUrlParser
import com.metrolist.music.constants.AudioQuality
import com.metrolist.music.utils.YTPlayerUtils
import com.music.spotui.data.api.LyricsApi
import com.music.spotui.util.AppDiagnostics
import org.eclipse.Mrbean.client.MrbeanNetworkSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

/**
 * Plays audio resolved from YouTube. The `song` argument is a "title artist"
 * search query (set by the Spotify-backed data layer): it's matched to a
 * YouTube video, whose stream URL is resolved via the ported [YTPlayerUtils]
 * flow (cipher / PoToken / sabr) and handed to ExoPlayer.
 */
object SongPlayer {
    private const val TAG = "SongPlayer"
    private const val SPOTIFY_TRACK_PREFIX = "spotify:track:"
    private var player: ExoPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Cache of resolved stream URLs keyed by the "title artist" query, so replays
    // and prefetched neighbours start instantly instead of re-hitting the network.
    private val streamCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    // Which engine each cached stream came from ("YouTube", "Lossless • …") so a
    // cache hit can restore the correct source badge.
    private val sourceCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    // Cache of resolved YouTube video candidates keyed by the play query
    private val videoCandidatesCache = java.util.concurrent.ConcurrentHashMap<String, List<String>>()
    // Coalesces concurrent network resolutions for the same song query (e.g. ExoPlayer loader + preResolve).
    private val inFlightResolves = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.CompletableDeferred<String?>>()

    // ── Lossless (SpotiFLAC) ──
    // When enabled, playback first tries to resolve a lossless FLAC stream (Tidal/
    // Amazon via SpotiFLAC's free community proxies) for the current track and only
    // falls back to YouTube if no FLAC is available or the proxies are throttled.
    // Trades a little first-tap latency for true lossless audio.
    @Volatile var losslessStreaming = true
    @Volatile var losslessHiRes = true

    // Source kill-switches. The Spotify web player is currently broken (off).
    // YouTube is the last-resort fallback, kept on so tracks SpotiFLAC misses or
    // can't serve during a proxy cooldown still play — with the wrong-song guards
    // (videoId match check + artist/title scoring + candidate fallback).
    @Volatile var webPlayerEnabled = false
    @Volatile var youtubeEnabled = true

    // Which engine is feeding the CURRENT track, for the on-screen source badge.
    // "Lossless" (SpotiFLAC: Tidal/Qobuz/Amazon) is NOT Spotify — surfaced so the
    // user knows real Spotify vs a lossless mirror vs the YouTube fallback.
    @Volatile var currentSource: String = "YouTube"
        private set
    // Human-readable quality of the CURRENT stream (e.g. "FLAC 16-bit",
    // "OPUS 141 kbps"), shown next to the source badge.
    @Volatile var currentQuality: String = ""
        private set

    @Volatile var isResolvingTrack: Boolean = false
        private set

    @Volatile var lastPlaybackError: String? = null
        private set

    @Volatile var abLoopStartMs: Long = -1L
        private set

    @Volatile var abLoopEndMs: Long = -1L
        private set

    @Volatile private var lastKnownSessionId: Int = 0
    @Volatile private var lastKnownPlaybackState: Int = Player.STATE_IDLE
    @Volatile private var lastKnownPlayWhenReady: Boolean = false
    @Volatile private var lastKnownIsPlaying: Boolean = false
    @Volatile private var lastKnownPositionMs: Long = 0L
    @Volatile private var lastKnownDurationMs: Long = 0L
    @Volatile private var lastKnownBufferedPct: Int = 0

    fun shouldPlayWhenResolved(): Boolean = playWhenResolved

    data class PlayerTelemetry(
        val sessionId: Int,
        val playbackState: Int,
        val playWhenReady: Boolean,
        val isPlaying: Boolean,
        val isResolving: Boolean,
        val bufferedPercentage: Int,
        val positionMs: Long,
        val durationMs: Long,
        val activeSource: String,
        val currentQuality: String,
        val softwareDspActive: Boolean,
        val hardwareEqActive: Boolean,
        val hardwareSpatialActive: Boolean,
        val abLoopStartMs: Long,
        val abLoopEndMs: Long,
        val lastError: String?,
    )

    fun telemetrySnapshot(context: Context): PlayerTelemetry {
        val p = player
        val caps = com.music.spotui.audio.AudioEffectController.capabilityReport()
        val canReadPlayerDirectly = p != null && android.os.Looper.myLooper() == p.applicationLooper
        val stateCode: Int
        val sessionId: Int
        val pwr: Boolean
        val playingNow: Boolean
        val pos: Long
        val dur: Long
        val bufPct: Int
        if (canReadPlayerDirectly && p != null) {
            stateCode = p.playbackState.also { lastKnownPlaybackState = it }
            sessionId = p.audioSessionId.takeIf { it > 0 } ?: caps.sessionId
            lastKnownSessionId = sessionId
            pwr = p.playWhenReady.also { lastKnownPlayWhenReady = it }
            playingNow = isPlaying().also { lastKnownIsPlaying = it }
            pos = runCatching { p.currentPosition.coerceAtLeast(0L) }.getOrDefault(0L).also { lastKnownPositionMs = it }
            dur = runCatching { p.duration.coerceAtLeast(0L) }.getOrDefault(0L).also { lastKnownDurationMs = it }
            bufPct = runCatching { p.bufferedPercentage.coerceIn(0, 100) }.getOrDefault(0).also { lastKnownBufferedPct = it }
        } else {
            stateCode = if (p == null) Player.STATE_IDLE else lastKnownPlaybackState
            sessionId = if (lastKnownSessionId > 0) lastKnownSessionId else caps.sessionId
            pwr = if (p == null) playWhenResolved else lastKnownPlayWhenReady
            playingNow = if (p == null) webPlaybackActive() && SpotifyWebPlayer.isPlaying else lastKnownIsPlaying
            pos = lastKnownPositionMs
            dur = lastKnownDurationMs
            bufPct = lastKnownBufferedPct
        }
        return PlayerTelemetry(
            sessionId = sessionId,
            playbackState = stateCode,
            playWhenReady = pwr,
            isPlaying = playingNow,
            isResolving = isResolvingTrack,
            bufferedPercentage = bufPct,
            positionMs = pos,
            durationMs = dur,
            activeSource = currentSource,
            currentQuality = currentQuality,
            softwareDspActive = caps.softwareDspAttached || currentDspProcessor != null,
            hardwareEqActive = caps.equalizerAvailable,
            hardwareSpatialActive = caps.hardwareSpatialAvailable,
            abLoopStartMs = abLoopStartMs,
            abLoopEndMs = abLoopEndMs,
            lastError = lastPlaybackError ?: lastYtFailureReason,
        )
    }

    @Volatile private var lastYtFailureReason: String? = null

    private fun updateResolveStatus(isResolving: Boolean, status: String = "") {
        isResolvingTrack = isResolving
        boundState?.updateResolveState(isResolving, status)
    }
    private val qualityCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    // Maps a "title artist" play query -> the track's real Spotify id, so the
    // lossless resolver can be seeded from a play site that only has the query.
    // Populated centrally whenever the queue changes (see CurrentSongState).
    private val trackIdRegistry = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val isrcRegistry = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val alternativeKeyRegistry = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Register query→spotifyTrackId pairs so lossless can be resolved by query. */
    fun registerLossless(pairs: List<Pair<String, String>>) {
        pairs.forEach { (query, spotifyId) ->
            if (query.isNotBlank() && spotifyId.isNotBlank()) trackIdRegistry[query] = spotifyId
        }
    }

    /** Register spotifyTrackId -> ISRC mapping so Qobuz matching can succeed during streaming. */
    fun registerIsrc(spotifyTrackId: String, isrc: String) {
        if (spotifyTrackId.isNotBlank() && isrc.isNotBlank()) {
            isrcRegistry[spotifyTrackId] = isrc
        }
    }

    fun registerAlternativeKeys(pairs: List<Pair<String, String>>) {
        pairs.forEach { (query, key) ->
            if (query.isNotBlank() && key.isNotBlank()) alternativeKeyRegistry[query] = key
        }
    }

    // Whether each play query is the explicit version on Spotify, so the YouTube
    // fallback can pick the matching (explicit vs clean) edit.
    private val explicitRegistry = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    /** Register query→explicit pairs (populated whenever the queue changes). */
    fun registerExplicit(pairs: List<Pair<String, Boolean>>) {
        pairs.forEach { (query, explicit) ->
            if (query.isNotBlank()) explicitRegistry[query] = explicit
        }
    }

    // Expected track length (ms) per query, from Spotify — lets the YouTube match
    // reject a same-title song by a different artist (different duration).
    private val durationRegistry = java.util.concurrent.ConcurrentHashMap<String, Int>()

    /** Register query→durationMs pairs (populated whenever the queue changes). */
    fun registerDuration(pairs: List<Pair<String, Int>>) {
        pairs.forEach { (query, ms) ->
            if (query.isNotBlank() && ms > 0) durationRegistry[query] = ms
        }
    }

    data class TrackMatchMetadata(
        val title: String,
        val artist: String,
        val album: String,
    )

    private val metadataRegistry =
        java.util.concurrent.ConcurrentHashMap<String, TrackMatchMetadata>()

    /** Register query→Spotify metadata pairs for strict YouTube result scoring. */
    fun registerMetadata(pairs: List<Pair<String, TrackMatchMetadata>>) {
        pairs.forEach { (query, meta) ->
            if (query.isNotBlank() && meta.title.isNotBlank()) metadataRegistry[query] = meta
        }
    }
    // Tracks which query is the latest play request so a slow resolve for an old
    // tap doesn't clobber a newer one (fast switching).
    @Volatile private var currentRequest: String = ""
    @Volatile private var loadedQuery: String? = null
    @Volatile private var playWhenResolved = true

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun runOnMain(block: () -> Unit) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    internal class DashRedirectException(val manifestUrl: String) :
        java.io.IOException("Redirecting stream to DASH manifest: $manifestUrl")

    internal fun onPlayerStateSettled(playbackState: Int) {
        if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
            isResolvingTrack = false
            updateResolveStatus(false)
        }
    }

    // Latest track metadata (title / artist / cover URL) so the MediaItem we build
    // carries it into the system media notification. Set via [setNowPlayingMeta]
    // (driven by CurrentSongState) just before / as playback starts.
    @Volatile private var metaTitle: String = ""
    @Volatile private var metaArtist: String = ""
    @Volatile private var metaCover: String = ""
    @Volatile private var metaAlbum: String = ""
    @Volatile private var metaDurationMs: Long = 0L

    data class NowPlayingMetaSnapshot(
        val title: String,
        val artist: String,
        val coverUri: String,
        val album: String,
        val durationMs: Long,
    )

    fun nowPlayingMetaSnapshot(): NowPlayingMetaSnapshot =
        NowPlayingMetaSnapshot(
            title = metaTitle,
            artist = metaArtist,
            coverUri = metaCover,
            album = metaAlbum,
            durationMs = metaDurationMs,
        )

    @androidx.annotation.VisibleForTesting
    internal fun setResolvingStateForTest(resolving: Boolean, shouldPlay: Boolean = true) {
        isResolvingTrack = resolving
        playWhenResolved = shouldPlay
    }

    /** True when the bound queue has a track to advance to (next item, repeat-one, or repeat-all). */
    fun hasUpcomingQueueTrack(): Boolean {
        val state = boundState ?: return false
        val q = state.queue.value
        if (q.isEmpty()) return false
        return when (state.repeat.value) {
            RepeatMode.ONE, RepeatMode.ALL -> true
            RepeatMode.OFF -> {
                val curId = state.songId.value
                val curIdx = q.indexOfFirst { it.id == curId }
                    .let { if (it >= 0) it else state.songIndex.value }
                curIdx in 0 until q.lastIndex
            }
        }
    }

    /** Updates active query/metadata state after ExoPlayer gaplessly auto-advances to a queued item. */
    fun onAutoAdvanced(
        songUrl: String,
        mediaId: String,
        title: String,
        artist: String,
        coverUri: String,
        album: String,
        durationMs: Long,
    ) {
        currentRequest = songUrl
        loadedQuery = songUrl
        currentMediaId = mediaId
        playWhenResolved = true
        isResolvingTrack = false
        sourceCache[songUrl]?.let { currentSource = it }
        qualityCache[songUrl]?.let { currentQuality = it }
        setNowPlayingMeta(
            title = title,
            artist = artist,
            coverUri = coverUri,
            album = album,
            durationMs = durationMs,
        )
    }

    @androidx.annotation.VisibleForTesting
    internal fun setCachedStreamForTest(song: String, url: String, source: String, quality: String) {
        streamCache[song] = url
        sourceCache[song] = source
        qualityCache[song] = quality
    }

    fun setNowPlayingMeta(
        title: String,
        artist: String,
        coverUri: String,
        album: String = "",
        durationMs: Long = 0L,
    ) {
        metaTitle = title
        metaArtist = artist
        metaCover = coverUri
        metaAlbum = album
        metaDurationMs = durationMs.coerceAtLeast(0L)
        refreshArtworkInBackground(currentMediaId, coverUri)
    }

    private fun refreshArtworkInBackground(songIdStr: String?, coverUrl: String) {
        if (coverUrl.isBlank() || coverUrl.startsWith("file:") || coverUrl.startsWith("content:")) return
        val ctx = appCtx ?: com.music.spotui.MyApplication.instanceOrNull ?: return
        val cleanId = songIdStr?.removePrefix("song/")?.removePrefix("playlist/")?.removePrefix("album/")?.trim()
        scope.launch(Dispatchers.IO) {
            val file = runCatching {
                com.bumptech.glide.Glide.with(ctx)
                    .downloadOnly()
                    .load(coverUrl)
                    .submit()
                    .get()
            }.getOrNull()
            if (file != null && file.exists()) {
                val dir = java.io.File(ctx.filesDir, "downloads")
                if (!cleanId.isNullOrBlank() && com.music.spotui.data.preferences.isDownloaded(ctx, cleanId)) {
                    val dest = java.io.File(dir, "${cleanId}_cover.jpg")
                    if (!dest.exists()) runCatching { file.copyTo(dest, overwrite = true) }
                }
            }
        }
    }

    /**
     * Build a stable playback identity for Spotify tracks. The full value is used
     * as cache/registry key, while only the text after "|" is sent to YouTube
     * search. This prevents same-title/same-artist tracks from reusing each
     * other's resolved stream.
     */
    fun buildSpotifyPlayQuery(spotifyTrackId: String, title: String, artist: String): String {
        val cleanArtist = artist.replace(",", " ").replace("  ", " ").trim()
        val searchText = listOf(cleanSpotifySearchTitle(title), cleanArtist)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        return if (spotifyTrackId.isBlank()) searchText else "$SPOTIFY_TRACK_PREFIX$spotifyTrackId|$searchText"
    }

    private val featSearchPattern = Regex("""\s*[\(\[]\s*(feat|ft)\..*?[\)\]]""", RegexOption.IGNORE_CASE)

    private fun cleanSpotifySearchTitle(title: String): String =
        title.replace(featSearchPattern, "").trim()

    private fun searchTextForPlayback(song: String): String =
        if (song.startsWith(SPOTIFY_TRACK_PREFIX) && song.contains('|')) {
            song.substringAfter('|').ifBlank { song }
        } else {
            song
        }

    private fun spotifyTrackIdForPlayback(song: String): String? =
        if (song.startsWith(SPOTIFY_TRACK_PREFIX)) {
            song.removePrefix(SPOTIFY_TRACK_PREFIX).substringBefore('|').takeIf { it.isNotBlank() }
        } else {
            null
        }

    fun videoIdFromYouTubeLink(text: String): String? =
        YouTubeUrlParser.extractVideoId(text)
            ?: text.trim().takeIf { it.matches(Regex("""[A-Za-z0-9_-]{11}""")) }

    fun invalidateResolvedStream(song: String) {
        streamCache.remove(song)
        sourceCache.remove(song)
        qualityCache.remove(song)
        videoCandidatesCache.remove("$song|FILTER_SONG")
        videoCandidatesCache.remove("$song|FILTER_VIDEO")
        appCtx?.let { ctx ->
            com.music.spotui.data.preferences.clearCachedVideoId(ctx, song)
            com.music.spotui.data.preferences.clearCachedStream(ctx, song)
        }
    }

    @Volatile private var currentMediaId: String? = null

    @Suppress("DEPRECATION")
    private fun acquireResolveWakeLocks(context: Context): Pair<PowerManager.WakeLock?, WifiManager.WifiLock?> {
        val wakeLock = runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Spotui::TrackResolve")?.apply {
                setReferenceCounted(false)
                acquire(30_000L)
            }
        }.getOrNull()
        val wifiLock = runCatching {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wm?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Spotui::TrackResolveWifi")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }.getOrNull()
        return wakeLock to wifiLock
    }

    private fun releaseResolveWakeLocks(wakeLock: PowerManager.WakeLock?, wifiLock: WifiManager.WifiLock?) {
        runCatching { if (wakeLock?.isHeld == true) wakeLock.release() }
        runCatching { if (wifiLock?.isHeld == true) wifiLock.release() }
    }

    /**
     * Invalidates cached streams for the active track and immediately restarts it at its current
     * timestamp so a quality change in Settings takes effect cleanly on the playing song.
     */
    fun restartCurrentTrackForQualityChange(context: Context): Boolean {
        val appContext = context.applicationContext
        appCtx = appContext
        val state = boundState
        val activeSongModel = state?.queue?.value?.firstOrNull { it.id == state.songId.value }
        val activeQuery = loadedQuery
            ?: currentRequest.takeIf { it.isNotBlank() }
            ?: activeSongModel?.url?.takeIf { it.isNotBlank() }
            ?: return false
        val currentPos = getCurrentPosition().coerceAtLeast(0L)
        invalidateResolvedStream(activeQuery)
        setRestorePoint(activeQuery, currentPos)
        playSong(activeQuery, appContext, activeSongModel?.let { "song/${it.id}" } ?: currentMediaId)
        return true
    }

    private fun fastLocalOrCachedStream(song: String, appContext: Context): String? {
        if (song.startsWith("content://", ignoreCase = true) || song.startsWith("file://", ignoreCase = true)) {
            currentSource = "Local Audio"
            currentQuality = song.substringAfterLast('.', "").uppercase().takeIf { it.length in 2..5 } ?: "LOCAL"
            return song
        }
        streamCache[song]?.let { cached ->
            currentSource = sourceCache[song] ?: "YouTube"
            currentQuality = qualityCache[song] ?: ""
            return cached
        }
        com.music.spotui.data.preferences.getCachedStream(appContext, song)?.let { (url, source, quality) ->
            streamCache[song] = url
            sourceCache[song] = source
            qualityCache[song] = quality
            currentSource = source
            currentQuality = quality
            return url
        }
        alternativeStreamForPlayback(song, appContext)?.takeIf { it.isLocal }?.let { alt ->
            currentSource = "Alternative file"
            currentQuality = alt.label.substringAfterLast('.', "").uppercase().takeIf { it.length in 2..5 }.orEmpty()
            return alt.value
        }
        com.music.spotui.data.preferences.downloadedPathForQuery(appContext, song)?.let { path ->
            currentSource = "Downloaded"
            currentQuality = path.substringAfterLast('.', "").uppercase()
            return android.net.Uri.fromFile(java.io.File(path)).toString()
        }
        return null
    }

    private fun nextTrackForQueueLookahead(): Pair<com.music.spotui.data.entity.SongsModel, String>? {
        val state = boundState ?: return null
        if (state.repeat.value == RepeatMode.ONE) return null
        val q = state.queue.value
        if (q.size <= 1) return null
        val curId = state.songId.value
        val curIdx = q.indexOfFirst { it.id == curId }
            .let { if (it >= 0) it else state.songIndex.value }
            .coerceIn(0, q.lastIndex)
        val nextIdx = when {
            curIdx < q.lastIndex -> curIdx + 1
            state.repeat.value == RepeatMode.ALL -> 0
            else -> return null
        }
        val nextSong = q[nextIdx]
        return nextSong to "song/${nextSong.id}"
    }

    private fun resolveUriForQuery(song: String): String =
        android.net.Uri.Builder()
            .scheme("spotui")
            .authority("resolve")
            .appendQueryParameter("q", song)
            .build()
            .toString()

    fun playSong(song: String, context: Context, mediaId: String? = null) {
        val appContext = context.applicationContext
        appCtx = appContext
        currentRequest = song
        val inferredMediaId = mediaId
            ?: boundState?.queue?.value?.firstOrNull { it.url == song }?.let { "song/${it.id}" }
            ?: boundState?.songId?.value?.takeIf { it > 0 }?.let { "song/$it" }
        currentMediaId = inferredMediaId
        playWhenResolved = true
        lastPlaybackError = null
        isResolvingTrack = true
        lastPreQueueTriggerQuery = null
        abLoopStartMs = -1L
        abLoopEndMs = -1L
        // Remember the play-query so history can replay this track on tap.
        boundState?.setSongUrl(song)

        runOnMain {
            if (currentRequest != song) return@runOnMain
            // A manual play (tap / next / prev) supersedes any in-flight crossfade.
            cancelCrossfade()
            ensurePlayer(appContext)
            // Lazy-initialize SpotifyWebPlayer only if a track is explicitly eligible to route there.
            val downloadedPath = com.music.spotui.data.preferences.downloadedPathForQuery(appContext, song)
            val webEnabled = webPlayerEnabled && com.music.spotui.data.preferences.isWebPlaybackEnabled(appContext)
            if (webEnabled && (song.startsWith("episode:") || downloadedPath == null)) {
                runCatching { SpotifyWebPlayer.attach(appContext) }
            }

            if (song.startsWith("episode:") && webEnabled && SpotifyWebPlayer.canPlay) {
                isResolvingTrack = false
                runCatching { player?.pause() }
                currentSource = "Spotify"
                currentQuality = ""
                updateResolveStatus(false)
                com.music.spotui.ui.notification.PlaybackService.ensureStarted(appContext)
                SpotifyWebPlayer.playEpisode(song.removePrefix("episode:"))
                com.music.spotui.ui.notification.PlaybackService.notifyPlaybackStateChanged(appContext)
                return@runOnMain
            }

            if (downloadedPath == null && webEnabled && SpotifyWebPlayer.canPlay) {
                val spotifyId = trackIdRegistry[song] ?: spotifyTrackIdForPlayback(song)
                if (spotifyId != null) {
                    isResolvingTrack = false
                    runCatching { player?.pause() }
                    currentSource = "Spotify"
                    currentQuality = ""
                    updateResolveStatus(true, "Loading Spotify track...")
                    com.music.spotui.ui.notification.PlaybackService.ensureStarted(appContext)
                    SpotifyWebPlayer.play(spotifyId)
                    com.music.spotui.ui.notification.PlaybackService.notifyPlaybackStateChanged(appContext)
                    scope.launch {
                        delay(1500)
                        if (currentRequest == song) {
                            updateResolveStatus(false)
                            com.music.spotui.ui.notification.PlaybackService.notifyPlaybackStateChanged(appContext)
                        }
                    }
                    return@runOnMain
                }
                Log.w(TAG, "web playback on but no Spotify id for query: $song — using fallback engine")
            }

            // Immediately queue a real MediaItem with full MediaMetadata onto ExoPlayer on frame 0 so
            // currentTimeline is non-empty and state enters STATE_BUFFERING (never STATE_IDLE while
            // ResolvingDataSource resolves uncached streams on ExoPlayer's background loader thread).
            val fastUrl = fastLocalOrCachedStream(song, appContext)
            val initialItem = if (fastUrl != null) {
                val cleanUrl = com.music.spotui.util.StreamSanitizer.sanitizeStreamUrl(fastUrl)
                loadedQuery = song
                isResolvingTrack = false
                updateResolveStatus(false)
                buildMediaItem(
                    cleanUrl,
                    streamMimeType(
                        cleanUrl,
                        source = sourceCache[song] ?: currentSource,
                        quality = qualityCache[song] ?: currentQuality,
                    ),
                )
            } else {
                updateResolveStatus(true, "Resolving audio stream...")
                buildMediaItem(resolveUriForQuery(song), null)
            }

            val p = player ?: return@runOnMain
            val fadeIn = com.music.spotui.data.preferences.isFadeInOnPlay(appContext)
            val startPosMs = if (song == restoreQuery && restorePositionMs > 0L) {
                restorePositionMs
            } else {
                androidx.media3.common.C.TIME_UNSET
            }
            restoreQuery = null

            val crossfadeMs = com.music.spotui.data.preferences.getCrossfadeMs(appContext)
            val lookahead = if (crossfadeMs <= 0) nextTrackForQueueLookahead() else null
            if (lookahead != null && (youtubeEnabled || losslessStreaming || streamCache.containsKey(lookahead.first.url))) {
                val (nextSong, nextMediaId) = lookahead
                val cachedNext = streamCache[nextSong.url]?.let {
                    com.music.spotui.util.StreamSanitizer.sanitizeStreamUrl(it)
                }
                val nextUri = cachedNext ?: resolveUriForQuery(nextSong.url)
                val nextMime = if (cachedNext != null) {
                    streamMimeType(
                        cachedNext,
                        source = sourceCache[nextSong.url] ?: "",
                        quality = qualityCache[nextSong.url] ?: "",
                    )
                } else {
                    null
                }
                val nextItem = buildMediaItemForTrack(
                    streamUrl = nextUri,
                    mimeType = nextMime,
                    title = nextSong.title,
                    artist = nextSong.singer,
                    coverUri = nextSong.coverUri,
                    album = nextSong.album,
                    durationMs = nextSong.durationMs.toLong(),
                    mediaId = nextMediaId,
                )
                p.setMediaItems(listOf(initialItem, nextItem), 0, startPosMs)
            } else {
                p.setMediaItem(initialItem, startPosMs)
            }
            p.repeatMode = if (boundState?.repeat?.value == RepeatMode.ONE) {
                Player.REPEAT_MODE_ONE
            } else {
                Player.REPEAT_MODE_OFF
            }
            p.prepare()
            com.music.spotui.audio.AudioEffectController.bindSoftwareProcessors(
                appContext, currentDspProcessor, currentSpatialProcessor
            )
            com.music.spotui.audio.AudioEffectController.attach(appContext, p.audioSessionId)
            p.volume = if (fadeIn && playWhenResolved) 0.15f else 1f
            p.playWhenReady = playWhenResolved
            com.music.spotui.ui.notification.PlaybackService.ensureStarted(appContext)
            com.music.spotui.ui.notification.PlaybackService.notifyPlaybackStateChanged(appContext)

            if (fadeIn && playWhenResolved) {
                scope.launch(Dispatchers.Main) {
                    val steps = 8
                    for (i in 1..steps) {
                        delay(20L)
                        if (player === p && !isCrossfading) {
                            p.volume = 0.15f + (0.85f * (i.toFloat() / steps))
                        }
                    }
                }
            }
            startPositionWatch()
            preResolveAndQueueNextTrack(appContext)
        }
    }

    // Build a MediaItem carrying the current track's metadata so the system media
    // notification (MediaSession) and One UI Now Bar show title / artist / album / duration / artwork.
    private fun buildMediaItem(streamUrl: String, mimeType: String? = null): MediaItem {
        val ctx = appCtx ?: com.music.spotui.MyApplication.instanceOrNull
        val metadata = if (ctx != null) {
            com.music.spotui.util.ArtworkHelper.buildTrackMetadata(
                context = ctx,
                title = metaTitle,
                artist = metaArtist,
                album = metaAlbum,
                coverUri = metaCover,
                durationMs = metaDurationMs,
                songId = currentMediaId,
                streamUrl = streamUrl,
                onAsyncArtReady = {
                    appCtx?.let { mainHandler.post { com.music.spotui.ui.notification.PlaybackService.refreshNotification(it) } }
                },
            )
        } else {
            androidx.media3.common.MediaMetadata.Builder()
                .setTitle(metaTitle)
                .setArtist(metaArtist)
                .setMediaType(androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC)
                .setIsPlayable(true)
                .apply {
                    if (metaAlbum.isNotBlank()) setAlbumTitle(metaAlbum)
                    if (metaDurationMs > 0L) setDurationMs(metaDurationMs)
                }
                .build()
        }
        return MediaItem.Builder()
            .apply { currentMediaId?.let { setMediaId(it) } }
            .setUri(streamUrl)
            // Hint the container so ExoPlayer picks the right source/extractor even
            // when the URL has no extension: TIDAL lossless is a DASH .mpd manifest,
            // and single-file lossless is FLAC.
            .apply { if (mimeType != null) setMimeType(mimeType) }
            .setMediaMetadata(metadata)
            .build()
    }

    /** MIME hint for a resolved stream: DASH manifest, single-file FLAC, MP3, or none. */
    internal fun streamMimeType(
        streamUrl: String,
        source: String = currentSource,
        quality: String = currentQuality,
    ): String? {
        val bare = streamUrl.substringBefore('?').lowercase()
        val isGoogleVideo = bare.contains("googlevideo.com") || bare.contains("youtube.com")
        return when {
            streamUrl.startsWith("data:application/dash+xml") ||
                bare.endsWith(".mpd") || streamUrl.contains("manifest.tidal.com") || streamUrl.contains("/manifests/") ->
                androidx.media3.common.MimeTypes.APPLICATION_MPD
            streamUrl.startsWith("deezer://") ->
                if (quality.contains("FLAC", ignoreCase = true) || streamUrl.contains("quality=9")) {
                    androidx.media3.common.MimeTypes.AUDIO_FLAC
                } else {
                    androidx.media3.common.MimeTypes.AUDIO_MPEG
                }
            bare.endsWith(".flac") -> androidx.media3.common.MimeTypes.AUDIO_FLAC
            !isGoogleVideo && source.startsWith("Lossless") && quality.contains("FLAC", ignoreCase = true) ->
                androidx.media3.common.MimeTypes.AUDIO_FLAC
            else -> null
        }
    }

    /**
     * Gapless Playback: Background Queue Pre-Resolution & Native ExoPlayer Queueing.
     * Pre-resolves the next stream so transitions happen smoothly without stalling.
     * When crossfade/gapless handoff (`transitionMs > 0`) owns the swap, we only warm
     * the stream/intro cache instead of appending to ExoPlayer's playlist so ExoPlayer's
     * auto-advance never races `triggerCrossfade`.
     */
    fun preResolveAndQueueNextTrack(context: Context) {
        val appContext = context.applicationContext
        if (webPlaybackActive()) return
        val state = boundState ?: return
        val q = state.queue.value
        if (q.isEmpty()) return
        val curId = state.songId.value
        val curIdx = q.indexOfFirst { it.id == curId }
            .let { if (it >= 0) it else state.songIndex.value }
            .coerceIn(0, q.size - 1)

        val nextIdx: Int = if (curIdx < q.size - 1) {
            curIdx + 1
        } else if (state.repeat.value == RepeatMode.ALL) {
            0
        } else {
            return
        }

        val nextSong = q[nextIdx]
        scope.launch {
            try {
                val nextUrl = resolveStreamUrl(nextSong.url, appContext) ?: return@launch
                val sanitizedUrl = com.music.spotui.util.StreamSanitizer.sanitizeStreamUrl(nextUrl)
                val crossfadeMs = com.music.spotui.data.preferences.getCrossfadeMs(appContext)

                if (crossfadeMs > 0) {
                    // Explicit multi-second crossfade uses a secondary ExoPlayer; warm intro cache.
                    cacheIntro(sanitizedUrl, appContext)
                } else {
                    withContext(Dispatchers.Main) {
                        val p = player ?: return@withContext
                        // Trim already-played items far behind the active index to keep timeline compact.
                        while (p.currentMediaItemIndex > 1) {
                            p.removeMediaItem(0)
                        }
                        val nextMediaId = "song/${nextSong.id}"
                        val nextIdxInPlayer = p.currentMediaItemIndex + 1
                        val existingNextItem = if (nextIdxInPlayer in 0 until p.mediaItemCount) {
                            p.getMediaItemAt(nextIdxInPlayer)
                        } else {
                            null
                        }
                        val existingNextId = existingNextItem?.mediaId
                        val existingNextScheme = existingNextItem?.localConfiguration?.uri?.scheme
                        val nextSource = sourceCache[nextSong.url] ?: ""
                        val nextQuality = qualityCache[nextSong.url] ?: ""
                        val nextMime = streamMimeType(sanitizedUrl, source = nextSource, quality = nextQuality)
                        if (p.currentMediaItemIndex >= p.mediaItemCount - 1) {
                            val nextItem = buildMediaItemForTrack(
                                streamUrl = sanitizedUrl,
                                mimeType = nextMime,
                                title = nextSong.title,
                                artist = nextSong.singer,
                                coverUri = nextSong.coverUri,
                                album = nextSong.album,
                                durationMs = nextSong.durationMs.toLong(),
                                mediaId = nextMediaId
                            )
                            p.addMediaItem(nextItem)
                            Log.d(TAG, "Queued next track directly in ExoPlayer (${nextSong.title})")
                        } else if (
                            existingNextId != nextMediaId ||
                            (existingNextScheme == "spotui" && nextMime == androidx.media3.common.MimeTypes.APPLICATION_MPD)
                        ) {
                            val nextItem = buildMediaItemForTrack(
                                streamUrl = sanitizedUrl,
                                mimeType = nextMime,
                                title = nextSong.title,
                                artist = nextSong.singer,
                                coverUri = nextSong.coverUri,
                                album = nextSong.album,
                                durationMs = nextSong.durationMs.toLong(),
                                mediaId = nextMediaId
                            )
                            p.replaceMediaItem(nextIdxInPlayer, nextItem)
                            Log.d(TAG, "Updated queued next track in ExoPlayer (${nextSong.title})")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Pre-resolution failed for next track: ${nextSong.title}", e)
            }
        }
    }

    private fun buildMediaItemForTrack(
        streamUrl: String,
        mimeType: String? = null,
        title: String,
        artist: String,
        coverUri: String,
        album: String = "",
        durationMs: Long = 0L,
        mediaId: String? = null
    ): MediaItem {
        val ctx = appCtx ?: com.music.spotui.MyApplication.instanceOrNull
        val metadata = if (ctx != null) {
            com.music.spotui.util.ArtworkHelper.buildTrackMetadata(
                context = ctx,
                title = title,
                artist = artist,
                album = album,
                coverUri = coverUri,
                durationMs = durationMs,
                songId = mediaId,
                streamUrl = streamUrl,
            )
        } else {
            androidx.media3.common.MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setMediaType(androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC)
                .setIsPlayable(true)
                .apply {
                    if (album.isNotBlank()) setAlbumTitle(album)
                    if (durationMs > 0L) setDurationMs(durationMs)
                }
                .build()
        }
        return MediaItem.Builder()
            .apply { mediaId?.let { setMediaId(it) } }
            .setUri(streamUrl)
            .apply { if (mimeType != null) setMimeType(mimeType) }
            .setMediaMetadata(metadata)
            .build()
    }

    /** Warm the cache for an upcoming track (e.g. the next/previous queue item). */
    fun prefetch(song: String, context: Context) {
        if (song.isBlank() || streamCache.containsKey(song)) return
        val appContext = context.applicationContext
        // No point resolving YouTube streams while Spotify web is the engine — it's
        // wasted network/CPU that competes with the streaming audio (caused stutter).
        if (webPlaybackActive()) return
        // Lossless FLAC URLs from the backends are short-lived / single-use. Caching
        // one now + preloading a partial intro makes playback stop after ~30s when the
        // continuation hits a stale URL, so resolve those fresh at play time instead.
        if (losslessStreaming && com.music.spotui.data.preferences.currentStreamingQuality(appContext).lossless) return
        scope.launch {
            val url = runCatching { resolveStreamUrl(song, appContext) }.getOrNull()
            if (url != null) cacheIntro(url, appContext)
        }
    }

    /**
     * Warm the cache for the first [count] tracks of a freshly-loaded list
     * (album/artist/search). Resolves them sequentially so we don't fire a dozen
     * PoToken/player chains at once, but get the likely-next taps ready ahead of
     * time — this is what kills the "~3s per track" first-tap latency.
     */
    fun prefetchList(songs: List<String>, context: Context, count: Int = 4) {
        // Do not resolve streams for whole result/album lists. That made search
        // and album screens kick off several network player/FLAC lookups before
        // the user chose anything, which feels like the app is downloading the
        // catalog instead of streaming the tapped song.
    }

    // ── Intro preloading (instant playback) ──
    // Resolving the stream URL hides most latency, but ExoPlayer still has to open the
    // connection and buffer the first segment on tap. We pre-cache the first ~1 MB (≈20–40s
    // of audio) of upcoming tracks into a media cache the player reads through, so a tap on a
    // preloaded track starts almost instantly. Skipped for local files (already instant) and
    // when the user turns preloading off in Settings.
    private const val PRELOAD_BYTES = 1L * 1024 * 1024

    @Volatile private var mediaCache: androidx.media3.datasource.cache.SimpleCache? = null
    @Volatile private var mediaCacheInitFailed = false

    private fun mediaCache(context: Context): androidx.media3.datasource.cache.SimpleCache? {
        if (mediaCacheInitFailed) return null
        return mediaCache ?: synchronized(this) {
            if (mediaCacheInitFailed) return null
            mediaCache ?: runCatching {
                val dir = java.io.File(context.cacheDir, "media")
                if (!dir.exists()) dir.mkdirs()
                androidx.media3.datasource.cache.SimpleCache(
                    dir,
                    androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor(256L * 1024 * 1024),
                    androidx.media3.database.StandaloneDatabaseProvider(context),
                )
            }.getOrElse { error ->
                Log.w(TAG, "SimpleCache init failed, resetting cache dir", error)
                runCatching {
                    val dir = java.io.File(context.cacheDir, "media")
                    dir.deleteRecursively()
                    dir.mkdirs()
                    androidx.media3.datasource.cache.SimpleCache(
                        dir,
                        androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor(256L * 1024 * 1024),
                        androidx.media3.database.StandaloneDatabaseProvider(context),
                    )
                }.getOrElse {
                    Log.e(TAG, "SimpleCache fallback also failed, disabling media cache", it)
                    mediaCacheInitFailed = true
                    null
                }
            }?.also { mediaCache = it }
        }
    }

    private fun cacheDataSourceFactory(context: Context): androidx.media3.datasource.DataSource.Factory {
        // MrBean owns the concrete Media3 HTTP transport when enabled; DeezerAwareDataSourceFactory
        // intercepts `deezer://` Blowfish-encrypted CDN streams and bypasses SimpleCache cleanly.
        val upstream = androidx.media3.datasource.DefaultDataSource.Factory(
            context,
            org.eclipse.Mrbean.client.MrbeanMediaDataSource.upstreamFactory(context),
        )
        val cache = mediaCache(context)
        val cachedOrUpstream = if (cache != null) {
            androidx.media3.datasource.cache.CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(upstream)
                .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        } else {
            upstream
        }
        return com.music.spotui.deezer.DeezerAwareDataSourceFactory(cachedOrUpstream)
    }

    /**
     * Wraps [cacheDataSourceFactory] in Media3's [androidx.media3.datasource.ResolvingDataSource.Factory]
     * so synthetic `spotui://resolve?q=...` MediaItems resolve lazily on ExoPlayer's background loader
     * thread while keeping the active timeline non-empty and state at STATE_BUFFERING on frame 0.
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun resolvingDataSourceFactory(context: Context): androidx.media3.datasource.DataSource.Factory {
        val appContext = context.applicationContext
        val upstreamFactory = cacheDataSourceFactory(appContext)
        return androidx.media3.datasource.ResolvingDataSource.Factory(
            upstreamFactory,
            androidx.media3.datasource.ResolvingDataSource.Resolver { dataSpec ->
                val uri = dataSpec.uri
                if (uri.scheme != "spotui" || uri.host != "resolve") {
                    return@Resolver dataSpec
                }
                val query = uri.getQueryParameter("q") ?: currentRequest
                if (query.isBlank()) {
                    throw java.io.IOException("Missing stream resolve query")
                }
                val (wakeLock, wifiLock) = acquireResolveWakeLocks(appContext)
                try {
                    val isCurrent = currentRequest == query
                    val rawUrl = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                        resolveStreamUrl(query, appContext, forPlayback = isCurrent)
                    } ?: throw java.io.IOException("No stream found for $query").also {
                        if (currentRequest == query) {
                            isResolvingTrack = false
                            lastPlaybackError = "No stream found"
                            com.music.spotui.util.DevConsoleManager.logError("SongPlayer", "No stream found for request: $query")
                            mainHandler.post {
                                android.widget.Toast.makeText(
                                    appContext,
                                    "No stream found",
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                                if (boundState?.resolveError?.value == null) {
                                    boundState?.updateResolveError("No stream found")
                                }
                                updateResolveStatus(false)
                                com.music.spotui.ui.notification.PlaybackService.refreshNotification(appContext)
                            }
                        }
                    }

                    val streamUrl = com.music.spotui.util.StreamSanitizer.sanitizeStreamUrl(rawUrl)
                    val resolvedSrc = sourceCache[query] ?: if (currentRequest == query) currentSource else ""
                    val resolvedQual = qualityCache[query] ?: if (currentRequest == query) currentQuality else ""
                    val mimeType = streamMimeType(streamUrl, source = resolvedSrc, quality = resolvedQual)
                    if (currentRequest == query) {
                        loadedQuery = query
                        isResolvingTrack = false
                        com.music.spotui.util.DevConsoleManager.logInfo("SongPlayer", "Resolved & sanitized stream from $currentSource [$currentQuality]")
                        com.music.spotui.util.DevConsoleManager.updateDiagnostics {
                            copy(
                                activeSource = currentSource,
                                currentQuality = currentQuality,
                                isBuffering = true,
                            )
                        }
                        mainHandler.post {
                            updateResolveStatus(false)
                            com.music.spotui.ui.notification.PlaybackService.refreshNotification(appContext)
                        }
                    }

                    if (mimeType == androidx.media3.common.MimeTypes.APPLICATION_MPD) {
                        streamCache[query] = streamUrl
                        mainHandler.post {
                            val p = player
                            if (p != null && currentRequest == query) {
                                val idx = p.currentMediaItemIndex
                                val item = if (idx in 0 until p.mediaItemCount) p.getMediaItemAt(idx) else null
                                if (item?.localConfiguration?.uri?.scheme == "spotui") {
                                    val dashItem = buildMediaItem(streamUrl, androidx.media3.common.MimeTypes.APPLICATION_MPD)
                                    val pos = p.currentPosition.coerceAtLeast(0L)
                                    p.replaceMediaItem(idx, dashItem)
                                    if (pos > 0L) p.seekTo(pos)
                                    p.prepare()
                                    p.playWhenReady = playWhenResolved
                                }
                            }
                        }
                        throw DashRedirectException(streamUrl)
                    }

                    dataSpec.buildUpon()
                        .setUri(android.net.Uri.parse(streamUrl))
                        .setKey(null)
                        .build()
                } finally {
                    releaseResolveWakeLocks(wakeLock, wifiLock)
                }
            },
        )
    }

    /** Pre-cache the first [PRELOAD_BYTES] of [url] into the media cache (http(s) only). */
    private fun cacheIntro(url: String, appContext: Context) {
        if (!url.startsWith("http")) return
        if (!com.music.spotui.data.preferences.isPreloadEnabled(appContext)) return
        val cache = mediaCache(appContext) ?: return
        runCatching {
            val upstream = androidx.media3.datasource.DefaultDataSource.Factory(
                appContext,
                org.eclipse.Mrbean.client.MrbeanMediaDataSource.upstreamFactory(appContext),
            )
            val ds = androidx.media3.datasource.cache.CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(upstream)
                .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                .createDataSource()
            val spec = androidx.media3.datasource.DataSpec.Builder()
                .setUri(android.net.Uri.parse(url))
                .setLength(PRELOAD_BYTES)
                .build()
            androidx.media3.datasource.cache.CacheWriter(ds, spec, null, null).cache()
        }.onFailure { Log.d(TAG, "intro preload skipped: ${it.message}") }
    }

    // forPlayback=true only for the track actually being played — so background
    // prefetch of upcoming tracks doesn't clobber the current source badge (a
    // prefetch resolving the NEXT track via YouTube was flipping the badge to
    // "YouTube" while the current track streamed from Spotify).
    internal suspend fun resolveStreamUrl(song: String, appContext: Context, forPlayback: Boolean = false): String? {
        if (song.startsWith("content://", ignoreCase = true) || song.startsWith("file://", ignoreCase = true)) {
            if (forPlayback) {
                currentSource = "Local Audio"
                currentQuality = song.substringAfterLast('.', "").uppercase().takeIf { it.length in 2..5 } ?: "LOCAL"
                updateResolveStatus(false)
            }
            return song
        }
        if (forPlayback) {
            updateResolveStatus(true, "Checking cache...")
        }
        streamCache[song]?.let {
            if (forPlayback) {
                currentSource = sourceCache[song] ?: "YouTube"
                currentQuality = qualityCache[song] ?: ""
                updateResolveStatus(false)
            }
            return it
        }
        // Persistent stream cache: survives app restarts. YouTube URLs are cached
        // with their server-provided expiry so stale entries are never served.
        if (forPlayback) {
            updateResolveStatus(true, "Checking saved cache...")
        }
        com.music.spotui.data.preferences.getCachedStream(appContext, song)?.let { (url, source, quality) ->
            // Promote back into the in-memory caches for this session.
            streamCache[song] = url
            sourceCache[song] = source
            qualityCache[song] = quality
            if (forPlayback) {
                currentSource = source
                currentQuality = quality
                updateResolveStatus(false)
            }
            return url
        }
        if (forPlayback) {
            updateResolveStatus(true, "Checking alternative source...")
        }
        alternativeStreamForPlayback(song, appContext)?.let { alt ->
            return when {
                alt.isLocal -> {
                    if (forPlayback) {
                        currentSource = "Alternative file"
                        currentQuality = alt.label.substringAfterLast('.', "").uppercase().takeIf { it.length in 2..5 }.orEmpty()
                        updateResolveStatus(false)
                    }
                    alt.value
                }
                alt.isYouTube -> {
                    if (forPlayback) {
                        currentSource = "Alternative YouTube"
                        currentQuality = ""
                    }
                    val quality = com.music.spotui.data.preferences.currentStreamingQuality(appContext)
                    val playback = resolveYtPlayback(alt.value, quality.audioQuality, appContext, forPlayback = forPlayback) ?: run {
                        if (forPlayback) updateResolveStatus(false)
                        return null
                    }
                    val codec = playback.format.mimeType
                        .substringAfter("codecs=\"", "").substringBefore('"').substringBefore('.')
                        .uppercase()
                    val ytQuality = listOf(codec, "${playback.format.bitrate / 1000} kbps")
                        .filter { it.isNotBlank() }.joinToString(" ")
                    if (forPlayback) {
                        currentQuality = ytQuality
                        updateResolveStatus(false)
                    }

                    streamCache[song] = playback.streamUrl
                    sourceCache[song] = if (forPlayback) currentSource else "Alternative YouTube"
                    qualityCache[song] = if (forPlayback) currentQuality else ytQuality

                    playback.streamUrl
                }
                else -> {
                    if (forPlayback) updateResolveStatus(false)
                    null
                }
            }
        }
        // Offline: if this track was downloaded, play the local file instead of the network.
        if (forPlayback) {
            updateResolveStatus(true, "Locating local file...")
        }
        com.music.spotui.data.preferences.downloadedPathForQuery(appContext, song)?.let { path ->
            if (forPlayback) {
                currentSource = "Downloaded"
                currentQuality = path.substringAfterLast('.', "").uppercase()
                updateResolveStatus(false)
            }
            return android.net.Uri.fromFile(java.io.File(path)).toString()
        }
        // Coalesce concurrent network resolutions for the same song query so ExoPlayer loader +
        // preResolve/prefetch never kick off duplicate SpotiFLAC/YouTube resolution chains.
        inFlightResolves[song]?.let { existing ->
            val joined = runCatching { existing.await() }.getOrNull()
            if (forPlayback && joined != null) {
                currentSource = sourceCache[song] ?: currentSource
                currentQuality = qualityCache[song] ?: currentQuality
                updateResolveStatus(false)
            }
            return joined
        }

        val deferred = kotlinx.coroutines.CompletableDeferred<String?>()
        val winner = inFlightResolves.putIfAbsent(song, deferred)
        if (winner != null) {
            val joined = runCatching { winner.await() }.getOrNull()
            if (forPlayback && joined != null) {
                currentSource = sourceCache[song] ?: currentSource
                currentQuality = qualityCache[song] ?: currentQuality
                updateResolveStatus(false)
            }
            return joined
        }

        return try {
            resolveStreamUrlNetwork(song, appContext, forPlayback).also { resolved ->
                deferred.complete(resolved)
            }
        } catch (t: Throwable) {
            deferred.complete(null)
            throw t
        } finally {
            inFlightResolves.remove(song, deferred)
        }
    }

    private suspend fun resolveStreamUrlNetwork(
        song: String,
        appContext: Context,
        forPlayback: Boolean,
    ): String? {
        // Quality for the current network (Wi-Fi vs cellular), from Settings.
        val quality = com.music.spotui.data.preferences.currentStreamingQuality(appContext)
        // ── Parallel resolution ──
        // FLAC community proxies are slow and unreliable. Instead of blocking on
        // FLAC first then starting YouTube after it fails, we launch both in
        // parallel. If FLAC wins, we cancel YouTube. If FLAC loses (timeout/
        // cooldown/miss), YouTube is already resolved — no wait.
        //
        // The candidates/search step of resolveYtPlayback also runs in this scope;
        // if FLAC succeeds, that search result is just discarded.
        val shouldTryFlac = losslessStreaming && quality.lossless &&
            com.metrolist.spotify.SpotiFlac.anyLosslessServerUp()
        val shouldTryYoutube = youtubeEnabled

        if (!shouldTryFlac && !shouldTryYoutube) {
            Log.w(TAG, "no stream source available for: $song")
            if (forPlayback) updateResolveStatus(false)
            return null
        }

        if (forPlayback) {
            if (shouldTryFlac) {
                currentSource = "Lossless"
                currentQuality = ""
                updateResolveStatus(true, "Searching Lossless (Tidal, Qobuz, Deezer, Amazon)...")
            } else if (shouldTryYoutube) {
                currentSource = "YouTube"
                currentQuality = ""
                updateResolveStatus(true, "Locating YouTube source...")
            }
        }

        val enabledLosslessProviders = com.music.spotui.data.preferences.ProviderRoutingPreferences.enabledProviders(appContext)
        val soloLossless = com.music.spotui.data.preferences.ProviderRoutingPreferences.soloProvider(appContext)
        val losslessOrderList = com.music.spotui.data.preferences.ProviderRoutingPreferences.losslessOrder(appContext)

        val flacDeferred = if (shouldTryFlac) {
            scope.async {
                val timeoutMs = com.music.spotui.data.preferences.currentLosslessTimeout(appContext).timeoutMs
                kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
                    val flacSpotifyId = trackIdRegistry[song] ?: spotifyTrackIdForPlayback(song)
                    if (flacSpotifyId == null) {
                        return@withTimeoutOrNull null
                    }
                    val isrc = isrcRegistry[flacSpotifyId] ?: runCatching {
                        com.metrolist.spotify.Spotify.track(flacSpotifyId).getOrNull()?.isrc?.also {
                            isrcRegistry[flacSpotifyId] = it
                        }
                    }.getOrNull()
                    val spotiResult = com.metrolist.spotify.SpotiFlac.resolve(
                        flacSpotifyId,
                        isrc = isrc,
                        preferHiRes = losslessHiRes,
                        providerOrder = losslessOrderList,
                        rejectPreviewLength = com.music.spotui.data.preferences.ProviderRoutingPreferences.preferFullLength(appContext),
                        enabledProviders = enabledLosslessProviders,
                        soloProvider = soloLossless,
                    )
                    if (spotiResult is com.metrolist.spotify.SpotiFlac.Result.Success) {
                        return@withTimeoutOrNull spotiResult
                    }
                    // Direct encrypted Deezer ARL fallback when enabled (handles cases where spotbye community proxy is in cooldown)
                    val allowDeezerDirect = "deezer" in enabledLosslessProviders &&
                        (!soloLossless || losslessOrderList.firstOrNull { it in enabledLosslessProviders } == "deezer")
                    if (allowDeezerDirect && com.music.spotui.deezer.DeezerSource.isConfigured(appContext)) {
                        val dz = com.music.spotui.deezer.DeezerSource.resolve(
                            context = appContext,
                            spotifyId = flacSpotifyId,
                            isrc = isrc,
                            searchQuery = searchTextForPlayback(song),
                        )
                        if (dz is com.music.spotui.deezer.DeezerSource.Result.Success) {
                            return@withTimeoutOrNull com.metrolist.spotify.SpotiFlac.Result.Success(
                                com.metrolist.spotify.SpotiFlac.LosslessTrack(
                                    url = dz.uri,
                                    provider = "deezer",
                                    quality = if (dz.mimeFlac) "16" else "mp3",
                                    container = if (dz.mimeFlac) "flac" else "mp3",
                                )
                            )
                        }
                    }
                    spotiResult
                }
            }
        } else null

        val ytDeferred = if (shouldTryYoutube) {
            scope.async {
                // If checking FLAC in parallel, run YouTube resolution silently in the background
                // without updating UI state or status message until fallback is needed.
                val runForPlayback = forPlayback && !shouldTryFlac
                if (runForPlayback) {
                    currentSource = "YouTube"
                    currentQuality = ""
                    updateResolveStatus(true, "Locating YouTube source...")
                }
                resolveYtPlayback(song, quality.audioQuality, appContext, forPlayback = runForPlayback)
            }
        } else null

        var flacFailReason: String? = null

        // Check FLAC first
        if (flacDeferred != null) {
            val flacResult = flacDeferred.await()
            when (flacResult) {
                is com.metrolist.spotify.SpotiFlac.Result.Success -> {
                    Log.d(TAG, "lossless ${flacResult.track.provider} ${flacResult.track.quality}-bit for: $song")
                    val providerName = flacResult.track.provider.replaceFirstChar { it.uppercase() }
                    val flacQuality = if (flacResult.track.container == "mp3") "MP3 320 kbps" else "FLAC ${flacResult.track.quality}-bit"
                    val labelSource = if (flacResult.track.container == "mp3") "Deezer Direct" else "Lossless • $providerName"
                    if (forPlayback) {
                        currentSource = labelSource
                        currentQuality = flacQuality
                        val note = "Source: $providerName • Format: $flacQuality • Container: ${flacResult.track.container.uppercase()}"
                        boundState?.updateResolveDetailNote(note)
                        updateResolveStatus(false)
                    }
                    sourceCache[song] = labelSource
                    qualityCache[song] = flacQuality
                    if (!flacResult.track.url.startsWith("deezer://")) {
                        streamCache[song] = flacResult.track.url
                    }
                    ytDeferred?.cancelAndJoin()
                    return flacResult.track.url
                }
                is com.metrolist.spotify.SpotiFlac.Result.Cooldown -> {
                    flacFailReason = "Provider cooldown (${flacResult.message})"
                    Log.d(TAG, "lossless provider on cooldown (${flacResult.message}), using YouTube for: $song")
                }
                null -> {
                    flacFailReason = "Lossless timeout"
                    Log.w(TAG, "lossless timed out, using YouTube for: $song")
                }
                else -> {
                    flacFailReason = "Track unavailable on FLAC mirrors"
                    Log.w(TAG, "lossless miss ($flacResult), using YouTube for: $song")
                }
            }
        }

        // FLAC didn't work — use YouTube (already resolving in parallel).
        if (!shouldTryYoutube) {
            Log.w(TAG, "YouTube fallback disabled — no stream for: $song")
            if (forPlayback) updateResolveStatus(false)
            return null
        }

        if (forPlayback) {
            currentSource = "YouTube"
            currentQuality = ""
            val note = if (flacFailReason != null) {
                "FLAC unavailable ($flacFailReason) → Switched to YouTube fallback"
            } else {
                "Source: YouTube • Quality: ${quality.audioQuality}"
            }
            boundState?.updateResolveDetailNote(note)
            // Show resolve status only if YouTube is not fully loaded yet.
            if (ytDeferred != null && !ytDeferred.isCompleted) {
                updateResolveStatus(true, "Locating YouTube source...")
            }
        }

        val playback = ytDeferred?.await()
        if (playback == null) {
            if (forPlayback) {
                val cacheKey = "$song|${com.metrolist.innertube.YouTube.SearchFilter.FILTER_SONG.value}"
                val cachedCandidates = videoCandidatesCache[cacheKey]
                val reason = when {
                    cachedCandidates == null -> "YouTube search failed"
                    cachedCandidates.isEmpty() -> "No matching song found on YouTube"
                    else -> lastYtFailureReason ?: "All candidates failed to resolve"
                }
                boundState?.updateResolveError(reason)
                updateResolveStatus(false)
            }
            return null
        }
        // e.g. "OPUS 141 kbps" from the chosen adaptive format.
        val codec = playback.format.mimeType
            .substringAfter("codecs=\"", "").substringBefore('"').substringBefore('.')
            .uppercase()
        val ytQuality = listOf(codec, "${playback.format.bitrate / 1000} kbps")
            .filter { it.isNotBlank() }.joinToString(" ")
        if (forPlayback) {
            currentQuality = ytQuality
            updateResolveStatus(false)
        }
        streamCache[song] = playback.streamUrl
        sourceCache[song] = "YouTube"
        qualityCache[song] = ytQuality
        // Persist to disk so replays after an app restart skip the whole pipeline.
        com.music.spotui.data.preferences.setCachedStream(
            appContext, song, playback.streamUrl, "YouTube", ytQuality,
            playback.streamExpiresInSeconds,
        )
        return playback.streamUrl
    }

    private fun alternativeStreamForPlayback(
        song: String,
        appContext: Context,
    ): com.music.spotui.data.preferences.AlternativeStream? {
        val key = alternativeKeyRegistry[song]
            ?: spotifyTrackIdForPlayback(song)?.let {
                com.music.spotui.data.preferences.alternativeStreamKeyForSpotifyId(it)
            }
        return key?.let { com.music.spotui.data.preferences.getAlternativeStream(appContext, it) }
    }

    // ── Downloads (offline playback) ──
    // Tracks which song queries are mid-download so the UI can show a spinner and
    // we don't kick off the same download twice.
    private val downloading = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    )
    @Volatile var onDownloadsChanged: (() -> Unit)? = null

    // Per-query download progress, 0..100. Present only while a download is active.
    private val downloadProgress = java.util.concurrent.ConcurrentHashMap<String, Int>()
    // The actual SongsModel of each in-progress download, so the Downloads screen can
    // render it (with a progress bar) before the file exists / it's added to prefs.
    private val downloadingSongs =
        java.util.concurrent.ConcurrentHashMap<String, com.music.spotui.data.entity.SongsModel>()

    fun isDownloading(query: String): Boolean = downloading.contains(query)

    /** Current download progress (0..100) for a query, or -1 if unknown/not downloading. */
    fun downloadProgress(query: String): Int = downloadProgress[query] ?: -1

    /** Snapshot of the currently-downloading tracks paired with their percent (0..100). */
    fun downloadingSnapshot(): List<Pair<com.music.spotui.data.entity.SongsModel, Int>> =
        downloadingSongs.entries.map { (q, song) -> song to (downloadProgress[q] ?: 0) }

    // Last download failure reason, surfaced to the user as a Toast for diagnosis.
    @Volatile var lastDownloadError: String? = null

    // googlevideo stream URLs 403 without a browser User-Agent and need redirects
    // followed (http↔https) — ExoPlayer does both, so a raw URLConnection must too.
    private fun openDownloadConn(url: String): java.net.HttpURLConnection {
        val context = appCtx ?: com.music.spotui.MyApplication.instanceOrNull
        val tuning = context?.let { MrbeanNetworkSettings.snapshot(it) }
        val connectMs = if (tuning != null && tuning.enabled) tuning.connectTimeoutMs else 15_000
        val readMs = if (tuning != null && tuning.enabled) tuning.readTimeoutMs else 30_000
        val activeProxy = com.metrolist.innertube.network.AuthSessionStore.streamOkHttpProxy()
        val conn = if (activeProxy != null && activeProxy != java.net.Proxy.NO_PROXY) {
            java.net.URL(url).openConnection(activeProxy)
        } else {
            java.net.URL(url).openConnection()
        }
        return (conn as java.net.HttpURLConnection).apply {
            connectTimeout = connectMs
            readTimeout = readMs
            instanceFollowRedirects = true
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Pixel) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
            )
            // Range accounting must be against raw bytes; transparent compression breaks byte positions.
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("Accept", "*/*")
            // Some signed media endpoints reject otherwise-valid byte-range requests
            // that lack the web-player request context.
            setRequestProperty("Referer", "https://music.youtube.com/")
            setRequestProperty("Origin", "https://music.youtube.com")
        }
    }

    /**
     * Download [url] to [tmpFile] using HTTP **Range** requests in chunks, reporting
     * progress (0..100) for [query]. A single full-file GET of a googlevideo stream gets
     * reset partway through (`SocketException: Connection reset`) — the server expects the
     * audio fetched in byte ranges, which is how ExoPlayer/NewPipe get it. Each chunk is a
     * short connection (retried a few times on reset); writing is append-continuous so a
     * retried chunk resumes from the current byte position. Returns true iff the whole
     * file was written. Falls back gracefully if the server ignores Range (HTTP 200).
     */
    private fun httpDownloadRanged(url: String, tmpFile: java.io.File, query: String): Boolean {
        val context = appCtx ?: com.music.spotui.MyApplication.instanceOrNull
        val tuning = context?.let { MrbeanNetworkSettings.snapshot(it) }
        val chunkMiB = if (tuning != null && tuning.enabled) tuning.chunkMiB else 8
        val maxRetries = if (tuning != null && tuning.enabled) tuning.rangeRetries else 3
        val chunk = chunkMiB.toLong() * 1024 * 1024
        var total = -1L
        var position = 0L
        downloadProgress[query] = 0
        try {
            java.io.BufferedOutputStream(tmpFile.outputStream()).use { output ->
                outer@ while (true) {
                    val end = if (total > 0) minOf(position + chunk - 1, total - 1) else position + chunk - 1
                    var attempt = 0
                    var fullBody = false
                    while (true) {
                        attempt++
                        val conn = openDownloadConn(url)
                        conn.setRequestProperty("Range", "bytes=$position-$end")
                        try {
                            val code = conn.responseCode
                            if (code !in 200..299) {
                                val retryable = code == 408 || code == 425 || code == 429 || code in 500..599
                                if (retryable && attempt < maxRetries) {
                                    Log.w(TAG, "chunk @$position returned HTTP $code; retrying $attempt/$maxRetries")
                                    Thread.sleep((attempt * 350L).coerceAtMost(1_500L))
                                    continue
                                }
                                lastDownloadError = "Stream returned HTTP $code"
                                return false
                            }
                            val contentRange = conn.getHeaderField("Content-Range")
                            if (code == java.net.HttpURLConnection.HTTP_PARTIAL) {
                                val rangeStart = contentRange
                                    ?.substringAfter("bytes ", "")
                                    ?.substringBefore('-')
                                    ?.toLongOrNull()
                                if (rangeStart == null || rangeStart != position) {
                                    lastDownloadError = "Server returned an invalid byte range"
                                    return false
                                }
                            }
                            if (total < 0) {
                                total = contentRange?.substringAfter('/')?.toLongOrNull() ?: conn.contentLengthLong
                            }
                            fullBody = code == 200 // server ignored Range → whole file in one body
                            if (fullBody && position > 0L) {
                                // Appending a complete 200 response after prior 206 chunks corrupts the file.
                                lastDownloadError = "Server ignored resumed byte range"
                                return false
                            }
                            conn.inputStream.use { input ->
                                val buf = ByteArray(64 * 1024)
                                while (true) {
                                    val r = input.read(buf)
                                    if (r < 0) break
                                    output.write(buf, 0, r)
                                    position += r
                                    if (total > 0) {
                                        val pct = ((position * 100) / total).toInt().coerceIn(0, 100)
                                        if (downloadProgress[query] != pct) {
                                            downloadProgress[query] = pct
                                            onDownloadsChanged?.invoke()
                                        }
                                    }
                                }
                            }
                            break // this chunk completed
                        } catch (e: Exception) {
                            Log.w(TAG, "chunk @${position} failed (attempt $attempt): ${e.message}")
                            if (attempt >= maxRetries) {
                                lastDownloadError = e.message ?: "Connection reset"
                                return false
                            }
                            // retry the remainder of this chunk from the current position
                        } finally {
                            conn.disconnect()
                        }
                    }
                    if (fullBody) { total = position; break@outer }
                    if (total in 1..position) break@outer
                    if (total < 0) break@outer // couldn't determine size; assume done
                }
            }
            downloadProgress[query] = 100
            return total <= 0 || position >= total
        } catch (e: Exception) {
            lastDownloadError = e.message ?: "Download error"
            return false
        }
    }

    /**
     * Resolve the track's stream and save the audio to local storage for offline
     * playback. Runs on the IO scope; invokes [onComplete] (main thread) with whether
     * it succeeded. No-op if it's already downloaded or downloading.
     */
    /**
     * Downloads a collection strictly in its supplied order. Parallel resolver requests were
     * causing PoToken/player chains to compete and produced intermittent "Couldn't resolve
     * stream" failures for playlists. The queue advances only after each track has completed
     * (success or failure), so a failed item never stalls the remaining playlist.
     */
    fun downloadAll(songs: List<com.music.spotui.data.entity.SongsModel>, context: Context) {
        val appContext = context.applicationContext
        val ordered = songs.distinctBy { it.id to it.url }
        scope.launch {
            ordered.forEachIndexed { index, song ->
                if (song.url.isBlank() || com.music.spotui.data.preferences.isDownloaded(appContext, song.id.toString())) {
                    return@forEachIndexed
                }
                // A direct user action may already own this item; preserve order by waiting
                // for the next queue item instead of launching a competing resolve.
                if (isDownloading(song.url)) return@forEachIndexed
                val completion = kotlinx.coroutines.CompletableDeferred<Boolean>()
                withContext(Dispatchers.Main) {
                    downloadSong(song, appContext) { completion.complete(it) }
                }
                val success = completion.await()
                AppDiagnostics.info(
                    TAG,
                    "Ordered download ${index + 1}/${ordered.size}: ${song.title} (${if (success) "complete" else "failed"})",
                )
                // Small spacing lets the source resolver release network/session resources.
                delay(180)
            }
        }
    }

    /** True once every track in [songs] is downloaded (for the album's "downloaded" state). */
    fun allDownloaded(
        songs: List<com.music.spotui.data.entity.SongsModel>,
        context: Context,
    ): Boolean {
        if (songs.isEmpty()) return false
        val appContext = context.applicationContext
        return songs.all {
            com.music.spotui.data.preferences.isDownloaded(appContext, it.id.toString())
        }
    }

    fun downloadSong(
        song: com.music.spotui.data.entity.SongsModel,
        context: Context,
        onComplete: (Boolean) -> Unit = {},
    ) {
        val appContext = context.applicationContext
        val query = song.url
        if (query.isBlank() ||
            com.music.spotui.data.preferences.isDownloaded(appContext, song.id.toString()) ||
            !downloading.add(query)
        ) return
        downloadingSongs[query] = song
        downloadProgress[query] = 0
        onDownloadsChanged?.invoke()
        lastDownloadError = null
        scope.launch {
            val ok = runCatching { downloadToFile(song, appContext) }
                .onFailure { lastDownloadError = it.message ?: "Unexpected error" }
                .getOrDefault(false)
            downloading.remove(query)
            downloadProgress.remove(query)
            downloadingSongs.remove(query)
            withContext(Dispatchers.Main) {
                if (!ok) {
                    android.widget.Toast.makeText(
                        appContext,
                        "Download failed: ${lastDownloadError ?: "unknown reason"}",
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                }
                onDownloadsChanged?.invoke()
                onComplete(ok)
            }
        }
    }

    private suspend fun downloadToFile(
        song: com.music.spotui.data.entity.SongsModel,
        appContext: Context,
    ): Boolean {
        val dlQuality = com.music.spotui.data.preferences.getDownloadQuality(appContext)
        registerLossless(listOf(song.url to song.spotifyTrackId))
        registerExplicit(listOf(song.url to song.explicit))
        registerDuration(listOf(song.url to song.durationMs))
        registerMetadata(listOf(song.url to TrackMatchMetadata(song.title, song.singer, song.album)))
        // Prefer a true lossless FLAC download (SpotiFLAC) when the download quality is
        // Lossless and we have a Spotify id, but bound it with a timeout — the community
        // proxies are often slow/on-cooldown and must NOT stall the whole download. On any
        // miss/timeout fall back to the YouTube m4a path at the chosen quality.
        if (dlQuality.lossless && song.spotifyTrackId.isNotBlank()) {
            val flacOk = kotlinx.coroutines.withTimeoutOrNull(30_000) {
                runCatching { downloadFlacToFile(song, appContext) }.getOrDefault(false)
            } ?: false
            if (flacOk) return true
        }
        if (!youtubeEnabled) {
            lastDownloadError = "Track not available on lossless providers"
            return false
        }

        val query = song.url
        // Resolve a fresh network stream URL (bypass any local-file short-circuit),
        // walking the ranked video candidates like playback does.
        val qualityLadder = listOf(
            dlQuality.audioQuality,
            com.metrolist.music.constants.AudioQuality.AUTO,
            com.metrolist.music.constants.AudioQuality.HIGH,
            com.metrolist.music.constants.AudioQuality.LOW,
        ).distinct()
        val playback = qualityLadder.firstNotNullOfOrNull { quality ->
            resolveYtPlayback(query, quality, appContext, forceFreshCandidates = quality != dlQuality.audioQuality)
        } ?: run {
            lastDownloadError = lastYtFailureReason?.let { "Couldn't resolve stream: $it" } ?: "Couldn't resolve a stream. Check connection and try again."
            return false
        }

        val dir = java.io.File(appContext.filesDir, "downloads").apply { mkdirs() }
        fun extensionFor(mimeType: String): String = when {
            mimeType.contains("webm", ignoreCase = true) -> "webm"
            mimeType.contains("opus", ignoreCase = true) || mimeType.contains("ogg", ignoreCase = true) -> "opus"
            mimeType.contains("flac", ignoreCase = true) -> "flac"
            mimeType.contains("mpeg", ignoreCase = true) || mimeType.contains("mp3", ignoreCase = true) -> "mp3"
            else -> "m4a"
        }
        var extension = extensionFor(playback.format.mimeType)
        var outFile = java.io.File(dir, "${song.id}.$extension")
        var tmpFile = java.io.File(dir, "${song.id}.$extension.part")
        runCatching { tmpFile.delete() }

        var downloaded = httpDownloadRanged(playback.streamUrl, tmpFile, song.url)
        // Stream URLs are short-lived. A rejected/expired URL gets a fresh uncached
        // resolution; the retry also recalculates its container so its filename stays valid.
        if (!downloaded) {
            runCatching { tmpFile.delete() }
            val refreshed = resolveYtPlayback(
                query,
                com.metrolist.music.constants.AudioQuality.AUTO,
                appContext,
                forceFreshCandidates = true,
            )
            if (refreshed != null) {
                extension = extensionFor(refreshed.format.mimeType)
                outFile = java.io.File(dir, "${song.id}.$extension")
                tmpFile = java.io.File(dir, "${song.id}.$extension.part")
                runCatching { tmpFile.delete() }
                downloaded = httpDownloadRanged(refreshed.streamUrl, tmpFile, song.url)
            }
        }
        if (!downloaded) {
            runCatching { tmpFile.delete() }
            return false
        }
        runCatching { if (outFile.exists()) outFile.delete() }
        if (!tmpFile.renameTo(outFile)) {
            val copied = runCatching {
                tmpFile.inputStream().use { input -> outFile.outputStream().use { output -> input.copyTo(output) } }
                tmpFile.delete()
                true
            }.getOrDefault(false)
            if (!copied) {
                lastDownloadError = "Couldn't finalize downloaded file"
                runCatching { tmpFile.delete() }
                return false
            }
        }
        com.music.spotui.data.preferences.addDownload(appContext, song, outFile.absolutePath)
        downloadCoverImage(song.coverUri, java.io.File(dir, "${song.id}_cover.jpg"))
        // Eagerly cache lyrics so offline playback doesn't need a network round-trip.
        LyricsApi.removeFromCache(song.title, song.singer)
        val lyricsOk = runCatching {
            LyricsApi.fetch(song.title, song.singer, song.album, song.durationMs / 1000)
        }.getOrNull() != null
        if (!lyricsOk) LyricsApi.removeFromCache(song.title, song.singer)
        return true
    }

    private fun downloadCoverImage(coverUrl: String, destFile: java.io.File) {
        if (coverUrl.isBlank() || destFile.exists()) return
        runCatching {
            val conn = (java.net.URL(coverUrl).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 10000
                instanceFollowRedirects = true
            }
            if (conn.responseCode in 200..299) {
                conn.inputStream.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    /**
     * Download a true lossless FLAC via SpotiFLAC. Resolves the track's ISRC first
     * (improves Qobuz matching), asks SpotiFLAC for a FLAC URL, and saves it as
     * `<id>.flac`. Returns false on any miss/cooldown so the caller can fall back.
     */
    private suspend fun downloadFlacToFile(
        song: com.music.spotui.data.entity.SongsModel,
        appContext: Context,
    ): Boolean {
        val isrc = runCatching {
            com.metrolist.spotify.Spotify.track(song.spotifyTrackId).getOrNull()?.isrc
        }.getOrNull()
        val dlTimeoutMs = com.music.spotui.data.preferences.getDownloadLosslessTimeout(appContext).timeoutMs
        val order = com.music.spotui.data.preferences.ProviderRoutingPreferences.losslessOrder(appContext)
        val enabled = com.music.spotui.data.preferences.ProviderRoutingPreferences.enabledProviders(appContext)
        val solo = com.music.spotui.data.preferences.ProviderRoutingPreferences.soloProvider(appContext)
        val flacResult = kotlinx.coroutines.withTimeoutOrNull(dlTimeoutMs) {
            com.metrolist.spotify.SpotiFlac.resolve(
                song.spotifyTrackId,
                isrc,
                preferHiRes = losslessHiRes,
                providerOrder = order,
                rejectPreviewLength = com.music.spotui.data.preferences.ProviderRoutingPreferences.preferFullLength(appContext),
                enabledProviders = enabled,
                soloProvider = solo,
            )
        }
        val flac = when (flacResult) {
            is com.metrolist.spotify.SpotiFlac.Result.Success -> flacResult.track
            else -> {
                // Direct encrypted Deezer download fallback when ARL is configured & enabled
                val allowDeezer = "deezer" in enabled && (!solo || order.firstOrNull { it in enabled } == "deezer")
                if (allowDeezer && com.music.spotui.deezer.DeezerSource.isConfigured(appContext)) {
                    val dz = com.music.spotui.deezer.DeezerSource.resolveRaw(
                        context = appContext,
                        spotifyId = song.spotifyTrackId,
                        isrc = isrc,
                        searchQuery = searchTextForPlayback(song.url),
                    )
                    if (dz != null) {
                        val dir = java.io.File(appContext.filesDir, "downloads").apply { mkdirs() }
                        val ext = if (dz.isFlac) "flac" else "mp3"
                        val outFile = java.io.File(dir, "${song.id}.$ext")
                        val tmpFile = java.io.File(dir, "${song.id}.$ext.part")
                        val dzOk = com.music.spotui.deezer.DeezerSource.downloadDecryptedToFile(dz, tmpFile) { pct ->
                            downloadProgress[song.url] = pct
                            onDownloadsChanged?.invoke()
                        }
                        if (dzOk && tmpFile.renameTo(outFile)) {
                            com.music.spotui.data.preferences.addDownload(appContext, song, outFile.absolutePath)
                            downloadCoverImage(song.coverUri, java.io.File(dir, "${song.id}_cover.jpg"))
                            return true
                        }
                        runCatching { tmpFile.delete() }
                    }
                }
                if (flacResult is com.metrolist.spotify.SpotiFlac.Result.Cooldown) {
                    Log.w(TAG, "FLAC download on cooldown for ${song.title}: ${flacResult.message}")
                }
                return false
            }
        }

        val dir = java.io.File(appContext.filesDir, "downloads").apply { mkdirs() }
        val outFile = java.io.File(dir, "${song.id}.flac")
        val tmpFile = java.io.File(dir, "${song.id}.flacpart")
        // TIDAL lossless is segmented DASH — remux the segments into a real .flac;
        // single-file providers (Qobuz) download directly.
        val ok = if (flac.container == "dash") {
            com.metrolist.spotify.SpotiFlac.downloadDashFlacToFile(flac.url, tmpFile)
        } else {
            httpDownloadRanged(flac.url, tmpFile, song.url)
        }
        if (!ok) {
            Log.e(TAG, "FLAC download failed for ${song.title}: $lastDownloadError")
            runCatching { tmpFile.delete() }
            return false
        }
        if (!tmpFile.renameTo(outFile)) {
            runCatching { tmpFile.delete() }
            return false
        }
        com.music.spotui.data.preferences.addDownload(appContext, song, outFile.absolutePath)
        downloadCoverImage(song.coverUri, java.io.File(dir, "${song.id}_cover.jpg"))
        // Eagerly cache lyrics so offline playback doesn't need a network round-trip.
        LyricsApi.removeFromCache(song.title, song.singer)
        val lyricsOk = runCatching {
            LyricsApi.fetch(song.title, song.singer, song.album, song.durationMs / 1000)
        }.getOrNull() != null
        if (!lyricsOk) LyricsApi.removeFromCache(song.title, song.singer)
        Log.d(TAG, "FLAC downloaded (${flac.provider} ${flac.quality}-bit): ${song.title}")
        return true
    }

    private data class CandidateScore(
        val item: SongItem,
        val score: Double,
        val titleScore: Double,
        val artistScore: Double,
        val artistEvidenceScore: Double,
        val durationScore: Double?,
        val albumScore: Double?,
        val alternatePenalty: Double,
        val unexpectedAlternates: List<String>,
    )

    private fun normalizedForMatch(value: String): String =
        value.lowercase()
            .replace(featSearchPattern, "")
            .replace(Regex("""[^\p{L}\p{Nd}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private val anyLatinTransliterator by lazy {
        runCatching {
            android.icu.text.Transliterator.getInstance("Any-Latin; Latin-ASCII")
        }.getOrNull()
    }

    private fun foldLatinDiacritics(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")

    private fun transliterateCyrillic(value: String): String {
        val map = mapOf(
            'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
            'е' to "e", 'ё' to "e", 'ж' to "zh", 'з' to "z", 'и' to "i",
            'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
            'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
            'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "ts", 'ч' to "ch",
            'ш' to "sh", 'щ' to "sch", 'ъ' to "", 'ы' to "y", 'ь' to "",
            'э' to "e", 'ю' to "yu", 'я' to "ya",
        )
        return buildString {
            value.lowercase().forEach { ch ->
                append(map[ch] ?: ch)
            }
        }
    }

    private fun bigramSimilarity(a: String, b: String): Double {
        fun variants(value: String): List<String> =
            listOfNotNull(
                value,
                foldLatinDiacritics(value),
                transliterateCyrillic(value),
                anyLatinTransliterator?.transliterate(value),
            )
                .map(::normalizedForMatch)
                .filter { it.isNotBlank() }
                .distinct()

        fun score(na: String, nb: String): Double {
            if (na == nb) return 1.0
            if (na.length < 2 || nb.length < 2) return 0.0
            val aBigrams = na.windowed(2).toSet()
            val bBigrams = nb.windowed(2).toSet()
            if (aBigrams.isEmpty() || bBigrams.isEmpty()) return 0.0
            val intersection = aBigrams.count { it in bBigrams }
            return (2.0 * intersection) / (aBigrams.size + bBigrams.size)
        }
        return variants(a).maxOf { aa ->
            variants(b).maxOf { bb -> score(aa, bb) }
        }
    }

    private data class VersionMarker(
        val name: String,
        val pattern: Regex,
        val hardReject: Boolean,
    )

    private fun markerPattern(terms: String): Regex =
        Regex("""(^|\s)($terms)(\s|$)""", RegexOption.IGNORE_CASE)

    private val alternateVersionMarkers = listOf(
        VersionMarker("remix", markerPattern("""re\s*mix|rmx|club mix|dance mix|dub mix|vip mix|ремикс|рмикс"""), true),
        VersionMarker("alternate", markerPattern("""alternative|alternate|alt version|demo|demo version|unreleased|rough mix|early version|альтернатив\w*|демо|неиздан\w*|чернов\w*"""), true),
        VersionMarker("sped up", markerPattern("""sped\s*up|speed\s*up|fast version|ускоренн\w*|быстрая версия"""), true),
        VersionMarker("slowed", markerPattern("""slowed|slowed reverb|slow version|замедленн\w*|медленная версия"""), true),
        VersionMarker("nightcore", markerPattern("""nightcore|daycore"""), true),
        VersionMarker("live", markerPattern("""live|concert|session|performance|лайв|концерт|с концерта|выступлен\w*"""), true),
        VersionMarker("acoustic", markerPattern("""acoustic|unplugged|piano version|guitar version|акустик\w*|пианино|гитар\w*"""), true),
        VersionMarker("cover", markerPattern("""cover|covered by|tribute|кавер|трибьют"""), true),
        VersionMarker("karaoke", markerPattern("""karaoke|minus one|караоке|минусовка"""), true),
        VersionMarker("instrumental", markerPattern("""instrumental|no vocals|инструментал|без вокала"""), true),
        VersionMarker("mashup", markerPattern("""mashup|mash up|bootleg|rework|flip|мешап|мэшап|бутлег"""), true),
        VersionMarker("fan edit", markerPattern("""fan edit|fanmade|right version|edit audio|перезалив|перезалит\w*"""), true),
        VersionMarker("clean", markerPattern("""\bclean\b|clean version|edited version|пensored"""), false),
        VersionMarker("explicit", markerPattern("""\bexplicit\b|explicit version|uncensored|uncut"""), false),
        VersionMarker("extended", markerPattern("""extended mix|extended version|12 inch|12"""), false),
        VersionMarker("radio edit", markerPattern("""radio edit|single edit|edit version"""), false),
        VersionMarker("remaster", markerPattern("""remaster|remastered|anniversary edition"""), false),
    )

    private fun versionMarkers(value: String): Set<String> {
        val normalized = normalizedForMatch(value)
        return alternateVersionMarkers
            .filter { marker -> marker.pattern.containsMatchIn(normalized) }
            .map { it.name }
            .toSet()
    }

    private fun hardVersionMarkers(names: Collection<String>): Set<String> {
        val hardNames = alternateVersionMarkers.filter { it.hardReject }.map { it.name }.toSet()
        return names.filterTo(mutableSetOf()) { it in hardNames }
    }

    private fun ytmusicTransferScore(
        candidate: SongItem,
        expected: TrackMatchMetadata,
        expectedDurationMs: Int,
    ): CandidateScore {
        var candidateTitle = candidate.title
        if (candidate.isVideoSong) {
            val split = candidateTitle.split("-", limit = 2)
            if (split.size == 2) candidateTitle = split[1].trim()
        }

        val titleScore = bigramSimilarity(candidateTitle, expected.title)
        val uploaderArtistScore = bigramSimilarity(
            candidate.artists.joinToString(" ") { it.name },
            expected.artist,
        )
        val titleArtistScore = if (candidate.isVideoSong) {
            bigramSimilarity(candidate.title.substringBefore("-"), expected.artist)
        } else {
            0.0
        }
        val artistScore = maxOf(uploaderArtistScore, titleArtistScore)

        val expectedDurationSec = expectedDurationMs / 1000.0
        val candidateDuration = candidate.duration
        val durationScore = if (expectedDurationSec > 0 && candidateDuration != null) {
            (1.0 - abs(candidateDuration - expectedDurationSec) * 2.0 / expectedDurationSec)
                .coerceIn(0.0, 1.0)
        } else {
            null
        }

        val albumScore = if (!candidate.isVideoSong && expected.album.isNotBlank()) {
            candidate.album?.name?.let { bigramSimilarity(it, expected.album) }
        } else {
            null
        }

        val expectedMarkers = versionMarkers(expected.title)
        val candidateMarkers = versionMarkers(
            listOf(
                candidate.title,
                candidate.album?.name.orEmpty(),
            ).joinToString(" "),
        )
        val unexpectedAlternates = (candidateMarkers - expectedMarkers).toList().sorted()
        val hardUnexpected = hardVersionMarkers(unexpectedAlternates).size
        val softUnexpected = unexpectedAlternates.size - hardUnexpected
        val alternatePenalty = hardUnexpected * 1.75 + softUnexpected * 0.65

        val parts = mutableListOf(titleScore, artistScore)
        durationScore?.let { parts += it * 5.0 }
        albumScore?.let { parts += it }
        val resultTypeBoost = if (candidate.isVideoSong) 1.0 else 2.0
        val baseScore = parts.average() * resultTypeBoost
        return CandidateScore(
            item = candidate,
            score = (baseScore - alternatePenalty).coerceAtLeast(0.0),
            titleScore = titleScore,
            artistScore = uploaderArtistScore,
            artistEvidenceScore = artistScore,
            durationScore = durationScore,
            albumScore = albumScore,
            alternatePenalty = alternatePenalty,
            unexpectedAlternates = unexpectedAlternates,
        )
    }

    private fun CandidateScore.isAcceptableMatch(wantExplicit: Boolean? = null): Boolean {
        val durationStrong = durationScore?.let { it >= 0.94 } ?: false
        val albumUseful = albumScore?.let { it >= 0.45 } ?: false
        val hasDuration = durationScore != null
        val baseMinScore = when {
            hasDuration && item.isVideoSong -> 1.55
            hasDuration -> 2.25
            item.isVideoSong -> 0.78
            else -> 1.35
        }
        // Penalise candidates whose explicit flag doesn't match the Spotify track.
        // This makes it harder for a clean version to pass the gate when an
        // explicit one is expected (and vice-versa).
        val explicitMismatch = wantExplicit != null && item.explicit != wantExplicit
        val minScore = if (explicitMismatch) baseMinScore + 0.6 else baseMinScore

        // Duration is deliberately weighted heavily, as in spotify_to_ytmusic,
        // but these gates prevent a same-length wrong-artist song from winning.
        // If Spotify did not ask for a remix/live/acoustic/etc version, do not
        // let that alternate upload win just because its title and length are close.
        // When duration is missing, allow strong title+artist/album matches
        // instead of rejecting every possible candidate.
        val hasUnexpectedHardAlternate = hardVersionMarkers(unexpectedAlternates).isNotEmpty()
        return score >= minScore &&
            !hasUnexpectedHardAlternate &&
            titleScore >= 0.45 &&
            (
                artistEvidenceScore >= 0.32 ||
                    (albumUseful && artistEvidenceScore >= 0.18) ||
                    (durationStrong && artistEvidenceScore >= 0.25)
                )
    }

    private suspend fun ensureSpotifyMatchMetadata(query: String): TrackMatchMetadata? {
        val currentMeta = metadataRegistry[query]
        val hasUsefulMeta = currentMeta?.let {
            it.title.isNotBlank() && it.artist.isNotBlank() && it.album.isNotBlank()
        } ?: false
        if (hasUsefulMeta && durationRegistry[query] != null && explicitRegistry.containsKey(query)) {
            return currentMeta
        }

        val spotifyId = trackIdRegistry[query] ?: spotifyTrackIdForPlayback(query) ?: return currentMeta
        val track = runCatching { com.metrolist.spotify.Spotify.track(spotifyId).getOrNull() }
            .onFailure { Log.w(TAG, "Spotify metadata repair failed for $spotifyId", it) }
            .getOrNull()
            ?: return currentMeta

        val repaired = TrackMatchMetadata(
            title = track.name,
            artist = track.artists.joinToString(", ") { it.name },
            album = track.album?.name ?: currentMeta?.album.orEmpty(),
        )
        metadataRegistry[query] = repaired
        trackIdRegistry[query] = spotifyId
        explicitRegistry[query] = track.explicit
        if (track.durationMs > 0) durationRegistry[query] = track.durationMs
        return repaired
    }

    private suspend fun resolveVideoCandidates(
        query: String,
        filter: YouTube.SearchFilter = YouTube.SearchFilter.FILTER_SONG,
        forPlayback: Boolean = false,
        forceRefresh: Boolean = false,
    ): List<String> {
        val cacheKey = "$query|${filter.value}"
        if (!forceRefresh) {
            videoCandidatesCache[cacheKey]?.let { return it }
            appCtx?.let { ctx ->
                com.music.spotui.data.preferences.getCachedVideoIds(ctx, cacheKey)?.let { cached ->
                    videoCandidatesCache[cacheKey] = cached
                    return cached
                }
            }
        } else {
            videoCandidatesCache.remove(cacheKey)
            appCtx?.let { com.music.spotui.data.preferences.clearCachedVideoId(it, cacheKey) }
        }
        if (forPlayback) {
            updateResolveStatus(true, "Searching YouTube videos...")
        }
        val wantExplicit = explicitRegistry[query]
        val baseSearchText = searchTextForPlayback(query)
        // Append "explicit" to the YouTube search query when Spotify says the
        // track is explicit — increases the chance YouTube returns the correct
        // version instead of the clean/radio edit.
        val searchText = if (wantExplicit == true) "$baseSearchText explicit" else baseSearchText
        // A raw YouTube videoId is 11 chars with no spaces — accept it directly.
        if (searchText.length == 11 && !searchText.contains(' ')) return listOf(searchText)
        val hits = YouTube.search(searchText, filter)
            .onFailure { Log.w(TAG, "resolveVideoId: YouTube search failed for: $searchText", it) }
            .getOrNull()
            ?.items
            ?.filterIsInstance<SongItem>()
            .orEmpty()
        if (hits.isEmpty()) {
            Log.w(TAG, "resolveVideoId: no YouTube song results for: $searchText")
            return emptyList()
        }
        // YouTube's top hit is NOT always the requested song (worst for
        // non-English titles). Score every hit against the query — the query is
        // "title artist1, artist2":
        //   +2 artist match, +1 title match, +2 duration match.
        // Duration is the key disambiguator for same-title/different-artist: a
        // wrong-artist song with the same name almost always has a different
        // length, so it never ties the real track once duration is in the score.
        fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
        val qn = norm(baseSearchText)
        val exactMeta = ensureSpotifyMatchMetadata(query)
        val wantSec = durationRegistry[query]?.let { it / 1000 }
        val scored = hits.map { h ->
            val cleanTitle = norm(h.title.substringBefore('(').substringBefore('['))
            var s = 0
            if (cleanTitle.isNotEmpty() && qn.contains(cleanTitle)) s += 1
            if (h.artists.any { a -> norm(a.name).let { it.isNotEmpty() && qn.contains(it) } }) s += 2
            // Within ~4s of the Spotify track length → very likely the same recording.
            val hDur = h.duration
            if (wantSec != null && hDur != null && kotlin.math.abs(hDur - wantSec) <= 4) s += 2
            h to s
        }
        val transferScored = if (exactMeta != null) {
            hits.map { ytmusicTransferScore(it, exactMeta, durationRegistry[query] ?: 0) }
        } else {
            emptyList()
        }
        // A hit is "verified" as the right recording when its artist matches the
        // query, OR (when we know the Spotify length) its duration is within ~4s.
        fun verified(h: SongItem): Boolean {
            val artistOk = h.artists.any { a -> norm(a.name).let { it.isNotEmpty() && qn.contains(it) } }
            val d = h.duration
            val durOk = wantSec != null && d != null && kotlin.math.abs(d - wantSec) <= 4
            return artistOk || durOk
        }
        fun explicitFirst(list: List<SongItem>) =
            if (wantExplicit != null) list.sortedByDescending { it.explicit == wantExplicit } else list
        val ordered = if (transferScored.isNotEmpty()) {
            val accepted = transferScored
                .filter { it.isAcceptableMatch(wantExplicit) }
                .let { pool ->
                    // Hard filter: when the Spotify track has an explicit flag and at
                    // least one acceptable candidate matches, discard the rest so a
                    // clean version can never win over an available explicit one.
                    if (wantExplicit != null) {
                        val matching = pool.filter { it.item.explicit == wantExplicit }
                        if (matching.isNotEmpty()) matching else pool
                    } else {
                        pool
                    }
                }
                .sortedWith(
                    compareByDescending<CandidateScore> { it.item.explicit == wantExplicit || wantExplicit == null }
                        .thenByDescending { it.score }
                )
                .map { it.item }
            if (accepted.isEmpty()) {
                val best = transferScored.maxByOrNull { it.score }
                Log.w(
                    TAG,
                    "resolveVideoId: no acceptable YouTube match for '$searchText' " +
                        "(best='${best?.item?.title}' score=${"%.2f".format(best?.score ?: 0.0)} " +
                        "title=${"%.2f".format(best?.titleScore ?: 0.0)} " +
                        "artist=${"%.2f".format(best?.artistEvidenceScore ?: 0.0)} " +
                        "duration=${"%.2f".format(best?.durationScore ?: 0.0)} " +
                        "album=${"%.2f".format(best?.albumScore ?: 0.0)} " +
                        "alt=${best?.unexpectedAlternates.orEmpty().joinToString("/")}), falling back to best-effort",
                )
                transferScored.sortedByDescending { it.score }.map { it.item }
            } else {
                accepted.distinctBy { it.id }
            }
        } else {
            // Legacy/plain queries without registered Spotify metadata: keep the
            // old best-effort ordering.
            val verifiedRanked = scored
                .filter { verified(it.first) }
                .sortedByDescending { it.second }
                .map { it.first }
            val restRanked = scored
                .filter { !verified(it.first) }
                .sortedByDescending { it.second }
                .map { it.first }
            (explicitFirst(verifiedRanked) + explicitFirst(restRanked)).distinctBy { it.id }
        }

        if (ordered.isEmpty()) return emptyList()
        val chosen = ordered.first()
        if (transferScored.isEmpty() && !verified(chosen)) {
            Log.w(TAG, "resolveVideoId: no verified match for: $searchText (want=${wantSec}s) — best-effort '${chosen.title}'")
        }
        val chosenScore = transferScored.firstOrNull { it.item.id == chosen.id }
        Log.d(
            TAG,
            "resolveVideoId: '$searchText' -> '${chosen.title}' by " +
                chosen.artists.joinToString { it.name } +
                " [explicit=${chosen.explicit} dur=${chosen.duration}s want=${wantSec}s id=${chosen.id}] " +
                (chosenScore?.let {
                    "score=${"%.2f".format(it.score)} title=${"%.2f".format(it.titleScore)} " +
                        "artist=${"%.2f".format(it.artistEvidenceScore)} duration=${"%.2f".format(it.durationScore ?: 0.0)} " +
                        "album=${"%.2f".format(it.albumScore ?: 0.0)} " +
                        "altPenalty=${"%.2f".format(it.alternatePenalty)} " +
                        "alt=${it.unexpectedAlternates.joinToString("/")}"
                } ?: "${ordered.count { verified(it) }} verified/${ordered.size}"),
        )
        val resolvedIds = ordered.map { it.id }.distinct()
        if (resolvedIds.isNotEmpty()) {
            videoCandidatesCache[cacheKey] = resolvedIds
            appCtx?.let { ctx ->
                com.music.spotui.data.preferences.setCachedVideoIds(ctx, cacheKey, resolvedIds)
            }
        }
        return resolvedIds
    }

    /**
     * Resolves a playable YouTube stream for [query], falling back through up to
     * 3 ranked video candidates when one has no obtainable stream.
     */
    private suspend fun resolveYtPlayback(
        query: String,
        audioQuality: com.metrolist.music.constants.AudioQuality,
        appContext: Context,
        forPlayback: Boolean = false,
        forceFreshCandidates: Boolean = false,
    ): YTPlayerUtils.PlaybackData? {
        lastYtFailureReason = null
        val connectivityManager =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        // Skip the HTTP HEAD validation probe when the video ID came from a
        // previous successful play (cached in memory or SharedPrefs). ExoPlayer
        // has its own retry logic, so the probe just adds ~0.5-1s of latency.
        val cacheKey = "$query|${com.metrolist.innertube.YouTube.SearchFilter.FILTER_SONG.value}"
        val candidatesCached = !forceFreshCandidates && videoCandidatesCache.containsKey(cacheKey)
        val tried = mutableSetOf<String>()
        suspend fun tryIds(ids: List<String>, skipValidation: Boolean = false): YTPlayerUtils.PlaybackData? {
            for ((index, videoId) in ids.withIndex()) {
                if (!tried.add(videoId)) continue
                if (forPlayback) {
                    updateResolveStatus(true, "Resolving YouTube stream ${index + 1}/${ids.size}...")
                }
                YTPlayerUtils.playerResponseForPlayback(
                    videoId = videoId,
                    audioQuality = audioQuality,
                    connectivityManager = connectivityManager,
                    skipValidation = skipValidation,
                ).fold(
                    onSuccess = { return it },
                    onFailure = { 
                        lastYtFailureReason = it.message ?: "Stream failed"
                        Log.w(TAG, "stream failed for $videoId (${it.message}) — trying next candidate for: ${searchTextForPlayback(query)}") 
                    },
                )
            }
            return null
        }
        val candidateLimit = if (MrbeanNetworkSettings.isEnabled(appContext)) {
            MrbeanNetworkSettings.candidateLimit(appContext)
        } else {
            6
        }
        tryIds(resolveVideoCandidates(query, forPlayback = forPlayback, forceRefresh = forceFreshCandidates).take(candidateLimit), skipValidation = candidatesCached)?.let { return it }
        if (!com.music.spotui.data.preferences.isVideoFallbackEnabled(appContext)) {
            Log.w(TAG, "song candidates exhausted and video fallback disabled for: ${searchTextForPlayback(query)}")
            return null
        }
        // Song results exhausted (e.g. every official upload is age-restricted and
        // we're not signed in to YouTube). Regular video uploads — lyric videos,
        // reuploads — usually aren't age-gated: last-resort pass over those.
        Log.w(TAG, "song candidates exhausted, trying video search for: ${searchTextForPlayback(query)}")
        tryIds(resolveVideoCandidates(query, YouTube.SearchFilter.FILTER_VIDEO, forPlayback = forPlayback, forceRefresh = forceFreshCandidates).take(candidateLimit), skipValidation = candidatesCached)?.let { return it }
        Log.e(TAG, "All YouTube candidates failed for: ${searchTextForPlayback(query)}")
        return null
    }

    private fun buildAudioAttributes() =
        androidx.media3.common.AudioAttributes.Builder()
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .build()

    /**
     * Build an ExoPlayer that reads through the shared media cache (so preloaded intro
     * bytes are reused) and carries its own [CrossfadeFilterAudioProcessor] so the DJ-style
     * low/high-pass sweep can be applied per track during a crossfade. The filter is
     * disabled (pass-through) outside a crossfade, so there's no overhead in normal playback.
     *
     * @param handleAudioFocus true for the active/session player, false for the transient
     *   secondary (incoming) player so it doesn't fight the primary for focus mid-fade.
     */
    private data class PlayerPipeline(
        val player: ExoPlayer,
        val dsp: com.music.spotui.audio.SpotuiDspAudioProcessor,
        val filter: com.music.spotui.audio.CrossfadeFilterAudioProcessor,
        val spatial: com.music.spotui.audio.SpatialWideningAudioProcessor,
    )

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun createPlayerWithFilter(
        context: Context,
        handleAudioFocus: Boolean,
    ): PlayerPipeline {
        val dsp = com.music.spotui.audio.SpotuiDspAudioProcessor()
        val filter = com.music.spotui.audio.CrossfadeFilterAudioProcessor()
        val spatial = com.music.spotui.audio.SpatialWideningAudioProcessor()
        applySoftwareDsp(context, dsp)
        applySoftwareSpatial(context, spatial)

        val focusMode = com.music.spotui.data.preferences.getAudioFocusMode(context)
        val effectiveFocus = handleAudioFocus && focusMode != com.music.spotui.data.preferences.AudioFocusMode.MIX_IGNORE_FOCUS
        val bufferProfile = com.music.spotui.data.preferences.getBufferProfile(context)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                bufferProfile.minMs,
                bufferProfile.maxMs,
                bufferProfile.playbackMs,
                bufferProfile.rebufferMs,
            )
            .build()
        val wantFloatPcm = com.music.spotui.data.preferences.isFloatAudioOutput(context)

        val renderers = object : androidx.media3.exoplayer.DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): androidx.media3.exoplayer.audio.AudioSink =
                androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput || wantFloatPcm)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessorChain(
                        androidx.media3.exoplayer.audio.DefaultAudioSink.DefaultAudioProcessorChain(
                            dsp,
                            filter,
                            spatial,
                        ),
                    ).build()
        }
        val p = ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(resolvingDataSourceFactory(context)),
            )
            .setRenderersFactory(renderers)
            .setAudioAttributes(buildAudioAttributes(), effectiveFocus)
            .setHandleAudioBecomingNoisy(handleAudioFocus)
            .build()
            .also {
                it.setWakeMode(androidx.media3.common.C.WAKE_MODE_NETWORK)
                val speed = com.music.spotui.data.preferences.getPlaybackSpeedPref(context)
                val pitch = com.music.spotui.data.preferences.getPlaybackPitchPref(context)
                it.playbackParameters = androidx.media3.common.PlaybackParameters(speed, pitch)
                it.skipSilenceEnabled = com.music.spotui.data.preferences.isSkipSilenceEnabled(context)
                applyAudioOffloadPolicy(context, it)
            }
        return PlayerPipeline(p, dsp, filter, spatial)
    }

    private fun applyAudioOffloadPolicy(context: Context, exo: ExoPlayer) {
        val offloadEnabled = com.music.spotui.data.preferences.isAudioOffloadEnabled(context)
        val mode = if (offloadEnabled) {
            androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
        } else {
            androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
        }
        val prefs = androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences.Builder()
            .setAudioOffloadMode(mode)
            .setIsGaplessSupportRequired(true)
            .setIsSpeedChangeSupportRequired(true)
            .build()
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setAudioOffloadPreferences(prefs)
            .build()
    }

    private fun applySoftwareDsp(
        context: Context,
        processor: com.music.spotui.audio.SpotuiDspAudioProcessor,
    ) {
        processor.eqEnabled = com.music.spotui.data.preferences.isEqEnabled(context)
        processor.setFromNormalizedSliders(
            subBass = com.music.spotui.data.preferences.getEqSubBass(context),
            bass = com.music.spotui.data.preferences.getEqBass(context),
            lowMid = com.music.spotui.data.preferences.getEqLowMid(context),
            vocal = com.music.spotui.data.preferences.getEqVocal(context),
            highMid = com.music.spotui.data.preferences.getEqHighMid(context),
            treble = com.music.spotui.data.preferences.getEqTreble(context),
            brilliance = com.music.spotui.data.preferences.getEqBrilliance(context),
        )
        processor.preampDb = com.music.spotui.data.preferences.getEqPreampDb(context)
        val bb = com.music.spotui.data.preferences.getBassBoostStrength(context)
        processor.bassBoostDb = (bb / 1000f) * 6.0f
        processor.limiterEnabled = com.music.spotui.data.preferences.isLimiterEnabled(context)
        processor.normalizerEnabled = com.music.spotui.data.preferences.isAudioNormalizerEnabled(context)
        processor.normalizerBoostDb = (com.music.spotui.data.preferences.getAudioNormalizerGainMb(context) / 100f).coerceIn(0f, 6f)
    }

    private fun applySoftwareSpatial(
        context: Context,
        processor: com.music.spotui.audio.SpatialWideningAudioProcessor,
    ) {
        processor.enabled = com.music.spotui.data.preferences.isEqSpatialEnabled(context)
        processor.width = (com.music.spotui.data.preferences.getSpatialStrength(context) / 1000f).coerceIn(0f, 1f)
        processor.profile = com.music.spotui.data.preferences.getSpatialProfile(context)
        processor.monoMix = com.music.spotui.ui.theme.ThemePreferences.monoAudio(context)
        processor.balance = com.music.spotui.ui.theme.ThemePreferences.audioBalance(context)
        processor.headphoneCrossfeed = com.music.spotui.data.preferences.isHeadphoneCrossfeedEnabled(context)
        processor.roomDelayMs = com.music.spotui.data.preferences.getSpatialRoomDelayMs(context)
    }

    private fun ensurePlayer(context: Context) {
        appCtx = context.applicationContext
        if (player == null) {
            val pipeline = createPlayerWithFilter(context, handleAudioFocus = true)
            val p = pipeline.player
            player = p
            currentDspProcessor = pipeline.dsp
            currentPlayerFilter = pipeline.filter
            currentSpatialProcessor = pipeline.spatial
            p.addListener(object : androidx.media3.common.Player.Listener {
                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    val ctx = appCtx ?: return
                    com.music.spotui.audio.AudioEffectController.bindSoftwareProcessors(ctx, currentDspProcessor, currentSpatialProcessor)
                    com.music.spotui.audio.AudioEffectController.attach(ctx, audioSessionId)
                }
            })
            com.music.spotui.audio.AudioEffectController.bindSoftwareProcessors(context, currentDspProcessor, currentSpatialProcessor)
            com.music.spotui.audio.AudioEffectController.attach(context, p.audioSessionId)
            onPlayerCreated?.invoke(p)
        }
    }

    /** The live ExoPlayer instance (may be null before first play). */
    val exoPlayer: ExoPlayer? get() = player

    /** Make sure the player exists (used by the media-session service). */
    fun ensureCreated(context: Context) = ensurePlayer(context.applicationContext)

    /** Re-applies equalizer, preamp, normalizer, and spatial-audio preferences immediately. */
    fun refreshAudioEffects(context: Context) {
        val appContext = context.applicationContext
        ensurePlayer(appContext)
        currentDspProcessor?.let { applySoftwareDsp(appContext, it) }
        secondaryDspProcessor?.let { applySoftwareDsp(appContext, it) }
        currentSpatialProcessor?.let { applySoftwareSpatial(appContext, it) }
        secondarySpatialProcessor?.let { applySoftwareSpatial(appContext, it) }
        com.music.spotui.audio.AudioEffectController.bindSoftwareProcessors(appContext, currentDspProcessor, currentSpatialProcessor)
        com.music.spotui.audio.AudioEffectController.attach(appContext, player?.audioSessionId ?: 0)
        com.music.spotui.audio.AudioEffectController.applyPreferences(appContext)
    }

    /** Applies updated AudioFocusMode without recreating the ExoPlayer instance. */
    fun refreshAudioFocusPolicy(context: Context) {
        val appContext = context.applicationContext
        val mode = com.music.spotui.data.preferences.getAudioFocusMode(appContext)
        val effectiveFocus = mode != com.music.spotui.data.preferences.AudioFocusMode.MIX_IGNORE_FOCUS
        scope.launch(Dispatchers.Main) {
            player?.setAudioAttributes(buildAudioAttributes(), effectiveFocus)
        }
    }

    /** Updates Media3 hardware AudioOffloadPreferences dynamically. */
    fun refreshAudioOffloadPolicy(context: Context) {
        val appContext = context.applicationContext
        scope.launch(Dispatchers.Main) {
            player?.let { applyAudioOffloadPolicy(appContext, it) }
        }
    }

    /** Notified right after the ExoPlayer is built so the session can attach to it. */
    @Volatile var onPlayerCreated: ((ExoPlayer) -> Unit)? = null

    fun isPlaying(): Boolean {
        if (webPlaybackActive()) return SpotifyWebPlayer.isPlaying
        return player?.isPlaying ?: false
    }

    fun webPlaybackActive(): Boolean {
        if (!webPlayerEnabled) return false
        val ctx = appCtx ?: return false
        // Spotify web playback needs: user hasn't opted out, the WebView actually has
        // Widevine, AND the user is logged into Spotify (sp_dc). Missing any of these
        // → fall back to the YouTube/FLAC engine so playback is never silent.
        return com.music.spotui.data.preferences.isWebPlaybackEnabled(ctx) &&
            SpotifyWebPlayer.canPlay &&
            com.music.spotui.data.api.SpotifySession.spDc(ctx).isNotBlank()
    }

    // ── Session restore (survive app restarts) ──
    // Set at launch from the persisted playback state; the first playSong for
    // this query seeks to the saved position, and play() with an empty player
    // re-resolves the track instead of doing nothing.
    @Volatile private var restoreQuery: String? = null
    @Volatile private var restorePositionMs: Long = 0L

    fun setRestorePoint(query: String, positionMs: Long) {
        if (query.isBlank()) return
        restoreQuery = query
        restorePositionMs = positionMs.coerceAtLeast(0L)
        loadedQuery = query
    }

    fun play() {
        appCtx?.let { com.music.spotui.ui.notification.PlaybackService.ensureStarted(it) }
        if (webPlaybackActive()) {
            SpotifyWebPlayer.resume()
            appCtx?.let { com.music.spotui.ui.notification.PlaybackService.notifyPlaybackStateChanged(it) }
            return
        }
        playWhenResolved = true
        if (currentRequest.isNotBlank() && currentRequest != loadedQuery) {
            // We are currently resolving a new track in the background.
            // Do not play the old track (loadedQuery).
            appCtx?.let { com.music.spotui.ui.notification.PlaybackService.notifyPlaybackStateChanged(it) }
            return
        }
        // Fresh launch: nothing loaded yet — resume the restored session track.
        if ((player?.mediaItemCount ?: 0) == 0) {
            val q = restoreQuery
            val ctx = appCtx
            if (q != null && ctx != null) { playSong(q, ctx); return }
        }
        player?.play()
        appCtx?.let { com.music.spotui.ui.notification.PlaybackService.notifyPlaybackStateChanged(it) }
    }

    fun pause() {
        cancelCrossfade()
        playWhenResolved = false
        if (webPlaybackActive()) {
            SpotifyWebPlayer.pause()
            appCtx?.let { com.music.spotui.ui.notification.PlaybackService.notifyPlaybackStateChanged(it) }
            return
        }
        player?.let {
            it.playWhenReady = false
            // Remember where we stopped so a relaunch can resume mid-track.
            appCtx?.let { ctx ->
                val pos = it.currentPosition
                if (pos > 0) com.music.spotui.data.preferences.saveLastPosition(ctx, pos)
            }
        }
        appCtx?.let { com.music.spotui.ui.notification.PlaybackService.notifyPlaybackStateChanged(it) }
    }

    fun stop() {
        cancelCrossfade()
        isResolvingTrack = false
        player?.stop()
        loadedQuery = null
        currentRequest = ""
        appCtx?.let { com.music.spotui.ui.notification.PlaybackService.notifyPlaybackStateChanged(it) }
    }

    fun togglePlay() {
        if (webPlaybackActive()) {
            if (SpotifyWebPlayer.isPlaying) pause() else play()
            return
        }
        player?.let {
            if (it.playWhenReady) pause() else play()
        }
    }

    fun next(context: Context) {
        val state = boundState
        if (state == null) {
            android.util.Log.w(TAG, "next: boundState is null")
            return
        }
        val q = state.queue.value
        if (q.isEmpty()) {
            android.util.Log.w(TAG, "next: queue is empty")
            return
        }
        val curId = state.songId.value
        val cur = q.indexOfFirst { it.id == curId }
            .let { if (it >= 0) it else state.songIndex.value }
            .coerceIn(0, q.size - 1)
        android.util.Log.d(TAG, "next: cur=$cur q.size=${q.size} repeat=${state.repeat.value} curId=$curId")
        val nextIdx: Int
        if (cur < q.size - 1) {
            nextIdx = cur + 1
        } else {
            if (state.repeat.value == RepeatMode.ALL) {
                nextIdx = 0
            } else {
                android.util.Log.d(TAG, "next: at end, repeat off - not advancing")
                return
            }
        }
        val song = q[nextIdx]
        android.util.Log.d(TAG, "next: advancing to idx=$nextIdx song=${song.title}")
        state.updateSongState(
            song.coverUri, song.title, song.singer, true,
            song.id, nextIdx, song.album
        )
        playSong(song.url, context, "song/${song.id}")
    }

    fun previous(context: Context) {
        val state = boundState
        if (state == null) {
            android.util.Log.w(TAG, "previous: boundState is null")
            return
        }
        val q = state.queue.value
        if (q.isEmpty()) {
            android.util.Log.w(TAG, "previous: queue is empty")
            return
        }
        val curId = state.songId.value
        val cur = q.indexOfFirst { it.id == curId }
            .let { if (it >= 0) it else state.songIndex.value }
            .coerceIn(0, q.size - 1)
        android.util.Log.d(TAG, "previous: cur=$cur q.size=${q.size} repeat=${state.repeat.value}")
        val nextIdx: Int
        if (cur > 0) {
            nextIdx = cur - 1
        } else {
            if (state.repeat.value == RepeatMode.ALL) {
                nextIdx = q.size - 1
            } else {
                android.util.Log.d(TAG, "previous: at start, repeat off - not going back")
                return
            }
        }
        val song = q[nextIdx]
        android.util.Log.d(TAG, "previous: going to idx=$nextIdx song=${song.title}")
        state.updateSongState(
            song.coverUri, song.title, song.singer, true,
            song.id, nextIdx, song.album
        )
        playSong(song.url, context, "song/${song.id}")
    }

    fun seekTo(position: Long) {
        cancelCrossfade()
        if (webPlaybackActive()) { SpotifyWebPlayer.seekTo(position); return }
        player?.seekTo(position.coerceAtLeast(0L))
    }

    fun seekRelative(deltaMs: Long) {
        val dur = getDuration()
        val target = (getCurrentPosition() + deltaMs).let { pos ->
            if (dur > 0) pos.coerceIn(0L, dur) else pos.coerceAtLeast(0L)
        }
        seekTo(target)
    }

    fun setAbLoopStart(posMs: Long = getCurrentPosition()) {
        abLoopStartMs = posMs.coerceAtLeast(0L)
        if (abLoopEndMs in 0..abLoopStartMs) abLoopEndMs = -1L
    }

    fun setAbLoopEnd(posMs: Long = getCurrentPosition()) {
        if (abLoopStartMs < 0L) abLoopStartMs = 0L
        if (posMs > abLoopStartMs + 500L) abLoopEndMs = posMs
    }

    fun clearAbLoop() {
        abLoopStartMs = -1L
        abLoopEndMs = -1L
    }

    fun queueSize(): Int = boundState?.queue?.value?.size ?: 0
    fun deduplicateQueue(): Int = boundState?.deduplicateQueue() ?: 0
    fun shuffleUpcomingQueue(): Int = boundState?.shuffleUpcoming() ?: 0
    fun trimPlayedQueueHistory(): Int = boundState?.trimPlayedHistory() ?: 0

    fun release() {
        positionWatchJob?.cancel()
        cancelCrossfade()
        isResolvingTrack = false
        playWhenResolved = true
        updateResolveStatus(false)
        com.music.spotui.audio.AudioEffectController.release()
        player?.release()
        player = null
        loadedQuery = null
        currentRequest = ""
        lastKnownSessionId = 0
        lastKnownPlaybackState = Player.STATE_IDLE
        lastKnownPlayWhenReady = false
        lastKnownIsPlaying = false
        lastKnownPositionMs = 0L
        lastKnownDurationMs = 0L
        lastKnownBufferedPct = 0
    }

    fun getDuration(): Long {
        if (webPlaybackActive()) return SpotifyWebPlayer.durationMs
        return player?.duration ?: 0L
    }

    fun getCurrentPosition(): Long {
        if (webPlaybackActive()) return SpotifyWebPlayer.positionMs
        return player?.currentPosition ?: 0L
    }

    fun isPrepared(): Boolean {
        val playerState = player?.playbackState
        return playerState != null && playerState != ExoPlayer.STATE_IDLE && playerState != ExoPlayer.STATE_ENDED
    }

    // ── Crossfade + DJ-style mixing ──
    // The end of the current track is blended into the start of the next over a user-set
    // window (Settings). A second, transient ExoPlayer plays the incoming track while the
    // primary fades out; volumes follow an equal-power (cos/sin) curve so total loudness
    // stays constant. In DJ mode, the outgoing track is low-passed (treble drops out) and the
    // incoming track high-passed (bass fills in) via per-player [CrossfadeFilterAudioProcessor]s,
    // swept on an S-curve — like a real DJ mixer. When the blend finishes the secondary player
    // is promoted to primary and the media session is re-bound to it via [onPlayerSwapped].
    private const val CF_LPF_START_HZ = 20000f
    private const val CF_LPF_END_HZ = 200f
    private const val CF_HPF_START_HZ = 2000f
    private const val CF_HPF_END_HZ = 20f
    private const val CF_SIGMOID_K = 6f
    // A short equal-power handoff hides resolver/decoder boundary gaps without sounding like
    // an audible crossfade. The next stream is resolved/prepared before this window opens.
    private const val GAPLESS_HANDOFF_MS = 320

    @Volatile private var appCtx: Context? = null
    @Volatile private var boundState: CurrentSongState? = null
    @Volatile private var currentDspProcessor: com.music.spotui.audio.SpotuiDspAudioProcessor? = null
    @Volatile private var currentPlayerFilter: com.music.spotui.audio.CrossfadeFilterAudioProcessor? = null
    @Volatile private var currentSpatialProcessor: com.music.spotui.audio.SpatialWideningAudioProcessor? = null
    @Volatile private var secondaryPlayer: ExoPlayer? = null
    @Volatile private var secondaryDspProcessor: com.music.spotui.audio.SpotuiDspAudioProcessor? = null
    @Volatile private var secondaryPlayerFilter: com.music.spotui.audio.CrossfadeFilterAudioProcessor? = null
    @Volatile private var secondarySpatialProcessor: com.music.spotui.audio.SpatialWideningAudioProcessor? = null
    @Volatile private var isCrossfading = false
    @Volatile private var crossfadeJob: kotlinx.coroutines.Job? = null
    @Volatile private var positionWatchJob: kotlinx.coroutines.Job? = null

    /** Notified (on the main thread) when the active ExoPlayer instance changes after a
     *  crossfade, so the media session can re-bind to the promoted player. */
    @Volatile var onPlayerSwapped: ((ExoPlayer) -> Unit)? = null

    /** Give the player access to the shared queue/now-playing state so it can advance the
     *  app's notion of "current track" itself when a crossfade fires. Called once at startup. */
    fun bindState(state: CurrentSongState) { boundState = state }

    fun isCrossfadeActive(): Boolean = isCrossfading

    private fun sigmoid(t: Float): Float = 1.0f / (1.0f + exp(-CF_SIGMOID_K * (t - 0.5f)))

    private fun expInterpolate(start: Float, end: Float, t: Float): Float {
        if (start <= 0f || end <= 0f) return end
        return exp(ln(start) + (ln(end) - ln(start)) * t).toFloat()
    }

    /** Cancel an in-flight crossfade and tear down the secondary player, restoring the
     *  primary to full volume with its filter disabled. Safe to call when not crossfading. */
    private fun cancelCrossfade() {
        if (!isCrossfading && secondaryPlayer == null) return
        crossfadeJob?.cancel()
        crossfadeJob = null
        currentPlayerFilter?.enabled = false
        secondaryPlayerFilter?.enabled = false
        runCatching { secondaryPlayer?.release() }
        secondaryDspProcessor = null
        secondaryPlayerFilter = null
        secondarySpatialProcessor = null
        secondaryPlayer = null
        player?.volume = 1f
        isCrossfading = false
    }

    /** (Re)start the loop that watches playback position, enforces A-B loop, zero-volume auto-pause,
     *  and fires a crossfade/gapless handoff as the current track approaches its end. */
    private var posSaveTick = 0
    @Volatile private var lastPreQueueTriggerQuery: String? = null

    private fun startPositionWatch() {
        positionWatchJob?.cancel()
        positionWatchJob = scope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(250)
                val ctx = appCtx ?: continue
                // Persist position & update DevConsole telemetry every ~2s
                val p = player ?: continue
                val (playing, pos, dur, bufPos) = withContext(Dispatchers.Main) {
                    lastKnownSessionId = p.audioSessionId
                    lastKnownPlaybackState = p.playbackState
                    lastKnownPlayWhenReady = p.playWhenReady
                    lastKnownIsPlaying = p.isPlaying
                    lastKnownBufferedPct = p.bufferedPercentage
                    if (p.isPlaying) {
                        val cp = p.currentPosition.coerceAtLeast(0L)
                        val d = p.duration.coerceAtLeast(0L)
                        lastKnownPositionMs = cp
                        lastKnownDurationMs = d
                        listOf(true, cp, d, p.bufferedPosition)
                    } else {
                        listOf(false, -1L, -1L, -1L)
                    }
                }.let { list ->
                    QuadState(list[0] as Boolean, list[1] as Long, list[2] as Long, list[3] as Long)
                }

                if (++posSaveTick % 8 == 0 && !webPlaybackActive() && playing && pos > 0) {
                    com.music.spotui.data.preferences.saveLastPosition(ctx, pos)
                    com.music.spotui.util.DevConsoleManager.updateDiagnostics {
                        copy(currentPositionMs = pos, bufferPositionMs = bufPos.coerceAtLeast(0L))
                    }
                }

                // Pause on Zero Volume
                if (playing && com.music.spotui.data.preferences.isPauseOnZeroVolume(ctx)) {
                    val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    if (am != null && am.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
                        withContext(Dispatchers.Main) { pause() }
                        continue
                    }
                }

                // A-B Loop enforcement
                if (playing && abLoopStartMs >= 0L && abLoopEndMs > abLoopStartMs + 500L && pos >= abLoopEndMs) {
                    withContext(Dispatchers.Main) { p.seekTo(abLoopStartMs) }
                    continue
                }

                if (isCrossfading) continue
                val crossfadeMs = com.music.spotui.data.preferences.getCrossfadeMs(ctx)
                val state = boundState ?: continue
                if (state.repeat.value == RepeatMode.ONE) continue // repeat-one loops the same track
                if (!playing || dur <= 0 || pos < 0) continue

                if (crossfadeMs > 0) {
                    if (pos >= dur - crossfadeMs) {
                        triggerCrossfade(ctx, crossfadeMs, gaplessOnly = false)
                    }
                } else if (dur > 25_000L && pos >= dur - 20_000L) {
                    // Ensure the upcoming track is resolved & queued in ExoPlayer ~20s prior
                    // to track end so background track transitions occur gaplessly without
                    // passing through STATE_ENDED while the screen is locked.
                    val needsPreQueue = withContext(Dispatchers.Main) {
                        p.currentMediaItemIndex >= p.mediaItemCount - 1
                    }
                    val curQuery = loadedQuery
                    if (needsPreQueue && curQuery != null && curQuery != lastPreQueueTriggerQuery) {
                        lastPreQueueTriggerQuery = curQuery
                        preResolveAndQueueNextTrack(ctx)
                    }
                }
            }
        }
    }

    private data class QuadState(
        val playing: Boolean,
        val pos: Long,
        val dur: Long,
        val bufPos: Long,
    )

    /** Begin blending the current track into the next queue item. */
    private fun triggerCrossfade(ctx: Context, configuredMs: Int, gaplessOnly: Boolean = false) {
        if (isCrossfading) return
        val state = boundState ?: return
        val q = state.queue.value
        if (q.isEmpty()) return
        val cur = q.indexOfFirst { it.id == state.songId.value }
        if (cur < 0 || cur >= q.size - 1) return // last track ends normally (PlaybackService handles autoplay)
        val nextSong = q[cur + 1]
        isCrossfading = true
        scope.launch {
            val (wakeLock, wifiLock) = acquireResolveWakeLocks(ctx)
            try {
                val nextUrl = resolveStreamUrl(nextSong.url, ctx, forPlayback = true) ?: run {
                    isCrossfading = false; return@launch
                }
                val sanitizedNext = com.music.spotui.util.StreamSanitizer.sanitizeStreamUrl(nextUrl)
                // Effective duration: never longer than the real time left on the outgoing track.
                val remaining = withContext(Dispatchers.Main) {
                    val p = player ?: return@withContext configuredMs.toLong()
                    val d = p.duration; val ps = p.currentPosition
                    if (d > 0 && ps >= 0) (d - ps) else configuredMs.toLong()
                }
                val minTransitionMs = if (gaplessOnly) 120L else 1_000L
                val effectiveMs = minOf(configuredMs.toLong(), remaining).coerceAtLeast(minTransitionMs).toInt()
                val djMode = !gaplessOnly && com.music.spotui.data.preferences.isCrossfadeDjMode(ctx)

                withContext(Dispatchers.Main) {
                    state.updateSongState(
                        nextSong.coverUri, nextSong.title, nextSong.singer, true,
                        nextSong.id, cur + 1, nextSong.album,
                    )
                    val pipeline = createPlayerWithFilter(ctx, handleAudioFocus = false)
                    val sp = pipeline.player
                    secondaryPlayer = sp
                    secondaryDspProcessor = pipeline.dsp
                    secondaryPlayerFilter = pipeline.filter
                    secondarySpatialProcessor = pipeline.spatial
                    val nextMime = streamMimeType(
                        sanitizedNext,
                        source = sourceCache[nextSong.url] ?: currentSource,
                        quality = qualityCache[nextSong.url] ?: currentQuality,
                    )
                    sp.setMediaItem(buildMediaItem(sanitizedNext, nextMime))
                    sp.prepare()
                    sp.volume = 0f
                    sp.playWhenReady = true
                }
                performCrossfade(effectiveMs, djMode)
            } catch (e: Exception) {
                Log.e(TAG, "crossfade failed", e)
                cancelCrossfade()
            } finally {
                releaseResolveWakeLocks(wakeLock, wifiLock)
            }
        }
    }

    private suspend fun performCrossfade(effectiveMs: Int, djMode: Boolean) {
        val steps = 50
        val delayPerStep = (effectiveMs / steps).coerceAtLeast(20)
        if (djMode) {
            currentPlayerFilter?.apply {
                filterType = com.music.spotui.audio.BiquadFilter.FilterType.LOW_PASS
                cutoffFrequencyHz = CF_LPF_START_HZ; enabled = true
            }
            secondaryPlayerFilter?.apply {
                filterType = com.music.spotui.audio.BiquadFilter.FilterType.HIGH_PASS
                cutoffFrequencyHz = CF_HPF_START_HZ; enabled = true
            }
        }
        crossfadeJob?.cancel()
        val job = scope.launch {
            try {
                for (step in 0..steps) {
                    if (!isActive) break
                    val progress = step.toFloat() / steps
                    val angle = (progress * PI / 2).toFloat()
                    withContext(Dispatchers.Main) {
                        player?.volume = cos(angle)
                        secondaryPlayer?.volume = sin(angle)
                        if (djMode) {
                            val fp = sigmoid(progress)
                            currentPlayerFilter?.cutoffFrequencyHz = expInterpolate(CF_LPF_START_HZ, CF_LPF_END_HZ, fp)
                            secondaryPlayerFilter?.cutoffFrequencyHz = expInterpolate(CF_HPF_START_HZ, CF_HPF_END_HZ, fp)
                        }
                    }
                    kotlinx.coroutines.delay(delayPerStep.toLong())
                }
                finalizeCrossfade()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            }
        }
        crossfadeJob = job
        job.join()
    }

    private suspend fun finalizeCrossfade() {
        withContext(Dispatchers.Main) {
            val incoming = secondaryPlayer ?: run { isCrossfading = false; return@withContext }
            val old = player
            // Promote the incoming (secondary) player + DSP chain to primary.
            currentPlayerFilter?.enabled = false
            secondaryPlayerFilter?.enabled = false
            player = incoming
            currentDspProcessor = secondaryDspProcessor
            currentPlayerFilter = secondaryPlayerFilter
            currentSpatialProcessor = secondarySpatialProcessor
            secondaryPlayer = null
            secondaryDspProcessor = null
            secondaryPlayerFilter = null
            secondarySpatialProcessor = null
            incoming.volume = 1f
            // The promoted player now owns audio focus / becoming-noisy handling & audio session effects.
            val ctx = appCtx ?: com.music.spotui.MyApplication.instanceOrNull
            val effectiveFocus = ctx == null || com.music.spotui.data.preferences.getAudioFocusMode(ctx) != com.music.spotui.data.preferences.AudioFocusMode.MIX_IGNORE_FOCUS
            incoming.setAudioAttributes(buildAudioAttributes(), effectiveFocus)
            incoming.setHandleAudioBecomingNoisy(true)
            incoming.addListener(object : Player.Listener {
                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    val activeCtx = appCtx ?: return
                    com.music.spotui.audio.AudioEffectController.bindSoftwareProcessors(activeCtx, currentDspProcessor, currentSpatialProcessor)
                    com.music.spotui.audio.AudioEffectController.attach(activeCtx, audioSessionId)
                }
            })
            if (ctx != null) {
                com.music.spotui.audio.AudioEffectController.bindSoftwareProcessors(ctx, currentDspProcessor, currentSpatialProcessor)
                com.music.spotui.audio.AudioEffectController.attach(ctx, incoming.audioSessionId)
                refreshAudioEffects(ctx)
            }
            runCatching { old?.stop(); old?.release() }
            isCrossfading = false
            // Re-bind the media session to the new player.
            onPlayerSwapped?.invoke(incoming)
        }
        // Watch the newly-promoted track for its own end and prefetch the following track.
        startPositionWatch()
        appCtx?.let { preResolveAndQueueNextTrack(it) }
    }

    // ── Sleep timer ──
    // Pauses playback after a delay. A new call replaces any pending timer;
    // passing 0 (or calling cancelSleepTimer) clears it.
    @Volatile private var sleepJob: kotlinx.coroutines.Job? = null
    @Volatile var sleepTimerEndAt: Long = 0L
        private set

    fun setSleepTimer(durationMillis: Long) {
        sleepJob?.cancel()
        if (durationMillis <= 0L) {
            sleepTimerEndAt = 0L
            return
        }
        sleepTimerEndAt = System.currentTimeMillis() + durationMillis
        sleepJob = scope.launch {
            kotlinx.coroutines.delay(durationMillis)
            withContext(Dispatchers.Main) { pause() }
            sleepTimerEndAt = 0L
        }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        sleepTimerEndAt = 0L
    }

    /**
     * Updates the playback speed and pitch via ExoPlayer's Sonic Audio Processor.
     */
    fun setPlaybackParameters(speed: Float, pitch: Float = 1.0f) {
        val boundedSpeed = speed.coerceIn(0.5f, 2.5f)
        val boundedPitch = pitch.coerceIn(0.5f, 2.0f)
        scope.launch(Dispatchers.Main) {
            player?.playbackParameters = androidx.media3.common.PlaybackParameters(boundedSpeed, boundedPitch)
        }
    }

    fun getPlaybackSpeed(): Float {
        return player?.playbackParameters?.speed ?: 1.0f
    }

    fun refreshPlaybackTuning(context: Context) {
        val speed = com.music.spotui.data.preferences.getPlaybackSpeedPref(context)
        val pitch = com.music.spotui.data.preferences.getPlaybackPitchPref(context)
        val skipSilence = com.music.spotui.data.preferences.isSkipSilenceEnabled(context)
        scope.launch(Dispatchers.Main) {
            player?.let { p ->
                p.playbackParameters = androidx.media3.common.PlaybackParameters(speed, pitch)
                p.skipSilenceEnabled = skipSilence
            }
            secondaryPlayer?.let { s ->
                s.playbackParameters = androidx.media3.common.PlaybackParameters(speed, pitch)
                s.skipSilenceEnabled = skipSilence
            }
        }
    }
}
