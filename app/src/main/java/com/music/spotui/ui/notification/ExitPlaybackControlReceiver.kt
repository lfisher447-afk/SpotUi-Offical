package com.music.spotui.ui.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.music.spotui.di.SongPlayer
import com.music.spotui.engine.MediaNotificationAdapter
import com.music.spotui.util.AppDiagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles Play/Pause/Prev/Next/Dismiss clicks from the background/exit mini-notification
 * strictly via BroadcastReceiver (`PendingIntent.getBroadcast`) so playback is controlled
 * without launching MainActivity.
 */
class ExitPlaybackControlReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val appContext = context.applicationContext
        AppDiagnostics.info("ExitNotifReceiver", "Received action=$action")

        when (action) {
            ACTION_EXIT_DISMISS -> {
                MediaNotificationAdapter.dismissExitMiniNotification(appContext)
            }

            ACTION_EXIT_PLAY_PAUSE -> {
                val pendingResult = goAsync()
                val willBePlaying = !SongPlayer.isPlaying()
                Handler(Looper.getMainLooper()).post {
                    runCatching {
                        PlaybackService.triggerBackgroundCommand(
                            appContext,
                            PlaybackService.ACTION_BACKGROUND_PLAY_PAUSE,
                        )
                        MediaNotificationAdapter.showOrUpdateExitMiniNotification(
                            appContext,
                            isPlayingOverride = willBePlaying,
                        )
                    }.onFailure {
                        AppDiagnostics.warning("ExitNotifReceiver", "Play/Pause dispatch failed", it)
                    }
                    CoroutineScope(Dispatchers.Main).launch {
                        kotlinx.coroutines.delay(450)
                        MediaNotificationAdapter.syncExitMiniIfShowing(appContext)
                        pendingResult.finish()
                    }
                }
            }

            ACTION_EXIT_PREV -> {
                val pendingResult = goAsync()
                Handler(Looper.getMainLooper()).post {
                    runCatching {
                        PlaybackService.triggerBackgroundCommand(
                            appContext,
                            PlaybackService.ACTION_BACKGROUND_PREV,
                        )
                    }.onFailure {
                        AppDiagnostics.warning("ExitNotifReceiver", "Prev dispatch failed", it)
                    }
                    CoroutineScope(Dispatchers.Main).launch {
                        kotlinx.coroutines.delay(550)
                        MediaNotificationAdapter.syncExitMiniIfShowing(appContext)
                        pendingResult.finish()
                    }
                }
            }

            ACTION_EXIT_NEXT -> {
                val pendingResult = goAsync()
                Handler(Looper.getMainLooper()).post {
                    runCatching {
                        PlaybackService.triggerBackgroundCommand(
                            appContext,
                            PlaybackService.ACTION_BACKGROUND_NEXT,
                        )
                    }.onFailure {
                        AppDiagnostics.warning("ExitNotifReceiver", "Next dispatch failed", it)
                    }
                    CoroutineScope(Dispatchers.Main).launch {
                        kotlinx.coroutines.delay(550)
                        MediaNotificationAdapter.syncExitMiniIfShowing(appContext)
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_EXIT_PLAY_PAUSE = "com.music.spotui.exit.PLAY_PAUSE"
        const val ACTION_EXIT_PREV = "com.music.spotui.exit.PREV"
        const val ACTION_EXIT_NEXT = "com.music.spotui.exit.NEXT"
        const val ACTION_EXIT_DISMISS = "com.music.spotui.exit.DISMISS"
    }
}
