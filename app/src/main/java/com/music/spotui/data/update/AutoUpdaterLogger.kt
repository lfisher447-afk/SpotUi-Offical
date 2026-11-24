package com.music.spotui.data.update

import android.content.Context
import android.os.Build
import com.music.spotui.BuildConfig
import com.music.spotui.util.DevConsoleManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Dedicated persistent ring-buffer logger for SpotUI's Auto-Updater.
 * Tracks background update checks, release metadata parsing, SHA-256 verification,
 * signing certificate comparisons (installed vs remote CI / archive), permission checks,
 * and Android PackageInstaller session / conflict diagnostics across app restarts.
 */
object AutoUpdaterLogger {

    private const val PREF_NAME = "spotui_auto_updater_logs"
    private const val KEY_ENTRIES_JSON = "entries_json"
    private const val MAX_ENTRIES = 160
    private const val TAG = "AutoUpdater"

    enum class Severity(val label: String) {
        INFO("INFO"),
        SUCCESS("OK"),
        WARNING("WARN"),
        CONFLICT("CONFLICT"),
        ERROR("ERROR"),
    }

    data class UpdaterLogEntry(
        val id: Long,
        val timestampMs: Long,
        val formattedTime: String,
        val stage: String,
        val severity: Severity,
        val message: String,
        val details: String = "",
    )

    private val lock = Any()
    private val nextId = AtomicLong(System.currentTimeMillis())
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    private val _entries = MutableStateFlow<List<UpdaterLogEntry>>(emptyList())
    val entries: StateFlow<List<UpdaterLogEntry>> = _entries.asStateFlow()

    private val _latestConflict = MutableStateFlow<UpdaterLogEntry?>(null)
    val latestConflict: StateFlow<UpdaterLogEntry?> = _latestConflict.asStateFlow()

    @Volatile
    private var loadedFromDisk = false

