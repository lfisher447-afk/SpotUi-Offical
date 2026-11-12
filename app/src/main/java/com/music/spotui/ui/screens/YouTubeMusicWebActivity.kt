package com.music.spotui.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.metrolist.innertube.network.AuthSessionStore
import com.music.spotui.BuildConfig
import com.music.spotui.data.preferences.setYoutubeCookie
import com.music.spotui.util.AppDiagnostics

/**
 * Optional user-authorized YouTube Music companion.
 *
 * The activity loads the official music.youtube.com website inside an isolated user-facing
 * WebView. Users complete their own sign-in there and can browse/play their YouTube Music
 * playlists. Cookie capture is limited to the YouTube domain and is used only by the existing
 * resolver for user-authorized, login-required playback attempts; no password or OAuth token is
 * collected by this screen.
 */
class YouTubeMusicWebActivity : Activity() {
    private var webView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { CookieManager.getInstance().setAcceptCookie(true) }
            .onFailure { AppDiagnostics.warning("YouTubeWeb", "CookieManager setup failed", it) }

        val createdWebView = runCatching {
            WebView(this).apply {
                setBackgroundColor(Color.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = true
                settings.userAgentString = "${settings.userAgentString} Spotui/${BuildConfig.VERSION_NAME}"
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false
                    override fun onPageFinished(view: WebView, url: String) {
                        persistYoutubeCookies()
                    }
                }
                loadUrl("https://music.youtube.com/library/playlists")
            }
        }.onFailure {
            AppDiagnostics.error("YouTubeWeb", "WebView creation failed", it)
            finish()
        }.getOrNull() ?: return

        webView = createdWebView
        setContentView(
            createdWebView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val wv = webView
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            finish()
        }
    }

    private fun persistYoutubeCookies() {
        runCatching {
            val cookieManager = CookieManager.getInstance()
            val ytCookie = cookieManager.getCookie("https://www.youtube.com")
            val ytmCookie = cookieManager.getCookie("https://music.youtube.com")
            val cookie = listOfNotNull(ytCookie, ytmCookie)
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString("; ")
                .ifBlank { return@runCatching }
            setYoutubeCookie(applicationContext, cookie)
            AuthSessionStore.updateFromWebViewCookies(cookie)
            cookieManager.flush()
        }.onFailure {
            AppDiagnostics.warning("YouTubeWeb", "Cookie persistence failed", it)
        }
    }

    override fun onPause() {
        persistYoutubeCookies()
        super.onPause()
    }

    override fun onDestroy() {
        runCatching {
            webView?.stopLoading()
            webView?.destroy()
        }
        webView = null
        super.onDestroy()
    }
}
