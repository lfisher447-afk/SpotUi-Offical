package com.music.spotui.audio

import android.content.Context
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import com.music.spotui.data.preferences.SpatialProfile
import com.music.spotui.data.preferences.getAudioNormalizerGainMb
import com.music.spotui.data.preferences.getEqBass
import com.music.spotui.data.preferences.getEqHighMid
import com.music.spotui.data.preferences.getEqLowMid
import com.music.spotui.data.preferences.getEqTreble
import com.music.spotui.data.preferences.getEqVocal
import com.music.spotui.data.preferences.getSpatialProfile
import com.music.spotui.data.preferences.getSpatialStrength
import com.music.spotui.data.preferences.isAudioNormalizerEnabled
import com.music.spotui.data.preferences.isEqSpatialEnabled
import com.music.spotui.util.AppDiagnostics
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Owns and orchestrates Android framework audio effects for the current Media3 audio session.
 *
 * Designed for low-latency, zero-jank audio processing:
 * - Queries hardware capabilities once on session attachment to eliminate Binder IPC roundtrips.
 * - Utilizes logarithmic frequency interpolation across any arbitrary device band layout.
 * - Employs value-diffing to avoid audio buffer flushes and popping on vendor audio HALs.
 */
object AudioEffectController {
    private const val TAG = "AudioEffects"

    // Parametric frequency anchor points (Hz) matching standard studio EQ curves
    private const val ANCHOR_BASS_HZ = 60.0
    private const val ANCHOR_LOW_MID_HZ = 250.0
    private const val ANCHOR_VOCAL_HZ = 1000.0
    private const val ANCHOR_HIGH_MID_HZ = 4000.0
    private const val ANCHOR_TREBLE_HZ = 14000.0

    private val lock = Any()
    private var activeSessionId = 0

    // Cached hardware wrappers and state tracking
    private var cachedEqualizer: CachedEqualizer? = null
    private var presetReverb: PresetReverb? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    // Effect state tracking to prevent redundant IPC writes
    private var lastReverbPreset: Short = -1
    private var lastReverbEnabled: Boolean? = null
    private var lastLoudnessGainMb: Int = -1
    private var lastLoudnessEnabled: Boolean? = null

    data class CapabilityReport(
        val sessionId: Int,
        val equalizerAvailable: Boolean,
        val hardwareSpatialAvailable: Boolean,
        val spatialStrengthSupported: Boolean,
        val roomAvailable: Boolean,
        val loudnessAvailable: Boolean,
        val equalizerBands: Int = 0,
        val minLevelMb: Int = 0,
        val maxLevelMb: Int = 0,
    )

    @Volatile
    private var latestCapabilities = CapabilityReport(
        sessionId = 0,
        equalizerAvailable = false,
        hardwareSpatialAvailable = false,
        spatialStrengthSupported = false,
        roomAvailable = false,
        loudnessAvailable = false,
    )

    fun capabilityReport(): CapabilityReport = latestCapabilities

    /**
     * Checks if effects are currently attached to an active audio session.
     */
    fun isAttached(): Boolean = synchronized(lock) { isAttachedLocked() }

    /**
     * Returns the currently active audio session ID, or 0 if unattached.
     */
    fun getActiveSessionId(): Int = synchronized(lock) { activeSessionId }

    /**
     * Retrieves the device equalizer center frequencies in Hz, or empty if unsupported.
     */
    fun getBandFrequenciesHz(): IntArray = synchronized(lock) {
        cachedEqualizer?.centerFreqsHz?.clone() ?: intArrayOf()
    }

    /**
     * Attaches audio effects to the specified [audioSessionId].
     * If the session is unchanged and already attached, existing preferences are reapplied.
     */
    fun attach(context: Context, audioSessionId: Int) {
        if (audioSessionId <= 0) {
            AppDiagnostics.warning(TAG, "Ignoring attach with invalid audioSessionId: $audioSessionId")
            return
        }

        synchronized(lock) {
            if (activeSessionId == audioSessionId && isAttachedLocked()) {
                applyPreferencesLocked(context)
                return
            }

            releaseLocked()
            activeSessionId = audioSessionId

            // 1. Equalizer setup with hardware capability caching
            cachedEqualizer = runCatching {
                val eq = Equalizer(0, audioSessionId)
                CachedEqualizer(eq)
            }.onFailure {
                AppDiagnostics.warning(TAG, "Equalizer unavailable for audio session $audioSessionId", it)
            }.getOrNull()

            // 2. Preset Reverb (Room / Spatial Acoustics)
            presetReverb = runCatching {
                PresetReverb(0, audioSessionId)
            }.onFailure {
                AppDiagnostics.info(TAG, "Spatial room effect is unavailable on this device")
            }.getOrNull()

            // 3. Loudness Enhancer (Normalizer / Gain control)
            loudnessEnhancer = runCatching {
                LoudnessEnhancer(audioSessionId)
            }.onFailure {
                AppDiagnostics.info(TAG, "Loudness effect is unavailable on this device")
            }.getOrNull()

            applyPreferencesLocked(context)

            val eqHolder = cachedEqualizer
            latestCapabilities = CapabilityReport(
                sessionId = audioSessionId,
                equalizerAvailable = eqHolder != null,
                hardwareSpatialAvailable = false,
                spatialStrengthSupported = false,
                roomAvailable = presetReverb != null,
                loudnessAvailable = loudnessEnhancer != null,
                equalizerBands = eqHolder?.numBands?.toInt() ?: 0,
                minLevelMb = eqHolder?.minLevel?.toInt() ?: 0,
                maxLevelMb = eqHolder?.maxLevel?.toInt() ?: 0,
            )

            AppDiagnostics.info(
                TAG,
                "Attached effects to session $audioSessionId (EQ Bands=${latestCapabilities.equalizerBands}, Range=[${latestCapabilities.minLevelMb}..${latestCapabilities.maxLevelMb}] mB, Reverb=${latestCapabilities.roomAvailable}, Normalizer=${latestCapabilities.loudnessAvailable})",
            )
        }
    }

