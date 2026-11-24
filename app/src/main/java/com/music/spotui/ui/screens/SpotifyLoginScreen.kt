@file:Suppress("TooManyFunctions", "LargeClass", "LongMethod", "LongParameterList", "MagicNumber")

package com.music.spotui.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
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
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MusicNote
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
import com.music.spotui.util.DevConsoleManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// ═══════════════════════════════════════════════════════════════════════════
// 1. Sign-in pipeline: cookie → token → session → profile → library sync
// ═══════════════════════════════════════════════════════════════════════════

private const val MAX_CLIPBOARD_PAYLOAD_CHARS = 20_000
private const val LOGIN_TOKEN_ATTEMPTS = 3
private const val LOGIN_RETRY_BASE_DELAY_MS = 400L
private const val LOGIN_SYNC_TIMEOUT_MS = 120_000L
private const val LOGIN_SUCCESS_NAV_DELAY_MS = 350L
private const val MIN_SP_DC_LENGTH = 16

/** The four visible steps of "cookie in → library synced". */
internal enum class LoginStage(val label: String) {
    VERIFY("Cookie"),
    TOKEN("Session"),
    PROFILE("Profile"),
    LIBRARY("Library"),
}

internal sealed interface LoginOutcome {
    data class Success(
        val signInLabel: String,
        val accountName: String?,
        val syncDetail: String?,
        val syncWarning: String?,
    ) : LoginOutcome

    data class Failure(
        val stage: LoginStage,
        val message: String,
        val cause: Throwable?,
    ) : LoginOutcome
}

/**
 * Process-lifetime scope for login + sync work. The screen's own scope dies the moment the user
 * presses Back, which used to cancel a half-finished library import; work launched here survives.
 */
