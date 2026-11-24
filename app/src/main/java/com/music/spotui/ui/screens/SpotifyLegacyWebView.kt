package com.music.spotui.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.music.spotui.audio.UiSoundEngine
import com.music.spotui.util.DevConsoleManager

/**
 * Experimental / Legacy embedded WebView fallback.
 *
 * Kept strictly opt-in behind an explicit confirmation gate because Android's
 * system WebView rejects `document.requestStorageAccess()`, causing Spotify's
 * `accounts.spotify.com/login/code/send` endpoint to reject requests with HTTP 400.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun ColumnScope.LegacyWebViewLoginPane(
    armed: Boolean,
    initialSessionLinked: Boolean,
    uaProfile: LoginUaProfile,
    onUaProfileChange: (LoginUaProfile) -> Unit,
    onArmWebView: () -> Unit,
    onSwitchToChromeCookie: () -> Unit,
    onTriggerSyncAll: () -> Unit,
    isProcessing: Boolean,
    isSyncing: Boolean,
    webViewRef: WebView?,
    onWebViewCreated: (WebView?) -> Unit,
    onTryReadSessionScriptAndVerify: (WebView?, String?, String, Boolean) -> Unit,
    onTryCaptureSessionFromWeb: (String?, String) -> Boolean,
) {
    val context = LocalContext.current

    if (!armed) {
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
                    .background(Color(0xFF211815))
                    .border(1.dp, Color(0xFFFFB74D).copy(alpha = 0.4f), RoundedCornerShape(22.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Experimental warning",
                        tint = Color(0xFFFFB74D),
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = "Legacy WebView (Experimental)",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = "Android's built-in WebView automatically rejects document.requestStorageAccess(), which breaks Spotify's reCAPTCHA/anti-bot check and triggers HTTP 400 on /login/code/send.\n\nUse the primary Chrome + Cookie tab instead — Chrome Custom Tabs runs inside the real Chrome engine where Email Code, Google/Apple sign-in, and reCAPTCHA pass cleanly.",
                    color = Color(0xFFFFDFB0),
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                )
                Button(
                    onClick = {
                        UiSoundEngine.playTapChime(context)
                        onSwitchToChromeCookie()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(LOGIN_SPOTIFY_GREEN),
                        contentColor = Color.Black,
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("legacy_switch_to_chrome_button"),
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Use Chrome + Cookie (Recommended)", fontWeight = FontWeight.ExtraBold)
                }
                OutlinedButton(
                    onClick = {
                        UiSoundEngine.playTapChime(context)
                        onArmWebView()
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("launch_legacy_webview_button"),
                ) {
                    Text(
                        text = "Launch Experimental WebView Anyway",
                        color = Color(0xFFFFB74D),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        return
    }

    var currentWebUrl by remember {
        mutableStateOf(if (initialSessionLinked) "https://open.spotify.com/" else SpotifyAuth.LOGIN_URL)
    }
    var isPageLoading by remember { mutableStateOf(true) }
    var pageProgress by remember { mutableIntStateOf(0) }
    var currentHost by remember {
        mutableStateOf(if (initialSessionLinked) "open.spotify.com" else "accounts.spotify.com")
    }
    var recoveryHint by remember { mutableStateOf<String?>(null) }
    var popupWebView by remember { mutableStateOf<WebView?>(null) }

    // The main WebView is destroyed by AndroidView's onRelease; this effect only owns the popup
    // and tells the parent to drop its reference (reading state, never a stale first-composition copy).
    val latestOnWebViewCreated by rememberUpdatedState(onWebViewCreated)
    DisposableEffect(Unit) {
        onDispose {
            popupWebView?.let { runCatching { it.stopLoading(); it.destroy() } }
            popupWebView = null
            latestOnWebViewCreated(null)
        }
    }

    fun applyUaProfileAndReload(nextProfile: LoginUaProfile, targetUrl: String = currentWebUrl) {
        onUaProfileChange(nextProfile)
        recoveryHint = null
        val wv = webViewRef ?: return
        wv.settings.userAgentString = resolveLoginUserAgent(context, nextProfile)
        DevConsoleManager.logAuth("Switched Legacy WebView UA profile -> ${nextProfile.label}")
        wv.loadUrl(targetUrl)
    }

    Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(
                onClick = { webViewRef?.goBack() },
                modifier = Modifier.size(48.dp).testTag("login_web_back"),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = {
                    recoveryHint = null
                    webViewRef?.reload()
                },
                modifier = Modifier.size(48.dp).testTag("login_web_reload"),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = Color.White, modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = {
                    UiSoundEngine.playTapChime(context)
                    resetSpotifyWebSession(webViewRef) {
                        recoveryHint = null
                        webViewRef?.loadUrl(SpotifyAuth.LOGIN_URL)
                    }
                },
                modifier = Modifier.size(48.dp).testTag("login_web_reset_cookies"),
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Reset Session", tint = Color(0xFFFFB74D), modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = { openSpotifyInChromeCustomTab(context, SpotifyAuth.LOGIN_URL) },
                modifier = Modifier.size(48.dp).testTag("login_open_browser"),
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
                onClick = onTriggerSyncAll,
                enabled = !isProcessing && !isSyncing,
                colors = ButtonDefaults.buttonColors(containerColor = Color(LOGIN_SPOTIFY_GREEN), contentColor = Color.Black),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.heightIn(min = 48.dp).testTag("login_verify_session_button"),
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Verify & Sync", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

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
                        currentWebUrl = SpotifyAuth.LOGIN_URL
                        webViewRef?.loadUrl(SpotifyAuth.LOGIN_URL)
                    }
                    .testTag("login_dest_signin"),
            ) {
                Text(
                    text = "Sign-In Page",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            Surface(
                color = Color(LOGIN_CARD_DARK),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .clickable {
                        UiSoundEngine.playTapChime(context)
                        currentWebUrl = "https://open.spotify.com/"
                        webViewRef?.loadUrl("https://open.spotify.com/")
                    }
                    .testTag("login_dest_library"),
            ) {
                Text(
                    text = "Web Player",
                    color = Color(LOGIN_SPOTIFY_GREEN),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
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
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }

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
                        modifier = Modifier.heightIn(min = 36.dp),
                    ) {
                        Text("Try ${nextUa.label}", color = Color.White, fontSize = 11.sp)
                    }
                    Button(
                        onClick = {
                            onSwitchToChromeCookie()
                            openSpotifyInChromeCustomTab(context, SpotifyAuth.LOGIN_URL)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(LOGIN_SPOTIFY_GREEN), contentColor = Color.Black),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.heightIn(min = 36.dp),
                    ) {
                        Text("Switch to Chrome + Cookie", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                        configureLegacyWebViewSettings(ctx, this, uaProfile)

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
                                        "SpotifyLegacyWebJS",
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
                                val popup = WebView(ctx).apply {
                                    configureLegacyWebViewSettings(ctx, this, uaProfile)
                                    cm.setAcceptCookie(true)
                                    cm.setAcceptThirdPartyCookies(this, true)
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onCloseWindow(window: WebView?) {
                                            popupWebView = null
                                            window?.post { runCatching { window.stopLoading(); window.destroy() } }
                                        }
                                    }
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                                            handleNonWebNavigation(ctx, view, request?.url?.toString().orEmpty())

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            runCatching { cm.flush() }
                                            if (url != null && url.contains("spotify.com", ignoreCase = true)) {
                                                if (onTryCaptureSessionFromWeb(url, "OAuthPopup")) {
                                                    popupWebView = null
                                                    view?.post { runCatching { view.stopLoading(); view.destroy() } }
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

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                                handleNonWebNavigation(ctx, view, request?.url?.toString().orEmpty())

                            override fun onReceivedHttpError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                errorResponse: WebResourceResponse?,
                            ) {
                                val code = errorResponse?.statusCode ?: return
                                val reqUrl = request?.url?.toString().orEmpty()
                                if (code >= 400 && reqUrl.contains("spotify", ignoreCase = true)) {
                                    DevConsoleManager.log(
                                        DevConsoleManager.LogLevel.ERROR,
                                        "SpotifyLegacyWebHTTP",
                                        "HTTP $code on ${reqUrl.take(120)} (ua=${uaProfile.label})",
                                        category = DevConsoleManager.LogCategory.AUTH,
                                    )
                                    if (code == 400 && reqUrl.contains("/login", ignoreCase = true)) {
                                        recoveryHint = "Embedded WebView hit HTTP 400 because WebView blocks requestStorageAccess(). Use Chrome + Cookie instead."
                                    }
                                }
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?,
                            ) {
                                if (request?.isForMainFrame == true) {
                                    DevConsoleManager.logError("SpotifyLegacyWebError", "Main frame error: ${error?.description}")
                                }
                            }

                            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                                DevConsoleManager.logError("SpotifyLegacyWebView", "Render process crashed (didCrash=${detail?.didCrash()})")
                                recoveryHint = "The embedded WebView crashed. Use Chrome + Cookie, or leave and reopen this screen."
                                isPageLoading = false
                                return true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isPageLoading = false
                                runCatching { cm.flush() }
                                if (url != null && !initialSessionLinked) {
                                    onTryReadSessionScriptAndVerify(view, url, "LegacyWebView", false)
                                }
                            }
                        }

                        onWebViewCreated(this)
                        loadUrl(currentWebUrl)
                    }
                },
                onRelease = { released ->
                    runCatching { released.stopLoading(); released.destroy() }
                },
            )
        }
    }

    popupWebView?.let { popup ->
        Dialog(
            onDismissRequest = {
                popupWebView = null
                popup.post { runCatching { popup.stopLoading(); popup.destroy() } }
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
                                popupWebView = null
                                popup.post { runCatching { popup.stopLoading(); popup.destroy() } }
                            },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                    AndroidView(factory = { popup }, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun configureLegacyWebViewSettings(
    context: android.content.Context,
    webView: WebView,
    profile: LoginUaProfile,
) {
    webView.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        @Suppress("DEPRECATION")
        databaseEnabled = true
        javaScriptCanOpenWindowsAutomatically = true
        setSupportMultipleWindows(true)
        loadsImagesAutomatically = true
        useWideViewPort = true
        loadWithOverviewMode = true
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        userAgentString = resolveLoginUserAgent(context, profile)
    }
}

/**
 * Handles non-http(s) navigations inside the login WebViews. Only `intent://` links are honoured, and
 * only after being stripped of anything that could target an arbitrary component: web content must
 * never be able to launch a specific app component or hand us a selector intent.
 */
private fun handleNonWebNavigation(context: android.content.Context, view: WebView?, url: String): Boolean {
    if (url.startsWith("http://") || url.startsWith("https://")) return false
    if (url.startsWith("intent://")) {
        runCatching {
            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            val fallback = intent.getStringExtra("browser_fallback_url")
            if (!fallback.isNullOrBlank() && (fallback.startsWith("https://") || fallback.startsWith("http://"))) {
                view?.loadUrl(fallback)
            } else {
                intent.addCategory(Intent.CATEGORY_BROWSABLE)
                intent.component = null
                intent.selector = null
                if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
    }
    return true
}
