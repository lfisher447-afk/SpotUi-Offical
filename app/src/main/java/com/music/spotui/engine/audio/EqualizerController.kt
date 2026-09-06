// File: app/src/main/java/com/music/spotui/engine/audio/EqualizerController.kt
package com.music.spotui.engine.audio

import android.content.Context
import com.music.spotui.audio.AudioEffectController
import com.music.spotui.util.Logger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Immutable five-band equalizer state in normalized decibels. */
data class EqualizerState(
    val bandsDb: List<Float> = List(5) { 0F },
    val isEnabled: Boolean = false,
)

/** Coordinates five-band intent with the active Android audio session. */
@Singleton
class EqualizerController @Inject constructor() {
    private val mutableState = MutableStateFlow(EqualizerState())

    /** Streams current five-band equalizer intent. */
    val state: StateFlow<EqualizerState> = mutableState.asStateFlow()

    /** Sets one band while keeping levels inside a safe +/-12 dB range. */
    fun setBand(index: Int, decibels: Float) {
        require(index in 0 until 5) { "Equalizer band must be between 0 and 4" }
        mutableState.value = mutableState.value.copy(
            bandsDb = mutableState.value.bandsDb.toMutableList().also {
                it[index] = decibels.coerceIn(-12F, 12F)
            },
        )
    }

    /** Enables or bypasses the equalizer without discarding the user's band values. */
    fun setEnabled(enabled: Boolean) {
        mutableState.value = mutableState.value.copy(isEnabled = enabled)
    }

    /** Attaches the existing platform effects controller to [audioSessionId]. */
    fun attach(context: Context, audioSessionId: Int) {
        AudioEffectController.attach(context, audioSessionId)
        Logger.debug("EqualizerController", "Attached equalizer to audio session")
    }

    /** Releases Android framework effects for the active session. */
    fun release() {
        AudioEffectController.release()
    }
}
