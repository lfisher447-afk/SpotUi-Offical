package com.music.spotui.widget

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.bumptech.glide.Glide
import com.music.spotui.R
import com.music.spotui.data.preferences.addLikedSongId
import com.music.spotui.data.preferences.isSongLiked
import com.music.spotui.data.preferences.removeLikedSongId
import com.music.spotui.playback.PlaybackSessionBus
import com.music.spotui.util.AppDiagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Compact 2×2 Now Playing widget: rounded album artwork + Play/Pause & Next transport.
 */
class SpotuiCompactWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SpotuiCompactWidget
}

/**
 * Expanded 4×2 Now Playing widget: album artwork, title & artist, Previous, Play/Pause, Next, Favorite.
 */
class SpotuiExpandedWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SpotuiExpandedWidget
}

object SpotuiCompactWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = NowPlayingWidgetStateStore.getState(context)
        val serviceRunning = PlaybackSessionBus.sessionAlive.value
        val buttons = WidgetControls.of(state, serviceRunning)
        val artwork = state?.artworkUrl?.takeIf { it.isNotBlank() }?.let { loadArtwork(context, it) }
        provideContent {
            CompactWidgetContent(context, state, buttons, artwork)
        }
    }
}

object SpotuiExpandedWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = NowPlayingWidgetStateStore.getState(context)
        val serviceRunning = PlaybackSessionBus.sessionAlive.value
        val buttons = WidgetControls.of(state, serviceRunning)
        val artwork = state?.artworkUrl?.takeIf { it.isNotBlank() }?.let { loadArtwork(context, it) }
        provideContent {
            ExpandedWidgetContent(context, state, buttons, artwork)
        }
    }
}

object GlanceWidgetUpdater {
    suspend fun refreshAll(context: Context) {
        val appContext = context.applicationContext
        val manager = GlanceAppWidgetManager(appContext)
        manager.getGlanceIds(SpotuiCompactWidget::class.java).forEach { id ->
            SpotuiCompactWidget.update(appContext, id)
        }
        manager.getGlanceIds(SpotuiExpandedWidget::class.java).forEach { id ->
            SpotuiExpandedWidget.update(appContext, id)
        }
    }

    fun compactComponent(context: Context): ComponentName =
        ComponentName(context, SpotuiCompactWidgetReceiver::class.java)

    fun expandedComponent(context: Context): ComponentName =
        ComponentName(context, SpotuiExpandedWidgetReceiver::class.java)
}

private val WidgetBg = Color(0xFF12141B)
private val WidgetSurfaceAlt = Color(0xFF1F2330)
private val WidgetAccent = Color(0xFF1DB954)
private val WidgetTextPrimary = Color(0xFFFFFFFF)
private val WidgetTextSecondary = Color(0xFFA8B0C2)