    /**
     * Re-applies all preferences to the active hardware effects.
     */
    fun applyPreferences(context: Context) {
        synchronized(lock) {
            if (!isAttachedLocked()) return
            applyPreferencesLocked(context)
        }
    }

    /**
     * Safely releases all native audio effect handles and resets capability reports.
     */
    fun release() {
        synchronized(lock) {
            releaseLocked()
        }
    }

    private fun isAttachedLocked(): Boolean =
        activeSessionId != 0 && (cachedEqualizer != null || presetReverb != null || loudnessEnhancer != null)

    private fun applyPreferencesLocked(context: Context) {
        // --- 1. Equalizer Tuning ---
        cachedEqualizer?.let { eqHolder ->
            runCatching {
                val bass = normalized(getEqBass(context))
                val lowMid = normalized(getEqLowMid(context))
                val vocal = normalized(getEqVocal(context))
                val highMid = normalized(getEqHighMid(context))
                val treble = normalized(getEqTreble(context))

                val min = eqHolder.minLevel.toInt()
                val max = eqHolder.maxLevel.toInt()
                val numBands = eqHolder.numBands.toInt()

                for (band in 0 until numBands) {
                    val freqHz = eqHolder.centerFreqsHz[band]
                    val targetGainNormalized = calculateTargetGain(
                        freqHz = freqHz,
                        bass = bass,
                        lowMid = lowMid,
                        vocal = vocal,
                        highMid = highMid,
                        treble = treble,
                    )
                    val targetLevelMb = levelFor(targetGainNormalized, min, max)

                    // Dirty-check: only invoke IPC if the level actually changed
                    if (eqHolder.appliedLevels[band] != targetLevelMb) {
                        eqHolder.eq.setBandLevel(band.toShort(), targetLevelMb)
                        eqHolder.appliedLevels[band] = targetLevelMb
                    }
                }

                // Prevent resetting DSP biquads if already enabled
                if (!eqHolder.eq.enabled) {
                    eqHolder.eq.enabled = true
                }
            }.onFailure {
                AppDiagnostics.warning(TAG, "Could not apply equalizer bands", it)
            }
        }

        // --- 2. Spatial Room Profile (Preset Reverb) ---
        presetReverb?.let { effect ->
            runCatching {
                val spatialEnabled = isEqSpatialEnabled(context)
                val spatialProfile = getSpatialProfile(context)

                if (spatialEnabled) {
                    val targetPreset = reverbPresetFor(spatialProfile)
                    if (lastReverbPreset != targetPreset) {
                        effect.preset = targetPreset
                        lastReverbPreset = targetPreset
                    }
                    if (lastReverbEnabled != true) {
                        effect.enabled = true
                        lastReverbEnabled = true
                    }
                } else {
                    if (lastReverbEnabled != false) {
                        effect.enabled = false
                        lastReverbEnabled = false
                    }
                }
            }.onFailure {
                AppDiagnostics.warning(TAG, "Could not apply spatial room profile", it)
            }
        }

        // --- 3. Audio Normalizer (Loudness Enhancer) ---
        loudnessEnhancer?.let { effect ->
            runCatching {
                val normalizerEnabled = isAudioNormalizerEnabled(context)
                val targetGainMb = getAudioNormalizerGainMb(context).coerceAtLeast(0)

                if (normalizerEnabled) {
                    if (lastLoudnessGainMb != targetGainMb) {
                        effect.setTargetGain(targetGainMb)
                        lastLoudnessGainMb = targetGainMb
                    }
                    if (lastLoudnessEnabled != true) {
                        effect.enabled = true
                        lastLoudnessEnabled = true
                    }
                } else {
                    if (lastLoudnessEnabled != false) {
                        effect.enabled = false
                        lastLoudnessEnabled = false
                    }
                }
            }.onFailure {
                AppDiagnostics.warning(TAG, "Could not apply loudness setting", it)
            }
        }
    }

