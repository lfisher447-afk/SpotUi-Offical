package com.music.spotui.audio

import android.content.Context
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import com.music.spotui.data.preferences.getEqBass
import com.music.spotui.data.preferences.getAudioNormalizerGainMb
import com.music.spotui.data.preferences.getEqTreble
import com.music.spotui.data.preferences.getEqLowMid
import com.music.spotui.data.preferences.getEqHighMid
import com.music.spotui.data.preferences.getEqVocal
import com.music.spotui.data.preferences.getSpatialProfile
import com.music.spotui.data.preferences.getSpatialStrength
import com.music.spotui.data.preferences.isAudioNormalizerEnabled
import com.music.spotui.data.preferences.isEqSpatialEnabled
import com.music.spotui.data.preferences.SpatialProfile
import com.music.spotui.util.AppDiagnostics
import kotlin.math.roundToInt

/**
 * Owns Android framework effects for the current Media3 audio session.
 *
 * Effects are optional: devices can omit individual implementations, and every
 * initialization/update path is guarded so unsupported effects never interrupt
 * playback. The user-facing three controls map to the device's available EQ bands.
 */
object AudioEffectController {
    private const val TAG = "AudioEffects"

    private val lock = Any()
    private var activeSessionId = 0
    private var equalizer: Equalizer? = null
    private var presetReverb: PresetReverb? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    data class CapabilityReport(
        val sessionId: Int,
        val equalizerAvailable: Boolean,
        val hardwareSpatialAvailable: Boolean,
        val spatialStrengthSupported: Boolean,
        val roomAvailable: Boolean,
        val loudnessAvailable: Boolean,
    )

    @Volatile private var latestCapabilities = CapabilityReport(0, false, false, false, false, false)

    fun capabilityReport(): CapabilityReport = latestCapabilities

    fun attach(context: Context, audioSessionId: Int) {
        if (audioSessionId <= 0) return
        synchronized(lock) {
            if (activeSessionId == audioSessionId && equalizer != null) {
                applyPreferencesLocked(context)
                return
            }
            releaseLocked()
            activeSessionId = audioSessionId
            equalizer = runCatching { Equalizer(0, audioSessionId) }
                .onFailure { AppDiagnostics.warning(TAG, "Equalizer unavailable for this audio session", it) }
                .getOrNull()
            presetReverb = runCatching { PresetReverb(0, audioSessionId) }
                .onFailure { AppDiagnostics.info(TAG, "Spatial room effect is unavailable on this device") }
                .getOrNull()
            loudnessEnhancer = runCatching { LoudnessEnhancer(audioSessionId) }
                .onFailure { AppDiagnostics.info(TAG, "Loudness effect is unavailable on this device") }
                .getOrNull()
            applyPreferencesLocked(context)
            latestCapabilities = CapabilityReport(
                sessionId = audioSessionId,
                equalizerAvailable = equalizer != null,
                // Spatial widening is implemented in Spotui's Media3 PCM processor. Avoid
                // Android's deprecated session Virtualizer so all devices use the same audible path.
                hardwareSpatialAvailable = false,
                spatialStrengthSupported = false,
                roomAvailable = presetReverb != null,
                loudnessAvailable = loudnessEnhancer != null,
            )
            AppDiagnostics.info(
                TAG,
                "Attached effects to audio session $audioSessionId (hardwareSpatial=${latestCapabilities.hardwareSpatialAvailable}, strength=${latestCapabilities.spatialStrengthSupported})",
            )
        }
    }

    fun applyPreferences(context: Context) {
        synchronized(lock) { applyPreferencesLocked(context) }
    }

    fun release() {
        synchronized(lock) { releaseLocked() }
    }

    private fun applyPreferencesLocked(context: Context) {
        val eq = equalizer
        if (eq != null) {
            runCatching {
                val range = eq.bandLevelRange
                val min = range[0].toInt()
                val max = range[1].toInt()
                val bass = normalized(getEqBass(context))
                val lowMid = normalized(getEqLowMid(context))
                val vocal = normalized(getEqVocal(context))
                val highMid = normalized(getEqHighMid(context))
                val treble = normalized(getEqTreble(context))
                for (band in 0 until eq.numberOfBands) {
                    val frequencyHz = eq.getCenterFreq(band.toShort()) / 1_000
                    val normalizedGain = when {
                        frequencyHz < 180 -> bass
                        frequencyHz < 650 -> lowMid
                        frequencyHz < 2_300 -> vocal
                        frequencyHz < 6_000 -> highMid
                        else -> treble
                    }
                    eq.setBandLevel(band.toShort(), levelFor(normalizedGain, min, max).toShort())
                }
                eq.enabled = true
            }.onFailure { AppDiagnostics.warning(TAG, "Could not apply equalizer bands", it) }
        }

        val spatialEnabled = isEqSpatialEnabled(context)
        val spatialProfile = getSpatialProfile(context)
        presetReverb?.let { effect ->
            runCatching {
                effect.preset = reverbPresetFor(spatialProfile)
                effect.enabled = spatialEnabled
            }.onFailure { AppDiagnostics.warning(TAG, "Could not apply spatial room profile", it) }
        }

        loudnessEnhancer?.let { effect ->
            runCatching {
                effect.setTargetGain(getAudioNormalizerGainMb(context))
                effect.enabled = isAudioNormalizerEnabled(context)
            }.onFailure { AppDiagnostics.warning(TAG, "Could not apply loudness setting", it) }
        }
    }

    private fun releaseLocked() {
        listOf(equalizer, presetReverb, loudnessEnhancer).forEach { effect ->
            runCatching {
                effect?.enabled = false
                effect?.release()
            }
        }
        equalizer = null
        presetReverb = null
        loudnessEnhancer = null
        activeSessionId = 0
        latestCapabilities = CapabilityReport(0, false, false, false, false, false)
    }

    private fun reverbPresetFor(profile: SpatialProfile): Short = when (profile) {
        SpatialProfile.STUDIO -> PresetReverb.PRESET_SMALLROOM
        SpatialProfile.WIDE -> PresetReverb.PRESET_MEDIUMHALL
        SpatialProfile.IMMERSIVE -> PresetReverb.PRESET_LARGEHALL
        SpatialProfile.CINEMA -> PresetReverb.PRESET_LARGEROOM
    }

    private fun normalized(value: Float): Float = ((value.coerceIn(0f, 100f) - 50f) / 50f)

    private fun levelFor(normalized: Float, min: Int, max: Int): Int = when {
        normalized >= 0f -> (normalized * max).roundToInt()
        else -> (normalized * -min).roundToInt()
    }.coerceIn(min, max)
}
