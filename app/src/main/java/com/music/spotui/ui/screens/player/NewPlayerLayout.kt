package com.music.spotui.ui.screens.player

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.music.spotui.R
import com.music.spotui.audio.UiSoundEngine
import com.music.spotui.data.api.ModuleLibsBridge
import com.music.spotui.data.api.SpotifySync
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.data.preferences.LocalPlaylistPref
import com.music.spotui.data.preferences.addLikedSongId
import com.music.spotui.data.preferences.isEqSpatialEnabled
import com.music.spotui.data.preferences.isSongLiked
import com.music.spotui.data.preferences.removeLikedSongId
import com.music.spotui.di.RepeatMode
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.components.ExplicitBadge
import com.music.spotui.ui.navigation.Routes
import com.music.spotui.ui.screens.CustomSlider
import com.music.spotui.ui.theme.AppPalette
import com.music.spotui.ui.viewmodel.PlayerViewModel
import com.music.spotui.util.DevConsoleManager

private fun resolveExpressiveRouteName(context: Context): String {
    return runCatching {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val outs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val bt = outs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
        } ?: outs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        }
        if (bt != null) {
            return bt.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Bluetooth"
        }
        val wired = outs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
        if (wired != null) "Headphones" else "Device Speaker"
    }.getOrDefault("Device Speaker")
}

