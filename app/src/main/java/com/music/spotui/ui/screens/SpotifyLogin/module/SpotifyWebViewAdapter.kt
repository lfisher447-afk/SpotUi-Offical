@file:Suppress("TooManyFunctions", "LongMethod", "MagicNumber")

package com.music.spotui.ui.screens.SpotifyLogin.module

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.metrolist.spotify.Spotify
import com.metrolist.spotify.models.SpotifyInternalToken
import com.music.spotui.ui.screens.SpotifyLogin.helper.LoginUaProfile
import com.music.spotui.ui.screens.SpotifyLogin.helper.resetSpotifyWebSession
import com.music.spotui.ui.screens.SpotifyLogin.helper.resolveLoginUserAgent
import com.music.spotui.ui.screens.SpotifyLogin.helper.sweepSpotifyCookies
import com.music.spotui.util.DevConsoleManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayInputStream
import java.util.Locale

internal enum class SpotifyWebContentMode(val label: String) {
    MOBILE("Mobile"),
    DESKTOP("Desktop"),
}

internal enum class SpotifyMediaCapabilityStatus {
    SUPPORTED,
    UNSUPPORTED,
    UNKNOWN,
}

internal data class SpotifyEngineNavigationState(
    val currentUrl: String = Spotify.LOGIN_URL,
    val title: String = "Spotify WebView Sign-In",
    val progress: Int = 0,
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isDesktopMode: Boolean = false,
)

internal data class SpotifyEngineSecurityState(
    val isHttps: Boolean = true,
    val tlsStrictMode: Boolean = true,
    val certificateErrorHost: String? = null,
    val sslCancelledCount: Int = 0,
    val requestedWithStripped: Boolean = false,
)

internal data class SpotifyEngineShieldState(
    val enabled: Boolean = true,
    val totalBlocked: Int = 0,
    val trackersBlocked: Int = 0,
    val spotifyAdsBlocked: Int = 0,
    val vastBlocked: Int = 0,
    val lastBlockedUrl: String? = null,
)

internal data class SpotifyEngineProcessState(
    val isAlive: Boolean = true,
    val crashCount: Int = 0,
    val recoveredFromCrash: Boolean = false,
    val lastFailureMessage: String? = null,
    val autoRecoverHint: String? = null,
)

internal data class SpotifyMediaCapabilityState(
    val encryptedMediaStatus: SpotifyMediaCapabilityStatus = SpotifyMediaCapabilityStatus.UNKNOWN,
    val widevineStatus: SpotifyMediaCapabilityStatus = SpotifyMediaCapabilityStatus.UNKNOWN,
    val isDesktopMode: Boolean = false,
    val isPrivateMode: Boolean = false,
    val isAutoplayAllowed: Boolean = true,
    val cookiePolicy: String = "First-Party + SSO Storage Enabled",
    val deviceDrmProvider: String = "System WebView (Protected Media Isolated)",
    val engineDetails: String = "SpotUI Hardened WebView · Android ${Build.VERSION.RELEASE}",
)

/**
 * Unified, hardened Chromium WebView Engine + Privacy Shield for SpotUI Spotify Auth & Web Sync.
 *
 * Designed directly from `com.musify.browser.engine.WebViewAdapter` with full anti-bot hardening:
 * 1. Strips `X-Requested-With` app package leak via [WebSettingsCompat.setRequestedWithHeaderOriginAllowList].
 * 2. Defaults to [LoginUaProfile.SYSTEM_DEFAULT] so `User-Agent` & `Sec-CH-UA` Client Hints match
 *    100% — zero `navigator` or `Document.prototype` JS overrides that trip reCAPTCHA Enterprise.
 * 3. Wipes stale cookies & [WebStorage] cleanly before loading [Spotify.LOGIN_URL].
 * 4. Neutralizes Spotify ad-logic/analytics (`{}` 200 OK JSON anti-freeze) & VAST/VMAP + 3P trackers,
 *    while preserving all auth/SSO/reCAPTCHA endpoints untouched.
 * 5. Enforces strict TLS (`handler?.cancel()`), sandboxed local storage (`allowFileAccess=false`),
 *    renderer crash recovery (`onRenderProcessGone`), and in-app multi-window SSO popups.
 */
