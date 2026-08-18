package com.music.spotui.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * Version 1.5.1 - TrackBlacklistDialog
 * Long-press context menu dialog allowing users to add an artist or track to the instant-skip TrashBin.
 */
@Composable
fun TrackBlacklistDialog(
    trackName: String,
    artistName: String,
    onBlacklistTrack: () -> Unit,
    onBlacklistArtist: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(Color(0xFF181820), RoundedCornerShape(12.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Add to TrashBin", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Instantly auto-skip this content in the future.", color = Color.Gray, fontSize = 14.sp)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = { onBlacklistTrack(); onDismiss() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE22134)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Blacklist Track: \$trackName")
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Button(
                onClick = { onBlacklistArtist(); onDismiss() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE22134)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Blacklist Artist: \$artistName")
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }
        }
    }
}
