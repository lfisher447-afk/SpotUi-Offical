package com.music.spotui.data.api

import android.content.Context
import com.harmber2.suadat.betterlyrics.BetterLyrics
import com.harmber2.suadat.betterlyrics.TTMLParser
import com.harmber2.suadat.paxsenix.PaxsenixLyrics
import com.harmber2.suadat.shazamkit.Shazam
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.kugou.KuGou
import com.metrolist.lastfm.LastFM
import com.metrolist.lrclib.LrcLib
import com.harmber2.suadat.simpmusic.SimpMusicLyrics
import com.music.spotui.BuildConfig
import com.music.spotui.data.entity.LyricLine
import com.music.spotui.data.entity.Lyrics
import com.music.spotui.data.preferences.ModuleLibsConfig
import com.music.spotui.data.preferences.SpotuiModuleId
import com.music.spotui.util.DevConsoleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Unified orchestration bridge for all 10 submodules in `modules/app/libs`:
 * spotify, innertube, lrclib, betterlyrics, kugou, simpmusic, paxsenix, lastfm, shazamkit, fastlane.
 */
object ModuleLibsBridge {
    private val bridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lastScrobbledKey = ConcurrentHashMap<String, Long>()
    private val lrcLinePattern = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")

    data class ModuleRuntimeStatus(
        val id: SpotuiModuleId,
        val enabled: Boolean,
        val summary: String,
    )

    fun syncRuntimeConfiguration(context: Context) {
        val appContext = context.applicationContext
        runCatching {
            KuGou.useTraditionalChinese = ModuleLibsConfig.isKugouTraditionalChinese(appContext)
        }
        runCatching {
            PaxsenixLyrics.setUserAgent("SpotUI", BuildConfig.VERSION_NAME)
        }
        runCatching {
            val apiKey = ModuleLibsConfig.getLastFmApiKey(appContext)
            val secret = ModuleLibsConfig.getLastFmSecret(appContext)
            if (apiKey.isNotBlank() && secret.isNotBlank()) {
                LastFM.initialize(apiKey, secret)
            }
            LastFM.sessionKey = ModuleLibsConfig.getLastFmSessionKey(appContext).takeIf { it.isNotBlank() }
        }
    }

