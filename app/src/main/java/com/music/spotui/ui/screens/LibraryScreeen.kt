package com.music.spotui.ui.screens

import com.music.spotui.ui.screens.SpotifyLogin.page.YouTubeMusicWebActivity

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.music.spotui.R
import com.music.spotui.data.api.Api
import com.music.spotui.data.api.Response
import com.music.spotui.data.entity.LibraryEntry
import com.music.spotui.data.preferences.LocalPlaylistPref
import com.music.spotui.data.preferences.getDownloadedSongs
import com.music.spotui.data.preferences.getLikedSongIds
import com.music.spotui.data.preferences.isLibraryGridView
import com.music.spotui.data.preferences.setLibraryGridView
import com.music.spotui.ui.components.Snackbar
import com.music.spotui.ui.navigation.Routes
import com.music.spotui.ui.navigation.albumRoute
import com.music.spotui.ui.navigation.artistRoute
import com.music.spotui.ui.navigation.playlistRoute
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.AppPalette
import com.music.spotui.ui.viewmodel.LibraryFilterType
import com.music.spotui.ui.viewmodel.LibrarySortOrder
import com.music.spotui.ui.viewmodel.LibraryViewModel

fun isLibraryEntryDownloaded(context: android.content.Context, entry: LibraryEntry): Boolean {
    if (entry.isLocal || entry.spotifyId == Api.HomeCache.DOWNLOADS_ID) return true
    if (entry.spotifyId == Api.HomeCache.LIKED_SONGS_ID) {
        return com.music.spotui.data.preferences.getDownloadedSongs(context).isNotEmpty()
    }
    val offlineCollections = com.music.spotui.data.preferences.OfflineCollectionsPref.getOfflineCollections(context)
    val cleanEntryId = Api.cleanId(entry.spotifyId)
    return offlineCollections.any { col ->
        val cleanColId = Api.cleanId(col.id)
        (cleanColId.isNotBlank() && cleanColId == cleanEntryId) ||
        (entry.isPlaylist == col.isPlaylist && entry.name.equals(col.name, ignoreCase = true))
    }
}

@Composable
fun LibraryFilterChips(
    selectedFilter: LibraryFilterType,
    isDownloadedOnly: Boolean,
    onFilterSelected: (LibraryFilterType) -> Unit,
    onToggleDownloaded: () -> Unit,
    onClearFilters: () -> Unit
) {
    val isAnyFilterActive = selectedFilter != LibraryFilterType.ALL || isDownloadedOnly

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isAnyFilterActive) {
            item(key = "lib_filter_clear") {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2A2A2A))
                        .clickable { onClearFilters() }
                        .testTag("library_clear_filters_chip"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear filters",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        item(key = "lib_filter_playlists") {
            LibraryChipItem(
                label = "Playlists",
                isSelected = selectedFilter == LibraryFilterType.PLAYLISTS,
                tag = "library_filter_playlists",
                onClick = { onFilterSelected(LibraryFilterType.PLAYLISTS) }
            )
        }

        item(key = "lib_filter_albums") {
            LibraryChipItem(
                label = "Albums",
                isSelected = selectedFilter == LibraryFilterType.ALBUMS,
                tag = "library_filter_albums",
                onClick = { onFilterSelected(LibraryFilterType.ALBUMS) }
            )
        }

        item(key = "lib_filter_artists") {
            LibraryChipItem(
                label = "Artists",
                isSelected = selectedFilter == LibraryFilterType.ARTISTS,
                tag = "library_filter_artists",
                onClick = { onFilterSelected(LibraryFilterType.ARTISTS) }
            )
        }

        item(key = "lib_filter_downloaded") {
            LibraryChipItem(
                label = "Downloaded",
                isSelected = isDownloadedOnly,
                tag = "library_filter_downloaded",
                onClick = { onToggleDownloaded() }
            )
        }
    }
}

