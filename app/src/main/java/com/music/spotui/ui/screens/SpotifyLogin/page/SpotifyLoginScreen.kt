package com.music.spotui.ui.screens.SpotifyLogin.page

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.metrolist.spotify.Spotify
import com.metrolist.spotify.SpotifyAuth
import com.music.spotui.R
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.graphicsLayer
import com.metrolist.spotify.models.SpotifyInternalToken
import com.music.spotui.ui.screens.SpotifyLogin.helper.parseSpotifyCookiesFromText
import com.music.spotui.ui.screens.SpotifyLogin.module.MIN_SP_DC_LENGTH
import com.music.spotui.ui.screens.SpotifyLogin.module.SpotifyWebViewAdapter
import com.music.spotui.ui.screens.SpotifyLogin.module.processLoginSession
import com.music.spotui.data.api.SpotifySession
import com.music.spotui.ui.navigation.Routes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

private const val SPOTIFY_GREEN = 0xFF1ED760

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SpotifyLoginScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }
    var signedInAs by remember { mutableStateOf<String?>(null) }
    var showWebFallback by remember { mutableStateOf(false) }
    var showCookieDialog by remember { mutableStateOf(false) }
    var manualCookieInput by remember { mutableStateOf("") }

    var popupView by remember { mutableStateOf<WebView?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val pipelineStarted = remember { AtomicBoolean(false) }

    // Adapter -> pipeline bridge (the adapter is created before the pipeline starter below).
    val captureSink = remember { arrayOfNulls<(String, String, SpotifyInternalToken?, String) -> Unit>(1) }

    // ONE hardened engine for both modes: the native form drives it invisibly, the web mode shows it.
    val adapter = remember {
        SpotifyWebViewAdapter(
            isPrivate = false,
            onSessionCaptured = { dc, key, token, source -> captureSink[0]?.invoke(dc, key, token, source) },
            onPopupRequested = { popupView = it },
        )
    }
    val nav by adapter.navigationState.collectAsState()
    val engine by adapter.processState.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            adapter.destroy()
            webViewRef = null
        }
    }

    // Start from a clean slate: drop any stale in-memory token (the WebView session is wiped by createView).
    LaunchedEffect(Unit) { Spotify.clearAuthSession() }

    // SSO popups (Google / Facebook / Apple) must be visible, so leave the native overlay.
    LaunchedEffect(popupView) { if (popupView != null) showWebFallback = true }

    val navigateToHome: () -> Unit = {
        com.music.spotui.di.SpotifyWebPlayer.refreshLogin(context)
        navController.navigate(Routes.Home.route) {
            popUpTo(Routes.Login.route) { inclusive = true }
        }
    }

    /**
     * Single entry for every sign-in path (native form, web mode, SSO popup, pasted cookie).
     * Runs Spotify.loginWithWebViewSession (retry/backoff, pre-captured `#session` token, profile),
     * seeds SpotifyTokenProvider, saves sp_dc + sp_key, installs token auto-refresh, syncs the library.
     */
    fun startPipeline(spDc: String, spKey: String, token: SpotifyInternalToken?, source: String) {
        if (!pipelineStarted.compareAndSet(false, true)) return
        adapter.closePopup()
        showWebFallback = false
        processLoginSession(
            context = context,
            spDc = spDc,
            spKey = spKey,
            source = source,
            preCapturedToken = token,
            autoSyncLibrary = true,
            scope = scope,
            setProcessing = { isProcessing = it },
            setStatus = { statusMessage = it },
            setError = { hasError = it },
            onComplete = { ok ->
                if (ok) {
                    password = ""
                } else {
                    pipelineStarted.set(false)
                    adapter.allowRecapture()
                }
            },
            onSuccess = { navigateToHome() },
            onAccount = { signedInAs = it },
        )
    }
    SideEffect {
        captureSink[0] = { dc, key, token, source -> startPipeline(dc, key, token, source) }
    }

    fun handleNativeSubmit() {
        if (email.isBlank() || password.isBlank()) {
            hasError = true
            statusMessage = "Please enter email/username and password"
            return
        }
        isProcessing = true
        hasError = false
        signedInAs = null
        statusMessage = "Connecting to Spotify…"

        scope.launch {
            fun fallbackToWeb(msg: String) {
                isProcessing = false
                hasError = false
                showWebFallback = true
                statusMessage = msg
            }

            val failureBefore = adapter.processState.value.lastFailureMessage

            // 1. Wait (max ~7s) for the hardened WebView to finish loading the login page.
            var waited = 0
            while (adapter.navigationState.value.isLoading && waited < 25) {
                delay(300)
                waited++
            }

            // 2. Spotify's login page is a SPA: the form can appear late, and may be two steps
            //    (username -> password). Retry until the click really lands on an enabled button.
            var submitted = false
            for (attempt in 1..12) {
                if (pipelineStarted.get()) return@launch
                val res = injectCredentials(webViewRef, email, password)
                if (res == "SUBMIT") {
                    submitted = true
                    break
                }
                statusMessage = if (res == "STEP1") "Sending username…" else "Waiting for Spotify login form…"
                delay(800)
            }
            if (!submitted) {
                fallbackToWeb("Couldn't auto-fill Spotify's form. Please sign in below")
                return@launch
            }

            // 3. Watchdog: the adapter reports the session via onSessionCaptured -> startPipeline.
            //    Poll as a safety net (SPA redirects don't always fire onPageFinished) and surface problems.
            statusMessage = "Submitting credentials…"
            val deadline = System.currentTimeMillis() + 45_000L
            while (System.currentTimeMillis() < deadline) {
                delay(700)
                if (pipelineStarted.get()) return@launch
                if (adapter.tryCaptureSessionNow("NativeSubmitPoll")) {
                    delay(400)
                    if (pipelineStarted.get()) return@launch
                }
                val failure = adapter.processState.value.lastFailureMessage
                if (failure != null && failure != failureBefore) {
                    fallbackToWeb("Spotify rejected the sign-in challenge. Use Auto-recover, then sign in below")
                    return@launch
                }
                when (val issue = readLoginIssue(webViewRef)) {
                    "" -> Unit
                    "CAPTCHA", "VERIFY" -> {
                        fallbackToWeb("Spotify needs a quick verification — finish signing in below")
                        return@launch
                    }
                    else -> {
                        isProcessing = false
                        hasError = true
                        statusMessage = issue
                        return@launch
                    }
                }
            }
            fallbackToWeb("Sign-in is taking too long — finish it below")
        }
    }

    fun submitManualCookie(raw: String) {
        val (dc, key) = parseSpotifyCookiesFromText(raw)
        if (dc.length < MIN_SP_DC_LENGTH) {
            hasError = true
            statusMessage = "That doesn't look like a valid sp_dc cookie"
            return
        }
        signedInAs = null
        startPipeline(dc, key, null, "ManualCookie")
    }

    fun enterGuestMode() {
        isProcessing = true
        statusMessage = "Starting session…"
        scope.launch {
            SpotifySession.setSpDc(context, "guest_demo_cookie")
            delay(300)
            isProcessing = false
            navigateToHome()
        }
    }

    BackHandler(enabled = popupView != null || showWebFallback) {
        when {
            popupView != null -> adapter.closePopup()
            adapter.goBack() -> Unit
            else -> showWebFallback = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (showWebFallback) {
                // ── Web mode top bar ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(56.dp)
                        .background(Color(0xFF181818))
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { showWebFallback = false }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = "Spotify Login",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            if (!adapter.tryCaptureSessionNow("ManualWebSync")) {
                                hasError = true
                                statusMessage = "No Spotify session yet — finish signing in first"
                            }
                        }) {
                            Text("Sync", color = Color(SPOTIFY_GREEN), fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { adapter.reload() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reload",
                                tint = Color.White
                            )
                        }
                    }
                }
                if (nav.isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(SPOTIFY_GREEN),
                        trackColor = Color(0xFF282828)
                    )
                }
                val hint = engine.autoRecoverHint
                if (hint != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF2A1A1A))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(hint, color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { adapter.autoRecover(context) }) {
                            Text("Auto-recover", color = Color(SPOTIFY_GREEN), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
                if (statusMessage.isNotBlank()) {
                    Text(
                        statusMessage,
                        color = if (hasError) Color(0xFFFF5252) else Color(0xFFB3B3B3),
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                // The single hardened WebView. Full-size and fully laid out even in native mode
                // (just transparent), so Spotify's page and reCAPTCHA see a normal viewport.
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (showWebFallback) 1f else 0f },
                    factory = { ctx ->
                        adapter.createView(ctx, Spotify.LOGIN_URL, clearPreviousSession = true)
                            .also { webViewRef = it }
                    }
                )

                if (!showWebFallback) {
                    // ── Native Compose Form (drives the WebView above) ──
                    LoginForm(
                        email = email,
                        onEmail = { email = it },
                        password = password,
                        onPassword = { password = it },
                        showPassword = showPassword,
                        onTogglePassword = { showPassword = !showPassword },
                        isProcessing = isProcessing,
                        statusMessage = statusMessage,
                        hasError = hasError,
                        accountName = signedInAs,
                        onSubmit = { handleNativeSubmit() },
                        onUseWeb = {
                            statusMessage = ""
                            hasError = false
                            showWebFallback = true
                        },
                        onSignUp = {
                            webViewRef?.loadUrl(Spotify.SIGNUP_URL)
                            showWebFallback = true
                        },
                        onPasteCookie = { showCookieDialog = true },
                        onGuestMode = { enterGuestMode() }
                    )
                }
            }
        }

        // ── SSO popup (Google / Facebook / Apple) ──
        popupView?.let { popup ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF121212))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(56.dp)
                        .background(Color(0xFF181818))
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { adapter.closePopup() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                    Text("Sign in", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                AndroidView(modifier = Modifier.fillMaxSize(), factory = { popup })
            }
        }

        // ── Manual Cookie Input Dialog ──
        if (showCookieDialog) {
            AlertDialog(
                onDismissRequest = { showCookieDialog = false },
                title = {
                    Text("Enter sp_dc Cookie", color = Color.White, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column {
                        Text(
                            "Log in to open.spotify.com in your browser, inspect Cookies -> copy sp_dc. " +
                                "A pasted cookie header or sp_dc=… also works.",
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = manualCookieInput,
                            onValueChange = { manualCookieInput = it },
                            placeholder = { Text("Paste sp_dc cookie value", color = Color.Gray) },
                            singleLine = true,
                            colors = spotifyFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showCookieDialog = false
                            submitManualCookie(manualCookieInput)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(SPOTIFY_GREEN))
                    ) {
                        Text("Connect", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCookieDialog = false }) {
                        Text("Cancel", color = Color.White)
                    }
                },
                containerColor = Color(0xFF1E1E24)
            )
        }
    }
}

