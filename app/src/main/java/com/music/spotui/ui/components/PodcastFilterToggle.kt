package com.music.spotui.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Version 1.5.1 - PodcastFilterToggle
 * Settings toggle allowing users to dynamically show or hide podcast content across search and feeds.
 */
@Composable
fun PodcastFilterToggle(isPodcastFilterEnabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Show Podcasts & Audiobooks", color = Color.White)
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = isPodcastFilterEnabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF1ED760),
                checkedTrackColor = Color(0xFF1ED760).copy(alpha = 0.5f)
            )
        )
    }
}
