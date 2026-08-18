package com.music.spotui.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * DevConsoleManager
 * Real-time logging, video & audio watchdog, stream diagnostic telemetry,
 * error viewing, and audio focus / stream request collision detection for SpotUI.
 */
object DevConsoleManager {

    data class LogEntry(
        val timestamp: String,
        val level: LogLevel,
        val tag: String,
        val message: String,
        val exceptionDetails: String? = null
    )

    enum class LogLevel {
        INFO, DEBUG, SANITIZATION, WATCHDOG, COLLISION, WARNING, ERROR
    }

    data class PlaybackDiagnostics(
        var activeSource: String = "Idle",
        var currentQuality: String = "Standard",
        var streamBitrateKbps: Int = 0,
        var bufferPositionMs: Long = 0L,
        var currentPositionMs: Long = 0L,
        var isBuffering: Boolean = false,
        var totalCollisionsDetected: Int = 0,
        var sanitizationCount: Int = 0,
        var activeEngine: String = "ExoPlayer (Media3)"
    )

    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val maxLogs = 500

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _diagnostics = MutableStateFlow(PlaybackDiagnostics())
    val diagnostics: StateFlow<PlaybackDiagnostics> = _diagnostics.asStateFlow()

    // Configurable Developer Flags
    val forceLosslessFlac = MutableStateFlow(false)
    val forceYoutubeEngine = MutableStateFlow(false)
    val strictSanitization = MutableStateFlow(true)
    val oneUiOptimizations = MutableStateFlow(true)
    val highPerformanceBuffer = MutableStateFlow(true)
    val watchdogEnabled = MutableStateFlow(true)

    init {
        logInfo("DevConsoleManager", "Developer Console & Telemetry Engine initialized.")
    }

    @Synchronized
    fun log(level: LogLevel, tag: String, message: String, exception: Throwable? = null) {
        val entry = LogEntry(
            timestamp = dateFormat.format(Date()),
            level = level,
            tag = tag,
            message = message,
            exceptionDetails = exception?.stackTraceToString()
        )
        val current = _logs.value.toMutableList()
        if (current.size >= maxLogs) {
            current.removeAt(0)
        }
        current.add(entry)
        _logs.value = current
        AppDiagnostics.record(level.name, tag, message, exception)
    }

    fun logInfo(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun logDebug(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun logSanitization(message: String) {
        log(LogLevel.SANITIZATION, "Sanitizer", message)
        updateDiagnostics { copy(sanitizationCount = sanitizationCount + 1) }
    }
    fun logWatchdog(tag: String, message: String) = log(LogLevel.WATCHDOG, tag, message)
    fun logCollision(tag: String, message: String) {
        log(LogLevel.COLLISION, tag, message)
        updateDiagnostics { copy(totalCollisionsDetected = totalCollisionsDetected + 1) }
    }
    fun logWarning(tag: String, message: String) = log(LogLevel.WARNING, tag, message)
    fun logError(tag: String, message: String, exception: Throwable? = null) =
        log(LogLevel.ERROR, tag, message, exception)

    fun updateDiagnostics(update: PlaybackDiagnostics.() -> PlaybackDiagnostics) {
        _diagnostics.value = _diagnostics.value.update()
    }

    fun clearLogs() {
        _logs.value = emptyList()
        logInfo("DevConsoleManager", "Logs cleared by developer.")
    }
}
