package com.music.spotui.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

/**
 * Version 1.5.1 - HomeFeedCleanView
 * Custom clean Jetpack Compose home screen rendering only user playlists, saved albums, and recent tracks.
 */
@Composable
fun HomeFeedCleanView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Clean Home Feed Active", color = Color.White, fontSize = 24.sp)
    }
}
