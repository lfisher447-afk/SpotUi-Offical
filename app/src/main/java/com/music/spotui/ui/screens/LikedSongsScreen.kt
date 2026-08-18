package com.music.spotui.ui.screens

import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.music.spotui.R
import com.music.spotui.data.api.Response
import com.music.spotui.data.export.NovaAcExportManager
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.components.Loader
import com.music.spotui.ui.components.Snackbar
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.AppPalette
import com.music.spotui.ui.viewmodel.LikedSongsViewModel
import com.music.spotui.ui.viewmodel.PlayerViewModel
import com.music.spotui.ui.components.SwipeToPlayNextWrapper
import kotlinx.coroutines.launch

private fun formatLikedNovaAcBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private fun formatLikedNovaAcTime(millis: Long): String {
    val seconds = (millis / 1_000L).coerceAtLeast(0L)
    return "%d:%02d".format(seconds / 60L, seconds % 60L)
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class, ExperimentalFoundationApi::class)
@Composable
fun LikedSongsScreen(navController: NavController) {

    val likedSongsViewModel: LikedSongsViewModel = hiltViewModel()
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val songsResp by likedSongsViewModel.songs.collectAsState()
    val context = LocalContext.current

    val songs = (songsResp as? Response.Success)?.data.orEmpty()

    LaunchedEffect(songs) {
        if (songs.isNotEmpty()) {
            SongPlayer.prefetchList(songs.map { it.url }, context)
        }
    }

    var menuSong by remember { mutableStateOf<com.music.spotui.data.entity.SongsModel?>(null) }
    var pendingNovaAcExport by remember { mutableStateOf<NovaAcExportManager.PreparedExport?>(null) }
    var pendingFullAudioPlan by remember { mutableStateOf<NovaAcExportManager.FullAudioArchivePlan?>(null) }
    var showNovaAcExportConfig by remember { mutableStateOf(false) }
    var novaAcExportName by remember { mutableStateOf("Liked Songs") }
    var novaAcAudioMode by remember { mutableStateOf(NovaAcExportManager.AudioPayloadMode.REQUIRE_ALL_LOCAL_AUDIO) }
    var novaAcSecurityMode by remember { mutableStateOf(NovaAcExportManager.ArchiveSecurityMode.DEVICE_SECURE) }
    var novaAcBrowserPassphrase by remember { mutableStateOf("") }
    val exportScope = rememberCoroutineScope()
    var novaAcProgress by remember { mutableStateOf<NovaAcExportManager.ExportProgress?>(null) }
    var isWritingNovaAcExport by remember { mutableStateOf(false) }
    var completedNovaAcArchive by remember { mutableStateOf<NovaAcExportManager.FullAudioArchiveResult?>(null) }
    var failedNovaAcExport by remember { mutableStateOf<NovaAcExportManager.ExportProgress?>(null) }
    val novaAcExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val pending = pendingNovaAcExport
        val fullPlan = pendingFullAudioPlan
        pendingNovaAcExport = null
        pendingFullAudioPlan = null
        if (uri == null) return@rememberLauncherForActivityResult
        exportScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val resultText = when {
                fullPlan != null -> {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        isWritingNovaAcExport = true
                        novaAcProgress = null
                        completedNovaAcArchive = null
                        failedNovaAcExport = null
                    }
                    var lastProgressAt = 0L
                    NovaAcExportManager.writeFullAudioArchive(
                        context = context.applicationContext,
                        destination = uri,
                        plan = fullPlan,
                        onProgress = { progress ->
                            val now = System.currentTimeMillis()
                            if (progress.stage == NovaAcExportManager.ExportStage.COMPLETED || progress.stage == NovaAcExportManager.ExportStage.FAILED || now - lastProgressAt >= 125L) {
                                lastProgressAt = now
                                exportScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                    novaAcProgress = progress
                                    if (progress.stage == NovaAcExportManager.ExportStage.FAILED) failedNovaAcExport = progress
                                }
                            }
                        },
                    ).fold(
                        onSuccess = {
                            exportScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                completedNovaAcArchive = it
                                isWritingNovaAcExport = false
                            }
                            "Liked Songs archive exported: ${formatLikedNovaAcBytes(it.archiveBytes)} · ${it.includedAudioTrackCount}/${it.trackCount} local payloads"
                        },
                        onFailure = {
                            exportScope.launch(kotlinx.coroutines.Dispatchers.Main) { isWritingNovaAcExport = false }
                            it.message ?: "Liked Songs full archive failed"
                        },
                    )
                }
                pending != null -> NovaAcExportManager.writePreparedExport(context.applicationContext, uri, pending).fold(
                    onSuccess = { "Liked Songs archive exported" },
                    onFailure = { it.message ?: "Liked Songs export failed" },
                )
                else -> "No archive was prepared"
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(context, resultText, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
    menuSong?.let { sel ->
        com.music.spotui.ui.components.SongOptionsSheet(
            song = sel,
            navController = navController,
            context = context,
            onDismiss = {
                // If the song was unliked in the menu, drop it from the list right
                // away (the Spotify-side removal is already in flight).
                if (!com.music.spotui.data.preferences.isSongLiked(context, sel.id.toString())) {
                    likedSongsViewModel.removeLocally(sel.id)
                }
                menuSong = null
            },
        )
    }

    if (showNovaAcExportConfig) {
        AlertDialog(
            onDismissRequest = { showNovaAcExportConfig = false },
            containerColor = Color(0xFF282828),
            title = { Text("Export Liked Songs as .novaac", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Create a large Liked Songs archive. Full local-audio mode packages every downloaded track and reports missing tracks before it writes the file.",
                        color = Color(0xFFB3B3B3),
                        fontSize = 13.sp,
                    )
                    TextField(
                        value = novaAcExportName,
                        onValueChange = { novaAcExportName = it },
                        label = { Text("Archive name") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF383838),
                            unfocusedContainerColor = Color(0xFF383838),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = AppPalette,
                            unfocusedLabelColor = Color.Gray,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                    Text("Audio payload profile", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    listOf(
                        NovaAcExportManager.AudioPayloadMode.REQUIRE_ALL_LOCAL_AUDIO to "Full — require all tracks downloaded",
                        NovaAcExportManager.AudioPayloadMode.INCLUDE_AVAILABLE_LOCAL_AUDIO to "Best-effort — include available local tracks",
                        NovaAcExportManager.AudioPayloadMode.METADATA_ONLY to "Metadata only — smallest archive",
                    ).forEach { (mode, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF20252A))
                                .clickable { novaAcAudioMode = mode }.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = label, tint = if (novaAcAudioMode == mode) AppPalette else Color.Gray, modifier = Modifier.size(19.dp))
                            Spacer(Modifier.width(10.dp)); Text(label, color = Color.White, fontSize = 12.sp)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF20252A))
                            .clickable { novaAcSecurityMode = if (novaAcSecurityMode == NovaAcExportManager.ArchiveSecurityMode.DEVICE_SECURE) NovaAcExportManager.ArchiveSecurityMode.WEB_PASSPHRASE else NovaAcExportManager.ArchiveSecurityMode.DEVICE_SECURE }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Browser player mode", tint = if (novaAcSecurityMode == NovaAcExportManager.ArchiveSecurityMode.WEB_PASSPHRASE) AppPalette else Color.Gray, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(10.dp)); Column { Text("Browser-player passphrase mode", color = Color.White, fontSize = 12.sp); Text("Enable to unlock this archive in the companion HTML player.", color = Color.Gray, fontSize = 10.sp) }
                    }
                    if (novaAcSecurityMode == NovaAcExportManager.ArchiveSecurityMode.WEB_PASSPHRASE) {
                        TextField(value = novaAcBrowserPassphrase, onValueChange = { novaAcBrowserPassphrase = it }, label = { Text("Browser archive passphrase") }, singleLine = true,
                            colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFF383838), unfocusedContainerColor = Color(0xFF383838), focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val archiveName = novaAcExportName.trim().ifBlank { "Liked Songs" }
                        val selectedMode = novaAcAudioMode
                        val selectedSecurity = novaAcSecurityMode
                        val passphrase = novaAcBrowserPassphrase
                        val songsToArchive = songs
                        exportScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val result = runCatching {
                                NovaAcExportManager.planFullAudioArchive(
                                    context = context.applicationContext,
                                    collectionName = archiveName,
                                    sourceType = "liked_songs",
                                    selectedSongs = songsToArchive,
                                    mode = selectedMode,
                                    securityMode = selectedSecurity,
                                    browserPassphrase = passphrase,
                                )
                            }
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                result.onSuccess { plan ->
                                    if (!plan.canExport) {
                                        android.widget.Toast.makeText(context, "Full archive needs ${plan.missingTrackCount} more downloaded liked track(s). Choose Best-effort to continue now.", android.widget.Toast.LENGTH_LONG).show()
                                    } else {
                                        showNovaAcExportConfig = false
                                        pendingFullAudioPlan = plan
                                        novaAcExportLauncher.launch(plan.fileName)
                                    }
                                }.onFailure { error ->
                                    android.widget.Toast.makeText(context, error.message ?: "Unable to plan Liked Songs export", android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    enabled = songs.isNotEmpty(),
                ) { Text("Export", color = AppPalette) }
            },
            dismissButton = {
                TextButton(onClick = { showNovaAcExportConfig = false }) { Text("Cancel", color = Color.White) }
            },
        )
    }

    if (isWritingNovaAcExport || completedNovaAcArchive != null || failedNovaAcExport != null) {
        val progress = novaAcProgress
        val completed = completedNovaAcArchive
        val failed = failedNovaAcExport
        AlertDialog(
            onDismissRequest = {
                if (completed != null) completedNovaAcArchive = null
                if (failed != null) failedNovaAcExport = null
            },
            containerColor = Color(0xFF16161C),
            title = {
                Text(
                    text = when {
                        failed != null -> "Liked Songs archive needs attention"
                        completed == null -> "Creating one Liked Songs .NovaAc"
                        else -> "Liked Songs archive complete"
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (failed != null) {
                        Text(failed.message, color = Color(0xFFFFB4AB), fontSize = 13.sp)
                        Text("Failure code: ${failed.failureCode ?: "UNKNOWN"}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("Last safe byte: ${formatLikedNovaAcBytes(failed.archiveBytesWritten)} · recovery: ${failed.recoveryId ?: "not retained"}", color = Color(0xFFCDD4E0), fontSize = 11.sp)
                    } else if (completed == null) {
                        Text(progress?.message ?: "Opening selected destination…", color = Color(0xFFCDD4E0), fontSize = 13.sp)
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { (progress?.fraction ?: 0f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
                            color = AppPalette,
                            trackColor = Color(0xFF353540),
                        )
                        Text(
                            text = "Track ${progress?.currentTrackIndex ?: 0}/${progress?.totalTrackCount ?: 0} · ${progress?.currentTrackTitle?.ifBlank { "metadata" } ?: "metadata"}",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Source ${formatLikedNovaAcBytes(progress?.sourceBytesProcessed ?: 0L)} / ${formatLikedNovaAcBytes(progress?.sourceBytesPlanned ?: 0L)} · archive ${formatLikedNovaAcBytes(progress?.archiveBytesWritten ?: 0L)} · ${formatLikedNovaAcBytes(progress?.bytesPerSecond ?: 0L)}/s · ETA ${formatLikedNovaAcTime(progress?.etaMillis ?: 0L)} · ${formatLikedNovaAcTime(progress?.elapsedMillis ?: 0L)}",
                            color = Color(0xFFB3B3B3),
                            fontSize = 11.sp,
                        )
                        Text(
                            text = "Stage-first export builds and verifies the one archive privately with bounded reusable buffers before destination delivery.",
                            color = Color(0xFF8EE9C0),
                            fontSize = 11.sp,
                        )
                    } else {
                        Text("One .NovaAc file now contains the complete Liked Songs selection.", color = Color(0xFFCDD4E0), fontSize = 13.sp)
                        Text("Final archive size: ${formatLikedNovaAcBytes(completed.archiveBytes)}", color = AppPalette, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Coverage: ${completed.includedAudioTrackCount}/${completed.trackCount} local payloads · ${completed.skippedAudioTrackCount} unavailable or omitted · ${formatLikedNovaAcTime(completed.durationMillis)}",
                            color = Color.White,
                            fontSize = 12.sp,
                        )
                        Text("Pre-export estimate: ${formatLikedNovaAcBytes(completed.estimatedArchiveBytes)}", color = Color(0xFFB3B3B3), fontSize = 11.sp)
                        Text("Staged bytes: ${formatLikedNovaAcBytes(completed.stagedArchiveBytes)} · SHA-256: ${completed.archiveSha256.take(16)}…", color = Color(0xFFB3B3B3), fontSize = 11.sp)
                        Text(if (completed.deliveryVerified) "Destination readback verified." else "Destination delivery completed but readback is unavailable; recovery copy retained: ${completed.recoveryId ?: "available"}.", color = if (completed.deliveryVerified) Color(0xFF8EE9C0) else Color(0xFFFFD28C), fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                if (completed != null || failed != null) TextButton(onClick = { completedNovaAcArchive = null; failedNovaAcExport = null }) { Text("Done", color = AppPalette) }
            },
            dismissButton = {
                if (isWritingNovaAcExport) Text("Writing…", color = Color(0xFFB3B3B3), modifier = Modifier.padding(12.dp))
            },
        )
    }

    var snackbarMessage by remember { mutableStateOf("") }
    var snackbarVisible by remember { mutableStateOf(false) }
    LaunchedEffect(snackbarVisible) {
        if (snackbarVisible) {
            kotlinx.coroutines.delay(1500)
            snackbarVisible = false
        }
    }

    // Spotify's Liked Songs uses a purple → dark gradient.
    val likedColor = Color(0xFF5038A0)

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(AppBackground.toArgb()))
    ) {
        if (songsResp is Response.Loading) {
            Loader()
            return@Surface
        }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    modifier = Modifier.padding(16.dp, 0.dp),
                    navigationIcon = {
                        Icon(
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { navController.navigateUp() },
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "",
                            tint = Color.White
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                    ),
                    title = { Text(text = "") }
                )
            }
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(AppBackground.toArgb()))
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(440.dp)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(likedColor, Color(AppBackground.toArgb())),
                                    startY = -100f,
                                ),
                            ),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Spacer(modifier = Modifier.padding(25.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(230.dp)
                                    .background(
                                        brush = Brush.linearGradient(
                                            colors = listOf(Color(0xFF8E6FE0), Color(0xFF3B2A82)),
                                        )
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Favorite,
                                    contentDescription = "",
                                    tint = Color.White,
                                    modifier = Modifier.size(90.dp),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.padding(5.dp))
                        Text(
                            modifier = Modifier.padding(20.dp, 5.dp, 0.dp, 0.dp),
                            text = "Liked Songs",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            modifier = Modifier.padding(20.dp, 4.dp, 20.dp, 0.dp),
                            text = "${songs.size} songs",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .padding(20.dp, 0.dp)
                        ) {
                            var likedDownloaded by remember(songs) {
                                mutableStateOf(songs.isNotEmpty() && SongPlayer.allDownloaded(songs, context))
                            }

                            if (snackbarVisible) {
                                Box(modifier = Modifier.weight(1f)) {
                                    Snackbar(showMessage = snackbarMessage)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                            } else {
                                Row(
                                    horizontalArrangement = Arrangement.Start,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (songs.isNotEmpty()) {
                                        Icon(
                                            imageVector = if (likedDownloaded)
                                                Icons.Default.CheckCircle else ImageVector.vectorResource(R.drawable.ic_download),
                                            tint = if (likedDownloaded) Color(AppPalette.toArgb()) else Color.White,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null,
                                                ) {
                                                    if (!likedDownloaded) {
                                                        SongPlayer.downloadAll(songs, context)
                                                        snackbarMessage = "Downloading ${songs.size} tracks…"
                                                        snackbarVisible = true
                                                    }
                                                },
                                            contentDescription = "Download liked songs",
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = "Export",
                                            color = Color.Black,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(AppPalette)
                                                .clickable { showNovaAcExportConfig = true }
                                                .padding(horizontal = 10.dp, vertical = 7.dp),
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_queue_add),
                                            tint = Color.White,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null,
                                                ) {
                                                    playerViewModel.addAllToQueue(songs)
                                                    android.widget.Toast.makeText(
                                                        context,
                                                        "${songs.size} track(s) added to queue",
                                                        android.widget.Toast.LENGTH_SHORT,
                                                    ).show()
                                                },
                                            contentDescription = "Add to queue",
                                        )
                                        Spacer(modifier = Modifier.width(18.dp))
                                        // Shuffle-play: start liked songs in random order.
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_player_shuffle),
                                            tint = Color.White,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null,
                                                ) {
                                                    likedSongsViewModel.startShuffled(songs)?.let { first ->
                                                        SongPlayer.playSong(first.url, context)
                                                        likedSongsViewModel.updateSongState(
                                                            first.coverUri,
                                                            first.title,
                                                            first.singer,
                                                            true,
                                                            first.id,
                                                            0,
                                                            "Liked Songs",
                                                        )
                                                    }
                                                },
                                            contentDescription = "Shuffle play",
                                        )
                                    }
                                }
                            }
                            // Always visible: pause when playing, resume when this
                            // list's track is paused, otherwise start from the top.
                            if (songs.isNotEmpty()) {
                                val playing = likedSongsViewModel.currentSongPlayingState.value
                                val currentInList = songs.any { it.id == likedSongsViewModel.currentSongId.value }
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(100.dp))
                                        .background(Color.White)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            when {
                                                currentInList -> likedSongsViewModel.setPlaying(!playing)
                                                else -> {
                                                    likedSongsViewModel.updateQueue(songs)
                                                    SongPlayer.playSong(songs[0].url, context)
                                                    likedSongsViewModel.updateSongState(
                                                        songs[0].coverUri,
                                                        songs[0].title,
                                                        songs[0].singer,
                                                        true,
                                                        songs[0].id,
                                                        0,
                                                        "Liked Songs"
                                                    )
                                                }
                                            }
                                        }
                                ) {
                                    Icon(
                                        modifier = Modifier.size(25.dp),
                                        tint = Color.Black,
                                        painter = painterResource(
                                            id = if (currentInList && playing) R.drawable.ic_playing else R.drawable.play_svgrepo_com,
                                        ),
                                        contentDescription = if (currentInList && playing) "Pause" else "Play"
                                    )
                                }
                            }
                        }
                    }
                }

                itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                    val currentColor = if (song.id == likedSongsViewModel.currentSongId.value)
                        Color(AppPalette.toArgb()) else Color.White

                    SwipeToPlayNextWrapper(
                        onPlayNext = {
                            playerViewModel.playNext(song)
                            android.widget.Toast.makeText(
                                context,
                                "${song.title} will play next",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AppBackground)
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onLongClick = { menuSong = song },
                                    onClick = {
                                        likedSongsViewModel.updateQueue(songs)
                                        SongPlayer.playSong(song.url, context)
                                        likedSongsViewModel.updateSongState(
                                            song.coverUri,
                                            song.title,
                                            song.singer,
                                            true,
                                            song.id,
                                            index,
                                            "Liked Songs"
                                        )
                                    },
                                )
                                .padding(20.dp, 8.dp)
                        ) {
                            GlideImage(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                model = song.coverUri,
                                failure = placeholder(R.drawable.placeholder),
                                contentScale = ContentScale.Crop,
                                contentDescription = ""
                            )
                            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (song.explicit) {
                                        com.music.spotui.ui.components.ExplicitBadge()
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = song.title,
                                        color = currentColor,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                                Text(
                                    text = song.singer,
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options for ${song.title}",
                                tint = Color.LightGray,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(50))
                                    .clickable { menuSong = song }
                                    .padding(6.dp),
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(160.dp)) }
            }
        }
    }
}