@Composable
private fun LibraryChipItem(
    label: String,
    isSelected: Boolean,
    tag: String = "",
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) AppPalette else Color(0xFF22242E)
    val textColor = if (isSelected) Color.Black else Color.White

    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(backgroundColor)
            .clickable { onClick() }
            .let { if (tag.isNotBlank()) it.testTag(tag) else it }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun CollapsibleLibraryHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1A202B)).clickable { onToggle() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("$count item${if (count == 1) "" else "s"}", color = Color(0xFFB3B3B3), fontSize = 11.sp)
        }
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (expanded) "Collapse $title" else "Expand $title",
            tint = Color.White,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(navController: NavController) {

    val libraryViewModel: LibraryViewModel = hiltViewModel()
    val entries by libraryViewModel.entries.collectAsStateWithLifecycle()
    val account by libraryViewModel.account.collectAsStateWithLifecycle()
    val selectedFilter by libraryViewModel.selectedFilter.collectAsStateWithLifecycle()
    val isDownloadedOnly by libraryViewModel.isDownloadedOnly.collectAsStateWithLifecycle()
    val searchQuery by libraryViewModel.searchQuery.collectAsStateWithLifecycle()
    val sortOrder by libraryViewModel.sortOrder.collectAsStateWithLifecycle()
    val followedArtists by libraryViewModel.followedArtists.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                libraryViewModel.load()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var gridView by remember { mutableStateOf(isLibraryGridView(context)) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showSearchBar by rememberSaveable { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    val likedCount = remember(entries) { getLikedSongIds(context).size }
    val downloadedCount = remember(entries) { getDownloadedSongs(context).size }

    if (showCreateDialog) {
        var playlistNameInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = Color(0xFF282828),
            title = { Text("Create Local Playlist", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                TextField(
                    value = playlistNameInput,
                    onValueChange = { playlistNameInput = it },
                    placeholder = { Text("Playlist name", color = Color.Gray) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF383838),
                        unfocusedContainerColor = Color(0xFF383838),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = AppPalette,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .testTag("create_playlist_name_input")
                )
            },
            confirmButton = {
                Text(
                    "Create",
                    color = AppPalette,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable {
                            val name = playlistNameInput.trim().ifBlank { "My Playlist" }
                            val created = LocalPlaylistPref.createPlaylist(context, name)
                            Api.HomeCache.library = null
                            libraryViewModel.load()
                            showCreateDialog = false
                            navController.navigate(playlistRoute(created.id, created.name))
                        }
                        .testTag("create_playlist_confirm_button")
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                )
            },
            dismissButton = {
                Text(
                    "Cancel",
                    color = Color.Gray,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable { showCreateDialog = false }
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                )
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(AppBackground.toArgb()))
            .statusBarsPadding()
    ) {
        // Header: title + search + create playlist + account avatar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 6.dp)
        ) {
            Text(
                text = "Your Library",
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                fontSize = 24.sp,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    showSearchBar = !showSearchBar
                    if (!showSearchBar) libraryViewModel.setSearchQuery("")
                },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("library_search_toggle_button"),
            ) {
                Icon(
                    imageVector = if (showSearchBar) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = if (showSearchBar) "Close library search" else "Search library",
                    tint = Color.White,
                )
            }
            IconButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("library_create_playlist_button"),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Playlist", tint = Color.White)
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable { navController.navigate(Routes.Settings.route) }
                    .testTag("library_settings_avatar_button"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2B2D38)),
                    contentAlignment = Alignment.Center,
                ) {
                    val avatar = (account as? Response.Success)?.data?.imageUrl.orEmpty()
                    if (avatar.isNotBlank()) {
                        AccountAvatar(avatar, 34.dp)
                    } else {
                        Icon(Icons.Default.Person, contentDescription = "Settings & Account", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // Expandable in-library filter search bar
        AnimatedVisibility(
            visible = showSearchBar || searchQuery.isNotBlank(),
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { libraryViewModel.setSearchQuery(it) },
                placeholder = { Text("Filter playlists, albums & artists…", color = Color(0xFF9AA0AE), fontSize = 13.sp) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AppPalette) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { libraryViewModel.setSearchQuery("") },
                            modifier = Modifier.testTag("library_search_clear_button"),
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search", tint = Color.White)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppPalette,
                    unfocusedBorderColor = Color(0xFF2D2E38),
                    focusedContainerColor = Color(0xFF171820),
                    unfocusedContainerColor = Color(0xFF171820),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("library_search_input"),
            )
        }

        // Quick Library Hub Row (Pinned shortcuts with stats)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "hub_liked") {
                LibraryQuickHubTile(
                    icon = Icons.Default.Favorite,
                    title = "Liked Songs",
                    subtitle = "$likedCount track${if (likedCount == 1) "" else "s"}",
                    accent = Color(0xFF5E35B1),
                    tag = "library_hub_liked",
                    onClick = { navController.navigate(Routes.Liked.route) },
                )
            }
            item(key = "hub_downloads") {
                LibraryQuickHubTile(
                    icon = Icons.Default.Download,
                    title = "Downloads",
                    subtitle = "$downloadedCount offline",
                    accent = Color(0xFF1E88E5),
                    tag = "library_hub_downloads",
                    onClick = { navController.navigate(Routes.Downloads.route) },
                )
            }
            item(key = "hub_history") {
                LibraryQuickHubTile(
                    icon = Icons.Default.History,
                    title = "History",
                    subtitle = "Recent plays",
                    accent = Color(0xFF00897B),
                    tag = "library_hub_history",
                    onClick = { navController.navigate(Routes.History.route) },
                )
            }
        }

        // Library Filter Chips bar
        LibraryFilterChips(
            selectedFilter = selectedFilter,
            isDownloadedOnly = isDownloadedOnly,
            onFilterSelected = { libraryViewModel.setFilterType(it) },
            onToggleDownloaded = { libraryViewModel.toggleDownloadedOnly() },
            onClearFilters = { libraryViewModel.clearFilters() }
        )

        // Sort + Grid/List switch bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 2.dp, bottom = 4.dp)
        ) {
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showSortMenu = true }
                        .testTag("library_sort_button")
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = "Sort library",
                        tint = AppPalette,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = sortOrder.label,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                    modifier = Modifier.background(Color(0xFF1F212B)),
                ) {
                    LibrarySortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = order.label,
                                    color = if (sortOrder == order) AppPalette else Color.White,
                                    fontWeight = if (sortOrder == order) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            onClick = {
                                libraryViewModel.setSortOrder(order)
                                showSortMenu = false
                            },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = {
                    gridView = !gridView
                    setLibraryGridView(context, gridView)
                },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("library_view_toggle"),
            ) {
                Icon(
                    painter = painterResource(if (gridView) R.drawable.ic_view_list else R.drawable.ic_view_grid),
                    contentDescription = if (gridView) "Show as list" else "Show as grid",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        when (entries) {
            is Response.Loading -> LibrarySkeleton(PaddingValues(0.dp))
            is Response.Success -> {
                val rawEntries = (entries as Response.Success).data
                val cleanQuery = searchQuery.trim()
                val filteredEntries = remember(rawEntries, selectedFilter, isDownloadedOnly, cleanQuery, sortOrder, context) {
                    val base = rawEntries.filter { entry ->
                        val matchesCategory = when (selectedFilter) {
                            LibraryFilterType.ALL -> true
                            LibraryFilterType.PLAYLISTS -> entry.isPlaylist
                            LibraryFilterType.ALBUMS -> !entry.isPlaylist && entry.spotifyId != Api.HomeCache.LIKED_SONGS_ID && entry.spotifyId != Api.HomeCache.DOWNLOADS_ID
                            LibraryFilterType.ARTISTS -> false
                        }
                        val matchesDownload = if (isDownloadedOnly) isLibraryEntryDownloaded(context, entry) else true
                        val matchesQuery = if (cleanQuery.isBlank()) {
                            true
                        } else {
                            entry.name.contains(cleanQuery, ignoreCase = true) ||
                                entry.artists.contains(cleanQuery, ignoreCase = true) ||
                                entry.subtitle.contains(cleanQuery, ignoreCase = true)
                        }
                        matchesCategory && matchesDownload && matchesQuery
                    }
                    when (sortOrder) {
                        LibrarySortOrder.RECENT -> base
                        LibrarySortOrder.ALPHABETICAL -> base.sortedBy { it.name.lowercase() }
                        LibrarySortOrder.CREATOR -> base.sortedBy { it.artists.ifBlank { it.subtitle }.lowercase() }
                        LibrarySortOrder.DOWNLOADED_FIRST -> base.sortedByDescending { isLibraryEntryDownloaded(context, it) }
                    }
                }
                val filteredArtists = remember(followedArtists, selectedFilter, cleanQuery, sortOrder) {
                    val baseArtists = if (selectedFilter == LibraryFilterType.PLAYLISTS || selectedFilter == LibraryFilterType.ALBUMS) {
                        emptyList()
                    } else if (cleanQuery.isBlank()) {
                        followedArtists
                    } else {
                        followedArtists.filter { it.name.contains(cleanQuery, ignoreCase = true) }
                    }
                    if (sortOrder == LibrarySortOrder.ALPHABETICAL) {
                        baseArtists.sortedBy { it.name.lowercase() }
                    } else {
                        baseArtists
                    }
                }
                val showHistoryTile = selectedFilter == LibraryFilterType.ALL && !isDownloadedOnly && cleanQuery.isBlank()

                if (gridView) {
                    LibraryGridScreen(
                        padding = PaddingValues(0.dp),
                        entries = filteredEntries,
                        followedArtists = filteredArtists,
                        navController = navController,
                        showHistoryTile = showHistoryTile,
                        onClearFilters = { libraryViewModel.clearFilters() }
                    )
                } else {
                    SumUpLibraryScreen(
                        padding = PaddingValues(0.dp),
                        entries = filteredEntries,
                        followedArtists = filteredArtists,
                        navController = navController,
                        showHistoryTile = showHistoryTile,
                        onClearFilters = { libraryViewModel.clearFilters() }
                    )
                }
            }
            else -> Box(modifier = Modifier.padding(20.dp, 100.dp)) { Snackbar(showMessage = "Couldn't load your library") }
        }
    }
}

