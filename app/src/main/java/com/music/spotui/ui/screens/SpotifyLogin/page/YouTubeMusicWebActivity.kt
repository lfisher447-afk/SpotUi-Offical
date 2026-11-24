package com.music.spotui.ui.screens.SpotifyLogin.page

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.metrolist.innertube.network.AuthSessionStore
import com.music.spotui.BuildConfig
import com.music.spotui.data.preferences.isAutoSyncYoutubePlaylistsEnabled
import com.music.spotui.data.preferences.setYoutubeCookie
import com.music.spotui.data.youtube.YouTubeMusicSync
import com.music.spotui.util.AppDiagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * YouTube Music account linker & playlist bridge.
 */
class YouTubeMusicWebActivity : Activity() {
    private var webView: WebView? = null
    private var statusText: TextView? = null
    private var syncButton: Button? = null
    private var autoSyncedOnce = false
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { CookieManager.getInstance().setAcceptCookie(true) }
            .onFailure { AppDiagnostics.warning("YouTubeWeb", "CookieManager setup failed", it) }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
        }

        val dp = resources.displayMetrics.density
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#181818"))
            setPadding((14 * dp).toInt(), (36 * dp).toInt(), (14 * dp).toInt(), (12 * dp).toInt())
        }

        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (10 * dp).toInt()
            }
        }
        val titleView = TextView(this).apply {
            text = "YouTube Music Sync"
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
        }
        val subtitleView = TextView(this).apply {
            text = if (YouTubeMusicSync.isSessionReady(this@YouTubeMusicWebActivity)) {
                "Account linked · Tap Sync All to import playlists natively"
            } else {
                "Sign in below to sync playlists natively into SpotUI"
            }
            setTextColor(Color.parseColor("#B3B3B3"))
            textSize = 12f
        }
        statusText = subtitleView
        labels.addView(titleView)
        labels.addView(subtitleView)

        val syncBtn = Button(this).apply {
            text = "Sync All"
            isAllCaps = false
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
            textSize = 13f
            setPadding((14 * dp).toInt(), (6 * dp).toInt(), (14 * dp).toInt(), (6 * dp).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1DB954"))
                cornerRadius = 24 * dp
            }
            setOnClickListener { triggerNativePlaylistSync(auto = false) }
        }
        syncButton = syncBtn

        header.addView(labels)
        header.addView(syncBtn)
        root.addView(
            header,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )

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
        root.addView(
            createdWebView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        setContentView(root)
    }

    private fun triggerNativePlaylistSync(auto: Boolean) {
        persistYoutubeCookies()
        if (!YouTubeMusicSync.isSessionReady(applicationContext)) {
            if (!auto) {
                Toast.makeText(
                    this,
                    "Sign in to your Google / YouTube account below first.",
                    Toast.LENGTH_LONG,
                ).show()
            }
            return
        }
        syncButton?.isEnabled = false
        syncButton?.text = "Syncing…"
        statusText?.text = "Importing YouTube Music playlists natively into SpotUI…"
        activityScope.launch {
            val res = YouTubeMusicSync.syncFromSignedInSession(applicationContext)
            syncButton?.isEnabled = true
            syncButton?.text = "Sync All"
            res.onSuccess { status ->
                statusText?.text = status.detail
                Toast.makeText(
                    this@YouTubeMusicWebActivity,
                    "Synced to SpotUI Library! Return to Library tab to play.",
                    Toast.LENGTH_LONG,
                ).show()
            }.onFailure { err ->
                statusText?.text = err.message ?: "Sync failed — tap Sync All to retry"
                if (!auto) {
                    Toast.makeText(
                        this@YouTubeMusicWebActivity,
                        err.message ?: "YouTube sync failed",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
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

            if (!autoSyncedOnce &&
                AuthSessionStore.current().hasLoginCookie &&
                isAutoSyncYoutubePlaylistsEnabled(applicationContext)
            ) {
                autoSyncedOnce = true
                triggerNativePlaylistSync(auto = true)
            }
        }.onFailure {
            AppDiagnostics.warning("YouTubeWeb", "Cookie persistence failed", it)
        }
    }

    override fun onPause() {
        persistYoutubeCookies()
        super.onPause()
    }

    override fun onDestroy() {
        activityScope.cancel()
        runCatching {
            webView?.stopLoading()
            webView?.destroy()
        }
        webView = null
        super.onDestroy()
    }
}
