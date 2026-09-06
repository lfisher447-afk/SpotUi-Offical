package com.music.spotui.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import com.music.spotui.MainActivity
import com.music.spotui.R

/**
 * Version 1.5.1 - MediaNotificationAdapter
 * Custom Android Media notification builder showing cover art, playback controls, and dislike buttons.
 */
object MediaNotificationAdapter {
    const val CHANNEL_ID = "spotui_playback_channel"
    private const val CHANNEL_NAME = "SpotUI Playback"

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
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
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
}
