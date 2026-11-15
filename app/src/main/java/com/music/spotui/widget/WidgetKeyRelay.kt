package com.music.spotui.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import com.music.spotui.playback.PlaybackSessionBus
import com.music.spotui.util.AppDiagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives Pause / Next / Previous taps from home-screen widgets when the widget believes
 * [com.music.spotui.ui.notification.PlaybackService] is alive.
 *
 * If the session is live, forwards the media key safely to [com.music.spotui.ui.notification.PlaybackService].
 * If the process or service was killed in the meantime, marks the persisted widget state stopped and
 * redraws all Glance widgets so the user never sees a stuck Pause icon.
 */
class WidgetKeyRelay : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MEDIA_BUTTON) return
        val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return
        if (keyEvent.action != KeyEvent.ACTION_DOWN) return

        val appContext = context.applicationContext
        if (PlaybackSessionBus.sessionAlive.value) {
            val relayIntent = WidgetMediaIntents.relayToService(appContext, keyEvent.keyCode)
            runCatching {
                androidx.core.content.ContextCompat.startForegroundService(appContext, relayIntent)
            }.onFailure { err ->
                AppDiagnostics.warning("WidgetKeyRelay", "FGS relay failed for key ${keyEvent.keyCode}; attempting fallback", err)
                val dispatchedInProcess = com.music.spotui.ui.notification.ServiceSessionHolder.activeService() != null &&
                    mapKeyCodeToCommand(keyEvent.keyCode)?.also { cmd ->
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            com.music.spotui.playback.MediaCommandDispatcher.dispatchMediaCommand(cmd)
                        }
                    } != null
                if (!dispatchedInProcess) {
                    val startedRegular = runCatching { appContext.startService(relayIntent) != null }.getOrDefault(false)
                    if (!startedRegular) {
                        val pendingResult = goAsync()
                        relayScope.launch {
                            try {
                                NowPlayingWidgetStateStore.markStopped(appContext)
                                GlanceWidgetUpdater.refreshAll(appContext)
                            } finally {
                                pendingResult.finish()
                            }
                        }
                    }
                }
            }
            return
        }

        // Service died behind the launcher widget: flip Play/Pause glyph back to Play cleanly.
        val pendingResult = goAsync()
        relayScope.launch {
            try {
                NowPlayingWidgetStateStore.markStopped(appContext)
                GlanceWidgetUpdater.refreshAll(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }

    internal fun mapKeyCodeToCommand(keyCode: Int): com.music.spotui.playback.MediaCommand? = when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_NEXT -> com.music.spotui.playback.MediaCommand.Next
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> com.music.spotui.playback.MediaCommand.Previous
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> com.music.spotui.playback.MediaCommand.PlayPause
        KeyEvent.KEYCODE_MEDIA_PLAY -> com.music.spotui.playback.MediaCommand.Play
        KeyEvent.KEYCODE_MEDIA_PAUSE -> com.music.spotui.playback.MediaCommand.Pause
        KeyEvent.KEYCODE_MEDIA_STOP -> com.music.spotui.playback.MediaCommand.Stop
        else -> null
    }

    companion object {
        private val relayScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
