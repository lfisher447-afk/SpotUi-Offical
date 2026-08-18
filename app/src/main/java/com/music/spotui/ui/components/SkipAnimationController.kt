package com.music.spotui.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Version 1.5.1 - SkipAnimationController
 * Handles zero-delay Compose UI transitions when auto-skipping blacklisted or ad tracks.
 */
class SkipAnimationController(private val scope: CoroutineScope) {
    val alphaAnim = Animatable(1f)
    val offsetXAnim = Animatable(0f)
    
    fun performZeroDelaySkipAnimation(onAnimationEnd: () -> Unit) {
        scope.launch {
            // Fast slide out
            launch { alphaAnim.animateTo(0f, animationSpec = tween(150)) }
            launch { offsetXAnim.animateTo(-300f, animationSpec = tween(150)) }.join()
            
            onAnimationEnd()
            
            // Fast slide in
            offsetXAnim.snapTo(300f)
            launch { alphaAnim.animateTo(1f, animationSpec = tween(150)) }
            launch { offsetXAnim.animateTo(0f, animationSpec = tween(150)) }
        }
    }
}
