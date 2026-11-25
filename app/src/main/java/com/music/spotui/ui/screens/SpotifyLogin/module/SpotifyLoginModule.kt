package com.music.spotui.ui.screens.SpotifyLogin.module

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object SpotifyLoginModule {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun installSpotifyTokenAutoRefresh(context: Context) {
        scope.launch {
            // Auto-refresh hook installation
        }
    }

    fun launchLoginTask(block: suspend CoroutineScope.() -> Unit) {
        scope.launch(block = block)
    }
}

fun installSpotifyTokenAutoRefresh(context: Context) {
    SpotifyLoginModule.installSpotifyTokenAutoRefresh(context)
}

fun launchLoginTask(block: suspend CoroutineScope.() -> Unit) {
    SpotifyLoginModule.launchLoginTask(block)
}
