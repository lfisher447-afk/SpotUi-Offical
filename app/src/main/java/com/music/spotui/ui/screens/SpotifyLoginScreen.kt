@file:Suppress("TooManyFunctions", "LongMethod")

package com.music.spotui.ui.screens

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.metrolist.spotify.SpotifyAuth
import com.music.spotui.R
import com.music.spotui.data.api.SpotifyTokenProvider
import com.music.spotui.data.preferences.setDeezerArl
import com.music.spotui.data.preferences.setYoutubeCookie
import com.music.spotui.ui.navigation.Routes
import com.music.spotui.ui.screens.SpotifyLogin.helper.LOGIN_BACKGROUND_DARK
import com.music.spotui.ui.screens.SpotifyLogin.helper.LOGIN_CARD_DARK
import com.music.spotui.ui.screens.SpotifyLogin.helper.LOGIN_SPOTIFY_GREEN
import com.music.spotui.ui.screens.SpotifyLogin.helper.LOGIN_TEXT_SECONDARY
import com.music.spotui.ui.screens.SpotifyLogin.helper.LoginSessionInspector
import com.music.spotui.ui.screens.SpotifyLogin.helper.openSpotifyInChromeCustomTab
import com.music.spotui.ui.screens.SpotifyLogin.helper.parseSpotifyCookiesFromText
import com.music.spotui.ui.screens.SpotifyLogin.module.AuthBrowserEnginePanel
import com.music.spotui.ui.screens.SpotifyLogin.module.AuthEcosystemStatusBanner
import com.music.spotui.ui.screens.SpotifyLogin.module.ChromeCustomTabAuthCard
import com.music.spotui.ui.screens.SpotifyLogin.module.LocalUrlLoginCard
import com.music.spotui.ui.screens.SpotifyLogin.module.MultiPlatformTokensCard
import com.music.spotui.ui.screens.SpotifyLogin.page.LocalLoginSubmission
import com.music.spotui.ui.screens.SpotifyLogin.page.LocalLoginWebServer
import com.music.spotui.util.localizedUiText
import com.music.spotui.util.uiLocalized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

