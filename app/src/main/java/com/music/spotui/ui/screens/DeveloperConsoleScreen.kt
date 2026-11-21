package com.music.spotui.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.navigation.NavController
import com.music.spotui.audio.BluetoothAudioInspector
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.AppPalette
import com.music.spotui.util.CheckSeverity
import com.music.spotui.util.CrashHandler
import com.music.spotui.util.DevConsoleManager
import com.music.spotui.util.HealthCheckResult
import com.music.spotui.util.HealthScanReport
import com.music.spotui.util.SystemHealthChecker
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.ScrollableTabRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperConsoleScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val logs by DevConsoleManager.logs.collectAsStateWithLifecycle()
    val diagnostics by DevConsoleManager.diagnostics.collectAsStateWithLifecycle()

    val faults by DevConsoleManager.faults.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }

    // Tab 1: 20-Point System Health Scanner State
    var healthReport by remember { mutableStateOf<HealthScanReport?>(null) }
    var isScanning by remember { mutableStateOf(false) }
    var fixingId by remember { mutableStateOf<String?>(null) }

    // Tab 4: Live Bluetooth Specs & Conflict Inspector State
    var btReport by remember { mutableStateOf<BluetoothAudioInspector.BluetoothSpecsReport?>(null) }
    var isInspectingBt by remember { mutableStateOf(false) }

    // Tab 2: Logger State
    var selectedFilter by remember { mutableStateOf<DevConsoleManager.LogLevel?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedLogForDialog by remember { mutableStateOf<DevConsoleManager.LogEntry?>(null) }

    // Tab 3: Telemetry & Developer Switches State
    var telemetry by remember { mutableStateOf(SongPlayer.telemetrySnapshot(context)) }
    var forceFlac by remember { mutableStateOf(DevConsoleManager.forceLosslessFlac.value) }
    var forceYt by remember { mutableStateOf(DevConsoleManager.forceYoutubeEngine.value) }
    var strictSanitize by remember { mutableStateOf(DevConsoleManager.strictSanitization.value) }
    var oneUiOpt by remember { mutableStateOf(DevConsoleManager.oneUiOptimizations.value) }
    var highPerfBuf by remember { mutableStateOf(DevConsoleManager.highPerformanceBuffer.value) }
    var watchdogOn by remember { mutableStateOf(DevConsoleManager.watchdogEnabled.value) }

    var showCrashDialog by remember { mutableStateOf(false) }
    var latestCrashText by remember { mutableStateOf<String?>(null) }
    var hasCrashReports by remember { mutableStateOf(CrashHandler.hasCrashReport(context)) }

    fun triggerScan(userInitiated: Boolean = false, forceRefresh: Boolean = false) {
        if (isScanning) return
        val remaining = if (forceRefresh) 0L else SystemHealthChecker.remainingCooldownMs()
        if (userInitiated && remaining > 0L) {
            val sec = ((remaining + 999L) / 1000L).coerceAtLeast(1L)
            Toast.makeText(context, "Diagnostics refreshed recently (${sec}s cooldown)", Toast.LENGTH_SHORT).show()
            return
        }
        isScanning = true
        scope.launch {
            healthReport = SystemHealthChecker.runFullScan(context, forceRefresh = forceRefresh)
            telemetry = SongPlayer.telemetrySnapshot(context)
            isScanning = false
        }
    }

    fun refreshBtSpecs(forceLog: Boolean = false) {
        if (isInspectingBt) return
        isInspectingBt = true
        scope.launch {
            btReport = BluetoothAudioInspector.inspect(context, forceLog = forceLog)
            isInspectingBt = false
        }
    }

    LaunchedEffect(Unit) {
        triggerScan()
        refreshBtSpecs()
    }

    // Periodic live telemetry refresh while on Telemetry or BT tab
    LaunchedEffect(selectedTab) {
        while (selectedTab == 2 || selectedTab == 3) {
            telemetry = SongPlayer.telemetrySnapshot(context)
            hasCrashReports = CrashHandler.hasCrashReport(context)
            if (selectedTab == 3) {
                btReport = BluetoothAudioInspector.inspect(context, forceLog = false)
            }
            delay(if (selectedTab == 3) 2_500L else 1_000L)
        }
    }

    val filteredLogs = remember(logs, selectedFilter, searchQuery) {
        logs.filter { entry ->
            (selectedFilter == null || entry.level == selectedFilter) &&
                (searchQuery.isBlank() ||
                    entry.tag.contains(searchQuery, ignoreCase = true) ||
                    entry.message.contains(searchQuery, ignoreCase = true))
        }.reversed()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Diagnostics, Health & Logger",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.testTag("dev_console_back_button"),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val summary = buildString {
                                appendLine("=== SPOTUI DIAGNOSTIC REPORT ===")
                                healthReport?.checks?.forEach { c ->
                                    appendLine("[${c.severity}] ${c.title}: ${c.summary} | ${c.detail}")
                                }
                                appendLine("\n=== RECENT LOGS (${filteredLogs.size}) ===")
                                filteredLogs.take(80).forEach { l ->
                                    appendLine("${l.timestamp} [${l.level}] ${l.tag}: ${l.message}")
                                }
                            }
                            clipboard.setPrimaryClip(ClipData.newPlainText("SpotUIDiagnostics", summary))
                            Toast.makeText(context, "Diagnostic report copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("dev_console_copy_all_button"),
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Full Report", tint = AppPalette)
                    }
                    IconButton(
                        onClick = { DevConsoleManager.clearLogs() },
                        modifier = Modifier.testTag("dev_console_clear_logs_button"),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear Logs", tint = Color(0xFFFF6B6B))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF17171F)),
            )
        },
        containerColor = AppBackground,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF17171F),
                contentColor = AppPalette,
                edgePadding = 8.dp,
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Health (20)", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("dev_tab_health"),
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Logger (${filteredLogs.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("dev_tab_logger"),
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Telemetry", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("dev_tab_telemetry"),
                )
                val unresolvedFaults = faults.count { !it.resolved }
                val btIssues = btReport?.issueCount ?: 0
                val badgeCount = unresolvedFaults + btIssues
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = {
                        Text(
                            if (badgeCount > 0) "BT & Faults ($badgeCount)" else "BT & Faults",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (badgeCount > 0) Color(0xFFFFB74D) else Color.Unspecified,
                        )
                    },
                    modifier = Modifier.testTag("dev_tab_bt_faults"),
                )
            }

            when (selectedTab) {
                0 -> HealthScannerTab(
                    report = healthReport,
                    isScanning = isScanning,
                    fixingId = fixingId,
                    onRescan = { triggerScan(userInitiated = true) },
                    onFix = { check ->
                        val fix = check.autoFix ?: return@HealthScannerTab
                        fixingId = check.id
                        scope.launch {
                            val msg = runCatching { fix(context) }.getOrElse { "Fix error: ${it.message}" }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            fixingId = null
                            triggerScan(forceRefresh = true)
                        }
                    },
                )
                1 -> LiveLoggerTab(
                    filteredLogs = filteredLogs,
                    selectedFilter = selectedFilter,
                    searchQuery = searchQuery,
                    onFilterChange = { selectedFilter = it },
                    onSearchChange = { searchQuery = it },
                    onSelectLog = { selectedLogForDialog = it },
                )
                2 -> TelemetryAndCrashesTab(
                    telemetry = telemetry,
                    diagnostics = diagnostics,
                    hasCrashReports = hasCrashReports,
                    forceFlac = forceFlac,
                    forceYt = forceYt,
                    strictSanitize = strictSanitize,
                    oneUiOpt = oneUiOpt,
                    highPerfBuf = highPerfBuf,
                    watchdogOn = watchdogOn,
                    onForceFlac = { forceFlac = it; DevConsoleManager.forceLosslessFlac.value = it },
                    onForceYt = { forceYt = it; DevConsoleManager.forceYoutubeEngine.value = it },
                    onStrictSanitize = { strictSanitize = it; DevConsoleManager.strictSanitization.value = it },
                    onOneUiOpt = { oneUiOpt = it; DevConsoleManager.oneUiOptimizations.value = it },
                    onHighPerfBuf = { highPerfBuf = it; DevConsoleManager.highPerformanceBuffer.value = it },
                    onWatchdogOn = { watchdogOn = it; DevConsoleManager.watchdogEnabled.value = it },
                    onOpenCrashDump = {
                        latestCrashText = CrashHandler.getLatestCrashReport(context)
                        showCrashDialog = true
                    },
                )
                3 -> BluetoothSpecsAndFaultsTab(
                    btReport = btReport,
                    isRefreshing = isInspectingBt,
                    faults = faults,
                    onRefreshBt = { refreshBtSpecs(forceLog = true) },
                    onApplyBtFix = { conflict ->
                        val fix = conflict.applyFix ?: return@BluetoothSpecsAndFaultsTab
                        val msg = runCatching { fix(context) }.getOrElse { "Fix failed: ${it.message}" }
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        refreshBtSpecs(forceLog = true)
                    },
                    onResolveFault = { DevConsoleManager.markFaultResolved(it) },
                    onClearFaults = { DevConsoleManager.clearFaults() },
                )
            }
        }
    }

    selectedLogForDialog?.let { log ->
        AlertDialog(
            onDismissRequest = { selectedLogForDialog = null },
            title = { Text("[${log.level}] ${log.tag}", color = Color.White, fontSize = 16.sp) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Time: ${log.timestamp}", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(log.message, color = Color.White, fontSize = 13.sp)
                    log.exceptionDetails?.let { stack ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            stack,
                            color = Color(0xFFFF6B6B),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText(
                        "LogEntry",
                        "${log.timestamp} [${log.level}] ${log.tag}: ${log.message}\n${log.exceptionDetails ?: ""}",
                    )
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Copy", color = AppPalette)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedLogForDialog = null }) {
                    Text("Close", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF17171F),
        )
    }

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
                        .padding(8.dp),
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item(key = "crash_dump_body") {
                            Text(
                                text = crashReport,
                                color = Color(0xFFFFCDD2),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("SpotUICrashDump", crashReport))
                    Toast.makeText(context, "Crash dump copied to clipboard", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Copy Full Dump", color = AppPalette)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        CrashHandler.clearAllCrashReports(context)
                        hasCrashReports = false
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
            containerColor = Color(0xFF17171F),
        )
    }
}

@Composable
private fun HealthScannerTab(
    report: HealthScanReport?,
    isScanning: Boolean,
    fixingId: String?,
    onRescan: () -> Unit,
    onFix: (HealthCheckResult) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Summary Banner + Rescan Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF1E2029))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("System Health Diagnostics", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (report != null) {
                    Text(
                        "${report.okCount} OK · ${report.warnCount} Warnings · ${report.failCount} Issues (${report.durationMs}ms)",
                        color = if (report.failCount > 0) Color(0xFFFF8B94) else Color(0xFF81C784),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Text("Scanning playback, DSP, network, accounts & cache…", color = Color(0xFFB3B3B3), fontSize = 12.sp)
                }
            }
            Button(
                enabled = !isScanning,
                onClick = onRescan,
                colors = ButtonDefaults.buttonColors(containerColor = AppPalette, contentColor = Color.Black),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("health_rescan_button"),
            ) {
                if (isScanning) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.Black)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(if (isScanning) "Scanning" else "Scan All", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        val checks = report?.checks.orEmpty()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(
                items = checks,
                key = { _, item -> item.id },
            ) { _, item ->
                HealthCheckRow(
                    item = item,
                    isFixing = fixingId == item.id,
                    onFix = { onFix(item) },
                )
            }
        }
    }
}

