package com.music.spotui.ui.screens

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Message
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.metrolist.spotify.SpotifyAuth
import com.music.spotui.R
import com.music.spotui.audio.UiSoundEngine
import com.music.spotui.di.SpotifyWebPlayer
import com.music.spotui.ui.navigation.Routes
import com.music.spotui.util.DevConsoleManager
import kotlinx.coroutines.delay
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Full Spotify authentication screen with:
 * 1. Native Compose Login UI (with credential handoff + 1-tap Email Code login)
 * 2. Embedded Chrome-sanitized Android WebView (authentic hardware UA, OAuth popups,
 *    automatic DOM error/CAPTCHA detection, auto /status -> open.spotify.com sync)
 * 3. Smart Cookie Importer (raw sp_dc, HTTP Cookie header, Cookie-Editor JSON, Netscape)
 * 4. Real-time in-screen Live Auth Diagnostics log viewer wired to DevConsoleManager
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SpotifyLoginScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val allLogs by DevConsoleManager.logs.collectAsStateWithLifecycle()
    val allFaults by DevConsoleManager.faults.collectAsStateWithLifecycle()
    val authFaultCount = remember(allFaults) {
        allFaults.count {
            !it.resolved && (it.component.contains("Spotify", ignoreCase = true) ||
                it.component.contains("Login", ignoreCase = true))
        }
    }

    var selectedTab by remember { mutableStateOf(LoginTab.NATIVE) }

    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }

    // Native Form Input State
    var emailOrUsername by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var nativeLoginError by remember { mutableStateOf<String?>(null) }

    // Transient handoff from Native -> WebView (wiped immediately once injected)
    var pendingHandoff by remember { mutableStateOf<PendingCredentialHandoff?>(null) }

    // Cookie Dialog & Diagnostics Dialog State
    var showCookieDialog by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }
    var dialogCookieInput by remember { mutableStateOf("") }
    var dialogCookieError by remember { mutableStateOf<String?>(null) }

    // Web Browser State
    var uaMode by remember { mutableStateOf(WebUaMode.ANDROID_CHROME) }
    var isPageLoading by remember { mutableStateOf(true) }
    var pageProgress by remember { mutableIntStateOf(0) }
    var hasWebError by remember { mutableStateOf(false) }
    var webErrorMessage by remember { mutableStateOf("") }
    var currentHostLabel by remember { mutableStateOf("accounts.spotify.com") }
    var domAuthBanner by remember { mutableStateOf<String?>(null) }
    var domHasOtpPrompt by remember { mutableStateOf(false) }
    var lastLoggedDomError by remember { mutableStateOf("") }

    // OAuth Popup WebView (Google/Facebook/Apple SSO)
    var popupWebView by remember { mutableStateOf<WebView?>(null) }

    // Direct Cookie tab inputs
    var cookieInputValue by remember { mutableStateOf("") }
    var cookieError by remember { mutableStateOf<String?>(null) }

    val tokenFetchStarted = remember { AtomicBoolean(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(Unit) {
        DevConsoleManager.logInfo(
            "SpotifyLogin",
            "Login screen opened (defaultUa=${uaMode.shortLabel})",
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            pendingHandoff = null
            runCatching { popupWebView?.destroy() }
            popupWebView = null
        }
    }

    val navigateToHome: () -> Unit = {
        pendingHandoff = null
        runCatching { SpotifyWebPlayer.refreshLogin(context) }
        runCatching {
            navController.navigate(Routes.Home.route) {
                popUpTo(Routes.Login.route) { inclusive = true }
            }
        }
    }

    val openInExternalBrowser: () -> Unit = {
        DevConsoleManager.logButtonAction("SpotifyLogin", "OpenExternalBrowser", SpotifyAuth.LOGIN_URL)
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(SpotifyAuth.LOGIN_URL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure { err ->
            Timber.e(err, "Failed to open external browser")
            DevConsoleManager.logError("SpotifyLogin", "Failed opening external browser: ${err.message}", err)
        }
    }

    fun tryExchangeCapturedCookies(source: String) {
        if (tokenFetchStarted.get()) return
        val sweep = runCatching { sweepSpotifyCookies(webViewRef?.url) }.getOrNull() ?: return
        val spDc = sweep.spDc
        if (!spDc.isNullOrBlank() && tokenFetchStarted.compareAndSet(false, true)) {
            pendingHandoff = null
            password = ""
            DevConsoleManager.logInfo(
                "SpotifyLogin",
                "Captured sp_dc from WebView CookieManager ($source, keys=${sweep.totalCookieKeysFound}, sp_dc_len=${spDc.length})",
            )
            processLoginSession(
                context = context,
                spDc = spDc,
                spKey = sweep.spKey,
                source = source,
                scope = scope,
                setProcessing = { isProcessing = it },
                setStatus = { statusMessage = it },
                setError = { hasError = it },
                onComplete = { success ->
                    if (!success) tokenFetchStarted.set(false)
                },
                onSuccess = navigateToHome,
            )
        }
    }

    fun applyUaAndReload(nextMode: WebUaMode) {
        uaMode = nextMode
        val resolvedUa = resolveUserAgent(context, nextMode)
        DevConsoleManager.logInfo(
            "SpotifyLogin",
            "Switched WebView UA to ${nextMode.shortLabel} (prefix=${resolvedUa.take(48)}...)",
        )
        domAuthBanner = null
        lastLoggedDomError = ""
        hasWebError = false
        isPageLoading = true
        webViewRef?.let { wv ->
            wv.settings.userAgentString = resolvedUa
            wv.loadUrl(SpotifyAuth.LOGIN_URL)
        }
    }

    BackHandler {
        when {
            popupWebView != null -> {
                runCatching { popupWebView?.destroy() }
                popupWebView = null
            }
            showLogsDialog -> showLogsDialog = false
            showCookieDialog -> showCookieDialog = false
            selectedTab == LoginTab.WEB && webViewRef?.canGoBack() == true -> webViewRef?.goBack()
            selectedTab != LoginTab.NATIVE -> selectedTab = LoginTab.NATIVE
            navController.previousBackStackEntry != null -> navController.popBackStack()
            else -> navigateToHome()
        }
    }

    // Monitor CookieManager continuously while screen is active
    LaunchedEffect(Unit) {
        while (true) {
            delay(900)
            if (!tokenFetchStarted.get()) {
                tryExchangeCapturedCookies("WebViewAutoSync")
            }
        }
    }

    // Monitor live Spotify DOM alerts, password rejection messages, CAPTCHAs & OTP prompts
    LaunchedEffect(selectedTab, webViewRef) {
        while (selectedTab == LoginTab.WEB && webViewRef != null) {
            delay(1400)
            val wv = webViewRef ?: break
            wv.evaluateJavascript(buildDomErrorProbeJs()) { raw ->
                val cleaned = raw?.trim()?.removeSurrounding("\"")
                    ?.replace("\\\"", "\"")
                    ?.replace("\\\\", "\\")
                    ?: return@evaluateJavascript
                if (!cleaned.startsWith("{")) return@evaluateJavascript
                runCatching {
                    val obj = JSONObject(cleaned)
                    val alerts = obj.optJSONArray("alerts")
                    val firstAlert = (0 until (alerts?.length() ?: 0))
                        .mapNotNull { alerts?.optString(it)?.takeIf { s -> s.isNotBlank() } }
                        .firstOrNull()
                    val hasCaptcha = obj.optBoolean("captcha", false)
                    val hasOtp = obj.optBoolean("otpPrompt", false)
                    domHasOtpPrompt = hasOtp

                    val combined = when {
                        !firstAlert.isNullOrBlank() -> firstAlert
                        hasCaptcha -> "Spotify security challenge/CAPTCHA active — complete verification below or try Email Code."
                        else -> null
                    }
                    domAuthBanner = combined

                    if (!combined.isNullOrBlank() && combined != lastLoggedDomError) {
                        lastLoggedDomError = combined
                        DevConsoleManager.logError(
                            "SpotifyWebAuth",
                            "Login page reported: $combined (host=$currentHostLabel, ua=${uaMode.shortLabel})",
                        )
                        DevConsoleManager.reportComponentFault(
                            component = "SpotifyLogin",
                            action = "WebPageAuthReject",
                            reason = "$combined [UA=${uaMode.shortLabel}]",
                            autoFixHint = "Use 'Email Code (No Password)', switch UA profile, or reset WebView cookies.",
                        )
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(LOGIN_BACKGROUND_DARK)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .background(Color(LOGIN_CARD_DARK))
                    .padding(horizontal = 6.dp),
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
                    tint = Color(LOGIN_SPOTIFY_GREEN),
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
                        text = statusMessage.ifBlank { "Connect your Spotify library" },
                        color = if (hasError) Color(0xFFE57373) else Color(LOGIN_TEXT_SECONDARY),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Live Login Diagnostics Button
                IconButton(
                    onClick = {
                        UiSoundEngine.playTapChime(context)
                        showLogsDialog = true
                    },
                    modifier = Modifier.testTag("login_open_logs_button"),
                ) {
                    BadgedBox(
                        badge = {
                            if (authFaultCount > 0) {
                                Badge(containerColor = Color(0xFFFF5252), contentColor = Color.White) {
                                    Text(authFaultCount.toString(), fontSize = 9.sp)
                                }
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = "View Live Login Logs",
                            tint = if (authFaultCount > 0) Color(0xFFFFB74D) else Color(LOGIN_TEXT_SECONDARY),
                        )
                    }
                }

                // Guest Mode Button
                TextButton(
                    onClick = {
                        DevConsoleManager.logButtonAction("SpotifyLogin", "ContinueAsGuest")
                        navigateToHome()
                    },
                    enabled = !isProcessing,
                ) {
                    Text(
                        text = "Continue as Guest",
                        color = Color(LOGIN_SPOTIFY_GREEN),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                    )
                }
            }

            // Tab Selector
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
                    onClick = {
                        UiSoundEngine.playTabSound(context)
                        DevConsoleManager.logButtonAction("SpotifyLogin", "TabSwitch", "NATIVE")
                        selectedTab = LoginTab.NATIVE
                    },
                    modifier = Modifier.weight(1f).testTag("login_tab_native"),
                )

                LoginTabPill(
                    label = "Web Browser",
                    isSelected = selectedTab == LoginTab.WEB,
                    onClick = {
                        UiSoundEngine.playTabSound(context)
                        DevConsoleManager.logButtonAction("SpotifyLogin", "TabSwitch", "WEB")
                        selectedTab = LoginTab.WEB
                    },
                    modifier = Modifier.weight(1f).testTag("login_tab_web"),
                )

                LoginTabPill(
                    label = "Cookie (sp_dc)",
                    isSelected = selectedTab == LoginTab.COOKIE,
                    onClick = {
                        UiSoundEngine.playTabSound(context)
                        DevConsoleManager.logButtonAction("SpotifyLogin", "TabSwitch", "COOKIE")
                        selectedTab = LoginTab.COOKIE
                    },
                    modifier = Modifier.weight(1f).testTag("login_tab_cookie"),
                )
            }

            // ── View 1: Native Login Tab ──
            if (selectedTab == LoginTab.NATIVE) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E3A25)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.logo),
                            contentDescription = "Spotify Logo",
                            tint = Color(LOGIN_SPOTIFY_GREEN),
                            modifier = Modifier.size(40.dp),
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    Text(
                        text = "Log in to Spotify",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = "Stream playlists & lossless audio with authentic Android Chrome verification",
                        color = Color(LOGIN_TEXT_SECONDARY),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(22.dp))

                    OutlinedTextField(
                        value = emailOrUsername,
                        onValueChange = {
                            emailOrUsername = it
                            nativeLoginError = null
                        },
                        label = { Text("Email or Username", color = Color(LOGIN_TEXT_SECONDARY)) },
                        placeholder = { Text("spotifyuser@example.com", color = Color(0xFF555555)) },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Color(LOGIN_SPOTIFY_GREEN))
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("native_email_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(LOGIN_SPOTIFY_GREEN),
                            focusedBorderColor = Color(LOGIN_SPOTIFY_GREEN),
                            unfocusedBorderColor = Color(0xFF444444),
                            focusedContainerColor = Color(0xFF181818),
                            unfocusedContainerColor = Color(0xFF181818),
                        ),
                        shape = RoundedCornerShape(12.dp),
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            nativeLoginError = null
                        },
                        label = { Text("Password (optional for Email Code)", color = Color(LOGIN_TEXT_SECONDARY)) },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color(LOGIN_SPOTIFY_GREEN))
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                    tint = Color(LOGIN_TEXT_SECONDARY),
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("native_password_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(LOGIN_SPOTIFY_GREEN),
                            focusedBorderColor = Color(LOGIN_SPOTIFY_GREEN),
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

                    Spacer(Modifier.height(18.dp))

                    // Guided Credential Submit Button
                    Button(
                        onClick = {
                            val trimmedUser = emailOrUsername.trim()
                            if (trimmedUser.isBlank() || password.isBlank()) {
                                nativeLoginError = "Enter both email/username and password — or tap Email Code below."
                                DevConsoleManager.logWarning("SpotifyLogin", "Native submit blocked: missing email or password")
                                return@Button
                            }
                            DevConsoleManager.logInfo(
                                "SpotifyLogin",
                                "Native login handoff started for user=${maskEmailForLog(trimmedUser)} (ua=${uaMode.shortLabel})",
                            )
                            statusMessage = "Auto-filling Spotify credentials…"
                            hasError = false
                            pendingHandoff = PendingCredentialHandoff(
                                emailOrUsername = trimmedUser,
                                password = password,
                                preferEmailCode = false,
                            )
                            selectedTab = LoginTab.WEB
                        },
                        enabled = !isProcessing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("native_login_submit_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(LOGIN_SPOTIFY_GREEN),
                            contentColor = Color.Black,
                        ),
                        shape = RoundedCornerShape(25.dp),
                    ) {
                        Text("Log In", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    Spacer(Modifier.height(10.dp))

                    // Passwordless 6-Digit Email Code (bypasses password challenges cleanly!)
                    OutlinedButton(
                        onClick = {
                            val trimmedUser = emailOrUsername.trim()
                            DevConsoleManager.logInfo(
                                "SpotifyLogin",
                                "Starting Passwordless 6-Digit Email Code flow (user=${maskEmailForLog(trimmedUser)})",
                            )
                            statusMessage = "Opening Spotify 6-digit email code login…"
                            hasError = false
                            pendingHandoff = if (trimmedUser.isNotBlank()) {
                                PendingCredentialHandoff(emailOrUsername = trimmedUser, preferEmailCode = true)
                            } else null
                            selectedTab = LoginTab.WEB
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("native_email_code_button"),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(LOGIN_SPOTIFY_GREEN)),
                        border = BorderStroke(1.dp, Color(0xFF285534)),
                    ) {
                        Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Log in with Email Code (No Password)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(Modifier.height(18.dp))

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF333333))
                        Text("  OR ALTERNATIVES  ", color = Color(0xFF777777), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF333333))
                    }

                    Spacer(Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = {
                            DevConsoleManager.logButtonAction("SpotifyLogin", "OpenWebBrowserButton")
                            selectedTab = LoginTab.WEB
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFF444444)),
                    ) {
                        Text("Log in with Spotify Web Browser", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }

                    Spacer(Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { showCookieDialog = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(LOGIN_SPOTIFY_GREEN)),
                        border = BorderStroke(1.dp, Color(0xFF1E3A25)),
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Paste Cookie or Cookie-Editor JSON", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }

                    Spacer(Modifier.height(10.dp))

                    TextButton(
                        onClick = navigateToHome,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text("Continue as Guest (Skip Sign-In)", color = Color(LOGIN_TEXT_SECONDARY), fontSize = 13.sp)
                    }
                }
            }

            // ── View 2: Authentic Android Chrome WebView Login ──
            if (selectedTab == LoginTab.WEB) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    // WebView Toolbar with UA Mode Chip, Cookie Reset, Manual Sync & Reload
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF161616))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f),
                        ) {
                            IconButton(
                                onClick = { selectedTab = LoginTab.NATIVE },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to Native",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Spotify Web (${uaMode.shortLabel})",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = if (isPageLoading) "Loading $pageProgress% · $currentHostLabel" else currentHostLabel,
                                    color = Color(LOGIN_TEXT_SECONDARY),
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // UA Toggle Pill
                            Text(
                                text = when (uaMode) {
                                    WebUaMode.ANDROID_CHROME -> "UA: Chrome"
                                    WebUaMode.SYSTEM_WEBVIEW -> "UA: System"
                                    WebUaMode.DESKTOP_CHROME -> "UA: Desktop"
                                },
                                color = Color(LOGIN_SPOTIFY_GREEN),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF213126))
                                    .clickable {
                                        val next = when (uaMode) {
                                            WebUaMode.ANDROID_CHROME -> WebUaMode.SYSTEM_WEBVIEW
                                            WebUaMode.SYSTEM_WEBVIEW -> WebUaMode.DESKTOP_CHROME
                                            WebUaMode.DESKTOP_CHROME -> WebUaMode.ANDROID_CHROME
                                        }
                                        applyUaAndReload(next)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .testTag("webview_ua_switch"),
                            )

                            IconButton(
                                onClick = {
                                    tryExchangeCapturedCookies("ManualSync")
                                    val sweep = sweepSpotifyCookies(webViewRef?.url)
                                    if (sweep.spDc.isNullOrBlank()) {
                                        statusMessage = "No sp_dc yet (${sweep.totalCookieKeysFound} keys)"
                                        webViewRef?.loadUrl("https://open.spotify.com/")
                                    }
                                },
                                modifier = Modifier.size(48.dp).testTag("webview_sync_cookie"),
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = "Sync Session Cookie", tint = Color(LOGIN_SPOTIFY_GREEN), modifier = Modifier.size(18.dp))
                            }

                            IconButton(
                                onClick = {
                                    hasWebError = false
                                    domAuthBanner = null
                                    isPageLoading = true
                                    webViewRef?.reload()
                                },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = Color.White, modifier = Modifier.size(18.dp))
                            }

                            IconButton(
                                onClick = openInExternalBrowser,
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open External Browser", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    // Active Spotify Page Error / Challenge Troubleshooting Strip
                    if (!domAuthBanner.isNullOrBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF2B181B))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "Spotify Web Notice: $domAuthBanner",
                                color = Color(0xFFFFB4AB),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = {
                                        resetSpotifyWebCookies {
                                            domAuthBanner = null
                                            lastLoggedDomError = ""
                                            webViewRef?.loadUrl(SpotifyAuth.LOGIN_URL)
                                        }
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) {
                                    Icon(Icons.Default.Cookie, contentDescription = null, tint = Color(LOGIN_SPOTIFY_GREEN), modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Reset Cookies", color = Color(LOGIN_SPOTIFY_GREEN), fontSize = 11.sp)
                                }
                                TextButton(
                                    onClick = { showLogsDialog = true },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) {
                                    Text("View Logs", color = Color(0xFFFFB74D), fontSize = 11.sp)
                                }
                                TextButton(
                                    onClick = { selectedTab = LoginTab.COOKIE },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) {
                                    Text("Cookie Login", color = Color.White, fontSize = 11.sp)
                                }
                            }
                        }
                    } else if (domHasOtpPrompt) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF17271D))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Enter the 6-digit verification code Spotify sent to your email below.",
                                color = Color(LOGIN_SPOTIFY_GREEN),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    if (isPageLoading && pageProgress in 1..99) {
                        LinearProgressIndicator(
                            progress = { pageProgress / 100f },
                            modifier = Modifier.fillMaxWidth().height(3.dp),
                            color = Color(LOGIN_SPOTIFY_GREEN),
                            trackColor = Color(0xFF282828),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color(LOGIN_BACKGROUND_DARK)),
                        contentAlignment = Alignment.Center,
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                try {
                                    val cookieManager = runCatching { CookieManager.getInstance() }.getOrNull()
                                    cookieManager?.setAcceptCookie(true)
                                    val initialUa = resolveUserAgent(ctx, uaMode)

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
                                            userAgentString = initialUa
                                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                            cacheMode = WebSettings.LOAD_DEFAULT
                                            javaScriptCanOpenWindowsAutomatically = true
                                            setSupportMultipleWindows(true)
                                        }

                                        webChromeClient = object : WebChromeClient() {
                                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                                pageProgress = newProgress
                                                if (newProgress >= 95) isPageLoading = false
                                            }

                                            override fun onConsoleMessage(cm: ConsoleMessage?): Boolean {
                                                val msg = cm?.message().orEmpty()
                                                val level = cm?.messageLevel()
                                                Timber.d("SpotifyWeb [$level]: $msg")
                                                if (level == ConsoleMessage.MessageLevel.ERROR) {
                                                    DevConsoleManager.logError("SpotifyWebViewJS", msg.take(300))
                                                } else if (level == ConsoleMessage.MessageLevel.WARNING &&
                                                    (msg.contains("error", ignoreCase = true) || msg.contains("block", ignoreCase = true))
                                                ) {
                                                    DevConsoleManager.logWarning("SpotifyWebViewJS", msg.take(300))
                                                }
                                                return true
                                            }

                                            override fun onCreateWindow(
                                                view: WebView?,
                                                isDialog: Boolean,
                                                isUserGesture: Boolean,
                                                resultMsg: Message?,
                                            ): Boolean {
                                                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                                                DevConsoleManager.logInfo("SpotifyLogin", "Opening OAuth popup window (Google/SSO)")
                                                val child = WebView(ctx).apply {
                                                    cookieManager?.setAcceptThirdPartyCookies(this, true)
                                                    settings.apply {
                                                        javaScriptEnabled = true
                                                        domStorageEnabled = true
                                                        userAgentString = resolveUserAgent(ctx, uaMode)
                                                        javaScriptCanOpenWindowsAutomatically = true
                                                    }
                                                    webViewClient = object : WebViewClient() {
                                                        override fun onPageFinished(v: WebView?, url: String?) {
                                                            runCatching { CookieManager.getInstance().flush() }
                                                            tryExchangeCapturedCookies("OAuthPopup")
                                                            if (url?.contains("open.spotify.com") == true ||
                                                                url?.contains("accounts.spotify.com/en/status") == true
                                                            ) {
                                                                runCatching { v?.destroy() }
                                                                popupWebView = null
                                                                webViewRef?.loadUrl("https://open.spotify.com/")
                                                            }
                                                        }
                                                    }
                                                    webChromeClient = object : WebChromeClient() {
                                                        override fun onCloseWindow(window: WebView?) {
                                                            runCatching { window?.destroy() }
                                                            popupWebView = null
                                                        }
                                                    }
                                                }
                                                popupWebView = child
                                                transport.webView = child
                                                resultMsg.sendToTarget()
                                                return true
                                            }
                                        }

                                        webViewClient = object : WebViewClient() {
                                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                                isPageLoading = true
                                                hasWebError = false
                                                val host = runCatching { Uri.parse(url).host }.getOrNull() ?: "accounts.spotify.com"
                                                val path = runCatching { Uri.parse(url).path }.getOrNull().orEmpty()
                                                currentHostLabel = host
                                                DevConsoleManager.logInfo("SpotifyWebNav", "Loading $host$path")
                                            }

                                            override fun onPageFinished(view: WebView?, url: String?) {
                                                isPageLoading = false
                                                runCatching { CookieManager.getInstance().flush() }
                                                val host = runCatching { Uri.parse(url).host }.getOrNull().orEmpty()
                                                val path = runCatching { Uri.parse(url).path }.getOrNull().orEmpty()
                                                DevConsoleManager.logDebug("SpotifyWebNav", "Finished $host$path")

                                                // Check cookies right away
                                                tryExchangeCapturedCookies("WebPageFinished")

                                                // If Spotify parked on /status after auth without redirecting yet, push to open.spotify.com
                                                if (!tokenFetchStarted.get() &&
                                                    host.contains("accounts.spotify.com", ignoreCase = true) &&
                                                    path.contains("/status", ignoreCase = true)
                                                ) {
                                                    DevConsoleManager.logInfo("SpotifyWebNav", "Detected /status landing; redirecting to open.spotify.com to expose sp_dc")
                                                    view?.loadUrl("https://open.spotify.com/")
                                                    return
                                                }

                                                // Auto-fill credentials if user came from Native Login form
                                                val handoff = pendingHandoff
                                                if (handoff != null && host.contains("accounts.spotify.com", ignoreCase = true)) {
                                                    val js = buildCredentialAutofillJs(
                                                        emailOrUsername = handoff.emailOrUsername,
                                                        password = handoff.password,
                                                        preferEmailCode = handoff.preferEmailCode,
                                                    )
                                                    pendingHandoff = null
                                                    password = ""
                                                    view?.evaluateJavascript(js, null)
                                                }
                                            }

                                            override fun onReceivedHttpError(
                                                view: WebView?,
                                                request: WebResourceRequest?,
                                                errorResponse: WebResourceResponse?,
                                            ) {
                                                val status = errorResponse?.statusCode ?: return
                                                val reqUrl = request?.url
                                                val host = reqUrl?.host.orEmpty()
                                                val path = reqUrl?.path.orEmpty()
                                                val isMain = request?.isForMainFrame == true
                                                if (status >= 400 && host.contains("spotify", ignoreCase = true)) {
                                                    val summary = "HTTP $status on $host$path (mainFrame=$isMain)"
                                                    DevConsoleManager.logError("SpotifyWebHTTP", summary)
                                                    if (isMain || status == 400 || status == 401 || status == 403 || status == 429) {
                                                        DevConsoleManager.reportComponentFault(
                                                            component = "SpotifyLogin",
                                                            action = "HTTP_$status",
                                                            reason = "$summary [UA=${uaMode.shortLabel}]",
                                                            autoFixHint = "Spotify returned HTTP $status. Switch UA mode or try Email Code / Reset Cookies.",
                                                        )
                                                    }
                                                }
                                            }

                                            override fun onReceivedError(
                                                view: WebView?,
                                                request: WebResourceRequest?,
                                                error: WebResourceError?,
                                            ) {
                                                val desc = error?.description?.toString() ?: "Unknown network error"
                                                val code = error?.errorCode ?: -1
                                                val host = request?.url?.host.orEmpty()
                                                DevConsoleManager.logError("SpotifyWebNet", "Error $code on $host: $desc (main=${request?.isForMainFrame})")
                                                if (request?.isForMainFrame == true) {
                                                    hasWebError = true
                                                    webErrorMessage = "$desc (code $code)"
                                                    DevConsoleManager.reportComponentFault(
                                                        component = "SpotifyLogin",
                                                        action = "WebViewLoadError",
                                                        reason = "$desc ($code)",
                                                        autoFixHint = "Check VPN/DNS/network or switch to Cookie login.",
                                                    )
                                                }
                                            }

                                            override fun onReceivedSslError(
                                                view: WebView?,
                                                handler: SslErrorHandler?,
                                                error: SslError?,
                                            ) {
                                                val primary = error?.primaryError ?: -1
                                                DevConsoleManager.logError("SpotifyWebSSL", "SSL error code=$primary on ${error?.url}")
                                                handler?.cancel()
                                                hasWebError = true
                                                webErrorMessage = "TLS/SSL verification failed (code $primary). Check device clock or network proxy."
                                            }

                                            override fun onRenderProcessGone(
                                                view: WebView?,
                                                detail: RenderProcessGoneDetail?,
                                            ): Boolean {
                                                Timber.e("WebView render process gone")
                                                DevConsoleManager.logError("SpotifyLogin", "WebView renderer crashed (didCrash=${detail?.didCrash()})")
                                                hasWebError = true
                                                webErrorMessage = "System WebView crashed. Tap Retry or use Cookie Login."
                                                return true
                                            }

                                            override fun shouldOverrideUrlLoading(
                                                view: WebView?,
                                                request: WebResourceRequest?,
                                            ): Boolean {
                                                val url = request?.url?.toString().orEmpty()
                                                return !(url.startsWith("http://") || url.startsWith("https://"))
                                            }
                                        }

                                        loadUrl(SpotifyAuth.LOGIN_URL)
                                    }
                                } catch (t: Throwable) {
                                    Timber.e(t, "System WebView failed to initialize")
                                    DevConsoleManager.logError("SpotifyLogin", "System WebView init exception: ${t.message}", t)
                                    DevConsoleManager.reportComponentFault(
                                        component = "SpotifyLogin",
                                        action = "WebViewInit",
                                        reason = t.message ?: "System WebView unavailable",
                                        autoFixHint = "Update Android System WebView or use Cookie Login tab.",
                                        exception = t,
                                    )
                                    hasWebError = true
                                    webErrorMessage = "System WebView unavailable (${t.localizedMessage ?: "init error"}). Use Cookie Login."
                                    isPageLoading = false
                                    android.view.View(ctx)
                                }
                            },
                        )

                        // OAuth Popup Window Overlay (Google / Facebook / Apple SSO)
                        popupWebView?.let { child ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF101010)),
                            ) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF1C1C22))
                                            .padding(horizontal = 12.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text("OAuth Sign-In Window", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        IconButton(
                                            onClick = {
                                                runCatching { child.destroy() }
                                                popupWebView = null
                                            },
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Close OAuth popup", tint = Color.White)
                                        }
                                    }
                                    AndroidView(
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                        factory = { child },
                                    )
                                }
                            }
                        }

                        if (isPageLoading && !hasWebError && pageProgress < 40) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(LOGIN_BACKGROUND_DARK).copy(alpha = 0.85f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Color(LOGIN_SPOTIFY_GREEN), strokeWidth = 3.dp)
                                    Spacer(Modifier.height(14.dp))
                                    Text(
                                        text = "Loading Spotify Login ($pageProgress%)…",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }

                        if (hasWebError) {
                            Card(
                                modifier = Modifier.fillMaxWidth(0.92f).padding(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(LOGIN_CARD_DARK)),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFE57373), modifier = Modifier.size(36.dp))
                                    Spacer(Modifier.height(10.dp))
                                    Text("Login Page Error", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = webErrorMessage.ifBlank { "Network or WebView issue." },
                                        color = Color(LOGIN_TEXT_SECONDARY),
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center,
                                    )
                                    Spacer(Modifier.height(14.dp))
                                    Button(
                                        onClick = {
                                            hasWebError = false
                                            isPageLoading = true
                                            webViewRef?.reload()
                                        },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(LOGIN_SPOTIFY_GREEN), contentColor = Color.Black),
                                    ) {
                                        Text("Retry Page Load", fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { showLogsDialog = true },
                                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFB74D)),
                                        ) {
                                            Text("View Logs", fontSize = 12.sp)
                                        }
                                        OutlinedButton(
                                            onClick = { showCookieDialog = true },
                                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(LOGIN_SPOTIFY_GREEN)),
                                        ) {
                                            Text("Paste Cookie", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── View 3: Smart Cookie / JSON Entry View (sp_dc) ──
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
                            tint = Color(LOGIN_SPOTIFY_GREEN),
                            modifier = Modifier.size(34.dp),
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    Text(
                        text = "Sign In with Session Cookie",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "Paste raw sp_dc, a Cookie header, or full Cookie-Editor JSON — SpotUI auto-extracts sp_dc & sp_key.",
                        color = Color(LOGIN_TEXT_SECONDARY),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                    )

                    Spacer(Modifier.height(18.dp))

                    OutlinedTextField(
                        value = cookieInputValue,
                        onValueChange = {
                            cookieInputValue = it
                            cookieError = null
                        },
                        label = { Text("sp_dc / Cookie Header / Cookie JSON", color = Color(LOGIN_TEXT_SECONDARY)) },
                        placeholder = {
                            Text("AQD... or Cookie-Editor JSON export", color = Color(0xFF666666), fontSize = 13.sp)
                        },
                        singleLine = false,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth().testTag("cookie_tab_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(LOGIN_SPOTIFY_GREEN),
                            focusedBorderColor = Color(LOGIN_SPOTIFY_GREEN),
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
                                val clip = runCatching {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val primaryClip = clipboard?.primaryClip
                                    if (primaryClip != null && primaryClip.itemCount > 0) {
                                        primaryClip.getItemAt(0)?.text?.toString()
                                    } else null
                                }.getOrNull().orEmpty()
                                if (clip.isNotBlank()) {
                                    val (dc, _) = parseSpotifyCookiesFromText(clip)
                                    cookieInputValue = dc.ifBlank { clip.trim() }
                                    cookieError = null
                                }
                            },
                        ) {
                            Text("Paste from Clipboard", color = Color(LOGIN_SPOTIFY_GREEN), fontSize = 13.sp)
                        }

                        if (cookieInputValue.isNotBlank()) {
                            TextButton(onClick = { cookieInputValue = "" }) {
                                Text("Clear", color = Color(0xFF888888), fontSize = 13.sp)
                            }
                        }
                    }

                    cookieError?.let { err ->
                        Spacer(Modifier.height(8.dp))
                        Text(err, color = Color(0xFFE57373), fontSize = 13.sp, textAlign = TextAlign.Center)
                    }

                    Spacer(Modifier.height(18.dp))

                    Button(
                        onClick = {
                            val (cleanDc, cleanKey) = parseSpotifyCookiesFromText(cookieInputValue)
                            if (cleanDc.isBlank()) {
                                cookieError = "Could not find a valid sp_dc value in that input."
                                DevConsoleManager.logWarning("SpotifyLogin", "Cookie paste failed: no sp_dc parsed")
                                return@Button
                            }
                            processLoginSession(
                                context = context,
                                spDc = cleanDc,
                                spKey = cleanKey,
                                source = "CookieTab",
                                scope = scope,
                                setProcessing = { isProcessing = it },
                                setStatus = { statusMessage = it },
                                setError = { hasError = it },
                                onComplete = { success ->
                                    if (!success) cookieError = statusMessage
                                },
                                onSuccess = navigateToHome,
                            )
                        },
                        enabled = !isProcessing && cookieInputValue.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("cookie_tab_submit"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(LOGIN_SPOTIFY_GREEN), contentColor = Color.Black),
                        shape = RoundedCornerShape(25.dp),
                    ) {
                        Text("Connect with Cookie", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    Spacer(Modifier.height(14.dp))

                    TextButton(onClick = navigateToHome, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Continue as Guest", color = Color(LOGIN_TEXT_SECONDARY), fontSize = 13.sp)
                    }
                }
            }

            // Bottom quick shortcut strip
            Surface(
                color = Color(0xFF141414),
                modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Auth Logs",
                        color = Color(0xFFFFB74D),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { showLogsDialog = true }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                    Text(" • ", color = Color(0xFF555555), fontSize = 12.sp)
                    Text(
                        text = "Paste Cookie",
                        color = Color(LOGIN_SPOTIFY_GREEN),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { showCookieDialog = true }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                    Text(" • ", color = Color(0xFF555555), fontSize = 12.sp)
                    Text(
                        text = "Continue as Guest",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(onClick = navigateToHome)
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }
            }
        }

        // Paste Cookie Modal Dialog
        if (showCookieDialog) {
            AlertDialog(
                onDismissRequest = { showCookieDialog = false },
                containerColor = Color(LOGIN_CARD_DARK),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.logo),
                            contentDescription = null,
                            tint = Color(LOGIN_SPOTIFY_GREEN),
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Paste Session Cookie (sp_dc)", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column {
                        Text(
                            text = "Supports raw sp_dc, HTTP Cookie headers, or Cookie-Editor JSON exports.",
                            color = Color(LOGIN_TEXT_SECONDARY),
                            fontSize = 12.sp,
                        )
                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = dialogCookieInput,
                            onValueChange = {
                                dialogCookieInput = it
                                dialogCookieError = null
                            },
                            label = { Text("sp_dc / Cookie JSON", color = Color(LOGIN_TEXT_SECONDARY)) },
                            placeholder = { Text("AQD...", color = Color(0xFF555555)) },
                            singleLine = false,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color(LOGIN_SPOTIFY_GREEN),
                                focusedBorderColor = Color(LOGIN_SPOTIFY_GREEN),
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
                                    val clip = runCatching {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        val primaryClip = clipboard?.primaryClip
                                        if (primaryClip != null && primaryClip.itemCount > 0) {
                                            primaryClip.getItemAt(0)?.text?.toString()
                                        } else null
                                    }.getOrNull().orEmpty()
                                    if (clip.isNotBlank()) {
                                        val (dc, _) = parseSpotifyCookiesFromText(clip)
                                        dialogCookieInput = dc.ifBlank { clip.trim() }
                                        dialogCookieError = null
                                    }
                                },
                            ) {
                                Text("Paste Clipboard", color = Color(LOGIN_SPOTIFY_GREEN), fontSize = 12.sp)
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
                            val (cleanDc, cleanKey) = parseSpotifyCookiesFromText(dialogCookieInput)
                            if (cleanDc.isBlank()) {
                                dialogCookieError = "Could not extract sp_dc cookie from input."
                                return@Button
                            }
                            showCookieDialog = false
                            processLoginSession(
                                context = context,
                                spDc = cleanDc,
                                spKey = cleanKey,
                                source = "CookieDialog",
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
                        colors = ButtonDefaults.buttonColors(containerColor = Color(LOGIN_SPOTIFY_GREEN), contentColor = Color.Black),
                    ) {
                        Text("Connect", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCookieDialog = false }) {
                        Text("Cancel", color = Color.White)
                    }
                },
            )
        }

        if (showLogsDialog) {
            LoginDiagnosticsDialog(
                logs = allLogs,
                faults = allFaults,
                onDismiss = { showLogsDialog = false },
            )
        }

        if (isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Card(
                    modifier = Modifier.padding(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(LOGIN_CARD_DARK)),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(
                            color = Color(LOGIN_SPOTIFY_GREEN),
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
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (isSelected) Color(LOGIN_SPOTIFY_GREEN) else Color(0xFF262626))
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