@Composable
private fun LibraryQuickHubTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    tag: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(accent.copy(alpha = 0.28f), Color(0xFF191B23))
                )
            )
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .testTag(tag)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.32f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(subtitle, color = Color(0xFFB3BAC8), fontSize = 11.sp, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun AccountAvatar(url: String, size: androidx.compose.ui.unit.Dp) {
    GlideImage(
        modifier = Modifier.size(size).clip(CircleShape),
        model = url,
        contentScale = ContentScale.Crop,
        contentDescription = ""
    )
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun LibraryListEntry(entry: LibraryEntry, navController: NavController) {
    Row(
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(20.dp, 6.dp).clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null,
        ) { openLibraryEntry(entry, navController) },
    ) {
        GlideImage(
            modifier = Modifier.size(55.dp).clip(RoundedCornerShape(if (entry.isPlaylist) 8.dp else 5.dp)),
            model = entry.coverUri,
            contentScale = ContentScale.Crop,
            contentDescription = entry.name,
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(entry.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry.isLocal) Icon(Icons.Default.PhoneAndroid, "Local Storage", tint = Color(0xFF1ED760), modifier = Modifier.size(14.dp).padding(end = 3.dp))
                Text(entry.subtitle, color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun SumUpLibraryScreen(
    padding: PaddingValues,
    entries: List<LibraryEntry>,
    followedArtists: List<com.music.spotui.data.entity.ArtistsModel>,
    navController: NavController,
    showHistoryTile: Boolean = true,
    onClearFilters: () -> Unit = {}
) {
    if (entries.isEmpty() && followedArtists.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "No items found",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Try clearing your active filters or adding more items to your library.",
                color = Color.Gray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF1ED760))
                    .clickable { onClearFilters() }
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Clear filters",
                    color = Color.Black,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        return
    }
    val specialEntries = entries.filter { it.spotifyId == Api.HomeCache.LIKED_SONGS_ID || it.spotifyId == Api.HomeCache.DOWNLOADS_ID }
    val allPlaylistEntries = entries.filter { it.isPlaylist && it !in specialEntries }
    val youtubePlaylistEntries = allPlaylistEntries.filter { it.spotifyId.startsWith("youtube_api_") }
    val playlistEntries = allPlaylistEntries - youtubePlaylistEntries.toSet()
    val albumEntries = entries.filter { !it.isPlaylist && it !in specialEntries }
    var playlistsExpanded by rememberSaveable { mutableStateOf(true) }
    var albumsExpanded by rememberSaveable { mutableStateOf(true) }
    var youtubeMusicExpanded by rememberSaveable(youtubePlaylistEntries.isNotEmpty()) { mutableStateOf(true) }
    val libraryContext = LocalContext.current
    val ytSyncScope = rememberCoroutineScope()
    val ytSyncRev by com.music.spotui.data.youtube.YouTubeMusicSync.syncRevision.collectAsState()
    val ytStatus = remember(ytSyncRev) { com.music.spotui.data.youtube.YouTubeMusicSync.status(libraryContext) }
    var ytSyncBusy by remember { mutableStateOf(false) }
    var showYtUrlImportDialog by remember { mutableStateOf(false) }
    var ytUrlInput by remember { mutableStateOf("") }

    if (showYtUrlImportDialog) {
        AlertDialog(
            onDismissRequest = { if (!ytSyncBusy) showYtUrlImportDialog = false },
            title = { Text("Import YouTube Playlist", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Paste any YouTube or YouTube Music playlist URL or ID (e.g. PL... or OLAK...) to import its tracks natively into SpotUI.",
                        color = Color(0xFFB3B3B3),
                        fontSize = 12.sp,
                    )
                    OutlinedTextField(
                        value = ytUrlInput,
                        onValueChange = { ytUrlInput = it },
                        singleLine = true,
                        placeholder = { Text("https://music.youtube.com/playlist?list=...", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF1ED760),
                            unfocusedBorderColor = Color(0xFF444444),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = ytUrlInput.isNotBlank() && !ytSyncBusy,
                    onClick = {
                        ytSyncBusy = true
                        ytSyncScope.launch {
                            val result = com.music.spotui.data.youtube.YouTubeMusicSync.importPlaylistUrl(libraryContext, ytUrlInput)
                            ytSyncBusy = false
                            showYtUrlImportDialog = false
                            ytUrlInput = ""
                            val msg = result.fold(
                                onSuccess = { it.detail },
                                onFailure = { it.message ?: "Import failed" },
                            )
                            android.widget.Toast.makeText(libraryContext, msg, android.widget.Toast.LENGTH_LONG).show()
                        }
                    },
                ) {
                    Text(if (ytSyncBusy) "Importing…" else "Import", color = Color(0xFF1ED760), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(enabled = !ytSyncBusy, onClick = { showYtUrlImportDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF1A1A20),
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .background(Color(0xFF0E0E13))
    ) {
        item(key = "lib_top_spacer") { Spacer(modifier = Modifier.height(8.dp)) }
        if (showHistoryTile) {
            // Listening history & stats entry.
            item(key = "lib_history_tile") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp, 6.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { navController.navigate(Routes.History.route) }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(55.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF27856A)),
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(text = "Listening history", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Your plays and stats", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        itemsIndexed(
            items = specialEntries,
            key = { i, entry -> "spec_${entry.spotifyId}_${entry.name}_$i" },
        ) { _, entry ->
            LibraryListEntry(entry, navController)
        }
        item(key = "hdr_playlists") {
            Spacer(Modifier.height(8.dp))
            CollapsibleLibraryHeader("Playlists", playlistEntries.size, playlistsExpanded) { playlistsExpanded = !playlistsExpanded }
        }
        if (playlistsExpanded) {
            itemsIndexed(
                items = playlistEntries,
                key = { i, entry -> "pl_${entry.spotifyId}_${entry.name}_$i" },
            ) { _, entry -> LibraryListEntry(entry, navController) }
        }
        item(key = "hdr_albums") {
            Spacer(Modifier.height(8.dp))
            CollapsibleLibraryHeader("Albums", albumEntries.size, albumsExpanded) { albumsExpanded = !albumsExpanded }
        }
        if (albumsExpanded) {
            itemsIndexed(
                items = albumEntries,
                key = { i, entry -> "alb_${entry.spotifyId}_${entry.name}_$i" },
            ) { _, entry -> LibraryListEntry(entry, navController) }
        }
        item(key = "hdr_ytm") {
            Spacer(Modifier.height(8.dp))
            CollapsibleLibraryHeader("YouTube Music", youtubePlaylistEntries.size, youtubeMusicExpanded) { youtubeMusicExpanded = !youtubeMusicExpanded }
        }
        if (youtubeMusicExpanded) {
            itemsIndexed(
                items = youtubePlaylistEntries,
                key = { i, entry -> "ytm_${entry.spotifyId}_${entry.name}_$i" },
            ) { _, entry -> LibraryListEntry(entry, navController) }
            item(key = "ytm_native_sync_hub") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp, 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF171B24))
                        .border(1.dp, Color(0xFF283042), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("YouTube Music Native Sync", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = when {
                                    ytStatus.sessionReady || ytStatus.connected -> ytStatus.detail.ifBlank { "Connected — tap Sync YT Playlists to pull playlists, Liked Music & albums into your library." }
                                    else -> "Sign in once to sync all your playlists, Liked Music & saved albums natively right into SpotUI."
                                },
                                color = if (ytStatus.sessionReady || ytStatus.connected) Color(0xFF81C784) else Color(0xFFB3B3B3),
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                if (!ytStatus.sessionReady && !ytStatus.connected) {
                                    libraryContext.startActivity(android.content.Intent(libraryContext, YouTubeMusicWebActivity::class.java))
                                } else {
                                    ytSyncBusy = true
                                    ytSyncScope.launch {
                                        val res = if (ytStatus.sessionReady) {
                                            com.music.spotui.data.youtube.YouTubeMusicSync.syncFromSignedInSession(libraryContext)
                                        } else {
                                            com.music.spotui.data.youtube.YouTubeMusicSync.syncNow(libraryContext)
                                        }
                                        ytSyncBusy = false
                                        val toastMsg = res.fold(
                                            onSuccess = { it.detail },
                                            onFailure = { it.message ?: "YouTube sync failed" },
                                        )
                                        android.widget.Toast.makeText(libraryContext, toastMsg, android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = !ytSyncBusy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1ED760),
                                contentColor = Color.Black,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("ytm_native_sync_button"),
                        ) {
                            if (ytSyncBusy) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.Black)
                                Spacer(Modifier.width(6.dp))
                                Text("Syncing…", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (ytStatus.sessionReady || ytStatus.connected) "Sync YT Playlists" else "Sign in to Sync",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = { showYtUrlImportDialog = true },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("ytm_import_url_button"),
                        ) {
                            Icon(Icons.Default.Link, contentDescription = "Import URL", tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("URL", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = {
                                libraryContext.startActivity(android.content.Intent(libraryContext, YouTubeMusicWebActivity::class.java))
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("ytm_account_button"),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "YouTube Music Account", tint = Color(0xFFFF3D4D), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
        // ── Artists the user follows on Spotify ──
        if (followedArtists.isNotEmpty()) {
            item(key = "hdr_followed_artists") {
                Text(
                    text = "Artists you follow",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(20.dp, 16.dp, 20.dp, 4.dp),
                )
            }
            itemsIndexed(
                items = followedArtists,
                key = { i, artist -> "artist_${artist.id}_${artist.name}_$i" },
            ) { _, artist ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp, 6.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { navController.navigate(artistRoute(artist.name, artist.id)) }
                ) {
                    GlideImage(
                        modifier = Modifier
                            .size(55.dp)
                            .clip(CircleShape),
                        model = artist.coverUri,
                        contentScale = ContentScale.Crop,
                        contentDescription = artist.name
                    )
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(text = artist.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(text = "Artist", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        item(key = "lib_bottom_spacer") { Spacer(modifier = Modifier.height(160.dp)) }
    }
}

private fun openLibraryEntry(entry: LibraryEntry, navController: NavController) {
    if (entry.spotifyId == Api.HomeCache.LIKED_SONGS_ID) navController.navigate(Routes.Liked.route)
    else if (entry.spotifyId == Api.HomeCache.DOWNLOADS_ID) navController.navigate(Routes.Downloads.route)
    else if (entry.isPlaylist) navController.navigate(playlistRoute(entry.spotifyId, entry.name))
    else navController.navigate(albumRoute(entry.name, entry.artists))
}

/**
 * Grid layout of the same library content: 3 columns of square covers with the
 * title/subtitle underneath, like Spotify's "Grid" library view. Followed
 * artists render as circles under their own full-width header.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun LibraryGridScreen(
    padding: PaddingValues,
    entries: List<LibraryEntry>,
    followedArtists: List<com.music.spotui.data.entity.ArtistsModel>,
    navController: NavController,
    showHistoryTile: Boolean = true,
    onClearFilters: () -> Unit = {}
) {
    if (entries.isEmpty() && followedArtists.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "No items found",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Try clearing your active filters or adding more items to your library.",
                color = Color.Gray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF1ED760))
                    .clickable { onClearFilters() }
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Clear filters",
                    color = Color.Black,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .background(Color(0xFF0E0E13)),
        contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, 130.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (showHistoryTile) {
            // Listening history & stats entry, as a tile.
            item(key = "grid_history_tile") {
                Column(
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { navController.navigate(Routes.History.route) }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF27856A)),
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "Listening history", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(text = "Your plays and stats", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        gridItemsIndexed(
            items = entries,
            key = { i, entry -> "grid_${entry.spotifyId}_${entry.name}_$i" },
        ) { _, entry ->
            Column(
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { openLibraryEntry(entry, navController) }
            ) {
                GlideImage(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(if (entry.isPlaylist) 6.dp else 4.dp)),
                    model = entry.coverUri,
                    contentScale = ContentScale.Crop,
                    contentDescription = entry.name
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = entry.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (entry.isLocal) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = "Local Storage",
                            tint = AppPalette,
                            modifier = Modifier
                                .size(12.dp)
                                .padding(end = 3.dp)
                        )
                    }
                    Text(text = entry.subtitle, color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (followedArtists.isNotEmpty()) {
            item(key = "grid_artists_hdr", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "Artists you follow",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            gridItemsIndexed(
                items = followedArtists,
                key = { i, artist -> "grid_artist_${artist.id}_${artist.name}_$i" },
            ) { _, artist ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { navController.navigate(artistRoute(artist.name, artist.id)) }
                ) {
                    GlideImage(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(CircleShape),
                        model = artist.coverUri,
                        contentScale = ContentScale.Crop,
                        contentDescription = artist.name
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = artist.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    Text(text = "Artist", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun LibrarySkeleton(padding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .background(Color(0xFF0E0E13))
    ) {
        Spacer(modifier = Modifier.height(10.dp))
        repeat(8) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp, 6.dp)
            ) {
                Box(modifier = Modifier.size(55.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF1E1E1E)))
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Box(modifier = Modifier.height(14.dp).width(160.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF1E1E1E)))
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(modifier = Modifier.height(11.dp).width(90.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF1E1E1E)))
                }
            }
        }
    }
}

@Composable
private fun AccountRow(label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Text(
        text = label,
        color = tint,
        fontSize = 15.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(20.dp, 16.dp)
    )
}
