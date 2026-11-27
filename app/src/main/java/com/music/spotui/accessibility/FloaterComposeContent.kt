@file:Suppress("TooManyFunctions", "LongMethod", "MagicNumber")

package com.music.spotui.accessibility

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode as AnimRepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeMute
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CenterFocusWeak
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MenuOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SpatialAudio
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.Glide
import com.music.spotui.di.RepeatMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun FloaterExpressiveConsole(
    state: FloaterUiModel,
    translationBubble: TranslationBubbleState,
    translationHistory: List<TranslationHistoryEntry>,
    hasMicPermission: Boolean,
    isListening: Boolean,
    translateSourceLang: String,
    bubbleEnabled: Boolean,
    bubbleWidthDp: Float,
    bubbleTextSizeSp: Float,
    bubbleOpacity: Float,
    callbacks: FloaterCallbacks,
) {
    val cs = MaterialTheme.colorScheme
    val appContext = LocalContext.current.applicationContext
    val loadedArtwork by produceState<Bitmap?>(initialValue = null, key1 = state.coverUri) {
        value = if (state.coverUri.isBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    Glide.with(appContext)
                        .asBitmap()
                        .load(state.coverUri)
                        .submit(192, 192)
                        .get()
                }.getOrNull()
            }
        }
    }

    val auraColor = if (state.isGameActive) cs.tertiary else cs.primary

    Surface(
        shape = RoundedCornerShape(32.dp),
        color = cs.surfaceContainerLow,
        tonalElevation = 8.dp,
        shadowElevation = 18.dp,
        border = BorderStroke(1.dp, auraColor.copy(alpha = 0.28f)),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (state.isDrawerExpanded) Modifier.fillMaxHeight() else Modifier),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            auraColor.copy(alpha = 0.14f),
                            cs.secondary.copy(alpha = 0.05f),
                            Color.Transparent,
                        ),
                    ),
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (state.isDrawerExpanded) Modifier.fillMaxHeight() else Modifier)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // ── 1. Expressive Header + Drag Handle ───────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    callbacks.onInteraction()
                                    callbacks.onHeaderDrag(dragAmount.x, dragAmount.y)
                                },
                                onDragEnd = { callbacks.onHeaderDragEnd() },
                                onDragCancel = { callbacks.onHeaderDragEnd() },
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val badgeContainer by animateColorAsState(
                        targetValue = if (state.isGameActive) cs.tertiaryContainer else cs.primaryContainer,
                        label = "headerBadgeBg",
                    )
                    val badgeOnContainer by animateColorAsState(
                        targetValue = if (state.isGameActive) cs.onTertiaryContainer else cs.onPrimaryContainer,
                        label = "headerBadgeFg",
                    )
                    Surface(
                        color = badgeContainer,
                        shape = CircleShape,
                        modifier = Modifier.height(32.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = if (state.isGameActive) Icons.Rounded.SportsEsports else Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = badgeOnContainer,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                text = if (state.isGameActive) {
                                    state.activeGameLabel?.take(14) ?: "GAME MODE"
                                } else {
                                    "SPOTUI STUDIO"
                                },
                                color = badgeOnContainer,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                            )
                        }
                    }

                    // Drag Grip Pill + Dimensions Badge
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .width(36.dp)
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(cs.onSurfaceVariant.copy(alpha = 0.45f)),
                            )
                            Text(
                                text = "${state.widthDp}×${state.heightDp}",
                                color = cs.onSurfaceVariant.copy(alpha = 0.72f),
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    val likeScale by animateFloatAsState(
                        targetValue = if (state.isLiked) 1.15f else 1.0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
                        label = "likeScale",
                    )
                    IconButton(
                        onClick = { callbacks.onHaptic(true); callbacks.onToggleLike() },
                        modifier = Modifier
                            .size(48.dp)
                            .scale(likeScale)
                            .testTag("floater_like_button"),
                    ) {
                        Icon(
                            imageVector = if (state.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Favorite Track",
                            tint = if (state.isLiked) cs.tertiary else cs.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    IconButton(
                        onClick = { callbacks.onHaptic(false); callbacks.onOpenApp() },
                        modifier = Modifier.size(48.dp).testTag("floater_open_app_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = "Open SpotUI",
                            tint = cs.onSurface,
                            modifier = Modifier.size(19.dp),
                        )
                    }

                    if (state.floaterEq3dEnabled) {
                        Surface(
                            onClick = { callbacks.onHaptic(true); callbacks.onToggleExpand() },
                            color = if (state.isDrawerExpanded) cs.primaryContainer else cs.surfaceContainerHigh,
                            shape = CircleShape,
                            modifier = Modifier.size(48.dp).testTag("floater_expand_button"),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (state.isDrawerExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = if (state.isDrawerExpanded) "Collapse Studio" else "Expand Studio",
                                    tint = if (state.isDrawerExpanded) cs.onPrimaryContainer else cs.primary,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { callbacks.onHaptic(true); callbacks.onSnapOverlayToEdge() },
                        modifier = Modifier.size(48.dp).testTag("floater_snap_edge_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CenterFocusWeak,
                            contentDescription = "Snap Floater to Screen Edge",
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    IconButton(
                        onClick = { callbacks.onHaptic(false); callbacks.onClose() },
                        modifier = Modifier.size(48.dp).testTag("floater_close_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close Floater",
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                // ── 2. Expressive Now-Playing Hero Card + LinearWavyProgressIndicator ────
                Surface(
                    color = cs.surfaceContainerHigh,
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(
                                        Brush.linearGradient(listOf(cs.primaryContainer, cs.tertiaryContainer)),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                val bmp = loadedArtwork
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Album Cover",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Album,
                                        contentDescription = "Album Art",
                                        tint = cs.onPrimaryContainer,
                                        modifier = Modifier.size(28.dp),
                                    )
                                }
                                if (state.isResolving) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(cs.scrim.copy(alpha = 0.45f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = cs.onPrimaryContainer,
                                            strokeWidth = 2.5.dp,
                                        )
                                    }
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = state.title.ifBlank { "SpotUI Ready" },
                                    color = cs.onSurface,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("floater_track_title"),
                                )
                                Text(
                                    text = state.artist.ifBlank { "Open SpotUI or tap Play" },
                                    color = cs.onSurfaceVariant,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Surface(
                                        color = cs.secondaryContainer,
                                        shape = CircleShape,
                                    ) {
                                        Text(
                                            text = "${state.sourceLabel} · ${state.qualityLabel}",
                                            color = cs.onSecondaryContainer,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                    Surface(
                                        color = cs.surfaceContainerHighest,
                                        shape = CircleShape,
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Headphones,
                                                contentDescription = null,
                                                tint = cs.primary,
                                                modifier = Modifier.size(10.dp),
                                            )
                                            Text(
                                                text = state.audioRouteLabel,
                                                color = cs.onSurfaceVariant,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                    if (state.isPlaying) {
                                        ExpressivePlayingEqualizer(color = cs.primary)
                                    }
                                }
                            }
                        }

                        val progress = if (state.durationMs > 0L) {
                            (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            color = cs.primary,
                            trackColor = cs.surfaceContainerLowest,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("floater_wavy_progress"),
                        )
                    }
                }

                // ── 3. Morphing Expressive Transport Controls Row ────────────────────────
                val playCornerRadius by animateDpAsState(
                    targetValue = if (state.isPlaying) 16.dp else 28.dp,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    label = "playCorner",
                )
                val playWidth by animateDpAsState(
                    targetValue = if (state.isPlaying) 74.dp else 64.dp,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
                    label = "playWidth",
                )
                val playContainerColor by animateColorAsState(
                    targetValue = if (state.isPlaying) cs.primary else cs.primaryContainer,
                    label = "playColor",
                )
                val playContentColor by animateColorAsState(
                    targetValue = if (state.isPlaying) cs.onPrimary else cs.onPrimaryContainer,
                    label = "playContentColor",
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        onClick = { callbacks.onHaptic(true); callbacks.onToggleShuffle() },
                        color = if (state.isShuffled) cs.secondaryContainer else Color.Transparent,
                        shape = CircleShape,
                        modifier = Modifier.size(48.dp).testTag("floater_shuffle_button"),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (state.isShuffled) cs.onSecondaryContainer else cs.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    Surface(
                        onClick = { callbacks.onHaptic(false); callbacks.onPrevious() },
                        color = cs.surfaceContainerHigh,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.size(48.dp).testTag("floater_prev_button"),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = cs.onSurface)
                        }
                    }

                    // Expressive Morphing Squircle ↔ Pill Hero Button
                    Surface(
                        onClick = { callbacks.onHaptic(true); callbacks.onTogglePlayPause() },
                        color = playContainerColor,
                        shape = RoundedCornerShape(playCornerRadius),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .width(playWidth)
                            .height(52.dp)
                            .testTag("floater_play_pause_button"),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            AnimatedContent(
                                targetState = state.isPlaying,
                                transitionSpec = {
                                    (fadeIn() + scaleIn(initialScale = 0.8f)) togetherWith
                                        (fadeOut() + scaleOut(targetScale = 0.8f))
                                },
                                label = "playIcon",
                            ) { playing ->
                                Icon(
                                    imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (playing) "Pause" else "Play",
                                    tint = playContentColor,
                                    modifier = Modifier.size(30.dp),
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = { callbacks.onHaptic(false); callbacks.onNext() },
                        color = cs.surfaceContainerHigh,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.size(48.dp).testTag("floater_next_button"),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = cs.onSurface)
                        }
                    }

                    Surface(
                        onClick = { callbacks.onHaptic(true); callbacks.onCycleRepeat() },
                        color = if (state.repeatMode != RepeatMode.OFF) cs.secondaryContainer else Color.Transparent,
                        shape = CircleShape,
                        modifier = Modifier.size(48.dp).testTag("floater_repeat_button"),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (state.repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                contentDescription = "Loop Mode",
                                tint = if (state.repeatMode != RepeatMode.OFF) cs.onSecondaryContainer else cs.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                // ── 4. Dedicated In-App Volume Bar (OS Volume Never Mutated) ──────────────
                Surface(
                    color = cs.surfaceContainer,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        IconButton(
                            onClick = { callbacks.onHaptic(true); callbacks.onToggleMute() },
                            modifier = Modifier.size(48.dp).testTag("floater_vol_mute"),
                        ) {
                            Icon(
                                imageVector = if (state.inAppVolumePct == 0) Icons.AutoMirrored.Rounded.VolumeMute else Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = "Mute SpotUI Audio",
                                tint = if (state.inAppVolumePct == 0) cs.error else cs.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clip(CircleShape)
                                .clickable {
                                    callbacks.onHaptic(false)
                                    callbacks.onVolumeChange((state.inAppVolumePct - 5).coerceIn(0, 100))
                                }
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "-5",
                                color = cs.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Slider(
                            value = (state.inAppVolumePct / 100f).coerceIn(0f, 1f),
                            onValueChange = { frac ->
                                callbacks.onInteraction()
                                callbacks.onVolumeChange((frac * 100f).roundToInt())
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = cs.primary,
                                activeTrackColor = cs.primary,
                                inactiveTrackColor = cs.surfaceContainerHighest,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("floater_vol_slider"),
                        )
                        Box(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clip(CircleShape)
                                .clickable {
                                    callbacks.onHaptic(false)
                                    callbacks.onVolumeChange((state.inAppVolumePct + 5).coerceIn(0, 100))
                                }
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "+5",
                                color = cs.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Surface(
                            color = cs.primaryContainer,
                            shape = CircleShape,
                            modifier = Modifier.padding(end = 6.dp),
                        ) {
                            Text(
                                text = "${state.inAppVolumePct}%",
                                color = cs.onPrimaryContainer,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }

                // ── 5. Expandable Material 3 Expressive Studio + Game Drawer ─────────────
                AnimatedVisibility(
                    visible = state.isDrawerExpanded && state.floaterEq3dEnabled,
                    enter = expandVertically(spring(dampingRatio = Spring.DampingRatioLowBouncy)) + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                    modifier = Modifier.weight(1f, fill = true),
                ) {
                    Surface(
                        color = cs.surfaceContainer,
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            // Left Expressive Navigation Rail Sidebar
                            val railWidth by animateDpAsState(
                                targetValue = if (state.isSidebarCompact) 56.dp else 82.dp,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
                                label = "railWidth",
                            )
                            Column(
                                modifier = Modifier
                                    .width(railWidth)
                                    .fillMaxHeight()
                                    .background(cs.surfaceContainerLowest)
                                    .verticalScroll(rememberScrollState())
                                    .padding(vertical = 8.dp, horizontal = 5.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                IconButton(
                                    onClick = { callbacks.onHaptic(false); callbacks.onToggleSidebarCompact() },
                                    modifier = Modifier.size(48.dp).testTag("floater_sidebar_compact_toggle"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MenuOpen,
                                        contentDescription = "Toggle Sidebar Width",
                                        tint = cs.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }

                                SidebarSectionHeader("MEDIA", state.isSidebarCompact)
                                SidebarNavPill(
                                    tab = FloaterTab.PLAYER,
                                    icon = Icons.Rounded.GraphicEq,
                                    selected = state.activeTab == FloaterTab.PLAYER,
                                    compact = state.isSidebarCompact,
                                    onClick = { callbacks.onHaptic(true); callbacks.onSelectTab(FloaterTab.PLAYER) },
                                )
                                SidebarNavPill(
                                    tab = FloaterTab.QUEUE,
                                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                                    selected = state.activeTab == FloaterTab.QUEUE,
                                    compact = state.isSidebarCompact,
                                    onClick = { callbacks.onHaptic(true); callbacks.onSelectTab(FloaterTab.QUEUE) },
                                )

                                SidebarSectionHeader("SOUND", state.isSidebarCompact)
                                SidebarNavPill(
                                    tab = FloaterTab.EQ,
                                    icon = Icons.Rounded.Tune,
                                    selected = state.activeTab == FloaterTab.EQ,
                                    compact = state.isSidebarCompact,
                                    onClick = { callbacks.onHaptic(true); callbacks.onSelectTab(FloaterTab.EQ) },
                                )
                                SidebarNavPill(
                                    tab = FloaterTab.SPATIAL_3D,
                                    icon = Icons.Rounded.SpatialAudio,
                                    selected = state.activeTab == FloaterTab.SPATIAL_3D,
                                    compact = state.isSidebarCompact,
                                    onClick = { callbacks.onHaptic(true); callbacks.onSelectTab(FloaterTab.SPATIAL_3D) },
                                )

                                SidebarSectionHeader("PRO", state.isSidebarCompact)
                                SidebarNavPill(
                                    tab = FloaterTab.TOOLS,
                                    icon = Icons.Rounded.Build,
                                    selected = state.activeTab == FloaterTab.TOOLS,
                                    compact = state.isSidebarCompact,
                                    onClick = { callbacks.onHaptic(true); callbacks.onSelectTab(FloaterTab.TOOLS) },
                                )

                                // Shown when user is in a detected Game OR Always-Unlock Translator is active
                                val translateUnlocked = state.isGameActive || state.alwaysUnlockTranslator
                                if (translateUnlocked) {
                                    SidebarSectionHeader(if (state.isGameActive) "GAME" else "LIVE", state.isSidebarCompact)
                                    SidebarNavPill(
                                        tab = FloaterTab.TRANSLATE,
                                        icon = Icons.Rounded.Translate,
                                        selected = state.activeTab == FloaterTab.TRANSLATE,
                                        compact = state.isSidebarCompact,
                                        accentBadge = isListening,
                                        onClick = { callbacks.onHaptic(true); callbacks.onSelectTab(FloaterTab.TRANSLATE) },
                                    )
                                }
                            }

                            // Active Tab Body Content with Pinned Quick-Switch Tab Bar + Expressive Transition
                            val translateUnlocked = state.isGameActive || state.alwaysUnlockTranslator
                            val effectiveTab = if (state.activeTab == FloaterTab.TRANSLATE && !translateUnlocked) {
                                FloaterTab.PLAYER
                            } else {
                                state.activeTab
                            }
                            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(cs.surfaceContainerLow)
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                        .testTag("floater_pinned_tabs_bar"),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    FloaterTab.entries.forEach { tabItem ->
                                        if (tabItem == FloaterTab.TRANSLATE && !translateUnlocked) return@forEach
                                        val isSelected = effectiveTab == tabItem
                                        Surface(
                                            onClick = {
                                                callbacks.onHaptic(true)
                                                callbacks.onSelectTab(tabItem)
                                            },
                                            color = if (isSelected) cs.primaryContainer else cs.surfaceContainerHigh,
                                            shape = CircleShape,
                                            modifier = Modifier.testTag("floater_pinned_tab_${tabItem.key}"),
                                        ) {
                                            Text(
                                                text = tabItem.label,
                                                color = if (isSelected) cs.onPrimaryContainer else cs.onSurfaceVariant,
                                                fontSize = 10.sp,
                                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                            )
                                        }
                                    }
                                }

                                AnimatedContent(
                                    targetState = effectiveTab,
                                    transitionSpec = {
                                        (fadeIn(tween(180)) + scaleIn(initialScale = 0.96f)) togetherWith
                                            fadeOut(tween(120))
                                    },
                                    label = "floaterTabBody",
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                ) { tab ->
                                    when (tab) {
                                        FloaterTab.PLAYER -> FloaterPlayerPanel(state, callbacks)
                                        FloaterTab.QUEUE -> FloaterQueuePanel(state, callbacks)
                                        FloaterTab.EQ -> FloaterEqPanel(state, callbacks)
                                        FloaterTab.SPATIAL_3D -> FloaterSpatial3dPanel(state, callbacks)
                                        FloaterTab.TOOLS -> FloaterToolsPanel(state, callbacks)
                                        FloaterTab.TRANSLATE -> FloaterGameTranslatePanel(
                                            state = state,
                                            translationBubble = translationBubble,
                                            history = translationHistory,
                                            hasMicPermission = hasMicPermission,
                                            isListening = isListening,
                                            sourceLangCode = translateSourceLang,
                                            bubbleEnabled = bubbleEnabled,
                                            bubbleWidthDp = bubbleWidthDp,
                                            bubbleTextSizeSp = bubbleTextSizeSp,
                                            bubbleOpacity = bubbleOpacity,
                                            callbacks = callbacks,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom-Left Corner Drag Resize Handle
            CornerResizeGrip(
                isLeftCorner = true,
                tint = cs.primary.copy(alpha = 0.78f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .size(30.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { callbacks.onHaptic(false) },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                callbacks.onInteraction()
                                callbacks.onCornerResize(dragAmount.x, dragAmount.y, true)
                            },
                            onDragEnd = { callbacks.onCornerResizeEnd() },
                            onDragCancel = { callbacks.onCornerResizeEnd() },
                        )
                    }
                    .testTag("floater_resize_left_corner"),
            )

            // Bottom-Right Corner Drag Resize Handle
            CornerResizeGrip(
                isLeftCorner = false,
                tint = cs.primary.copy(alpha = 0.78f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(30.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { callbacks.onHaptic(false) },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                callbacks.onInteraction()
                                callbacks.onCornerResize(dragAmount.x, dragAmount.y, false)
                            },
                            onDragEnd = { callbacks.onCornerResizeEnd() },
                            onDragCancel = { callbacks.onCornerResizeEnd() },
                        )
                    }
                    .testTag("floater_resize_right_corner"),
            )
        }
    }
}

/**
 * Top-Left Game Live Translation Overlay Bubble.
 * Auto-detects teammate speech/chat (Spanish, Arabic, etc.), translates into English,
 * and allows inline font-size adjustment (A-/A+), drag repositioning, and corner width resize.
 */
@Composable
internal fun TranslationBubbleOverlayContent(
    bubble: TranslationBubbleState,
    widthDp: Float,
    textSizeSp: Float,
    opacity: Float,
    gameLabel: String?,
    onBubbleDrag: (Float, Float) -> Unit,
    onBubbleDragEnd: () -> Unit,
    onBubbleResizeWidthPx: (Float) -> Unit,
    onBubbleTextSizeDelta: (Float) -> Unit,
    onDismissBubble: () -> Unit,
    onOpenTranslatorTab: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val borderColor by animateColorAsState(
        targetValue = when {
            bubble.isTranslating -> cs.tertiary
            bubble.isListening -> cs.primary
            else -> cs.outlineVariant.copy(alpha = 0.7f)
        },
        label = "bubbleBorder",
    )

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = cs.surfaceContainerLowest.copy(alpha = opacity.coerceIn(0.55f, 0.98f)),
        border = BorderStroke(1.25.dp, borderColor.copy(alpha = 0.85f)),
        shadowElevation = 14.dp,
        modifier = Modifier
            .width(widthDp.coerceIn(160f, 420f).dp)
            .animateContentSize(spring(dampingRatio = Spring.DampingRatioLowBouncy))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onBubbleDrag(dragAmount.x, dragAmount.y)
                    },
                    onDragEnd = onBubbleDragEnd,
                    onDragCancel = onBubbleDragEnd,
                )
            }
            .testTag("game_translation_bubble"),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenTranslatorTab)
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Surface(
                        color = if (bubble.isListening) cs.primaryContainer else cs.secondaryContainer,
                        shape = CircleShape,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            if (bubble.isTranslating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(11.dp),
                                    color = cs.primary,
                                    strokeWidth = 1.75.dp,
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (bubble.isListening) cs.tertiary else cs.primary),
                                )
                            }
                            Text(
                                text = "${bubble.detectedLangCode.uppercase(Locale.US)} → ${bubble.targetLangCode.uppercase(Locale.US)}",
                                color = if (bubble.isListening) cs.onPrimaryContainer else cs.onSecondaryContainer,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }

                    Text(
                        text = gameLabel ?: "Live Translation",
                        color = cs.onSurfaceVariant,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    // Inline Quick Text-Size Step Pills
                    Surface(
                        onClick = { onBubbleTextSizeDelta(-1f) },
                        color = cs.surfaceContainerHigh,
                        shape = CircleShape,
                        modifier = Modifier.testTag("bubble_text_smaller"),
                    ) {
                        Text(
                            text = "A-",
                            color = cs.onSurface,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                    }
                    Surface(
                        onClick = { onBubbleTextSizeDelta(1f) },
                        color = cs.surfaceContainerHigh,
                        shape = CircleShape,
                        modifier = Modifier.testTag("bubble_text_larger"),
                    ) {
                        Text(
                            text = "A+",
                            color = cs.primary,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(cs.surfaceContainerHigh)
                            .clickable(onClick = onDismissBubble)
                            .testTag("bubble_hide_button"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Hide Translation Bubble",
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }

                if (bubble.originalText.isNotBlank()) {
                    Text(
                        text = bubble.originalText,
                        color = cs.onSurfaceVariant,
                        fontSize = (textSizeSp - 2.5f).coerceAtLeast(9.5f).sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Text(
                    text = bubble.translatedEnglish.ifBlank { bubble.statusHint },
                    color = cs.onSurface,
                    fontSize = textSizeSp.sp,
                    lineHeight = (textSizeSp + 4f).sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            CornerResizeGrip(
                isLeftCorner = false,
                tint = cs.primary.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(24.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onBubbleResizeWidthPx(dragAmount.x)
                        }
                    }
                    .testTag("bubble_resize_corner"),
            )
        }
    }
}

@Composable
private fun SidebarSectionHeader(title: String, compact: Boolean) {
    if (compact) {
        Spacer(Modifier.height(4.dp))
        return
    }
    Text(
        text = title,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
        fontSize = 8.5.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.9.sp,
        modifier = Modifier.padding(top = 4.dp, bottom = 1.dp),
    )
}

@Composable
private fun SidebarNavPill(
    tab: FloaterTab,
    icon: ImageVector,
    selected: Boolean,
    compact: Boolean,
    accentBadge: Boolean = false,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val bgColor by animateColorAsState(
        targetValue = if (selected) cs.primaryContainer else Color.Transparent,
        label = "navPillBg",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant,
        label = "navPillFg",
    )
    val cornerDp by animateDpAsState(
        targetValue = if (selected) 16.dp else 12.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "navPillCorner",
    )

    Surface(
        onClick = onClick,
        color = bgColor,
        shape = RoundedCornerShape(cornerDp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("floater_nav_${tab.key}"),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box {
                Icon(
                    imageVector = icon,
                    contentDescription = tab.label,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp),
                )
                if (accentBadge) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(cs.tertiary),
                    )
                }
            }
            if (!compact) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = tab.label,
                    color = contentColor,
                    fontSize = 9.5.sp,
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ExpressivePlayingEqualizer(color: Color) {
    val transition = rememberInfiniteTransition(label = "eqBars")
    val bars = listOf(
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), AnimRepeatMode.Reverse),
            label = "b1",
        ),
        transition.animateFloat(
            initialValue = 0.85f,
            targetValue = 0.30f,
            animationSpec = infiniteRepeatable(tween(340, easing = FastOutSlowInEasing), AnimRepeatMode.Reverse),
            label = "b2",
        ),
        transition.animateFloat(
            initialValue = 0.40f,
            targetValue = 0.95f,
            animationSpec = infiniteRepeatable(tween(500, easing = FastOutSlowInEasing), AnimRepeatMode.Reverse),
            label = "b3",
        ),
    )
    Canvas(modifier = Modifier.size(width = 14.dp, height = 12.dp)) {
        val barW = size.width / 5f
        bars.forEachIndexed { index, anim ->
            val x = index * (barW * 1.6f) + barW * 0.5f
            val h = size.height * anim.value
            drawLine(
                color = color,
                start = Offset(x, size.height),
                end = Offset(x, size.height - h),
                strokeWidth = barW,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun CornerResizeGrip(
    isLeftCorner: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.padding(6.dp)) {
        val w = size.width
        val h = size.height
        if (isLeftCorner) {
            drawLine(tint, Offset(2f, h * 0.35f), Offset(w * 0.65f, h - 2f), strokeWidth = 3f, cap = StrokeCap.Round)
            drawLine(tint, Offset(2f, h * 0.68f), Offset(w * 0.32f, h - 2f), strokeWidth = 3f, cap = StrokeCap.Round)
        } else {
            drawLine(tint, Offset(w - 2f, h * 0.35f), Offset(w * 0.35f, h - 2f), strokeWidth = 3f, cap = StrokeCap.Round)
            drawLine(tint, Offset(w - 2f, h * 0.68f), Offset(w * 0.68f, h - 2f), strokeWidth = 3f, cap = StrokeCap.Round)
        }
    }
}
