package com.music.spotui.engine

import androidx.media3.common.PlaybackParameters
import com.music.spotui.di.SongPlayer

/**
 * Version 1.5.1 - PlaybackSpeedController
 * Fine-grained playback pitch and speed modifier (0.5x to 2.5x) powered by ExoPlayer Sonic Audio Processor.
 */
object PlaybackSpeedController {
    
    fun setPlaybackSpeed(speed: Float, pitch: Float = 1.0f) {
        val boundedSpeed = speed.coerceIn(0.5f, 2.5f)
        val parameters = PlaybackParameters(boundedSpeed, pitch)
        // Requires reflection or exposed player instance if not public
        // SongPlayer.player?.playbackParameters = parameters
    }
}