/**
 * Rich, expressive SpotUI v2.5.0 SongPlay screen layout. Can be switched back to
 * [PlayerUiStyle.LEGACY_CLASSIC] anytime from Settings -> Advanced Settings.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalGlideComposeApi::class)
@Composable
fun NewPlayerLayout(
    navController: NavController,
    playerViewModel: PlayerViewModel,
    context: Context,
    dominantColor: Color,
    canvasUrl: String?,
    songTitle: String,
    songSinger: String,
    songCoverUri: String,
    songPlayingState: Boolean,
    songId: Int,
    currentTrack: SongsModel?,
    queueSongs: List<SongsModel>,
    artworkPagerState: PagerState,
    isLiked: MutableState<Boolean>,
    shuffle: Boolean,
    repeat: RepeatMode,
    songProgressText: String,
    songDurationText: String,
    onBackClick: () -> Unit,
    onMenuClick: () -> Unit,
    onOpenAudioQuickSheet: () -> Unit,
    onShowSavedIn: () -> Unit,
    onArtistClick: () -> Unit,
    onMessageToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val appVolumePercent by SongPlayer.appVolumePercentFlow.collectAsState()
    val spatialOn = remember(songId, appVolumePercent) { isEqSpatialEnabled(context) }
    val routeName = remember(songId) { resolveExpressiveRouteName(context) }
    val inAnyPlaylist = remember(songId, currentTrack?.spotifyTrackId) {
        currentTrack != null && LocalPlaylistPref.getLocalPlaylists(context).any { pl ->
            LocalPlaylistPref.isSongInPlaylist(context, pl.id, currentTrack.id, currentTrack.spotifyTrackId)
        }
    }

    val playButtonScale by animateFloatAsState(
        targetValue = if (songPlayingState) 1.0f else 0.94f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 420f),
        label = "newPlayerPlayScale",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .testTag("new_expressive_player_layout"),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.statusBarsPadding())

        // ── Top Glassmorphic Pill Header ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconButton(
                onClick = {
                    UiSoundEngine.playTapChime(context)
                    onBackClick()
                },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("new_player_dismiss"),
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Minimize player",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                    .clickable {
                        UiSoundEngine.playTapChime(context)
                        onOpenAudioQuickSheet()
                    }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "PLAYING FROM",
                    color = AppPalette,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.9.sp,
                )
                Text(
                    text = playerViewModel.currentSongAlbum.value.ifBlank { "Now Playing" },
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            IconButton(
                onClick = {
                    UiSoundEngine.playTapChime(context)
                    DevConsoleManager.logButtonAction("NewPlayer", "open_menu", songTitle)
                    onMenuClick()
                },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("new_player_menu"),
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More track options",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        // ── Expressive Artwork Stage (Flexible weight prevents vertical clipping on small screens) ──
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        ) {
            val haloAlpha = if (canvasUrl != null) 0f else 1f
            Box(
                modifier = Modifier
                    .sizeIn(maxWidth = 360.dp, maxHeight = 360.dp)
                    .aspectRatio(1f)
                    .alpha(haloAlpha)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                dominantColor.copy(alpha = 0.55f),
                                Color.Black.copy(alpha = 0.25f),
                            ),
                        ),
                    )
                    .border(1.5.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp))
                    .padding(10.dp),
            ) {
                if (queueSongs.isEmpty()) {
                    GlideImage(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(18.dp)),
                        model = songCoverUri,
                        contentScale = ContentScale.Crop,
                        contentDescription = songTitle,
                    )
                } else {
                    HorizontalPager(
                        state = artworkPagerState,
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        GlideImage(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(18.dp)),
                            model = queueSongs.getOrNull(page)?.coverUri ?: songCoverUri,
                            contentScale = ContentScale.Crop,
                            contentDescription = queueSongs.getOrNull(page)?.title ?: songTitle,
                        )
                    }
                }
            }
        }

        // ── Telemetry & Source Ribbon ──
        val isResolving = playerViewModel.isResolving.value && playerViewModel.resolveStatus.value.isNotBlank()
        val hasError = !playerViewModel.resolveError.value.isNullOrBlank()
        val sourceLabel = when {
            hasError -> playerViewModel.resolveError.value ?: "Stream Error"
            isResolving -> playerViewModel.resolveStatus.value
            SongPlayer.currentQuality.isNotBlank() -> "${SongPlayer.currentSource} · ${SongPlayer.currentQuality}"
            else -> SongPlayer.currentSource.ifBlank { "Studio Stream" }
        }
        val sourceTint = when {
            hasError -> Color(0xFFFF7A7A)
            isResolving -> Color(0xFF5CE1E6)
            SongPlayer.currentSource.startsWith("Lossless") -> Color(0xFFFFCA62)
            SongPlayer.currentSource == "Downloaded" -> Color(0xFF8BE9FD)
            else -> AppPalette
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = sourceTint,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = sourceLabel,
                        color = sourceTint,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .heightIn(min = 36.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (spatialOn) AppPalette.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.10f))
                    .border(
                        width = 1.dp,
                        color = if (spatialOn) AppPalette.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(50),
                    )
                    .clickable {
                        UiSoundEngine.playTapChime(context)
                        onOpenAudioQuickSheet()
                    }
                    .testTag("new_player_audio_dock_pill")
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Icon(
                    imageVector = if (appVolumePercent == 0) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "In-app volume and 3D spatial",
                    tint = if (spatialOn) AppPalette else Color.White,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = if (spatialOn) "$appVolumePercent% · 3D" else "$appVolumePercent%",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }

        // ── Track Info + Direct Playlist & Love Actions ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val trackExplicit = com.music.spotui.util.ExplicitContentDetector.detectExplicit(
                        apiExplicit = currentTrack?.explicit == true,
                        title = songTitle,
                        album = currentTrack?.album.orEmpty(),
                    )
                    if (trackExplicit) {
                        ExplicitBadge(size = 11.sp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = songTitle.ifBlank { "Select a track" },
                        color = Color.White,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                    )
                }
                Text(
                    text = songSinger.ifBlank { "Unknown Artist" },
                    color = Color(0xFFC5CAD6),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clickable {
                            UiSoundEngine.playTapChime(context)
                            onArtistClick()
                        },
                )
            }

            IconButton(
                onClick = {
                    UiSoundEngine.playTapChime(context)
                    DevConsoleManager.logButtonAction("NewPlayer", "add_to_playlist", songTitle)
                    onShowSavedIn()
                },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("new_player_playlist_button"),
            ) {
                Icon(
                    imageVector = if (inAnyPlaylist) Icons.Default.PlaylistAddCheck else Icons.Default.PlaylistAdd,
                    contentDescription = "Add to playlist",
                    tint = if (inAnyPlaylist) AppPalette else Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }

            IconButton(
                onClick = {
                    val nextLiked = !isLiked.value
                    UiSoundEngine.playToggleSound(context, nextLiked)
                    if (nextLiked) addLikedSongId(context, songId.toString())
                    else removeLikedSongId(context, songId.toString())
                    isLiked.value = isSongLiked(context, songId.toString())
                    currentTrack?.spotifyTrackId?.takeIf { it.isNotBlank() }?.let { tid ->
                        SpotifySync.setTrackSaved(context, tid, isLiked.value)
                    }
                    ModuleLibsBridge.setLastFmLoveStatus(context, songTitle, songSinger, isLiked.value)
                    DevConsoleManager.logButtonAction("NewPlayer", "toggle_like", "$songTitle -> ${isLiked.value}")
                    onMessageToast(if (isLiked.value) "Added to Liked Songs" else "Removed from Liked Songs")
                },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("new_player_like_button"),
            ) {
                Icon(
                    imageVector = if (isLiked.value) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isLiked.value) "Liked" else "Like song",
                    tint = if (isLiked.value) AppPalette else Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        // ── Expressive Scrubber ──
        var isDragging by remember { mutableStateOf(false) }
        var dragValue by remember { mutableStateOf(0f) }
        val liveFraction = SongPlayer.getDuration().toFloat().let { dur ->
            if (dur > 0f) (SongPlayer.getCurrentPosition().toFloat() / dur).coerceIn(0f, 1f) else 0f
        }
        CustomSlider(
            value = if (isDragging) dragValue else liveFraction,
            onValueChange = { newValue ->
                isDragging = true
                dragValue = newValue
                UiSoundEngine.playSliderTick(context, newValue)
            },
            onValueChangeFinished = {
                val dur = SongPlayer.getDuration()
                if (dur > 0) SongPlayer.seekTo((dragValue * dur).toLong())
                isDragging = false
                if (!songPlayingState) {
                    SongPlayer.play()
                    playerViewModel.updateSongState(
                        playerViewModel.currentSongCoverUri.value,
                        playerViewModel.currentSongTitle.value,
                        playerViewModel.currentSongSinger.value,
                        true,
                        playerViewModel.currentSongId.value,
                        playerViewModel.currentSongIndex.value,
                        playerViewModel.currentSongAlbum.value,
                    )
                }
            },
            valueRange = 0f..1f,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (isDragging) {
                    val dur = SongPlayer.getDuration()
                    if (dur > 0) playerViewModel.formatDuration((dragValue * dur).toLong()) else "0:00"
                } else songProgressText,
                color = Color(0xFFB3B8C4),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = songDurationText,
                color = Color(0xFFB3B8C4),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // ── Transport Deck ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        ) {
            IconButton(
                onClick = {
                    val next = !shuffle
                    UiSoundEngine.playToggleSound(context, next)
                    DevConsoleManager.logButtonAction("NewPlayer", "shuffle", next.toString())
                    playerViewModel.updateShuffleState(next)
                },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("new_player_shuffle"),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_player_shuffle),
                    contentDescription = "Shuffle",
                    tint = if (shuffle) AppPalette else Color(0xFFD0D5E0),
                    modifier = Modifier.size(22.dp),
                )
            }

            IconButton(
                onClick = {
                    UiSoundEngine.playTapChime(context)
                    DevConsoleManager.logButtonAction("NewPlayer", "previous", songTitle)
                    playerViewModel.playPreviousSong(queueSongs, context)
                    isLiked.value = isSongLiked(context, playerViewModel.currentSongId.value.toString())
                },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("new_player_previous"),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_player_back),
                    contentDescription = "Previous track",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }

            Box(
                modifier = Modifier
                    .scale(playButtonScale)
                    .requiredSize(68.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color.White, Color(0xFFE8ECF5)),
                        ),
                    )
                    .border(2.dp, AppPalette.copy(alpha = 0.55f), RoundedCornerShape(22.dp))
                    .clickable {
                        UiSoundEngine.playTapChime(context)
                        if (songPlayingState) {
                            SongPlayer.pause()
                            DevConsoleManager.logButtonAction("NewPlayer", "pause", songTitle)
                            playerViewModel.updateSongState(
                                playerViewModel.currentSongCoverUri.value,
                                playerViewModel.currentSongTitle.value,
                                playerViewModel.currentSongSinger.value,
                                false,
                                playerViewModel.currentSongId.value,
                                playerViewModel.currentSongIndex.value,
                                playerViewModel.currentSongAlbum.value,
                            )
                        } else {
                            SongPlayer.play()
                            DevConsoleManager.logButtonAction("NewPlayer", "play", songTitle)
                            playerViewModel.updateSongState(
                                playerViewModel.currentSongCoverUri.value,
                                playerViewModel.currentSongTitle.value,
                                playerViewModel.currentSongSinger.value,
                                true,
                                playerViewModel.currentSongId.value,
                                playerViewModel.currentSongIndex.value,
                                playerViewModel.currentSongAlbum.value,
                            )
                        }
                    }
                    .testTag("new_player_play_pause"),
                contentAlignment = Alignment.Center,
            ) {
                val busy = playerViewModel.isResolving.value || playerViewModel.isBuffering.value
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = Color.Black,
                        strokeWidth = 3.dp,
                    )
                } else {
                    Icon(
                        imageVector = if (songPlayingState) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (songPlayingState) "Pause" else "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }

            IconButton(
                onClick = {
                    UiSoundEngine.playTapChime(context)
                    DevConsoleManager.logButtonAction("NewPlayer", "next", songTitle)
                    playerViewModel.playNextSongs(queueSongs, context)
                    isLiked.value = isSongLiked(context, playerViewModel.currentSongId.value.toString())
                },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("new_player_next"),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_player_skip),
                    contentDescription = "Next track",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clickable {
                        val nextRepeat = when (repeat) {
                            RepeatMode.OFF -> RepeatMode.ALL
                            RepeatMode.ALL -> RepeatMode.ONE
                            RepeatMode.ONE -> RepeatMode.OFF
                        }
                        UiSoundEngine.playToggleSound(context, nextRepeat != RepeatMode.OFF)
                        DevConsoleManager.logButtonAction("NewPlayer", "repeat", nextRepeat.name)
                        playerViewModel.updateRepeatState(nextRepeat)
                    }
                    .testTag("new_player_repeat"),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_repeat),
                    contentDescription = "Repeat",
                    tint = if (repeat != RepeatMode.OFF) AppPalette else Color(0xFFD0D5E0),
                    modifier = Modifier.size(22.dp),
                )
                if (repeat == RepeatMode.ONE) {
                    Text(
                        text = "1",
                        color = AppPalette,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.offset(y = (-1).dp),
                    )
                }
            }
        }

        // ── Bottom Connect, Share & Queue Dock ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(16.dp))
                .padding(horizontal = 10.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable {
                        UiSoundEngine.playTapChime(context)
                        navController.navigate(Routes.DeveloperConsole.route)
                    },
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_devices),
                    contentDescription = "Audio output route",
                    tint = AppPalette,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = routeName,
                    color = AppPalette,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 150.dp),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        UiSoundEngine.playTapChime(context)
                        val link = currentTrack?.spotifyTrackId
                            ?.takeIf { it.isNotBlank() }
                            ?.let { "https://open.spotify.com/track/$it" }
                            ?: "${currentTrack?.title ?: songTitle} ${currentTrack?.singer ?: songSinger}".trim()
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, link)
                        }
                        context.startActivity(Intent.createChooser(send, "Share track"))
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share track",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(
                    onClick = {
                        UiSoundEngine.playTapChime(context)
                        navController.navigate(Routes.Queue.route)
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = "Open Queue",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}
