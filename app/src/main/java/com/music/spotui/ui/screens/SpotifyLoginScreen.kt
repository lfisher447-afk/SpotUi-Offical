package com.music.spotui.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.metrolist.spotify.SpotifyAuth
import com.music.spotui.audio.UiSoundEngine
import com.music.spotui.data.api.Api
import com.music.spotui.data.api.SpotifySession
import com.music.spotui.data.api.SpotifySync
import com.music.spotui.data.api.SpotifyTokenProvider
import com.music.spotui.di.SpotifyWebPlayer
import com.music.spotui.ui.navigation.Routes
import com.music.spotui.util.DevConsoleManager
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

private const val SPOTIFY_LOGIN_URL = SpotifyAuth.LOGIN_URL

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

    var isProcessing by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var syncSummaryText by remember { mutableStateOf(SpotifySync.lastSyncDetail) }
    var statusMessage by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }

    var showManualCookieInput by remember { mutableStateOf(false) }
    var cookieText by remember { mutableStateOf("") }
    var showLogsDialog by remember { mutableStateOf(false) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    val tokenFetchStarted = remember { AtomicBoolean(false) }

    LaunchedEffect(Unit) {
        val ready = SpotifyTokenProvider.syncAuthState(context)
        isAccountLinked = ready
        DevConsoleManager.logAuth("Spotify Music Sync opened (linked=$ready, mode=EmbeddedAutoCaptureWebView)")
    }

    val navigateToHome: () -> Unit = {
        runCatching { SpotifyWebPlayer.refreshLogin(context) }
        runCatching {
            navController.navigate(Routes.Home.route) {
                popUpTo(Routes.Login.route) { inclusive = true }
            }
        }
    }

    fun handleCapturedSession(spDc: String, spKey: String, source: String) {
        if (!tokenFetchStarted.compareAndSet(false, true)) return
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
                Toast.makeText(context, "Signed in & synced Spotify library!", Toast.LENGTH_SHORT).show()
                navigateToHome()
            },
        )
    }

    fun checkAndCaptureCookies(url: String?) {
        val sweep = sweepSpotifyCookies(url)
        val dc = sweep.spDc
        if (!dc.isNullOrBlank() && dc.length >= 16 && !tokenFetchStarted.get()) {
            DevConsoleManager.logAuth("Auto-captured valid sp_dc cookie from WebView (len=${dc.length})")
            handleCapturedSession(dc, sweep.spKey, "EmbeddedWebViewAutoCapture")
        }
    }

    BackHandler {
        if (webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
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
                effectiveLinked -> "Spotify session linked · Tap Done"
                else -> "Sign in via embedded Spotify login (auto-captures sp_dc)"
            }

            // Top Bar
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
                        maxLines = 2,
                    )
                }

                IconButton(
                    onClick = {
                        UiSoundEngine.playTapChime(context)
                        showManualCookieInput = !showManualCookieInput
                    },
                    modifier = Modifier.size(44.dp).testTag("toggle_manual_cookie"),
                ) {
                    Icon(
                        imageVector = if (showManualCookieInput) Icons.Default.Language else Icons.Default.Code,
                        contentDescription = "Toggle Manual Cookie",
                        tint = Color(LOGIN_SPOTIFY_GREEN),
                    )
                }

                IconButton(
                    onClick = {
                        UiSoundEngine.playTapChime(context)
                        showLogsDialog = true
                    },
                    modifier = Modifier.size(44.dp).testTag("login_logs_button"),
                ) {
                    Icon(Icons.Default.BugReport, contentDescription = "Login Logs", tint = Color(LOGIN_TEXT_SECONDARY))
                }

                TextButton(
                    onClick = {
                        UiSoundEngine.playTapChime(context)
                        navigateToHome()
                    },
                    enabled = !isProcessing && !isSyncing,
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

            if (statusMessage.isNotBlank()) {
                Surface(
                    color = if (hasError) Color(0xFF2B161A) else Color(0xFF14271D),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
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

            if (showManualCookieInput) {
                // Secondary Manual Cookie Paste Pane
                ManualCookiePane(
                    cookieText = cookieText,
                    onCookieTextChange = { cookieText = it },
                    isProcessing = isProcessing,
                    isSyncing = isSyncing,
                    onPasteClipboard = {
                        UiSoundEngine.playTapChime(context)
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = cm.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                        if (clip.isNotBlank()) cookieText = clip
                    },
                    onConnectAndSync = { dc, key ->
                        handleCapturedSession(dc, key, "ManualCookiePaste")
                    },
                    onClose = { showManualCookieInput = false },
                )
            } else {
                // Primary Embedded Auto-Capture WebView
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFF34384A), RoundedCornerShape(16.dp)),
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                                val cm = CookieManager.getInstance()
                                cm.setAcceptCookie(true)
                                cm.setAcceptThirdPartyCookies(this, true)

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    setSupportMultipleWindows(true)
                                    javaScriptCanOpenWindowsAutomatically = true
                                    userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        runCatching { CookieManager.getInstance().flush() }
                                        checkAndCaptureCookies(url)
                                        view?.evaluateJavascript(
                                            "(function(){try{Document.prototype.requestStorageAccess=function(){return Promise.resolve(true);};Object.defineProperty(navigator,'webdriver',{get:()=>undefined});}catch(e){}})();",
                                            null,
                                        )
                                    }

                                    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                        super.doUpdateVisitedHistory(view, url, isReload)
                                        checkAndCaptureCookies(url)
                                    }

                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        val reqUrl = request?.url?.toString().orEmpty()
                                        checkAndCaptureCookies(reqUrl)
                                        return false
                                    }
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onCreateWindow(
                                        view: WebView?,
                                        isDialog: Boolean,
                                        isUserGesture: Boolean,
                                        resultMsg: Message?,
                                    ): Boolean {
                                        val newWebView = WebView(ctx).apply {
                                            settings.javaScriptEnabled = true
                                            settings.domStorageEnabled = true
                                            settings.setSupportMultipleWindows(true)
                                        }
                                        val transport = resultMsg?.obj as? WebView.WebViewTransport
                                        transport?.webView = newWebView
                                        resultMsg?.sendToTarget()
                                        return true
                                    }
                                }

                                webViewInstance = this
                                loadUrl(SPOTIFY_LOGIN_URL)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
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
private fun ManualCookiePane(
    cookieText: String,
    onCookieTextChange: (String) -> Unit,
    isProcessing: Boolean,
    isSyncing: Boolean,
    onPasteClipboard: () -> Unit,
    onConnectAndSync: (String, String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val (detectedDc, detectedKey) = remember(cookieText) { parseSpotifyCookiesFromText(cookieText) }

    Column(
        modifier = Modifier
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
                text = "Manual sp_dc Cookie Fallback",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = onClose) {
                Text("Back to WebView", color = Color(LOGIN_SPOTIFY_GREEN))
            }
        }

        OutlinedTextField(
            value = cookieText,
            onValueChange = onCookieTextChange,
            label = { Text("Paste sp_dc cookie string or JSON", color = Color(LOGIN_TEXT_SECONDARY)) },
            minLines = 4,
            maxLines = 6,
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
                enabled = !isProcessing && !isSyncing && detectedDc.length >= 16,
                onClick = { onConnectAndSync(detectedDc, detectedKey) },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(LOGIN_SPOTIFY_GREEN), contentColor = Color.Black),
                modifier = Modifier.weight(1.35f).heightIn(min = 48.dp),
            ) {
                Text("Connect & Sync", fontWeight = FontWeight.Bold)
            }
        }
    }
}