private object LoginPipelineScope {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

internal fun launchLoginTask(block: suspend CoroutineScope.() -> Unit): Job =
    LoginPipelineScope.scope.launch(block = block)

/**
 * The whole sign-in pipeline as one suspend function with a structured result:
 * cookie → web-player token (with retry/backoff) → persist session → profile → library sync.
 */
internal suspend fun runLoginPipeline(
    context: Context,
    spDc: String,
    spKey: String,
    source: String,
    preCapturedToken: SpotifyInternalToken?,
    autoSyncLibrary: Boolean,
    onStage: (LoginStage) -> Unit,
): LoginOutcome {
    val app = context.applicationContext
    DevConsoleManager.logAuth(
        "Verifying session via $source (sp_dc len=${spDc.length}, preToken=${preCapturedToken != null})",
    )

    // 1 — sp_dc cookie → web-player access token. Bad cookies are not retried; network blips are.
    onStage(LoginStage.VERIFY)
    var token: SpotifyInternalToken? = null
    var lastError: Throwable? = null
    var attempt = 0
    while (token == null && attempt < LOGIN_TOKEN_ATTEMPTS) {
        attempt++
        val result = SpotifyAuth.fetchAccessToken(spDc, spKey)
        token = result.getOrNull()
        if (token != null) {
            DevConsoleManager.logAuth("Login verified via $source on attempt $attempt (anon=${token.isAnonymous})")
            break
        }
        val error = result.exceptionOrNull()
        lastError = error
        Timber.e(error, "Spotify token exchange failed (attempt $attempt)")
        DevConsoleManager.logWarning("SpotifyLogin", "Attempt $attempt/$LOGIN_TOKEN_ATTEMPTS failed: ${error?.message}")
        if (!SpotifyAuth.isRetryable(error)) break
        if (attempt < LOGIN_TOKEN_ATTEMPTS) delay(LOGIN_RETRY_BASE_DELAY_MS * attempt * attempt)
    }

    // Fallback: WebView captured a live non-anonymous open.spotify.com #session token alongside sp_dc.
    var viaWebSession = false
    if (token == null && preCapturedToken != null && !preCapturedToken.isAnonymous && preCapturedToken.accessToken.isNotBlank()) {
        token = preCapturedToken
        viaWebSession = true
        DevConsoleManager.logAuth("Accepted verified WebView #session token ($source) as fallback")
    }

    val verifiedToken = token ?: run {
        val message = SpotifyAuth.describeFailure(lastError)
        DevConsoleManager.logError("SpotifyLogin", "Verification failed ($source): ${lastError?.message ?: message}", lastError)
        return LoginOutcome.Failure(LoginStage.VERIFY, message, lastError)
    }

    // 2 — persist the session and arm the token provider.
    onStage(LoginStage.TOKEN)
    SpotifySession.setSpDc(app, spDc, spKey)
    SpotifyTokenProvider.seedVerifiedToken(verifiedToken.accessToken, verifiedToken.accessTokenExpirationTimestampMs)

    // 3 — who is this? (non-fatal: the session is already valid)
    onStage(LoginStage.PROFILE)
    val accountName = Spotify.me().getOrNull()?.let { user ->
        user.displayName?.takeIf { it.isNotBlank() } ?: user.id.takeIf { it.isNotBlank() }
    }
    DevConsoleManager.logAuth("Profile resolved=${accountName != null}")

    // 4 — full native library import. Kept alive in the pipeline scope so a slow sync can
    //     outlive the wait below (and the screen) instead of being cancelled with it.
    var syncDetail: String? = null
    var syncWarning: String? = null
    if (autoSyncLibrary) {
        onStage(LoginStage.LIBRARY)
        val syncJob = LoginPipelineScope.scope.async {
            // Never let a throwing sync crash `await()` — fold it into a failed Result.
            try {
                SpotifySync.syncAllFromSession(app)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
        val syncResult = withTimeoutOrNull(LOGIN_SYNC_TIMEOUT_MS) { syncJob.await() }
        when {
            syncResult == null ->
                syncWarning = "Library sync is still running — it will finish in the background."
            syncResult.isSuccess -> syncDetail = syncResult.getOrNull()?.detail
            else -> syncWarning =
                "Signed in, but the library sync failed: ${SpotifyAuth.describeFailure(syncResult.exceptionOrNull())}"
        }
    }
    if (syncDetail.isNullOrBlank()) SpotifySync.notifyAuthStateChanged("Signed in!")

    return LoginOutcome.Success(
        signInLabel = if (viaWebSession) "Signed in via Web session!" else "Signed in!",
        accountName = accountName,
        syncDetail = syncDetail,
        syncWarning = syncWarning,
    )
}

/**
 * Callback-style entry point. The heavy lifting runs in [LoginPipelineScope]; UI callbacks are
 * delivered on the main thread through [scope] and are simply dropped if that scope is gone.
 */
internal fun processLoginSession(
    context: Context,
    spDc: String,
    spKey: String = "",
    source: String = "Session",
    preCapturedToken: SpotifyInternalToken? = null,
    autoSyncLibrary: Boolean = true,
    scope: CoroutineScope,
    setProcessing: (Boolean) -> Unit,
    setStatus: (String) -> Unit,
    setError: (Boolean) -> Unit,
    onComplete: (Boolean) -> Unit,
    onSuccess: (String?) -> Unit,
    onStage: (LoginStage) -> Unit = {},
    onAccount: (String?) -> Unit = {},
) {
    val app = context.applicationContext
    setProcessing(true)
    setError(false)
    setStatus("Verifying Spotify session…")

    fun onMain(block: () -> Unit) {
        scope.launch(Dispatchers.Main) { block() }
    }

    launchLoginTask {
        val outcome = runLoginPipeline(
            context = app,
            spDc = spDc,
            spKey = spKey,
            source = source,
            preCapturedToken = preCapturedToken,
            autoSyncLibrary = autoSyncLibrary,
            onStage = { stage ->
                onMain {
                    onStage(stage)
                    setStatus(
                        when (stage) {
                            LoginStage.VERIFY -> "Verifying Spotify session…"
                            LoginStage.TOKEN -> "Starting your web-player session…"
                            LoginStage.PROFILE -> "Loading your account…"
                            LoginStage.LIBRARY -> "Importing playlists, liked songs & artists…"
                        },
                    )
                }
            },
        )

        when (outcome) {
            is LoginOutcome.Success -> {
                onMain {
                    UiSoundEngine.playToggleSound(app, true)
                    onAccount(outcome.accountName)
                    setStatus(outcome.syncWarning ?: outcome.syncDetail ?: outcome.signInLabel)
                    setProcessing(false)
                    setError(false)
                    onComplete(true)
                }
                delay(LOGIN_SUCCESS_NAV_DELAY_MS)
                onMain { onSuccess(outcome.syncDetail) }
            }
            is LoginOutcome.Failure -> onMain {
                onStage(outcome.stage)
                setStatus("Sign-in failed: ${outcome.message}")
                setError(true)
                setProcessing(false)
                onComplete(false)
            }
        }
    }
}

// ── Clipboard / cookie-payload detection ─────────────────────────────────────

private val RAW_SP_DC_VALUE = Regex("^AQ[A-Za-z0-9_-]{50,}$")

/**
 * Strict "is this clipboard text a Spotify cookie?" test. [parseSpotifyCookiesFromText] is
 * deliberately forgiving (it treats any text as a raw cookie), which is right for a paste box but
 * would auto-fill random clipboard contents — so clipboard sniffing uses this stricter check.
 */
internal fun looksLikeSpotifyCookiePayload(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed.length > MAX_CLIPBOARD_PAYLOAD_CHARS) return false
    return trimmed.contains("sp_dc", ignoreCase = true) || RAW_SP_DC_VALUE.matches(trimmed)
}

private fun readClipboardText(context: Context): String {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return ""
    val clip = manager.primaryClip ?: return ""
    if (clip.itemCount == 0) return ""
    return runCatching { clip.getItemAt(0).coerceToText(context)?.toString().orEmpty() }.getOrDefault("")
}

// ═══════════════════════════════════════════════════════════════════════════
// 2. Login screen
// ═══════════════════════════════════════════════════════════════════════════

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
    var stage by remember { mutableStateOf<LoginStage?>(null) }
    val isSyncing = isProcessing && stage == LoginStage.LIBRARY
    var accountName by remember { mutableStateOf<String?>(null) }
    var syncSummaryText by remember { mutableStateOf(SpotifySync.lastSyncDetail) }
    var statusMessage by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }

