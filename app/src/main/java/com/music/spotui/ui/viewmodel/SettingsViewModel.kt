// File: app/src/main/java/com/music/spotui/ui/viewmodel/SettingsViewModel.kt
package com.music.spotui.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.spotui.data.preferences.SettingsPreferences
import com.music.spotui.engine.stream.StreamQuality
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Exposes persisted clean-layer preferences as StateFlow values. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsPreferences: SettingsPreferences,
) : ViewModel() {
    /** Streams the selected stream-quality tier. */
    val streamQuality: StateFlow<StreamQuality> = settingsPreferences.observeStreamQuality()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settingsPreferences.streamQuality())

    /** Persists [quality] as the user's quality preference. */
    fun setStreamQuality(quality: StreamQuality) = settingsPreferences.setStreamQuality(quality)

    /** Returns whether explicit tracks should be displayed and played. */
    fun isExplicitContentAllowed(): Boolean = settingsPreferences.isExplicitContentAllowed()

    /** Persists whether explicit tracks may be displayed and played. */
    fun setExplicitContentAllowed(allowed: Boolean) = settingsPreferences.setExplicitContentAllowed(allowed)
}
