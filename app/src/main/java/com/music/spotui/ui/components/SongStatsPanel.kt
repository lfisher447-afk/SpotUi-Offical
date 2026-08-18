package com.music.spotui.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Version 1.5.1 - SongStatsPanel
 * One UI bottom-sheet overlay displaying raw track metrics: BPM, Musical Key, Valence, Energy, and Loudness (dB).
 */
@Composable
fun SongStatsPanel(bpm: Int, key: String, valence: Float, energy: Float, loudness: Float) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text("Track Acoustic Metrics", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("BPM", color = Color.Gray)
            Text(bpm.toString(), color = Color.White)
        }
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Key", color = Color.Gray)
            Text(key, color = Color.White)
        }
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Valence", color = Color.Gray)
            Text(String.format("%.2f", valence), color = Color.White)
        }
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Energy", color = Color.Gray)
            Text(String.format("%.2f", energy), color = Color.White)
        }
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Loudness", color = Color.Gray)
            Text(String.format("%.1f dB", loudness), color = Color.White)
        }
    }
}
