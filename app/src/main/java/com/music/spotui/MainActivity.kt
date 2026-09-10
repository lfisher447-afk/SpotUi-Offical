package com.music.spotui

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.music.spotui.di.SongPlayer
import com.music.spotui.util.AppDiagnostics
import com.music.spotui.ui.notification.PlaybackService
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.music.spotui.ui.theme.SpotuiTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?){
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        AppDiagnostics.info("MainActivity", "onCreate")
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

        // Ask for notification permission (Android 13+) so the media notification shows.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            runCatching { notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
                .onFailure { AppDiagnostics.warning("Permissions", "Notification permission request failed", it) }
        }

        // Connect a controller to bootstrap the MediaSessionService: this brings up
        // the system media notification and keeps playback alive in the background.
        runCatching {
            val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
            controllerFuture = MediaController.Builder(this, token).buildAsync()
        }.onFailure { AppDiagnostics.warning("MediaController", "Session controller bootstrap failed", it) }


        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightNavigationBars = false
        setContent {

            SpotuiTheme {
                // A surface container using the 'background' color from the theme
                    App()

                // New-release check (GitHub): prompts Upgrade / Dismiss / Don't show again.
                com.music.spotui.ui.components.UpdatePrompt()

                // Default link handler prompt: helps user enable open.spotify.com defaults.
                com.music.spotui.ui.components.DefaultAppPrompt()
            }
        }

        // Experimental Spotify web-player engine: attach its hidden WebView AFTER
        // setContent so the Compose content view doesn't replace/orphan it (an
        // orphaned WebView gets a 0×0 viewport and Spotify won't render/navigate).
        runCatching {
            com.music.spotui.di.SpotifyWebPlayer.attach(this)
        }.onFailure { AppDiagnostics.warning("SpotifyWebPlayer", "Web player attach failed", it) }

        // Perform background auto-backup if a backup directory is configured.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching { com.music.spotui.util.BackupHelper.performAutoBackup(applicationContext) }
                .onFailure { AppDiagnostics.warning("Backup", "Automatic backup failed", it) }
        }

        // Complete an official user-authorized YouTube OAuth callback before
        // processing generic Spotify deep links.
        handleYouTubeOAuthIntent(intent)

        // Handle initial deep link intent if launched via Spotify link
        runCatching { com.music.spotui.util.DeepLinkHandler.handleIntent(intent) }
            .onFailure { AppDiagnostics.warning("DeepLink", "Initial deep-link handling failed", it) }
    }

    private fun handleYouTubeOAuthIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "com.music.spotui" || uri.host != "oauth2redirect") return
        val denied = uri.getQueryParameter("error")
        val code = uri.getQueryParameter("code")
        if (!denied.isNullOrBlank()) {
            android.widget.Toast.makeText(this, "YouTube authorization was not granted: $denied", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        if (code.isNullOrBlank()) return
        lifecycleScope.launch {
            com.music.spotui.data.youtube.YouTubeMusicSync.completeAuthorization(applicationContext, code)
                .onSuccess { android.widget.Toast.makeText(this@MainActivity, "YouTube playlist sync connected.", android.widget.Toast.LENGTH_LONG).show() }
                .onFailure { error ->
                    AppDiagnostics.warning("YouTubeOAuth", "Authorization exchange failed", error)
                    android.widget.Toast.makeText(this@MainActivity, error.message ?: "YouTube authorization failed", android.widget.Toast.LENGTH_LONG).show()
                }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleYouTubeOAuthIntent(intent)
        runCatching { com.music.spotui.util.DeepLinkHandler.handleIntent(intent) }
            .onFailure { AppDiagnostics.warning("DeepLink", "New deep-link handling failed", it) }
    }

    override fun onDestroy() {
        AppDiagnostics.info("MainActivity", "onDestroy")
        // The MediaLibraryService owns the active engine and notification. Releasing
        // SongPlayer here also runs when Android destroys or recreates the UI activity,
        // which abruptly stops otherwise healthy background playback. Only the UI-side
        // controller connection is released here; the service releases its own resources.
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onDestroy()
    }
}



