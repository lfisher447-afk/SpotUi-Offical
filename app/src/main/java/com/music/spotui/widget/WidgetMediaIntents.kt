package com.music.spotui.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import com.music.spotui.MainActivity
import com.music.spotui.ui.notification.PlaybackService

/**
 * Builds the intents behind home-screen widget taps.
 *
 * Play / Skip-Next-from-Stopped target [PlaybackService] directly as foreground service starts.
 * Pause / Skip-Next-while-Playing route through [WidgetKeyRelay] (so androidx.media3.session
 * MediaButtonReceiver never drops non-Play keys on Android 8+, and stale widgets redraw cleanly
 * if the service died).
 */
object WidgetMediaIntents {

    const val EXTRA_SKIP_FIRST = "com.music.spotui.extra.WIDGET_SKIP_FIRST"
    const val EXTRA_FROM_WIDGET_RELAY = "com.music.spotui.extra.FROM_WIDGET_RELAY"

    fun mediaButton(context: Context, transport: WidgetTransport): Intent {
        val targetComponent = if (transport.foreground) {
            ComponentName(context, PlaybackService::class.java)
        } else {
            ComponentName(context, WidgetKeyRelay::class.java)
        }
        return Intent(Intent.ACTION_MEDIA_BUTTON)
            .setComponent(targetComponent)
            .setData(Uri.parse("spotui://widget/media/${transport.keyCode}/${transport.skipFirst}/${transport.foreground}"))
            .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, transport.keyCode))
            .apply {
                if (transport.skipFirst) putExtra(EXTRA_SKIP_FIRST, true)
            }
    }

    fun relayToService(context: Context, keyCode: Int): Intent =
        Intent(Intent.ACTION_MEDIA_BUTTON)
            .setComponent(ComponentName(context, PlaybackService::class.java))
            .setData(Uri.parse("spotui://widget/relay/$keyCode"))
            .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            .putExtra(EXTRA_FROM_WIDGET_RELAY, true)

    fun isSkipFirst(intent: Intent?): Boolean =
        intent?.action == Intent.ACTION_MEDIA_BUTTON && intent.getBooleanExtra(EXTRA_SKIP_FIRST, false)

    fun openPlayer(context: Context): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_player", true)
        }

    fun openApp(context: Context): Intent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
}
