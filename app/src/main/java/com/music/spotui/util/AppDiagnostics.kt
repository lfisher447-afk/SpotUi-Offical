package com.music.spotui.util

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bounded app-private diagnostics for support and crash investigation.
 *
 * Records operational events only; it deliberately does not write access tokens,
 * stream URLs, cookies, or playlist contents. Every recorded entry is also mirrored
 * cleanly into [DevConsoleManager] for live in-app inspection without recursion.
 */
object AppDiagnostics {
    private const val TAG = "AppDiagnostics"
    private const val LOG_FILE_NAME = "spotui-diagnostics.log"
    private const val MAX_LOG_BYTES = 512 * 1024L
    private const val MAX_STACK_CHARS = 12_000

    private val initialized = AtomicBoolean(false)
    private val writeLock = Any()
    private val ioExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "SpotuiDiagnosticsIO").apply { isDaemon = true }
    }

    private fun formatNow(): String {
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date())
        }.getOrDefault("")
    }

    @Volatile
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (!initialized.compareAndSet(false, true)) return

        CrashHandler.install(context)
        record("INFO", TAG, buildLaunchMessage(context))
    }

    fun info(tag: String, message: String) = record("INFO", tag, message)

    fun debug(tag: String, message: String) = record("DEBUG", tag, message)

    fun warning(tag: String, message: String, throwable: Throwable? = null) =
        record("WARN", tag, message, throwable)

    fun error(tag: String, message: String, throwable: Throwable? = null) =
        record("ERROR", tag, message, throwable)

    fun record(
        level: String,
        tag: String,
        message: String,
        throwable: Throwable? = null,
        mirrorToConsole: Boolean = true,
    ) {
        val safeTag = tag.take(80).replace(Regex("[\\r\\n]"), " ")
        val safeMessage = sanitize(message)
        val throwableText = throwable?.stackTraceToString()?.take(MAX_STACK_CHARS)
        val line = buildString {
            append(formatNow())
            append(' ')
            append(level)
            append(" [")
            append(safeTag)
            append("] ")
            append(safeMessage)
            append('\n')
            if (!throwableText.isNullOrBlank()) {
                append(throwableText)
                if (!throwableText.endsWith('\n')) append('\n')
            }
        }

        when (level) {
            "ERROR", "FATAL" -> Log.e(TAG, "[$safeTag] $safeMessage", throwable)
            "WARN", "WARNING" -> Log.w(TAG, "[$safeTag] $safeMessage", throwable)
            "DEBUG" -> Log.d(TAG, "[$safeTag] $safeMessage")
            else -> Log.i(TAG, "[$safeTag] $safeMessage")
        }

        if (mirrorToConsole) {
            runCatching {
                DevConsoleManager.appendExternalLog(level, safeTag, safeMessage, throwable)
            }
        }

        val context = appContext ?: return
        runCatching {
            ioExecutor.execute {
                synchronized(writeLock) {
                    runCatching {
                        val file = logFile(context)
                        file.parentFile?.mkdirs()
                        if (file.exists() && file.length() > MAX_LOG_BYTES) {
                            val retained = file.readText().takeLast((MAX_LOG_BYTES / 2).toInt())
                            file.writeText("--- SpotUI diagnostic log rotated ---\n$retained")
                        }
                        file.appendText(line)
                    }.onFailure {
                        Log.w(TAG, "Unable to persist diagnostics", it)
                    }
                }
            }
        }
    }

    fun logFile(context: Context): File = File(context.filesDir, "diagnostics/$LOG_FILE_NAME")

    fun logFileSizeBytes(context: Context): Long = runCatching {
        val f = logFile(context)
        if (f.exists()) f.length() else 0L
    }.getOrDefault(0L)

    fun readRecent(context: Context, maxCharacters: Int = 32_000): String = runCatching {
        val file = logFile(context)
        if (!file.exists()) "No diagnostic entries recorded."
        else file.readText().takeLast(maxCharacters.coerceIn(1_000, 256_000))
    }.getOrElse { "Unable to read diagnostics: ${it.javaClass.simpleName}" }

    fun writeShareExportFile(context: Context, content: String): File? = runCatching {
        val dir = File(context.cacheDir, "logs").apply { mkdirs() }
        // Retain at most the 2 newest temporary log exports.
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(2)?.forEach { it.delete() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        File(dir, "spotui-diagnostics-$stamp.txt").apply {
            writeText(content)
        }
    }.getOrNull()

    fun clear(context: Context) {
        val appCtx = context.applicationContext
        runCatching {
            ioExecutor.execute {
                synchronized(writeLock) {
                    runCatching { logFile(appCtx).delete() }
                }
            }
        }
        record("INFO", TAG, "Diagnostics cleared by user", mirrorToConsole = false)
    }

    private fun buildLaunchMessage(context: Context): String {
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        val version = packageInfo?.versionName ?: "unknown"
        return "Launch: version=$version sdk=${Build.VERSION.SDK_INT} device=${Build.MANUFACTURER.take(32)} ${Build.MODEL.take(48)}"
    }

    private fun sanitize(value: String): String = value
        .replace(Regex("https?://\\S+"), "[redacted-url]")
        .replace(Regex("(?i)(token|cookie|authorization|bearer)\\s*[:=]\\s*[^\\s,]+"), "$1=[redacted]")
        .replace(Regex("[\\r\\n]+"), " ")
        .take(2_000)
}
