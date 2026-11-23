package com.music.spotui.ui.screens

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Message
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.metrolist.spotify.SpotifyAuth
import com.metrolist.spotify.models.SpotifyInternalToken
import com.music.spotui.audio.UiSoundEngine
import com.music.spotui.data.api.SpotifySession
import com.music.spotui.data.api.SpotifySync
import com.music.spotui.di.SpotifyWebPlayer
import com.music.spotui.ui.navigation.Routes
import com.music.spotui.util.DevConsoleManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

private const val SPOTIFY_LOGIN_URL = SpotifyAuth.LOGIN_URL
private const val SPOTIFY_OPEN_URL = "https://open.spotify.com/"

/**
 * Researched & Hardened Spotify Music Sync & Login Screen (v2.5.4):
 * - Mirrors SpotUI's `YouTubeMusicWebActivity` flow: sign in below (or open your live Spotify web
 *   player) and tap **Sync All** to capture `sp_dc` + `#session` credentials and import every
 *   playlist, album, liked song, followed artist, and Home feed natively into SpotUI.
 * - Strips Android WebView's `; wv` & `Version/4.0` UA markers by default (`Chrome Mobile`) so
 *   `accounts.spotify.com` does not reject `/login/code/send` with HTTP 400.
 * - Includes UA switcher (`Chrome Mobile`, `Desktop Chrome`, `System Default`), Auto-Recover banner,
 *   `Paste Cookie` tab, `Open in Chrome`, and live `Logs`.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SpotifyLoginScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val logs by DevConsoleManager.logs.collectAsStateWithLifecycle()
    val faults by DevConsoleManager.faults.collectAsStateWithLifecycle()

    val initialSessionLinked = remember { SpotifySync.isSessionReady(context) }
    var isAccountLinked by remember { mutableStateOf(initialSessionLinked) }
    var initialWebUrl by remember {
        mutableStateOf(if (initialSessionLinked) SPOTIFY_OPEN_URL else SPOTIFY_LOGIN_URL)
    }

    var selectedTab by remember { mutableStateOf(LoginTab.WEB) }
    var uaProfile by remember { mutableStateOf(LoginUaProfile.CLEAN_MOBILE) }
    var isProcessing by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var syncSummaryText by remember { mutableStateOf(SpotifySync.lastSyncDetail) }
    var statusMessage by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }
    var recoveryHint by remember { mutableStateOf<String?>(null) }

    var isPageLoading by remember { mutableStateOf(true) }
    var pageProgress by remember { mutableIntStateOf(0) }
    var currentHost by remember {
        mutableStateOf(if (initialSessionLinked) "open.spotify.com" else "accounts.spotify.com")
    }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var popupWebView by remember { mutableStateOf<WebView?>(null) }
    var cookieText by remember { mutableStateOf("") }
    var showLogsDialog by remember { mutableStateOf(false) }

    val tokenFetchStarted = remember { AtomicBoolean(false) }

    LaunchedEffect(Unit) {
        DevConsoleManager.logAuth(
            "Spotify Music Sync opened (linked=$isAccountLinked, defaultUA=${uaProfile.label})",
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { popupWebView?.destroy() }
            popupWebView = null
        }
    }

    val navigateToHome: () -> Unit = {
        runCatching { SpotifyWebPlayer.refreshLogin(context) }
        runCatching {
            navController.navigate(Routes.Home.route) {
                popUpTo(Routes.Login.route) { inclusive = true }
            }
        }
    }

    fun runNativeLibrarySync(autoTriggered: Boolean) {
        if (isSyncing || isProcessing) return
        scope.launch {
            isSyncing = true
            hasError = false
            statusMessage = "Importing Spotify playlists, liked songs & artists natively into SpotUI…"
            val res = SpotifySync.syncAllFromSession(context)
            isSyncing = false
            res.onSuccess { summary ->
                isAccountLinked = true
                syncSummaryText = summary.detail
                statusMessage = summary.detail
                UiSoundEngine.playToggleSound(context, true)
                Toast.makeText(
                    context,
                    if (autoTriggered) {
                        "Signed in & synced to SpotUI Library!"
                    } else {
                        "Synced to SpotUI Library! Tap Done or return to Library to play."
                    },
                    Toast.LENGTH_LONG,
                ).show()
            }.onFailure { err ->
                hasError = true
                statusMessage = err.message ?: "Sync failed — tap Sync All to retry"
                if (!autoTriggered) {
                    Toast.makeText(
                        context,
                        err.message ?: "Spotify sync failed",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    fun tryCaptureSessionFromWeb(
        currentUrl: String?,
        source: String,
        preCapturedToken: SpotifyInternalToken? = null,
        stayOnSyncScreen: Boolean = false,
    ): Boolean {
        val sweep = sweepSpotifyCookies(currentUrl)
        val dc = sweep.spDc
        if (dc.isNullOrBlank()) return false
        if (!tokenFetchStarted.compareAndSet(false, true)) return true
        recoveryHint = null
        processLoginSession(
            context = context,
            spDc = dc,
            spKey = sweep.spKey,
            source = source,
            preCapturedToken = preCapturedToken,
            autoSyncLibrary = true,
            scope = scope,
            setProcessing = { isProcessing = it },
            setStatus = { statusMessage = it },
            setError = { hasError = it },
            onComplete = { ok ->
                if (!ok) {
                    tokenFetchStarted.set(false)
                } else {
                    isAccountLinked = true
                    SpotifySync.lastSyncDetail.takeIf { it.isNotBlank() }?.let { syncSummaryText = it }
                    runCatching { SpotifyWebPlayer.refreshLogin(context) }
                }
            },
            onSuccess = { summary ->
                if (!summary.isNullOrBlank()) syncSummaryText = summary
                if (!stayOnSyncScreen && !initialSessionLinked) {
                    Toast.makeText(
                        context,
                        "Signed in & synced Spotify library!",
                        Toast.LENGTH_SHORT,
                    ).show()
                    navigateToHome()
                } else {
                    Toast.makeText(
                        context,
                        "Synced to SpotUI Library! Tap Done or return to Library to play.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
        )
        return true
    }

    fun tryReadSessionScriptAndVerify(
        view: WebView?,
        url: String?,
        source: String,
        stayOnSyncScreen: Boolean = false,
        onNoCookie: (() -> Unit)? = null,
    ) {
        if (tokenFetchStarted.get()) {
            if (isAccountLinked && stayOnSyncScreen) {
                runNativeLibrarySync(autoTriggered = false)
            }
            return
        }
        val wv = view
        if (wv != null && url.orEmpty().contains("open.spotify.com", ignoreCase = true)) {
            runCatching {
                wv.evaluateJavascript(
                    "(function(){var el=document.getElementById('session');return el?el.textContent:'';})();",
                ) { raw ->
                    val cleanedJson = raw?.trim()?.let { str ->
                        if (str.startsWith("\"") && str.endsWith("\"") && str.length >= 2) {
                            str.substring(1, str.length - 1)
                                .replace("\\\"", "\"")
                                .replace("\\\\", "\\")
                        } else {
                            str
                        }
                    }.orEmpty()
                    val parsedToken = SpotifyAuth.parseSessionScriptJson(cleanedJson)
                    val captured = tryCaptureSessionFromWeb(url, source, parsedToken, stayOnSyncScreen)
                    if (!captured) onNoCookie?.invoke()
                }
            }.onFailure {
                val captured = tryCaptureSessionFromWeb(url, source, stayOnSyncScreen = stayOnSyncScreen)
                if (!captured) onNoCookie?.invoke()
            }
        } else {
            val captured = tryCaptureSessionFromWeb(url, source, stayOnSyncScreen = stayOnSyncScreen)
            if (!captured) onNoCookie?.invoke()
        }
    }

    fun triggerSyncAll() {
        UiSoundEngine.playTapChime(context)
        val liveSweep = sweepSpotifyCookies(webViewRef?.url)
        val storedDc = SpotifySession.spDc(context)
        when {
            !liveSweep.spDc.isNullOrBlank() && (!isAccountLinked || liveSweep.spDc != storedDc) -> {
                tokenFetchStarted.set(false)
                tryReadSessionScriptAndVerify(
                    view = webViewRef,
                    url = webViewRef?.url,
                    source = "SyncAllButton",
                    stayOnSyncScreen = true,
                )
            }
            SpotifySync.isSessionReady(context) -> {
                runNativeLibrarySync(autoTriggered = false)
            }
            else -> {
                tryReadSessionScriptAndVerify(
                    view = webViewRef,
                    url = webViewRef?.url,
                    source = "SyncAllButton",
                    stayOnSyncScreen = true,
                    onNoCookie = {
                        Toast.makeText(
                            context,
                            "Sign in to Spotify below first (or use Paste Cookie).",
                            Toast.LENGTH_LONG,
                        ).show()
                    },
                )
            }
        }
    }

    fun applyUaProfileAndReload(nextProfile: LoginUaProfile, targetUrl: String = initialWebUrl) {
        uaProfile = nextProfile
        recoveryHint = null
        val wv = webViewRef ?: return
        val nextUa = resolveLoginUserAgent(context, nextProfile)
        wv.settings.userAgentString = nextUa
        DevConsoleManager.logAuth("Switched WebView UA profile -> ${nextProfile.label}")
        wv.loadUrl(targetUrl)
    }

    // Continuous background CookieManager poll only when signed out (catches fresh sign-ins/SSO)
    LaunchedEffect(selectedTab, isProcessing, isAccountLinked) {
        if (selectedTab != LoginTab.WEB || initialSessionLinked) return@LaunchedEffect
        while (isActive) {
            delay(1_200L)
            if (!isProcessing && !isSyncing && !tokenFetchStarted.get()) {
                val urlNow = webViewRef?.url
                if (urlNow.orEmpty().contains("open.spotify.com", ignoreCase = true)) {
                    tryReadSessionScriptAndVerify(webViewRef, urlNow, "PollOpenSpotify")
                } else {
                    tryCaptureSessionFromWeb(urlNow, "CookiePoll")
                }
            }
        }
    }

    BackHandler {
        when {
            popupWebView != null -> {
                runCatching { popupWebView?.destroy() }
                popupWebView = null
            }
            selectedTab == LoginTab.WEB && webViewRef?.canGoBack() == true -> {
                webViewRef?.goBack()
            }
            else -> navigateToHome()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(LOGIN_BACKGROUND_DARK))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Header (matches YouTubeMusicWebActivity Sync UX)
            val headerSubtitle = when {
                isSyncing || isProcessing -> statusMessage.ifBlank { "Importing Spotify playlists natively into SpotUI…" }
                syncSummaryText.isNotBlank() -> syncSummaryText
                isAccountLinked -> "Account linked · Tap Sync All to import playlists natively"
                else -> "Sign in below to sync playlists & liked songs natively into SpotUI"
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF181818), Color(LOGIN_BACKGROUND_DARK)),
                        ),
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(LOGIN_SPOTIFY_GREEN)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "SpotUI",
                        tint = Color.Black,
                        modifier = Modifier.size(21.dp),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Spotify Music Sync",
                        color = Color.White,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        text = headerSubtitle,
                        color = if (hasError) Color(0xFFFF9EA6) else Color(0xFFB3B3B3),
                        fontSize = 11.5.sp,
                        lineHeight = 14.sp,
                        maxLines = 2,
                    )
                }

                Button(
                    onClick = { triggerSyncAll() },
                    enabled = !isProcessing && !isSyncing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1DB954),
                        contentColor = Color.Black,
                        disabledContainerColor = Color(0xFF1DB954).copy(alpha = 0.45f),
                        disabledContentColor = Color.Black,
                    ),
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("login_sync_all_button"),
                ) {
                    if (isSyncing || isProcessing) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = if (isSyncing || isProcessing) "Syncing…" else "Sync All",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                IconButton(
                    onClick = {
                        UiSoundEngine.playTapChime(context)
                        showLogsDialog = true
                    },
                    modifier = Modifier.size(48.dp).testTag("login_logs_button"),
                ) {
                    Icon(Icons.Default.BugReport, contentDescription = "Login Logs", tint = Color(LOGIN_TEXT_SECONDARY))
                }

                TextButton(
                    onClick = {
                        UiSoundEngine.playTapChime(context)
                        DevConsoleManager.logAuth(
                            if (isAccountLinked) "User returned to SpotUI after Spotify sync" else "User continued as Guest",
                        )
                        navigateToHome()
                    },
                    enabled = !isProcessing && !isSyncing,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("login_guest_button"),
                ) {
                    Text(
                        text = if (isAccountLinked) "Done" else "Guest",
                        color = Color(LOGIN_SPOTIFY_GREEN),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                }
            }

            // Expressive Pill Tab Switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(LOGIN_CARD_DARK))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SimpleTabChip(
                    label = "Web Sign-In",
                    icon = Icons.Default.Language,
                    selected = selectedTab == LoginTab.WEB,
                    onClick = {
                        UiSoundEngine.playTapChime(context)
                        selectedTab = LoginTab.WEB
                    },
                    modifier = Modifier.weight(1f).testTag("login_tab_web"),
                )
                SimpleTabChip(
                    label = "Paste Cookie",
                    icon = Icons.Default.Key,
                    selected = selectedTab == LoginTab.COOKIE,
                    onClick = {
                        UiSoundEngine.playTapChime(context)
                        selectedTab = LoginTab.COOKIE
                    },
                    modifier = Modifier.weight(1f).testTag("login_tab_cookie"),
                )
            }

            if (statusMessage.isNotBlank()) {
                Surface(
                    color = if (hasError) Color(0xFF2B161A) else Color(0xFF14271D),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                color = Color(LOGIN_SPOTIFY_GREEN),
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                        Text(
                            text = statusMessage,
                            color = if (hasError) Color(0xFFFF9EA6) else Color(0xFFA7F3D0),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            // Tab 1: Hardened Chrome-UA WebView
            if (selectedTab == LoginTab.WEB) {
                Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    // Browser Controls & Host Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        IconButton(
                            onClick = { webViewRef?.goBack() },
                            modifier = Modifier.size(40.dp).testTag("login_web_back"),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = {
                                recoveryHint = null
                                webViewRef?.reload()
                            },
                            modifier = Modifier.size(40.dp).testTag("login_web_reload"),
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = {
                                UiSoundEngine.playTapChime(context)
                                resetSpotifyWebSession(webViewRef) {
                                    tokenFetchStarted.set(false)
                                    statusMessage = ""
                                    hasError = false
                                    recoveryHint = null
                                    webViewRef?.loadUrl(SPOTIFY_LOGIN_URL)
                                }
                            },
                            modifier = Modifier.size(40.dp).testTag("login_web_reset_cookies"),
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Reset Session", tint = Color(0xFFFFB74D), modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SPOTIFY_LOGIN_URL)))
                                }.onFailure {
                                    Toast.makeText(context, "Unable to open browser", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(40.dp).testTag("login_open_browser"),
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = "Open in Chrome", tint = Color(0xFF9ED4FF), modifier = Modifier.size(18.dp))
                        }

                        Text(
                            text = currentHost,
                            color = Color(LOGIN_TEXT_SECONDARY),
                            fontSize = 11.sp,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )

                        Button(
                            onClick = { triggerSyncAll() },
                            enabled = !isProcessing && !isSyncing,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(LOGIN_SPOTIFY_GREEN), contentColor = Color.Black),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.heightIn(min = 40.dp).testTag("login_verify_session_button"),
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Verify & Sync", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }

                    // Page Destination + User-Agent Profile Strip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            color = Color(LOGIN_CARD_DARK),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .clickable {
                                    UiSoundEngine.playTapChime(context)
                                    initialWebUrl = SPOTIFY_LOGIN_URL
                                    webViewRef?.loadUrl(SPOTIFY_LOGIN_URL)
                                }
                                .testTag("login_dest_signin"),
                        ) {
                            Text(
                                text = "Sign-In Page",
                                color = Color.White,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                        Surface(
                            color = Color(LOGIN_CARD_DARK),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .clickable {
                                    UiSoundEngine.playTapChime(context)
                                    initialWebUrl = SPOTIFY_OPEN_URL
                                    webViewRef?.loadUrl(SPOTIFY_OPEN_URL)
                                }
                                .testTag("login_dest_library"),
                        ) {
                            Text(
                                text = "Web Player",
                                color = Color(LOGIN_SPOTIFY_GREEN),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                        Text("· UA:", color = Color(LOGIN_TEXT_SECONDARY), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        LoginUaProfile.entries.forEach { profile ->
                            val selected = uaProfile == profile
                            Surface(
                                color = if (selected) Color(LOGIN_SPOTIFY_GREEN) else Color(LOGIN_CARD_DARK),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .clickable {
                                        UiSoundEngine.playTapChime(context)
                                        applyUaProfileAndReload(profile)
                                    }
                                    .testTag("login_ua_${profile.name.lowercase()}"),
                            ) {
                                Text(
                                    text = profile.label,
                                    color = if (selected) Color.Black else Color.White,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }

                    // Inline Auto-Recover Banner on HTTP 400 / Auth Rejection / StorageAccess Failure
                    AnimatedVisibility(visible = !recoveryHint.isNullOrBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF281A16))
                                .border(1.dp, Color(0xFFFF9800).copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFFB74D), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = recoveryHint.orEmpty(),
                                    color = Color(0xFFFFDFB0),
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                )
                            }
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                val nextUa = if (uaProfile == LoginUaProfile.DESKTOP_CHROME) {
                                    LoginUaProfile.CLEAN_MOBILE
                                } else {
                                    LoginUaProfile.DESKTOP_CHROME
                                }
                                OutlinedButton(
                                    onClick = { applyUaProfileAndReload(nextUa) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.heightIn(min = 34.dp),
                                ) {
                                    Text("Try ${nextUa.label}", color = Color.White, fontSize = 10.5.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        resetSpotifyWebSession(webViewRef) {
                                            recoveryHint = null
                                            webViewRef?.loadUrl(SPOTIFY_LOGIN_URL)
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.heightIn(min = 34.dp),
                                ) {
                                    Text("Reset Session", color = Color(0xFFFFB74D), fontSize = 10.5.sp)
                                }
                                Button(
                                    onClick = {
                                        selectedTab = LoginTab.COOKIE
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SPOTIFY_OPEN_URL)))
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(LOGIN_SPOTIFY_GREEN), contentColor = Color.Black),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.heightIn(min = 34.dp),
                                ) {
                                    Text("Chrome + Cookie", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    if (isPageLoading) {
                        LinearProgressIndicator(
                            progress = { (pageProgress / 100f).coerceIn(0.05f, 1f) },
                            color = Color(LOGIN_SPOTIFY_GREEN),
                            trackColor = Color(0xFF202330),
                            modifier = Modifier.fillMaxWidth().height(3.dp),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp)),
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                    )
                                    configureLoginWebViewSettings(ctx, this, uaProfile)

                                    val cm = CookieManager.getInstance()
                                    cm.setAcceptCookie(true)
                                    cm.setAcceptThirdPartyCookies(this, true)

                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                            pageProgress = newProgress
                                            isPageLoading = newProgress in 1..99
                                        }

                                        override fun onPermissionRequest(request: PermissionRequest?) {
                                            request ?: return
                                            val protectedMedia = PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID
                                            if (request.resources.contains(protectedMedia)) {
                                                request.grant(arrayOf(protectedMedia))
                                            } else {
                                                request.deny()
                                            }
                                        }

                                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                            val msg = consoleMessage?.message().orEmpty()
                                            if (msg.contains("requestStorageAccess", ignoreCase = true) ||
                                                msg.contains("Oops", ignoreCase = true) ||
                                                consoleMessage?.messageLevel() == ConsoleMessage.MessageLevel.ERROR
                                            ) {
                                                DevConsoleManager.log(
                                                    DevConsoleManager.LogLevel.WARNING,
                                                    "SpotifyWebJS",
                                                    msg.take(220),
                                                    category = DevConsoleManager.LogCategory.AUTH,
                                                )
                                            }
                                            return false
                                        }

                                        override fun onCreateWindow(
                                            view: WebView?,
                                            isDialog: Boolean,
                                            isUserGesture: Boolean,
                                            resultMsg: Message?,
                                        ): Boolean {
                                            DevConsoleManager.logAuth("Opening OAuth popup window (${uaProfile.label})")
                                            val popup = WebView(ctx).apply {
                                                configureLoginWebViewSettings(ctx, this, uaProfile)
                                                cm.setAcceptCookie(true)
                                                cm.setAcceptThirdPartyCookies(this, true)
                                                webChromeClient = object : WebChromeClient() {
                                                    override fun onCloseWindow(window: WebView?) {
                                                        runCatching { window?.destroy() }
                                                        popupWebView = null
                                                    }
                                                }
                                                webViewClient = object : WebViewClient() {
                                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                                        val u = request?.url?.toString().orEmpty()
                                                        if (u.startsWith("http://") || u.startsWith("https://")) return false
                                                        if (u.startsWith("intent://")) {
                                                            runCatching {
                                                                val intent = Intent.parseUri(u, Intent.URI_INTENT_SCHEME)
                                                                val fallback = intent.getStringExtra("browser_fallback_url")
                                                                if (!fallback.isNullOrBlank()) {
                                                                    view?.loadUrl(fallback)
                                                                } else {
                                                                    ctx.startActivity(intent)
                                                                }
                                                            }
                                                        }
                                                        return true
                                                    }

                                                    override fun onPageFinished(view: WebView?, url: String?) {
                                                        if (url != null && url.contains("spotify.com", ignoreCase = true)) {
                                                            if (tryCaptureSessionFromWeb(url, "OAuthPopup")) {
                                                                runCatching { destroy() }
                                                                popupWebView = null
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            popupWebView = popup
                                            val transport = resultMsg?.obj as? WebView.WebViewTransport
                                            transport?.webView = popup
                                            resultMsg?.sendToTarget()
                                            return true
                                        }
                                    }

                                    webViewClient = object : WebViewClient() {
                                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                            isPageLoading = true
                                            runCatching { Uri.parse(url).host }.getOrNull()?.let { currentHost = it }
                                        }

                                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                            val u = request?.url?.toString().orEmpty()
                                            if (u.startsWith("http://") || u.startsWith("https://")) return false
                                            if (u.startsWith("intent://")) {
                                                runCatching {
                                                    val intent = Intent.parseUri(u, Intent.URI_INTENT_SCHEME)
                                                    val fallback = intent.getStringExtra("browser_fallback_url")
                                                    if (!fallback.isNullOrBlank()) {
                                                        view?.loadUrl(fallback)
                                                    } else {
                                                        ctx.startActivity(intent)
                                                    }
                                                }
                                            }
                                            return true
                                        }

                                        override fun onReceivedHttpError(
                                            view: WebView?,
                                            request: WebResourceRequest?,
                                            errorResponse: WebResourceResponse?,
                                        ) {
                                            val code = errorResponse?.statusCode ?: return
                                            val reqUrl = request?.url?.toString().orEmpty()
                                            val mainFrame = request?.isForMainFrame ?: false
                                            if (code >= 400 && reqUrl.contains("spotify", ignoreCase = true)) {
                                                DevConsoleManager.log(
                                                    DevConsoleManager.LogLevel.ERROR,
                                                    "SpotifyWebHTTP",
                                                    "HTTP $code on ${reqUrl.take(120)} (mainFrame=$mainFrame, ua=${uaProfile.label})",
                                                    category = DevConsoleManager.LogCategory.AUTH,
                                                )
                                                if (code == 400 && reqUrl.contains("/login", ignoreCase = true)) {
                                                    recoveryHint = "Spotify returned HTTP 400 (${uaProfile.label}). Try switching UA to Desktop Chrome, Reset Session, or sign in via Chrome + Cookie."
                                                    DevConsoleManager.reportComponentFault(
                                                        component = "SpotifyLogin",
                                                        action = "HTTP_400",
                                                        reason = "HTTP 400 on ${reqUrl.take(80)} [UA=${uaProfile.label}]",
                                                        autoFixHint = "Switch UA profile, reset session, or use Chrome + Paste Cookie.",
                                                    )
                                                }
                                            }
                                        }

                                        override fun onReceivedError(
                                            view: WebView?,
                                            request: WebResourceRequest?,
                                            error: WebResourceError?,
                                        ) {
                                            if (request?.isForMainFrame == true) {
                                                val desc = error?.description?.toString().orEmpty()
                                                DevConsoleManager.logError("SpotifyWebError", "Main frame error: $desc")
                                            }
                                        }

                                        override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                                            DevConsoleManager.logError("SpotifyWebView", "Render process crashed (didCrash=${detail?.didCrash()})")
                                            return true
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            isPageLoading = false
                                            runCatching { cm.flush() }
                                            if (url != null) {
                                                DevConsoleManager.logAuth("Loaded: ${url.take(120)}")
                                                if (!initialSessionLinked) {
                                                    tryReadSessionScriptAndVerify(view, url, "WebView")
                                                }
                                                // Non-invasive check on accounts.spotify.com for visible Oops banner
                                                if (url.contains("accounts.spotify.com", ignoreCase = true)) {
                                                    runCatching {
                                                        view?.evaluateJavascript(
                                                            "(function(){var t=(document.body&&document.body.innerText)||'';return t.indexOf('Something went wrong')>-1||t.indexOf('something went wrong')>-1?'OOPS':'OK';})();",
                                                        ) { flag ->
                                                            if (flag?.contains("OOPS") == true) {
                                                                recoveryHint = "Spotify showed 'Something went wrong'. Switch UA to Desktop Chrome, Reset Session, or sign in via Chrome + Cookie."
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    webViewRef = this
                                    loadUrl(initialWebUrl)
                                }
                            },
                        )
                    }
                }
            } else {
                // Tab 2: Fast Cookie Paste / Chrome Handoff Helper
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color(LOGIN_CARD_DARK))
                            .border(1.dp, Color(LOGIN_SPOTIFY_GREEN).copy(alpha = 0.25f), RoundedCornerShape(22.dp))
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "sp_dc Cookie Sign-In",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            "Sign into open.spotify.com in Chrome or Desktop browser (bypasses all WebView/SSO blocks), copy your `sp_dc` cookie, Cookie-Editor JSON, or cURL header, and paste below.",
                            color = Color(LOGIN_TEXT_SECONDARY),
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    UiSoundEngine.playTapChime(context)
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SPOTIFY_LOGIN_URL)))
                                    }.onFailure {
                                        Toast.makeText(context, "Unable to open browser", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f).heightIn(min = 44.dp).testTag("cookie_open_chrome_button"),
                            ) {
                                Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = Color(0xFF9ED4FF), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Open in Chrome", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                            OutlinedButton(
                                onClick = {
                                    UiSoundEngine.playTapChime(context)
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("SpotifyLoginUrl", SPOTIFY_LOGIN_URL))
                                    Toast.makeText(context, "Copied Spotify login link", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.heightIn(min = 44.dp).testTag("cookie_copy_url_button"),
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(LOGIN_SPOTIFY_GREEN), modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Copy URL", color = Color.White, fontSize = 11.5.sp)
                            }
                        }

                        OutlinedTextField(
                            value = cookieText,
                            onValueChange = { cookieText = it },
                            label = { Text("sp_dc value, Cookie header, cURL, or JSON", color = Color(LOGIN_TEXT_SECONDARY)) },
                            minLines = 3,
                            maxLines = 5,
                            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(LOGIN_SPOTIFY_GREEN),
                                unfocusedBorderColor = Color(0xFF34384A),
                                focusedContainerColor = Color(0xFF10121A),
                                unfocusedContainerColor = Color(0xFF10121A),
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("cookie_input_field"),
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = cm.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                                    if (clip.isNotBlank()) {
                                        cookieText = clip
                                    } else {
                                        Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("cookie_paste_button"),
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Paste", color = Color.White, fontWeight = FontWeight.SemiBold)
                            }

                            Button(
                                enabled = !isProcessing && cookieText.isNotBlank(),
                                onClick = {
                                    val (dc, key) = parseSpotifyCookiesFromText(cookieText)
                                    if (dc.length < 16) {
                                        hasError = true
                                        statusMessage = "Invalid or empty sp_dc token"
                                        return@Button
                                    }
                                    processLoginSession(
                                        context = context,
                                        spDc = dc,
                                        spKey = key,
                                        source = "CookiePaste",
                                        autoSyncLibrary = true,
                                        scope = scope,
                                        setProcessing = { isProcessing = it },
                                        setStatus = { statusMessage = it },
                                        setError = { hasError = it },
                                        onComplete = { ok ->
                                            if (ok) {
                                                isAccountLinked = true
                                                SpotifySync.lastSyncDetail.takeIf { it.isNotBlank() }?.let { syncSummaryText = it }
                                                runCatching { SpotifyWebPlayer.refreshLogin(context) }
                                            }
                                        },
                                        onSuccess = { summary ->
                                            if (!summary.isNullOrBlank()) syncSummaryText = summary
                                            Toast.makeText(
                                                context,
                                                "Connected & synced Spotify library!",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                            navigateToHome()
                                        },
                                    )
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(LOGIN_SPOTIFY_GREEN), contentColor = Color.Black),
                                modifier = Modifier.weight(1.3f).heightIn(min = 48.dp).testTag("cookie_submit_button"),
                            ) {
                                Text("Connect & Sync", fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }
        }

        // OAuth Popup Dialog
        popupWebView?.let { popup ->
            Dialog(
                onDismissRequest = {
                    runCatching { popup.destroy() }
                    popupWebView = null
                },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = Color(LOGIN_CARD_DARK),
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Account Sign-In", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            IconButton(
                                onClick = {
                                    runCatching { popup.destroy() }
                                    popupWebView = null
                                },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                            }
                        }
                        AndroidView(
                            factory = { popup },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        if (showLogsDialog) {
            LoginDiagnosticsDialog(
                logs = logs,
                faults = faults,
                onDismiss = { showLogsDialog = false },
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun configureLoginWebViewSettings(
    context: Context,
    webView: WebView,
    profile: LoginUaProfile,
) {
    webView.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        javaScriptCanOpenWindowsAutomatically = true
        setSupportMultipleWindows(true)
        loadsImagesAutomatically = true
        useWideViewPort = true
        loadWithOverviewMode = true
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        userAgentString = resolveLoginUserAgent(context, profile)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            safeBrowsingEnabled = false
        }
    }
}

@Composable
private fun SimpleTabChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color(LOGIN_SPOTIFY_GREEN) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) Color.Black else Color(LOGIN_TEXT_SECONDARY),
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = if (selected) Color.Black else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
