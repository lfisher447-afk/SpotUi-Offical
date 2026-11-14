package com.music.spotui.playback

import com.music.spotui.util.AppDiagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface MediaCommand {
    data object Play : MediaCommand
    data object Pause : MediaCommand
    data object PlayPause : MediaCommand
    data object Next : MediaCommand
    data object Previous : MediaCommand
    data class Seek(val positionMs: Long) : MediaCommand
    data object Stop : MediaCommand
}

/**
 * Hardware media-command debounce actor.
 *
 * Bluetooth headsets and car steering-wheel controls frequently emit rapid burst signals or
 * bounced AVRCP frames inside tens of milliseconds. Funnelling hardware-origin [MediaCommand]s
 * through this trailing-edge debounce actor coalesces bursts inside a 50–150ms window (default
 * 100ms) so only the final intent reaches playback transport.
 */
object MediaCommandDispatcher {

    private const val TAG = "MediaCommandDispatcher"

    const val MIN_DEBOUNCE_WINDOW_MS = 50L
    const val MAX_DEBOUNCE_WINDOW_MS = 150L
    const val DEFAULT_DEBOUNCE_WINDOW_MS = 100L

    private val mediaCommandChannel = Channel<MediaCommand>(Channel.CONFLATED)

    @Volatile
    private var debounceWindowMs = DEFAULT_DEBOUNCE_WINDOW_MS

    @Volatile
    private var consumer: Job? = null

    /**
     * Dispatch sink invoked with each settled command. Internal so JVM unit tests can observe
     * debounced dispatches deterministically using virtual time.
     */
    @Volatile
    internal var dispatchSink: (MediaCommand) -> Unit = {}

    private val fallbackScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Synchronized
    fun start(
        scope: CoroutineScope = fallbackScope,
        windowMs: Long = DEFAULT_DEBOUNCE_WINDOW_MS,
        sink: ((MediaCommand) -> Unit)? = null,
    ) {
        debounceWindowMs = windowMs.coerceIn(MIN_DEBOUNCE_WINDOW_MS, MAX_DEBOUNCE_WINDOW_MS)
        if (sink != null) {
            dispatchSink = sink
        }
        if (consumer?.isActive == true) return
        consumer = scope.launch { runDebounceLoop() }
    }

    @Synchronized
    fun stop() {
        consumer?.cancel()
        consumer = null
        while (mediaCommandChannel.tryReceive().isSuccess) {
            // Drain undelivered tail commands when session tears down.
        }
    }

    fun dispatchMediaCommand(command: MediaCommand) {
        mediaCommandChannel.trySend(command)
    }

    private suspend fun CoroutineScope.runDebounceLoop() {
        while (isActive) {
            var pending = mediaCommandChannel.receive()
            while (true) {
                val next = withTimeoutOrNull(debounceWindowMs) {
                    mediaCommandChannel.receive()
                } ?: break
                pending = next
            }
            runCatching { dispatchSink(pending) }
                .onFailure { t -> AppDiagnostics.warning(TAG, "Hardware media command sink failed", t) }
        }
    }
}
