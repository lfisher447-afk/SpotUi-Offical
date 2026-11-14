package com.music.spotui.widget

import android.view.KeyEvent

/**
 * One widget button's media-button event: the key it sends, whether its action must start the
 * service in the foreground, and whether the service skips one song before playing.
 */
data class WidgetTransport(
    val keyCode: Int,
    val foreground: Boolean,
    val skipFirst: Boolean = false,
)

/** The widget's two primary transport buttons for one state. */
data class WidgetButtons(
    val showsPause: Boolean,
    val playPause: WidgetTransport,
    val next: WidgetTransport,
)

/**
 * Pure mapping from widget state + service liveness to transport actions.
 *
 * Uses explicit KEYCODE_MEDIA_PLAY / KEYCODE_MEDIA_PAUSE keys instead of PLAY_PAUSE so an
 * out-of-date widget never toggles into the opposite state of what the user tapped.
 * When stopped/paused, Next starts PlaybackService in the foreground with [WidgetTransport.skipFirst]
 * so it skips ahead and immediately starts playback (posting the foreground notification in time).
 */
object WidgetControls {

    fun of(state: NowPlayingWidgetState?, serviceRunning: Boolean): WidgetButtons? {
        if (state == null || !state.hasTrack) return null
        return if (state.isPlaying && serviceRunning) {
            WidgetButtons(
                showsPause = true,
                playPause = WidgetTransport(KeyEvent.KEYCODE_MEDIA_PAUSE, foreground = false),
                next = WidgetTransport(KeyEvent.KEYCODE_MEDIA_NEXT, foreground = false),
            )
        } else {
            WidgetButtons(
                showsPause = false,
                playPause = WidgetTransport(KeyEvent.KEYCODE_MEDIA_PLAY, foreground = true),
                next = WidgetTransport(KeyEvent.KEYCODE_MEDIA_PLAY, foreground = true, skipFirst = true),
            )
        }
    }
}
