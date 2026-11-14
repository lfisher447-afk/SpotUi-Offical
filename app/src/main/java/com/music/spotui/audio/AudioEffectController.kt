package com.music.spotui.audio

import android.content.Context
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import com.music.spotui.data.preferences.SpatialProfile
import com.music.spotui.data.preferences.getAudioNormalizerGainMb
import com.music.spotui.data.preferences.getBassBoostStrength
import com.music.spotui.data.preferences.getEqBass
import com.music.spotui.data.preferences.getEqBrilliance
import com.music.spotui.data.preferences.getEqHighMid
import com.music.spotui.data.preferences.getEqLowMid
import com.music.spotui.data.preferences.getEqPreampDb
import com.music.spotui.data.preferences.getEqSubBass
import com.music.spotui.data.preferences.getEqTreble
import com.music.spotui.data.preferences.getEqVocal
import com.music.spotui.data.preferences.getSpatialProfile
import com.music.spotui.data.preferences.getSpatialRoomDelayMs
import com.music.spotui.data.preferences.getSpatialStrength
import com.music.spotui.data.preferences.isAudioNormalizerEnabled
import com.music.spotui.data.preferences.isEqEnabled
import com.music.spotui.data.preferences.isEqSpatialEnabled
import com.music.spotui.data.preferences.isHeadphoneCrossfeedEnabled
import com.music.spotui.data.preferences.isLimiterEnabled
import com.music.spotui.ui.theme.ThemePreferences
import com.music.spotui.util.AppDiagnostics
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Unified Hardware + Software Audio Effects Orchestrator for the active Media3 audio session.
 *
 * Syncs Android hardware effects (`Equalizer`, `Virtualizer`, `BassBoost`, `PresetReverb`,
 * `LoudnessEnhancer`) AND ExoPlayer's in-process software PCM DSP (`SpotuiDspAudioProcessor` +
 * `SpatialWideningAudioProcessor`) so EQ, Bass Boost, 3D Spatial, and Normalization always work
 * deterministically on speakers, wired headphones, USB DACs, and Bluetooth outputs.
 */
object AudioEffectController {
    private const val TAG = "AudioEffects"

    private const val ANCHOR_BASS_HZ = 60.0
    private const val ANCHOR_LOW_MID_HZ = 250.0
    private const val ANCHOR_VOCAL_HZ = 1000.0
    private const val ANCHOR_HIGH_MID_HZ = 4000.0
    private const val ANCHOR_TREBLE_HZ = 14000.0

    private val lock = Any()
    private var activeSessionId = 0

    private var cachedEqualizer: CachedEqualizer? = null
    private var virtualizer: Virtualizer? = null
    private var virtualizerStrengthSupported = false
    private var bassBoost: BassBoost? = null
    private var bassBoostStrengthSupported = false
    private var presetReverb: PresetReverb? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private var lastReverbPreset: Short = -1
    private var lastReverbEnabled: Boolean? = null
    private var lastLoudnessGainMb: Int = -1
    private var lastLoudnessEnabled: Boolean? = null
    private var lastVirtualizerStrength: Short = -1
    private var lastVirtualizerEnabled: Boolean? = null
    private var lastBassStrength: Short = -1
    private var lastBassEnabled: Boolean? = null

    @Volatile private var softwareDspRef: SpotuiDspAudioProcessor? = null
    @Volatile private var softwareSpatialRef: SpatialWideningAudioProcessor? = null

    data class CapabilityReport(
        val sessionId: Int,
        val equalizerAvailable: Boolean,
        val hardwareSpatialAvailable: Boolean,
        val spatialStrengthSupported: Boolean,
        val bassBoostAvailable: Boolean = false,
        val roomAvailable: Boolean,
        val loudnessAvailable: Boolean,
        val softwareDspAttached: Boolean = false,
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
        bassBoostAvailable = false,
        roomAvailable = false,
        loudnessAvailable = false,
    )

    fun capabilityReport(): CapabilityReport = latestCapabilities

    fun isAttached(): Boolean = synchronized(lock) { isAttachedLocked() }

    fun getActiveSessionId(): Int = synchronized(lock) { activeSessionId }

    fun getBandFrequenciesHz(): IntArray = synchronized(lock) {
        cachedEqualizer?.centerFreqsHz?.clone() ?: intArrayOf()
    }

    /** Registers active software PCM processors so every settings change applies immediately in-process. */
    fun bindSoftwareProcessors(
        context: Context,
        dsp: SpotuiDspAudioProcessor?,
        spatial: SpatialWideningAudioProcessor?,
    ) {
        softwareDspRef = dsp
        softwareSpatialRef = spatial
        syncSoftwareProcessors(context.applicationContext)
    }

