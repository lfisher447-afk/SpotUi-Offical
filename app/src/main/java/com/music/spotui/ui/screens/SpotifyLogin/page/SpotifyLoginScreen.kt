@file:Suppress("TooManyFunctions", "LongMethod", "LongParameterList", "MagicNumber")

package com.music.spotui.ui.screens.SpotifyLogin.page

import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.metrolist.spotify.Spotify
import com.metrolist.spotify.models.SpotifyInternalToken
import com.music.spotui.audio.UiSoundEngine
import com.music.spotui.data.api.SpotifySession
import com.music.spotui.data.api.SpotifySync
import com.music.spotui.data.api.SpotifyTokenProvider
import com.music.spotui.di.SpotifyWebPlayer
import com.music.spotui.ui.navigation.Routes
import com.music.spotui.ui.screens.SpotifyLogin.helper.LOGIN_BACKGROUND_DARK
import com.music.spotui.ui.screens.SpotifyLogin.helper.LOGIN_CARD_DARK
import com.music.spotui.ui.screens.SpotifyLogin.helper.LOGIN_SPOTIFY_GREEN
import com.music.spotui.ui.screens.SpotifyLogin.helper.LOGIN_TEXT_SECONDARY
import com.music.spotui.ui.screens.SpotifyLogin.helper.LoginDiagnosticsDialog
import com.music.spotui.ui.screens.SpotifyLogin.helper.LoginTab
import com.music.spotui.ui.screens.SpotifyLogin.helper.LoginUaProfile
import com.music.spotui.ui.screens.SpotifyLogin.helper.looksLikeSpotifyCookiePayload
import com.music.spotui.ui.screens.SpotifyLogin.helper.parseSpotifyCookiesFromText
import com.music.spotui.ui.screens.SpotifyLogin.helper.readClipboardText
import com.music.spotui.ui.screens.SpotifyLogin.module.LoginStage
import com.music.spotui.ui.screens.SpotifyLogin.module.MIN_SP_DC_LENGTH
import com.music.spotui.ui.screens.SpotifyLogin.module.SpotifyEngineNavigationState
import com.music.spotui.ui.screens.SpotifyLogin.module.SpotifyEngineProcessState
import com.music.spotui.ui.screens.SpotifyLogin.module.SpotifyEngineSecurityState
import com.music.spotui.ui.screens.SpotifyLogin.module.SpotifyEngineShieldState
import com.music.spotui.ui.screens.SpotifyLogin.module.SpotifyWebViewAdapter
import com.music.spotui.ui.screens.SpotifyLogin.module.launchLoginTask
import com.music.spotui.ui.screens.SpotifyLogin.module.processLoginSession
import com.music.spotui.util.DevConsoleManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

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

    var activeTab by remember { mutableStateOf(LoginTab.WEBVIEW) }
    var isProcessing by remember { mutableStateOf(false) }
    var stage by remember { mutableStateOf<LoginStage?>(null) }
    val isSyncing = isProcessing && stage == LoginStage.LIBRARY
    var accountName by remember { mutableStateOf<String?>(null) }
    var syncSummaryText by remember { mutableStateOf(SpotifySync.lastSyncDetail) }
    var statusMessage by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }

    var cookieText by remember { mutableStateOf("") }
    var showLogsDialog by remember { mutableStateOf(false) }
    var popupWebView by remember { mutableStateOf<WebView?>(null) }
    var showToolbar by remember { mutableStateOf(true) }

    val tokenFetchStarted = remember { AtomicBoolean(false) }

    val navigateToHome: () -> Unit = {
        runCatching { SpotifyWebPlayer.refreshLogin(context) }
        runCatching {
            navController.navigate(Routes.Home.route) {
                popUpTo(Routes.Login.route) { inclusive = true }
            }
        }
    }

    var webAdapterRef by remember { mutableStateOf<SpotifyWebViewAdapter?>(null) }

    fun handleCapturedSession(
        spDc: String,
        spKey: String,
        preCapturedToken: SpotifyInternalToken?,
        source: String,
        navigateOnComplete: Boolean = true,
    ) {
        if (!tokenFetchStarted.compareAndSet(false, true)) return
        processLoginSession(
            context = context,
            spDc = spDc,
            spKey = spKey,
            source = source,
            preCapturedToken = preCapturedToken,
            autoSyncLibrary = true,
            scope = scope,
            setProcessing = { isProcessing = it },
            setStatus = { statusMessage = it },
            setError = { hasError = it },
            onStage = { stage = it },
            onAccount = { accountName = it },
            onComplete = { ok ->
                if (!ok) {
                    tokenFetchStarted.set(false)
                    webAdapterRef?.allowRecapture()
                } else {
                    stage = null
                    isAccountLinked = true
                    SpotifySync.lastSyncDetail.takeIf { it.isNotBlank() }?.let { syncSummaryText = it }
                    runCatching { SpotifyWebPlayer.refreshLogin(context) }
                }
            },
            onSuccess = { summary ->
                if (!summary.isNullOrBlank()) syncSummaryText = summary
                val who = accountName?.let { " as $it" }.orEmpty()
                Toast.makeText(context, "Synced Spotify library$who!", Toast.LENGTH_SHORT).show()
                if (navigateOnComplete) {
                    navigateToHome()
                } else {
                    tokenFetchStarted.set(false)
                }
            },
        )
    }

    val webAdapter = remember {
        SpotifyWebViewAdapter(
            isPrivate = false,
            initialUaProfile = LoginUaProfile.SYSTEM_DEFAULT,
            onSessionCaptured = { spDc, spKey, preToken, source ->
                handleCapturedSession(
                    spDc = spDc,
                    spKey = spKey,
                    preCapturedToken = preToken,
                    source = source,
                    navigateOnComplete = true,
                )
            },
            onPopupRequested = { popup ->
                popupWebView = popup
            },
        ).also { adapter ->
            adapter.onScrollVisibilityListener = { visible -> showToolbar = visible }
            webAdapterRef = adapter
        }
    }

    val webNavState by webAdapter.navigationState.collectAsStateWithLifecycle()
    val webSecurityState by webAdapter.securityState.collectAsStateWithLifecycle()
    val webShieldState by webAdapter.shieldState.collectAsStateWithLifecycle()
    val webProcessState by webAdapter.processState.collectAsStateWithLifecycle()
    val uaProfile by webAdapter.uaProfile.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        val ready = SpotifyTokenProvider.syncAuthState(context)
        isAccountLinked = ready
        DevConsoleManager.logAuth(
            "Spotify WebView Sync ready (linked=$ready, engine=UnifiedSpotifyWebView)",
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            webAdapter.destroy()
            popupWebView = null
        }
    }

    fun triggerSyncAll() {
        if (isProcessing) return
        UiSoundEngine.playTapChime(context)

        if (!effectiveLinked && webAdapter.tryCaptureSessionNow("SyncAllButtonWebCapture")) {
            return
        }

        val spDc = SpotifySession.spDc(context)
        if (spDc.isBlank()) {
            statusMessage = "Sign in inside the WebView below — SpotUI captures sp_dc and syncs automatically."
            hasError = false
            Toast.makeText(context, "Sign in below first, then tap Sync All", Toast.LENGTH_SHORT).show()
            return
        }

        isProcessing = true
        hasError = false
        stage = LoginStage.LIBRARY
        statusMessage = "Importing Spotify playlists, liked songs & artists into SpotUI…"
        launchLoginTask {
            val res = SpotifySync.syncAllFromSession(context.applicationContext)
            scope.launch(Dispatchers.Main) {
                isProcessing = false
                stage = null
                res.onSuccess { summary ->
                    UiSoundEngine.playToggleSound(context, true)
                    syncSummaryText = summary.detail
                    statusMessage = summary.detail
                    hasError = false
                    Toast.makeText(
                        context,
                        "Synced to SpotUI Library! Return to Library to play.",
                        Toast.LENGTH_LONG,
                    ).show()
                }.onFailure { err ->
                    hasError = true
                    statusMessage = err.message ?: "Sync failed — tap Sync All to retry"
                    Toast.makeText(context, statusMessage, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    BackHandler {
        when {
            popupWebView != null -> webAdapter.closePopup()
            activeTab == LoginTab.WEBVIEW && webAdapter.goBack() -> Unit
            activeTab != LoginTab.WEBVIEW -> if (!isProcessing) activeTab = LoginTab.WEBVIEW
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
            val headerSubtitle = when {
                isSyncing || isProcessing -> statusMessage.ifBlank { "Importing Spotify playlists natively into SpotUI…" }
                syncSummaryText.isNotBlank() -> syncSummaryText
                effectiveLinked -> "Account linked · Tap Sync All to refresh library"
                else -> "WebView auto-capture · Sign in to sync Spotify natively"
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF171D28), Color(LOGIN_BACKGROUND_DARK)),
                        ),
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(LOGIN_SPOTIFY_GREEN)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.Black,
                            strokeWidth = 2.5.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "SpotUI Spotify Sync",
                            tint = Color.Black,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Spotify Music Sync",
                        color = Color.White,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                    )
                    Text(
                        text = headerSubtitle,
                        color = if (hasError) Color(0xFFFF9EA6) else Color(0xFFB3B8CA),
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Button(
                    onClick = { triggerSyncAll() },
                    enabled = !isProcessing,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(LOGIN_SPOTIFY_GREEN),
                        contentColor = Color.Black,
                        disabledContainerColor = Color(0xFF284836),
                        disabledContentColor = Color.White,
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("spotify_sync_all_button"),
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = if (isSyncing) "Syncing…" else "Sync All",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }

                IconButton(
                    onClick = {
                        UiSoundEngine.playTapChime(context)
                        showLogsDialog = true
                    },
                    modifier = Modifier.size(48.dp).testTag("login_logs_button"),
                ) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = "Login Diagnostics Logs",
                        tint = if (faults.isNotEmpty()) Color(0xFFFFB74D) else Color(LOGIN_TEXT_SECONDARY),
                    )
                }

                TextButton(
                    onClick = {
                        UiSoundEngine.playTapChime(context)
                        navigateToHome()
                    },
                    enabled = !isProcessing,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("login_done_button"),
                ) {
                    Text(
                        text = if (effectiveLinked) "Done" else "Skip",
                        color = Color(LOGIN_SPOTIFY_GREEN),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LoginTab.entries.forEach { tab ->
                    val selected = activeTab == tab
                    val icon = when (tab) {
                        LoginTab.WEBVIEW -> Icons.Default.Language
                        LoginTab.PASTE_COOKIE -> Icons.Default.Code
                    }
                    FilterChip(
                        selected = selected,
                        onClick = {
                            if (!isProcessing) {
                                UiSoundEngine.playTapChime(context)
                                activeTab = tab
                            }
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        label = {
                            Text(
                                text = tab.label,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            )
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(LOGIN_SPOTIFY_GREEN),
                            selectedLabelColor = Color.Black,
                            selectedLeadingIconColor = Color.Black,
                            containerColor = Color(LOGIN_CARD_DARK),
                            labelColor = Color(LOGIN_TEXT_SECONDARY),
                            iconColor = Color(LOGIN_TEXT_SECONDARY),
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = Color(0xFF2B3042),
                            selectedBorderColor = Color(LOGIN_SPOTIFY_GREEN),
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("login_tab_${tab.name.lowercase()}"),
                    )
                }
            }

            if (stage != null) {
                LoginStageRow(current = stage, hasError = hasError)
            }

            AnimatedVisibility(visible = statusMessage.isNotBlank()) {
                Surface(
                    color = if (hasError) Color(0xFF2B161A) else Color(0xFF14271D),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = statusMessage,
                            color = if (hasError) Color(0xFFFF9EA6) else Color(0xFFA7F3D0),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            when (activeTab) {
                LoginTab.WEBVIEW -> {
                    HardenedWebViewLoginBody(
                        adapter = webAdapter,
                        navState = webNavState,
                        securityState = webSecurityState,
                        shieldState = webShieldState,
                        processState = webProcessState,
                        uaProfile = uaProfile,
                        showToolbar = showToolbar,
                        startUrl = if (initialSessionLinked) "https://open.spotify.com/" else Spotify.LOGIN_URL,
                        clearPreviousSession = !initialSessionLinked,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
                LoginTab.PASTE_COOKIE -> {
                    ManualCookiePane(
                        cookieText = cookieText,
                        onCookieTextChange = { cookieText = it },
                        isProcessing = isProcessing,
                        onPasteClipboard = {
                            UiSoundEngine.playTapChime(context)
                            val clip = readClipboardText(context).trim()
                            if (clip.isBlank()) {
                                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                            } else {
                                cookieText = clip
                                if (!looksLikeSpotifyCookiePayload(clip)) {
                                    Toast.makeText(
                                        context,
                                        "Pasted text doesn't look like an sp_dc cookie",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        },
                        onConnectAndSync = { dc, key ->
                            handleCapturedSession(
                                spDc = dc,
                                spKey = key,
                                preCapturedToken = null,
                                source = "ManualCookiePaste",
                            )
                        },
                        onClose = { activeTab = LoginTab.WEBVIEW },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        popupWebView?.let { popup ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(LOGIN_BACKGROUND_DARK)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF181B24))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color(LOGIN_SPOTIFY_GREEN),
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Protected Spotify Sign-In Window",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    IconButton(
                        onClick = { webAdapter.closePopup() },
                        modifier = Modifier.size(48.dp).testTag("close_sso_popup_button"),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Popup", tint = Color.White)
                    }
                }
                AndroidView(
                    factory = {
                        (popup.parent as? ViewGroup)?.removeView(popup)
                        popup.apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 56.dp),
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
private fun HardenedWebViewLoginBody(
    adapter: SpotifyWebViewAdapter,
    navState: SpotifyEngineNavigationState,
    securityState: SpotifyEngineSecurityState,
    shieldState: SpotifyEngineShieldState,
    processState: SpotifyEngineProcessState,
    uaProfile: LoginUaProfile,
    showToolbar: Boolean,
    startUrl: String,
    clearPreviousSession: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Column(
        modifier = modifier.animateContentSize(
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        ),
    ) {
        AnimatedVisibility(
            visible = showToolbar,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(
                    onClick = { adapter.goBack() },
                    enabled = navState.canGoBack,
                    modifier = Modifier.size(48.dp).testTag("login_web_back"),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = if (navState.canGoBack) Color.White else Color(0xFF555B6E),
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = { adapter.goForward() },
                    enabled = navState.canGoForward,
                    modifier = Modifier.size(48.dp).testTag("login_web_forward"),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Forward",
                        tint = if (navState.canGoForward) Color.White else Color(0xFF555B6E),
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = { adapter.reload() },
                    modifier = Modifier.size(48.dp).testTag("login_web_reload"),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = Color.White, modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = {
                        UiSoundEngine.playTapChime(context)
                        adapter.resetSessionAndLoad()
                    },
                    modifier = Modifier.size(48.dp).testTag("login_web_reset_cookies"),
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Reset Session", tint = Color(0xFFFFB74D), modifier = Modifier.size(18.dp))
                }

                Surface(
                    color = Color(0xFF14241C),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, Color(LOGIN_SPOTIFY_GREEN).copy(alpha = 0.35f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            imageVector = if (securityState.isHttps) Icons.Default.Shield else Icons.Default.Lock,
                            contentDescription = "WebView Shield Active",
                            tint = if (securityState.certificateErrorHost != null) Color(0xFFFF6B6B) else Color(LOGIN_SPOTIFY_GREEN),
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = "${shieldState.totalBlocked} blocked",
                            color = Color(0xFFD5F5E3),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Surface(
                    onClick = {
                        UiSoundEngine.playTapChime(context)
                        val next = when (uaProfile) {
                            LoginUaProfile.SYSTEM_DEFAULT -> LoginUaProfile.MOBILE_CHROME
                            LoginUaProfile.MOBILE_CHROME -> LoginUaProfile.DESKTOP_CHROME
                            LoginUaProfile.DESKTOP_CHROME -> LoginUaProfile.SYSTEM_DEFAULT
                        }
                        adapter.switchUaProfile(context, next)
                    },
                    color = Color(LOGIN_CARD_DARK),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, Color(0xFF34384A)),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("login_ua_cycle"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "UA: ${uaProfile.label}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        if (navState.isLoading) {
            LinearProgressIndicator(
                progress = { (navState.progress.coerceIn(5, 100) / 100f) },
                color = Color(LOGIN_SPOTIFY_GREEN),
                trackColor = Color(0xFF1A1E2A),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            )
        }

        val hint = processState.autoRecoverHint ?: processState.lastFailureMessage
        AnimatedVisibility(visible = !hint.isNullOrBlank()) {
            Surface(
                color = Color(0xFF281B18),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color(0xFFFFB74D).copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFFB74D), modifier = Modifier.size(18.dp))
                        Text(
                            text = hint.orEmpty(),
                            color = Color(0xFFFFE0B2),
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                UiSoundEngine.playTapChime(context)
                                adapter.autoRecover(context)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(LOGIN_SPOTIFY_GREEN),
                                contentColor = Color.Black,
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("login_auto_recover_button"),
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Auto-Recover", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                UiSoundEngine.playTapChime(context)
                                adapter.resetSessionAndLoad()
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("login_reset_retry_button"),
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Wipe & Retry", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(22.dp))
                .border(1.dp, Color(0xFF2F3446), RoundedCornerShape(22.dp)),
        ) {
            AndroidView(
                factory = { ctx ->
                    adapter.createView(
                        context = ctx,
                        startUrl = startUrl,
                        clearPreviousSession = clearPreviousSession,
                    )
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun LoginStageRow(current: LoginStage?, hasError: Boolean) {
    val currentIndex = current?.ordinal ?: -1
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LoginStage.entries.forEach { step ->
            val done = step.ordinal < currentIndex
            val active = step.ordinal == currentIndex
            val tint = when {
                active && hasError -> Color(0xFFFF9EA6)
                done || active -> Color(LOGIN_SPOTIFY_GREEN)
                else -> Color(0xFF34384A)
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, tint, RoundedCornerShape(14.dp))
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (done) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = step.label,
                    color = tint,
                    fontSize = 11.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ManualCookiePane(
    cookieText: String,
    onCookieTextChange: (String) -> Unit,
    isProcessing: Boolean,
    onPasteClipboard: () -> Unit,
    onConnectAndSync: (String, String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (detectedDc, detectedKey) = remember(cookieText) { parseSpotifyCookiesFromText(cookieText) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Import sp_dc Cookie",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Back to WebView", color = Color(LOGIN_SPOTIFY_GREEN))
            }
        }

        Text(
            text = "Optional backup: paste raw sp_dc strings, HTTP Cookie headers, cURL snippets, Netscape cookies.txt, or Cookie-Editor JSON.",
            color = Color(LOGIN_TEXT_SECONDARY),
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )

        OutlinedTextField(
            value = cookieText,
            onValueChange = onCookieTextChange,
            label = { Text("Paste sp_dc cookie string or JSON", color = Color(LOGIN_TEXT_SECONDARY)) },
            minLines = 4,
            maxLines = 6,
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(LOGIN_SPOTIFY_GREEN),
                unfocusedBorderColor = Color(0xFF34384A),
                focusedContainerColor = Color(0xFF10121A),
                unfocusedContainerColor = Color(0xFF10121A),
            ),
            modifier = Modifier.fillMaxWidth().testTag("manual_cookie_input"),
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onPasteClipboard,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Icon(Icons.Default.ContentPaste, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Paste", color = Color.White)
            }

            Button(
                enabled = !isProcessing && detectedDc.length >= MIN_SP_DC_LENGTH,
                onClick = { onConnectAndSync(detectedDc, detectedKey) },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(LOGIN_SPOTIFY_GREEN),
                    contentColor = Color.Black,
                ),
                modifier = Modifier.weight(1.4f).heightIn(min = 48.dp).testTag("connect_cookie_button"),
            ) {
                Text("Connect & Sync All", fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}