    /**
     * Fetches lyrics across user-configured `modules/app/libs` providers in priority order:
     * BetterLyrics (TTML + word timing), Paxsenix (Apple Music / NetEase / Spotify / Musixmatch),
     * LrcLib, SimpMusic (by YouTube videoId), KuGou.
     */
    suspend fun fetchModuleLyrics(
        context: Context?,
        title: String,
        artist: String,
        album: String,
        durationSec: Int,
        youtubeVideoId: String? = null,
    ): Lyrics? {
        val appContext = context?.applicationContext
        if (appContext != null) {
            syncRuntimeConfiguration(appContext)
        }
        val order = if (appContext != null) {
            ModuleLibsConfig.getLyricsModuleIdsOrder(appContext)
        } else {
            SpotuiModuleId.LYRICS_PROVIDERS
        }

        val dur = if (durationSec > 0) durationSec else -1
        var resolvedVideoId = youtubeVideoId?.takeIf { it.isNotBlank() }

        for (provider in order) {
            if (appContext != null && !ModuleLibsConfig.isModuleEnabled(appContext, provider)) {
                continue
            }
            val parsed: Lyrics? = when (provider) {
                SpotuiModuleId.BETTERLYRICS -> runCatching {
                    BetterLyrics.getLyrics(
                        title = title,
                        artist = artist,
                        album = album.takeIf { it.isNotBlank() },
                        durationSeconds = dur,
                    ).getOrNull()?.let { raw -> parseRawOrTtmlLyrics(raw, provider.displayName) }
                }.getOrNull()

                SpotuiModuleId.PAXSENIX -> runCatching {
                    if (appContext == null || ModuleLibsConfig.isPaxsenixMultiEndpoint(appContext)) {
                        PaxsenixLyrics.getLyrics(title, artist, dur).getOrNull()
                            ?.let { raw -> parseRawOrTtmlLyrics(raw, provider.displayName) }
                    } else {
                        PaxsenixLyrics.getAppleMusicLyrics(title, artist, dur).getOrNull()
                            ?.let { raw -> parseRawOrTtmlLyrics(raw, provider.displayName) }
                    }
                }.getOrNull()

                SpotuiModuleId.LRCLIB -> runCatching {
                    LrcLib.getLyrics(
                        title = title,
                        artist = artist,
                        duration = dur,
                        album = album.takeIf { it.isNotBlank() },
                    ).getOrNull()?.let { raw -> parseRawOrTtmlLyrics(raw, provider.displayName) }
                }.getOrNull()

                SpotuiModuleId.SIMPMUSIC -> runCatching {
                    if (resolvedVideoId == null && (appContext == null || ModuleLibsConfig.isModuleEnabled(appContext, SpotuiModuleId.INNERTUBE))) {
                        resolvedVideoId = findYoutubeVideoId(title, artist)
                    }
                    val vid = resolvedVideoId ?: return@runCatching null
                    SimpMusicLyrics.getLyrics(vid, durationSec.coerceAtLeast(0)).getOrNull()
                        ?.let { raw -> parseRawOrTtmlLyrics(raw, provider.displayName) }
                }.getOrNull()

                SpotuiModuleId.KUGOU -> runCatching {
                    KuGou.getLyrics(
                        title = title,
                        artist = artist,
                        duration = dur,
                        album = album.takeIf { it.isNotBlank() },
                    ).getOrNull()?.let { raw -> parseRawOrTtmlLyrics(raw, provider.displayName) }
                }.getOrNull()

                else -> null
            }

            if (parsed != null && parsed.lines.isNotEmpty()) {
                DevConsoleManager.logInfo(
                    "ModuleLibsBridge",
                    "Lyrics resolved via ${provider.displayName} (${parsed.lines.size} lines, synced=${parsed.synced})",
                )
                return parsed
            }
        }
        return null
    }

    private suspend fun findYoutubeVideoId(title: String, artist: String): String? = runCatching {
        val searchResult = YouTube.search("$title $artist", YouTube.SearchFilter.FILTER_SONG).getOrNull()
        searchResult?.items?.filterIsInstance<SongItem>()?.firstOrNull()?.id
    }.getOrNull()

    fun parseRawOrTtmlLyrics(raw: String, sourceTag: String): Lyrics? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null

        // TTML XML format (BetterLyrics / Paxsenix Apple Music)
        if (trimmed.startsWith("<tt", ignoreCase = true) ||
            trimmed.startsWith("<?xml", ignoreCase = true) ||
            trimmed.contains("<div", ignoreCase = true) && trimmed.contains("<p ", ignoreCase = true)
        ) {
            val ttmlLines = runCatching { TTMLParser.parseTTML(trimmed) }.getOrNull().orEmpty()
            if (ttmlLines.isNotEmpty()) {
                val mapped = ttmlLines
                    .filter { it.text.isNotBlank() }
                    .map { line ->
                        LyricLine(
                            timeMs = (line.startTime * 1000.0).toLong().coerceAtLeast(0L),
                            text = line.text.trim(),
                        )
                    }
                    .sortedBy { it.timeMs }
                if (mapped.isNotEmpty()) {
                    return Lyrics(lines = mapped, synced = true)
                }
            }
        }

        // Standard LRC timestamped format
        val syncedLines = mutableListOf<LyricLine>()
        for (line in trimmed.lines()) {
            val matches = lrcLinePattern.findAll(line).toList()
            if (matches.isEmpty()) continue
            val content = line.substring(matches.last().range.last + 1).trim()
            if (content.isBlank()) continue
            for (match in matches) {
                val min = match.groupValues[1].toLongOrNull() ?: 0L
                val sec = match.groupValues[2].toLongOrNull() ?: 0L
                val frac = match.groupValues[3]
                val ms = when (frac.length) {
                    0 -> 0L
                    1 -> frac.toLong() * 100L
                    2 -> frac.toLong() * 10L
                    else -> frac.take(3).toLongOrNull() ?: 0L
                }
                syncedLines += LyricLine(min * 60_000L + sec * 1_000L + ms, content)
            }
        }
        if (syncedLines.isNotEmpty()) {
            return Lyrics(lines = syncedLines.sortedBy { it.timeMs }, synced = true)
        }

