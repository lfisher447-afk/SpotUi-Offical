package com.music.spotui.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.spotui.ui.theme.AppPalette
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
    val summaryLine = remember(text) {
        text.lineSequence()
            .firstOrNull { it.startsWith("Exception Type:") || it.startsWith("Root Cause Type:") }
            ?.substringAfter(":")
            ?.trim()
            ?: "Unhandled crash dump captured automatically"
    }

    AlertDialog(
        onDismissRequest = {
            CrashHandler.clearAllCrashReports(context)
            report = null
        },
        containerColor = Color(0xFF141419),
        titleContentColor = Color.White,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BugReport, contentDescription = null, tint = Color(0xFFFF5252))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Crash Recovered Safely",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                }
                Text(
                    text = summaryLine,
                    color = Color(0xFFFFB74D),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
                    .background(Color(0xFF0C0C10), RoundedCornerShape(10.dp))
                    .padding(10.dp)
                    .verticalScroll(rememberScrollState()),
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
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(
                    onClick = {
                        runCatching {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "SpotUI Crash Report")
                                putExtra(Intent.EXTRA_TEXT, text.take(24_000))
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Crash Log"))
                        }
                    },
                    modifier = Modifier.testTag("crash_share_button"),
                ) {
                    Text("Share", color = Color(0xFF90CAF9), fontWeight = FontWeight.SemiBold)
                }
                TextButton(
                    onClick = {
                        runCatching {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("SpotUI Crash Report", text))
                            Toast.makeText(context, "Crash report copied", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("crash_copy_button"),
                ) {
                    Text("Copy Log", color = AppPalette, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    CrashHandler.clearAllCrashReports(context)
                    report = null
                },
                modifier = Modifier.testTag("crash_dismiss_button"),
            ) {
                Text("Dismiss", color = Color.White)
            }
        },
    )
}
