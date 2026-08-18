package com.music.spotui.ui.screens

import android.content.Context
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.music.spotui.data.api.Response
import com.music.spotui.data.entity.AlbumsModel
import com.music.spotui.data.export.NovaAcExportManager
import com.music.spotui.di.Palette
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.components.Loader
import com.music.spotui.ui.components.Snackbar
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.AppPalette
import com.music.spotui.ui.viewmodel.PlaylistViewModel
import com.music.spotui.ui.viewmodel.PlayerViewModel
import com.music.spotui.ui.components.SwipeToPlayNextWrapper
import kotlinx.coroutines.launch

enum class PlaylistSortOption(val label: String) {
    DATE("Date added"),
    TITLE("Title"),
    ARTIST("Artist"),
    ALBUM("Album")
}

fun PlaylistSortOption.getDescriptiveLabel(isDescending: Boolean): String {
    return when (this) {
        PlaylistSortOption.DATE -> if (isDescending) "Date added (newest to oldest)" else "Date added (oldest to newest)"
        PlaylistSortOption.TITLE -> if (isDescending) "Title (Z to A)" else "Title (A to Z)"
        PlaylistSortOption.ARTIST -> if (isDescending) "Artist (Z to A)" else "Artist (A to Z)"
        PlaylistSortOption.ALBUM -> if (isDescending) "Album (Z to A)" else "Album (A to Z)"
    }
}

private const val PREF_PLAYLIST_SORTS = "PlaylistSorts"

private fun formatPlaylistNovaAcBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private fun formatPlaylistNovaAcTime(millis: Long): String {
    val seconds = (millis / 1_000L).coerceAtLeast(0L)
    return "%d:%02d".format(seconds / 60L, seconds % 60L)
}

fun getPlaylistSortOption(context: Context, playlistId: String): PlaylistSortOption {
    val prefs = context.getSharedPreferences(PREF_PLAYLIST_SORTS, Context.MODE_PRIVATE)
    val saved = prefs.getString("sort_option_$playlistId", PlaylistSortOption.DATE.name)
    return runCatching { PlaylistSortOption.valueOf(saved!!) }.getOrDefault(PlaylistSortOption.DATE)
}

fun isPlaylistSortDescending(context: Context, playlistId: String): Boolean {
    val prefs = context.getSharedPreferences(PREF_PLAYLIST_SORTS, Context.MODE_PRIVATE)
    if (!prefs.contains("sort_descending_$playlistId")) {
        val opt = getPlaylistSortOption(context, playlistId)
        return opt == PlaylistSortOption.DATE
    }
    return prefs.getBoolean("sort_descending_$playlistId", true)
}