@Composable
private fun HealthCheckRow(
    item: HealthCheckResult,
    isFixing: Boolean,
    onFix: () -> Unit,
) {
    val (icon, tint, bgBadge) = when (item.severity) {
        CheckSeverity.OK -> Triple(Icons.Default.CheckCircle, Color(0xFF81C784), Color(0x2281C784))
        CheckSeverity.WARNING -> Triple(Icons.Default.Warning, Color(0xFFFFB74D), Color(0x22FFB74D))
        CheckSeverity.FAIL -> Triple(Icons.Default.Error, Color(0xFFFF5252), Color(0x22FF5252))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A1B23))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(bgBadge),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = item.severity.name, tint = tint, modifier = Modifier.size(18.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(item.category.uppercase(), color = Color(0xFF8C92A4), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Text(item.summary, color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(item.detail, color = Color(0xFFA5AAB8), fontSize = 11.sp, lineHeight = 14.sp)
        }

        if (item.autoFix != null && item.fixLabel != null) {
            TextButton(
                enabled = !isFixing,
                onClick = onFix,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("health_fix_${item.id}"),
            ) {
                Icon(Icons.Default.Healing, contentDescription = null, tint = AppPalette, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (isFixing) "…" else item.fixLabel, color = AppPalette, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LiveLoggerTab(
    filteredLogs: List<DevConsoleManager.LogEntry>,
    selectedFilter: DevConsoleManager.LogLevel?,
    searchQuery: String,
    onFilterChange: (DevConsoleManager.LogLevel?) -> Unit,
    onSearchChange: (String) -> Unit,
    onSelectLog: (DevConsoleManager.LogEntry) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Filter by tag or message…", color = Color.Gray, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("logger_search_input"),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppPalette,
                unfocusedBorderColor = Color(0xFF34343F),
                focusedContainerColor = Color(0xFF1D1E26),
                unfocusedContainerColor = Color(0xFF1D1E26),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
            ),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip(
                selected = selectedFilter == null,
                onClick = { onFilterChange(null) },
                label = { Text("ALL", fontSize = 11.sp) },
                modifier = Modifier.testTag("log_filter_all"),
            )
            DevConsoleManager.LogLevel.entries.forEach { lvl ->
                FilterChip(
                    selected = selectedFilter == lvl,
                    onClick = { onFilterChange(if (selectedFilter == lvl) null else lvl) },
                    label = { Text(lvl.name, fontSize = 11.sp) },
                    modifier = Modifier.testTag("log_filter_${lvl.name.lowercase()}"),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF0B0C10), RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFF2B2D3A), RoundedCornerShape(10.dp))
                .padding(8.dp),
        ) {
            if (filteredLogs.isEmpty()) {
                Text(
                    "No matching log entries recorded.",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(
                        items = filteredLogs,
                        key = { idx, entry -> "${entry.timestamp}_${entry.tag}_$idx" },
                    ) { _, log ->
                        LogItemRow(log) { onSelectLog(log) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryAndCrashesTab(
    telemetry: SongPlayer.PlayerTelemetry,
    diagnostics: DevConsoleManager.PlaybackDiagnostics,
    hasCrashReports: Boolean,
    forceFlac: Boolean,
    forceYt: Boolean,
    strictSanitize: Boolean,
    oneUiOpt: Boolean,
    highPerfBuf: Boolean,
    watchdogOn: Boolean,
    onForceFlac: (Boolean) -> Unit,
    onForceYt: (Boolean) -> Unit,
    onStrictSanitize: (Boolean) -> Unit,
    onOneUiOpt: (Boolean) -> Unit,
    onHighPerfBuf: (Boolean) -> Unit,
    onWatchdogOn: (Boolean) -> Unit,
    onOpenCrashDump: () -> Unit,
) {
    val stateName = when (telemetry.playbackState) {
        Player.STATE_IDLE -> "IDLE"
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY -> "READY"
        Player.STATE_ENDED -> "ENDED"
        else -> telemetry.playbackState.toString()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Live Telemetry Panel
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E2029))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("LIVE AUDIO & MEDIA3 TELEMETRY", color = AppPalette, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DiagnosticBadge("Session", "#${telemetry.sessionId}")
                DiagnosticBadge("State", stateName)
                DiagnosticBadge("Source", telemetry.activeSource)
                DiagnosticBadge("Buffer", "${telemetry.bufferedPercentage}%")
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DiagnosticBadge("Quality", telemetry.currentQuality.ifBlank { "Adaptive" })
                DiagnosticBadge("SW DSP", if (telemetry.softwareDspActive) "Active" else "Standby")
                DiagnosticBadge("HW FX", if (telemetry.hardwareEqActive) "Active" else "Bypass")
                DiagnosticBadge("Collisions", "${diagnostics.totalCollisionsDetected}")
            }
            if (!telemetry.lastError.isNullOrBlank()) {
                Text("Last Error: ${telemetry.lastError}", color = Color(0xFFFF8B94), fontSize = 11.sp)
            }
            if (hasCrashReports) {
                Button(
                    onClick = onOpenCrashDump,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("view_latest_crash_dump_button"),
                ) {
                    Icon(Icons.Default.BugReport, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("VIEW LATEST CRASH DUMP", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Dev Flags
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E2029))
                .padding(14.dp),
        ) {
            Text("RUNTIME ENGINE FLAGS", color = Color(0xFFB3B3B3), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            DevToggleRow("Force Lossless FLAC", forceFlac, onForceFlac)
            DevToggleRow("Force YouTube Fallback Engine", forceYt, onForceYt)
            DevToggleRow("Strict Stream Sanitization", strictSanitize, onStrictSanitize)
            DevToggleRow("Samsung One UI / Background Optimizations", oneUiOpt, onOneUiOpt)
            DevToggleRow("High-Performance Audio Buffering", highPerfBuf, onHighPerfBuf)
            DevToggleRow("Stream & Focus Watchdog", watchdogOn, onWatchdogOn)
        }
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
            .heightIn(min = 48.dp)
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = state,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = AppPalette,
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color.DarkGray,
            ),
        )
    }
}

@Composable
private fun BluetoothSpecsAndFaultsTab(
    btReport: BluetoothAudioInspector.BluetoothSpecsReport?,
    isRefreshing: Boolean,
    faults: List<DevConsoleManager.ComponentFault>,
    onRefreshBt: () -> Unit,
    onApplyBtFix: (BluetoothAudioInspector.BluetoothConflict) -> Unit,
    onResolveFault: (String) -> Unit,
    onClearFaults: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "bt_header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1E2029))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            Icons.Default.BluetoothAudio,
                            contentDescription = "Bluetooth Audio",
                            tint = if (btReport?.isBluetoothConnected == true) AppPalette else Color(0xFFB3B3B3),
                        )
                        Column {
                            Text("BLUETOOTH & HEADSET SPECS", color = AppPalette, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(btReport?.deviceName ?: "Probing audio sink...", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        enabled = !isRefreshing,
                        onClick = onRefreshBt,
                        colors = ButtonDefaults.buttonColors(containerColor = AppPalette, contentColor = Color.Black),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("bt_specs_refresh"),
                    ) {
                        Text(if (isRefreshing) "…" else "Probe", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                btReport?.let { r ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        DiagnosticBadge("Codec", r.codecName)
                        DiagnosticBadge("Rate", r.sampleRateKhz)
                        DiagnosticBadge("Depth", r.bitsPerSample)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        DiagnosticBadge("Transport", r.transportLabel.substringAfter("Bluetooth ").take(18))
                        DiagnosticBadge("Channels", r.channelMode.take(18))
                        DiagnosticBadge("BT-Safe DSP", if (r.bluetoothSafeBypassActive) "Active" else "Standby")
                    }
                    Text(
                        "Supported Rates: ${r.supportedRates}  ·  Formats: ${r.supportedEncodings}",
                        color = Color(0xFFB3B8C8),
                        fontSize = 11.sp,
                    )
                    if (r.selectableCodecs.isNotEmpty()) {
                        Text(
                            "Selectable Codecs: ${r.selectableCodecs.joinToString(" / ")}",
                            color = Color(0xFF9ED4FF),
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }

        val conflicts = btReport?.conflicts.orEmpty()
        item(key = "bt_conflicts_header") {
            Text(
                if (conflicts.isEmpty()) "BLUETOOTH ISSUE DETECTOR — NO CONFLICTS" else "DETECTED BLUETOOTH / DSP CONFLICTS (${conflicts.size})",
                color = if (conflicts.isEmpty()) Color(0xFF81C784) else Color(0xFFFFB74D),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (conflicts.isEmpty()) {
            item(key = "bt_clean") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF17221B))
                        .padding(12.dp),
                ) {
                    Text(
                        "No SpotUI DSP, Audio Offload, gain, or mono settings are interfering with your Bluetooth headset or speaker.",
                        color = Color(0xFFA5D6A7),
                        fontSize = 12.sp,
                    )
                }
            }
        } else {
            itemsIndexed(conflicts, key = { _, c -> "conflict_${c.id}" }) { _, c ->
                val tint = if (c.severity == CheckSeverity.FAIL) Color(0xFFFF5252) else Color(0xFFFFB74D)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1F1C22))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${if (c.causedBySpotui) "[SpotUI Conflict] " else "[System] "}${c.title}",
                            color = tint,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(c.explanation, color = Color(0xFFD0D3DC), fontSize = 11.sp, lineHeight = 15.sp)
                    }
                    if (c.fixLabel != null && c.applyFix != null) {
                        TextButton(
                            onClick = { onApplyBtFix(c) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("bt_fix_${c.id}"),
                        ) {
                            Icon(Icons.Default.Build, contentDescription = null, tint = AppPalette, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(c.fixLabel, color = AppPalette, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item(key = "faults_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "COMPONENT & BUTTON FAULTS (${faults.count { !it.resolved }} active)",
                    color = Color(0xFFFF8B94),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (faults.isNotEmpty()) {
                    TextButton(
                        onClick = onClearFaults,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text("Clear Faults", color = Color(0xFFFF6B6B), fontSize = 11.sp)
                    }
                }
            }
        }

        if (faults.isEmpty()) {
            item(key = "faults_empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1A1B23))
                        .padding(12.dp),
                ) {
                    Text(
                        "No broken buttons, playback stalls, or UI faults logged during this session.",
                        color = Color(0xFFA5AAB8),
                        fontSize = 12.sp,
                    )
                }
            }
        } else {
            itemsIndexed(faults, key = { _, f -> f.id }) { _, fault ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (fault.resolved) Color(0xFF16181F) else Color(0xFF24181C))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${fault.timestamp} · ${fault.component}.${fault.action}${if (fault.resolved) " (Resolved)" else ""}",
                            color = if (fault.resolved) Color(0xFF81C784) else Color(0xFFFF7676),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(fault.reason, color = Color.White, fontSize = 12.sp)
                        if (fault.autoFixHint.isNotBlank()) {
                            Text("Hint: ${fault.autoFixHint}", color = Color(0xFF9ED4FF), fontSize = 11.sp)
                        }
                    }
                    if (!fault.resolved) {
                        TextButton(
                            onClick = { onResolveFault(fault.id) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text("Resolve", color = AppPalette, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LogItemRow(log: DevConsoleManager.LogEntry, onClick: () -> Unit) {
    val levelColor = when (log.level) {
        DevConsoleManager.LogLevel.ERROR -> Color(0xFFFF4D4D)
        DevConsoleManager.LogLevel.COMPONENT_FAULT -> Color(0xFFFF6B9D)
        DevConsoleManager.LogLevel.BT_DIAGNOSTIC -> Color(0xFF64B5F6)
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
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            log.timestamp,
            color = Color.Gray,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(72.dp),
        )
        Text(
            "[${log.level.name.take(5)}]",
            color = levelColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(58.dp),
        )
        Text(
            "${log.tag}: ${log.message}",
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 2,
        )
    }
}
