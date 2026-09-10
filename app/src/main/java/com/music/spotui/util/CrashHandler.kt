package com.music.spotui.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Robust Global UncaughtExceptionHandler for SpotUI.
 *
 * Captures all uncaught exceptions across all threads, formats a detailed crash report
 * including device specs, OS release, thread state, memory statistics, and recent
 * diagnostics breadcrumbs, and synchronously writes the crash dump to local files
 * with disk syncing before handing off to the system handler.
 */
object CrashHandler {
    private const val TAG = "CrashHandler"
    private const val CRASH_DIR_NAME = "crashes"
    private const val LATEST_CRASH_FILE_NAME = "latest_crash.txt"
    private const val MAX_RETAINED_CRASH_FILES = 20

    private val isInstalled = AtomicBoolean(false)
    private val isHandlingCrash = AtomicBoolean(false)
    private var defaultUncaughtExceptionHandler: Thread.UncaughtExceptionHandler? = null

    @Volatile
    private var appContext: Context? = null

    /**
     * Installs the global uncaught exception handler.
     * Can safely be invoked multiple times (e.g. in attachBaseContext and onCreate).
     */
    fun install(context: Context) {
        appContext = context.applicationContext

        if (isInstalled.compareAndSet(false, true)) {
            defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()

            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                handleUncaughtException(thread, throwable)
            }

            Log.i(TAG, "Global UncaughtExceptionHandler successfully installed.")
        }
    }

    private fun handleUncaughtException(thread: Thread, throwable: Throwable) {
        // Prevent recursive handler loops if file writing or logging itself encounters an issue
        if (!isHandlingCrash.compareAndSet(false, true)) {
            defaultUncaughtExceptionHandler?.uncaughtException(thread, throwable)
            return
        }

        try {
            val context = appContext
            val crashReport = buildCrashReport(context, thread, throwable)

            // Log directly to logcat
            Log.e(TAG, "FATAL CRASH DETECTED ON THREAD [${thread.name}]")
            Log.e(TAG, crashReport)

            // Persist report to disk synchronously
            if (context != null) {
                writeCrashReportToDisk(context, crashReport)
            }

            // Record into diagnostics & DevConsole engines if available
            runCatching {
                AppDiagnostics.record(
                    level = "FATAL",
                    tag = "Crash:${thread.name}",
                    message = "Uncaught exception: ${throwable.javaClass.name}: ${throwable.message}",
                    throwable = throwable
                )
            }
            runCatching {
                DevConsoleManager.log(
                    level = DevConsoleManager.LogLevel.ERROR,
                    tag = "FATAL_CRASH",
                    message = "Thread: ${thread.name} -> ${throwable.message}",
                    exception = throwable
                )
            }
        } catch (e: Throwable) {
            // Absolute failsafe
            Log.e(TAG, "Error while saving crash dump", e)
        } finally {
            // Pass to the system / previous handler to ensure standard Android crash handling
            defaultUncaughtExceptionHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Formats a complete, human-readable crash report.
     */
    fun buildCrashReport(context: Context?, thread: Thread, throwable: Throwable): String {
        val now = Date()
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(now)
        val readableFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(now)

        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        val stackTrace = sw.toString()

        val rootCause = getRootCause(throwable)

        return buildString {
            appendLine("================================================================================")
            appendLine("                         SPOTUI CRASH REPORT                                    ")
            appendLine("================================================================================")
            appendLine("Timestamp (ISO):     $isoFormat")
            appendLine("Timestamp (Local):   $readableFormat")
            appendLine()

            appendLine("----------------- APPLICATION INFO -----------------")
            if (context != null) {
                val packageInfo = runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0)
                }.getOrNull()
                appendLine("Package Name:        ${context.packageName}")
                appendLine("Version Name:        ${packageInfo?.versionName ?: "unknown"}")
                @Suppress("DEPRECATION")
                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo?.longVersionCode?.toString() ?: "unknown"
                } else {
                    packageInfo?.versionCode?.toString() ?: "unknown"
                }
                appendLine("Version Code:        $versionCode")
                appendLine("Process ID:          ${Process.myPid()}")
                appendLine("Process Name:        ${getProcessName(context)}")
            } else {
                appendLine("Application Context: Not available at time of crash")
            }
            appendLine()

            appendLine("----------------- DEVICE & OS INFO -----------------")
            appendLine("Manufacturer:        ${Build.MANUFACTURER}")
            appendLine("Model:               ${Build.MODEL}")
            appendLine("Brand:               ${Build.BRAND}")
            appendLine("Device:              ${Build.DEVICE}")
            appendLine("Board:               ${Build.BOARD}")
            appendLine("Hardware:            ${Build.HARDWARE}")
            appendLine("Product:             ${Build.PRODUCT}")
            appendLine("Android Release:     ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                appendLine("Security Patch:      ${Build.VERSION.SECURITY_PATCH}")
            }
            appendLine("Fingerprint:         ${Build.FINGERPRINT}")
            appendLine("Supported ABIs:      ${Build.SUPPORTED_ABIS.joinToString(", ")}")
            appendLine()

            appendLine("----------------- MEMORY STATUS -----------------")
            val runtime = Runtime.getRuntime()
            val maxMemoryMb = runtime.maxMemory() / (1024 * 1024)
            val totalMemoryMb = runtime.totalMemory() / (1024 * 1024)
            val freeMemoryMb = runtime.freeMemory() / (1024 * 1024)
            val usedMemoryMb = totalMemoryMb - freeMemoryMb
            appendLine("Heap Max:            $maxMemoryMb MB")
            appendLine("Heap Total:          $totalMemoryMb MB")
            appendLine("Heap Used:           $usedMemoryMb MB")
            appendLine("Heap Free:           $freeMemoryMb MB")

            if (context != null) {
                runCatching {
                    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    val memoryInfo = ActivityManager.MemoryInfo()
                    activityManager?.getMemoryInfo(memoryInfo)
                    val availMb = memoryInfo.availMem / (1024 * 1024)
                    val totalDeviceMb = memoryInfo.totalMem / (1024 * 1024)
                    appendLine("Device RAM Avail:    $availMb MB / $totalDeviceMb MB (Low Memory: ${memoryInfo.lowMemory})")
                }
            }
            appendLine()

            appendLine("----------------- THREAD INFO -----------------")
            appendLine("Thread Name:         ${thread.name}")
            appendLine("Thread ID:           ${thread.id}")
            appendLine("Thread Priority:     ${thread.priority}")
            appendLine("Thread State:        ${thread.state}")
            appendLine("Thread Group:        ${thread.threadGroup?.name ?: "none"}")
            appendLine("Is Daemon:           ${thread.isDaemon}")
            appendLine("Is Alive:            ${thread.isAlive}")
            appendLine()

            appendLine("----------------- EXCEPTION SUMMARY -----------------")
            appendLine("Exception Type:      ${throwable.javaClass.name}")
            appendLine("Exception Message:   ${throwable.message ?: "<no message>"}")
            if (rootCause !== throwable) {
                appendLine("Root Cause Type:     ${rootCause.javaClass.name}")
                appendLine("Root Cause Message:  ${rootCause.message ?: "<no message>"}")
            }
            appendLine()

            appendLine("----------------- FULL STACK TRACE -----------------")
            appendLine(stackTrace.trimEnd())
            appendLine()

            val suppressed = throwable.suppressed
            if (!suppressed.isNullOrEmpty()) {
                appendLine("----------------- SUPPRESSED EXCEPTIONS -----------------")
                for ((index, s) in suppressed.withIndex()) {
                    appendLine("[$index] ${s.javaClass.name}: ${s.message}")
                    val suppressedSw = StringWriter()
                    s.printStackTrace(PrintWriter(suppressedSw))
                    appendLine(suppressedSw.toString().trimEnd())
                    appendLine()
                }
            }

            if (context != null) {
                appendLine("----------------- RECENT DIAGNOSTIC LOGS -----------------")
                val recentLogs = runCatching { AppDiagnostics.readRecent(context, maxCharacters = 4000) }.getOrNull()
                if (!recentLogs.isNullOrBlank()) {
                    appendLine(recentLogs.trimEnd())
                } else {
                    appendLine("No preceding diagnostics logged.")
                }
                appendLine()
            }

            appendLine("================================================================================")
            appendLine("                              END OF REPORT                                     ")
            appendLine("================================================================================")
        }
    }

    private fun writeCrashReportToDisk(context: Context, crashReport: String) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())

        // 1. Write to specific timestamped crash file: filesDir/crashes/crash_<timestamp>.txt
        runCatching {
            val crashDir = File(context.filesDir, CRASH_DIR_NAME)
            if (!crashDir.exists()) {
                crashDir.mkdirs()
            }
            val crashFile = File(crashDir, "crash_$timestamp.txt")
            writeSynchronously(crashFile, crashReport)

            // Maintain rotation so crashes directory doesn't grow unbounded
            pruneOldCrashReports(crashDir)
        }.onFailure { e ->
            Log.e(TAG, "Failed to write timestamped crash file", e)
        }

        // 2. Write to latest crash file: filesDir/latest_crash.txt (overwrites previous)
        runCatching {
            val latestFile = File(context.filesDir, LATEST_CRASH_FILE_NAME)
            writeSynchronously(latestFile, crashReport)
        }.onFailure { e ->
            Log.e(TAG, "Failed to write latest_crash.txt", e)
        }

        // 3. Fallback to cacheDir if filesDir had any issue
        runCatching {
            val cacheFallbackFile = File(context.cacheDir, "crash_fallback.txt")
            writeSynchronously(cacheFallbackFile, crashReport)
        }.onFailure { /* ignore cache fallback error */ }
    }

    private fun writeSynchronously(file: File, content: String) {
        file.parentFile?.mkdirs()
        FileOutputStream(file, false).use { fos ->
            fos.write(content.toByteArray(Charsets.UTF_8))
            fos.flush()
            // Force write through OS buffers directly to physical storage
            fos.fd.sync()
        }
    }

    private fun pruneOldCrashReports(crashDir: File) {
        val files = crashDir.listFiles { _, name -> name.startsWith("crash_") && name.endsWith(".txt") } ?: return
        if (files.size > MAX_RETAINED_CRASH_FILES) {
            files.sortBy { it.lastModified() }
            val toDeleteCount = files.size - MAX_RETAINED_CRASH_FILES
            for (i in 0 until toDeleteCount) {
                runCatching { files[i].delete() }
            }
        }
    }

    private fun getRootCause(throwable: Throwable): Throwable {
        var current = throwable
        while (current.cause != null && current.cause !== current) {
            current = current.cause!!
        }
        return current
    }

    private fun getProcessName(context: Context): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            android.app.Application.getProcessName()
        } else {
            val pid = Process.myPid()
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            activityManager?.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName ?: "unknown"
        }
    }

    /**
     * Reads the latest crash report content, if any exists.
     */
    fun getLatestCrashReport(context: Context): String? {
        val latestFile = File(context.filesDir, LATEST_CRASH_FILE_NAME)
        if (latestFile.exists() && latestFile.length() > 0) {
            return runCatching { latestFile.readText() }.getOrNull()
        }
        val crashDir = File(context.filesDir, CRASH_DIR_NAME)
        val latestFromDir = crashDir.listFiles()?.maxByOrNull { it.lastModified() }
        if (latestFromDir != null && latestFromDir.exists() && latestFromDir.length() > 0) {
            return runCatching { latestFromDir.readText() }.getOrNull()
        }
        return null
    }

    /**
     * Checks if a crash report is present in storage.
     */
    fun hasCrashReport(context: Context): Boolean {
        val latestFile = File(context.filesDir, LATEST_CRASH_FILE_NAME)
        if (latestFile.exists() && latestFile.length() > 0) return true
        val crashDir = File(context.filesDir, CRASH_DIR_NAME)
        return (crashDir.listFiles()?.isNotEmpty() == true)
    }

    /**
     * Returns all historical crash report files.
     */
    fun getAllCrashReportFiles(context: Context): List<File> {
        val crashDir = File(context.filesDir, CRASH_DIR_NAME)
        return crashDir.listFiles { _, name -> name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() }
            ?.toList()
            ?: emptyList()
    }

    /**
     * Clears all crash reports.
     */
    fun clearAllCrashReports(context: Context) {
        runCatching { File(context.filesDir, LATEST_CRASH_FILE_NAME).delete() }
        runCatching { File(context.cacheDir, "crash_fallback.txt").delete() }
        val crashDir = File(context.filesDir, CRASH_DIR_NAME)
        crashDir.listFiles()?.forEach { file -> runCatching { file.delete() } }
    }
}
