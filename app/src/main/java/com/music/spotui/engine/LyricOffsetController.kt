package com.music.spotui.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Version 1.5.1 - LyricOffsetController
 * Manual timing adjustment widget letting users shift lyric playback forwards or backwards in 100ms steps.
 */
object LyricOffsetController {
    private val _offsetMs = MutableStateFlow(0L)
    val offsetMs: StateFlow<Long> = _offsetMs
    
    fun shiftForward() {
        _offsetMs.value += 100L
    }
    
    fun shiftBackward() {
        _offsetMs.value -= 100L
    }
    
    fun reset() {
        _offsetMs.value = 0L
    }
    
    fun getAdjustedPlaybackTime(actualPlaybackMs: Long): Long {
        return (actualPlaybackMs + _offsetMs.value).coerceAtLeast(0L)
    }
}