    fun syncSoftwareProcessors(context: Context) {
        val appCtx = context.applicationContext
        softwareDspRef?.let { dsp ->
            dsp.eqEnabled = isEqEnabled(appCtx)
            dsp.setFromNormalizedSliders(
                subBass = getEqSubBass(appCtx),
                bass = getEqBass(appCtx),
                lowMid = getEqLowMid(appCtx),
                vocal = getEqVocal(appCtx),
                highMid = getEqHighMid(appCtx),
                treble = getEqTreble(appCtx),
                brilliance = getEqBrilliance(appCtx),
            )
            dsp.preampDb = getEqPreampDb(appCtx)
            // Software bass boost shelf supplements or covers when hardware BassBoost is absent
            val bbStrength = getBassBoostStrength(appCtx)
            val hwBassPresent = bassBoost != null && bassBoostStrengthSupported
            dsp.bassBoostDb = if (hwBassPresent) (bbStrength / 1000f) * 3.5f else (bbStrength / 1000f) * 8.0f
            dsp.limiterEnabled = isLimiterEnabled(appCtx)
            val normOn = isAudioNormalizerEnabled(appCtx)
            dsp.normalizerEnabled = normOn
            dsp.normalizerBoostDb = (getAudioNormalizerGainMb(appCtx) / 100f).coerceIn(0f, 6f)
        }
        softwareSpatialRef?.let { sp ->
            sp.enabled = isEqSpatialEnabled(appCtx)
            sp.width = (getSpatialStrength(appCtx) / 1000f).coerceIn(0f, 1f)
            sp.profile = getSpatialProfile(appCtx)
            sp.monoMix = ThemePreferences.monoAudio(appCtx)
            sp.balance = ThemePreferences.audioBalance(appCtx)
            sp.headphoneCrossfeed = isHeadphoneCrossfeedEnabled(appCtx)
            sp.roomDelayMs = getSpatialRoomDelayMs(appCtx)
        }
        latestCapabilities = latestCapabilities.copy(
            softwareDspAttached = softwareDspRef != null || softwareSpatialRef != null,
        )
    }

    fun attach(context: Context, audioSessionId: Int) {
        val appCtx = context.applicationContext
        syncSoftwareProcessors(appCtx)

        if (audioSessionId <= 0) {
            AppDiagnostics.warning(TAG, "Deferring hardware attach (sessionId=$audioSessionId); software DSP active")
            return
        }

        synchronized(lock) {
            if (activeSessionId == audioSessionId && isAttachedLocked()) {
                applyPreferencesLocked(appCtx)
                return
            }

            releaseHardwareLocked()
            activeSessionId = audioSessionId

            cachedEqualizer = runCatching {
                CachedEqualizer(Equalizer(0, audioSessionId))
            }.onFailure {
                AppDiagnostics.info(TAG, "Hardware Equalizer unavailable for session $audioSessionId (software EQ handles playback)")
            }.getOrNull()

            virtualizer = runCatching {
                @Suppress("DEPRECATION")
                Virtualizer(0, audioSessionId).also {
                    virtualizerStrengthSupported = runCatching { it.strengthSupported }.getOrDefault(false)
                }
            }.onFailure {
                AppDiagnostics.info(TAG, "Hardware Virtualizer unavailable; using software 3D spatializer")
            }.getOrNull()

            bassBoost = runCatching {
                BassBoost(0, audioSessionId).also {
                    bassBoostStrengthSupported = runCatching { it.strengthSupported }.getOrDefault(false)
                }
            }.onFailure {
                AppDiagnostics.info(TAG, "Hardware BassBoost unavailable; using software low-shelf boost")
            }.getOrNull()

            presetReverb = runCatching {
                PresetReverb(0, audioSessionId)
            }.onFailure {
                AppDiagnostics.info(TAG, "PresetReverb unavailable on this device")
            }.getOrNull()

            loudnessEnhancer = runCatching {
                LoudnessEnhancer(audioSessionId)
            }.onFailure {
                AppDiagnostics.info(TAG, "Hardware LoudnessEnhancer unavailable; software normalizer active")
            }.getOrNull()

            applyPreferencesLocked(appCtx)

            val eqHolder = cachedEqualizer
            latestCapabilities = CapabilityReport(
                sessionId = audioSessionId,
                equalizerAvailable = eqHolder != null,
                hardwareSpatialAvailable = virtualizer != null,
                spatialStrengthSupported = virtualizerStrengthSupported,
                bassBoostAvailable = bassBoost != null,
                roomAvailable = presetReverb != null,
                loudnessAvailable = loudnessEnhancer != null,
                softwareDspAttached = softwareDspRef != null || softwareSpatialRef != null,
                equalizerBands = eqHolder?.numBands?.toInt() ?: 0,
                minLevelMb = eqHolder?.minLevel?.toInt() ?: 0,
                maxLevelMb = eqHolder?.maxLevel?.toInt() ?: 0,
            )

            AppDiagnostics.info(
                TAG,
                "Attached session $audioSessionId (HW EQ=${latestCapabilities.equalizerBands} bands, Virtualizer=${latestCapabilities.hardwareSpatialAvailable}, BassBoost=${latestCapabilities.bassBoostAvailable}, SoftwareDSP=${latestCapabilities.softwareDspAttached})",
            )
        }
    }