    fun initialize(context: Context) {
        if (loadedFromDisk) return
        val appCtx = context.applicationContext
        synchronized(lock) {
            if (loadedFromDisk) return
            loadedFromDisk = true
            val raw = appCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_ENTRIES_JSON, null)
                .orEmpty()
            if (raw.isNotBlank()) {
                val parsed = parseJsonList(raw)
                if (parsed.isNotEmpty()) {
                    _entries.value = parsed
                    _latestConflict.value = parsed.firstOrNull {
                        it.severity == Severity.CONFLICT || it.severity == Severity.ERROR
                    }
                    val maxId = parsed.maxOfOrNull { it.id } ?: System.currentTimeMillis()
                    if (maxId >= nextId.get()) {
                        nextId.set(maxId + 1L)
                    }
                }
            }
        }
    }

    fun log(
        context: Context?,
        stage: String,
        severity: Severity,
        message: String,
        details: String = "",
    ) {
        val appCtx = context?.applicationContext
        if (appCtx != null && !loadedFromDisk) {
            initialize(appCtx)
        }
        val now = System.currentTimeMillis()
        val entry = UpdaterLogEntry(
            id = nextId.incrementAndGet(),
            timestampMs = now,
            formattedTime = runCatching { timeFormat.format(Date(now)) }.getOrDefault("--:--:--"),
            stage = stage,
            severity = severity,
            message = message,
            details = details,
        )

        val updated: List<UpdaterLogEntry>
        synchronized(lock) {
            val mutable = _entries.value.toMutableList()
            mutable.add(0, entry)
            while (mutable.size > MAX_ENTRIES) {
                mutable.removeAt(mutable.lastIndex)
            }
            updated = mutable.toList()
            _entries.value = updated
            if (severity == Severity.CONFLICT || severity == Severity.ERROR) {
                _latestConflict.value = entry
            }
            if (appCtx != null) {
                persistLocked(appCtx, updated)
            }
        }

        val consoleMsg = buildString {
            append("[$stage] ")
            append(message)
            if (details.isNotBlank()) {
                append(" | ")
                append(details)
            }
        }
        when (severity) {
            Severity.INFO, Severity.SUCCESS -> DevConsoleManager.log(
                DevConsoleManager.LogLevel.INFO,
                TAG,
                consoleMsg,
                category = DevConsoleManager.LogCategory.NETWORK_DL,
            )
            Severity.WARNING -> DevConsoleManager.log(
                DevConsoleManager.LogLevel.WARNING,
                TAG,
                consoleMsg,
                category = DevConsoleManager.LogCategory.NETWORK_DL,
            )
            Severity.CONFLICT -> DevConsoleManager.log(
                DevConsoleManager.LogLevel.COLLISION,
                TAG,
                consoleMsg,
                category = DevConsoleManager.LogCategory.NETWORK_DL,
            )
            Severity.ERROR -> DevConsoleManager.log(
                DevConsoleManager.LogLevel.ERROR,
                TAG,
                consoleMsg,
                category = DevConsoleManager.LogCategory.NETWORK_DL,
            )
        }
    }

    fun clear(context: Context?) {
        val appCtx = context?.applicationContext
        synchronized(lock) {
            _entries.value = emptyList()
            _latestConflict.value = null
            if (appCtx != null) {
                appCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .remove(KEY_ENTRIES_JSON)
                    .apply()
            }
        }
        log(appCtx, "Logger", Severity.INFO, "Auto-Updater conflict log cleared")
    }

    fun conflictCount(): Int = _entries.value.count {
        it.severity == Severity.CONFLICT || it.severity == Severity.ERROR || it.severity == Severity.WARNING
    }

    fun exportReport(
        context: Context,
        installedCert: UpdateDownloader.SigningCertInfo? = UpdateDownloader.getInstalledSigningCertInfo(context),
        latestInfo: UpdateChecker.UpdateInfo? = UpdateChecker.latestAvailable.value,
    ): String = buildString {
        initialize(context)
        appendLine("=== SPOTUI AUTO-UPDATER CONFLICT & DIAGNOSTIC LOG ===")
        appendLine("App Package: ${context.packageName}")
        appendLine("Installed Version: v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT})")
        appendLine("Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine("Install Unknown Apps Permission: ${UpdateDownloader.canRequestPackageInstalls(context)}")
        if (installedCert != null) {
            appendLine("Installed Signing ID: ${installedCert.shortId}… (SHA-256 ${installedCert.sha256Hex})")
        } else {
            appendLine("Installed Signing ID: Unavailable")
        }
        if (latestInfo != null) {
            appendLine("Latest Remote Version: v${latestInfo.version} (${latestInfo.apkFileName.ifBlank { "no apk asset" }})")
            if (latestInfo.remoteSigningKeyId.isNotBlank()) {
                appendLine(
                    "Remote Signing ID: ${latestInfo.remoteSigningKeyId}… (source=${latestInfo.remoteSigningSource.ifBlank { "unknown" }})",
                )
            }
            if (latestInfo.expectedSha256.isNotBlank()) {
                appendLine("Expected APK SHA-256: ${latestInfo.expectedSha256}")
            }
        }
        appendLine()
        val list = _entries.value
        appendLine("=== UPDATER EVENTS (${list.size}) ===")
        list.forEach { e ->
            appendLine("${e.formattedTime} [${e.severity.label}] (${e.stage}) ${e.message}")
            if (e.details.isNotBlank()) {
                appendLine("    -> ${e.details}")
            }
        }
    }

    private fun persistLocked(appCtx: Context, list: List<UpdaterLogEntry>) {
        runCatching {
            val arr = JSONArray()
            list.forEach { entry ->
                arr.put(
                    JSONObject().apply {
                        put("id", entry.id)
                        put("ts", entry.timestampMs)
                        put("time", entry.formattedTime)
                        put("stage", entry.stage)
                        put("sev", entry.severity.name)
                        put("msg", entry.message)
                        put("det", entry.details)
                    },
                )
            }
            appCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ENTRIES_JSON, arr.toString())
                .apply()
        }
    }

    private fun parseJsonList(raw: String): List<UpdaterLogEntry> = runCatching {
        val arr = JSONArray(raw)
        val out = ArrayList<UpdaterLogEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val sevName = obj.optString("sev", Severity.INFO.name)
            val sev = Severity.entries.firstOrNull { it.name == sevName } ?: Severity.INFO
            out += UpdaterLogEntry(
                id = obj.optLong("id", i.toLong()),
                timestampMs = obj.optLong("ts", 0L),
                formattedTime = obj.optString("time", "--:--:--"),
                stage = obj.optString("stage", "Updater"),
                severity = sev,
                message = obj.optString("msg", ""),
                details = obj.optString("det", ""),
            )
        }
        out
    }.getOrDefault(emptyList())
}
