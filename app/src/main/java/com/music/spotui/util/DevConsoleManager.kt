package com.music.spotui.util

import android.content.Context
import android.os.Build
import com.music.spotui.BuildConfig
import com.music.spotui.accessibility.AccessibilityHelper
import com.music.spotui.audio.AudioEffectController
import com.music.spotui.audio.BluetoothAudioInspector
import com.music.spotui.data.preferences.getBassBoostStrength
import com.music.spotui.data.preferences.getEqPreset
import com.music.spotui.data.preferences.getFloaterAutoHideMs
import com.music.spotui.data.preferences.getFloaterHapticStrength
import com.music.spotui.data.preferences.getSpatialProfile
import com.music.spotui.data.preferences.getSpatialStrength
import com.music.spotui.data.preferences.isEqEnabled
import com.music.spotui.data.preferences.isEqSpatialEnabled
import com.music.spotui.data.preferences.isFloaterEqAnd3dEnabled
import com.music.spotui.data.preferences.isFloaterHapticsEnabled
import com.music.spotui.data.preferences.isSpatial8dOrbitEnabled
import com.music.spotui.di.SongPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * DevConsoleManager (v2.5.4)
 * Dedicated real-time categorized logger, 2,000-entry ring buffer, audio/DSP/3D telemetry,
 * MrBean network throughput tracking, Accessibility Floater monitoring, and fault ledger.
 */
object DevConsoleManager {

    enum class LogCategory(val label: String) {
        PLAYBACK("Playback"),
        AUDIO_DSP("DSP & 3D"),
        NETWORK_DL("Network & DL"),
        AUTH("Auth"),
        FLOATER_UI("Floater & UI"),
        BLUETOOTH("Bluetooth"),
        MODULES("Modules"),
        SYSTEM("System"),
    }

    data class LogEntry(
        val timestamp: String,
        val level: LogLevel,
        val tag: String,
        val message: String,
        val exceptionDetails: String? = null,
        val category: LogCategory = inferCategory(tag, level),
    )

    enum class LogLevel {
        INFO, DEBUG, SANITIZATION, WATCHDOG, COLLISION, COMPONENT_FAULT, BT_DIAGNOSTIC, WARNING, ERROR
    }

    data class ComponentFault(
        val id: String,
        val timestamp: String,
        val component: String,
        val action: String,
        val reason: String,
        val autoFixHint: String = "",
        val resolved: Boolean = false,
    )

    data class PlaybackDiagnostics(
        var activeSource: String = "Idle",
        var currentQuality: String = "Standard",
        var streamBitrateKbps: Int = 0,
        var bufferPositionMs: Long = 0L,
        var currentPositionMs: Long = 0L,
        var isBuffering: Boolean = false,
        var totalCollisionsDetected: Int = 0,
        var sanitizationCount: Int = 0,
        var activeEngine: String = "ExoPlayer (Media3)",
        var totalComponentFaults: Int = 0,
        var bluetoothSummary: String = "Probing...",
        var totalLogsRecorded: Long = 0L,
        var totalErrorsRecorded: Long = 0L,
        var totalWarningsRecorded: Long = 0L,
    )

    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private const val MAX_LOGS = 2000
    private val initialized = AtomicBoolean(false)

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _faults = MutableStateFlow<List<ComponentFault>>(emptyList())
    val faults: StateFlow<List<ComponentFault>> = _faults.asStateFlow()

    private val _diagnostics = MutableStateFlow(PlaybackDiagnostics())
    val diagnostics: StateFlow<PlaybackDiagnostics> = _diagnostics.asStateFlow()

    private val _isStreamPaused = MutableStateFlow(false)
    val isStreamPaused: StateFlow<Boolean> = _isStreamPaused.asStateFlow()

    // Configurable Developer Flags
    val forceLosslessFlac = MutableStateFlow(false)
    val forceYoutubeEngine = MutableStateFlow(false)
    val strictSanitization = MutableStateFlow(true)
    val oneUiOptimizations = MutableStateFlow(true)
    val highPerformanceBuffer = MutableStateFlow(true)
    val watchdogEnabled = MutableStateFlow(true)

    init {
        appendInMemoryEntry(
            LogEntry(
                timestamp = formatNow(),
                level = LogLevel.INFO,
                tag = "DevConsoleManager",
                message = "Dedicated v2.5.4 Logger ready (2,000-entry ring buffer, 8 channels, live telemetry).",
                category = LogCategory.SYSTEM,
            ),
        )
    }