@Composable
private fun LoginForm(
    email: String,
    onEmail: (String) -> Unit,
    password: String,
    onPassword: (String) -> Unit,
    showPassword: Boolean,
    onTogglePassword: () -> Unit,
    isProcessing: Boolean,
    statusMessage: String,
    hasError: Boolean,
    accountName: String?,
    onSubmit: () -> Unit,
    onUseWeb: () -> Unit,
    onSignUp: () -> Unit,
    onPasteCookie: () -> Unit,
    onGuestMode: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Icon(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "Spotify",
            tint = Color(SPOTIFY_GREEN),
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "Log in to Spotify",
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 26.sp,
        )
        Spacer(Modifier.height(28.dp))

        Text(
            "Email or username",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = email,
            onValueChange = onEmail,
            singleLine = true,
            placeholder = { Text("Email or username", color = Color(0xFF8A8A8A)) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            colors = spotifyFieldColors(),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))
        Text(
            "Password",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = password,
            onValueChange = onPassword,
            singleLine = true,
            placeholder = { Text("Password", color = Color(0xFF8A8A8A)) },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            trailingIcon = {
                IconButton(onClick = onTogglePassword) {
                    Icon(
                        painter = painterResource(
                            id = if (showPassword) R.drawable.visibility else R.drawable.visibility_off
                        ),
                        contentDescription = "Toggle password",
                        tint = Color(0xFFB3B3B3),
                    )
                }
            },
            colors = spotifyFieldColors(),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        if (!accountName.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(
                "Signed in as $accountName",
                color = Color(SPOTIFY_GREEN),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }

        if (statusMessage.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(
                statusMessage,
                color = if (hasError) Color(0xFFFF5252) else Color(0xFFB3B3B3),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onSubmit,
            enabled = !isProcessing,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(SPOTIFY_GREEN),
                contentColor = Color.Black,
                disabledContainerColor = Color(0xFF12863B),
            ),
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    color = Color.Black,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Text("Log In", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = onUseWeb,
            enabled = !isProcessing,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            border = ButtonDefaults.outlinedToolboxBorder(enabled = true),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Log in with Spotify Web Browser", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }

        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onSignUp, enabled = !isProcessing) {
            Text(
                "Don't have an account? Sign up",
                color = Color(0xFFB3B3B3),
                fontSize = 12.sp,
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onPasteCookie, enabled = !isProcessing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = Color(0xFFB3B3B3),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.size(4.dp))
                    Text("Paste sp_dc Cookie", color = Color(0xFFB3B3B3), fontSize = 12.sp)
                }
            }

            TextButton(onClick = onGuestMode, enabled = !isProcessing) {
                Text("Continue as Guest", color = Color(SPOTIFY_GREEN), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ButtonDefaults.outlinedToolboxBorder(enabled: Boolean) =
    androidx.compose.foundation.BorderStroke(
        width = 1.dp,
        color = if (enabled) Color(0xFF727272) else Color(0xFF333333)
    )

@Composable
private fun spotifyFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = Color.White,
    focusedBorderColor = Color.White,
    unfocusedBorderColor = Color(0xFF727272),
    focusedContainerColor = Color(0xFF181818),
    unfocusedContainerColor = Color(0xFF181818),
)

private suspend fun injectCredentials(webView: WebView?, email: String, password: String): String {
    webView ?: return "NOFORM"
    val u = JSONObject.quote(email)
    val p = JSONObject.quote(password)
    val js = """
        (function(){
          function setVal(el, val){
            try {
              var proto = window.HTMLInputElement.prototype;
              var setter = Object.getOwnPropertyDescriptor(proto, 'value').set;
              setter.call(el, val);
            } catch(e) { el.value = val; }
            el.dispatchEvent(new Event('input', {bubbles:true}));
            el.dispatchEvent(new Event('change', {bubbles:true}));
          }
          function isOff(b){ return b.disabled || b.getAttribute('aria-disabled') === 'true'; }
          var u = document.querySelector('#login-username, input[data-testid=login-username], input[autocomplete=username], input[name=username], input[type=email]');
          var p = document.querySelector('#login-password, input[data-testid=login-password], input[type=password]');
          if(!u && !p){ return 'NOFORM'; }
          var b = document.querySelector('#login-button, button[data-testid=login-button]') || document.querySelector('button[type=submit]');
          if(u){ setVal(u, $u); }
          if(!p){
            // Username-first step: submit it, the password field appears on the next attempt.
            if(b && !isOff(b)){ b.click(); return 'STEP1'; }
            return 'NOBTN';
          }
          setVal(p, $p);
          if(b){
            if(isOff(b)){ return 'DISABLED'; }
            b.click();
            return 'SUBMIT';
          }
          if(p.form){
            if(p.form.requestSubmit){ p.form.requestSubmit(); } else { p.form.submit(); }
            return 'SUBMIT';
          }
          return 'NOBTN';
        })();
    """.trimIndent()
    return withContext(Dispatchers.Main) {
        val done = kotlinx.coroutines.CompletableDeferred<String>()
        webView.evaluateJavascript(js) { raw ->
            done.complete(raw?.trim('"') ?: "NOFORM")
        }
        done.await()
    }
}

/**
 * Reads what the hidden login page is showing after submit.
 * Returns "" (nothing wrong yet), "CAPTCHA", "VERIFY", or Spotify's own error text.
 */
private suspend fun readLoginIssue(webView: WebView?): String {
    webView ?: return ""
    val js = """
        (function(){
          var sels = ['[data-testid=login-error]', '#username-error', '#password-error',
                      '[data-encore-id=formFeedback]', '[role=alert]'];
          for (var i = 0; i < sels.length; i++) {
            var el = document.querySelector(sels[i]);
            if (el && el.innerText && el.innerText.trim().length > 0) { return el.innerText.trim().substring(0, 160); }
          }
          var t = document.body ? document.body.innerText.toLowerCase() : '';
          if (/captcha|verify you are human|not a robot/.test(t)) { return 'CAPTCHA'; }
          if (/verification code|enter the code|6-digit|check your email|two-step/.test(t)) { return 'VERIFY'; }
          return '';
        })();
    """.trimIndent()
    return withContext(Dispatchers.Main) {
        val done = kotlinx.coroutines.CompletableDeferred<String>()
        webView.evaluateJavascript(js) { raw ->
            val value = raw?.let { runCatching { org.json.JSONTokener(it).nextValue() as? String }.getOrNull() }
            done.complete(value.orEmpty())
        }
        done.await()
    }
}