@Composable
private fun CompactWidgetContent(
    context: Context,
    state: NowPlayingWidgetState?,
    buttons: WidgetButtons?,
    artwork: Bitmap?,
) {
    val openIntent = if (state?.hasTrack == true) {
        WidgetMediaIntents.openPlayer(context)
    } else {
        WidgetMediaIntents.openApp(context) ?: WidgetMediaIntents.openPlayer(context)
    }
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBg)
            .cornerRadius(20.dp)
            .padding(10.dp)
            .clickable(actionStartActivity(openIntent)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WidgetArtwork(artwork = artwork, sizeDp = 76)
        Spacer(GlanceModifier.height(6.dp))
        Text(
            text = state?.title?.ifBlank { "Spotui" } ?: "Nothing playing",
            style = TextStyle(
                color = ColorProvider(WidgetTextPrimary),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
        if (buttons != null) {
            Spacer(GlanceModifier.height(6.dp))
            Row(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TransportButton(
                    context = context,
                    resId = if (buttons.showsPause) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                    description = if (buttons.showsPause) "Pause" else "Play",
                    transport = buttons.playPause,
                    sizeDp = 34,
                )
                Spacer(GlanceModifier.width(12.dp))
                TransportButton(
                    context = context,
                    resId = R.drawable.ic_widget_next,
                    description = "Next",
                    transport = buttons.next,
                    sizeDp = 30,
                )
            }
        }
    }
}

@Composable
private fun ExpandedWidgetContent(
    context: Context,
    state: NowPlayingWidgetState?,
    buttons: WidgetButtons?,
    artwork: Bitmap?,
) {
    val openIntent = if (state?.hasTrack == true) {
        WidgetMediaIntents.openPlayer(context)
    } else {
        WidgetMediaIntents.openApp(context) ?: WidgetMediaIntents.openPlayer(context)
    }
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBg)
            .cornerRadius(22.dp)
            .padding(12.dp)
            .clickable(actionStartActivity(openIntent)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WidgetArtwork(artwork = artwork, sizeDp = 88)
        Spacer(GlanceModifier.width(12.dp))
        Column(
            modifier = GlanceModifier
                .fillMaxHeight()
                .defaultWeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state?.title?.ifBlank { "Nothing playing" } ?: "Nothing playing",
                style = TextStyle(
                    color = ColorProvider(WidgetTextPrimary),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = state?.artist?.ifBlank { "Tap to open Spotui" } ?: "Tap to open Spotui",
                style = TextStyle(
                    color = ColorProvider(WidgetTextSecondary),
                    fontSize = 12.sp,
                ),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(10.dp))
            if (buttons != null) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val prevTransport = WidgetTransport(
                        keyCode = KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                        foreground = !PlaybackSessionBus.sessionAlive.value,
                    )
                    TransportButton(
                        context = context,
                        resId = R.drawable.ic_widget_prev,
                        description = "Previous",
                        transport = prevTransport,
                        sizeDp = 28,
                    )
                    Spacer(GlanceModifier.width(14.dp))
                    TransportButton(
                        context = context,
                        resId = if (buttons.showsPause) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                        description = if (buttons.showsPause) "Pause" else "Play",
                        transport = buttons.playPause,
                        sizeDp = 34,
                    )
                    Spacer(GlanceModifier.width(14.dp))
                    TransportButton(
                        context = context,
                        resId = R.drawable.ic_widget_next,
                        description = "Next",
                        transport = buttons.next,
                        sizeDp = 28,
                    )
                    Spacer(GlanceModifier.defaultWeight())
                    Image(
                        provider = ImageProvider(
                            if (state?.isLiked == true) R.drawable.ic_favorite_filled
                            else R.drawable.ic_favorite_border
                        ),
                        contentDescription = if (state?.isLiked == true) "Favorited" else "Favorite",
                        colorFilter = ColorFilter.tint(
                            ColorProvider(if (state?.isLiked == true) WidgetAccent else WidgetTextSecondary)
                        ),
                        modifier = GlanceModifier
                            .size(28.dp)
                            .clickable(actionRunCallback<WidgetLikeAction>()),
                    )
                }
            } else {
                Box(
                    modifier = GlanceModifier
                        .background(WidgetSurfaceAlt)
                        .cornerRadius(12.dp)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "Open Spotui",
                        style = TextStyle(
                            color = ColorProvider(WidgetTextPrimary),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetArtwork(artwork: Bitmap?, sizeDp: Int) {
    val mod = GlanceModifier
        .size(sizeDp.dp)
        .cornerRadius(14.dp)
    if (artwork != null) {
        Image(
            provider = ImageProvider(artwork),
            contentDescription = "Album artwork",
            modifier = mod,
        )
    } else {
        Image(
            provider = ImageProvider(R.drawable.placeholder),
            contentDescription = "Default artwork",
            modifier = mod,
        )
    }
}

@Composable
private fun TransportButton(
    context: Context,
    resId: Int,
    description: String,
    transport: WidgetTransport,
    sizeDp: Int,
) {
    val intent = WidgetMediaIntents.mediaButton(context, transport)
    val action = if (transport.foreground) {
        actionStartService(intent, isForegroundService = true)
    } else {
        actionSendBroadcast(intent)
    }
    Image(
        provider = ImageProvider(resId),
        contentDescription = description,
        modifier = GlanceModifier
            .size(sizeDp.dp)
            .clickable(action),
    )
}

class WidgetLikeAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val current = NowPlayingWidgetStateStore.getState(context) ?: return
        if (current.songId <= 0) return
        val key = current.songId.toString()
        val wasLiked = isSongLiked(context, key)
        if (wasLiked) {
            removeLikedSongId(context, key)
        } else {
            addLikedSongId(context, key)
        }
        val nextLiked = !wasLiked
        NowPlayingWidgetStateStore.saveState(context, current.copy(isLiked = nextLiked, updatedAtMs = System.currentTimeMillis()))
        WidgetActionRouter.onToggleLike?.invoke(current.songId, nextLiked)
        GlanceWidgetUpdater.refreshAll(context)
    }
}

private suspend fun loadArtwork(context: Context, url: String): Bitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            Glide.with(context.applicationContext)
                .asBitmap()
                .load(url)
                .submit(256, 256)
                .get()
        }.onFailure { err ->
            AppDiagnostics.warning("SpotuiWidget", "Widget artwork load failed", err)
        }.getOrNull()
    }
