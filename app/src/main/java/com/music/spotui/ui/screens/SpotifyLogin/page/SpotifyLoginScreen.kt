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
    var showWebFallback by remember { mutableStateOf(false) }
    var showCookieDialog by remember { mutableStateOf(false) }
    var manualCookieInput by remember { mutableStateOf("") }

    var isWebLoading by remember { mutableStateOf(true) }
    var webHasError by remember { mutableStateOf(false) }

    val pageReady = remember { AtomicBoolean(false) }
    val tokenFetchStarted = remember { AtomicBoolean(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val navigateToHome: () -> Unit = {
        com.music.spotui.di.SpotifyWebPlayer.refreshLogin(context)
        navController.navigate(Routes.Home.route) {
            popUpTo(Routes.Login.route) { inclusive = true }
        }
    }

    // Poll for the sp_dc cookie — set on .spotify.com when user signs in
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            if (tokenFetchStarted.get()) continue
            val spDc = extractCookie("sp_dc")
            if (!spDc.isNullOrBlank() && tokenFetchStarted.compareAndSet(false, true)) {
                finishLogin(
                    webViewRef, context as Activity, scope,
                    setProcessing = { isProcessing = it },
                    setStatus = { statusMessage = it },
                    setError = { hasError = it },
                    tokenFetchStarted = tokenFetchStarted,
                    onSuccess = navigateToHome,
                )
            }
        }
    }

    fun handleNativeSubmit() {
        if (email.isBlank() || password.isBlank()) {
            hasError = true
            statusMessage = "Please enter email/username and password"
            return
        }
        isProcessing = true
        hasError = false
        statusMessage = "Connecting to Spotify…"

        scope.launch {
            fun fallbackToWeb(msg: String) {
                isProcessing = false
                hasError = false
                showWebFallback = true
                statusMessage = msg
            }

            // 1. Wait (max ~6s) for the hidden page to finish loading.
            var waited = 0
            while (!pageReady.get() && waited < 20) {
                delay(300)
                waited++
            }

            // 2. Spotify's login page is a SPA: the form can appear late, and may be two steps
            //    (username -> password). Retry until the click really lands on an enabled button.
            var submitted = false
            for (attempt in 1..12) {
                if (tokenFetchStarted.get()) return@launch
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

            // 3. Watchdog: never sit on "Submitting credentials…" forever.
            statusMessage = "Submitting credentials…"
            val deadline = System.currentTimeMillis() + 30_000L
            while (System.currentTimeMillis() < deadline) {
                delay(700)
                if (tokenFetchStarted.get()) return@launch // sp_dc seen: finishLogin owns the UI now
                val issue = readLoginIssue(webViewRef)
                when (issue) {
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

    fun submitManualCookie(spDc: String) {
        val clean = spDc.trim()
        if (clean.isBlank()) return
        isProcessing = true
        statusMessage = "Authenticating with cookie…"
        hasError = false
        scope.launch(Dispatchers.IO) {
            SpotifySession.setSpDc(context, clean)
            val result = SpotifyAuth.fetchAccessToken(clean)
            result.onSuccess { token ->
                Spotify.accessToken = token.accessToken
                withContext(Dispatchers.Main) {
                    isProcessing = false
                    navigateToHome()
                }
            }.onFailure { e ->
                withContext(Dispatchers.Main) {
                    isProcessing = false
                    hasError = true
                    statusMessage = "Cookie authentication failed: ${e.message}"
                }
            }
        }
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        if (!showWebFallback) {
            // ── Native Compose Form ──
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
                onSubmit = { handleNativeSubmit() },
                onUseWeb = { showWebFallback = true },
                onPasteCookie = { showCookieDialog = true },
                onGuestMode = { enterGuestMode() }
            )
        } else {
            // ── Web Browser Login View ──
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Bar
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
                    IconButton(onClick = {
                        webHasError = false
                        webViewRef?.reload()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reload",
                            tint = Color.White
                        )
                    }
                }

                if (isWebLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(SPOTIFY_GREEN),
                        trackColor = Color(0xFF282828)
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewRef = this
                                setBackgroundColor(android.graphics.Color.parseColor("#121212"))
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.databaseEnabled = true
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                settings.javaScriptCanOpenWindowsAutomatically = true

                                webChromeClient = WebChromeClient()
                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        isWebLoading = true
                                        webHasError = false
                                        pageReady.set(false)
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        isWebLoading = false
                                        pageReady.set(true)
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        error: WebResourceError?
                                    ) {
                                        if (request?.isForMainFrame == true) {
                                            isWebLoading = false
                                            webHasError = true
                                        }
                                    }
                                }
                                loadUrl(SpotifyAuth.LOGIN_URL)
                            }
                        }
                    )

                    if (webHasError) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF121212))
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Unable to load Spotify Login",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Please check your network connection and try again.",
                                color = Color.Gray,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(24.dp))
                            Button(
                                onClick = {
                                    webHasError = false
                                    webViewRef?.reload()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(SPOTIFY_GREEN))
                            ) {
                                Text("Retry", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(12.dp))
                            TextButton(onClick = { showWebFallback = false }) {
                                Text("Return to Native Login", color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // Hidden background WebView for native form auto-submission
        if (!showWebFallback) {
            Box(modifier = Modifier.size(1.dp).background(Color.Transparent)) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewRef = this
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    pageReady.set(false)
                                }
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    pageReady.set(true)
                                }
                            }
                            loadUrl(SpotifyAuth.LOGIN_URL)
                        }
                    }
                )
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
                            "Log in to open.spotify.com in your browser, inspect Cookies -> copy sp_dc.",
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
    onSubmit: () -> Unit,
    onUseWeb: () -> Unit,
    onPasteCookie: () -> Unit,
    onGuestMode: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
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

        Spacer(Modifier.height(12.dp))

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

