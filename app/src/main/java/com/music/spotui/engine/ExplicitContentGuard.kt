package com.music.spotui.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Version 1.5.1 - ExplicitContentGuard
 * Automatic filter toggled by user preferences to skip explicit lyrics or clean-version alternatives.
 */
object ExplicitContentGuard {
    private val _blockExplicit = MutableStateFlow(false)
    val blockExplicit: StateFlow<Boolean> = _blockExplicit
    
    fun setBlockExplicit(block: Boolean) {
        _blockExplicit.value = block
    }
    
    fun shouldBlock(isExplicit: Boolean): Boolean {
        return _blockExplicit.value && isExplicit
    }
}
