package com.music.spotui.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Version 1.5.1 - CrossfadeManager
 * Controls dynamic track-overlap crossfading duration for smooth song transitions.
 */
object CrossfadeManager {
    
    // Configurable crossfade duration (0s to 12s)
    private val _crossfadeDurationSeconds = MutableStateFlow(0)
    val crossfadeDurationSeconds: StateFlow<Int> = _crossfadeDurationSeconds
    
    fun setCrossfadeDuration(seconds: Int) {
        val bounded = seconds.coerceIn(0, 12)
        _crossfadeDurationSeconds.value = bounded
    }
    
    fun getCrossfadeDurationMs(): Long {
        return _crossfadeDurationSeconds.value.toLong() * 1000L
    }
    
    fun isCrossfadeEnabled(): Boolean {
        return _crossfadeDurationSeconds.value > 0
    }
}