    private fun releaseLocked() {
        releaseEffectSafely(cachedEqualizer?.eq, "Equalizer")
        releaseEffectSafely(presetReverb, "PresetReverb")
        releaseEffectSafely(loudnessEnhancer, "LoudnessEnhancer")

        cachedEqualizer = null
        presetReverb = null
        loudnessEnhancer = null
        activeSessionId = 0

        lastReverbPreset = -1
        lastReverbEnabled = null
        lastLoudnessGainMb = -1
        lastLoudnessEnabled = null

        latestCapabilities = CapabilityReport(0, false, false, false, false, false)
    }

    private fun releaseEffectSafely(effect: AudioEffect?, name: String) {
        if (effect == null) return
        runCatching {
            if (effect.hasControl()) {
                effect.enabled = false
            }
        }.onFailure { AppDiagnostics.warning(TAG, "Failed to disable $name during release", it) }

        runCatching {
            effect.release()
        }.onFailure { AppDiagnostics.warning(TAG, "Failed to release $name handle", it) }
    }

    /**
     * Maps user spatial profiles to Android framework reverb presets.
     */
    private fun reverbPresetFor(profile: SpatialProfile): Short = when (profile) {
        SpatialProfile.STUDIO -> PresetReverb.PRESET_SMALLROOM
        SpatialProfile.WIDE -> PresetReverb.PRESET_MEDIUMHALL
        SpatialProfile.IMMERSIVE -> PresetReverb.PRESET_LARGEHALL
        SpatialProfile.CINEMA -> PresetReverb.PRESET_LARGEROOM
    }

    /**
     * Converts a 0..100 UI preference value into a normalized -1.0..+1.0 float range.
     */
    private fun normalized(value: Float): Float = ((value.coerceIn(0f, 100f) - 50f) / 50f)

    /**
     * Converts a normalized -1.0..+1.0 gain into absolute millibels bounded by [min] and [max].
     */
    private fun levelFor(normalized: Float, min: Int, max: Int): Short {
        val clamped = normalized.coerceIn(-1f, 1f)
        val level = when {
            clamped > 0f -> (clamped * max).roundToInt()
            clamped < 0f -> (-clamped * min).roundToInt()
            else -> 0
        }
        return level.coerceIn(min, max).toShort()
    }

    /**
     * Calculates the target gain using continuous log-linear interpolation across 5 frequency anchors.
     * Guarantees smooth parametric curves across any hardware EQ layout (3 to 31 bands).
     */
    private fun calculateTargetGain(
        freqHz: Int,
        bass: Float,
        lowMid: Float,
        vocal: Float,
        highMid: Float,
        treble: Float,
    ): Float {
        val f = freqHz.toDouble().coerceAtLeast(1.0)
        return when {
            f <= ANCHOR_BASS_HZ -> bass
            f <= ANCHOR_LOW_MID_HZ -> interpolateLog(f, ANCHOR_BASS_HZ, ANCHOR_LOW_MID_HZ, bass, lowMid)
            f <= ANCHOR_VOCAL_HZ -> interpolateLog(f, ANCHOR_LOW_MID_HZ, ANCHOR_VOCAL_HZ, lowMid, vocal)
            f <= ANCHOR_HIGH_MID_HZ -> interpolateLog(f, ANCHOR_VOCAL_HZ, ANCHOR_HIGH_MID_HZ, vocal, highMid)
            f <= ANCHOR_TREBLE_HZ -> interpolateLog(f, ANCHOR_HIGH_MID_HZ, ANCHOR_TREBLE_HZ, highMid, treble)
            else -> treble
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun interpolateLog(
        f: Double,
        fStart: Double,
        fEnd: Double,
        gainStart: Float,
        gainEnd: Float,
    ): Float {
        val t = (ln(f) - ln(fStart)) / (ln(fEnd) - ln(fStart))
        return (gainStart + t * (gainEnd - gainStart)).toFloat()
    }

    /**
     * Holds hardware capabilities and applied states for an attached [Equalizer] instance.
     */
    private class CachedEqualizer(val eq: Equalizer) {
        val numBands: Short = eq.numberOfBands
        val minLevel: Short
        val maxLevel: Short
        val centerFreqsHz: IntArray
        val appliedLevels: ShortArray = ShortArray(numBands.toInt()) { Short.MIN_VALUE }

        init {
            val range = eq.bandLevelRange
            minLevel = range[0]
            maxLevel = range[1]
            centerFreqsHz = IntArray(numBands.toInt()) { band ->
                (eq.getCenterFreq(band.toShort()) / 1_000).coerceAtLeast(1)
            }
        }
    }
}
