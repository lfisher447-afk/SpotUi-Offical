// File: app/src/main/java/com/music/spotui/engine/audio/EqualizerController.kt
package com.music.spotui.engine.audio

import android.content.Context
import com.music.spotui.audio.AudioEffectController
import com.music.spotui.data.preferences.getEqBass
import com.music.spotui.data.preferences.getEqHighMid
import com.music.spotui.data.preferences.getEqLowMid
import com.music.spotui.data.preferences.getEqTreble
import com.music.spotui.data.preferences.getEqVocal
import com.music.spotui.data.preferences.isEqEnabled
import com.music.spotui.data.preferences.setEqBass
import com.music.spotui.data.preferences.setEqEnabled
import com.music.spotui.data.preferences.setEqHighMid
import com.music.spotui.data.preferences.setEqLowMid
import com.music.spotui.data.preferences.setEqPreset
import com.music.spotui.data.preferences.setEqTreble
import com.music.spotui.data.preferences.setEqVocal
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

/** Coordinates five-band intent with the active Android audio session and persisted DSP prefs. */
@Singleton
class EqualizerController @Inject constructor() {
    private val mutableState = MutableStateFlow(EqualizerState())
    @Volatile private var boundContext: Context? = null

    /** Streams current five-band equalizer intent. */
    val state: StateFlow<EqualizerState> = mutableState.asStateFlow()

    /** Sets one band while keeping levels inside a safe +/-12 dB range and syncing live DSP. */
    fun setBand(index: Int, decibels: Float) {
        require(index in 0 until 5) { "Equalizer band must be between 0 and 4" }
        val clamped = decibels.coerceIn(-12F, 12F)
        mutableState.value = mutableState.value.copy(
            bandsDb = mutableState.value.bandsDb.toMutableList().also {
                it[index] = clamped
            },
        )
        boundContext?.let { ctx ->
            val sliderVal = ((clamped / 12f) * 50f + 50f).coerceIn(0f, 100f)
            when (index) {
                0 -> setEqBass(ctx, sliderVal)
                1 -> setEqLowMid(ctx, sliderVal)
                2 -> setEqVocal(ctx, sliderVal)
                3 -> setEqHighMid(ctx, sliderVal)
                4 -> setEqTreble(ctx, sliderVal)
            }
            setEqPreset(ctx, "Custom")
            AudioEffectController.applyPreferences(ctx)
        }
    }

    /** Enables or bypasses the equalizer without discarding the user's band values. */
    fun setEnabled(enabled: Boolean) {
        mutableState.value = mutableState.value.copy(isEnabled = enabled)
        boundContext?.let { ctx ->
            setEqEnabled(ctx, enabled)
            AudioEffectController.applyPreferences(ctx)
        }
    }

    /** Hydrates UI state from persisted SettingsPref and attaches platform effects to [audioSessionId]. */
    fun attach(context: Context, audioSessionId: Int) {
        val appCtx = context.applicationContext
        boundContext = appCtx
        mutableState.value = EqualizerState(
            bandsDb = listOf(
                sliderToDb(getEqBass(appCtx)),
                sliderToDb(getEqLowMid(appCtx)),
                sliderToDb(getEqVocal(appCtx)),
                sliderToDb(getEqHighMid(appCtx)),
                sliderToDb(getEqTreble(appCtx)),
            ),
            isEnabled = isEqEnabled(appCtx),
        )
        AudioEffectController.attach(appCtx, audioSessionId)
        Logger.debug("EqualizerController", "Attached equalizer to audio session")
    }

    /** Releases Android framework effects for the active session. */
    fun release() {
        AudioEffectController.release()
    }

    private fun sliderToDb(slider: Float): Float = ((slider.coerceIn(0f, 100f) - 50f) / 50f) * 12f
}