private fun extractCookie(name: String): String? {
    val allCookies = CookieManager.getInstance().getCookie("https://open.spotify.com") ?: return null
    return allCookies.split(";")
        .mapNotNull {
            val parts = it.trim().split("=", limit = 2)
            if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
        }
        .firstOrNull { it.first == name && it.second.isNotBlank() }
        ?.second
}

private fun finishLogin(
    view: WebView?,
    activity: Activity,
    scope: kotlinx.coroutines.CoroutineScope,
    setProcessing: (Boolean) -> Unit,
    setStatus: (String) -> Unit,
    setError: (Boolean) -> Unit,
    tokenFetchStarted: AtomicBoolean,
    onSuccess: () -> Unit,
) {
    val spDc = extractCookie("sp_dc")
    val spKey = extractCookie("sp_key") ?: ""
    if (spDc.isNullOrBlank()) {
        setProcessing(true)
        setError(true)
        setStatus("Couldn't read login cookie. Please complete Spotify login.")
        tokenFetchStarted.set(false)
        return
    }

    setProcessing(true)
    setError(false)
    setStatus("Connecting…")
    view?.stopLoading()

    scope.launch(Dispatchers.IO) {
        SpotifySession.setSpDc(activity, spDc)
        var lastError: Throwable? = null
        repeat(3) { attempt ->
            val result = SpotifyAuth.fetchAccessToken(spDc, spKey)
            result.onSuccess { token ->
                Spotify.accessToken = token.accessToken
                withContext(Dispatchers.Main) { setStatus("Success!") }
                delay(300)
                withContext(Dispatchers.Main) { onSuccess() }
                return@launch
            }.onFailure { e ->
                lastError = e
                Timber.e(e, "Spotify token fetch failed (attempt ${attempt + 1})")
                if (attempt < 2) delay(800)
            }
        }
        withContext(Dispatchers.Main) {
            setStatus("Login failed: ${lastError?.message ?: "unknown error"}")
            setError(true)
            setProcessing(false)
        }
        tokenFetchStarted.set(false)
    }
}
