package com.music.spotui.engine

import com.music.spotui.di.SongPlayer

/**
 * Version 1.5.1 - PlaybackSpeedController
 * Fine-grained playback pitch and speed modifier (0.5x to 2.5x) powered by ExoPlayer Sonic Audio Processor.
 */
object PlaybackSpeedController {
    
    fun setPlaybackSpeed(speed: Float, pitch: Float = 1.0f) {
        val boundedSpeed = speed.coerceIn(0.5f, 2.5f)
        val boundedPitch = pitch.coerceIn(0.5f, 2.0f)
        SongPlayer.setPlaybackParameters(boundedSpeed, boundedPitch)
    }

    fun getPlaybackSpeed(): Float {
        return SongPlayer.getPlaybackSpeed()
    }
}
