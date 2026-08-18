package com.music.spotui.util

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bounded app-private diagnostics for support and crash investigation.
 *
 * Records operational events only; it deliberately does not write access tokens,
 * stream URLs, cookies, or playlist contents. The log remains in app-private
 * storage and can be exported only through an explicit user action.
 */
object AppDiagnostics {
    private const val TAG = "AppDiagnostics"
    private const val LOG_FILE_NAME = "spotui-diagnostics.log"
    private const val MAX_LOG_BYTES = 512 * 1024L
    private const val MAX_STACK_CHARS = 12_000

    private val initialized = AtomicBoolean(false)
    private val writeLock = Any()
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)

    @Volatile
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (!initialized.compareAndSet(false, true)) return

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            record(
                level = "FATAL",
                tag = thread.name,
                message = "Uncaught exception on ${thread.name}",
                throwable = throwable,
            )
            previousHandler?.uncaughtException(thread, throwable)
        }

        record("INFO", TAG, buildLaunchMessage(context))
    }

    fun info(tag: String, message: String) = record("INFO", tag, message)

    fun warning(tag: String, message: String, throwable: Throwable? = null) =
        record("WARN", tag, message, throwable)

    fun error(tag: String, message: String, throwable: Throwable? = null) =
        record("ERROR", tag, message, throwable)

    fun record(level: String, tag: String, message: String, throwable: Throwable? = null) {
        val safeTag = tag.take(80).replace(Regex("[\\r\\n]"), " ")
        val safeMessage = sanitize(message)
        val throwableText = throwable?.stackTraceToString()?.take(MAX_STACK_CHARS)
        val line = buildString {
            append(timestampFormat.format(Date()))
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
            "WARN" -> Log.w(TAG, "[$safeTag] $safeMessage", throwable)
            else -> Log.i(TAG, "[$safeTag] $safeMessage")
        }

        val context = appContext ?: return
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

    fun logFile(context: Context): File = File(context.filesDir, "diagnostics/$LOG_FILE_NAME")

    fun readRecent(context: Context, maxCharacters: Int = 32_000): String = runCatching {
        val file = logFile(context)
        if (!file.exists()) "No diagnostic entries recorded."
        else file.readText().takeLast(maxCharacters.coerceIn(1_000, 256_000))
    }.getOrElse { "Unable to read diagnostics: ${it.javaClass.simpleName}" }

    fun clear(context: Context) {
        synchronized(writeLock) {
            runCatching { logFile(context).delete() }
        }
        info(TAG, "Diagnostics cleared by user")
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
