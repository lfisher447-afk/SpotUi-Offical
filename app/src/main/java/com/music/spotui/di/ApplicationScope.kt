// File: app/src/main/java/com/music/spotui/di/ApplicationScope.kt
package com.music.spotui.di

import javax.inject.Qualifier

/** Qualifies a coroutine scope that lives for the lifetime of the process. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
