package com.music.spotui.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.AppPalette
import com.music.spotui.util.CrashHandler
import com.music.spotui.util.DevConsoleManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperConsoleScreen(navController: NavController) {
    val context = LocalContext.current
    val logs by DevConsoleManager.logs.collectAsState()
    val diagnostics by DevConsoleManager.diagnostics.collectAsState()

    var forceFlac by remember { mutableStateOf(DevConsoleManager.forceLosslessFlac.value) }
    var forceYt by remember { mutableStateOf(DevConsoleManager.forceYoutubeEngine.value) }
    var strictSanitize by remember { mutableStateOf(DevConsoleManager.strictSanitization.value) }
    var oneUiOpt by remember { mutableStateOf(DevConsoleManager.oneUiOptimizations.value) }
    var highPerfBuf by remember { mutableStateOf(DevConsoleManager.highPerformanceBuffer.value) }
    var watchdogOn by remember { mutableStateOf(DevConsoleManager.watchdogEnabled.value) }

    var selectedFilter by remember { mutableStateOf<DevConsoleManager.LogLevel?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedLogForDialog by remember { mutableStateOf<DevConsoleManager.LogEntry?>(null) }
    var showCrashDialog by remember { mutableStateOf(false) }
    var latestCrashText by remember { mutableStateOf<String?>(null) }

    val hasCrashReports = remember { CrashHandler.hasCrashReport(context) }

    val filteredLogs = remember(logs, selectedFilter, searchQuery) {
        logs.filter { entry ->
            (selectedFilter == null || entry.level == selectedFilter) &&
            (searchQuery.isBlank() || entry.tag.contains(searchQuery, ignoreCase = true) || entry.message.contains(searchQuery, ignoreCase = true))
        }.reversed()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Developer Console & Telemetry",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { DevConsoleManager.clearLogs() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear Logs", tint = Color.Red)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF17171F))
            )
        },
        containerColor = AppBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Diagnostics Summary Dashboard
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF22222B)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "REAL-TIME DIAGNOSTICS & WATCHDOG",
                        color = Color(0xFF81C784),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DiagnosticBadge("Engine", diagnostics.activeSource)
                        DiagnosticBadge("Quality", diagnostics.currentQuality.ifEmpty { "Adaptive" })
                        DiagnosticBadge("Collisions", "${diagnostics.totalCollisionsDetected}")
                        DiagnosticBadge("Sanitized", "${diagnostics.sanitizationCount}")
                    }

                    if (hasCrashReports) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                latestCrashText = CrashHandler.getLatestCrashReport(context)
                                showCrashDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(38.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("VIEW LATEST CRASH LOG DUMP", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Developer Toggles Section
            Text(
                "DEVELOPER CONFIGURATIONS",
                color = Color(0xFFB3B3B3),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF22222B), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                DevToggleRow("Force Lossless FLAC", forceFlac) {
                    forceFlac = it
                    DevConsoleManager.forceLosslessFlac.value = it
                }
                DevToggleRow("Force YouTube Fallback Engine", forceYt) {
                    forceYt = it
                    DevConsoleManager.forceYoutubeEngine.value = it
                }
                DevToggleRow("Strict Stream Sanitization", strictSanitize) {
                    strictSanitize = it
                    DevConsoleManager.strictSanitization.value = it
                }
                DevToggleRow("OneUI & Universal Background Optimizations", oneUiOpt) {
                    oneUiOpt = it
                    DevConsoleManager.oneUiOptimizations.value = it
                }
                DevToggleRow("High-Performance Audio Buffering", highPerfBuf) {
                    highPerfBuf = it
                    DevConsoleManager.highPerformanceBuffer.value = it
                }
                DevToggleRow("Video & Playback Watchdog", watchdogOn) {
                    watchdogOn = it
                    DevConsoleManager.watchdogEnabled.value = it
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Log Console Section Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "LOG CAT & STREAM WATCHDOG (${filteredLogs.size})",
                    color = Color(0xFFB3B3B3),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search Bar & Filter Chips
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter logs...", color = Color.Gray, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF81C784),
                    unfocusedBorderColor = Color.DarkGray,
                    focusedContainerColor = Color(0xFF22222B),
                    unfocusedContainerColor = Color(0xFF22222B),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Log Filter Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == null,
                    onClick = { selectedFilter = null },
                    label = { Text("ALL", fontSize = 10.sp) }
                )
                FilterChip(
                    selected = selectedFilter == DevConsoleManager.LogLevel.ERROR,
                    onClick = { selectedFilter = DevConsoleManager.LogLevel.ERROR },
                    label = { Text("ERROR", fontSize = 10.sp) }
                )
                FilterChip(
                    selected = selectedFilter == DevConsoleManager.LogLevel.COLLISION,
                    onClick = { selectedFilter = DevConsoleManager.LogLevel.COLLISION },
                    label = { Text("COLLISION", fontSize = 10.sp) }
                )
                FilterChip(
                    selected = selectedFilter == DevConsoleManager.LogLevel.SANITIZATION,
                    onClick = { selectedFilter = DevConsoleManager.LogLevel.SANITIZATION },
                    label = { Text("SANITIZER", fontSize = 10.sp) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Log List Display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black, RoundedCornerShape(8.dp))
                    .border(1.dp, Color.DarkGray, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                if (filteredLogs.isEmpty()) {
                    Text(
                        "No log entries recorded.",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filteredLogs) { log ->
                            LogItemRow(log) {
                                selectedLogForDialog = log
                            }
                        }
                    }
                }
            }
        }
    }

    // Detail Modal for Log / Stack Trace
    selectedLogForDialog?.let { log ->
        AlertDialog(
            onDismissRequest = { selectedLogForDialog = null },
            title = { Text("[${log.level}] ${log.tag}", color = Color.White, fontSize = 16.sp) },
            text = {
                Column {
                    Text("Time: ${log.timestamp}", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(log.message, color = Color.White, fontSize = 13.sp)
                    log.exceptionDetails?.let { stack ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            stack,
                            color = Color(0xFFFF6B6B),
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 11.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("LogEntry", "${log.timestamp} [${log.level}] ${log.tag}: ${log.message}\n${log.exceptionDetails ?: ""}")
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Copy", color = Color(0xFF81C784))
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedLogForDialog = null }) {
                    Text("Close", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF17171F)
        )
    }

    // Modal for Full Crash Log Dump
    if (showCrashDialog) {
        val crashReport = latestCrashText ?: "No crash dump available."
        AlertDialog(
            onDismissRequest = { showCrashDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BugReport, contentDescription = null, tint = Color(0xFFFF5252))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Latest Crash Report", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .background(Color.Black, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            Text(
                                text = crashReport,
                                color = Color(0xFFFFCDD2),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("SpotUICrashDump", crashReport)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Crash dump copied to clipboard", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Copy Full Dump", color = Color(0xFF81C784))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        CrashHandler.clearAllCrashReports(context)
                        showCrashDialog = false
                        Toast.makeText(context, "Crash logs cleared", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Delete Log", color = Color(0xFFFF5252))
                    }
                    TextButton(onClick = { showCrashDialog = false }) {
                        Text("Close", color = Color.Gray)
                    }
                }
            },
            containerColor = Color(0xFF17171F)
        )
    }
}

