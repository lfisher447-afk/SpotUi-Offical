package com.music.spotui.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.metrolist.spotify.SpotifyAuth
import com.metrolist.spotify.models.SpotifyInternalToken
import com.music.spotui.audio.UiSoundEngine
import com.music.spotui.data.api.Api
import com.music.spotui.data.api.SpotifySession
import com.music.spotui.data.api.SpotifySync
import com.music.spotui.data.api.SpotifyTokenProvider
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
 * Spotify Music Sync & Sign-In Screen:
 * - Primary flow: **Chrome Custom Tabs + Paste `sp_dc` Cookie** (runs login inside Chrome's real
 *   engine where `requestStorageAccess()`, reCAPTCHA, Passkeys, and Google/Apple SSO pass without
 *   Android WebView's `HTTP 400` block on `accounts.spotify.com/login/code/send`).
 * - Includes 1-tap **Open Spotify in Chrome**, a built-in JS **Bookmarklet** copy button,
 *   step-by-step instructions, smart auto-extracting paste box (raw `sp_dc`, Cookie header, cURL,
 *   Cookie-Editor JSON, Netscape `cookies.txt`), and reactive Guest -> Authenticated transitions.
 * - Legacy embedded WebView is preserved strictly behind an opt-in Experimental warning gate.
 */
@Composable
fun SpotifyLoginScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val logs by DevConsoleManager.logs.collectAsStateWithLifecycle()
    val faults by DevConsoleManager.faults.collectAsStateWithLifecycle()
    val providerAuthenticated by SpotifyTokenProvider.isAuthenticated.collectAsStateWithLifecycle()

    val initialSessionLinked = remember { SpotifySync.isSessionReady(context) }
    var isAccountLinked by remember { mutableStateOf(initialSessionLinked) }
    val effectiveLinked = isAccountLinked || providerAuthenticated

    var selectedTab by remember { mutableStateOf(LoginTab.CHROME_COOKIE) }
    var legacyWebViewArmed by remember { mutableStateOf(false) }
    var uaProfile by remember { mutableStateOf(LoginUaProfile.CLEAN_MOBILE) }
    var isProcessing by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var syncSummaryText by remember { mutableStateOf(SpotifySync.lastSyncDetail) }
    var statusMessage by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }

    var legacyWebViewRef by remember { mutableStateOf<WebView?>(null) }
    var cookieText by remember { mutableStateOf("") }
    var showLogsDialog by remember { mutableStateOf(false) }

    val tokenFetchStarted = remember { AtomicBoolean(false) }

    LaunchedEffect(Unit) {
        val ready = SpotifyTokenProvider.syncAuthState(context)
        isAccountLinked = ready
        DevConsoleManager.logAuth(
            "Spotify Music Sync opened (linked=$ready, mode=ChromeCustomTabs+Cookie)",
        )
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

    fun connectWithCookieInput(
        spDc: String,
        spKey: String,
        source: String,
        stayOnSyncScreen: Boolean = false,
    ) {
        processLoginSession(
            context = context,
            spDc = spDc,
            spKey = spKey,
            source = source,
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
                if (!stayOnSyncScreen && !initialSessionLinked) {
                    Toast.makeText(
                        context,
                        "Connected & synced Spotify library!",
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
    ) {
        if (tokenFetchStarted.get()) {
            if (effectiveLinked && stayOnSyncScreen) {
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
                    tryCaptureSessionFromWeb(url, source, parsedToken, stayOnSyncScreen)
                }
            }.onFailure {
                tryCaptureSessionFromWeb(url, source, stayOnSyncScreen = stayOnSyncScreen)
            }
        } else {
            tryCaptureSessionFromWeb(url, source, stayOnSyncScreen = stayOnSyncScreen)
        }
    }

    fun triggerSyncAll() {
        UiSoundEngine.playTapChime(context)
        val (parsedDc, parsedKey) = parseSpotifyCookiesFromText(cookieText)
        when {
            parsedDc.length >= 16 -> {
                connectWithCookieInput(parsedDc, parsedKey, "SyncAllCookieInput", stayOnSyncScreen = true)
            }
            SpotifySync.isSessionReady(context) -> {
                runNativeLibrarySync(autoTriggered = false)
            }
            selectedTab == LoginTab.LEGACY_WEB && legacyWebViewArmed -> {
                val liveSweep = sweepSpotifyCookies(legacyWebViewRef?.url)
                if (!liveSweep.spDc.isNullOrBlank()) {
                    tokenFetchStarted.set(false)
                    tryReadSessionScriptAndVerify(
                        view = legacyWebViewRef,
                        url = legacyWebViewRef?.url,
                        source = "LegacySyncAll",
                        stayOnSyncScreen = true,
                    )
                } else {
                    Toast.makeText(
                        context,
                        "Open Spotify in Chrome and paste your sp_dc cookie first.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
            else -> {
                Toast.makeText(
                    context,
                    "Sign in via Chrome and paste your sp_dc cookie below first.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    LaunchedEffect(selectedTab, legacyWebViewArmed, isProcessing, effectiveLinked) {
        if (selectedTab != LoginTab.LEGACY_WEB || !legacyWebViewArmed || initialSessionLinked) return@LaunchedEffect
        while (isActive) {
            delay(1_200L)
            if (!isProcessing && !isSyncing && !tokenFetchStarted.get()) {
                val urlNow = legacyWebViewRef?.url
                if (urlNow.orEmpty().contains("open.spotify.com", ignoreCase = true)) {
                    tryReadSessionScriptAndVerify(legacyWebViewRef, urlNow, "PollOpenSpotify")
                } else {
                    tryCaptureSessionFromWeb(urlNow, "CookiePoll")
                }
            }
        }
    }

    BackHandler {
        if (selectedTab == LoginTab.LEGACY_WEB && legacyWebViewArmed && legacyWebViewRef?.canGoBack() == true) {
            legacyWebViewRef?.goBack()
        } else {
            navigateToHome()
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
            val headerSubtitle = when {
                isSyncing || isProcessing -> statusMessage.ifBlank { "Importing Spotify playlists natively into SpotUI…" }
                syncSummaryText.isNotBlank() -> syncSummaryText
                effectiveLinked -> "Spotify session linked · Tap Sync All to refresh library"
                else -> "Guest mode · Sign in via Chrome + Paste sp_dc cookie to sync"
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
                            if (effectiveLinked) "User returned to SpotUI after Spotify sync" else "User continued as Guest",
                        )
                        navigateToHome()
                    },
                    enabled = !isProcessing && !isSyncing,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("login_guest_button"),
                ) {
                    Text(
                        text = if (effectiveLinked) "Done" else "Guest",
                        color = Color(LOGIN_SPOTIFY_GREEN),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                }
            }

            // Tab Switcher: Chrome + Cookie (Default) vs Legacy WebView (Experimental)
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
                    label = "Chrome + Cookie",
                    icon = Icons.Default.Key,
                    selected = selectedTab == LoginTab.CHROME_COOKIE,
                    onClick = {
                        UiSoundEngine.playTapChime(context)
                        selectedTab = LoginTab.CHROME_COOKIE
                    },
                    modifier = Modifier.weight(1.1f).testTag("login_tab_cookie"),
                )
                SimpleTabChip(
                    label = "Legacy WebView",
                    icon = Icons.Default.Language,
                    selected = selectedTab == LoginTab.LEGACY_WEB,
                    onClick = {
                        UiSoundEngine.playTapChime(context)
                        selectedTab = LoginTab.LEGACY_WEB
                    },
                    modifier = Modifier.weight(0.9f).testTag("login_tab_web"),
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
                        if (isProcessing || isSyncing) {
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

            if (selectedTab == LoginTab.CHROME_COOKIE) {
                ChromeCookieLoginPane(
                    effectiveLinked = effectiveLinked,
                    cookieText = cookieText,
                    onCookieTextChange = { cookieText = it },
                    isProcessing = isProcessing,
                    isSyncing = isSyncing,
                    onOpenChrome = { openSpotifyInChromeCustomTab(context, SPOTIFY_LOGIN_URL) },
                    onCopyBookmarklet = {
                        UiSoundEngine.playTapChime(context)
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("SpotifySpDcBookmarklet", SPOTIFY_BOOKMARKLET_JS))
                        Toast.makeText(
                            context,
                            "Copied JS bookmarklet to clipboard",
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                    onCopyUrl = {
                        UiSoundEngine.playTapChime(context)
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("SpotifyLoginUrl", SPOTIFY_LOGIN_URL))
                        Toast.makeText(context, "Copied Spotify login link", Toast.LENGTH_SHORT).show()
                    },
                    onPasteClipboard = {
                        UiSoundEngine.playTapChime(context)
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = cm.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                        if (clip.isNotBlank()) {
                            cookieText = clip
                        } else {
                            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onConnectAndSync = { dc, key ->
                        connectWithCookieInput(dc, key, "ChromeCustomTabCookiePaste", stayOnSyncScreen = false)
                    },
                    onValidationError = { msg ->
                        hasError = true
                        statusMessage = msg
                    },
                    onDisconnect = {
                        UiSoundEngine.playTapChime(context)
                        SpotifySession.setSpDc(context, "")
                        Api.HomeCache.clear()
                        SpotifySync.notifyAuthStateChanged("")
                        isAccountLinked = false
                        syncSummaryText = ""
                        statusMessage = "Disconnected Spotify session (Guest mode active)."
                        hasError = false
                    },
                )
            } else {
                LegacyWebViewLoginPane(
                    armed = legacyWebViewArmed,
                    initialSessionLinked = initialSessionLinked,
                    uaProfile = uaProfile,
                    onUaProfileChange = { uaProfile = it },
                    onArmWebView = { legacyWebViewArmed = true },
                    onSwitchToChromeCookie = { selectedTab = LoginTab.CHROME_COOKIE },
                    onTriggerSyncAll = { triggerSyncAll() },
                    isProcessing = isProcessing,
                    isSyncing = isSyncing,
                    webViewRef = legacyWebViewRef,
                    onWebViewCreated = { legacyWebViewRef = it },
                    onTryReadSessionScriptAndVerify = { view, url, source, stay ->
                        tryReadSessionScriptAndVerify(view, url, source, stay)
                    },
                    onTryCaptureSessionFromWeb = { url, source ->
                        tryCaptureSessionFromWeb(url, source)
                    },
                )
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

@Composable
private fun ChromeCookieLoginPane(
    effectiveLinked: Boolean,
    cookieText: String,
    onCookieTextChange: (String) -> Unit,
    isProcessing: Boolean,
    isSyncing: Boolean,
    onOpenChrome: () -> Unit,
    onCopyBookmarklet: () -> Unit,
    onCopyUrl: () -> Unit,
    onPasteClipboard: () -> Unit,
    onConnectAndSync: (String, String) -> Unit,
    onValidationError: (String) -> Unit,
    onDisconnect: () -> Unit,
) {
    val context = LocalContext.current
    val (detectedDc, detectedKey) = remember(cookieText) { parseSpotifyCookiesFromText(cookieText) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Primary Chrome Custom Tabs Launch Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color(LOGIN_CARD_DARK))
                .border(1.dp, Color(LOGIN_SPOTIFY_GREEN).copy(alpha = 0.28f), RoundedCornerShape(22.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Chrome Custom Tabs Sign-In",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Surface(
                    color = if (effectiveLinked) Color(0xFF143521) else Color(0xFF2B2214),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = if (effectiveLinked) "Authenticated" else "Guest Mode",
                        color = if (effectiveLinked) Color(LOGIN_SPOTIFY_GREEN) else Color(0xFFFFB74D),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            Text(
                text = "Signs you in using your real Chrome browser engine so reCAPTCHA, requestStorageAccess(), Email Code, Google & Apple login work cleanly with zero WebView HTTP 400 errors.",
                color = Color(LOGIN_TEXT_SECONDARY),
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )

            Button(
                onClick = {
                    UiSoundEngine.playTapChime(context)
                    onOpenChrome()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(LOGIN_SPOTIFY_GREEN),
                    contentColor = Color.Black,
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .testTag("chrome_login_open_button"),
            ) {
                Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Open Spotify in Chrome", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onCopyBookmarklet,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("copy_bookmarklet_button"),
                ) {
                    Icon(Icons.Default.Code, contentDescription = null, tint = Color(0xFF9ED4FF), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy Bookmarklet", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onCopyUrl,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("cookie_copy_url_button"),
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(LOGIN_SPOTIFY_GREEN), modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy Login URL", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Step-by-Step Cookie Guide Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF131620))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "How to get your sp_dc cookie",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            StepGuideRow(
                number = "1",
                text = "Tap Open Spotify in Chrome above and finish logging in until open.spotify.com loads.",
            )
            StepGuideRow(
                number = "2",
                text = "Copy sp_dc: run the copied JS Bookmarklet on open.spotify.com, or copy it from Chrome DevTools (Application → Cookies → open.spotify.com → sp_dc) / Cookie-Editor JSON / cURL header (recommended if sp_dc is HttpOnly).",
            )
            StepGuideRow(
                number = "3",
                text = "Tap Paste below — SpotUI auto-extracts sp_dc from any format — then tap Connect & Sync.",
            )
        }

        // Smart Paste & Connect Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color(LOGIN_CARD_DARK))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Paste & Connect Spotify Session",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )

            OutlinedTextField(
                value = cookieText,
                onValueChange = onCookieTextChange,
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

            if (cookieText.isNotBlank()) {
                val hasValid = detectedDc.length >= 16
                Surface(
                    color = if (hasValid) Color(0xFF13291C) else Color(0xFF281D13),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (hasValid) Color(LOGIN_SPOTIFY_GREEN) else Color(0xFFFFB74D),
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = if (hasValid) {
                                val keyTag = if (detectedKey.isNotBlank()) " + sp_key" else ""
                                "Detected sp_dc (${detectedDc.length} chars$keyTag) ready to verify"
                            } else {
                                "No valid sp_dc token detected yet — paste raw sp_dc, Cookie header, cURL, or Cookie-Editor JSON"
                            },
                            color = if (hasValid) Color(0xFFA7F3D0) else Color(0xFFFFDFB0),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onPasteClipboard,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("cookie_paste_button"),
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Paste", color = Color.White, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    enabled = !isProcessing && !isSyncing && cookieText.isNotBlank(),
                    onClick = {
                        if (detectedDc.length < 16) {
                            onValidationError("Invalid or empty sp_dc token — paste the sp_dc value from Chrome or Cookie-Editor.")
                        } else {
                            onConnectAndSync(detectedDc, detectedKey)
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(LOGIN_SPOTIFY_GREEN), contentColor = Color.Black),
                    modifier = Modifier.weight(1.35f).heightIn(min = 48.dp).testTag("cookie_submit_button"),
                ) {
                    Text("Connect & Sync", fontWeight = FontWeight.ExtraBold)
                }
            }

            if (effectiveLinked) {
                TextButton(
                    onClick = onDisconnect,
                    modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp).testTag("spotify_disconnect_button"),
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color(0xFFE57373), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Disconnect Spotify Account", color = Color(0xFFE57373), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun StepGuideRow(number: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(LOGIN_SPOTIFY_GREEN).copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number,
                color = Color(LOGIN_SPOTIFY_GREEN),
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        Text(
            text = text,
            color = Color(LOGIN_TEXT_SECONDARY),
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            modifier = Modifier.weight(1f),
        )
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
            .padding(horizontal = 10.dp, vertical = 8.dp),
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
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
