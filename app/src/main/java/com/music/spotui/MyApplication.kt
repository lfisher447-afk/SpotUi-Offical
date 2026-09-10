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
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
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

        // Install and verify the global crash handler and logger
        CrashHandler.install(this)
        Logger.initialize(this)
        AppDiagnostics.initialize(this)
        AppDiagnostics.info("MyApplication", "Application startup initialized")

        // Surface provider diagnostics to both logcat and bounded app-private storage.
        com.metrolist.spotify.Spotify.logger = { level, msg ->
            android.util.Log.d("SpotifyREST", "[$level] $msg")
            AppDiagnostics.info("SpotifyREST:$level", msg)
        }
        com.metrolist.spotify.SpotifyCanvas.setLogger { level, msg ->
            android.util.Log.d("SpotifyCanvas", "[$level] $msg")
            AppDiagnostics.info("SpotifyCanvas:$level", msg)
        }
        // Required by the ported YouTube streaming flow (cipher/PoToken WebViews).
        runCatching { CipherDeobfuscator.initialize(this) }
            .onFailure { AppDiagnostics.error("CipherDeobfuscator", "Initialization failed", it) }

        // Initialize persistent TrashBin / Blacklist Room database
        runCatching { com.music.spotui.engine.TrashBinManager.initialize(this) }
            .onFailure { AppDiagnostics.warning("TrashBin", "Initialization failed", it) }

        // Locale + visitorData must be set or the player can't mint a PoToken,
        // and googlevideo rejects the stream URL with HTTP 403 (tracks stuck at 0:00).
        val locale = Locale.getDefault()
        YouTube.locale = YouTubeLocale(
            gl = locale.country.takeIf { it.isNotBlank() } ?: "US",
            hl = locale.language.takeIf { it.isNotBlank() } ?: "en",
        )
        appScope.launch {
            runCatching {
                YouTube.visitorData = YouTube.visitorData().getOrNull() ?: YouTube.visitorData
            }.onFailure { AppDiagnostics.warning("YouTube", "Visitor data warm-up failed", it) }
        }
        // YouTube playback runs anonymously; age-gated official audio falls back
        // to matching normal YouTube uploads instead of requiring sign-in.
        YouTube.cookie = null

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
        // Pre-warm the YouTube playback pipeline so the first tap doesn't pay for
        // cold-starting the player.js parser and BotGuard WebView (~2-4s combined).
        appScope.launch {
            runCatching { com.metrolist.innertube.NewPipeExtractor.init() }
                .onFailure { AppDiagnostics.warning("NewPipe", "Playback pipeline warm-up failed", it) }
        }
    }
}