    var showManualCookieInput by remember { mutableStateOf(false) }
    var cookieText by remember { mutableStateOf("") }
    var showLogsDialog by remember { mutableStateOf(false) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var popupWebView by remember { mutableStateOf<WebView?>(null) }

    val tokenFetchStarted = remember { AtomicBoolean(false) }
    val cookiesWiped = remember { AtomicBoolean(false) }

    LaunchedEffect(Unit) {
        val ready = SpotifyTokenProvider.syncAuthState(context)
        isAccountLinked = ready
        DevConsoleManager.logAuth("Spotify Music Sync opened (linked=$ready, mode=EmbeddedAntiBotWebView)")
    }

    fun closePopup() {
        val popup = popupWebView
        popupWebView = null
        popup?.post { runCatching { popup.stopLoading(); popup.destroy() } }
    }

    DisposableEffect(Unit) {
        onDispose {
            popupWebView?.let { runCatching { it.stopLoading(); it.destroy() } }
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

    fun handleCapturedSession(spDc: String, spKey: String, source: String) {
        if (!tokenFetchStarted.compareAndSet(false, true)) return
        closePopup()
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
            onStage = { stage = it },
            onAccount = { accountName = it },
            onComplete = { ok ->
                if (!ok) {
                    tokenFetchStarted.set(false)
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
                Toast.makeText(context, "Signed in$who & synced Spotify library!", Toast.LENGTH_SHORT).show()
                navigateToHome()
            },
        )
    }

    fun checkAndCaptureCookies(url: String?) {
        if (tokenFetchStarted.get()) return
        val sweep = sweepSpotifyCookies(url)
        val dc = sweep.spDc
        if (!dc.isNullOrBlank() && dc.length >= MIN_SP_DC_LENGTH) {
            DevConsoleManager.logAuth("Auto-captured valid sp_dc cookie from WebView (len=${dc.length})")
            handleCapturedSession(dc, sweep.spKey, "EmbeddedWebViewAutoCapture")
        }
    }

    BackHandler {
        when {
            popupWebView != null -> closePopup()
            !showManualCookieInput && webViewInstance?.canGoBack() == true -> webViewInstance?.goBack()
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
                effectiveLinked -> "Spotify session linked · Tap Done"
                else -> "Sign in via anti-bot embedded Spotify login (auto-captures sp_dc)"
            }

            // Top bar
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

            if (stage != null) {
                LoginStageRow(current = stage, hasError = hasError)
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

            if (showManualCookieInput) {
                ManualCookiePane(
                    cookieText = cookieText,
                    onCookieTextChange = { cookieText = it },
                    isProcessing = isProcessing,
                    onPasteClipboard = {
                        UiSoundEngine.playTapChime(context)
                        val clip = readClipboardText(context).trim()
                        when {
                            clip.isBlank() ->
                                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                            else -> {
                                cookieText = clip
                                if (!looksLikeSpotifyCookiePayload(clip)) {
                                    Toast.makeText(
                                        context,
                                        "Pasted, but it doesn't look like an sp_dc cookie",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        }
                    },
                    onConnectAndSync = { dc, key ->
                        handleCapturedSession(dc, key, "ManualCookiePaste")
                    },
                    onClose = { showManualCookieInput = false },
                )
            } else {
                // Primary embedded WebView
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

                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                val loginUserAgent = resolveLoginUserAgent(ctx, LoginUaProfile.CLEAN_MOBILE)
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    @Suppress("DEPRECATION")
                                    databaseEnabled = true
                                    setSupportMultipleWindows(true)
                                    javaScriptCanOpenWindowsAutomatically = true
                                    // Clean mobile UA matching the device's built-in WebView / Chromium engine
                                    userAgentString = loginUserAgent
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        runCatching { CookieManager.getInstance().flush() }
                                        checkAndCaptureCookies(url)
                                    }

                                    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                        super.doUpdateVisitedHistory(view, url, isReload)
                                        checkAndCaptureCookies(url)
                                    }

                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        checkAndCaptureCookies(request?.url?.toString())
                                        return false // every login hop (Spotify / Google / Apple / captcha) stays in-WebView
                                    }
                                }

                                webChromeClient = object : WebChromeClient() {
                                    // OAuth providers open their sign-in in a popup. It must be attached to the
                                    // view tree, carry its own client (otherwise redirects escape to the system
                                    // browser) and report cookies back, or the login never completes.
                                    override fun onCreateWindow(
                                        view: WebView?,
                                        isDialog: Boolean,
                                        isUserGesture: Boolean,
                                        resultMsg: Message?,
                                    ): Boolean {
                                        val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                                        val popup = WebView(ctx).apply {
                                            val popupCookies = CookieManager.getInstance()
                                            popupCookies.setAcceptCookie(true)
                                            popupCookies.setAcceptThirdPartyCookies(this, true)
                                            settings.javaScriptEnabled = true
                                            settings.domStorageEnabled = true
                                            settings.javaScriptCanOpenWindowsAutomatically = true
                                            settings.userAgentString = loginUserAgent
                                            webViewClient = object : WebViewClient() {
                                                override fun onPageFinished(v: WebView?, url: String?) {
                                                    super.onPageFinished(v, url)
                                                    runCatching { CookieManager.getInstance().flush() }
                                                    checkAndCaptureCookies(url)
                                                }

                                                override fun shouldOverrideUrlLoading(v: WebView?, request: WebResourceRequest?): Boolean {
                                                    checkAndCaptureCookies(request?.url?.toString())
                                                    return false
                                                }
                                            }
                                            webChromeClient = object : WebChromeClient() {
                                                override fun onCloseWindow(window: WebView?) {
                                                    super.onCloseWindow(window)
                                                    closePopup()
                                                    runCatching { CookieManager.getInstance().flush() }
                                                    checkAndCaptureCookies(SPOTIFY_LOGIN_URL)
                                                }
                                            }
                                        }
                                        closePopup()
                                        popupWebView = popup
                                        transport.webView = popup
                                        resultMsg.sendToTarget()
                                        return true
                                    }

                                    override fun onCloseWindow(window: WebView?) {
                                        super.onCloseWindow(window)
                                        if (window != null && window === popupWebView) closePopup()
                                    }
                                }

                                webViewInstance = this

                                // Wipe stale cookies/storage once per screen visit. Toggling the manual pane
                                // recreates this WebView and must NOT log the user back out mid-flow.
                                if (cookiesWiped.compareAndSet(false, true)) {
                                    runCatching { WebStorage.getInstance().deleteAllData() }
                                    cookieManager.removeAllCookies {
                                        cookieManager.flush()
                                        cookieManager.setAcceptCookie(true)
                                        cookieManager.setAcceptThirdPartyCookies(this, true)
                                        loadUrl(SPOTIFY_LOGIN_URL)
                                    }
                                } else {
                                    loadUrl(SPOTIFY_LOGIN_URL)
                                }
                            }
                        },
                        onRelease = { released ->
                            runCatching { released.stopLoading(); released.destroy() }
                            if (webViewInstance === released) webViewInstance = null
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // OAuth popup overlay (Google / Apple / Facebook sign-in)
        popupWebView?.let { popup ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(LOGIN_BACKGROUND_DARK)),
            ) {
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
                    modifier = Modifier.fillMaxSize().padding(top = 48.dp),
                )
                TextButton(
                    onClick = { closePopup() },
                    modifier = Modifier.align(Alignment.TopEnd).heightIn(min = 48.dp),
                ) {
                    Text("Close", color = Color(LOGIN_SPOTIFY_GREEN), fontWeight = FontWeight.Bold)
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

/** Four-step progress strip: Cookie → Session → Profile → Library. */
@Composable
private fun LoginStageRow(current: LoginStage?, hasError: Boolean) {
    val currentIndex = current?.ordinal ?: -1
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LoginStage.values().forEach { step ->
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
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, tint, RoundedCornerShape(10.dp))
                    .padding(horizontal = 6.dp, vertical = 5.dp),
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
) {
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
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
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
                enabled = !isProcessing && detectedDc.length >= MIN_SP_DC_LENGTH,
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

// ═══════════════════════════════════════════════════════════════════════════
// 3. SpotifyAuth — web-player token exchange (TOTP)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Spotify web-player authentication.
 *
 * Exchanges the `sp_dc` cookie (captured from the embedded login WebView) for a web-player
 * access token via `open.spotify.com/api/token`, which requires a TOTP generated from a shared
 * secret that Spotify rotates periodically. The secret + version come from a community-maintained
 * GitHub Gist.
 *
 *  - the TOTP secret is cached for [NUANCE_TTL_MS] and **single-flight** (a mutex stops parallel
 *    refreshes from hammering GitHub's 60 req/hour unauthenticated limit);
 *  - a *stale* cached secret is used when the Gist is unreachable or malformed;
 *  - a 400/401/403 while using a cached secret force-refreshes it once (max once a minute) and
 *    retries, so a secret rotation no longer looks like "your cookie is invalid";
 *  - cookie values are stripped of whitespace/control characters/`;` before entering a header;
 *  - [describeFailure] / [isRetryable] map raw exceptions to UI messages and retry policy.
 */
object SpotifyAuth {
    private const val TOKEN_URL = "https://open.spotify.com/api/token"
    private const val SERVER_TIME_URL = "https://open.spotify.com/api/server-time"
    private const val NUANCE_GIST_URL =
        "https://api.github.com/gists/22ed9c6ba463899e933427f7de1f0eef"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    const val LOGIN_URL =
        "https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F"

    // Direct entry to Spotify's web signup flow.
    const val SIGNUP_URL = "https://www.spotify.com/signup"

    private const val HTTP_TIMEOUT_MS = 15_000
    private const val TOTP_INTERVAL_SEC = 30L
    private const val TOTP_DIGITS_MODULUS = 1_000_000
    private const val NUANCE_TTL_MS = 6 * 60 * 60 * 1000L
    private const val NUANCE_MIN_REFRESH_INTERVAL_MS = 60_000L
    private val SECRET_ROTATION_STATUS_CODES = setOf(400, 401, 403)

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    @Serializable
    private data class Nuance(val s: String, val v: Int)

    @Serializable
    private data class GistFile(val content: String)

    @Serializable
    private data class GistFiles(val files: Map<String, GistFile>)

    @Serializable
    private data class ServerTimeResponse(val serverTime: Long)

    private class NuanceLease(val nuance: Nuance, val wasCached: Boolean)

    private val nuanceLock = Mutex()

    @Volatile
    private var nuanceCache: Nuance? = null

    @Volatile
    private var nuanceFetchedAtMs = 0L

    /**
     * Fetches an internal web-player access token using session cookies and a TOTP.
     *
     * Fails with [Spotify.SpotifyException] (401) when Spotify answers with an anonymous token,
     * which is what an invalid/expired `sp_dc` produces. Coroutine cancellation is rethrown,
     * never swallowed into the [Result].
     */
    suspend fun fetchAccessToken(
        spDc: String,
        spKey: String = "",
    ): Result<SpotifyInternalToken> {
        return try {
            val cookie = sanitizeCookieValue(spDc)
            if (cookie.isEmpty()) throw Spotify.SpotifyException(401, "No sp_dc cookie provided")
            val key = sanitizeCookieValue(spKey)

            val lease = leaseNuance(forceRefresh = false)
            val token = try {
                requestToken(cookie, key, lease.nuance)
            } catch (e: Spotify.SpotifyException) {
                val secretMayHaveRotated = lease.wasCached && e.statusCode in SECRET_ROTATION_STATUS_CODES
                if (!secretMayHaveRotated) throw e
                requestToken(cookie, key, leaseNuance(forceRefresh = true).nuance)
            }
            Result.success(token)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** `true` when trying again can plausibly succeed (network blips, 5xx); never for a bad cookie. */
    fun isRetryable(error: Throwable?): Boolean = when (error) {
        null -> false
        is Spotify.SpotifyException -> error.statusCode == 408 || error.statusCode == 425 || error.statusCode in 500..599
        is IOException -> true
        else -> false
    }

    /** A short, user-facing explanation for a login/sync failure. */
    fun describeFailure(error: Throwable?): String = when (error) {
        null -> "Unknown error"
        is Spotify.SpotifyException -> when (error.statusCode) {
            401, 403 -> "Spotify rejected the sp_dc cookie — it expired or you signed out. Log in again."
            429 -> "Spotify is rate limiting this device. Wait a minute, then retry."
            503 -> "A required helper service is unreachable (${error.message})."
            else -> error.message ?: "Spotify error ${error.statusCode}"
        }
        is UnknownHostException -> "No internet connection."
        is SocketTimeoutException -> "The connection timed out."
        else -> error.message ?: error.javaClass.simpleName
    }

    private suspend fun requestToken(
        spDc: String,
        spKey: String,
        nuance: Nuance,
    ): SpotifyInternalToken {
        val totp = generateTotp(nuance.s, fetchServerTime())
        val tokenUrl = buildString {
            append(TOKEN_URL)
            append("?reason=transport")
            append("&productType=web-player")
            append("&totp=$totp")
            append("&totpServer=$totp")
            append("&totpVer=${nuance.v}")
        }
        val cookieHeader = buildString {
            append("sp_dc=$spDc")
            if (spKey.isNotEmpty()) append("; sp_key=$spKey")
        }

        val body = withContext(Dispatchers.IO) {
            httpGet(tokenUrl, mapOf("Cookie" to cookieHeader))
        }
        val token = json.decodeFromString<SpotifyInternalToken>(body)
        if (token.isAnonymous || token.accessToken.isBlank()) {
            throw Spotify.SpotifyException(
                401,
                "Received anonymous token — sp_dc cookie is invalid or expired",
            )
        }
        return token
    }

    private suspend fun leaseNuance(forceRefresh: Boolean): NuanceLease = nuanceLock.withLock {
        val now = System.currentTimeMillis()
        val cached = nuanceCache
        val cacheIsFresh = cached != null && now - nuanceFetchedAtMs < NUANCE_TTL_MS
        val refreshAllowed = now - nuanceFetchedAtMs >= NUANCE_MIN_REFRESH_INTERVAL_MS
        val reuseCache = cached != null && if (forceRefresh) !refreshAllowed else cacheIsFresh
        if (reuseCache && cached != null) return@withLock NuanceLease(cached, wasCached = true)

        try {
            val fetched = fetchNuance()
            nuanceCache = fetched
            nuanceFetchedAtMs = System.currentTimeMillis()
            NuanceLease(fetched, wasCached = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A stale secret still beats failing the whole login because GitHub hiccuped
            // or the gist payload changed shape.
            if (cached != null) NuanceLease(cached, wasCached = true) else throw e
        }
    }

    private suspend fun fetchNuance(): Nuance = withContext(Dispatchers.IO) {
        val body = try {
            httpGet(NUANCE_GIST_URL, emptyMap())
        } catch (e: Exception) {
            throw Spotify.SpotifyException(503, "Failed to fetch TOTP secret from gist: ${e.message}")
        }
        val gist = json.decodeFromString<GistFiles>(body)
        val nuancesJson = gist.files.values.firstOrNull()?.content
            ?: throw Spotify.SpotifyException(500, "Gist has no files")
        val nuances = json.decodeFromString<List<Nuance>>(nuancesJson)
        nuances.maxByOrNull { it.v }
            ?: throw Spotify.SpotifyException(500, "No nuance data found in gist")
    }

    private suspend fun fetchServerTime(): Long = withContext(Dispatchers.IO) {
        val body = try {
            httpGet(SERVER_TIME_URL, emptyMap())
        } catch (e: Exception) {
            throw Spotify.SpotifyException(503, "Failed to fetch Spotify server time: ${e.message}")
        }
        json.decodeFromString<ServerTimeResponse>(body).serverTime
    }

    /**
     * Generates a 6-digit TOTP using HMAC-SHA1 (RFC 6238).
     * @param secret Base32-encoded shared secret
     * @param serverTimeSec Spotify server time in seconds since epoch
     */
    internal fun generateTotp(secret: String, serverTimeSec: Long): String {
        val key = base32Decode(secret)
        val timeStep = serverTimeSec / TOTP_INTERVAL_SEC

        val timeBytes = ByteArray(8)
        var value = timeStep
        for (i in 7 downTo 0) {
            timeBytes[i] = (value and 0xFF).toByte()
            value = value shr 8
        }

        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val hash = mac.doFinal(timeBytes)

        val offset = hash[hash.size - 1].toInt() and 0x0F
        val code = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)

        return (code % TOTP_DIGITS_MODULUS).toString().padStart(6, '0')
    }

    internal fun base32Decode(input: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val cleaned = input.uppercase().replace("=", "")

        val output = mutableListOf<Byte>()
        var buffer = 0
        var bitsLeft = 0

        for (c in cleaned) {
            val value = alphabet.indexOf(c)
            if (value < 0) continue
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                output.add(((buffer shr bitsLeft) and 0xFF).toByte())
            }
        }
        return output.toByteArray()
    }

    /** Cookie values go straight into a header, so drop anything that could split or inject one. */
    private fun sanitizeCookieValue(value: String): String =
        value.filterNot { it.isWhitespace() || it.isISOControl() || it == ';' }

    private fun httpGet(urlString: String, extraHeaders: Map<String, String>): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            connection.connectTimeout = HTTP_TIMEOUT_MS
            connection.readTimeout = HTTP_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json, text/plain, */*")
            connection.setRequestProperty("Accept-Language", "en")
            for ((key, value) in extraHeaders) {
                connection.setRequestProperty(key, value)
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                throw Spotify.SpotifyException(responseCode, "HTTP $responseCode: ${errorBody.take(300)}")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