    fun applyPreferences(context: Context) {
        val appCtx = context.applicationContext
        syncSoftwareProcessors(appCtx)
        synchronized(lock) {
            if (!isAttachedLocked()) return
            applyPreferencesLocked(appCtx)
        }
    }

    fun release() {
        synchronized(lock) {
            releaseHardwareLocked()
        }
    }

    private fun isAttachedLocked(): Boolean =
        activeSessionId != 0 && (cachedEqualizer != null || virtualizer != null || bassBoost != null || presetReverb != null || loudnessEnhancer != null)

    private fun applyPreferencesLocked(context: Context) {
        // Software DSP runs full Parametric EQ & BassBoost; apply hardware EQ gently only when HW is used
        // or skip double-boosting by letting software DSP be the primary precision engine when attached.
        val softwareDspActive = softwareDspRef != null
        val eqEnabled = isEqEnabled(context)

        cachedEqualizer?.let { eqHolder ->
            runCatching {
                if (softwareDspActive || !eqEnabled) {
                    if (eqHolder.eq.enabled) eqHolder.eq.enabled = false
                } else {
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
                        val targetGainNormalized = calculateTargetGain(freqHz, bass, lowMid, vocal, highMid, treble)
                        val targetLevelMb = levelFor(targetGainNormalized, min, max)
                        if (eqHolder.appliedLevels[band] != targetLevelMb) {
                            eqHolder.eq.setBandLevel(band.toShort(), targetLevelMb)
                            eqHolder.appliedLevels[band] = targetLevelMb
                        }
                    }
                    if (!eqHolder.eq.enabled) eqHolder.eq.enabled = true
                }
            }.onFailure { AppDiagnostics.warning(TAG, "Hardware EQ update failed", it) }
        }

        // Hardware Virtualizer (paired cleanly with software spatializer)
        virtualizer?.let { virt ->
            runCatching {
                val spatialOn = isEqSpatialEnabled(context)
                if (spatialOn) {
                    val target = ((getSpatialStrength(context) * 0.7f).roundToInt().coerceIn(0, 1000)).toShort()
                    if (virtualizerStrengthSupported && lastVirtualizerStrength != target) {
                        @Suppress("DEPRECATION")
                        virt.setStrength(target)
                        lastVirtualizerStrength = target
                    }
                    if (lastVirtualizerEnabled != true) {
                        virt.enabled = true
                        lastVirtualizerEnabled = true
                    }
                } else if (lastVirtualizerEnabled != false) {
                    virt.enabled = false
                    lastVirtualizerEnabled = false
                }
            }.onFailure { AppDiagnostics.warning(TAG, "Virtualizer update failed", it) }
        }

        // Hardware BassBoost
        bassBoost?.let { bb ->
            runCatching {
                val strength = getBassBoostStrength(context)
                if (strength > 0) {
                    val target = strength.coerceIn(0, 1000).toShort()
                    if (bassBoostStrengthSupported && lastBassStrength != target) {
                        bb.setStrength(target)
                        lastBassStrength = target
                    }
                    if (lastBassEnabled != true) {
                        bb.enabled = true
                        lastBassEnabled = true
                    }
                } else if (lastBassEnabled != false) {
                    bb.enabled = false
                    lastBassEnabled = false
                }
            }.onFailure { AppDiagnostics.warning(TAG, "BassBoost update failed", it) }
        }

        // PresetReverb — subtle acoustic room only for hall/cinema/immersive profiles
        presetReverb?.let { effect ->
            runCatching {
                val spatialEnabled = isEqSpatialEnabled(context)
                val spatialProfile = getSpatialProfile(context)
                val targetPreset = reverbPresetFor(spatialProfile)
                if (spatialEnabled && targetPreset != PresetReverb.PRESET_NONE) {
                    if (lastReverbPreset != targetPreset) {
                        effect.preset = targetPreset
                        lastReverbPreset = targetPreset
                    }
                    if (lastReverbEnabled != true) {
                        effect.enabled = true
                        lastReverbEnabled = true
                    }
                } else if (lastReverbEnabled != false) {
                    effect.enabled = false
                    lastReverbEnabled = false
                }
            }.onFailure { AppDiagnostics.warning(TAG, "PresetReverb update failed", it) }
        }