fun setPlaylistSort(context: Context, playlistId: String, option: PlaylistSortOption, descending: Boolean) {
    context.getSharedPreferences(PREF_PLAYLIST_SORTS, Context.MODE_PRIVATE).edit()
        .putString("sort_option_$playlistId", option.name)
        .putBoolean("sort_descending_$playlistId", descending)
        .apply()
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistScreen(navController: NavController, playlistId: String, playlistName: String = "") {

    val playlistViewModel: PlaylistViewModel = hiltViewModel()
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val songsResp by playlistViewModel.songs.collectAsState()
    val playlistResp by playlistViewModel.playlist.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(playlistId) {
        playlistViewModel.loadPlaylist(playlistId)
    }

    val songs = (songsResp as? Response.Success)?.data.orEmpty()
    val playlist = (playlistResp as? Response.Success)?.data
        ?: AlbumsModel(
            id = playlistId.hashCode() and 0x7fffffff,
            artists = "",
            coverUri = songs.firstOrNull()?.coverUri ?: "",
            name = playlistName,
            time = "",
        )

    LaunchedEffect(songs, playlist, songsResp, playlistResp) {
        if (songsResp is Response.Success && playlistResp is Response.Success && songs.isNotEmpty() && playlist != null) {
            com.music.spotui.data.preferences.OfflineCollectionsPref.saveCollection(
                context = context,
                id = playlistId,
                name = playlist.name,
                coverUri = playlist.coverUri,
                artists = playlist.artists,
                isPlaylist = true,
                songs = songs
            )
        }
    }

    LaunchedEffect(songs) {
        if (songs.isNotEmpty()) {
            SongPlayer.prefetchList(songs.map { it.url }, context)
        }
    }
    
    var searchQuery by remember(playlistId) { mutableStateOf("") }
    var currentSort by remember(playlistId) { mutableStateOf(getPlaylistSortOption(context, playlistId)) }
    var isDescending by remember(playlistId) { mutableStateOf(isPlaylistSortDescending(context, playlistId)) }
    var showSortSheet by remember { mutableStateOf(false) }
    var selectionMode by remember(playlistId) { mutableStateOf(false) }
    var selectedTrackIds by remember(playlistId) { mutableStateOf(setOf<String>()) }
    var pendingNovaAcExport by remember { mutableStateOf<NovaAcExportManager.PreparedExport?>(null) }
    var pendingFullAudioPlan by remember { mutableStateOf<NovaAcExportManager.FullAudioArchivePlan?>(null) }
    var showNovaAcMenu by remember { mutableStateOf(false) }
    var showNovaAcExportConfig by remember { mutableStateOf(false) }
    var novaAcExportName by remember(playlistId) { mutableStateOf(playlistName.ifBlank { "Playlist cache" }) }
    var novaAcAudioMode by remember(playlistId) { mutableStateOf(NovaAcExportManager.AudioPayloadMode.REQUIRE_ALL_LOCAL_AUDIO) }
    var novaAcSecurityMode by remember(playlistId) { mutableStateOf(NovaAcExportManager.ArchiveSecurityMode.DEVICE_SECURE) }
    var novaAcBrowserPassphrase by remember(playlistId) { mutableStateOf("") }
    var novaAcSongsOverride by remember(playlistId) { mutableStateOf<List<com.music.spotui.data.entity.SongsModel>?>(null) }
    val exportScope = androidx.compose.runtime.rememberCoroutineScope()
    var novaAcProgress by remember(playlistId) { mutableStateOf<NovaAcExportManager.ExportProgress?>(null) }
    var isWritingNovaAcExport by remember(playlistId) { mutableStateOf(false) }
    var completedNovaAcArchive by remember(playlistId) { mutableStateOf<NovaAcExportManager.FullAudioArchiveResult?>(null) }
    var failedNovaAcExport by remember(playlistId) { mutableStateOf<NovaAcExportManager.ExportProgress?>(null) }

    val novaAcImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            NovaAcExportManager.importExport(context, uri, saveToOfflineLibrary = true)
                .onSuccess { result ->
                    playlistViewModel.reloadPlaylist(playlistId)
                    android.widget.Toast.makeText(
                        context,
                        "Imported '${result.collectionName}' (${result.importedAudioFileCount} audio files restored)",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
                .onFailure {
                    android.widget.Toast.makeText(context, "NovaAc import failed: ${it.message}", android.widget.Toast.LENGTH_LONG).show()
                }
        }
    }
    val novaAcExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val prepared = pendingNovaAcExport
        val fullPlan = pendingFullAudioPlan
        pendingNovaAcExport = null
        pendingFullAudioPlan = null
        when {
            uri == null -> android.widget.Toast.makeText(context, "NovaAc export cancelled", android.widget.Toast.LENGTH_SHORT).show()
            fullPlan != null -> {
                isWritingNovaAcExport = true
                novaAcProgress = null
                completedNovaAcArchive = null
                failedNovaAcExport = null
                exportScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                var lastProgressAt = 0L
                val result = NovaAcExportManager.writeFullAudioArchive(
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
                )
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    isWritingNovaAcExport = false
                    result.onSuccess { archive ->
                        completedNovaAcArchive = archive
                        android.widget.Toast.makeText(
                            context,
                            "Saved one ${formatPlaylistNovaAcBytes(archive.archiveBytes)} .NovaAc for ${archive.trackCount} playlist tracks",
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    }.onFailure { error ->
                        android.widget.Toast.makeText(context, "NovaAc export failed: ${error.message}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
            }
            prepared != null -> {
                NovaAcExportManager.writePreparedExport(context, uri, prepared)
                    .onSuccess {
                        android.widget.Toast.makeText(context, "Exported ${prepared.trackCount}-track NovaAc archive", android.widget.Toast.LENGTH_LONG).show()
                    }
                    .onFailure { android.widget.Toast.makeText(context, "NovaAc export failed: ${it.message}", android.widget.Toast.LENGTH_LONG).show() }
            }
            else -> android.widget.Toast.makeText(context, "No NovaAc export was prepared", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun trackSelectionId(song: com.music.spotui.data.entity.SongsModel): String =
        song.spotifyTrackId.ifBlank { "local:${song.id}" }

    val filteredSongs = remember(songs, searchQuery, currentSort, isDescending) {
        val filtered = if (searchQuery.isBlank()) {
            songs
        } else {
            songs.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.singer.contains(searchQuery, ignoreCase = true)
            }
        }
        
        when (currentSort) {
            PlaylistSortOption.DATE -> if (isDescending) filtered.reversed() else filtered
            PlaylistSortOption.TITLE -> if (isDescending) filtered.sortedByDescending { it.title.lowercase() } else filtered.sortedBy { it.title.lowercase() }
            PlaylistSortOption.ARTIST -> if (isDescending) filtered.sortedByDescending { it.singer.lowercase() } else filtered.sortedBy { it.singer.lowercase() }
            PlaylistSortOption.ALBUM -> if (isDescending) filtered.sortedByDescending { it.album.lowercase() } else filtered.sortedBy { it.album.lowercase() }
        }
    }

    var menuSong by remember { mutableStateOf<com.music.spotui.data.entity.SongsModel?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    if (showRenameDialog) {
        val currentName = playlist.name.ifBlank { playlistName }
        var newPlaylistNameInput by remember(currentName) { mutableStateOf(currentName) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = Color(0xFF282828),
            title = { Text("Rename Playlist", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                TextField(
                    value = newPlaylistNameInput,
                    onValueChange = { newPlaylistNameInput = it },
                    placeholder = { Text("Playlist name", color = Color.Gray) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF383838),
                        unfocusedContainerColor = Color(0xFF383838),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFF1ED760),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                )
            },
            confirmButton = {
                Text(
                    "Save",
                    color = Color(0xFF1ED760),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .clickable {
                            val name = newPlaylistNameInput.trim()
                            if (name.isNotBlank()) {
                                com.music.spotui.data.preferences.LocalPlaylistPref.renamePlaylist(context, playlistId, name)
                                com.music.spotui.data.api.Api.HomeCache.library = null
                                playlistViewModel.reloadPlaylist(playlistId)
                            }
                            showRenameDialog = false
                        }
                        .padding(8.dp)
                )
            },
            dismissButton = {
                Text(
                    "Cancel",
                    color = Color.Gray,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .clickable { showRenameDialog = false }
                        .padding(8.dp)
                )
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = Color(0xFF282828),
            title = { Text("Delete Playlist", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete this playlist?", color = Color.LightGray) },
            confirmButton = {
                Text(
                    "Delete",
                    color = Color(0xFFFF5252),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .clickable {
                            com.music.spotui.data.preferences.LocalPlaylistPref.deletePlaylist(context, playlistId)
                            com.music.spotui.data.api.Api.HomeCache.library = null
                            showDeleteDialog = false
                            navController.navigateUp()
                        }
                        .padding(8.dp)
                )
            },
            dismissButton = {
                Text(
                    "Cancel",
                    color = Color.Gray,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .clickable { showDeleteDialog = false }
                        .padding(8.dp)
                )
            }
        )
    }

    if (showNovaAcExportConfig) {
        val defaultName = playlist.name.ifBlank { playlistName }.ifBlank { "Playlist cache" }
        AlertDialog(
            onDismissRequest = { showNovaAcExportConfig = false },
            containerColor = Color(0xFF282828),
            title = { Text("Export playlist as .novaac", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Create a large, configurable archive from ${songs.size} track(s). Full local-audio mode streams every eligible downloaded file into the archive and reports anything unavailable before writing.",
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
                        NovaAcExportManager.AudioPayloadMode.REQUIRE_ALL_LOCAL_AUDIO to "Full archive — require every track downloaded",
                        NovaAcExportManager.AudioPayloadMode.INCLUDE_AVAILABLE_LOCAL_AUDIO to "Best-effort archive — include every available local track",
                        NovaAcExportManager.AudioPayloadMode.METADATA_ONLY to "Metadata-only archive — smallest file",
                    ).forEach { (mode, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF20252A))
                                .clickable { novaAcAudioMode = mode }.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = if (novaAcAudioMode == mode) "$label selected" else "$label not selected", tint = if (novaAcAudioMode == mode) AppPalette else Color.Gray, modifier = Modifier.size(19.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(label, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        }
                    }
                    Text("Security & browser playback", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF20252A))
                            .clickable { novaAcSecurityMode = NovaAcExportManager.ArchiveSecurityMode.DEVICE_SECURE }.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Device-secure archive", tint = if (novaAcSecurityMode == NovaAcExportManager.ArchiveSecurityMode.DEVICE_SECURE) AppPalette else Color.Gray, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text("Device-secure archive", color = Color.White, fontSize = 12.sp); Text("Android Keystore protection; inspectable but not browser-decryptable.", color = Color.Gray, fontSize = 10.sp) }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF20252A))
                            .clickable { novaAcSecurityMode = NovaAcExportManager.ArchiveSecurityMode.WEB_PASSPHRASE }.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Browser-passphrase archive", tint = if (novaAcSecurityMode == NovaAcExportManager.ArchiveSecurityMode.WEB_PASSPHRASE) AppPalette else Color.Gray, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text("Browser-passphrase archive", color = Color.White, fontSize = 12.sp); Text("Use the companion HTML player with this passphrase to play included local audio.", color = Color.Gray, fontSize = 10.sp) }
                    }
                    if (novaAcSecurityMode == NovaAcExportManager.ArchiveSecurityMode.WEB_PASSPHRASE) {
                        TextField(
                            value = novaAcBrowserPassphrase,
                            onValueChange = { novaAcBrowserPassphrase = it },
                            label = { Text("Browser archive passphrase") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFF383838), unfocusedContainerColor = Color(0xFF383838), focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val archiveName = novaAcExportName.trim().ifBlank { defaultName }
                        val selectedMode = novaAcAudioMode
                        val selectedSecurity = novaAcSecurityMode
                        val passphrase = novaAcBrowserPassphrase
                        val songsToArchive = novaAcSongsOverride ?: songs
                        exportScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val result = runCatching {
                                NovaAcExportManager.planFullAudioArchive(
                                    context = context.applicationContext,
                                    collectionName = archiveName,
                                    sourceType = "playlist",
                                    selectedSongs = songsToArchive,
                                    mode = selectedMode,
                                    securityMode = selectedSecurity,
                                    browserPassphrase = passphrase,
                                )
                            }
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                result.onSuccess { plan ->
                                    if (!plan.canExport) {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Full archive needs ${plan.missingTrackCount} more downloaded track(s). Choose Best-effort to export available audio now.",
                                            android.widget.Toast.LENGTH_LONG,
                                        ).show()
                                    } else {
                                        showNovaAcExportConfig = false
                                        pendingFullAudioPlan = plan
                                        novaAcExportLauncher.launch(plan.fileName)
                                    }
                                }.onFailure { error ->
                                    android.widget.Toast.makeText(context, error.message ?: "Unable to plan NovaAc export", android.widget.Toast.LENGTH_LONG).show()
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
                        failed != null -> "Playlist archive needs attention"
                        completed == null -> "Creating one playlist .NovaAc"
                        else -> "Playlist archive complete"
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
                        Text("Last safe byte: ${formatPlaylistNovaAcBytes(failed.archiveBytesWritten)} · recovery: ${failed.recoveryId ?: "not retained"}", color = Color(0xFFCDD4E0), fontSize = 11.sp)
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
                            text = "Source ${formatPlaylistNovaAcBytes(progress?.sourceBytesProcessed ?: 0L)} / ${formatPlaylistNovaAcBytes(progress?.sourceBytesPlanned ?: 0L)} · archive ${formatPlaylistNovaAcBytes(progress?.archiveBytesWritten ?: 0L)} · ${formatPlaylistNovaAcBytes(progress?.bytesPerSecond ?: 0L)}/s · ETA ${formatPlaylistNovaAcTime(progress?.etaMillis ?: 0L)} · ${formatPlaylistNovaAcTime(progress?.elapsedMillis ?: 0L)}",
                            color = Color(0xFFB3B3B3),
                            fontSize = 11.sp,
                        )
                        Text(
                            text = "Spotui first creates and verifies one fixed private archive with bounded reusable buffers, then delivers it to the document destination.",
                            color = Color(0xFF8EE9C0),
                            fontSize = 11.sp,
                        )
                    } else {
                        Text("One .NovaAc file now contains the complete playlist selection.", color = Color(0xFFCDD4E0), fontSize = 13.sp)
                        Text("Final archive size: ${formatPlaylistNovaAcBytes(completed.archiveBytes)}", color = AppPalette, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Coverage: ${completed.includedAudioTrackCount}/${completed.trackCount} local payloads · ${completed.skippedAudioTrackCount} unavailable or omitted · ${formatPlaylistNovaAcTime(completed.durationMillis)}",
                            color = Color.White,
                            fontSize = 12.sp,
                        )
                        Text(
                            text = "Pre-export estimate: ${formatPlaylistNovaAcBytes(completed.estimatedArchiveBytes)}. The final encrypted archive is calculated from the bytes actually written.",
                            color = Color(0xFFB3B3B3),
                            fontSize = 11.sp,
                        )
                        Text("Staged bytes: ${formatPlaylistNovaAcBytes(completed.stagedArchiveBytes)} · SHA-256: ${completed.archiveSha256.take(16)}…", color = Color(0xFFB3B3B3), fontSize = 11.sp)
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

    menuSong?.let { sel ->
        com.music.spotui.ui.components.SongOptionsSheet(
            song = sel,
            navController = navController,
            context = context,
            currentPlaylistId = playlistId,
            onSongRemovedFromPlaylist = { playlistViewModel.reloadPlaylist(playlistId) },
            onDismiss = { menuSong = null },
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(AppBackground.toArgb()))
    ) {
        if (songsResp is Response.Loading && playlistResp is Response.Loading) {
            Loader()
            return@Surface
        }

        var snackbarMessage by remember { mutableStateOf("") }
        var snackbarVisible by remember { mutableStateOf(false) }
        LaunchedEffect(snackbarVisible) {
            if (snackbarVisible) {
                kotlinx.coroutines.delay(1500)
                snackbarVisible = false
            }
        }

        var dominentColor by remember { mutableStateOf(Color(AppBackground.toArgb())) }
        Palette().extractSecondColorFromCoverUrl(context = context, playlist.coverUri) { color ->
            dominentColor = color
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
                    title = {
                        if (selectionMode) {
                            Text("${selectedTrackIds.size} selected", fontWeight = FontWeight.Bold)
                        } else {
                            Text(text = "")
                        }
                    },
                    actions = {
                        if (songs.isNotEmpty()) {
                            Text(
                                text = if (selectionMode) "Done" else "Select",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable {
                                        selectionMode = !selectionMode
                                        if (!selectionMode) selectedTrackIds = emptySet()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                            )
                            Box {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "NovaAc Options",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .clickable { showNovaAcMenu = true }
                                        .padding(8.dp)
                                )
                                DropdownMenu(
                                    expanded = showNovaAcMenu,
                                    onDismissRequest = { showNovaAcMenu = false },
                                    modifier = Modifier.background(Color(0xFF282828))
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Multi-select tracks", color = Color.White) },
                                        onClick = {
                                            showNovaAcMenu = false
                                            selectionMode = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Export Full Playlist .NovaAc", color = Color.White) },
                                        onClick = {
                                            showNovaAcMenu = false
                                            // Never compile a full playlist into a ByteArray. The advanced
                                            // flow plans coverage, then streams every local payload into one
                                            // encrypted destination-selected NovaAc container.
                                            novaAcExportName = playlist.name.ifBlank { playlistName }.ifBlank { "Playlist archive" }
                                            novaAcSongsOverride = null
                                            showNovaAcExportConfig = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Import .NovaAc Archive", color = Color.White) },
                                        onClick = {
                                            showNovaAcMenu = false
                                            novaAcImportLauncher.launch(arrayOf("*/*"))
                                        }
                                    )
                                }
                            }
                        }
                    },
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
                            .heightIn(min = 440.dp)
                            .padding(bottom = 8.dp)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(dominentColor, Color(AppBackground.toArgb())),
                                    startY = -100f,
                                ),
                            ),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Spacer(modifier = Modifier.padding(25.dp))

                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            GlideImage(
                                modifier = Modifier.size(230.dp),
                                model = playlist.coverUri,
                                failure = placeholder(R.drawable.placeholder),
                                contentDescription = "",
                            )
                        }
                        Spacer(modifier = Modifier.padding(5.dp))
                        val isLocalPlaylist = playlistId.startsWith("local_pl_")
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(20.dp, 5.dp, 20.dp, 0.dp)
                                .then(
                                    if (isLocalPlaylist) {
                                        Modifier.clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { showRenameDialog = true }
                                    } else Modifier
                                )
                        ) {
                            Text(
                                text = playlist.name.ifBlank { playlistName },
                                color = Color.White,
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (isLocalPlaylist) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Playlist Name",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        if (playlist.time.isNotBlank()) {
                            Text(
                                modifier = Modifier.padding(20.dp, 4.dp, 20.dp, 0.dp),
                                text = playlist.time,
                                color = Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(20.dp, 4.dp, 0.dp, 0.dp)
                        ) {
                            if (playlistId.startsWith("local_pl_")) {
                                Icon(
                                    imageVector = Icons.Default.PhoneAndroid,
                                    contentDescription = "Local Playlist",
                                    tint = Color(0xFF1ED760),
                                    modifier = Modifier
                                        .size(14.dp)
                                        .padding(end = 4.dp)
                                )
                                Text(
                                    text = "Local Playlist",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            } else if (playlist.artists.isNotBlank()) {
                                Text(
                                    text = "Playlist • ${playlist.artists}",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .padding(20.dp, 0.dp)
                        ) {
                            var playlistDownloaded by remember(songs) {
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
                                        // Add all playlist tracks to the queue.
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_queue_add),
                                            tint = Color.White,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null,
                                                ) {
                                                    playlistViewModel.addAllToQueue(filteredSongs)
                                                    android.widget.Toast.makeText(
                                                        context,
                                                        "${filteredSongs.size} track(s) added to queue",
                                                        android.widget.Toast.LENGTH_SHORT,
                                                    ).show()
                                                },
                                            contentDescription = "Add to queue",
                                        )
                                        Spacer(modifier = Modifier.width(18.dp))
                                        Icon(
                                            imageVector = if (playlistDownloaded)
                                                Icons.Default.CheckCircle else ImageVector.vectorResource(R.drawable.ic_download),
                                            tint = if (playlistDownloaded) Color(AppPalette.toArgb()) else Color.White,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null,
                                                ) {
                                                    com.music.spotui.data.preferences.OfflineCollectionsPref.saveCollection(
                                                        context = context,
                                                        id = playlistId,
                                                        name = playlist.name,
                                                        coverUri = playlist.coverUri,
                                                        artists = playlist.artists,
                                                        isPlaylist = true,
                                                        songs = songs
                                                    )
                                                    if (!playlistDownloaded) {
                                                        SongPlayer.downloadAll(songs, context)
                                                        snackbarMessage = "Downloading ${songs.size} tracks…"
                                                        snackbarVisible = true
                                                    } else {
                                                        snackbarMessage = "Playlist added to offline library"
                                                        snackbarVisible = true
                                                    }
                                                },
                                            contentDescription = "Download playlist",
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
                                        // Shuffle-play: start the playlist in random order.
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_player_shuffle),
                                            tint = Color.White,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null,
                                                ) {
                                                    playlistViewModel.startShuffled(songs)?.let { first ->
                                                        SongPlayer.playSong(first.url, context)
                                                        playlistViewModel.updateSongState(
                                                            first.coverUri,
                                                            first.title,
                                                            first.singer,
                                                            true,
                                                            first.id,
                                                            0,
                                                            playlist.name,
                                                        )
                                                    }
                                                },
                                            contentDescription = "Shuffle play",
                                        )
                                        if (playlistId.startsWith("local_pl_")) {
                                            Spacer(modifier = Modifier.width(18.dp))
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete Playlist",
                                                tint = Color.White,
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clickable(
                                                        interactionSource = remember { MutableInteractionSource() },
                                                        indication = null,
                                                    ) { showDeleteDialog = true }
                                            )
                                        }
                                    }
                                }
                            }
                            // Always visible: pause when playing, resume when this
                            // list's track is paused, otherwise start from the top.
                            if (songs.isNotEmpty()) {
                                val playing = playlistViewModel.currentSongPlayingState.value
                                val currentInList = songs.any { it.id == playlistViewModel.currentSongId.value }
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
                                                currentInList -> playlistViewModel.setPlaying(!playing)
                                                filteredSongs.isNotEmpty() -> {
                                                    playlistViewModel.updateQueue(filteredSongs)
                                                    SongPlayer.playSong(filteredSongs[0].url, context)
                                                    playlistViewModel.updateSongState(
                                                        filteredSongs[0].coverUri,
                                                        filteredSongs[0].title,
                                                        filteredSongs[0].singer,
                                                        true,
                                                        filteredSongs[0].id,
                                                        0,
                                                        playlist.name
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

                // ── Search bar for playlist ──
                item {
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
                                    text = "Search in playlist",
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
                }
                
                // ── Sort action ──
                item {
                    if (songs.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp, 0.dp, 20.dp, 8.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
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
                                    contentDescription = "Sort Options",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .padding(start = 4.dp)
                                )
                            }
                        }
                    }
                }

                if (selectionMode) {
                    item {
                        val selectedSongs = songs.filter { trackSelectionId(it) in selectedTrackIds }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF20252A))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                "${selectedSongs.size} selected",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                if (selectedSongs.size == filteredSongs.size && filteredSongs.isNotEmpty()) "Clear" else "Select all",
                                color = AppPalette,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    selectedTrackIds = if (selectedSongs.size == filteredSongs.size) {
                                        emptySet()
                                    } else {
                                        filteredSongs.map(::trackSelectionId).toSet()
                                    }
                                },
                            )
                            Text(
                                "Export selected .NovaAc",
                                color = if (selectedSongs.isEmpty()) Color.Gray else AppPalette,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable(enabled = selectedSongs.isNotEmpty()) {
                                    // Selected exports share the same streaming writer as the complete
                                    // playlist. This keeps one chosen subset in one archive without ever
                                    // accumulating its audio payloads in a ByteArray.
                                    novaAcSongsOverride = selectedSongs
                                    novaAcExportName = playlist.name.ifBlank { playlistName }.ifBlank { "Playlist archive" } + " selection"
                                    showNovaAcExportConfig = true
                                },
                            )
                        }
                    }
                }

                itemsIndexed(filteredSongs, key = { _, song -> song.id }) { index, song ->
                    val currentColor = if (song.id == playlistViewModel.currentSongId.value)
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
                                    onLongClick = {
                                        if (selectionMode) {
                                            val id = trackSelectionId(song)
                                            selectedTrackIds = if (id in selectedTrackIds) selectedTrackIds - id else selectedTrackIds + id
                                        } else {
                                            menuSong = song
                                        }
                                    },
                                    onClick = {
                                        if (selectionMode) {
                                            val id = trackSelectionId(song)
                                            selectedTrackIds = if (id in selectedTrackIds) selectedTrackIds - id else selectedTrackIds + id
                                        } else {
                                            playlistViewModel.updateQueue(filteredSongs)
                                            SongPlayer.playSong(song.url, context)
                                            playlistViewModel.updateSongState(
                                                song.coverUri,
                                                song.title,
                                                song.singer,
                                                true,
                                                song.id,
                                                index,
                                                playlist.name
                                            )
                                        }
                                    },
                                )
                                .padding(20.dp, 8.dp)
                        ) {
                            if (selectionMode) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = if (trackSelectionId(song) in selectedTrackIds) "Selected" else "Not selected",
                                    tint = if (trackSelectionId(song) in selectedTrackIds) AppPalette else Color(0xFF777777),
                                    modifier = Modifier
                                        .size(24.dp)
                                        .padding(end = 8.dp),
                                )
                            }
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
                            if (!selectionMode) {
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

                item { Spacer(modifier = Modifier.height(160.dp)) }
            }
        }
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
                    PlaylistSortOption.entries.forEach { option ->
                        val isSelected = option == currentSort
                        val icon = when (option) {
                            PlaylistSortOption.DATE -> Icons.Default.DateRange
                            PlaylistSortOption.TITLE -> Icons.AutoMirrored.Filled.List
                            PlaylistSortOption.ARTIST -> Icons.Default.Person
                            PlaylistSortOption.ALBUM -> Icons.Default.Menu
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val nextDesc = if (currentSort == option) {
                                        !isDescending
                                    } else {
                                        option == PlaylistSortOption.DATE
                                    }
                                    currentSort = option
                                    isDescending = nextDesc
                                    setPlaylistSort(context, playlistId, option, nextDesc)
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
                                 text = if (isSelected) option.getDescriptiveLabel(isDescending) else option.getDescriptiveLabel(option == PlaylistSortOption.DATE),
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
    }
}
