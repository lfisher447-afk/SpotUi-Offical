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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person


import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.music.spotui.R
import com.music.spotui.data.export.NovaAcExportManager
import com.music.spotui.data.preferences.DownloadSortOption
import com.music.spotui.data.preferences.getDownloadedSongs
import com.music.spotui.data.preferences.getDownloadsSortOption
import com.music.spotui.data.preferences.isDownloadsSortDescending
import com.music.spotui.data.preferences.setDownloadsSortOption
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.AppPalette
import com.music.spotui.ui.viewmodel.PlayerViewModel
import com.music.spotui.ui.components.SwipeToPlayNextWrapper
import kotlinx.coroutines.launch

private fun formatNovaAcBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private fun formatNovaAcDuration(millis: Long): String {
    val seconds = (millis / 1_000L).coerceAtLeast(0L)
    return "%d:%02d".format(seconds / 60L, seconds % 60L)
}

fun DownloadSortOption.getDescriptiveLabel(isDescending: Boolean): String {
    return when (this) {
        DownloadSortOption.DATE -> if (isDescending) "Date added (newest to oldest)" else "Date added (oldest to newest)"
        DownloadSortOption.TITLE -> if (isDescending) "Title (Z to A)" else "Title (A to Z)"
        DownloadSortOption.ARTIST -> if (isDescending) "Artist (Z to A)" else "Artist (A to Z)"
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalGlideComposeApi::class,
    ExperimentalFoundationApi::class
)
@Composable
fun DownloadsScreen(navController: NavController) {

    val playerViewModel: PlayerViewModel = hiltViewModel()
    val context = LocalContext.current

    // Completed downloads (from prefs) + live in-progress ones (from SongPlayer). Poll
    // while the screen is open so a track appears here the moment its download starts,
    // shows a live percentage, and moves into the list when it finishes.
    var songs by remember { mutableStateOf(getDownloadedSongs(context)) }
    var inProgress by remember {
        mutableStateOf(com.music.spotui.di.SongPlayer.downloadingSnapshot())
    }
    var showSortSheet by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showDownloadActions by remember { mutableStateOf(false) }
    var pendingNovaAcExport by remember { mutableStateOf<NovaAcExportManager.PreparedExport?>(null) }
    var pendingFullAudioNovaAcExport by remember { mutableStateOf<NovaAcExportManager.FullAudioArchivePlan?>(null) }
    var isPreparingNovaAcExport by remember { mutableStateOf(false) }
    var showNovaAcExportDialog by remember { mutableStateOf(false) }
    var novaAcSecurityMode by remember { mutableStateOf(NovaAcExportManager.ArchiveSecurityMode.WEB_PASSPHRASE) }
    var novaAcPassphrase by remember { mutableStateOf("") }
    var novaAcProgress by remember { mutableStateOf<NovaAcExportManager.ExportProgress?>(null) }
    var isWritingNovaAcExport by remember { mutableStateOf(false) }
    var completedNovaAcArchive by remember { mutableStateOf<NovaAcExportManager.FullAudioArchiveResult?>(null) }
    var failedNovaAcExport by remember { mutableStateOf<NovaAcExportManager.ExportProgress?>(null) }
    var currentSort by remember { mutableStateOf(getDownloadsSortOption(context)) }
    var isDescending by remember { mutableStateOf(isDownloadsSortDescending(context)) }

    androidx.compose.runtime.LaunchedEffect(currentSort, isDescending) {
        songs = getDownloadedSongs(context)
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            val snap = com.music.spotui.di.SongPlayer.downloadingSnapshot()
            // A download leaving the snapshot means it finished → refresh the saved list.
            if (snap.size != inProgress.size) songs = getDownloadedSongs(context)
            inProgress = snap
            kotlinx.coroutines.delay(400)
        }
    }

    val novaAcImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val appContext = context.applicationContext
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                val result = NovaAcExportManager.importExport(appContext, uri, saveToOfflineLibrary = true)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    result.onSuccess { imported ->
                        songs = getDownloadedSongs(context)
                        android.widget.Toast.makeText(
                            context,
                            "Imported ${imported.songs.size} track(s); ${imported.importedAudioFileCount} audio file(s) restored",
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    }.onFailure { error ->
                        android.widget.Toast.makeText(
                            context,
                            error.message ?: "NovaAc import failed",
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
    }
    val novaAcExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val prepared = pendingNovaAcExport
        val fullPlan = pendingFullAudioNovaAcExport
        pendingNovaAcExport = null
        pendingFullAudioNovaAcExport = null
        when {
            uri == null -> android.widget.Toast.makeText(context, "NovaAc export cancelled", android.widget.Toast.LENGTH_SHORT).show()
            fullPlan != null -> {
                val appContext = context.applicationContext
                isWritingNovaAcExport = true
                novaAcProgress = null
                completedNovaAcArchive = null
                failedNovaAcExport = null
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    var lastProgressAt = 0L
                    val result = NovaAcExportManager.writeFullAudioArchive(
                        context = appContext,
                        destination = uri,
                        plan = fullPlan,
                        onProgress = { progress ->
                            val now = System.currentTimeMillis()
                            if (progress.stage == NovaAcExportManager.ExportStage.COMPLETED || progress.stage == NovaAcExportManager.ExportStage.FAILED || now - lastProgressAt >= 125L) {
                                lastProgressAt = now
                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                    novaAcProgress = progress
                                    if (progress.stage == NovaAcExportManager.ExportStage.FAILED) failedNovaAcExport = progress
                                }
                            }
                        },
                    )
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        isWritingNovaAcExport = false
                        result.onSuccess { archive ->
                            songs = getDownloadedSongs(context)
                            completedNovaAcArchive = archive
                            android.widget.Toast.makeText(
                                context,
                                "Saved one ${formatNovaAcBytes(archive.archiveBytes)} .NovaAc with ${archive.includedAudioTrackCount}/${archive.trackCount} local payload(s)",
                                android.widget.Toast.LENGTH_LONG,
                            ).show()
                        }.onFailure { error ->
                            android.widget.Toast.makeText(context, error.message ?: "NovaAc export failed", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            prepared != null -> {
                val appContext = context.applicationContext
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    val result = NovaAcExportManager.writePreparedExport(appContext, uri, prepared)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        result.onSuccess {
                            android.widget.Toast.makeText(context, "Saved ${prepared.trackCount}-track .NovaAc archive", android.widget.Toast.LENGTH_LONG).show()
                        }.onFailure { error ->
                            android.widget.Toast.makeText(context, error.message ?: "NovaAc export failed", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            else -> android.widget.Toast.makeText(context, "No NovaAc export was prepared", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val beginDownloadsNovaAcExport: () -> Unit = {
        if (isPreparingNovaAcExport) {
            android.widget.Toast.makeText(context, "NovaAc export is already being prepared", android.widget.Toast.LENGTH_SHORT).show()
        } else if (songs.isEmpty()) {
            android.widget.Toast.makeText(context, "There are no downloaded tracks to archive", android.widget.Toast.LENGTH_SHORT).show()
        } else if (novaAcSecurityMode == NovaAcExportManager.ArchiveSecurityMode.WEB_PASSPHRASE && novaAcPassphrase.isBlank()) {
            android.widget.Toast.makeText(context, "Enter a passphrase for browser playback", android.widget.Toast.LENGTH_LONG).show()
        } else {
            showNovaAcExportDialog = false
            isPreparingNovaAcExport = true
            val songsToArchive = songs.toList()
            val appContext = context.applicationContext
            val passphrase = novaAcPassphrase.takeIf { novaAcSecurityMode == NovaAcExportManager.ArchiveSecurityMode.WEB_PASSPHRASE }
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                val result = runCatching {
                    NovaAcExportManager.planFullAudioArchive(
                        context = appContext,
                        collectionName = "Spotui Downloads",
                        sourceType = "downloads",
                        selectedSongs = songsToArchive,
                        mode = NovaAcExportManager.AudioPayloadMode.INCLUDE_AVAILABLE_LOCAL_AUDIO,
                        securityMode = novaAcSecurityMode,
                        browserPassphrase = passphrase,
                    )
                }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    isPreparingNovaAcExport = false
                    result.onSuccess { plan ->
                        pendingFullAudioNovaAcExport = plan
                        novaAcExportLauncher.launch(plan.fileName)
                    }.onFailure { error ->
                        android.widget.Toast.makeText(context, error.message ?: "Unable to prepare NovaAc export", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    if (showNovaAcExportDialog) {
        AlertDialog(
            onDismissRequest = { if (!isPreparingNovaAcExport) showNovaAcExportDialog = false },
            containerColor = Color(0xFF16161C),
            title = { Text("Export Downloads as .NovaAc", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Choose browser-portable encryption to unlock and play the archive in the standalone NovaAc player.", color = Color(0xFFCDD4E0), fontSize = 13.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (novaAcSecurityMode == NovaAcExportManager.ArchiveSecurityMode.WEB_PASSPHRASE) Color(0xFF18372B) else Color(0xFF25252D)).clickable { novaAcSecurityMode = NovaAcExportManager.ArchiveSecurityMode.WEB_PASSPHRASE }.padding(12.dp),
                    ) {
                        Column {
                            Text("Web Passphrase — browser playable", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Portable encrypted archive. Keep the passphrase: the browser needs it to decrypt and extract local audio.", color = Color(0xFFB3B3B3), fontSize = 11.sp)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (novaAcSecurityMode == NovaAcExportManager.ArchiveSecurityMode.DEVICE_SECURE) Color(0xFF2B2B35) else Color(0xFF25252D)).clickable { novaAcSecurityMode = NovaAcExportManager.ArchiveSecurityMode.DEVICE_SECURE }.padding(12.dp),
                    ) {
                        Column {
                            Text("This Android device only", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Uses the Android Keystore. It can be re-imported on this authorized app install but cannot be opened by a browser.", color = Color(0xFFB3B3B3), fontSize = 11.sp)
                        }
                    }
                    if (novaAcSecurityMode == NovaAcExportManager.ArchiveSecurityMode.WEB_PASSPHRASE) {
                        TextField(
                            value = novaAcPassphrase,
                            onValueChange = { novaAcPassphrase = it },
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)),
                            label = { Text("Archive passphrase") },
                            placeholder = { Text("Required to open in the browser player") },
                            singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF25252D), unfocusedContainerColor = Color(0xFF25252D),
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                focusedIndicatorColor = AppPalette, unfocusedIndicatorColor = Color.Transparent,
                            ),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = beginDownloadsNovaAcExport, enabled = !isPreparingNovaAcExport && (novaAcSecurityMode != NovaAcExportManager.ArchiveSecurityMode.WEB_PASSPHRASE || novaAcPassphrase.isNotBlank())) { Text(if (isPreparingNovaAcExport) "Preparing…" else "Choose save location", color = AppPalette) } },
            dismissButton = { TextButton(onClick = { showNovaAcExportDialog = false }, enabled = !isPreparingNovaAcExport) { Text("Cancel", color = Color(0xFFB3B3B3)) } },
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
                        failed != null -> "NovaAc export needs attention"
                        completed == null -> "Creating one .NovaAc archive"
                        else -> "NovaAc archive complete"
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
                        Text("Last safe archive byte: ${formatNovaAcBytes(failed.archiveBytesWritten)} · source processed: ${formatNovaAcBytes(failed.sourceBytesProcessed)}", color = Color(0xFFCDD4E0), fontSize = 11.sp)
                        if (!failed.recoveryId.isNullOrBlank()) {
                            Text("Recovery copy: ${failed.recoveryId}. If staging completed, Spotui retained the verified private archive so destination delivery can be retried without rebuilding the playlist.", color = Color(0xFF8EE9C0), fontSize = 11.sp)
                        }
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
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                        )
                        Text(
                            text = "Source ${formatNovaAcBytes(progress?.sourceBytesProcessed ?: 0L)} / ${formatNovaAcBytes(progress?.sourceBytesPlanned ?: 0L)} · archive ${formatNovaAcBytes(progress?.archiveBytesWritten ?: 0L)} · ${formatNovaAcBytes(progress?.bytesPerSecond ?: 0L)}/s · ETA ${formatNovaAcDuration(progress?.etaMillis ?: 0L)} · ${formatNovaAcDuration(progress?.elapsedMillis ?: 0L)}",
                            color = Color(0xFFB3B3B3),
                            fontSize = 11.sp,
                        )
                        Text(
                            text = "Stage-first mode writes one fixed private archive with a reusable 256 KB buffer, then delivers and verifies it separately. A document-provider interruption cannot corrupt the staged archive.",
                            color = Color(0xFF8EE9C0),
                            fontSize = 11.sp,
                        )
                    } else {
                        Text("One file was written for the entire Downloads collection.", color = Color(0xFFCDD4E0), fontSize = 13.sp)
                        Text("Final archive size: ${formatNovaAcBytes(completed.archiveBytes)}", color = AppPalette, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Payload coverage: ${completed.includedAudioTrackCount}/${completed.trackCount} local files · ${completed.skippedAudioTrackCount} unavailable or omitted · ${formatNovaAcDuration(completed.durationMillis)}",
                            color = Color.White,
                            fontSize = 12.sp,
                        )
                        Text(
                            text = "Pre-export estimate: ${formatNovaAcBytes(completed.estimatedArchiveBytes)}. The final size can differ because the archive is compressed and encrypted while it streams.",
                            color = Color(0xFFB3B3B3),
                            fontSize = 11.sp,
                        )
                        Text("Staged archive: ${formatNovaAcBytes(completed.stagedArchiveBytes)} · SHA-256: ${completed.archiveSha256.take(16)}…", color = Color(0xFFB3B3B3), fontSize = 11.sp)
                        Text(
                            text = if (completed.deliveryVerified) "Destination readback verified." else "Destination write completed, but this provider did not allow readback. Spotui retained a private recovery copy: ${completed.recoveryId ?: "available"}.",
                            color = if (completed.deliveryVerified) Color(0xFF8EE9C0) else Color(0xFFFFD28C),
                            fontSize = 11.sp,
                        )
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

    var menuSong by remember { mutableStateOf<com.music.spotui.data.entity.SongsModel?>(null) }
    menuSong?.let { sel ->
        com.music.spotui.ui.components.SongOptionsSheet(
            song = sel,
            navController = navController,
            context = context,
            onDismiss = { menuSong = null },
        )
    }

    var searchQuery by remember { mutableStateOf("") }
    // filter downloads by simple title/singer string match
    val displayedSongs = remember(songs, searchQuery) {
        if (searchQuery.isBlank()) {
            songs
        } else {
            songs.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.singer.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val accent = Color(0xFF1DB954)

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(AppBackground.toArgb()))
    ) {
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(AppBackground.toArgb()))
                    .verticalScroll(rememberScrollState())
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    accent.copy(alpha = 0.5f),
                                    Color(AppBackground.toArgb())
                                ),
                                startY = -100f,
                            ),
                        ),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Spacer(modifier = Modifier.padding(25.dp))
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(200.dp)
                                .background(accent.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = ImageVector.vectorResource(R.drawable.ic_download),
                                contentDescription = "",
                                tint = accent,
                                modifier = Modifier.size(90.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.padding(5.dp))
                    Text(
                        modifier = Modifier.padding(20.dp, 5.dp, 0.dp, 0.dp),
                        text = "Downloaded",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        modifier = Modifier.padding(20.dp, 4.dp, 20.dp, 0.dp),
                        text = "${songs.size} songs • available offline",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // ── Search bar for downloads ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp, 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .height(55.dp)
                        .background(Color.White)
                        .padding(10.dp, 0.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_search_big),
                        tint = Color.Black,
                        contentDescription = "Search",
                        modifier = Modifier.size(24.dp)
                    )

                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle.Default.copy(
                            fontSize = 16.sp,
                            color = Color.Black,
                            fontWeight = FontWeight(500)
                        ),
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = Color.Black
                        ),
                        singleLine = true,
                        placeholder = {
                            Text(
                                text = "Search downloaded songs",
                                color = Color.Gray,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )

                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = Color.Black,
                            modifier = Modifier
                                .size(24.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { searchQuery = "" }
                        )
                    }
                }

                // ── Library actions: sort, bulk Music export, archive tools, and clear ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp, 0.dp, 20.dp, 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (songs.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFF2A2A30))
                                .clickable { showSortSheet = true }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = currentSort.getDescriptiveLabel(isDescending),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Sort options",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(start = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        if (searchQuery.isBlank()) {
                            Text(
                                text = "Export all",
                                color = Color(0xFF1ED760),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFF1A1A20))
                                    .clickable {
                                        val appContext = context.applicationContext
                                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                            val (exported, destination) = com.music.spotui.data.preferences.exportDownloads(appContext)
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                val message = if (exported > 0) {
                                                    "Exported $exported track(s) to $destination"
                                                } else {
                                                    destination
                                                }
                                                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            )
                        }
                        Box {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Download library options",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(50))
                                    .clickable { showDownloadActions = true }
                                    .padding(4.dp),
                            )
                            DropdownMenu(
                                expanded = showDownloadActions,
                                onDismissRequest = { showDownloadActions = false },
                                modifier = Modifier.background(Color(0xFF282828)),
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Export all as .NovaAc", color = Color.White) },
                                    onClick = {
                                        showDownloadActions = false
                                        if (isPreparingNovaAcExport) {
                                            android.widget.Toast.makeText(context, "NovaAc export is already being prepared", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            // Default to portable mode so a user intentionally choosing
                                            // this Downloads export can unlock it in the bundled browser player.
                                            novaAcSecurityMode = NovaAcExportManager.ArchiveSecurityMode.WEB_PASSPHRASE
                                            showNovaAcExportDialog = true
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Import .NovaAc archive", color = Color.White) },
                                    onClick = {
                                        showDownloadActions = false
                                        novaAcImportLauncher.launch(arrayOf("application/octet-stream", "application/*", "*/*"))
                                    },
                                )
                                if (searchQuery.isBlank()) {
                                    DropdownMenuItem(
                                        text = { Text("Clear all downloads", color = Color(0xFFE57373)) },
                                        onClick = {
                                            showDownloadActions = false
                                            showClearConfirmDialog = true
                                        },
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }
                }

                // ── In-progress downloads (with live progress bar) ──
                inProgress.forEach { (song, pct) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp, 8.dp),
                    ) {
                        GlideImage(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            model = song.coverUri,
                            failure = placeholder(R.drawable.placeholder),
                            contentScale = ContentScale.Crop,
                            contentDescription = "",
                        )
                        Column(
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .weight(1f)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (song.explicit) {
                                    com.music.spotui.ui.components.ExplicitBadge()
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text(
                                    text = song.title,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { (pct.coerceIn(0, 100)) / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = accent,
                                trackColor = Color(0xFF333333),
                            )
                        }
                        Text(
                            text = "$pct%",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }

                if (songs.isEmpty() && inProgress.isEmpty()) {
                    Text(
                        text = "No downloads yet. Tap ⋯ on a track and choose Download.",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(20.dp),
                    )
                } else if (displayedSongs.isEmpty() && inProgress.isEmpty()) {
                    Text(
                        text = "No matches found for \"$searchQuery\"",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(20.dp),
                    )
                } else {
                    repeat(displayedSongs.size) { index ->
                        val song = displayedSongs[index]
                        val currentColor = if (song.id == playerViewModel.currentSongId.value)
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
                                            playerViewModel.updateQueue(displayedSongs)
                                            SongPlayer.playSong(song.url, context)
                                            playerViewModel.updateSongState(
                                                song.coverUri, song.title, song.singer,
                                                true, song.id, index, "Downloaded"
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
                                Column(
                                    modifier = Modifier
                                        .padding(start = 12.dp)
                                        .weight(1f)
                                ) {
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
                }

                Spacer(modifier = Modifier.height(160.dp))
                if (showSortSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { showSortSheet = false },
                        containerColor = Color(0xFF1A1A1A)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                        ) {
                            Text(
                                text = "Sort by",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 12.dp)
                            )
                            HorizontalDivider(color = Color(0xFF2A2A2A))
                            Spacer(modifier = Modifier.height(4.dp))
                            DownloadSortOption.entries.forEach { option ->
                                val isSelected = option == currentSort
                                 val icon = when (option) {
                                     DownloadSortOption.DATE -> Icons.Default.DateRange
                                     DownloadSortOption.TITLE -> Icons.AutoMirrored.Filled.List
                                     DownloadSortOption.ARTIST -> Icons.Default.Person
                                 }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (currentSort == option) {
                                                isDescending = !isDescending
                                            } else {
                                                currentSort = option
                                                isDescending = (option == DownloadSortOption.DATE)
                                            }
                                            setDownloadsSortOption(context, currentSort, isDescending)
                                            showSortSheet = false
                                        }
                                        .padding(16.dp, 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (isSelected) Color(AppPalette.toArgb()) else Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(18.dp))
                                    Text(
                                        text = if (isSelected) option.getDescriptiveLabel(isDescending) else option.getDescriptiveLabel(option == DownloadSortOption.DATE),
                                        color = if (isSelected) Color(AppPalette.toArgb()) else Color.White,
                                        fontSize = 15.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = if (isDescending) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                            contentDescription = null,
                                            tint = Color(AppPalette.toArgb()),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
                if (showClearConfirmDialog) {
                    AlertDialog(
                        onDismissRequest = { showClearConfirmDialog = false },
                        title = { Text(text = "Clear all downloads?", color = Color.White, fontWeight = FontWeight.Bold) },
                        text = { Text(text = "Are you sure you want to remove all downloaded songs? This action cannot be undone.", color = Color(0xFFB3B3B3)) },
                        confirmButton = {
                            TextButton(onClick = {
                                val n =
                                    com.music.spotui.data.preferences.clearAllDownloads(context)
                                songs = getDownloadedSongs(context)
                                showClearConfirmDialog = false
                                android.widget.Toast.makeText(
                                    context, "Removed $n download${if (n == 1) "" else "s"}",
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }) {
                                Text("Clear", color = Color(0xFFE57373))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showClearConfirmDialog = false }) {
                                Text("Cancel", color = Color.White)
                            }
                        },
                        containerColor = Color(0xFF1A1A1A),
                        titleContentColor = Color.White,
                        textContentColor = Color(0xFFB3B3B3),
                    )
                }
            }
        }
    }
}
