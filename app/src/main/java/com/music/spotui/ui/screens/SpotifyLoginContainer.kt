package com.music.spotui.ui.screens

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SpotifyLogin {
    class page {
        class YouTubeMusicWebActivity : Activity()
        class DeezerLoginActivity : Activity()

        companion object {
            @Composable
            fun SpotifyLoginScreen(navController: NavController) {
                com.music.spotui.ui.screens.SpotifyLoginScreen(navController)
            }
        }
    }

    class module {
        companion object {
            fun installSpotifyTokenAutoRefresh(context: Context) {}
            suspend fun launchLoginTask(context: Context): Boolean = true
            suspend fun applyLearnedOnce() {}
            suspend fun liveHash(operationName: String, rejected: Set<String>): String? = null
            fun syncAuthState() {}
            fun notifyAuthStateChanged() {}
            val isAuthenticated: StateFlow<Boolean> = MutableStateFlow(true)
            val syncRevision: StateFlow<Long> = MutableStateFlow(1L)
        }
    }
}

fun launchLoginTask(block: suspend () -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
        block()
    }
}
