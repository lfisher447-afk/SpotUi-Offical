package com.music.spotui

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YouTubeLocale
import com.metrolist.music.utils.cipher.CipherDeobfuscator
import com.music.spotui.data.api.Api
import com.music.spotui.util.AppDiagnostics
import com.music.spotui.util.CrashHandler
import com.music.spotui.util.Logger
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltAndroidApp
class MyApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        // For non-DI singletons (LyricsApi) that need a Context to refresh the token.
        @JvmStatic
        lateinit var instance: MyApplication
            private set

        val instanceOrNull: MyApplication?
            get() = runCatching { if (::instance.isInitialized) instance else null }.getOrNull()
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        instance = this
        // Install global UncaughtExceptionHandler as early as possible before any class loading or onCreate
        base?.let { CrashHandler.install(it) }
    }

    override val workManagerConfiguration: Configuration
        get() {
            return try {
                if (::workerFactory.isInitialized) {
                    Configuration.Builder()
                        .setWorkerFactory(workerFactory)
                        .build()
                } else {
                    AppDiagnostics.warning("WorkManager", "Hilt workerFactory not initialized, using default configuration")
                    Configuration.Builder().build()
                }
            } catch (e: Throwable) {
                AppDiagnostics.warning("WorkManager", "Failed to build WorkManager configuration", e)
                Configuration.Builder().build()
            }
        }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Install and verify the global crash handler, developer console, and disk logger
        CrashHandler.install(this)
        AppDiagnostics.initialize(this)
        Logger.initialize(this)
        AppDiagnostics.info("MyApplication", "Application startup initialized")

        // Surface provider diagnostics to both logcat, live Developer Console, and bounded app-private storage.
        com.metrolist.spotify.Spotify.logger = { level, msg ->
            android.util.Log.d("SpotifyREST", "[$level] $msg")
            when {
                level.contains("ERR", ignoreCase = true) -> AppDiagnostics.error("SpotifyREST:$level", msg)
                level.contains("WARN", ignoreCase = true) -> AppDiagnostics.warning("SpotifyREST:$level", msg)
                else -> AppDiagnostics.info("SpotifyREST:$level", msg)
            }
        }
        com.metrolist.spotify.SpotifyCanvas.setLogger { level, msg ->
            android.util.Log.d("SpotifyCanvas", "[$level] $msg")
            when {
                level.contains("ERR", ignoreCase = true) -> AppDiagnostics.error("SpotifyCanvas:$level", msg)
                level.contains("WARN", ignoreCase = true) -> AppDiagnostics.warning("SpotifyCanvas:$level", msg)
                else -> AppDiagnostics.info("SpotifyCanvas:$level", msg)
            }
        }
        // Install Spotify 401 auto-refresh + GQL persisted-query hash self-healing at cold start
        runCatching { com.music.spotui.ui.screens.SpotifyLogin.module.installSpotifyTokenAutoRefresh(this) }
            .onFailure { AppDiagnostics.warning("SpotifyAuth", "Auto-refresh install failed", it) }

        // Required by the ported YouTube streaming flow (cipher/PoToken WebViews).
        runCatching { CipherDeobfuscator.initialize(this) }
            .onFailure { AppDiagnostics.error("CipherDeobfuscator", "Initialization failed", it) }

        // Initialize persistent TrashBin / Blacklist Room database
        runCatching { com.music.spotui.engine.TrashBinManager.initialize(this) }
            .onFailure { AppDiagnostics.warning("TrashBin", "Initialization failed", it) }

        // Explicitly initialize WorkManager with Hilt worker factory safely
        runCatching {
            if (!androidx.work.WorkManager.isInitialized()) {
                androidx.work.WorkManager.initialize(this, workManagerConfiguration)
            }
        }.onFailure { AppDiagnostics.warning("WorkManager", "Initialization failed", it) }

        // Locale + visitorData must be set or the player can't mint a PoToken,
        // and googlevideo rejects the stream URL with HTTP 403 (tracks stuck at 0:00).
        val locale = Locale.getDefault()
        YouTube.locale = YouTubeLocale(
            gl = locale.country.takeIf { it.isNotBlank() } ?: "US",
            hl = locale.language.takeIf { it.isNotBlank() } ?: "en",
        )
        YouTube.timingHook = com.metrolist.innertube.network.InnerTubeTimingHook { endpoint, method, durationMs, statusCode, error ->
            if (error != null || (statusCode != null && statusCode >= 400) || durationMs >= 2_500L) {
                AppDiagnostics.warning(
                    "InnerTube",
                    "$method $endpoint status=${statusCode ?: -1} durationMs=$durationMs${if (error != null) " err=$error" else ""}",
                )
            }
        }
        // Hydrate lossless provider credentials & mirror config early on startup.
        runCatching {
            val savedArl = com.music.spotui.data.preferences.getDeezerArl(this).trim()
            val savedMirror = com.music.spotui.data.preferences.getCustomLosslessMirrorUrl(this).trim()
            com.metrolist.spotify.SpotiFlac.deezerArl = savedArl.ifBlank { null }
            com.metrolist.spotify.SpotiFlac.customMirrorBaseUrl = savedMirror.ifBlank { null }
            com.music.spotui.deezer.DeezerSession.setArl(savedArl)
        }.onFailure { AppDiagnostics.warning("LosslessConfig", "Startup hydration failed", it) }

        // Apply DoH, proxy pool, WebClient PoToken flag, and optional YouTube session cookie on IO.
        appScope.launch {
            runCatching {
                com.music.spotui.data.preferences.applyNetworkAndAuthSettings(applicationContext)
            }.onFailure { AppDiagnostics.warning("NetworkSettings", "Restore failed", it) }
            runCatching {
                YouTube.visitorData = YouTube.visitorData().getOrNull() ?: YouTube.visitorData
            }.onFailure { AppDiagnostics.warning("YouTube", "Visitor data warm-up failed", it) }
        }

        // Warm the Home feed cache so the first navigation to Home is instant.
        // No-op (gracefully) until a Spotify token is available.
        val api = Api(this)
        appScope.launch {
            runCatching { api.getHomeFeed().collect {} }
                .onFailure { AppDiagnostics.warning("HomeFeed", "Warm-up failed", it) }
        }
        appScope.launch {
            runCatching { api.getAlbums().collect {} }
                .onFailure { AppDiagnostics.warning("Albums", "Warm-up failed", it) }
        }
        appScope.launch {
            runCatching { api.getArtists().collect {} }
                .onFailure { AppDiagnostics.warning("Artists", "Warm-up failed", it) }
        }
        // Pre-warm the YouTube playback pipeline, SimpleCache, + embedded yt-dlp engine on IO
        appScope.launch {
            com.music.spotui.di.SongPlayer.warmupMediaCacheAsync(applicationContext)
            runCatching { com.metrolist.innertube.NewPipeExtractor.init() }
                .onFailure { AppDiagnostics.warning("NewPipe", "Playback pipeline warm-up failed", it) }
            com.music.spotui.engine.YtDlpEngine.warmupAsync(applicationContext)
        }
    }
}
