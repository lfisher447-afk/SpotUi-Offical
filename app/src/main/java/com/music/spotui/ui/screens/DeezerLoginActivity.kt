package com.music.spotui.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import com.music.spotui.BuildConfig
import com.music.spotui.data.preferences.setDeezerArl
import com.music.spotui.util.AppDiagnostics

/**
 * Isolated user-facing Deezer sign-in WebView.
 *
 * When a Deezer ARL cookie becomes present on deezer.com after the user completes
 * sign-in, it is persisted locally so the lossless provider router can authenticate
 * compatible community/custom mirrors without exposing passwords.
 */
class DeezerLoginActivity : Activity() {
    private var webView: WebView? = null
    private var captured = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { CookieManager.getInstance().setAcceptCookie(true) }
            .onFailure { AppDiagnostics.warning("DeezerWeb", "CookieManager setup failed", it) }

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        val banner = TextView(this).apply {
            text = "Sign in to Deezer — ARL session will link automatically"
            setTextColor(Color.WHITE)
            setBackgroundColor(0xFF1A1A22.toInt())
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
        }

        val createdWebView = runCatching {
            WebView(this).apply {
                setBackgroundColor(Color.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = "${settings.userAgentString} Spotui/${BuildConfig.VERSION_NAME}"
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false
                    override fun onPageFinished(view: WebView, url: String) {
                        checkAndPersistArl()
                    }
                }
                loadUrl("https://www.deezer.com/login")
            }
        }.onFailure {
            AppDiagnostics.error("DeezerWeb", "WebView creation failed", it)
            finish()
        }.getOrNull() ?: return

        webView = createdWebView
        root.addView(
            createdWebView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                topMargin = 88
            },
        )
        root.addView(
            banner,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        setContentView(root)
    }

    private fun checkAndPersistArl() {
        if (captured) return
        runCatching {
            val cookieManager = CookieManager.getInstance()
            val raw = listOfNotNull(
                cookieManager.getCookie("https://www.deezer.com"),
                cookieManager.getCookie("https://deezer.com"),
            ).joinToString("; ")
            val arl = extractArlFromCookieHeader(raw)
            if (!arl.isNullOrBlank()) {
                captured = true
                setDeezerArl(applicationContext, arl)
                cookieManager.flush()
                Toast.makeText(this, "Deezer ARL linked cleanly.", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
        }.onFailure {
            AppDiagnostics.warning("DeezerWeb", "ARL cookie capture failed", it)
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

    override fun onPause() {
        checkAndPersistArl()
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

    companion object {
        fun extractArlFromCookieHeader(cookieHeader: String): String? {
            if (cookieHeader.isBlank()) return null
            return cookieHeader
                .split(';')
                .asSequence()
                .map { it.trim() }
                .firstOrNull { it.startsWith("arl=", ignoreCase = true) }
                ?.substringAfter('=')
                ?.trim()
                ?.takeIf { it.length >= 24 }
        }
    }
}