@Composable
fun DiagnosticBadge(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color(0xFFB3B3B3), fontSize = 10.sp)
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun DevToggleRow(label: String, state: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 13.sp)
        Switch(
            checked = state,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Color(0xFF81C784),
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color.DarkGray
            )
        )
    }
}

@Composable
fun LogItemRow(log: DevConsoleManager.LogEntry, onClick: () -> Unit) {
    val levelColor = when (log.level) {
        DevConsoleManager.LogLevel.ERROR -> Color(0xFFFF4D4D)
        DevConsoleManager.LogLevel.WARNING -> Color(0xFFFFC107)
        DevConsoleManager.LogLevel.COLLISION -> Color(0xFFFF9800)
        DevConsoleManager.LogLevel.SANITIZATION -> Color(0xFF00E676)
        DevConsoleManager.LogLevel.WATCHDOG -> Color(0xFF29B6F6)
        else -> Color.LightGray
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            log.timestamp,
            color = Color.Gray,
            fontSize = 10.sp,
            fontFamily = FontFamily.SansSerif,
            modifier = Modifier.width(70.dp)
        )
        Text(
            "[${log.level.name.take(4)}]",
            color = levelColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(50.dp)
        )
        Text(
            "${log.tag}: ${log.message}",
            color = Color.White,
            fontSize = 11.sp,
            fontFamily = FontFamily.SansSerif,
            maxLines = 2
        )
    }
}
