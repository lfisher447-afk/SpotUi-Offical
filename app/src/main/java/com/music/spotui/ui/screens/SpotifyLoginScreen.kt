package com.music.spotui.ui.screens

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.metrolist.spotify.Spotify
import com.metrolist.spotify.SpotifyAuth
import com.music.spotui.R
import com.music.spotui.data.api.SpotifySession
import com.music.spotui.di.SpotifyWebPlayer
import com.music.spotui.ui.navigation.Routes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

private const val SPOTIFY_GREEN = 0xFF1ED760
private const val BACKGROUND_DARK = 0xFF121212
private const val CARD_DARK = 0xFF1E1E1E
private const val TEXT_SECONDARY = 0xFFB3B3B3

// Desktop user-agent to avoid Spotify blocking or returning blank screens to Android WebView
private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

private enum class LoginTab {
    NATIVE,
    WEB,
    COOKIE,
}

/**
 * Native Spotify authentication screen featuring:
 * 1. Native Compose Login UI (Default View) with Email/Password fields & toggles
 * 2. Embedded Spotify Web Browser Login Mode with navigation controls & dark theme
 * 3. Interactive Paste sp_dc Cookie Dialog & Cookie tab
 * 4. Guest Mode for instant access to Home, Search, Library & Player
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SpotifyLoginScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var selectedTab by remember { mutableStateOf(LoginTab.NATIVE) }

    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }

    // Native Form Input State
    var emailOrUsername by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var nativeLoginError by remember { mutableStateOf<String?>(null) }

    // Cookie Dialog State
    var showCookieDialog by remember { mutableStateOf(false) }
    var dialogCookieInput by remember { mutableStateOf("") }
    var dialogCookieError by remember { mutableStateOf<String?>(null) }

    // Web Browser State
    var isPageLoading by remember { mutableStateOf(true) }
    var pageProgress by remember { mutableIntStateOf(0) }
    var hasWebError by remember { mutableStateOf(false) }
    var webErrorMessage by remember { mutableStateOf("") }
    var loadDurationSeconds by remember { mutableIntStateOf(0) }

    // Direct Cookie tab inputs
    var cookieInputValue by remember { mutableStateOf("") }
    var cookieError by remember { mutableStateOf<String?>(null) }

    val tokenFetchStarted = remember { AtomicBoolean(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val navigateToHome: () -> Unit = {
        SpotifyWebPlayer.refreshLogin(context)
        navController.navigate(Routes.Home.route) {
            popUpTo(Routes.Login.route) { inclusive = true }
        }
    }

    val openInExternalBrowser: () -> Unit = {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(SpotifyAuth.LOGIN_URL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure { err ->
            Timber.e(err, "Failed to open external browser")
        }
    }

    // Back button handling
    BackHandler {
        when {
            showCookieDialog -> showCookieDialog = false
            selectedTab != LoginTab.NATIVE -> selectedTab = LoginTab.NATIVE
            webViewRef?.canGoBack() == true -> webViewRef?.goBack()
            navController.previousBackStackEntry != null -> navController.popBackStack()
            else -> navigateToHome()
        }
    }

    // Track page loading time to assist user if it takes too long
    LaunchedEffect(isPageLoading) {
        if (isPageLoading) {
            loadDurationSeconds = 0
            while (isPageLoading) {
                delay(1000)
                loadDurationSeconds++
            }
        }
    }

    // Continuously monitor CookieManager for the sp_dc session cookie
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            if (tokenFetchStarted.get()) continue
            val spDc = extractCookie("sp_dc", webViewRef?.url)
            val spKey = extractCookie("sp_key", webViewRef?.url) ?: ""
            if (!spDc.isNullOrBlank() && tokenFetchStarted.compareAndSet(false, true)) {
                processLoginSession(
                    context = context,
                    spDc = spDc,
                    spKey = spKey,
                    scope = scope,
                    setProcessing = { isProcessing = it },
                    setStatus = { statusMessage = it },
                    setError = { hasError = it },
                    onComplete = { success ->
                        if (!success) {
                            tokenFetchStarted.set(false)
                        }
                    },
                    onSuccess = navigateToHome,
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(BACKGROUND_DARK))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Color(CARD_DARK))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (navController.previousBackStackEntry != null) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }
                } else if (selectedTab != LoginTab.NATIVE) {
                    IconButton(onClick = { selectedTab = LoginTab.NATIVE }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Native Login",
                            tint = Color.White,
                        )
                    }
                } else {
                    Spacer(Modifier.width(10.dp))
                }

                Icon(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "Spotify",
                    tint = Color(SPOTIFY_GREEN),
                    modifier = Modifier.size(26.dp),
                )

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Spotify Sign In",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                    )
                    Text(
                        text = if (statusMessage.isNotBlank()) statusMessage else "Connect your Spotify library",
                        color = if (hasError) Color(0xFFE57373) else Color(TEXT_SECONDARY),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Skip / Guest Mode Button
                TextButton(
                    onClick = navigateToHome,
                    enabled = !isProcessing,
                ) {
                    Text(
                        text = "Continue as Guest",
                        color = Color(SPOTIFY_GREEN),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                    )
                }
            }

            // Tab Selector: Native Form | Web Browser | Cookie Entry
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF181818))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LoginTabPill(
                    label = "Native Login",
                    isSelected = selectedTab == LoginTab.NATIVE,
                    onClick = { selectedTab = LoginTab.NATIVE },
                    modifier = Modifier.weight(1f),
                )

                LoginTabPill(
                    label = "Web Browser",
                    isSelected = selectedTab == LoginTab.WEB,
                    onClick = { selectedTab = LoginTab.WEB },
                    modifier = Modifier.weight(1f),
                )

                LoginTabPill(
                    label = "Cookie (sp_dc)",
                    isSelected = selectedTab == LoginTab.COOKIE,
                    onClick = { selectedTab = LoginTab.COOKIE },
                    modifier = Modifier.weight(1f),
                )
            }

            // ── View 1: Native Jetpack Compose Login UI (Default View) ──
            if (selectedTab == LoginTab.NATIVE) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E3A25)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.logo),
                            contentDescription = "Spotify Logo",
                            tint = Color(SPOTIFY_GREEN),
                            modifier = Modifier.size(42.dp),
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = "Log in to Spotify",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = "Stream millions of songs, playlists & lossless audio seamlessly",
                        color = Color(TEXT_SECONDARY),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(28.dp))

                    // Email / Username Field
                    OutlinedTextField(
                        value = emailOrUsername,
                        onValueChange = {
                            emailOrUsername = it
                            nativeLoginError = null
                        },
                        label = { Text("Email or Username", color = Color(TEXT_SECONDARY)) },
                        placeholder = { Text("spotifyuser@example.com", color = Color(0xFF555555)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Color(SPOTIFY_GREEN)
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(SPOTIFY_GREEN),
                            focusedBorderColor = Color(SPOTIFY_GREEN),
                            unfocusedBorderColor = Color(0xFF444444),
                            focusedContainerColor = Color(0xFF181818),
                            unfocusedContainerColor = Color(0xFF181818),
                        ),
                        shape = RoundedCornerShape(12.dp),
                    )

                    Spacer(Modifier.height(14.dp))

                    // Password Field
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            nativeLoginError = null
                        },
                        label = { Text("Password", color = Color(TEXT_SECONDARY)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(SPOTIFY_GREEN)
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                    tint = Color(TEXT_SECONDARY)
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(SPOTIFY_GREEN),
                            focusedBorderColor = Color(SPOTIFY_GREEN),
                            unfocusedBorderColor = Color(0xFF444444),
                            focusedContainerColor = Color(0xFF181818),
                            unfocusedContainerColor = Color(0xFF181818),
                        ),
                        shape = RoundedCornerShape(12.dp),
                    )

                    nativeLoginError?.let { err ->
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = err,
                            color = Color(0xFFE57373),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    // Log In Button
                    Button(
                        onClick = {
                            if (emailOrUsername.isBlank() || password.isBlank()) {
                                nativeLoginError = "Please enter both email/username and password"
                                return@Button
                            }
                            // Switch to Web Browser mode to complete official Spotify OAuth login sequence
                            selectedTab = LoginTab.WEB
                        },
                        enabled = !isProcessing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(SPOTIFY_GREEN),
                            contentColor = Color.Black,
                        ),
                        shape = RoundedCornerShape(25.dp),
                    ) {
                        Text(
                            text = "Log In",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Divider(modifier = Modifier.weight(1f), color = Color(0xFF333333))
                        Text(
                            text = "  OR ALTERNATIVES  ",
                            color = Color(0xFF777777),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Divider(modifier = Modifier.weight(1f), color = Color(0xFF333333))
                    }

                    Spacer(Modifier.height(20.dp))

                    // Option 1: Log in with Spotify Web Browser
                    OutlinedButton(
                        onClick = { selectedTab = LoginTab.WEB },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF444444)),
                    ) {
                        Text("🌐 Log in with Spotify Web Browser", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }

                    Spacer(Modifier.height(10.dp))

                    // Option 2: Paste sp_dc Cookie
                    OutlinedButton(
                        onClick = { showCookieDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(SPOTIFY_GREEN)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E3A25)),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color(SPOTIFY_GREEN)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("🔑 Paste sp_dc Cookie", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }

                    Spacer(Modifier.height(10.dp))

                    // Option 3: Continue as Guest / Demo Mode
                    TextButton(
                        onClick = navigateToHome,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Continue as Guest (Skip Sign-In)",
                            color = Color(TEXT_SECONDARY),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                        )
                    }
                }
            }

            // ── View 2: Web Browser Login View ──
            if (selectedTab == LoginTab.WEB) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    // Toolbar for WebView (reload, title, external browser)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF161616))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            IconButton(
                                onClick = { selectedTab = LoginTab.NATIVE },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Column {
                                Text(
                                    text = "Spotify Web Login",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                )
                                Text(
                                    text = if (isPageLoading) "Loading $pageProgress%" else "accounts.spotify.com",
                                    color = Color(TEXT_SECONDARY),
                                    fontSize = 11.sp,
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            IconButton(
                                onClick = {
                                    hasWebError = false
                                    isPageLoading = true
                                    webViewRef?.reload()
                                },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reload",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }

                            IconButton(
                                onClick = openInExternalBrowser,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Open in Chrome/Browser",
                                    tint = Color(SPOTIFY_GREEN),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }

                    // Progress indicator during page load
                    if (isPageLoading && pageProgress in 1..99) {
                        LinearProgressIndicator(
                            progress = { pageProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                            color = Color(SPOTIFY_GREEN),
                            trackColor = Color(0xFF282828),
                        )
                    }

                    // WebView Container with fallback and loading state
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color(BACKGROUND_DARK)),
                        contentAlignment = Alignment.Center,
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                try {
                                    val cookieManager = runCatching { CookieManager.getInstance() }.getOrNull()
                                    cookieManager?.setAcceptCookie(true)

                                    WebView(ctx).apply {
                                        webViewRef = this
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                        )
                                        setBackgroundColor(android.graphics.Color.parseColor("#121212"))
                                        cookieManager?.setAcceptThirdPartyCookies(this, true)

                                        settings.apply {
                                            javaScriptEnabled = true
                                            domStorageEnabled = true
                                            databaseEnabled = true
                                            useWideViewPort = true
                                            loadWithOverviewMode = true
                                            userAgentString = DESKTOP_USER_AGENT
                                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                            cacheMode = WebSettings.LOAD_DEFAULT
                                            javaScriptCanOpenWindowsAutomatically = true
                                            setSupportMultipleWindows(false)
                                        }

                                        webChromeClient = object : WebChromeClient() {
                                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                                pageProgress = newProgress
                                                if (newProgress >= 90) {
                                                    isPageLoading = false
                                                }
                                            }

                                            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                                Timber.d("SpotifyWeb [${consoleMessage?.messageLevel()}]: ${consoleMessage?.message()}")
                                                return true
                                            }
                                        }

                                        webViewClient = object : WebViewClient() {
                                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                                isPageLoading = true
                                                hasWebError = false
                                            }

                                            override fun onPageFinished(view: WebView?, url: String?) {
                                                isPageLoading = false
                                                runCatching { CookieManager.getInstance().flush() }
                                            }

                                            override fun onReceivedError(
                                                view: WebView?,
                                                request: WebResourceRequest?,
                                                error: WebResourceError?,
                                            ) {
                                                if (request?.isForMainFrame == true) {
                                                    hasWebError = true
                                                    webErrorMessage = error?.description?.toString()
                                                        ?: "Unable to load Spotify login page."
                                                }
                                            }

                                            override fun onReceivedSslError(
                                                view: WebView?,
                                                handler: SslErrorHandler?,
                                                error: SslError?,
                                            ) {
                                                Timber.w("SSL Error encountered in WebView: $error")
                                                handler?.proceed()
                                            }

                                            override fun onRenderProcessGone(
                                                view: WebView?,
                                                detail: RenderProcessGoneDetail?,
                                            ): Boolean {
                                                Timber.e("WebView render process gone")
                                                hasWebError = true
                                                webErrorMessage = "System WebView crashed. Please use Cookie Login."
                                                return true
                                            }

                                            override fun shouldOverrideUrlLoading(
                                                view: WebView?,
                                                request: WebResourceRequest?,
                                            ): Boolean {
                                                val url = request?.url?.toString().orEmpty()
                                                if (url.startsWith("http://") || url.startsWith("https://")) {
                                                    return false
                                                }
                                                return true
                                            }
                                        }

                                        loadUrl(SpotifyAuth.LOGIN_URL)
                                    }
                                } catch (t: Throwable) {
                                    Timber.e(t, "System WebView failed to initialize")
                                    com.music.spotui.util.AppDiagnostics.error("SpotifyLogin", "System WebView unavailable", t)
                                    hasWebError = true
                                    webErrorMessage = "System WebView is unavailable or disabled on this device (${t.localizedMessage ?: "initialization error"}). Please use Cookie Login."
                                    isPageLoading = false
                                    android.view.View(ctx)
                                }
                            },
                        )

                        // Loading spinner overlay
                        if (isPageLoading && !hasWebError) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(BACKGROUND_DARK).copy(alpha = 0.9f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        color = Color(SPOTIFY_GREEN),
                                        strokeWidth = 3.dp,
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        text = "Loading Spotify Login ($pageProgress%)…",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }

                        // Web Error Recovery Card
                        if (hasWebError) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth(0.92f)
                                    .padding(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(CARD_DARK)),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Color(0xFFE57373),
                                        modifier = Modifier.size(36.dp),
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        text = "Login Page Error",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        textAlign = TextAlign.Center,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = webErrorMessage.ifBlank { "Network connection issue or WebView provider restriction." },
                                        color = Color(TEXT_SECONDARY),
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center,
                                    )
                                    Spacer(Modifier.height(16.dp))

                                    Button(
                                        onClick = {
                                            hasWebError = false
                                            isPageLoading = true
                                            webViewRef?.reload()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(SPOTIFY_GREEN),
                                            contentColor = Color.Black,
                                        ),
                                    ) {
                                        Text("Retry Page Load", fontWeight = FontWeight.Bold)
                                    }

                                    Spacer(Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        OutlinedButton(
                                            onClick = { showCookieDialog = true },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(SPOTIFY_GREEN)),
                                        ) {
                                            Text("Paste Cookie", fontSize = 12.sp)
                                        }
                                        OutlinedButton(
                                            onClick = openInExternalBrowser,
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                        ) {
                                            Text("Open Chrome", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── View 3: Direct Cookie Entry View (sp_dc) ──
            if (selectedTab == LoginTab.COOKIE) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E3A25)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.logo),
                            contentDescription = "Spotify",
                            tint = Color(SPOTIFY_GREEN),
                            modifier = Modifier.size(34.dp),
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = "Sign In with Session Cookie",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "Bypass browser restrictions by providing your Spotify 'sp_dc' session cookie directly.",
                        color = Color(TEXT_SECONDARY),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                    )

                    Spacer(Modifier.height(20.dp))

                    // Cookie input text field
                    OutlinedTextField(
                        value = cookieInputValue,
                        onValueChange = {
                            cookieInputValue = it
                            cookieError = null
                        },
                        label = { Text("Spotify sp_dc Cookie", color = Color(TEXT_SECONDARY)) },
                        placeholder = {
                            Text("AQD... or raw sp_dc token string", color = Color(0xFF666666), fontSize = 13.sp)
                        },
                        singleLine = false,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(SPOTIFY_GREEN),
                            focusedBorderColor = Color(SPOTIFY_GREEN),
                            unfocusedBorderColor = Color(0xFF4A4A4A),
                            focusedContainerColor = Color(0xFF141414),
                            unfocusedContainerColor = Color(0xFF141414),
                        ),
                        shape = RoundedCornerShape(10.dp),
                    )

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = clipboard?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                                if (clip.isNotBlank()) {
                                    cookieInputValue = cleanSpDcCookie(clip)
                                    cookieError = null
                                }
                            },
                        ) {
                            Text("📋 Paste from Clipboard", color = Color(SPOTIFY_GREEN), fontSize = 13.sp)
                        }

                        if (cookieInputValue.isNotBlank()) {
                            TextButton(onClick = { cookieInputValue = "" }) {
                                Text("Clear", color = Color(0xFF888888), fontSize = 13.sp)
                            }
                        }
                    }

                    cookieError?.let { err ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = err,
                            color = Color(0xFFE57373),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    // Connect button
                    Button(
                        onClick = {
                            val cleanCookie = cleanSpDcCookie(cookieInputValue)
                            if (cleanCookie.isBlank()) {
                                cookieError = "Please enter or paste your sp_dc cookie"
                                return@Button
                            }
                            processLoginSession(
                                context = context,
                                spDc = cleanCookie,
                                spKey = "",
                                scope = scope,
                                setProcessing = { isProcessing = it },
                                setStatus = { statusMessage = it },
                                setError = { hasError = it },
                                onComplete = { success ->
                                    if (!success) {
                                        cookieError = statusMessage
                                    }
                                },
                                onSuccess = navigateToHome,
                            )
                        },
                        enabled = !isProcessing && cookieInputValue.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(SPOTIFY_GREEN),
                            contentColor = Color.Black,
                        ),
                        shape = RoundedCornerShape(25.dp),
                    ) {
                        Text("Connect with Cookie", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    Spacer(Modifier.height(16.dp))

                    TextButton(onClick = navigateToHome) {
                        Text("Continue as Guest", color = Color(TEXT_SECONDARY), fontSize = 13.sp)
                    }
                }
            }

            // Bottom status & quick shortcut strip
            Surface(
                color = Color(0xFF141414),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Need alternative? ",
                        color = Color(0xFF888888),
                        fontSize = 12.sp,
                    )
                    Text(
                        text = "Paste Cookie",
                        color = Color(SPOTIFY_GREEN),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { showCookieDialog = true }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                    Text(
                        text = " • ",
                        color = Color(0xFF555555),
                        fontSize = 12.sp,
                    )
                    Text(
                        text = "Continue as Guest",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(onClick = navigateToHome)
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
        }

        // ── Interactive Paste sp_dc Cookie Dialog ──
        if (showCookieDialog) {
            AlertDialog(
                onDismissRequest = { showCookieDialog = false },
                containerColor = Color(CARD_DARK),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.logo),
                            contentDescription = null,
                            tint = Color(SPOTIFY_GREEN),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Paste Session Cookie (sp_dc)", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column {
                        Text(
                            text = "Provide your Spotify sp_dc session cookie token to authenticate instantly.",
                            color = Color(TEXT_SECONDARY),
                            fontSize = 12.sp,
                        )
                        Spacer(Modifier.height(14.dp))

                        OutlinedTextField(
                            value = dialogCookieInput,
                            onValueChange = {
                                dialogCookieInput = it
                                dialogCookieError = null
                            },
                            label = { Text("sp_dc Cookie Value", color = Color(TEXT_SECONDARY)) },
                            placeholder = { Text("AQD...", color = Color(0xFF555555)) },
                            singleLine = false,
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color(SPOTIFY_GREEN),
                                focusedBorderColor = Color(SPOTIFY_GREEN),
                                unfocusedBorderColor = Color(0xFF555555),
                                focusedContainerColor = Color(0xFF141414),
                                unfocusedContainerColor = Color(0xFF141414),
                            ),
                            shape = RoundedCornerShape(8.dp),
                        )

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clip = clipboard?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                                    if (clip.isNotBlank()) {
                                        dialogCookieInput = cleanSpDcCookie(clip)
                                        dialogCookieError = null
                                    }
                                },
                            ) {
                                Text("📋 Paste Clipboard", color = Color(SPOTIFY_GREEN), fontSize = 12.sp)
                            }

                            if (dialogCookieInput.isNotBlank()) {
                                TextButton(onClick = { dialogCookieInput = "" }) {
                                    Text("Clear", color = Color(0xFF888888), fontSize = 12.sp)
                                }
                            }
                        }

                        dialogCookieError?.let { err ->
                            Spacer(Modifier.height(4.dp))
                            Text(err, color = Color(0xFFE57373), fontSize = 12.sp)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val cleanCookie = cleanSpDcCookie(dialogCookieInput)
                            if (cleanCookie.isBlank()) {
                                dialogCookieError = "Please enter or paste your sp_dc cookie"
                                return@Button
                            }
                            showCookieDialog = false
                            processLoginSession(
                                context = context,
                                spDc = cleanCookie,
                                spKey = "",
                                scope = scope,
                                setProcessing = { isProcessing = it },
                                setStatus = { statusMessage = it },
                                setError = { hasError = it },
                                onComplete = { success ->
                                    if (!success) {
                                        dialogCookieError = statusMessage
                                        showCookieDialog = true
                                    }
                                },
                                onSuccess = navigateToHome,
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(SPOTIFY_GREEN),
                            contentColor = Color.Black,
                        ),
                    ) {
                        Text("Connect", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCookieDialog = false }) {
                        Text("Cancel", color = Color.White)
                    }
                }
            )
        }

        // Active connection progress overlay
        if (isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Card(
                    modifier = Modifier.padding(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(CARD_DARK)),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(
                            color = Color(SPOTIFY_GREEN),
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(38.dp),
                        )
                        Spacer(Modifier.height(18.dp))
                        Text(
                            text = statusMessage.ifBlank { "Authenticating with Spotify…" },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginTabPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (isSelected) Color(SPOTIFY_GREEN) else Color(0xFF262626))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.Black else Color.White,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Normalizes user-pasted cookie strings, handling prefixes like 'sp_dc=' or trailing attributes.
 */
