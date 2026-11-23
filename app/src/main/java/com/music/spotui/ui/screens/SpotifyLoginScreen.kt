package com.music.spotui.ui.screens

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
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
import com.music.spotui.audio.UiSoundEngine
import com.music.spotui.di.SpotifyWebPlayer
import com.music.spotui.ui.navigation.Routes
import com.music.spotui.util.DevConsoleManager
import java.util.concurrent.atomic.AtomicBoolean

private const val SPOTIFY_LOGIN_URL =
    "https://accounts.spotify.com/en/login?continue=https%3A%2F%2Fopen.spotify.com%2F"

/**
 * Simple, clean Spotify Login screen (v2.5.3):
 * 1. Vanilla Android WebView (system default settings, zero UA spoofing or DOM script injection)
 *    with automatic `sp_dc` cookie capture + Open in Browser fallback.
 * 2. Clean `sp_dc` / Cookie JSON importer.
 * 3. Instant Continue as Guest option.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SpotifyLoginScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val logs by DevConsoleManager.logs.collectAsStateWithLifecycle()
    val faults by DevConsoleManager.faults.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(LoginTab.WEB) }
    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }

    var isPageLoading by remember { mutableStateOf(true) }
    var pageProgress by remember { mutableIntStateOf(0) }
    var currentHost by remember { mutableStateOf("accounts.spotify.com") }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var popupWebView by remember { mutableStateOf<WebView?>(null) }
    var cookieText by remember { mutableStateOf("") }
    var showLogsDialog by remember { mutableStateOf(false) }

    val tokenFetchStarted = remember { AtomicBoolean(false) }

    LaunchedEffect(Unit) {
        DevConsoleManager.logAuth("Simple Spotify login screen opened")
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

    fun tryCaptureSessionFromWeb(currentUrl: String?, source: String): Boolean {
        val sweep = sweepSpotifyCookies(currentUrl)
        val dc = sweep.spDc
        if (dc.isNullOrBlank()) return false
        if (!tokenFetchStarted.compareAndSet(false, true)) return true
        processLoginSession(
            context = context,
            spDc = dc,
            spKey = sweep.spKey,
            source = source,
            scope = scope,
            setProcessing = { isProcessing = it },
            setStatus = { statusMessage = it },
            setError = { hasError = it },
            onComplete = { ok -> if (!ok) tokenFetchStarted.set(false) },
            onSuccess = navigateToHome,
        )
        return true
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
            // Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF1A2520), Color(LOGIN_BACKGROUND_DARK)),
                        ),
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(LOGIN_SPOTIFY_GREEN)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "SpotUI",
                            tint = Color.Black,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "Connect Spotify",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            "Simple sign-in or guest mode",
                            color = Color(LOGIN_TEXT_SECONDARY),
                            fontSize = 11.sp,
                        )
                    }
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
                        DevConsoleManager.logAuth("User continued as Guest")
                        navigateToHome()
                    },
                    enabled = !isProcessing,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("login_guest_button"),
                ) {
                    Text("Guest", color = Color(LOGIN_SPOTIFY_GREEN), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            // Expressive Pill Tab Switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
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

            // Tab 1: Simple Vanilla Android WebView
            if (selectedTab == LoginTab.WEB) {
                Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
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
                            onClick = { webViewRef?.reload() },
                            modifier = Modifier.size(40.dp).testTag("login_web_reload"),
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = {
                                resetSpotifyWebCookies {
                                    tokenFetchStarted.set(false)
                                    statusMessage = ""
                                    hasError = false
                                    webViewRef?.loadUrl(SPOTIFY_LOGIN_URL)
                                }
                            },
                            modifier = Modifier.size(40.dp).testTag("login_web_reset_cookies"),
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Reset Cookies", tint = Color(0xFFFFB74D), modifier = Modifier.size(18.dp))
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
                            onClick = {
                                UiSoundEngine.playTapChime(context)
                                if (!tryCaptureSessionFromWeb(webViewRef?.url, "ManualVerify")) {
                                    Toast.makeText(context, "Finish signing in first (no sp_dc yet)", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(LOGIN_SPOTIFY_GREEN), contentColor = Color.Black),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.heightIn(min = 36.dp).testTag("login_verify_session_button"),
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Verify", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
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
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.databaseEnabled = true
                                    settings.javaScriptCanOpenWindowsAutomatically = true
                                    settings.setSupportMultipleWindows(true)

                                    val cm = CookieManager.getInstance()
                                    cm.setAcceptCookie(true)
                                    cm.setAcceptThirdPartyCookies(this, true)

                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                            pageProgress = newProgress
                                            isPageLoading = newProgress in 1..99
                                        }

                                        override fun onCreateWindow(
                                            view: WebView?,
                                            isDialog: Boolean,
                                            isUserGesture: Boolean,
                                            resultMsg: Message?,
                                        ): Boolean {
                                            val popup = WebView(ctx).apply {
                                                settings.javaScriptEnabled = true
                                                settings.domStorageEnabled = true
                                                settings.databaseEnabled = true
                                                cm.setAcceptCookie(true)
                                                cm.setAcceptThirdPartyCookies(this, true)
                                                webChromeClient = object : WebChromeClient() {
                                                    override fun onCloseWindow(window: WebView?) {
                                                        runCatching { window?.destroy() }
                                                        popupWebView = null
                                                    }
                                                }
                                                webViewClient = object : WebViewClient() {
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
                                            return true
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            isPageLoading = false
                                            runCatching { cm.flush() }
                                            if (url != null) {
                                                DevConsoleManager.logAuth("Loaded: $url")
                                                if (url.contains("open.spotify.com", ignoreCase = true) ||
                                                    url.contains("/status", ignoreCase = true)
                                                ) {
                                                    tryCaptureSessionFromWeb(url, "WebView")
                                                } else {
                                                    tryCaptureSessionFromWeb(url, "WebView")
                                                }
                                            }
                                        }
                                    }

                                    webViewRef = this
                                    loadUrl(SPOTIFY_LOGIN_URL)
                                }
                            },
                        )
                    }
                }
            } else {
                // Tab 2: Simple Paste Cookie / sp_dc Importer
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
                            "Simple sp_dc Cookie Sign-In",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            "Sign into open.spotify.com in Chrome/Desktop, copy your sp_dc cookie (or Cookie-Editor JSON), and paste below.",
                            color = Color(LOGIN_TEXT_SECONDARY),
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                        )

                        OutlinedTextField(
                            value = cookieText,
                            onValueChange = { cookieText = it },
                            label = { Text("sp_dc value, Cookie header, or JSON", color = Color(LOGIN_TEXT_SECONDARY)) },
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
                                        scope = scope,
                                        setProcessing = { isProcessing = it },
                                        setStatus = { statusMessage = it },
                                        setError = { hasError = it },
                                        onComplete = {},
                                        onSuccess = navigateToHome,
                                    )
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(LOGIN_SPOTIFY_GREEN), contentColor = Color.Black),
                                modifier = Modifier.weight(1.3f).heightIn(min = 48.dp).testTag("cookie_submit_button"),
                            ) {
                                Text("Connect Account", fontWeight = FontWeight.ExtraBold)
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
