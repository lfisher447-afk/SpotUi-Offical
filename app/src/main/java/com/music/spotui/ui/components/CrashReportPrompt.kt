package com.music.spotui.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.spotui.util.CrashHandler

@Composable
fun CrashReportPrompt() {
    val context = LocalContext.current
    var report by remember {
        mutableStateOf(
            runCatching {
                if (CrashHandler.hasCrashReport(context)) {
                    CrashHandler.getLatestCrashReport(context)
                } else {
                    null
                }
            }.getOrNull()
        )
    }

    val text = report ?: return

    AlertDialog(
        onDismissRequest = {
            CrashHandler.clearAllCrashReports(context)
            report = null
        },
        containerColor = Color(0xFF141419),
        titleContentColor = Color.White,
        title = {
            Text(
                text = "Previous Crash Detected",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
                    .background(Color(0xFF0C0C10), RoundedCornerShape(8.dp))
                    .padding(10.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = text,
                    color = Color(0xFFE0E0E0),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    runCatching {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("SpotUI Crash Report", text))
                        Toast.makeText(context, "Crash report copied", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Text("Copy Log", color = Color(0xFF1DB954), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    CrashHandler.clearAllCrashReports(context)
                    report = null
                }
            ) {
                Text("Dismiss", color = Color.White)
            }
        },
    )
}
