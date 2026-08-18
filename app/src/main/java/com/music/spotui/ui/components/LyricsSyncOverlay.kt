package com.music.spotui.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.spotui.engine.LyricLine

/**
 * Version 1.5.1 - LyricsSyncOverlay
 * Floating Compose lyric visualizer displaying real-time highlighted text lines over current album artwork.
 */
@Composable
fun LyricsSyncOverlay(lyrics: List<LyricLine>, currentPlaybackMs: Long, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    
    val currentIndex = remember(currentPlaybackMs, lyrics) {
        lyrics.indexOfLast { it.timeMs <= currentPlaybackMs }.coerceAtLeast(0)
    }

    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0 && currentIndex < lyrics.size) {
            listState.animateScrollToItem(currentIndex, scrollOffset = -200)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 100.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(lyrics) { index, line ->
                val isActive = index == currentIndex
                Text(
                    text = line.text,
                    color = if (isActive) Color.White else Color.White.copy(alpha = 0.4f),
                    fontSize = if (isActive) 24.sp else 18.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}
