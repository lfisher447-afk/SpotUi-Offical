// File: app/src/main/java/com/music/spotui/util/Logger.kt
package com.music.spotui.util

import android.content.Context
import com.music.spotui.BuildConfig
import timber.log.Timber

/** Centralized logging policy for release-safe diagnostics. */
object Logger {
    /** Installs a debug tree when diagnostics are enabled for this build. */
    fun initialize(context: Context) {
        if (BuildConfig.DEBUG && Timber.forest().isEmpty()) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.tag("SpotUI").d("Logger initialized for %s", context.packageName)
    }

    /** Writes a debug message under [tag]. */
    fun debug(tag: String, message: String) {
        Timber.tag(tag).d(message)
    }

    /** Writes an informational message under [tag]. */
    fun info(tag: String, message: String) {
        Timber.tag(tag).i(message)
    }

    /** Writes a warning and its optional [throwable] under [tag]. */
    fun warning(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable == null) Timber.tag(tag).w(message) else Timber.tag(tag).w(throwable, message)
    }

    /** Writes an error and its [throwable] under [tag]. */
    fun error(tag: String, message: String, throwable: Throwable) {
        Timber.tag(tag).e(throwable, message)
    }
}
