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
            runCatching {
                androidx.core.content.ContextCompat.startForegroundService(
                    appContext,
                    WidgetMediaIntents.relayToService(appContext, keyEvent.keyCode),
                )
            }.onFailure { err ->
                AppDiagnostics.warning("WidgetKeyRelay", "Failed to relay widget key ${keyEvent.keyCode}", err)
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

    companion object {
        private val relayScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
