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
import com.music.spotui.data.preferences.setYoutubeCookie

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
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CookieManager.getInstance().setAcceptCookie(true)
        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true
            settings.userAgentString = settings.userAgentString + " Spotui/1.9.9"
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false
                override fun onPageFinished(view: WebView, url: String) {
                    persistYoutubeCookies()
                }
            }
            loadUrl("https://music.youtube.com/library/playlists")
        }
        setContentView(webView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else finish()
    }

    private fun persistYoutubeCookies() {
        val cookie = CookieManager.getInstance().getCookie("https://www.youtube.com")
            ?: CookieManager.getInstance().getCookie("https://music.youtube.com")
            ?: return
        setYoutubeCookie(applicationContext, cookie)
        CookieManager.getInstance().flush()
    }

    override fun onPause() {
        persistYoutubeCookies()
        super.onPause()
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }
}
