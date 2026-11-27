package com.music.spotui.ui.screens.SpotifyLogin.module

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.music.spotui.ui.screens.SpotifyLogin.helper.BrowserBookmark
import com.music.spotui.ui.screens.SpotifyLogin.helper.BrowserHistoryEntry
import com.music.spotui.ui.screens.SpotifyLogin.helper.LOGIN_BACKGROUND_DARK
import com.music.spotui.ui.screens.SpotifyLogin.helper.LOGIN_CARD_DARK
import com.music.spotui.ui.screens.SpotifyLogin.helper.LOGIN_SPOTIFY_GREEN
import com.music.spotui.ui.screens.SpotifyLogin.helper.LOGIN_TEXT_SECONDARY
import com.music.spotui.ui.screens.SpotifyLogin.helper.LoginBrowserBookmarkStore

@Composable
internal fun BrowserBookmarksDialog(
    context: Context,
    bookmarks: List<BrowserBookmark>,
    currentUrl: String,
    currentTitle: String,
    onOpenUrl: (String, Boolean) -> Unit,
    onBookmarksChanged: (List<BrowserBookmark>) -> Unit,
    onSetHomeUrl: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFolder by remember { mutableStateOf("All") }
    var showAddEditor by remember { mutableStateOf(false) }
    var showJsonTools by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf<String?>(null) }
    var titleInput by remember { mutableStateOf(currentTitle) }
    var urlInput by remember { mutableStateOf(currentUrl) }
    var folderInput by remember { mutableStateOf("Auth") }
    var pinnedInput by remember { mutableStateOf(true) }
    var importJsonInput by remember { mutableStateOf("") }

    val folders = remember(bookmarks) {
        listOf("All") + bookmarks.map { it.folder }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val filtered = remember(bookmarks, searchQuery, selectedFolder) {
        LoginBrowserBookmarkStore.filterBookmarks(bookmarks, searchQuery, selectedFolder)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 640.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color(LOGIN_BACKGROUND_DARK),
            border = BorderStroke(1.dp, Color(0xFF28334A)),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Bookmark, contentDescription = null, tint = Color(LOGIN_SPOTIFY_GREEN))
                        Column {
                            Text("Browser Bookmarks", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("${bookmarks.size} saved · tap to open or long actions", color = Color(LOGIN_TEXT_SECONDARY), fontSize = 11.sp)
                        }
                    }
                    Row {
                        IconButton(
                            onClick = {
                                editId = null
                                titleInput = currentTitle.ifBlank { LoginBrowserBookmarkStore.extractHostLabel(currentUrl) }
                                urlInput = currentUrl
                                folderInput = "Auth"
                                pinnedInput = true
                                showAddEditor = !showAddEditor
                            },
                            modifier = Modifier.size(48.dp).testTag("browser_bookmark_add_toggle"),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Bookmark", tint = Color(LOGIN_SPOTIFY_GREEN))
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp).testTag("browser_bookmarks_close")) {
                            Icon(Icons.Default.Close, contentDescription = "Close Bookmarks", tint = Color.White)
                        }
                    }
                }

                if (showAddEditor) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(LOGIN_CARD_DARK),
                        border = BorderStroke(1.dp, Color(0xFF2B3852)),
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = if (editId == null) "Save Bookmark" else "Edit Bookmark",
                                color = Color(LOGIN_SPOTIFY_GREEN),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                            )
                            OutlinedTextField(
                                value = titleInput,
                                onValueChange = { titleInput = it },
                                label = { Text("Title") },
                                singleLine = true,
                                colors = browserDialogFieldColors(),
                                modifier = Modifier.fillMaxWidth().testTag("bookmark_title_input"),
                            )
                            OutlinedTextField(
                                value = urlInput,
                                onValueChange = { urlInput = it },
                                label = { Text("URL") },
                                singleLine = true,
                                colors = browserDialogFieldColors(),
                                modifier = Modifier.fillMaxWidth().testTag("bookmark_url_input"),
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = folderInput,
                                    onValueChange = { folderInput = it },
                                    label = { Text("Folder") },
                                    singleLine = true,
                                    colors = browserDialogFieldColors(),
                                    modifier = Modifier.weight(1f),
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = pinnedInput, onCheckedChange = { pinnedInput = it })
                                    Text("Pin", color = Color.White, fontSize = 12.sp)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        val updated = LoginBrowserBookmarkStore.addOrUpdateBookmark(
                                            context = context,
                                            title = titleInput,
                                            url = urlInput,
                                            folder = folderInput,
                                            pinned = pinnedInput,
                                            existingId = editId,
                                        )
                                        onBookmarksChanged(updated)
                                        showAddEditor = false
                                        editId = null
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(LOGIN_SPOTIFY_GREEN),
                                        contentColor = Color.Black,
                                    ),
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("bookmark_save_button"),
                                ) {
                                    Text("Save Bookmark", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { showAddEditor = false; editId = null },
                                    modifier = Modifier.height(48.dp),
                                ) {
                                    Text("Cancel", color = Color.White)
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search bookmarks or URL...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    colors = browserDialogFieldColors(),
                    modifier = Modifier.fillMaxWidth().testTag("bookmark_search_field"),
                )

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    folders.forEach { folder ->
                        FilterChip(
                            selected = selectedFolder == folder,
                            onClick = { selectedFolder = folder },
                            label = { Text(folder, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF143521),
                                selectedLabelColor = Color(LOGIN_SPOTIFY_GREEN),
                            ),
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false).heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(filtered, key = { it.id }) { bm ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(LOGIN_CARD_DARK),
                            border = BorderStroke(1.dp, if (bm.pinned) Color(0xFF284735) else Color(0xFF232C3F)),
                            modifier = Modifier.fillMaxWidth().clickable {
                                onOpenUrl(bm.url, false)
                                onDismiss()
                            },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(bm.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF222A3D)) {
                                            Text(bm.folder, color = Color(LOGIN_TEXT_SECONDARY), fontSize = 9.sp, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                        }
                                    }
                                    Text(bm.url, color = Color(LOGIN_TEXT_SECONDARY), fontFamily = FontFamily.Monospace, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(
                                    onClick = { onBookmarksChanged(LoginBrowserBookmarkStore.togglePin(context, bm.id)) },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PushPin,
                                        contentDescription = "Pin Bookmark",
                                        tint = if (bm.pinned) Color(LOGIN_SPOTIFY_GREEN) else Color(0xFF6B748B),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        onOpenUrl(bm.url, true)
                                        onDismiss()
                                    },
                                ) {
                                    Text("+Tab", fontSize = 11.sp, color = Color(0xFF8BB2FF))
                                }
                                IconButton(
                                    onClick = { onBookmarksChanged(LoginBrowserBookmarkStore.removeBookmark(context, bm.id)) },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Bookmark", tint = Color(0xFFFF7A85), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF242D40))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            onSetHomeUrl(currentUrl)
                            Toast.makeText(context, "Home page updated", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.height(48.dp),
                    ) {
                        Text("Set Current as Home", fontSize = 11.sp, color = Color.White)
                    }
                    OutlinedButton(
                        onClick = { showJsonTools = !showJsonTools },
                        modifier = Modifier.height(48.dp),
                    ) {
                        Text("JSON Backup/Import", fontSize = 11.sp, color = Color.White)
                    }
                    OutlinedButton(
                        onClick = {
                            onBookmarksChanged(LoginBrowserBookmarkStore.resetDefaultBookmarks(context))
                            Toast.makeText(context, "Default bookmarks restored", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.height(48.dp),
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFFFB74D))
                        Spacer(Modifier.width(4.dp))
                        Text("Reset", fontSize = 11.sp, color = Color(0xFFFFB74D))
                    }
                }

                if (showJsonTools) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = importJsonInput,
                            onValueChange = { importJsonInput = it },
                            label = { Text("Paste exported Bookmarks JSON") },
                            maxLines = 3,
                            colors = browserDialogFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val exported = LoginBrowserBookmarkStore.exportBookmarksJson(bookmarks)
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(ClipData.newPlainText("SpotUI Browser Bookmarks", exported))
                                    Toast.makeText(context, "Copied ${bookmarks.size} bookmarks JSON", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f).height(48.dp),
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Copy JSON", fontSize = 12.sp)
                            }
                            Button(
                                onClick = {
                                    LoginBrowserBookmarkStore.importBookmarksJson(context, importJsonInput)
                                        .onSuccess {
                                            onBookmarksChanged(it)
                                            importJsonInput = ""
                                            Toast.makeText(context, "Imported ${it.size} bookmarks", Toast.LENGTH_SHORT).show()
                                        }
                                        .onFailure {
                                            Toast.makeText(context, "Invalid bookmarks JSON", Toast.LENGTH_SHORT).show()
                                        }
                                },
                                enabled = importJsonInput.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(LOGIN_SPOTIFY_GREEN), contentColor = Color.Black),
                                modifier = Modifier.weight(1f).height(48.dp),
                            ) {
                                Text("Import JSON", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun BrowserHistoryDialog(
    context: Context,
    history: List<BrowserHistoryEntry>,
    onOpenUrl: (String, Boolean) -> Unit,
    onHistoryCleared: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).heightIn(max = 540.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color(LOGIN_BACKGROUND_DARK),
            border = BorderStroke(1.dp, Color(0xFF28334A)),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.History, contentDescription = null, tint = Color(0xFF8BB2FF))
                        Text("Browser History (${history.size})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Row {
                        TextButton(
                            onClick = {
                                LoginBrowserBookmarkStore.clearHistory(context)
                                onHistoryCleared()
                            },
                        ) {
                            Text("Clear", color = Color(0xFFFF7A85))
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close History", tint = Color.White)
                        }
                    }
                }

                if (history.isEmpty()) {
                    Text(
                        text = "No pages visited yet in this browser session.",
                        color = Color(LOGIN_TEXT_SECONDARY),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 20.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(history, key = { "${it.url}_${it.visitedAtMs}" }) { item ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(LOGIN_CARD_DARK),
                                modifier = Modifier.fillMaxWidth().clickable {
                                    onOpenUrl(item.url, false)
                                    onDismiss()
                                },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(item.url, color = Color(LOGIN_TEXT_SECONDARY), fontFamily = FontFamily.Monospace, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    TextButton(onClick = { onOpenUrl(item.url, true); onDismiss() }) {
                                        Text("+Tab", fontSize = 11.sp, color = Color(0xFF8BB2FF))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun BrowserSessionInspectorDialog(
    currentUrl: String,
    spDcPresent: Boolean,
    spKeyPresent: Boolean,
    ytmCookiePresent: Boolean,
    deezerArlPresent: Boolean,
    consoleLogs: List<String>,
    onCaptureSpotifyNow: () -> Unit,
    onCaptureYtmNow: () -> Unit,
    onCaptureDeezerNow: () -> Unit,
    onClearCookies: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).heightIn(max = 580.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color(LOGIN_BACKGROUND_DARK),
            border = BorderStroke(1.dp, Color(0xFF28334A)),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Cookie, contentDescription = null, tint = Color(LOGIN_SPOTIFY_GREEN))
                        Text("Live Cookie & Session Inspector", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close Inspector", tint = Color.White)
                    }
                }

                Text(
                    text = "Active URL: $currentUrl",
                    color = Color(LOGIN_TEXT_SECONDARY),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Surface(shape = RoundedCornerShape(12.dp), color = Color(LOGIN_CARD_DARK)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SessionActionRow("Spotify (sp_dc=${if (spDcPresent) "YES" else "NO"}, sp_key=${if (spKeyPresent) "YES" else "NO"})", spDcPresent, "Import Spotify", onCaptureSpotifyNow)
                        SessionActionRow("YouTube Music Cookies (${if (ytmCookiePresent) "DETECTED" else "WAIT"})", ytmCookiePresent, "Save YTM", onCaptureYtmNow)
                        SessionActionRow("Deezer Lossless ARL (${if (deezerArlPresent) "DETECTED" else "WAIT"})", deezerArlPresent, "Save ARL", onCaptureDeezerNow)
                    }
                }

                Text("Recent Console / Auth Events", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF070A10),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp, max = 160.dp),
                ) {
                    LazyColumn(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (consoleLogs.isEmpty()) {
                            item {
                                Text("No console events logged in this tab yet.", color = Color(LOGIN_TEXT_SECONDARY), fontSize = 11.sp)
                            }
                        } else {
                            items(consoleLogs) { line ->
                                Text(line, color = Color(0xFF9DD6B2), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = onClearCookies,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text("Wipe All Browser Cookies & Storage", color = Color(0xFFFF7A85))
                }
            }
        }
    }
}

@Composable
private fun SessionActionRow(
    label: String,
    detected: Boolean,
    buttonLabel: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = if (detected) Color(LOGIN_SPOTIFY_GREEN) else Color(LOGIN_TEXT_SECONDARY),
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = onAction,
            enabled = detected,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(LOGIN_SPOTIFY_GREEN),
                contentColor = Color.Black,
            ),
            modifier = Modifier.height(40.dp),
        ) {
            Text(buttonLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun browserDialogFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = Color(LOGIN_SPOTIFY_GREEN),
    unfocusedBorderColor = Color(0xFF39435B),
    focusedLabelColor = Color(LOGIN_SPOTIFY_GREEN),
    unfocusedLabelColor = Color(LOGIN_TEXT_SECONDARY),
)