private enum class AuthMethodTab(
    val title: String,
    val icon: ImageVector,
    val tag: String,
) {
    LOCAL_URL("Local URL", Icons.Default.Http, "login_tab_local_url"),
    BROWSER("Chrome Tab", Icons.Default.Language, "login_tab_browser"),
    WEBVIEW("Browser Engine", Icons.Default.Public, "login_tab_webview"),
    OAUTH_PKCE("OAuth PKCE", Icons.Default.VerifiedUser, "login_tab_oauth"),
    TOKENS("Tokens", Icons.Default.Key, "login_tab_tokens"),
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SpotifyLoginScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val language = com.music.spotui.data.preferences.SpotidroidLanguage.current
    fun message(value: String) = localizedUiText(value, language)

    var active by remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        onDispose {
            active = false
            LocalLoginWebServer.stop()
        }
    }

    var selectedTab by remember { mutableStateOf(AuthMethodTab.LOCAL_URL) }
    var rejectedCookie by remember { mutableStateOf<String?>(null) }

    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }
    var authStatusRefreshTick by remember { mutableIntStateOf(0) }

    val tokenFetchStarted = remember { AtomicBoolean(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val serverState by LocalLoginWebServer.state.collectAsStateWithLifecycle()
    val authStatus = remember(authStatusRefreshTick) { LoginSessionInspector.inspect(context) }

    val navigateToHome: () -> Unit = {
        if (com.music.spotui.di.SongPlayer.webPlayerEnabled &&
            com.music.spotui.data.preferences.isWebPlaybackEnabled(context)
        ) {
            runCatching {
                com.music.spotui.di.SpotifyWebPlayer.refreshLogin(context)
            }.onFailure { Timber.w(it, "Spotify playback session refresh deferred") }
        }
        navController.navigate(Routes.Home.route) {
            popUpTo(Routes.Login.route) { inclusive = true }
            launchSingleTop = true
        }
    }

    val submitSpotifyPayload: (String) -> Unit = submit@{ rawPayload ->
        val (spDc, spKey) = parseSpotifyCookiesFromText(rawPayload)
        if (spDc.isBlank()) {
            hasError = true
            statusMessage = "No valid sp_dc cookie found in input"
            return@submit
        }
        if (tokenFetchStarted.compareAndSet(false, true)) {
            finishLogin(
                spDc = spDc,
                spKey = spKey,
                view = webViewRef,
                context = context.applicationContext,
                scope = scope,
                setProcessing = { isProcessing = it },
                setStatus = { statusMessage = it },
                setError = { hasError = it },
                tokenFetchStarted = tokenFetchStarted,
                onSuccess = navigateToHome,
                localize = ::message,
                isActive = { active },
                onRejected = { rejectedCookie = it },
            )
        }
    }

    // Auto-start Local URL portal on first visit to Local URL tab
    LaunchedEffect(selectedTab) {
        if (selectedTab == AuthMethodTab.LOCAL_URL && !LocalLoginWebServer.state.value.isRunning) {
            withContext(Dispatchers.IO) {
                LocalLoginWebServer.start(allowLan = false)
            }
        }
    }

    // Listen for live submissions from the Local URL web server
    LaunchedEffect(Unit) {
        LocalLoginWebServer.submissions.collectLatest { submission ->
            when (submission) {
                is LocalLoginSubmission.SpotifyCookie -> {
                    submitSpotifyPayload("sp_dc=${submission.spDc}; sp_key=${submission.spKey}")
                }
                is LocalLoginSubmission.YouTubeCookie -> {
                    setYoutubeCookie(context.applicationContext, submission.cookieHeader)
                    authStatusRefreshTick++
                    statusMessage = "YouTube Music session saved"
                    hasError = false
                }
                is LocalLoginSubmission.DeezerArl -> {
                    setDeezerArl(context.applicationContext, submission.arl)
                    authStatusRefreshTick++
                    statusMessage = "Deezer ARL saved"
                    hasError = false
                }
            }
        }
    }

    // Session capture for Auth Browser Engine runs inside AuthBrowserEnginePanel.

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF101827), Color(LOGIN_BACKGROUND_DARK), Color(0xFF06080D)),
                ),
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Branded Top Bar with App Logo
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                if (navController.previousBackStackEntry != null) {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.size(48.dp).testTag("login_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = uiLocalized("Back"),
                            tint = Color.White,
                        )
                    }
                }
                Image(
                    painter = painterResource(id = R.drawable.spotui_logo_main),
                    contentDescription = "SpotUI Logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (isProcessing || statusMessage.isNotBlank()) statusMessage else "SpotUI Auth Studio",
                        color = if (hasError) Color(0xFFFF6B7A) else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Multi-method login & session bridge",
                        color = Color(LOGIN_TEXT_SECONDARY),
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
            }

            if (isProcessing) {
                CircularProgressIndicator(
                    color = Color(LOGIN_SPOTIFY_GREEN),
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(28.dp).padding(end = 4.dp),
                )
            } else {
                IconButton(
                    onClick = {
                        runCatching {
                            CookieManager.getInstance().removeAllCookies(null)
                            CookieManager.getInstance().flush()
                        }
                        rejectedCookie = null
                        hasError = false
                        statusMessage = "Browser cookies cleared"
                    },
                    modifier = Modifier.size(48.dp).testTag("login_clear_cookies_button"),
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Reset WebView Cookies",
                        tint = Color(LOGIN_TEXT_SECONDARY),
                    )
                }
            }
        }

        // Method Tabs
        ScrollableTabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = Color.Transparent,
            contentColor = Color(LOGIN_SPOTIFY_GREEN),
            edgePadding = 12.dp,
            divider = {},
        ) {
            AuthMethodTab.entries.forEach { tab ->
                val selected = selectedTab == tab
                Tab(
                    selected = selected,
                    onClick = { selectedTab = tab },
                    modifier = Modifier.height(48.dp).testTag(tab.tag),
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = null,
                                tint = if (selected) Color(LOGIN_SPOTIFY_GREEN) else Color(LOGIN_TEXT_SECONDARY),
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = tab.title,
                                color = if (selected) Color.White else Color(LOGIN_TEXT_SECONDARY),
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                            )
                        }
                    },
                )
            }
        }

        // Body Content
        when (selectedTab) {
            AuthMethodTab.LOCAL_URL -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AuthEcosystemStatusBanner(status = authStatus)
                    LocalUrlLoginCard(
                        context = context,
                        serverState = serverState,
                        onStartServer = { allowLan ->
                            scope.launch(Dispatchers.IO) {
                                LocalLoginWebServer.start(allowLan = allowLan)
                            }
                        },
                        onStopServer = { LocalLoginWebServer.stop() },
                        onOpenLocalUrl = { url -> openSpotifyInChromeCustomTab(context, url) },
                    )
                }
            }

            AuthMethodTab.BROWSER -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AuthEcosystemStatusBanner(status = authStatus)
                    ChromeCustomTabAuthCard(
                        context = context,
                        onPasteFromClipboard = { clip -> submitSpotifyPayload(clip) },
                    )
                }
            }

            AuthMethodTab.WEBVIEW -> {
                AuthBrowserEnginePanel(
                    context = context,
                    localPortalUrl = if (serverState.isRunning) serverState.loopbackUrl else null,
                    isProcessingAuth = isProcessing,
                    rejectedSpotifyCookie = rejectedCookie,
                    onSpotifySessionCaptured = { spDc, spKey, wv ->
                        webViewRef = wv
                        if (tokenFetchStarted.compareAndSet(false, true)) {
                            finishLogin(
                                spDc = spDc,
                                spKey = spKey,
                                view = wv,
                                context = context.applicationContext,
                                scope = scope,
                                setProcessing = { isProcessing = it },
                                setStatus = { statusMessage = it },
                                setError = { hasError = it },
                                tokenFetchStarted = tokenFetchStarted,
                                onSuccess = navigateToHome,
                                localize = ::message,
                                isActive = { active },
                                onRejected = { rejectedCookie = it },
                            )
                        }
                    },
                    onYoutubeCookieCaptured = { cookieHeader ->
                        setYoutubeCookie(context.applicationContext, cookieHeader)
                        authStatusRefreshTick++
                        statusMessage = "YouTube Music session synced from Browser Engine"
                        hasError = false
                    },
                    onDeezerArlCaptured = { arl ->
                        setDeezerArl(context.applicationContext, arl)
                        authStatusRefreshTick++
                        statusMessage = "Deezer ARL synced from Browser Engine"
                        hasError = false
                    },
                    onSwitchToLocalUrl = { selectedTab = AuthMethodTab.LOCAL_URL },
                )
            }

            AuthMethodTab.OAUTH_PKCE -> {
                OAuthPkceTabContent(
                    context = context,
                    scope = scope,
                    onProcessing = { isProcessing = it },
                    onStatus = { statusMessage = it },
                    onError = { hasError = it },
                    onComplete = { ok ->
                        authStatusRefreshTick++
                        if (ok) navigateToHome()
                    },
                )
            }

            AuthMethodTab.TOKENS -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AuthEcosystemStatusBanner(status = authStatus)
                    MultiPlatformTokensCard(
                        context = context,
                        onSubmitSpotifyCookie = { raw -> submitSpotifyPayload(raw) },
                        onSaveYoutubeCookie = { raw ->
                            setYoutubeCookie(context.applicationContext, raw.trim())
                            authStatusRefreshTick++
                            statusMessage = "YouTube Music cookie saved"
                            hasError = false
                            Toast.makeText(context, "YouTube Music cookie saved", Toast.LENGTH_SHORT).show()
                        },
                        onSaveDeezerArl = { raw ->
                            setDeezerArl(context.applicationContext, raw.trim())
                            authStatusRefreshTick++
                            statusMessage = "Deezer ARL saved"
                            hasError = false
                            Toast.makeText(context, "Deezer ARL saved", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun OAuthPkceTabContent(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    onProcessing: (Boolean) -> Unit,
    onStatus: (String) -> Unit,
    onError: (Boolean) -> Unit,
    onComplete: (Boolean) -> Unit,
) {
    var clientIdInput by remember { mutableStateOf(SpotifyOAuth.clientId(context)) }
    val validClient = SpotifyOAuth.isValidClientId(clientIdInput)
    var signedIn by remember { mutableStateOf(SpotifyOAuth.isSignedIn(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(LOGIN_CARD_DARK)),
            border = BorderStroke(1.dp, Color(0xFF283146)),
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Official Spotify OAuth 2.0 (PKCE)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
                Text(
                    text = "Uses Chrome Custom Tabs + loopback redirect (${SpotifyOAuth.REGISTERED_REDIRECT_URI}) with zero cookies or WebView anti-bot friction.",
                    color = Color(LOGIN_TEXT_SECONDARY),
                    fontSize = 12.sp,
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF0B0F17),
                    border = BorderStroke(1.dp, Color(0xFF263147)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Redirect URI", color = Color(LOGIN_TEXT_SECONDARY), fontSize = 10.sp)
                            Text(
                                SpotifyOAuth.REGISTERED_REDIRECT_URI,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                            )
                        }
                        IconButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(ClipData.newPlainText("Spotify Redirect URI", SpotifyOAuth.REGISTERED_REDIRECT_URI))
                                Toast.makeText(context, "Redirect URI copied", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Redirect URI", tint = Color(LOGIN_SPOTIFY_GREEN))
                        }
                    }
                }

                OutlinedTextField(
                    value = clientIdInput,
                    onValueChange = {
                        clientIdInput = it
                        SpotifyOAuth.saveClientId(context, it)
                    },
                    label = { Text("Spotify Client ID (32 hex chars)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(LOGIN_SPOTIFY_GREEN),
                        unfocusedBorderColor = Color(0xFF39435B),
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("oauth_client_id_field"),
                )

                Button(
                    onClick = {
                        if (SpotifyOAuth.saveClientId(context, clientIdInput)) {
                            processOAuthLogin(
                                context = context,
                                scope = scope,
                                setProcessing = onProcessing,
                                setStatus = onStatus,
                                setError = onError,
                                onComplete = { ok ->
                                    signedIn = SpotifyOAuth.isSignedIn(context)
                                    onComplete(ok)
                                },
                                onSuccess = { onComplete(true) },
                            )
                        }
                    },
                    enabled = validClient,
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("oauth_sign_in_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(LOGIN_SPOTIFY_GREEN),
                        contentColor = Color.Black,
                    ),
                ) {
                    Text(if (signedIn) "Re-Sync Spotify OAuth" else "Sign In via OAuth PKCE", fontWeight = FontWeight.Bold)
                }

                if (signedIn) {
                    OutlinedButton(
                        onClick = {
                            SpotifyOAuth.signOut(context)
                            signedIn = false
                            onStatus("OAuth session signed out")
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Text("Sign Out OAuth", color = Color(0xFFFF7A85))
                    }
                }
            }
        }
    }
}

private fun extractWebViewCookie(name: String): String? = runCatching {
    val cookieManager = CookieManager.getInstance()
    val domains = listOf(
        "https://open.spotify.com",
        "https://accounts.spotify.com",
        "https://spotify.com",
    )
    for (domain in domains) {
        val allCookies = cookieManager.getCookie(domain) ?: continue
        val match = allCookies.split(";")
            .mapNotNull {
                val parts = it.trim().split("=", limit = 2)
                if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
            }
            .firstOrNull { it.first == name && it.second.isNotBlank() }
            ?.second
        if (!match.isNullOrBlank()) return@runCatching match
    }
    null
}.getOrNull()

private fun finishLogin(
    spDc: String,
    spKey: String,
    view: WebView?,
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    setProcessing: (Boolean) -> Unit,
    setStatus: (String) -> Unit,
    setError: (Boolean) -> Unit,
    tokenFetchStarted: AtomicBoolean,
    onSuccess: () -> Unit,
    localize: (String) -> String,
    isActive: () -> Boolean,
    onRejected: (String) -> Unit,
) {
    setProcessing(true)
    setError(false)
    setStatus(localize("Connecting…"))
    scope.launch {
        var succeeded = false
        try {
            val result = kotlinx.coroutines.withTimeout(45_000) {
                withContext(Dispatchers.IO) { SpotifyTokenProvider.signIn(context, spDc.trim(), spKey) }
            }
            result.getOrThrow()
            if (!isActive()) return@launch
            runCatching { view?.stopLoading() }
            onSuccess()
            succeeded = true
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            if (isActive()) {
                setError(true)
                setStatus(localize("Spotify took too long to respond. Try again."))
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Timber.w("Spotify login failed: %s", failure.javaClass.simpleName)
            if (isActive()) {
                setError(true)
                setStatus(localize("Could not sign in to Spotify. Check your connection and try again."))
            }
        } finally {
            tokenFetchStarted.set(false)
            if (isActive()) {
                setProcessing(false)
                if (!succeeded) onRejected(spDc)
            }
        }
    }
}
