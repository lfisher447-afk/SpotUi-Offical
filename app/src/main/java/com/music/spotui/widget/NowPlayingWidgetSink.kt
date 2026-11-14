package com.music.spotui.widget

import android.content.Context
import com.music.spotui.util.AppDiagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Sink interface used by [com.music.spotui.ui.notification.PlaybackService] to push settled widget
 * state without coupling service callbacks directly to Glance WorkManager/RemoteViews overhead.
 */
interface NowPlayingWidgetSink {
    fun publish(state: NowPlayingWidgetState)
    fun serviceStopped()
}

/**
 * App-level singleton implementation owning an independent background scope that outlives
 * [com.music.spotui.ui.notification.PlaybackService] so `serviceStopped()` during `onDestroy()`
 * always finishes persisting and redrawing home-screen widgets.
 */
class GlanceWidgetSink(context: Context) : NowPlayingWidgetSink {

    private val appContext = context.applicationContext

    override fun publish(state: NowPlayingWidgetState) {
        sinkScope.launch {
            runCatching {
                NowPlayingWidgetStateStore.saveState(appContext, state)
                GlanceWidgetUpdater.refreshAll(appContext)
            }.onFailure { err ->
                AppDiagnostics.warning("GlanceWidgetSink", "Widget publish failed", err)
            }
        }
    }

    override fun serviceStopped() {
        sinkScope.launch {
            runCatching {
                NowPlayingWidgetStateStore.markStopped(appContext)
                GlanceWidgetUpdater.refreshAll(appContext)
            }.onFailure { err ->
                AppDiagnostics.warning("GlanceWidgetSink", "Widget stop refresh failed", err)
            }
        }
    }

    companion object {
        private val sinkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
