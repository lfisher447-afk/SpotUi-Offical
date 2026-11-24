package com.music.spotui.accessibility

import android.content.Context
import android.os.Bundle
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Provides the required [LifecycleOwner], [SavedStateRegistryOwner], and [ViewModelStoreOwner]
 * trees so Jetpack Compose [ComposeView] renders and animates cleanly inside an
 * [android.accessibilityservice.AccessibilityService] WindowManager overlay.
 */
internal class OverlayLifecycleOwner :
    LifecycleOwner,
    SavedStateRegistryOwner,
    ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    private var created = false

    fun onAttach() {
        if (!created) {
            savedStateController.performRestore(null)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            created = true
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onDetach() {
        if (!created) return
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        }
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        created = false
    }

    fun performSave(outState: Bundle) {
        savedStateController.performSave(outState)
    }

    fun attachTo(view: android.view.View) {
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
    }
}

/**
 * Root container for WindowManager Compose overlays that intercepts [MotionEvent.ACTION_OUTSIDE]
 * cleanly without swallowing internal Compose touch, drag, or scroll gestures.
 */
internal class OverlayTouchContainer(
    context: Context,
    private val onOutsideTouch: (() -> Unit)? = null,
) : FrameLayout(context) {

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_OUTSIDE) {
            onOutsideTouch?.invoke()
            return false
        }
        return super.dispatchTouchEvent(ev)
    }
}
