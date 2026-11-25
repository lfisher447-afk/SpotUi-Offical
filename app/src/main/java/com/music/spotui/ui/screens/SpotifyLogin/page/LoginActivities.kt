package com.music.spotui.ui.screens.SpotifyLogin.page

import android.app.Activity
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient

class YouTubeMusicWebActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val webView = WebView(this)
        setContentView(webView)
        webView.loadUrl("https://music.youtube.com")
    }
}

class DeezerLoginActivity : Activity() {
    companion object {
        fun extractArlFromCookieHeader(cookieHeader: String?): String? {
            if (cookieHeader.isNullOrBlank()) return null
            cookieHeader.split(";").forEach { part ->
                val kv = part.trim().split("=", limit = 2)
                if (kv.size == 2 && kv[0].trim().equals("arl", ignoreCase = true)) {
                    val valTrim = kv[1].trim()
                    if (valTrim.isNotBlank()) return valTrim
                }
            }
            return null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val webView = WebView(this)
        setContentView(webView)
        webView.loadUrl("https://www.deezer.com/login")
    }
}
