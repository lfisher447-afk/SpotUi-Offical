@file:Suppress("TooManyFunctions", "LongMethod")

package com.music.spotui.ui.screens.SpotifyLogin.module

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Message
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.metrolist.spotify.SpotifyAuth
import com.music.spotui.ui.screens.SpotifyLogin.helper.LOGIN_BACKGROUND_DARK
import com.music.spotui.ui.screens.SpotifyLogin.helper.LOGIN_CARD_DARK
import com.music.spotui.ui.screens.SpotifyLogin.helper.LOGIN_SPOTIFY_GREEN
import com.music.spotui.ui.screens.SpotifyLogin.helper.LOGIN_TEXT_SECONDARY
import com.music.spotui.ui.screens.SpotifyLogin.helper.LoginBrowserBookmarkStore
import com.music.spotui.ui.screens.SpotifyLogin.helper.LoginUaProfile
import com.music.spotui.ui.screens.SpotifyLogin.helper.resolveLoginUserAgent
import com.music.spotui.ui.screens.SpotifyLogin.page.DeezerLoginActivity
import kotlinx.coroutines.delay
import timber.log.Timber
import java.util.UUID

internal data class AuthBrowserTab(
    val id: String = UUID.randomUUID().toString().take(8),
    val url: String = SpotifyAuth.LOGIN_URL,
    val title: String = "Spotify Sign In",
    val uaProfile: LoginUaProfile = LoginUaProfile.DESKTOP_CHROME,
    val incognito: Boolean = false,
    val reloadNonce: Int = 0,
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun AuthBrowserEnginePanel(
    context: Context,
    localPortalUrl: String?,
    isProcessingAuth: Boolean,
    rejectedSpotifyCookie: String?,
    onSpotifySessionCaptured: (spDc: String, spKey: String, activeWebView: WebView?) -> Unit,
    onYoutubeCookieCaptured: (cookieHeader: String) -> Unit,
    onDeezerArlCaptured: (arl: String) -> Unit,
    onSwitchToLocalUrl: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val initialHome = remember { LoginBrowserBookmarkStore.getHomeUrl(context) }

    val tabs = remember {
        mutableStateListOf(
            AuthBrowserTab(
                id = "tab_main",
                url = initialHome,
                title = LoginBrowserBookmarkStore.extractHostLabel(initialHome),
                incognito = LoginBrowserBookmarkStore.isIncognito(context),
            ),
        )
    }
    var activeTabId by remember { mutableStateOf("tab_main") }
    val activeTab = tabs.firstOrNull { it.id == activeTabId } ?: tabs.first()

    val webViews = remember { mutableMapOf<String, WebView>() }
    val activeWebView: WebView? = webViews[activeTab.id]

    var bookmarks by remember { mutableStateOf(LoginBrowserBookmarkStore.loadBookmarks(context)) }
    var history by remember { mutableStateOf(LoginBrowserBookmarkStore.loadHistory(context)) }

    var addressText by remember(activeTab.id, activeTab.url) { mutableStateOf(activeTab.url) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isLoadingPage by remember { mutableStateOf(true) }
    var loadingProgress by remember { mutableFloatStateOf(0f) }
    var hasPageError by remember { mutableStateOf(false) }
    var pageErrorMessage by remember { mutableStateOf("") }

    var showBookmarksDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showInspectorDialog by remember { mutableStateOf(false) }
    var showFindBar by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var desktopZoomScale by remember { mutableIntStateOf(100) }
    val consoleLogs = remember { mutableStateListOf<String>() }

    var spDcDetected by remember { mutableStateOf(false) }
    var spKeyDetected by remember { mutableStateOf(false) }
    var ytmDetected by remember { mutableStateOf(false) }
    var deezerDetected by remember { mutableStateOf(false) }
    var lastSavedYtm by remember { mutableStateOf("") }
    var lastSavedDeezer by remember { mutableStateOf("") }

    fun appendLog(line: String) {
        if (consoleLogs.size > 40) consoleLogs.removeAt(consoleLogs.lastIndex)
        consoleLogs.add(0, line)
    }

    fun updateActiveTab(transform: (AuthBrowserTab) -> AuthBrowserTab) {
        val idx = tabs.indexOfFirst { it.id == activeTab.id }
        if (idx >= 0) tabs[idx] = transform(tabs[idx])
    }

    fun navigateTo(rawInput: String, openInNewTab: Boolean = false) {
        val targetUrl = LoginBrowserBookmarkStore.normalizeBrowserInput(rawInput)
        focusManager.clearFocus()
        hasPageError = false
        isLoadingPage = true
        if (openInNewTab) {
            val newTab = AuthBrowserTab(
                url = targetUrl,
                title = LoginBrowserBookmarkStore.extractHostLabel(targetUrl),
                uaProfile = activeTab.uaProfile,
                incognito = activeTab.incognito,
            )
            tabs.add(newTab)
            activeTabId = newTab.id
            addressText = targetUrl
        } else {
            addressText = targetUrl
            updateActiveTab { it.copy(url = targetUrl) }
            val currentWv = webViews[activeTab.id]
            if (currentWv != null) {
                currentWv.loadUrl(targetUrl)
            } else {
                updateActiveTab { it.copy(reloadNonce = it.reloadNonce + 1) }
            }
        }
    }

    fun clearAllBrowserData() {
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
            activeWebView?.clearCache(true)
            activeWebView?.clearHistory()
        }
        spDcDetected = false
        spKeyDetected = false
        ytmDetected = false
        deezerDetected = false
        appendLog("Cleared cookies, storage, and WebView cache")
        updateActiveTab { it.copy(reloadNonce = it.reloadNonce + 1) }
    }

    // Periodically inspect cookies for Spotify, YouTube Music, and Deezer while browsing
    LaunchedEffect(activeTab.id, activeTab.url) {
        while (true) {
            val spDc = readBrowserCookie("sp_dc", SPOTIFY_COOKIE_DOMAINS)
            val spKey = readBrowserCookie("sp_key", SPOTIFY_COOKIE_DOMAINS).orEmpty()
            spDcDetected = !spDc.isNullOrBlank()
            spKeyDetected = spKey.isNotBlank()
            if (!spDc.isNullOrBlank() && spDc != rejectedSpotifyCookie && !isProcessingAuth) {
                onSpotifySessionCaptured(spDc, spKey, activeWebView)
            }

            val ytmCookie = runCatching { CookieManager.getInstance().getCookie("https://music.youtube.com") }.getOrNull().orEmpty()
            ytmDetected = ytmCookie.contains("SAPISID=") || ytmCookie.contains("__Secure-3PAPISID=")
            if (ytmDetected && ytmCookie != lastSavedYtm && activeTab.url.contains("youtube.com", ignoreCase = true)) {
                lastSavedYtm = ytmCookie
                onYoutubeCookieCaptured(ytmCookie)
            }

            val deezerCookie = runCatching { CookieManager.getInstance().getCookie("https://www.deezer.com") }.getOrNull()
            val arl = DeezerLoginActivity.extractArlFromCookieHeader(deezerCookie)
            deezerDetected = !arl.isNullOrBlank()
            if (!arl.isNullOrBlank() && arl != lastSavedDeezer && activeTab.url.contains("deezer.com", ignoreCase = true)) {
                lastSavedDeezer = arl
                onDeezerArlCaptured(arl)
            }

            canGoBack = activeWebView?.canGoBack() == true
            canGoForward = activeWebView?.canGoForward() == true
            delay(1100)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViews.values.forEach { wv ->
                runCatching {
                    wv.webViewClient = WebViewClient()
                    wv.webChromeClient = null
                    wv.stopLoading()
                    wv.destroy()
                }
            }
            webViews.clear()
        }
    }

    val isCurrentBookmarked = LoginBrowserBookmarkStore.isBookmarked(bookmarks, activeTab.url)
    val pinnedBookmarks = remember(bookmarks, localPortalUrl) {
        bookmarks.filter { it.pinned }.map { bm ->
            if (bm.id == "bm_local_portal" && !localPortalUrl.isNullOrBlank()) {
                bm.copy(url = localPortalUrl)
            } else {
                bm
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 1. Multi-Tab Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0A0D14))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            tabs.forEach { tab ->
                val selected = tab.id == activeTab.id
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) Color(0xFF192235) else Color(0xFF111521),
                    border = BorderStroke(
                        1.dp,
                        if (selected) Color(LOGIN_SPOTIFY_GREEN) else Color(0xFF232C3F),
                    ),
                    modifier = Modifier
                        .height(36.dp)
                        .clickable {
                            activeTabId = tab.id
                            addressText = tab.url
                            hasPageError = false
                        }
                        .testTag("browser_tab_${tab.id}"),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 10.dp, end = if (tabs.size > 1) 4.dp else 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = if (tab.incognito) "Incognito · ${tab.title}" else tab.title,
                            color = if (selected) Color.White else Color(LOGIN_TEXT_SECONDARY),
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(105.dp),
                        )
                        if (tabs.size > 1) {
                            IconButton(
                                onClick = {
                                    val closingIdx = tabs.indexOfFirst { it.id == tab.id }
                                    webViews.remove(tab.id)?.let { wv ->
                                        runCatching { wv.stopLoading(); wv.destroy() }
                                    }
                                    tabs.removeAll { it.id == tab.id }
                                    if (activeTabId == tab.id && tabs.isNotEmpty()) {
                                        activeTabId = tabs[closingIdx.coerceIn(0, tabs.lastIndex)].id
                                    }
                                },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close Tab", tint = Color(LOGIN_TEXT_SECONDARY), modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            IconButton(
                onClick = {
                    val url = initialHome
                    val newTab = AuthBrowserTab(
                        url = url,
                        title = LoginBrowserBookmarkStore.extractHostLabel(url),
                        uaProfile = activeTab.uaProfile,
                        incognito = activeTab.incognito,
                    )
                    tabs.add(newTab)
                    activeTabId = newTab.id
                },
                modifier = Modifier.size(48.dp).testTag("browser_new_tab_button"),
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Browser Tab", tint = Color(LOGIN_SPOTIFY_GREEN))
            }
        }

        // 2. Navigation & Omnibox Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(LOGIN_CARD_DARK))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { activeWebView?.goBack() },
                enabled = canGoBack,
                modifier = Modifier.size(48.dp).testTag("browser_back_button"),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = if (canGoBack) Color.White else Color(0xFF4C556B),
                )
            }
            IconButton(
                onClick = { activeWebView?.goForward() },
                enabled = canGoForward,
                modifier = Modifier.size(48.dp).testTag("browser_forward_button"),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Forward",
                    tint = if (canGoForward) Color.White else Color(0xFF4C556B),
                )
            }

            OutlinedTextField(
                value = addressText,
                onValueChange = { addressText = it },
                singleLine = true,
                leadingIcon = {
                    val isLocal = addressText.startsWith("http://127.") || addressText.startsWith("http://localhost")
                    Icon(
                        imageVector = if (isLocal) Icons.Default.Http else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (isLocal || addressText.startsWith("https://")) Color(LOGIN_SPOTIFY_GREEN) else Color(0xFFFFB74D),
                        modifier = Modifier.size(16.dp),
                    )
                },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (isCurrentBookmarked) {
                                LoginBrowserBookmarkStore.findByUrl(bookmarks, activeTab.url)?.let { existing ->
                                    bookmarks = LoginBrowserBookmarkStore.removeBookmark(context, existing.id)
                                    Toast.makeText(context, "Bookmark removed", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                bookmarks = LoginBrowserBookmarkStore.addOrUpdateBookmark(
                                    context = context,
                                    title = activeTab.title,
                                    url = activeTab.url,
                                    folder = "Bookmarks",
                                    pinned = true,
                                )
                                Toast.makeText(context, "Bookmarked ${activeTab.title}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.size(40.dp).testTag("browser_star_bookmark_button"),
                    ) {
                        Icon(
                            imageVector = if (isCurrentBookmarked) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Bookmark This Page",
                            tint = if (isCurrentBookmarked) Color(0xFFFFD54F) else Color(LOGIN_TEXT_SECONDARY),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { navigateTo(addressText) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(LOGIN_SPOTIFY_GREEN),
                    unfocusedBorderColor = Color(0xFF2D374E),
                    focusedContainerColor = Color(0xFF0A0D14),
                    unfocusedContainerColor = Color(0xFF0A0D14),
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("browser_omnibox_field"),
            )

            IconButton(
                onClick = {
                    if (isLoadingPage) {
                        activeWebView?.stopLoading()
                        isLoadingPage = false
                    } else {
                        hasPageError = false
                        isLoadingPage = true
                        activeWebView?.reload() ?: updateActiveTab { it.copy(reloadNonce = it.reloadNonce + 1) }
                    }
                },
                modifier = Modifier.size(48.dp).testTag("browser_reload_button"),
            ) {
                Icon(
                    imageVector = if (isLoadingPage) Icons.Default.Close else Icons.Default.Refresh,
                    contentDescription = if (isLoadingPage) "Stop Loading" else "Reload",
                    tint = Color.White,
                )
            }
        }

        // 3. Engine Controls + Bookmarks / History / Find / Inspector Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF101521))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconButton(
                onClick = { navigateTo(LoginBrowserBookmarkStore.getHomeUrl(context)) },
                modifier = Modifier.size(48.dp).testTag("browser_home_button"),
            ) {
                Icon(Icons.Default.Home, contentDescription = "Home", tint = Color.White, modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = { showBookmarksDialog = true },
                modifier = Modifier.size(48.dp).testTag("browser_bookmarks_button"),
            ) {
                Icon(Icons.Default.Bookmarks, contentDescription = "Open Bookmarks", tint = Color(LOGIN_SPOTIFY_GREEN), modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = { showHistoryDialog = true },
                modifier = Modifier.size(48.dp).testTag("browser_history_button"),
            ) {
                Icon(Icons.Default.History, contentDescription = "Open History", tint = Color(0xFF8BB2FF), modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = { showFindBar = !showFindBar },
                modifier = Modifier.size(48.dp).testTag("browser_find_button"),
            ) {
                Icon(Icons.Default.FindInPage, contentDescription = "Find in Page", tint = if (showFindBar) Color(LOGIN_SPOTIFY_GREEN) else Color.White, modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = { showInspectorDialog = true },
                modifier = Modifier.size(48.dp).testTag("browser_inspector_button"),
            ) {
                Icon(
                    Icons.Default.Cookie,
                    contentDescription = "Session & Cookie Inspector",
                    tint = if (spDcDetected || ytmDetected || deezerDetected) Color(LOGIN_SPOTIFY_GREEN) else Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }

            LoginUaProfile.entries.forEach { profile ->
                val label = when (profile) {
                    LoginUaProfile.DESKTOP_CHROME -> "Desktop"
                    LoginUaProfile.MOBILE_CHROME -> "Mobile"
                    LoginUaProfile.SYSTEM_DEFAULT -> "System"
                }
                FilterChip(
                    selected = activeTab.uaProfile == profile,
                    onClick = {
                        updateActiveTab { it.copy(uaProfile = profile, reloadNonce = it.reloadNonce + 1) }
                    },
                    label = { Text(label, fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF143521),
                        selectedLabelColor = Color(LOGIN_SPOTIFY_GREEN),
                    ),
                )
            }

            FilterChip(
                selected = activeTab.incognito,
                onClick = {
                    val nextIncognito = !activeTab.incognito
                    LoginBrowserBookmarkStore.setIncognito(context, nextIncognito)
                    if (nextIncognito) {
                        clearAllBrowserData()
                    }
                    updateActiveTab { it.copy(incognito = nextIncognito, reloadNonce = it.reloadNonce + 1) }
                },
                leadingIcon = { Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(14.dp)) },
                label = { Text(if (activeTab.incognito) "Incognito ON" else "Incognito", fontSize = 10.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF2B2040),
                    selectedLabelColor = Color(0xFFD0B2FF),
                ),
            )

            FilterChip(
                selected = desktopZoomScale != 100,
                onClick = {
                    desktopZoomScale = when (desktopZoomScale) {
                        100 -> 85
                        85 -> 115
                        else -> 100
                    }
                    activeWebView?.settings?.textZoom = desktopZoomScale
                },
                label = { Text("Zoom $desktopZoomScale%", fontSize = 10.sp) },
            )
        }

        // 4. Pinned Bookmarks Quick-Launch Bar
        if (pinnedBookmarks.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D111B))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Default.BookmarkBorder, contentDescription = null, tint = Color(LOGIN_TEXT_SECONDARY), modifier = Modifier.size(14.dp))
                pinnedBookmarks.forEach { bm ->
                    val isCurrent = activeTab.url.trimEnd('/').equals(bm.url.trimEnd('/'), ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isCurrent) Color(0xFF143521) else Color(0xFF171E2E),
                        border = BorderStroke(1.dp, if (isCurrent) Color(LOGIN_SPOTIFY_GREEN) else Color(0xFF273147)),
                        modifier = Modifier
                            .clickable { navigateTo(bm.url) }
                            .testTag("pinned_bookmark_${bm.id}"),
                    ) {
                        Text(
                            text = bm.title,
                            color = if (isCurrent) Color(LOGIN_SPOTIFY_GREEN) else Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }

        // 5. Optional Find-In-Page Bar
        AnimatedVisibility(visible = showFindBar) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF131A29))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedTextField(
                    value = findQuery,
                    onValueChange = { q ->
                        findQuery = q
                        activeWebView?.findAllAsync(q)
                    },
                    placeholder = { Text("Find in page...") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(LOGIN_SPOTIFY_GREEN),
                        unfocusedBorderColor = Color(0xFF34415C),
                    ),
                    modifier = Modifier.weight(1f).height(48.dp),
                )
                IconButton(onClick = { activeWebView?.findNext(false) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Match", tint = Color.White)
                }
                IconButton(onClick = { activeWebView?.findNext(true) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Match", tint = Color.White)
                }
                IconButton(
                    onClick = {
                        showFindBar = false
                        findQuery = ""
                        activeWebView?.clearMatches()
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close Find", tint = Color.White)
                }
            }
        }

        if (isLoadingPage && !isProcessingAuth) {
            LinearProgressIndicator(
                progress = { loadingProgress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = Color(LOGIN_SPOTIFY_GREEN),
                trackColor = Color.Transparent,
            )
        }

        // 6. Embedded Chromium WebView Engine
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            key(activeTab.id, activeTab.reloadNonce) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        FrameLayout(ctx).apply {
                            try {
                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                val wv = WebView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                    )
                                    webViews[activeTab.id] = this
                                    cookieManager.setAcceptThirdPartyCookies(this, true)
                                    setBackgroundColor(android.graphics.Color.BLACK)
                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        databaseEnabled = true
                                        loadWithOverviewMode = true
                                        useWideViewPort = true
                                        builtInZoomControls = true
                                        displayZoomControls = false
                                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                        javaScriptCanOpenWindowsAutomatically = true
                                        setSupportMultipleWindows(true)
                                        cacheMode = if (activeTab.incognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
                                        textZoom = desktopZoomScale
                                        userAgentString = resolveLoginUserAgent(ctx, activeTab.uaProfile)
                                    }
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                            loadingProgress = newProgress / 100f
                                            if (newProgress >= 100) isLoadingPage = false
                                        }

                                        override fun onReceivedTitle(view: WebView?, title: String?) {
                                            val clean = title?.trim().orEmpty()
                                            if (clean.isNotBlank()) {
                                                updateActiveTab { it.copy(title = clean) }
                                            }
                                        }

                                        override fun onCreateWindow(
                                            view: WebView?,
                                            isDialog: Boolean,
                                            isUserGesture: Boolean,
                                            resultMsg: Message?,
                                        ): Boolean {
                                            val hit = view?.hitTestResult
                                            val extraUrl = hit?.extra
                                            if (!extraUrl.isNullOrBlank()) {
                                                navigateTo(extraUrl, openInNewTab = true)
                                                return false
                                            }
                                            return super.onCreateWindow(view, isDialog, isUserGesture, resultMsg)
                                        }

                                        override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                                            appendLog("[${m.messageLevel()}] ${m.message()}")
                                            return true
                                        }
                                    }
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(
                                            view: WebView?,
                                            request: WebResourceRequest?,
                                        ): Boolean {
                                            val target = request?.url?.toString().orEmpty()
                                            if (target.startsWith("http://") || target.startsWith("https://")) {
                                                return false
                                            }
                                            return true
                                        }

                                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                            isLoadingPage = true
                                            hasPageError = false
                                            if (!url.isNullOrBlank()) {
                                                addressText = url
                                                updateActiveTab { it.copy(url = url) }
                                            }
                                            canGoBack = view?.canGoBack() == true
                                            canGoForward = view?.canGoForward() == true
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            isLoadingPage = false
                                            val finalUrl = url ?: activeTab.url
                                            addressText = finalUrl
                                            val title = view?.title?.takeIf { it.isNotBlank() }
                                                ?: LoginBrowserBookmarkStore.extractHostLabel(finalUrl)
                                            updateActiveTab { it.copy(url = finalUrl, title = title) }
                                            if (!activeTab.incognito) {
                                                history = LoginBrowserBookmarkStore.recordHistory(ctx, finalUrl, title)
                                            }
                                            canGoBack = view?.canGoBack() == true
                                            canGoForward = view?.canGoForward() == true
                                        }

                                        override fun onReceivedError(
                                            view: WebView?,
                                            request: WebResourceRequest?,
                                            error: WebResourceError?,
                                        ) {
                                            if (request?.isForMainFrame == true) {
                                                pageErrorMessage = error?.description?.toString() ?: "Connection error"
                                                isLoadingPage = false
                                                hasPageError = true
                                                appendLog("Error: $pageErrorMessage")
                                            }
                                        }
                                    }
                                    loadUrl(activeTab.url)
                                }
                                addView(wv)
                            } catch (failure: RuntimeException) {
                                hasPageError = true
                                isLoadingPage = false
                                pageErrorMessage = "WebView unavailable on device. Use Local URL or Chrome Tab."
                                Timber.w(failure, "AuthBrowserEnginePanel WebView failed")
                            }
                        }
                    },
                    onRelease = { container ->
                        val view = container.getChildAt(0) as? WebView
                        if (view != null) {
                            webViews.entries.removeAll { it.value === view }
                            container.removeAllViews()
                            runCatching {
                                view.webViewClient = WebViewClient()
                                view.webChromeClient = null
                                view.stopLoading()
                                view.destroy()
                            }
                        }
                    },
                )
            }

            if (hasPageError) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(LOGIN_BACKGROUND_DARK)).padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Browser page blocked or offline",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = pageErrorMessage,
                            color = Color(LOGIN_TEXT_SECONDARY),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    hasPageError = false
                                    updateActiveTab { it.copy(reloadNonce = it.reloadNonce + 1) }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF23314B), contentColor = Color.White),
                                modifier = Modifier.height(48.dp),
                            ) {
                                Text("Retry")
                            }
                            Button(
                                onClick = onSwitchToLocalUrl,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(LOGIN_SPOTIFY_GREEN), contentColor = Color.Black),
                                modifier = Modifier.height(48.dp),
                            ) {
                                Text("Use Local URL Login", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBookmarksDialog) {
        BrowserBookmarksDialog(
            context = context,
            bookmarks = bookmarks,
            currentUrl = activeTab.url,
            currentTitle = activeTab.title,
            onOpenUrl = { url, newTab -> navigateTo(url, openInNewTab = newTab) },
            onBookmarksChanged = { bookmarks = it },
            onSetHomeUrl = { home -> LoginBrowserBookmarkStore.setHomeUrl(context, home) },
            onDismiss = { showBookmarksDialog = false },
        )
    }

    if (showHistoryDialog) {
        BrowserHistoryDialog(
            context = context,
            history = history,
            onOpenUrl = { url, newTab -> navigateTo(url, openInNewTab = newTab) },
            onHistoryCleared = { history = emptyList() },
            onDismiss = { showHistoryDialog = false },
        )
    }

    if (showInspectorDialog) {
        BrowserSessionInspectorDialog(
            currentUrl = activeTab.url,
            spDcPresent = spDcDetected,
            spKeyPresent = spKeyDetected,
            ytmCookiePresent = ytmDetected,
            deezerArlPresent = deezerDetected,
            consoleLogs = consoleLogs.toList(),
            onCaptureSpotifyNow = {
                val dc = readBrowserCookie("sp_dc", SPOTIFY_COOKIE_DOMAINS)
                val key = readBrowserCookie("sp_key", SPOTIFY_COOKIE_DOMAINS).orEmpty()
                if (!dc.isNullOrBlank()) {
                    onSpotifySessionCaptured(dc, key, activeWebView)
                    showInspectorDialog = false
                }
            },
            onCaptureYtmNow = {
                val ytmCookie = runCatching { CookieManager.getInstance().getCookie("https://music.youtube.com") }.getOrNull().orEmpty()
                if (ytmCookie.isNotBlank()) {
                    onYoutubeCookieCaptured(ytmCookie)
                    Toast.makeText(context, "YouTube Music session imported", Toast.LENGTH_SHORT).show()
                }
            },
            onCaptureDeezerNow = {
                val deezerCookie = runCatching { CookieManager.getInstance().getCookie("https://www.deezer.com") }.getOrNull()
                val arl = DeezerLoginActivity.extractArlFromCookieHeader(deezerCookie)
                if (!arl.isNullOrBlank()) {
                    onDeezerArlCaptured(arl)
                    Toast.makeText(context, "Deezer ARL imported", Toast.LENGTH_SHORT).show()
                }
            },
            onClearCookies = { clearAllBrowserData() },
            onDismiss = { showInspectorDialog = false },
        )
    }
}

private val SPOTIFY_COOKIE_DOMAINS = listOf(
    "https://open.spotify.com",
    "https://accounts.spotify.com",
    "https://spotify.com",
)

private fun readBrowserCookie(name: String, domains: List<String>): String? = runCatching {
    val cookieManager = CookieManager.getInstance()
    for (domain in domains) {
        val allCookies = cookieManager.getCookie(domain) ?: continue
        val match = allCookies.split(";")
            .mapNotNull {
                val parts = it.trim().split("=", limit = 2)
                if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
            }
            .firstOrNull { it.first == name && it.second.isNotBlank() }
            ?.second
        if (!match.isNullOrBlank()) return@runCatching match
    }
    null
}.getOrNull()
