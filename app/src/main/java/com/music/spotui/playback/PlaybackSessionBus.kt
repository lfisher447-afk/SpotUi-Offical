package com.music.spotui.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Same-process handshake between [com.music.spotui.ui.notification.PlaybackService] and
 * [com.music.spotui.ui.notification.MediaSessionController] for the service's idle self-stop.
 *
 * The service flips [sessionAlive] true in `onCreate` and false just before it stops itself
 * (and again in `onDestroy`, idempotently, for every other teardown path). Controllers react:
 * on `false` they release their `MediaController` (the binding that would otherwise pin a stopped
 * service alive forever), and on `true` they reconnect so UI/history state observation continues
 * working even for playback started outside the activity (media button, Bluetooth, widget, Auto).
 */
object PlaybackSessionBus {

    private val _sessionAlive = MutableStateFlow(false)

    /** True while [com.music.spotui.ui.notification.PlaybackService] owns a live MediaSession. */
    val sessionAlive: StateFlow<Boolean> = _sessionAlive.asStateFlow()

    /** Called by PlaybackService in onCreate() once the session is active. */
    fun onServiceCreated() {
        _sessionAlive.value = true
    }

    /** Called idempotently before idle stopSelf() and in onDestroy(). */
    fun onServiceStopping() {
        _sessionAlive.value = false
    }
}
