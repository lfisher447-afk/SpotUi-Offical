package com.music.spotui.ui.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.music.spotui.R

/**
 * Custom [MediaNotification.Provider] wrapping [DefaultMediaNotificationProvider] so SpotUI keeps
 * its [Favorite | Transport | Shuffle] button layout while attaching a clean
 * [PlaybackService.ACTION_MEDIA_NOTIFICATION_DISMISSED] `deleteIntent` on every notification
 * and async artwork update.
 */
@OptIn(UnstableApi::class)
class SpotuiMediaNotificationProvider(
    context: Context,
) : MediaNotification.Provider {

    private val appContext = context.applicationContext

    private inner class DelegateProvider : DefaultMediaNotificationProvider(
        appContext,
        NotificationIdProvider { PlaybackService.NOTIFICATION_ID },
        PlaybackService.CHANNEL_ID,
        R.string.playback_notification_channel_name,
    ) {
        init {
            setSmallIcon(R.drawable.ic_spotui_notification)
        }

        public override fun getMediaButtons(
            session: MediaSession,
            playerCommands: Player.Commands,
            customLayout: ImmutableList<CommandButton>,
            showPauseButton: Boolean,
        ): ImmutableList<CommandButton> {
            val effectiveCommands = if (com.music.spotui.di.SongPlayer.queueSize() > 1) {
                playerCommands.buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            } else {
                playerCommands
            }
            return runCatching {
                val base = super.getMediaButtons(session, effectiveCommands, customLayout, showPauseButton)
                val favoriteBtn = base.find { it.sessionCommand?.customAction == PlaybackService.ACTION_FAVORITE }
                val shuffleBtn = base.find { it.sessionCommand?.customAction == PlaybackService.ACTION_SHUFFLE }
                val transport = base.filterNotNull().filter {
                    val act = it.sessionCommand?.customAction
                    act != PlaybackService.ACTION_FAVORITE &&
                        act != PlaybackService.ACTION_SHUFFLE &&
                        act != PlaybackService.ACTION_REPEAT &&
                        act != PlaybackService.ACTION_CLOSE &&
                        act != "ACTION_NONE"
                }
                val builder = ImmutableList.builder<CommandButton>()
                favoriteBtn?.let { builder.add(it) }
                builder.addAll(transport)
                shuffleBtn?.let { builder.add(it) }
                builder.build()
            }.getOrElse {
                runCatching { super.getMediaButtons(session, effectiveCommands, customLayout, showPauseButton) }
                    .getOrDefault(ImmutableList.of())
            }
        }
    }

    private val delegate = DelegateProvider()

    internal fun resolveMediaButtons(
        session: MediaSession,
        playerCommands: Player.Commands,
        customLayout: ImmutableList<CommandButton>,
        showPauseButton: Boolean,
    ): ImmutableList<CommandButton> =
        delegate.getMediaButtons(session, playerCommands, customLayout, showPauseButton)

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        val wrappedCallback = MediaNotification.Provider.Callback { updated ->
            onNotificationChangedCallback.onNotificationChanged(decorate(updated))
        }
        return decorate(
            delegate.createNotification(mediaSession, customLayout, actionFactory, wrappedCallback)
        )
    }

    override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo =
        delegate.notificationChannelInfo

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle,
    ): Boolean = delegate.handleCustomCommand(session, action, extras)

    internal fun decorate(mediaNotification: MediaNotification): MediaNotification {
        val dismissIntent = Intent(appContext, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_MEDIA_NOTIFICATION_DISMISSED
        }
        val dismissPendingIntent = PendingIntent.getService(
            appContext,
            DISMISS_REQUEST_CODE,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        mediaNotification.notification.deleteIntent = dismissPendingIntent
        return mediaNotification
    }

    companion object {
        private const val DISMISS_REQUEST_CODE = 1009
    }
}
