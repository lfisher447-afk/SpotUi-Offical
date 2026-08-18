package com.music.spotui.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Version 1.5.1 - BitrateOverrideConfig
 * Forces maximum stream resolution configuration on all playback requests regardless of network conditions.
 */
object BitrateOverrideConfig {
    private val _forceMaxBitrate = MutableStateFlow(true)
    val forceMaxBitrate: StateFlow<Boolean> = _forceMaxBitrate
    
    fun setForceMaxBitrate(force: Boolean) {
        _forceMaxBitrate.value = force
    }
    
    fun getQualityString(): String {
        return if (_forceMaxBitrate.value) "320kbps (Max)" else "Auto (Adaptive)"
    }
}