        // Plain fallback
        val plain = trimmed.lines().map { it.trim() }.filter { it.isNotBlank() }.map { LyricLine(0L, it) }
        return if (plain.isNotEmpty()) Lyrics(lines = plain, synced = false) else null
    }

    /**
     * Authenticates a Last.fm mobile session and persists key + username in [ModuleLibsConfig].
     */
    suspend fun authenticateLastFm(
        context: Context,
        apiKey: String,
        secret: String,
        username: String,
        password: String,
    ): Result<String> = runCatching {
        val cleanKey = apiKey.trim()
        val cleanSecret = secret.trim()
        require(cleanKey.isNotBlank() && cleanSecret.isNotBlank()) {
            "Enter Last.fm API Key and Shared Secret first."
        }
        LastFM.initialize(cleanKey, cleanSecret)
        val auth = LastFM.getMobileSession(username.trim(), password).getOrThrow()
        val sessionKey = auth.session.key
        val accountName = auth.session.name
        LastFM.sessionKey = sessionKey
        ModuleLibsConfig.setLastFmCredentials(context, cleanKey, cleanSecret, sessionKey, accountName)
        DevConsoleManager.logInfo("LastFM", "Authenticated Last.fm session for $accountName")
        accountName
    }.onFailure { err ->
        DevConsoleManager.logError("LastFM", "Last.fm mobile session sign-in failed", err)
    }

    /**
     * Sends Last.fm `updateNowPlaying` when enabled and configured, and schedules or emits
     * scrobble telemetry safely.
     */
    fun notifyTrackPlaying(
        context: Context,
        title: String,
        artist: String,
        album: String?,
        durationSec: Int?,
    ) {
        val appContext = context.applicationContext
        if (!ModuleLibsConfig.isModuleEnabled(appContext, SpotuiModuleId.LASTFM)) return
        if (!ModuleLibsConfig.isLastFmScrobbleEnabled(appContext)) return
        syncRuntimeConfiguration(appContext)
        if (!LastFM.isInitialized() || LastFM.sessionKey.isNullOrBlank()) return
        if (title.isBlank() || artist.isBlank()) return

        bridgeScope.launch {
            LastFM.updateNowPlaying(
                artist = artist.substringBefore(",").trim(),
                track = title.trim(),
                album = album?.takeIf { it.isNotBlank() },
                duration = durationSec?.takeIf { it > 0 },
            ).onFailure {
                DevConsoleManager.logWarning("LastFM", "updateNowPlaying failed: ${it.message}")
            }
        }
    }

    /**
     * Submits a Last.fm scrobble once the configured listen percentage threshold has passed
     * for the current playback session.
     */
    fun maybeScrobbleTrack(
        context: Context,
        trackKey: String,
        title: String,
        artist: String,
        album: String?,
        positionMs: Long,
        durationMs: Long,
    ) {
        val appContext = context.applicationContext
        if (!ModuleLibsConfig.isModuleEnabled(appContext, SpotuiModuleId.LASTFM)) return
        if (!ModuleLibsConfig.isLastFmScrobbleEnabled(appContext)) return
        if (durationMs < LastFM.DEFAULT_SCROBBLE_MIN_SONG_DURATION * 1000L) return
        val thresholdPct = ModuleLibsConfig.getLastFmScrobbleThresholdPercent(appContext).coerceIn(25, 95) / 100f
        if (positionMs < (durationMs * thresholdPct).toLong()) return

        val nowSec = System.currentTimeMillis() / 1000L
        val lastAt = lastScrobbledKey[trackKey] ?: 0L
        if (nowSec - lastAt < 120L) return

        syncRuntimeConfiguration(appContext)
        if (!LastFM.isInitialized() || LastFM.sessionKey.isNullOrBlank()) return
        lastScrobbledKey[trackKey] = nowSec

        bridgeScope.launch {
            LastFM.scrobble(
                artist = artist.substringBefore(",").trim(),
                track = title.trim(),
                timestamp = nowSec,
                album = album?.takeIf { it.isNotBlank() },
                duration = (durationMs / 1000L).toInt().takeIf { it > 0 },
            ).onSuccess {
                DevConsoleManager.logInfo("LastFM", "Scrobbled \"$title\" by $artist")
            }.onFailure { err ->
                DevConsoleManager.logWarning("LastFM", "Scrobble failed: ${err.message}")
            }
        }
    }

    fun setLastFmLoveStatus(context: Context, title: String, artist: String, loved: Boolean) {
        val appContext = context.applicationContext
        if (!ModuleLibsConfig.isModuleEnabled(appContext, SpotuiModuleId.LASTFM)) return
        syncRuntimeConfiguration(appContext)
        if (!LastFM.isInitialized() || LastFM.sessionKey.isNullOrBlank()) return
        bridgeScope.launch {
            LastFM.setLoveStatus(artist.substringBefore(",").trim(), title.trim(), loved)
                .onFailure { DevConsoleManager.logWarning("LastFM", "setLoveStatus failed: ${it.message}") }
        }
    }

    fun clearShazamCache() {
        runCatching {
            Shazam.clearCache()
            Shazam.cancelPendingRequests()
            DevConsoleManager.logInfo("ShazamKit", "Cleared Shazam recognition cache and pending queue")
        }
    }

    fun shazamQueueSummary(): String = runCatching {
        "Active ${Shazam.getActiveRequestsCount()} · Queued ${Shazam.getPendingRequestsCount()}"
    }.getOrDefault("Idle")

    fun collectStatusSummary(context: Context): List<ModuleRuntimeStatus> {
        val appContext = context.applicationContext
        return SpotuiModuleId.entries.map { mod ->
            val enabled = ModuleLibsConfig.isModuleEnabled(appContext, mod)
            val detail = when (mod) {
                SpotuiModuleId.SPOTIFY -> "Metadata, playlists & color-lyrics (${if (enabled) "Active" else "Bypassed"})"
                SpotuiModuleId.INNERTUBE -> "Audio stream & YTM search engine (${if (enabled) "Active" else "Bypassed"})"
                SpotuiModuleId.BETTERLYRICS -> "TTML word-by-word synced lyrics"
                SpotuiModuleId.PAXSENIX -> "Apple/NetEase/Musixmatch (${if (ModuleLibsConfig.isPaxsenixMultiEndpoint(appContext)) "Multi-endpoint" else "AMP only"})"
                SpotuiModuleId.LRCLIB -> "Community synced LRC database"
                SpotuiModuleId.SIMPMUSIC -> "Video-ID aligned LRC lyrics"
                SpotuiModuleId.KUGOU -> "KuGou LRC (${if (ModuleLibsConfig.isKugouTraditionalChinese(appContext)) "Traditional ZH" else "Standard"})"
                SpotuiModuleId.LASTFM -> {
                    val user = ModuleLibsConfig.getLastFmUsername(appContext)
                    if (user.isNotBlank() && LastFM.isInitialized()) "Signed in as $user" else "Scrobbling & love sync"
                }
                SpotuiModuleId.SHAZAMKIT -> "${shazamQueueSummary()} · Cache ${if (ModuleLibsConfig.isShazamCacheEnabled(appContext)) "On" else "Off"}"
                SpotuiModuleId.FASTLANE -> "Release channel: ${ModuleLibsConfig.getFastlaneChannel(appContext)}"
            }
            ModuleRuntimeStatus(id = mod, enabled = enabled, summary = detail)
        }
    }
}