    fun inferCategory(tag: String, level: LogLevel): LogCategory {
        val t = tag.lowercase(Locale.US)
        return when {
            level == LogLevel.BT_DIAGNOSTIC || "bluetooth" in t || t.startsWith("bt") -> LogCategory.BLUETOOTH
            "spatial" in t || "dsp" in t || "audioeffect" in t || "equalizer" in t || "sound" in t -> LogCategory.AUDIO_DSP
            "mrbean" in t || "download" in t || "updater" in t || "network" in t || "http" in t || "dns" in t -> LogCategory.NETWORK_DL
            "login" in t || "auth" in t || "token" in t || "session" in t || "oauth" in t -> LogCategory.AUTH
            "floater" in t || "accessibility" in t || t.startsWith("ui:") || "theme" in t -> LogCategory.FLOATER_UI
            "module" in t || "lastfm" in t || "shazam" in t || "lyrics" in t || "ytdlp" in t || "innertube" in t -> LogCategory.MODULES
            "songplayer" in t || "playback" in t || "player" in t || "queue" in t || "media" in t || "sanitizer" in t -> LogCategory.PLAYBACK
            else -> LogCategory.SYSTEM
        }
    }

    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        val appCtx = context.applicationContext
        val floaterEnabled = runCatching { AccessibilityHelper.isServiceEnabled(appCtx) }.getOrDefault(false)
        val appVol = runCatching { SongPlayer.getAppVolumePercent(appCtx) }.getOrDefault(100)
        val spatialOn = runCatching { isEqSpatialEnabled(appCtx) }.getOrDefault(false)
        logInfo(
            "DevConsoleManager",
            "Session v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE}) | Floater=${if (floaterEnabled) "Active" else "Standby"} | InAppVol=$appVol% | 3D=${if (spatialOn) getSpatialProfile(appCtx).label else "Off"}",
        )
    }

    fun setStreamPaused(paused: Boolean) {
        _isStreamPaused.value = paused
    }

    @Synchronized
    private fun appendInMemoryEntry(entry: LogEntry) {
        if (_isStreamPaused.value && entry.level != LogLevel.ERROR && entry.level != LogLevel.COMPONENT_FAULT) {
            return
        }
        val current = _logs.value.toMutableList()
        while (current.size >= MAX_LOGS) {
            current.removeAt(0)
        }
        current.add(entry)
        _logs.value = current
        _diagnostics.value = _diagnostics.value.let { d ->
            d.copy(
                totalLogsRecorded = d.totalLogsRecorded + 1L,
                totalErrorsRecorded = d.totalErrorsRecorded + if (entry.level == LogLevel.ERROR || entry.level == LogLevel.COMPONENT_FAULT) 1L else 0L,
                totalWarningsRecorded = d.totalWarningsRecorded + if (entry.level == LogLevel.WARNING) 1L else 0L,
            )
        }
    }

    private fun formatNow(): String = runCatching {
        dateFormat.format(Date())
    }.getOrDefault("00:00:00.000")

    fun log(
        level: LogLevel,
        tag: String,
        message: String,
        exception: Throwable? = null,
        category: LogCategory = inferCategory(tag, level),
    ) {
        val entry = LogEntry(
            timestamp = formatNow(),
            level = level,
            tag = tag,
            message = message,
            exceptionDetails = exception?.stackTraceToString(),
            category = category,
        )
        appendInMemoryEntry(entry)
        AppDiagnostics.record(level.name, tag, message, exception, mirrorToConsole = false)
    }

    internal fun appendExternalLog(
        rawLevel: String,
        tag: String,
        message: String,
        exception: Throwable? = null,
    ) {
        val mapped = when (rawLevel.uppercase(Locale.US)) {
            "ERROR", "FATAL" -> LogLevel.ERROR
            "WARN", "WARNING" -> LogLevel.WARNING
            "DEBUG" -> LogLevel.DEBUG
            "WATCHDOG" -> LogLevel.WATCHDOG
            "SANITIZATION" -> LogLevel.SANITIZATION
            "COLLISION" -> LogLevel.COLLISION
            "COMPONENT_FAULT" -> LogLevel.COMPONENT_FAULT
            "BT_DIAGNOSTIC" -> LogLevel.BT_DIAGNOSTIC
            else -> LogLevel.INFO
        }
        appendInMemoryEntry(
            LogEntry(
                timestamp = formatNow(),
                level = mapped,
                tag = tag,
                message = message,
                exceptionDetails = exception?.stackTraceToString(),
                category = inferCategory(tag, mapped),
            ),
        )
    }

    fun logInfo(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun logDebug(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun logSanitization(message: String) {
        log(LogLevel.SANITIZATION, "Sanitizer", message, category = LogCategory.PLAYBACK)
        updateDiagnostics { copy(sanitizationCount = sanitizationCount + 1) }
    }
    fun logWatchdog(tag: String, message: String) = log(LogLevel.WATCHDOG, tag, message)
    fun logCollision(tag: String, message: String) {
        log(LogLevel.COLLISION, tag, message)
        updateDiagnostics { copy(totalCollisionsDetected = totalCollisionsDetected + 1) }
    }
    fun logBluetoothDiagnostic(message: String) =
        log(LogLevel.BT_DIAGNOSTIC, "BluetoothInspector", message, category = LogCategory.BLUETOOTH)

    fun logDsp(message: String) = log(LogLevel.INFO, "AudioDsp", message, category = LogCategory.AUDIO_DSP)
    fun logNetwork(message: String) = log(LogLevel.INFO, "MrbeanNetwork", message, category = LogCategory.NETWORK_DL)
    fun logAuth(message: String) = log(LogLevel.INFO, "SpotifyAuth", message, category = LogCategory.AUTH)
    fun logFloater(message: String) = log(LogLevel.INFO, "FloaterOverlay", message, category = LogCategory.FLOATER_UI)

    fun logButtonAction(component: String, action: String, detail: String = "") {
        val suffix = if (detail.isNotBlank()) " — $detail" else ""
        log(LogLevel.DEBUG, "UI:$component", "Action [$action]$suffix", category = LogCategory.FLOATER_UI)
    }

    @Synchronized
    fun reportComponentFault(
        component: String,
        action: String,
        reason: String,
        autoFixHint: String = "",
        exception: Throwable? = null,
    ) {
        val ts = formatNow()
        val id = "${component}_${action}_${System.currentTimeMillis()}"
        val fault = ComponentFault(
            id = id,
            timestamp = ts,
            component = component,
            action = action,
            reason = reason,
            autoFixHint = autoFixHint,
        )
        val list = _faults.value.toMutableList()
        if (list.size >= 150) list.removeAt(0)
        list.add(0, fault)
        _faults.value = list
        log(LogLevel.COMPONENT_FAULT, component, "[$action] FAULT: $reason", exception)
        updateDiagnostics { copy(totalComponentFaults = _faults.value.count { !it.resolved }) }
    }

    @Synchronized
    fun markFaultResolved(id: String) {
        _faults.value = _faults.value.map { if (it.id == id) it.copy(resolved = true) else it }
        updateDiagnostics { copy(totalComponentFaults = _faults.value.count { !it.resolved }) }
    }

    @Synchronized
    fun clearFaults() {
        _faults.value = emptyList()
        updateDiagnostics { copy(totalComponentFaults = 0) }
        logInfo("DevConsoleManager", "Component fault ledger cleared.")
    }

    fun logWarning(tag: String, message: String) = log(LogLevel.WARNING, tag, message)
    fun logError(tag: String, message: String, exception: Throwable? = null) =
        log(LogLevel.ERROR, tag, message, exception)

    fun updateDiagnostics(update: PlaybackDiagnostics.() -> PlaybackDiagnostics) {
        _diagnostics.value = _diagnostics.value.update()
    }

    fun clearLogs() {
        _logs.value = emptyList()
        logInfo("DevConsoleManager", "Live memory logs cleared by user.")
    }

    fun clearAllLogs(context: Context) {
        _logs.value = emptyList()
        AppDiagnostics.clear(context.applicationContext)
        logInfo("DevConsoleManager", "Cleared both live memory logs and persistent disk diagnostics.")
    }

    /** Generates a clean, redacted full support bundle suitable for Clipboard or File Share. */
    fun buildDiagnosticReportText(
        context: Context,
        healthReport: HealthScanReport? = null,
        btReport: BluetoothAudioInspector.BluetoothSpecsReport? = null,
        includeDiskTail: Boolean = true,
    ): String = buildString {
        val appCtx = context.applicationContext
        val floaterActive = runCatching { AccessibilityHelper.isServiceEnabled(appCtx) }.getOrDefault(false)
        val appVol = runCatching { SongPlayer.getAppVolumePercent(appCtx) }.getOrDefault(100)
        val route = AudioEffectController.routeState.value
        val telemetry = runCatching { SongPlayer.telemetrySnapshot(appCtx) }.getOrNull()
        val autoHideMs = getFloaterAutoHideMs(appCtx)

        appendLine("=== SPOTUI DIAGNOSTIC REPORT ===")
        appendLine("Version: v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android SDK ${Build.VERSION.SDK_INT})")
        appendLine("Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())}")
        appendLine("Session Stats: ${diagnostics.value.totalLogsRecorded} logged | ${diagnostics.value.totalErrorsRecorded} errors | ${diagnostics.value.totalWarningsRecorded} warnings")
        appendLine()

        appendLine("=== ACCESSIBILITY FLOATER & AUDIO DSP ===")
        appendLine("Accessibility Service: ${if (floaterActive) "Active" else "Inactive"}")
        appendLine("In-App Volume: $appVol% (independent of Android OS volume)")
        appendLine("Floater EQ/3D Hook: ${if (isFloaterEqAnd3dEnabled(appCtx)) "Enabled" else "Hidden"}")
        appendLine("Floater Haptics: ${if (isFloaterHapticsEnabled(appCtx)) getFloaterHapticStrength(appCtx).label else "Off"}")
        appendLine("Floater Auto-Hide: ${if (autoHideMs <= 0L) "Stay Open" else "${autoHideMs / 1000L}s"}")
        appendLine("EQ: enabled=${isEqEnabled(appCtx)} preset=${getEqPreset(appCtx)} bassBoost=${getBassBoostStrength(appCtx) / 10}%")
        appendLine(
            "3D Spatial: enabled=${isEqSpatialEnabled(appCtx)} profile=${getSpatialProfile(appCtx).label} " +
                "depth=${getSpatialStrength(appCtx) / 10}% 8D=${isSpatial8dOrbitEnabled(appCtx)}",
        )
        appendLine("Output Route: ${route.route.label} (${route.deviceName})")
        if (telemetry != null) {
            appendLine(
                "Playback: source=${telemetry.activeSource} quality=${telemetry.currentQuality} " +
                    "state=${telemetry.playbackState} session=#${telemetry.sessionId} swDsp=${telemetry.softwareDspActive}",
            )
            if (!telemetry.lastError.isNullOrBlank()) {
                appendLine("Last Player Error: ${telemetry.lastError}")
            }
        }

        if (btReport != null) {
            appendLine()
            appendLine("=== BLUETOOTH / SINK SPECS ===")
            appendLine("Device=${btReport.deviceName} Codec=${btReport.codecName} Rate=${btReport.sampleRateKhz} Depth=${btReport.bitsPerSample}")
            appendLine("Transport=${btReport.transportLabel} Conflicts=${btReport.issueCount}")
        }

        if (healthReport != null) {
            appendLine()
            appendLine("=== SYSTEM HEALTH (${healthReport.checks.size} CHECKS) ===")
            appendLine("Summary: ${healthReport.okCount} OK | ${healthReport.warnCount} WARN | ${healthReport.failCount} FAIL (${healthReport.durationMs}ms)")
            healthReport.checks.forEach { c ->
                appendLine("[${c.severity}] ${c.title}: ${c.summary} | ${c.detail}")
            }
        }

        val currentFaults = faults.value
        if (currentFaults.isNotEmpty()) {
            appendLine()
            appendLine("=== COMPONENT FAULTS (${currentFaults.size}) ===")
            currentFaults.take(30).forEach { f ->
                appendLine("${f.timestamp} [${if (f.resolved) "RESOLVED" else "OPEN"}] ${f.component}.${f.action}: ${f.reason}")
            }
        }

        val liveLogs = logs.value.takeLast(180).reversed()
        appendLine()
        appendLine("=== LIVE MEMORY LOGS (${liveLogs.size} of ${logs.value.size}) ===")
        liveLogs.forEach { l ->
            appendLine("${l.timestamp} [${l.category.label}/${l.level}] ${l.tag}: ${l.message}")
            if (!l.exceptionDetails.isNullOrBlank()) {
                appendLine(l.exceptionDetails.take(600))
            }
        }

        if (includeDiskTail) {
            appendLine()
            appendLine("=== PERSISTED DIAGNOSTICS LOG TAIL ===")
            appendLine(AppDiagnostics.readRecent(appCtx, maxCharacters = 12_000))
        }
    }
}
