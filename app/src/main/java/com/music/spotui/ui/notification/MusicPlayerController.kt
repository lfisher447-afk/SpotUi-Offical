package com.music.spotui.ui.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.music.spotui.R
import com.music.spotui.ui.screens.CustomSlider
import java.util.Locale

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun MusicPlayerController(
    songName: String,
    artistName: String,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier,
    coverUri: String = "",
    currentPositionMs: Long = 0L,
    durationMs: Long = 0L,
    onAddClick: () -> Unit = {},
    onSeek: (Long) -> Unit = { MediaSessionController.seekTo(it) },
) {
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary

    val safeDuration = durationMs.coerceAtLeast(1L)
    val clampedPos = currentPositionMs.coerceIn(0L, safeDuration)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(10.dp)
            .height(188.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(surfaceColor)
            .testTag("music_player_controller_card"),
    ) {
        if (coverUri.isNotBlank()) {
            GlideImage(
                model = coverUri,
                contentDescription = "$songName cover art",
                contentScale = ContentScale.Crop,
                failure = placeholder(R.drawable.placeholder),
                loading = placeholder(R.drawable.placeholder),
                modifier = Modifier
                    .size(188.dp)
                    .align(Alignment.BottomEnd)
                    .zIndex(0f),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                surfaceColor,
                                surfaceColor.copy(alpha = 0.92f),
                                surfaceColor.copy(alpha = 0.55f),
                                Color.Transparent,
                            )
                        )
                    )
                    .zIndex(1f),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .zIndex(2f),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "spotui",
                    color = primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = songName.ifBlank { "No track selected" },
                    color = onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = artistName.ifBlank { "Unknown artist" },
                    color = onSurfaceVariant,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            NotificationPlayer(
                songPlayingState = isPlaying,
                onPlayPauseClick = onPlayPauseClick,
                onPreviousClick = onPreviousClick,
                onNextClick = onNextClick,
                onAddClick = onAddClick,
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                CustomSlider(
                    value = clampedPos.toFloat(),
                    onValueChange = { newValue -> onSeek(newValue.toLong()) },
                    valueRange = 0f..safeDuration.toFloat(),
                    steps = 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("controller_seek_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = primary,
                        activeTrackColor = primary,
                        inactiveTrackColor = onSurface.copy(alpha = 0.2f),
                    ),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatControllerTime(currentPositionMs),
                        color = onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = formatControllerTime(durationMs),
                        color = onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
fun NotificationPlayer(
    songPlayingState: Boolean,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier,
    onAddClick: () -> Unit = {},
) {
    val iconTint = MaterialTheme.colorScheme.onSurface
    val primaryContainer = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.width(232.dp),
    ) {
        IconButton(
            onClick = onAddClick,
            modifier = Modifier
                .size(48.dp)
                .testTag("controller_add_button"),
        ) {
            Icon(
                modifier = Modifier.size(22.dp),
                painter = painterResource(id = R.drawable.ic_add),
                tint = iconTint,
                contentDescription = "Save track",
            )
        }

        IconButton(
            onClick = onPreviousClick,
            modifier = Modifier
                .size(48.dp)
                .testTag("controller_previous_button"),
        ) {
            Icon(
                modifier = Modifier.size(24.dp),
                painter = painterResource(id = R.drawable.ic_player_back),
                tint = iconTint,
                contentDescription = "Previous track",
            )
        }

        IconButton(
            onClick = onPlayPauseClick,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(primaryContainer)
                .testTag("controller_play_pause_button"),
        ) {
            Icon(
                modifier = Modifier.size(22.dp),
                painter = if (songPlayingState) {
                    painterResource(id = R.drawable.ic_playing)
                } else {
                    painterResource(id = R.drawable.play_svgrepo_com)
                },
                tint = onPrimary,
                contentDescription = if (songPlayingState) "Pause" else "Play",
            )
        }

        IconButton(
            onClick = onNextClick,
            modifier = Modifier
                .size(48.dp)
                .testTag("controller_next_button"),
        ) {
            Icon(
                modifier = Modifier.size(24.dp),
                painter = painterResource(id = R.drawable.ic_player_skip),
                tint = iconTint,
                contentDescription = "Next track",
            )
        }
    }
}

private fun formatControllerTime(ms: Long): String {
    val safeSec = (ms.coerceAtLeast(0L) / 1000L)
    val min = safeSec / 60L
    val sec = safeSec % 60L
    return String.format(Locale.US, "%d:%02d", min, sec)
}