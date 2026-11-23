// File: app/src/main/java/com/music/spotui/util/Logger.kt
package com.music.spotui.util

import android.content.Context
import com.music.spotui.BuildConfig
import timber.log.Timber

/**
 * Centralized logging gateway forwarding into Timber (debug builds) and
 * DevConsoleManager / bounded app-private diagnostics (all builds).
 */
object Logger {
    fun initialize(context: Context) {
        if (BuildConfig.DEBUG && Timber.forest().isEmpty()) {
            Timber.plant(Timber.DebugTree())
        }
        DevConsoleManager.initialize(context.applicationContext)
        Timber.tag("SpotUI").d("Logger initialized for %s", context.packageName)
    }

    fun debug(tag: String, message: String) {
        Timber.tag(tag).d(message)
        DevConsoleManager.logDebug(tag, message)
    }

    fun info(tag: String, message: String) {
        Timber.tag(tag).i(message)
        DevConsoleManager.logInfo(tag, message)
    }

    fun warning(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable == null) Timber.tag(tag).w(message) else Timber.tag(tag).w(throwable, message)
        DevConsoleManager.log(DevConsoleManager.LogLevel.WARNING, tag, message, throwable)
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable == null) Timber.tag(tag).e(message) else Timber.tag(tag).e(throwable, message)
        DevConsoleManager.logError(tag, message, throwable)
    }
}
