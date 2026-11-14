package com.music.spotui.widget

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Collapses a burst of player events into a single Glance widget update.
 *
 * Trailing-edge debounce bounded by [maxWaitMs]: publishes [quietMs] after the last change, or
 * at most [maxWaitMs] after the burst began. Duplicate states matching the last published snapshot
 * are ignored cleanly.
 */
class WidgetUpdateDebouncer(
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val quietMs: Long = QUIET_MS,
    private val maxWaitMs: Long = MAX_WAIT_MS,
    private val publish: (NowPlayingWidgetState) -> Unit,
) {
    private var pending: NowPlayingWidgetState? = null
    private var lastPublished: NowPlayingWidgetState? = null
    private var burstStartedAt = 0L
    private var timer: Job? = null

    fun submit(state: NowPlayingWidgetState) {
        val now = clock()
        if (timer == null) burstStartedAt = now
        timer?.cancel()
        if (state == lastPublished) {
            pending = null
            timer = null
            return
        }
        pending = state
        val wait = minOf(quietMs, maxWaitMs - (now - burstStartedAt)).coerceAtLeast(0L)
        timer = scope.launch {
            delay(wait)
            timer = null
            val settled = pending ?: return@launch
            pending = null
            if (settled != lastPublished) {
                lastPublished = settled
                publish(settled)
            }
        }
    }

    fun reset() {
        timer?.cancel()
        timer = null
        pending = null
        lastPublished = null
    }

    companion object {
        const val QUIET_MS = 350L
        const val MAX_WAIT_MS = 1_500L
    }
}
