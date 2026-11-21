package com.music.spotui.engine

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.music.spotui.MainActivity
import com.music.spotui.R
import com.music.spotui.data.preferences.isBackgroundExitNotificationEnabled
import com.music.spotui.data.preferences.loadLastPlayback
import com.music.spotui.di.SongPlayer
import com.music.spotui.di.SpotifyWebPlayer
import com.music.spotui.ui.notification.ExitPlaybackControlReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Custom Android Media notification builder & exit mini-control notification manager.
 */
object MediaNotificationAdapter {
    const val CHANNEL_ID = "spotui_playback_channel"
    private const val CHANNEL_NAME = "SpotUI Playback"

    const val EXIT_MINI_NOTIFICATION_ID = 1002
    const val EXIT_CHANNEL_ID = "spotui_exit_mini_channel"
    private const val EXIT_CHANNEL_NAME = "SpotUI Quick Controls"

    private val artScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    var isExitMiniShowing: Boolean = false
        private set

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "SpotUI Audio Playback Controls"
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
            }
            if (manager.getNotificationChannel(EXIT_CHANNEL_ID) == null) {
                val exitChannel = NotificationChannel(
                    EXIT_CHANNEL_ID,
                    EXIT_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Quick Play/Pause controls when exiting SpotUI"
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                }
                manager.createNotificationChannel(exitChannel)
            }
        }
    }

    fun buildNotification(
        context: Context,
        title: String,
        artist: String,
        coverBitmap: Bitmap?,
        isPlaying: Boolean
    ): Notification {
        ensureChannel(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_spotui_notification)
            .setContentTitle(title.ifBlank { "SpotUI Music" })
            .setContentText(artist)
            .setContentIntent(contentPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)

        if (coverBitmap != null) {
            builder.setLargeIcon(coverBitmap)
        }

        return builder.build()
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun broadcastIntentFor(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context.applicationContext, ExitPlaybackControlReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context.applicationContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Posts a compact Play/Pause control notification when the user exits SpotUI so playback can
     * be started, paused, skipped, or dismissed without reopening MainActivity.
     */
    fun showOrUpdateExitMiniNotification(
        context: Context,
        isPlayingOverride: Boolean? = null,
    ) {
        val appContext = context.applicationContext
        if (!isBackgroundExitNotificationEnabled(appContext)) return
        if (!canPostNotifications(appContext)) return
        ensureChannel(appContext)

        val snap = SongPlayer.nowPlayingMetaSnapshot()
        val lastSaved = if (snap.title.isBlank()) loadLastPlayback(appContext)?.first else null
        val title = snap.title.ifBlank { lastSaved?.title.orEmpty() }.ifBlank { "SpotUI Ready" }
        val artist = snap.artist.ifBlank { lastSaved?.singer.orEmpty() }.ifBlank { "Tap Play to resume in background" }
        val coverUri = snap.coverUri.ifBlank { lastSaved?.coverUri.orEmpty() }
        val songId = lastSaved?.id?.toString()
        val isPlaying = isPlayingOverride ?: (SongPlayer.isPlaying() || SpotifyWebPlayer.isPlaying)

        val cachedArt = loadLocalCoverFast(appContext, songId, coverUri)
        postExitMiniInternal(appContext, title, artist, isPlaying, cachedArt)
        isExitMiniShowing = true

        if (cachedArt == null && coverUri.isNotBlank()) {
            artScope.launch {
                val remoteArt = runCatching {
                    com.bumptech.glide.Glide.with(appContext)
                        .asBitmap()
                        .load(coverUri)
                        .submit(192, 192)
                        .get()
                }.getOrNull()
                if (remoteArt != null && isExitMiniShowing) {
                    val livePlaying = SongPlayer.isPlaying() || SpotifyWebPlayer.isPlaying
                    postExitMiniInternal(appContext, title, artist, livePlaying, remoteArt)
                }
            }
        }
    }

    fun syncExitMiniIfShowing(context: Context, isPlayingOverride: Boolean? = null) {
        if (!isExitMiniShowing) return
        val appContext = context.applicationContext
        if (!isBackgroundExitNotificationEnabled(appContext)) {
            dismissExitMiniNotification(appContext)
            return
        }
        showOrUpdateExitMiniNotification(appContext, isPlayingOverride)
    }

    fun dismissExitMiniNotification(context: Context) {
        isExitMiniShowing = false
        val nm = context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(EXIT_MINI_NOTIFICATION_ID)
    }

    private fun postExitMiniInternal(
        appContext: Context,
        title: String,
        artist: String,
        isPlaying: Boolean,
        coverBitmap: Bitmap?,
    ) {
        val playPausePending = broadcastIntentFor(appContext, ExitPlaybackControlReceiver.ACTION_EXIT_PLAY_PAUSE, 2001)
        val prevPending = broadcastIntentFor(appContext, ExitPlaybackControlReceiver.ACTION_EXIT_PREV, 2002)
        val nextPending = broadcastIntentFor(appContext, ExitPlaybackControlReceiver.ACTION_EXIT_NEXT, 2003)
        val dismissPending = broadcastIntentFor(appContext, ExitPlaybackControlReceiver.ACTION_EXIT_DISMISS, 2004)

        val statusPrefix = if (isPlaying) "Playing" else "Paused"
        val playPauseLabel = if (isPlaying) "Pause" else "Play"
        val playPauseIcon = if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play

        val builder = NotificationCompat.Builder(appContext, EXIT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_spotui_notification)
            .setContentTitle(title)
            .setContentText("$statusPrefix · $artist")
            .setSubText("SpotUI Background Controls")
            .setContentIntent(playPausePending)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setAutoCancel(false)
            .setOngoing(false)
            .addAction(R.drawable.ic_widget_prev, "Prev", prevPending)
            .addAction(playPauseIcon, playPauseLabel, playPausePending)
            .addAction(R.drawable.ic_widget_next, "Next", nextPending)
            .setDeleteIntent(dismissPending)

        if (coverBitmap != null) {
            builder.setLargeIcon(coverBitmap)
        }

        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { nm.notify(EXIT_MINI_NOTIFICATION_ID, builder.build()) }
    }

    private fun loadLocalCoverFast(context: Context, songId: String?, coverUri: String): Bitmap? {
        val dir = File(context.filesDir, "downloads")
        if (!songId.isNullOrBlank()) {
            val f1 = File(dir, "${songId}_cover.jpg")
            if (f1.exists() && f1.length() > 0L) {
                return runCatching { BitmapFactory.decodeFile(f1.absolutePath) }.getOrNull()
            }
            val f2 = File(dir, "$songId.jpg")
            if (f2.exists() && f2.length() > 0L) {
                return runCatching { BitmapFactory.decodeFile(f2.absolutePath) }.getOrNull()
            }
        }
        if (coverUri.startsWith("/")) {
            return runCatching { BitmapFactory.decodeFile(coverUri) }.getOrNull()
        }
        return null
    }
}