private fun cleanSpDcCookie(raw: String): String {
    var str = raw.trim()
    if (str.startsWith("sp_dc=", ignoreCase = true)) {
        str = str.substring(6).trim()
    }
    return str.split(";").first().trim().trim('"')
}

/**
 * Checks all relevant Spotify domains in CookieManager for the requested cookie.
 */
private fun extractCookie(name: String, currentUrl: String? = null): String? {
    val manager = runCatching { CookieManager.getInstance() }.getOrNull() ?: return null
    val urls = buildList {
        currentUrl?.takeIf { it.isNotBlank() }?.let { add(it) }
        add("https://accounts.spotify.com")
        add("https://open.spotify.com")
        add("https://spotify.com")
        add(".spotify.com")
    }

    for (url in urls) {
        val allCookies = runCatching { manager.getCookie(url) }.getOrNull() ?: continue
        val match = allCookies.split(";")
            .mapNotNull {
                val parts = it.trim().split("=", limit = 2)
                if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
            }
            .firstOrNull { it.first.equals(name, ignoreCase = true) && it.second.isNotBlank() }
            ?.second

        if (!match.isNullOrBlank()) return match
    }
    return null
}

/**
 * Exchanges the Spotify sp_dc session cookie for an authenticated access token
 * and initializes application playback and session credentials upon success.
 */