        // Loudness Enhancer — hardware makeup disabled if software normalizer handles it (prevents double gain)
        loudnessEnhancer?.let { effect ->
            runCatching {
                val normalizerEnabled = isAudioNormalizerEnabled(context)
                val targetGainMb = getAudioNormalizerGainMb(context).coerceAtLeast(0)
                if (normalizerEnabled && !softwareDspActive) {
                    if (lastLoudnessGainMb != targetGainMb) {
                        effect.setTargetGain(targetGainMb)
                        lastLoudnessGainMb = targetGainMb
                    }
                    if (lastLoudnessEnabled != true) {
                        effect.enabled = true
                        lastLoudnessEnabled = true
                    }
                } else if (lastLoudnessEnabled != false) {
                    effect.enabled = false
                    lastLoudnessEnabled = false
                }
            }.onFailure { AppDiagnostics.warning(TAG, "LoudnessEnhancer update failed", it) }
        }
    }

    private fun releaseHardwareLocked() {
        releaseEffectSafely(cachedEqualizer?.eq, "Equalizer")
        releaseEffectSafely(virtualizer, "Virtualizer")
        releaseEffectSafely(bassBoost, "BassBoost")
        releaseEffectSafely(presetReverb, "PresetReverb")
        releaseEffectSafely(loudnessEnhancer, "LoudnessEnhancer")

        cachedEqualizer = null
        virtualizer = null
        virtualizerStrengthSupported = false
        bassBoost = null
        bassBoostStrengthSupported = false
        presetReverb = null
        loudnessEnhancer = null
        activeSessionId = 0

        lastReverbPreset = -1
        lastReverbEnabled = null
        lastLoudnessGainMb = -1
        lastLoudnessEnabled = null
        lastVirtualizerStrength = -1
        lastVirtualizerEnabled = null
        lastBassStrength = -1
        lastBassEnabled = null

        latestCapabilities = CapabilityReport(
            sessionId = 0,
            equalizerAvailable = false,
            hardwareSpatialAvailable = false,
            spatialStrengthSupported = false,
            bassBoostAvailable = false,
            roomAvailable = false,
            loudnessAvailable = false,
            softwareDspAttached = softwareDspRef != null || softwareSpatialRef != null,
        )
    }

    private fun releaseEffectSafely(effect: AudioEffect?, name: String) {
        if (effect == null) return
        runCatching { if (effect.hasControl()) effect.enabled = false }
        runCatching { effect.release() }
            .onFailure { AppDiagnostics.warning(TAG, "Failed to release $name", it) }
    }

    private fun reverbPresetFor(profile: SpatialProfile): Short = when (profile) {
        SpatialProfile.STUDIO -> PresetReverb.PRESET_NONE
        SpatialProfile.WIDE -> PresetReverb.PRESET_SMALLROOM
        SpatialProfile.IMMERSIVE -> PresetReverb.PRESET_MEDIUMHALL
        SpatialProfile.CINEMA -> PresetReverb.PRESET_LARGEROOM
        SpatialProfile.CONCERT_HALL -> PresetReverb.PRESET_LARGEHALL
        SpatialProfile.BINAURAL_HEADPHONES -> PresetReverb.PRESET_NONE
    }

    private fun normalized(value: Float): Float = ((value.coerceIn(0f, 100f) - 50f) / 50f)

    private fun levelFor(normalized: Float, min: Int, max: Int): Short {
        val clamped = normalized.coerceIn(-1f, 1f)
        val level = when {
            clamped > 0f -> (clamped * max).roundToInt()
            clamped < 0f -> (-clamped * min).roundToInt()
            else -> 0
        }
        return level.coerceIn(min, max).toShort()
    }

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

    private class CachedEqualizer(val eq: Equalizer) {
        val numBands: Short = eq.numberOfBands
        val minLevel: Short
        val maxLevel: Short
        val centerFreqsHz: IntArray
        val appliedLevels: ShortArray = ShortArray(numBands.toInt()) { Short.MIN_VALUE }

        init {
            val range = runCatching { eq.bandLevelRange }.getOrNull()
            minLevel = range?.getOrNull(0) ?: (-1500).toShort()
            maxLevel = range?.getOrNull(1) ?: 1500.toShort()
            val bandsCount = numBands.toInt().coerceAtLeast(0)
            centerFreqsHz = IntArray(bandsCount) { band ->
                runCatching { (eq.getCenterFreq(band.toShort()) / 1_000).coerceAtLeast(1) }.getOrDefault(1000)
            }
        }
    }
}