internal class SpotifyWebViewAdapter(
    val isPrivate: Boolean = false,
    private val initialUaProfile: LoginUaProfile = LoginUaProfile.SYSTEM_DEFAULT,
    private val onSessionCaptured: (spDc: String, spKey: String, sessionToken: SpotifyInternalToken?, source: String) -> Unit,
    private val onPopupRequested: (WebView?) -> Unit,
) {
    private val _navigationState = MutableStateFlow(SpotifyEngineNavigationState())
    val navigationState: StateFlow<SpotifyEngineNavigationState> = _navigationState.asStateFlow()

    private val _securityState = MutableStateFlow(SpotifyEngineSecurityState())
    val securityState: StateFlow<SpotifyEngineSecurityState> = _securityState.asStateFlow()

    private val _shieldState = MutableStateFlow(SpotifyEngineShieldState())
    val shieldState: StateFlow<SpotifyEngineShieldState> = _shieldState.asStateFlow()

    private val _processState = MutableStateFlow(SpotifyEngineProcessState())
    val processState: StateFlow<SpotifyEngineProcessState> = _processState.asStateFlow()

    private val _contentMode = MutableStateFlow(
        if (initialUaProfile == LoginUaProfile.DESKTOP_CHROME) SpotifyWebContentMode.DESKTOP else SpotifyWebContentMode.MOBILE,
    )
    val contentMode: StateFlow<SpotifyWebContentMode> = _contentMode.asStateFlow()

    private val _uaProfile = MutableStateFlow(initialUaProfile)
    val uaProfile: StateFlow<LoginUaProfile> = _uaProfile.asStateFlow()

    private val _mediaCapabilityState = MutableStateFlow(
        SpotifyMediaCapabilityState(
            isDesktopMode = initialUaProfile == LoginUaProfile.DESKTOP_CHROME,
            isPrivateMode = isPrivate,
        ),
    )
    val mediaCapabilityState: StateFlow<SpotifyMediaCapabilityState> = _mediaCapabilityState.asStateFlow()

    @Volatile
    private var primaryWebView: WebView? = null

    @Volatile
    private var popupWebView: WebView? = null

    @Volatile
    private var sessionWipedThisRun = false

    @Volatile
    private var sessionReported = false

    var onScrollVisibilityListener: ((Boolean) -> Unit)? = null

    private val authSafeHosts = listOf(
        "accounts.spotify.com",
        "challenge.spotify.com",
        "recaptcha.net",
        "www.google.com/recaptcha",
        "www.gstatic.com",
        "accounts.google.com",
        "appleid.apple.com",
        "apis.google.com",
        "facebook.com",
        "fbcdn.net",
    )

    private val blockedTrackerKeywords = listOf(
        "doubleclick.net",
        "googlesyndication.com",
        "analytics.twitter.com",
        "connect.facebook.net",
        "scorecardresearch.com",
        "adnxs.com",
        "criteo.com",
        "hotjar.com",
        "googleads.g.doubleclick.net",
        "pagead2.googlesyndication.com",
        "adservice.google.com",
        "s0.2mdn.net",
        "segment.io",
        "branch.io",
        "app.adjust.com",
        "appsflyer.com",
    )

    private val spotifyAdKeywords = listOf(
        "adeventtracker.spotify.com",
        "adstudio.spotify.com",
        "ads.spotify.com",
        "gew1-spclient.spotify.com/ad-logic",
        "spclient.wg.spotify.com/ad-logic",
        "spclient.wg.spotify.com/ads",
        "spclient.wg.spotify.com/v1/ads",
        "/ad-logic/",
        "/v1/ads",
        "/desktop-interstitial/",
    )

    private val vastIndicators = listOf(
        "vast.xml",
        "vmap.xml",
        "/vast/",
        "/vmap/",
        "pubads.g.doubleclick.net/gampad",
        "imasdk.googleapis.com",
    )

    // Cosmetic annoyance CSS only injected on open.spotify.com (never on accounts.spotify.com login/challenge pages!)
    private val openPlayerAnnoyanceCssJs = """
        (function() {
          try {
            if (!document.getElementById('spotui-shield-css')) {
              var s = document.createElement('style');
              s.id = 'spotui-shield-css';
              s.textContent = '[data-testid="upgrade-button"],[data-testid="ad-slot"],[data-testid="upsell-banner"],.onetrust-pc-dark-filter,#onetrust-consent-sdk,.ot-sdk-container{display:none !important;}';
              (document.head || document.documentElement).appendChild(s);
            }
          } catch (e) {}
        })();
    """.trimIndent()

    @SuppressLint("SetJavaScriptEnabled")
    fun createView(
        context: Context,
        startUrl: String = Spotify.LOGIN_URL,
        clearPreviousSession: Boolean = false,
    ): WebView {
        val wv = WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )

            // 60/120Hz hardware compositing & crisp scroll telemetry
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            isVerticalScrollBarEnabled = true
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS

            setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                val dy = scrollY - oldScrollY
                if (dy > 16) onScrollVisibilityListener?.invoke(false)
                else if (dy < -16 || scrollY <= 4) onScrollVisibilityListener?.invoke(true)
            }

            stripRequestedWithHeader(this)
            configureHardenedSettings(context, settings, _uaProfile.value, isPrivate)

            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            cm.setAcceptThirdPartyCookies(this, true)

            webViewClient = buildHardenedWebViewClient(context, isPopup = false)
            webChromeClient = buildHardenedWebChromeClient(context, isPopup = false)
        }

        primaryWebView = wv

        if (clearPreviousSession && !sessionWipedThisRun) {
            sessionWipedThisRun = true
            resetSessionAndLoad(wv, startUrl)
        } else {
            wv.loadUrl(startUrl)
        }
        return wv
    }

    private fun stripRequestedWithHeader(webView: WebView) {
        val supported = runCatching {
            WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)
        }.getOrDefault(false)
        if (supported) {
            val ok = runCatching {
                @Suppress("DEPRECATION")
                WebSettingsCompat.setRequestedWithHeaderOriginAllowList(webView.settings, emptySet())
                true
            }.getOrDefault(false)
            _securityState.value = _securityState.value.copy(requestedWithStripped = ok)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureHardenedSettings(
        context: Context,
        settings: WebSettings,
        profile: LoginUaProfile,
        privateMode: Boolean,
    ) {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = !privateMode
            @Suppress("DEPRECATION")
            databaseEnabled = !privateMode
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            mediaPlaybackRequiresUserGesture = false

            // Strict sandbox: block arbitrary filesystem / content URI reads
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false

            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = if (privateMode) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT

            // Only override userAgentString when leaving SYSTEM_DEFAULT so Client Hints match naturally
            if (profile != LoginUaProfile.SYSTEM_DEFAULT) {
                userAgentString = resolveLoginUserAgent(context, profile)
            }
        }
    }

    private fun buildHardenedWebViewClient(context: Context, isPopup: Boolean): WebViewClient {
        return object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?,
            ): WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                if (!_shieldState.value.enabled) return super.shouldInterceptRequest(view, request)
                val lower = url.lowercase(Locale.US)

                // Never interfere with login/SSO/reCAPTCHA/challenge requests
                if (authSafeHosts.any { lower.contains(it) }) {
                    return super.shouldInterceptRequest(view, request)
                }

                if (spotifyAdKeywords.any { lower.contains(it) }) {
                    recordBlockedRequest(url, kind = BlockKind.SPOTIFY_AD)
                    return WebResourceResponse(
                        "application/json",
                        "UTF-8",
                        200,
                        "OK",
                        mapOf("Access-Control-Allow-Origin" to "*"),
                        ByteArrayInputStream("{}".toByteArray()),
                    )
                }

                if (vastIndicators.any { lower.contains(it) }) {
                    recordBlockedRequest(url, kind = BlockKind.VAST)
                    return WebResourceResponse(
                        "application/xml",
                        "UTF-8",
                        200,
                        "OK",
                        emptyMap(),
                        ByteArrayInputStream("<VAST version=\"3.0\"/>".toByteArray()),
                    )
                }

                if (blockedTrackerKeywords.any { lower.contains(it) }) {
                    recordBlockedRequest(url, kind = BlockKind.TRACKER)
                    return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                }

                return super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                val scheme = request.url?.scheme?.lowercase(Locale.US).orEmpty()
                if (scheme != "https" && scheme != "http") {
                    DevConsoleManager.logWarning("SpotifyWebView", "Blocked non-HTTP scheme redirect: $scheme")
                    return true
                }
                captureCookiesFromManager(view, url, source = if (isPopup) "WebViewPopup" else "WebViewRedirect")
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                val current = url.orEmpty()
                _navigationState.value = _navigationState.value.copy(
                    currentUrl = current,
                    isLoading = true,
                    progress = 12,
                    canGoBack = view?.canGoBack() == true,
                    canGoForward = view?.canGoForward() == true,
                )
                _securityState.value = _securityState.value.copy(
                    isHttps = current.startsWith("https://", ignoreCase = true),
                    certificateErrorHost = null,
                )
                _processState.value = _processState.value.copy(
                    lastFailureMessage = null,
                    autoRecoverHint = null,
                )
                if (current.contains("accounts.spotify.com", ignoreCase = true) ||
                    current.contains("open.spotify.com", ignoreCase = true)
                ) {
                    DevConsoleManager.logAuth("Loading ${redactUrlQuery(current)} [UA=${_uaProfile.value.label}]")
                }
                captureCookiesFromManager(view, current, source = if (isPopup) "WebViewPopupStart" else "WebViewStart")
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                captureCookiesFromManager(view, url, source = "WebHistory")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val current = url.orEmpty()
                _navigationState.value = _navigationState.value.copy(
                    currentUrl = current,
                    title = view?.title?.takeIf { it.isNotBlank() } ?: _navigationState.value.title,
                    isLoading = false,
                    progress = 100,
                    canGoBack = view?.canGoBack() == true,
                    canGoForward = view?.canGoForward() == true,
                )
                runCatching { CookieManager.getInstance().flush() }

                if (current.contains("open.spotify.com", ignoreCase = true)) {
                    runCatching { view?.evaluateJavascript(openPlayerAnnoyanceCssJs, null) }
                }

                inspectPageForAuthOrRejection(view, current)
                captureCookiesFromManager(view, current, source = if (isPopup) "WebViewPopupFinished" else "WebPageFinished")
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?,
            ) {
                handler?.cancel()
                val badHost = runCatching { Uri.parse(error?.url.orEmpty()).host }.getOrNull() ?: "unknown"
                _securityState.value = _securityState.value.copy(
                    isHttps = false,
                    certificateErrorHost = badHost,
                    sslCancelledCount = _securityState.value.sslCancelledCount + 1,
                )
                _processState.value = _processState.value.copy(
                    lastFailureMessage = "TLS certificate validation failed for $badHost (blocked safely).",
                    autoRecoverHint = "Check device date/time or network, then tap Reset & Retry.",
                )
                DevConsoleManager.logError(
                    "SpotifyWebTLS",
                    "Strict TLS cancelled invalid certificate for $badHost (primaryError=${error?.primaryError})",
                )
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?,
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                val status = errorResponse?.statusCode ?: return
                val reqUrl = request?.url?.toString().orEmpty()
                if (status >= 400 && reqUrl.contains("spotify.com", ignoreCase = true)) {
                    val hostPath = runCatching {
                        val u = Uri.parse(reqUrl)
                        "${u.host}${u.path}"
                    }.getOrDefault(reqUrl)
                    DevConsoleManager.logWarning(
                        "SpotifyWebHTTP",
                        "HTTP $status on $hostPath (mainFrame=${request?.isForMainFrame == true}, UA=${_uaProfile.value.label})",
                    )
                    if (status == 400 || status == 403) {
                        val hint = "Spotify returned HTTP $status on $hostPath. Tap Auto-Recover (resets cookies & toggles UA) or switch to Backup Cookie."
                        _processState.value = _processState.value.copy(
                            lastFailureMessage = "HTTP $status from Spotify ($hostPath)",
                            autoRecoverHint = hint,
                        )
                    }
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    val desc = error?.description?.toString().orEmpty()
                    _processState.value = _processState.value.copy(
                        lastFailureMessage = desc.ifBlank { "Page load error" },
                        autoRecoverHint = "Check internet / DNS or tap Reload.",
                    )
                }
            }

            @RequiresApi(Build.VERSION_CODES.O)
            override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?,
            ): Boolean {
                val crashes = _processState.value.crashCount + 1
                DevConsoleManager.logWarning(
                    "SpotifyWebRenderer",
                    "Renderer exited (didCrash=${detail?.didCrash()}, count=$crashes) — recovering",
                )
                _processState.value = _processState.value.copy(
                    isAlive = false,
                    crashCount = crashes,
                    recoveredFromCrash = true,
                    lastFailureMessage = "Web renderer restarted cleanly",
                )
                val resumeUrl = _navigationState.value.currentUrl.ifBlank { Spotify.LOGIN_URL }
                (view?.parent as? ViewGroup)?.removeView(view)
                runCatching { view?.destroy() }
                if (view === popupWebView) {
                    popupWebView = null
                    onPopupRequested(null)
                } else if (view === primaryWebView && crashes <= 3) {
                    val fresh = createView(context, startUrl = resumeUrl, clearPreviousSession = false)
                    primaryWebView = fresh
                    _processState.value = _processState.value.copy(isAlive = true)
                }
                return true
            }
        }
    }

    private fun buildHardenedWebChromeClient(context: Context, isPopup: Boolean): WebChromeClient {
        return object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (!isPopup) {
                    _navigationState.value = _navigationState.value.copy(
                        progress = newProgress.coerceIn(0, 100),
                        isLoading = newProgress in 1..99,
                    )
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                if (!isPopup && !title.isNullOrBlank()) {
                    _navigationState.value = _navigationState.value.copy(title = title)
                }
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                val req = request ?: return
                val drm = PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID
                if (req.resources.contains(drm)) {
                    req.grant(arrayOf(drm))
                    _mediaCapabilityState.value = _mediaCapabilityState.value.copy(
                        widevineStatus = SpotifyMediaCapabilityStatus.SUPPORTED,
                        encryptedMediaStatus = SpotifyMediaCapabilityStatus.SUPPORTED,
                    )
                } else {
                    req.deny()
                }
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                val msg = consoleMessage?.message().orEmpty()
                if (msg.contains("requestStorageAccess", ignoreCase = true) ||
                    msg.contains("Something went wrong", ignoreCase = true)
                ) {
                    DevConsoleManager.logWarning("SpotifyWebJS", msg)
                    if (msg.contains("requestStorageAccess", ignoreCase = true)) {
                        _processState.value = _processState.value.copy(
                            autoRecoverHint = "WebView storage access blocked. Tap Auto-Recover to reset cookies or switch UA.",
                        )
                    }
                }
                return true
            }

            @SuppressLint("SetJavaScriptEnabled")
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?,
            ): Boolean {
                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                DevConsoleManager.logAuth("Opening sandboxed SSO popup window")
                val popup = WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    stripRequestedWithHeader(this)
                    configureHardenedSettings(context, settings, _uaProfile.value, isPrivate)
                    val cm = CookieManager.getInstance()
                    cm.setAcceptCookie(true)
                    cm.setAcceptThirdPartyCookies(this, true)
                    webViewClient = buildHardenedWebViewClient(context, isPopup = true)
                    webChromeClient = buildHardenedWebChromeClient(context, isPopup = true)
                }
                closePopup()
                popupWebView = popup
                onPopupRequested(popup)
                transport.webView = popup
                resultMsg.sendToTarget()
                return true
            }

            override fun onCloseWindow(window: WebView?) {
                super.onCloseWindow(window)
                if (window === popupWebView || isPopup) {
                    closePopup()
                    runCatching { CookieManager.getInstance().flush() }
                    captureCookiesFromManager(primaryWebView, Spotify.LOGIN_URL, source = "SSOPopupClosed")
                }
            }
        }
    }

    private fun inspectPageForAuthOrRejection(view: WebView?, currentUrl: String) {
        if (view == null) return
        val host = runCatching { Uri.parse(currentUrl).host.orEmpty() }.getOrDefault("")
        if (host.contains("open.spotify.com", ignoreCase = true)) {
            view.evaluateJavascript(
                "(function(){var el=document.getElementById('session');return el?el.textContent:'';})();",
            ) { rawJson ->
                val token = Spotify.parseWebViewSessionScript(rawJson.orEmpty())
                val sweep = sweepSpotifyCookies(currentUrl)
                val dc = sweep.spDc
                if (!dc.isNullOrBlank() && dc.length >= MIN_SP_DC_LENGTH) {
                    reportCapturedSession(dc, sweep.spKey, token, "WebViewSessionDOM")
                } else if (token != null && !token.isAnonymous && token.accessToken.isNotBlank()) {
                    runCatching { CookieManager.getInstance().flush() }
                    val secondSweep = sweepSpotifyCookies("https://open.spotify.com/")
                    if (!secondSweep.spDc.isNullOrBlank() && secondSweep.spDc.length >= MIN_SP_DC_LENGTH) {
                        reportCapturedSession(secondSweep.spDc, secondSweep.spKey, token, "WebViewSessionFlush")
                    }
                }
            }
        } else if (host.contains("accounts.spotify.com", ignoreCase = true)) {
            view.evaluateJavascript(
                "(function(){return (document.body&&document.body.innerText)?document.body.innerText.slice(0,800):'';})();",
            ) { snippet ->
                val text = snippet.orEmpty()
                if (text.contains("Oops! Something went wrong", ignoreCase = true) ||
                    text.contains("Something went wrong", ignoreCase = true)
                ) {
                    val hint = "Spotify rejected challenge state. Tap Auto-Recover to wipe stale cookies & retry cleanly."
                    _processState.value = _processState.value.copy(
                        lastFailureMessage = "Spotify displayed 'Oops! Something went wrong'",
                        autoRecoverHint = hint,
                    )
                    DevConsoleManager.reportComponentFault(
                        component = "SpotifyLogin",
                        action = "WebPageAuthReject",
                        reason = "Spotify WebView login reject [UA=${_uaProfile.value.label}]",
                        autoFixHint = hint,
                    )
                }
            }
        }
    }

    private fun reportCapturedSession(
        spDc: String,
        spKey: String,
        token: SpotifyInternalToken?,
        source: String,
    ) {
        if (sessionReported) return
        sessionReported = true
        onSessionCaptured(spDc, spKey, token, source)
    }

    fun allowRecapture() {
        sessionReported = false
    }

    private fun captureCookiesFromManager(view: WebView?, url: String?, source: String) {
        val sweep = sweepSpotifyCookies(url)
        val dc = sweep.spDc
        if (dc.isNullOrBlank() || dc.length < MIN_SP_DC_LENGTH) return
        if (view != null && url?.contains("open.spotify.com", ignoreCase = true) == true) {
            view.evaluateJavascript(
                "(function(){var el=document.getElementById('session');return el?el.textContent:'';})();",
            ) { rawJson ->
                val token = Spotify.parseWebViewSessionScript(rawJson.orEmpty())
                reportCapturedSession(dc, sweep.spKey, token, source)
            }
        } else {
            reportCapturedSession(dc, sweep.spKey, null, source)
        }
    }

    fun tryCaptureSessionNow(source: String = "ManualWebSync"): Boolean {
        val url = _navigationState.value.currentUrl
        val sweep = sweepSpotifyCookies(url)
        val dc = sweep.spDc
        if (!dc.isNullOrBlank() && dc.length >= MIN_SP_DC_LENGTH) {
            sessionReported = false
            captureCookiesFromManager(primaryWebView, url, source)
            return true
        }
        return false
    }

    fun reload() {
        _processState.value = _processState.value.copy(lastFailureMessage = null, autoRecoverHint = null)
        primaryWebView?.reload()
    }

    fun goBack(): Boolean {
        val wv = primaryWebView ?: return false
        return if (wv.canGoBack()) {
            wv.goBack()
            true
        } else {
            false
        }
    }

    fun goForward(): Boolean {
        val wv = primaryWebView ?: return false
        return if (wv.canGoForward()) {
            wv.goForward()
            true
        } else {
            false
        }
    }

    fun switchUaProfile(context: Context, nextProfile: LoginUaProfile, reloadTarget: String? = null) {
        _uaProfile.value = nextProfile
        val desktop = nextProfile == LoginUaProfile.DESKTOP_CHROME
        _contentMode.value = if (desktop) SpotifyWebContentMode.DESKTOP else SpotifyWebContentMode.MOBILE
        _navigationState.value = _navigationState.value.copy(isDesktopMode = desktop)
        _mediaCapabilityState.value = _mediaCapabilityState.value.copy(isDesktopMode = desktop)
        _processState.value = _processState.value.copy(lastFailureMessage = null, autoRecoverHint = null)

        val wv = primaryWebView ?: return
        wv.settings.userAgentString = if (nextProfile == LoginUaProfile.SYSTEM_DEFAULT) {
            WebSettings.getDefaultUserAgent(context)
        } else {
            resolveLoginUserAgent(context, nextProfile)
        }
        DevConsoleManager.logAuth("Switched WebView UA -> ${nextProfile.label}")
        wv.loadUrl(reloadTarget ?: _navigationState.value.currentUrl.ifBlank { Spotify.LOGIN_URL })
    }

    fun autoRecover(context: Context) {
        val nextProfile = when (_uaProfile.value) {
            LoginUaProfile.SYSTEM_DEFAULT -> LoginUaProfile.MOBILE_CHROME
            LoginUaProfile.MOBILE_CHROME -> LoginUaProfile.DESKTOP_CHROME
            LoginUaProfile.DESKTOP_CHROME -> LoginUaProfile.SYSTEM_DEFAULT
        }
        sessionReported = false
        val wv = primaryWebView
        resetSpotifyWebSession(wv) {
            switchUaProfile(context, nextProfile, Spotify.LOGIN_URL)
        }
    }

    fun resetSessionAndLoad(wv: WebView? = primaryWebView, targetUrl: String = Spotify.LOGIN_URL) {
        sessionReported = false
        _processState.value = _processState.value.copy(lastFailureMessage = null, autoRecoverHint = null)
        resetSpotifyWebSession(wv) {
            wv?.loadUrl(targetUrl)
        }
    }

    fun closePopup() {
        val popup = popupWebView
        popupWebView = null
        onPopupRequested(null)
        popup?.post { runCatching { popup.stopLoading(); popup.destroy() } }
    }

    fun destroy() {
        closePopup()
        primaryWebView?.let { wv ->
            runCatching {
                wv.stopLoading()
                wv.destroy()
            }
        }
        primaryWebView = null
    }

    private enum class BlockKind {
        TRACKER,
        SPOTIFY_AD,
        VAST,
    }

    private fun recordBlockedRequest(url: String, kind: BlockKind) {
        val cur = _shieldState.value
        val host = runCatching { Uri.parse(url).host }.getOrNull() ?: url
        _shieldState.value = cur.copy(
            totalBlocked = cur.totalBlocked + 1,
            trackersBlocked = if (kind == BlockKind.TRACKER) cur.trackersBlocked + 1 else cur.trackersBlocked,
            spotifyAdsBlocked = if (kind == BlockKind.SPOTIFY_AD) cur.spotifyAdsBlocked + 1 else cur.spotifyAdsBlocked,
            vastBlocked = if (kind == BlockKind.VAST) cur.vastBlocked + 1 else cur.vastBlocked,
            lastBlockedUrl = host,
        )
    }

    private fun redactUrlQuery(rawUrl: String): String {
        val idx = rawUrl.indexOf('?')
        return if (idx > 0) rawUrl.substring(0, idx) else rawUrl
    }
}