private fun processLoginSession(
    context: Context,
    spDc: String,
    spKey: String = "",
    scope: kotlinx.coroutines.CoroutineScope,
    setProcessing: (Boolean) -> Unit,
    setStatus: (String) -> Unit,
    setError: (Boolean) -> Unit,
    onComplete: (Boolean) -> Unit,
    onSuccess: () -> Unit,
) {
    setProcessing(true)
    setError(false)
    setStatus("Connecting to Spotify…")

    scope.launch(Dispatchers.IO) {
        var lastError: Throwable? = null
        repeat(3) { attempt ->
            val result = SpotifyAuth.fetchAccessToken(spDc, spKey)
            result.onSuccess { token ->
                SpotifySession.setSpDc(context, spDc)
                Spotify.accessToken = token.accessToken
                withContext(Dispatchers.Main) {
                    setStatus("Signed in successfully!")
                    setProcessing(false)
                    setError(false)
                    onComplete(true)
                }
                delay(300)
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
                return@launch
            }.onFailure { e ->
                lastError = e
                Timber.e(e, "Spotify token fetch failed (attempt ${attempt + 1})")
                if (attempt < 2) delay(800)
            }
        }
        withContext(Dispatchers.Main) {
            val errorMsg = lastError?.message ?: "Unable to verify Spotify session"
            setStatus("Login failed: $errorMsg")
            setError(true)
            setProcessing(false)
            onComplete(false)
        }
    }
}
