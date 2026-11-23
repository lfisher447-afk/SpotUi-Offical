package com.music.spotui.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.media.Spatializer
import android.os.Build
import android.os.Handler
import android.os.Looper
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
import com.music.spotui.data.preferences.getSpatialElevationDepth
import com.music.spotui.data.preferences.getSpatialOrbitSpeed
import com.music.spotui.data.preferences.getSpatialProfile
import com.music.spotui.data.preferences.getSpatialRoomDelayMs
import com.music.spotui.data.preferences.getSpatialStrength
import com.music.spotui.data.preferences.isAudioNormalizerEnabled
import com.music.spotui.data.preferences.isEqEnabled
import com.music.spotui.data.preferences.isEqSpatialEnabled
import com.music.spotui.data.preferences.isHeadphoneCrossfeedEnabled
import com.music.spotui.data.preferences.isLimiterEnabled
import com.music.spotui.data.preferences.isSpatial8dOrbitEnabled
import com.music.spotui.data.preferences.isSpatialCorrelationGuardEnabled
import com.music.spotui.ui.theme.ThemePreferences
import com.music.spotui.util.AppDiagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Unified Hardware + Software Audio Effects Orchestrator for the active Media3 audio session.
 *
 * CRITICAL BLUETOOTH / ONE UI AUDIO SAFETY:
 * Samsung One UI & Qualcomm/Exynos Bluetooth A2DP HALs frequently mute or stall playback when
 * hardware `Virtualizer` or `PresetReverb` (auxiliary effect) is attached/enabled on a BT sink,
 * or when hardware FX conflict with ExoPlayer's in-process 32-bit float PCM AudioProcessors.
 *
 * Whenever software Spatial (`SpatialWideningAudioProcessor`) or a Bluetooth output route is active,
 * this controller cleanly disables hardware `Virtualizer`, `PresetReverb`, `Equalizer`, and
 * `LoudnessEnhancer` (plus `BassBoost` on Bluetooth), guaranteeing uninterrupted, zero-dropout
 * 3D soundstage and 7-band parametric EQ audio on every headset.
 */
object AudioEffectController {
    private const val TAG = "AudioEffects"

    private const val ANCHOR_BASS_HZ = 60.0
    private const val ANCHOR_LOW_MID_HZ = 250.0
    private const val ANCHOR_VOCAL_HZ = 1000.0
    private const val ANCHOR_HIGH_MID_HZ = 4000.0
    private const val ANCHOR_TREBLE_HZ = 14000.0

    enum class AudioOutputRoute(val label: String) {
        SPEAKER("Phone Speaker"),
        WIRED_HEADSET("Wired Headphones"),
        USB_AUDIO("USB Audio / DAC"),
        BLUETOOTH("Bluetooth Headset"),
        HEARING_AID("Hearing Aid"),
    }

    data class AudioRouteInfo(
        val route: AudioOutputRoute = AudioOutputRoute.SPEAKER,
        val deviceName: String = "Device Speaker",
        val isBluetooth: Boolean = false,
        val isHeadphone: Boolean = false,
    ) {
        val isPersonalListening: Boolean
            get() = isBluetooth || isHeadphone || route == AudioOutputRoute.USB_AUDIO || route == AudioOutputRoute.HEARING_AID
    }

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
    @Volatile private var deviceCallbackRegistered = false
    @Volatile var onRouteChanged: ((Context) -> Unit)? = null

    private val _routeState = MutableStateFlow(AudioRouteInfo())
    val routeState: StateFlow<AudioRouteInfo> = _routeState.asStateFlow()

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
        val activeRoute: AudioOutputRoute = AudioOutputRoute.SPEAKER,
        val activeDeviceName: String = "Device Speaker",
        val bluetoothSafeBypassActive: Boolean = false,
        val systemSpatializerAvailable: Boolean = false,
        val systemSpatializerEnabled: Boolean = false,
        val systemHeadTrackerAvailable: Boolean = false,
        val systemSpatializerLabel: String = "SW HRTF Only",
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

    /** Detects current physical audio output route and keeps `routeState` fresh. */
    fun refreshOutputRoute(context: Context): AudioRouteInfo {
        val appCtx = context.applicationContext
        registerDeviceCallbackIfNeeded(appCtx)
        val info = detectOutputRoute(appCtx)
        if (_routeState.value != info) {
            _routeState.value = info
            AppDiagnostics.info(TAG, "Audio route changed -> ${info.route.label} (${info.deviceName})")
        }
        return info
    }

    private fun registerDeviceCallbackIfNeeded(appCtx: Context) {
        if (deviceCallbackRegistered) return
        synchronized(this) {
            if (deviceCallbackRegistered) return
            val am = appCtx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            deviceCallbackRegistered = true
            runCatching {
                am.registerAudioDeviceCallback(
                    object : AudioDeviceCallback() {
                        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                            handleDeviceChange(appCtx)
                        }

                        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                            handleDeviceChange(appCtx)
                        }
                    },
                    Handler(Looper.getMainLooper()),
                )
            }.onFailure {
                deviceCallbackRegistered = false
            }
        }
    }

    private fun handleDeviceChange(appCtx: Context) {
        val prev = _routeState.value
        val next = detectOutputRoute(appCtx)
        _routeState.value = next
        if (prev != next) {
            AppDiagnostics.info(TAG, "AudioDeviceCallback -> ${next.route.label} (${next.deviceName})")
            applyPreferences(appCtx)
            onRouteChanged?.invoke(appCtx)
        }
    }

    private fun detectOutputRoute(context: Context): AudioRouteInfo {
        val am = runCatching { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }.getOrNull()
            ?: return AudioRouteInfo()
        val allDevices = runCatching { am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList() }.getOrNull().orEmpty()
        if (allDevices.isEmpty()) return AudioRouteInfo()

        val mediaRoutedDevices = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                val mediaAttrs = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                am.getAudioDevicesForAttributes(mediaAttrs)
            }.getOrNull().orEmpty()
        } else {
            emptyList()
        }
        val candidates = if (mediaRoutedDevices.isNotEmpty()) mediaRoutedDevices else allDevices

        val mediaBluetooth = candidates.firstOrNull { d ->
            d.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                d.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                d.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                d.type == AudioDeviceInfo.TYPE_BLE_BROADCAST
        }
        if (mediaBluetooth != null) {
            val name = mediaBluetooth.productName?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: "Bluetooth Audio"
            return AudioRouteInfo(
                route = AudioOutputRoute.BLUETOOTH,
                deviceName = name,
                isBluetooth = true,
                isHeadphone = true,
            )
        }

        val hearingAid = candidates.firstOrNull { it.type == AudioDeviceInfo.TYPE_HEARING_AID }
        if (hearingAid != null) {
            val name = hearingAid.productName?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: "Hearing Aid"
            return AudioRouteInfo(
                route = AudioOutputRoute.HEARING_AID,
                deviceName = name,
                isBluetooth = true,
                isHeadphone = true,
            )
        }

        val usb = candidates.firstOrNull { d ->
            d.type == AudioDeviceInfo.TYPE_USB_HEADSET || d.type == AudioDeviceInfo.TYPE_USB_DEVICE
        }
        if (usb != null) {
            val name = usb.productName?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: "USB DAC"
            return AudioRouteInfo(
                route = AudioOutputRoute.USB_AUDIO,
                deviceName = name,
                isBluetooth = false,
                isHeadphone = true,
            )
        }

        val wired = candidates.firstOrNull { d ->
            d.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || d.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
        }
        if (wired != null) {
            val name = wired.productName?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: "Wired Headphones"
            return AudioRouteInfo(
                route = AudioOutputRoute.WIRED_HEADSET,
                deviceName = name,
                isBluetooth = false,
                isHeadphone = true,
            )
        }

        val scoFallback = candidates.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
        if (scoFallback != null) {
            val name = scoFallback.productName?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: "Bluetooth Audio"
            return AudioRouteInfo(
                route = AudioOutputRoute.BLUETOOTH,
                deviceName = name,
                isBluetooth = true,
                isHeadphone = true,
            )
        }

        return AudioRouteInfo(
            route = AudioOutputRoute.SPEAKER,
            deviceName = "Device Speaker",
            isBluetooth = false,
            isHeadphone = false,
        )
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
        val route = refreshOutputRoute(appCtx)
        val bypassHwBassOnBluetooth = route.isBluetooth
        val hwBassActive = !bypassHwBassOnBluetooth && bassBoost != null && bassBoostStrengthSupported

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
            val bbStrength = getBassBoostStrength(appCtx)
            dsp.bassBoostDb = if (hwBassActive) (bbStrength / 1000f) * 3.5f else (bbStrength / 1000f) * 8.0f
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
            sp.autoHeadphoneHrtf = route.isPersonalListening
            sp.roomDelayMs = getSpatialRoomDelayMs(appCtx)
            sp.orbit8dEnabled = isSpatial8dOrbitEnabled(appCtx)
            sp.orbitSpeedHz = getSpatialOrbitSpeed(appCtx)
            sp.elevationDepth = getSpatialElevationDepth(appCtx)
            sp.correlationGuard = isSpatialCorrelationGuardEnabled(appCtx)
            com.music.spotui.util.DevConsoleManager.logDsp(
                "Spatial 3D sync: enabled=${sp.enabled} profile=${sp.profile.label} depth=${(sp.width * 100).roundToInt()}% orbit8D=${sp.orbit8dEnabled} crossfeed=${sp.headphoneCrossfeed} route=${route.route.label}",
            )
        }

        val sysSp = inspectSystemSpatializer(appCtx)
        latestCapabilities = latestCapabilities.copy(
            softwareDspAttached = softwareDspRef != null || softwareSpatialRef != null,
            activeRoute = route.route,
            activeDeviceName = route.deviceName,
            bluetoothSafeBypassActive = route.isBluetooth || softwareSpatialRef != null,
            systemSpatializerAvailable = sysSp.available,
            systemSpatializerEnabled = sysSp.enabled,
            systemHeadTrackerAvailable = sysSp.headTracker,
            systemSpatializerLabel = sysSp.label,
        )
    }

    private data class SystemSpatialInfo(
        val available: Boolean = false,
        val enabled: Boolean = false,
        val headTracker: Boolean = false,
        val label: String = "SW HRTF Active",
    )

    private fun inspectSystemSpatializer(context: Context): SystemSpatialInfo {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S_V2) {
            return SystemSpatialInfo(label = "SW Woodworth HRTF")
        }
        return runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return@runCatching SystemSpatialInfo()
            val spatializer = am.spatializer
            val avail = spatializer.isAvailable
            val on = spatializer.isEnabled
            val headTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                runCatching { spatializer.isHeadTrackerAvailable }.getOrDefault(false)
            } else {
                false
            }
            val level = spatializer.immersiveAudioLevel
            val label = buildString {
                if (avail && on) append("OS Spatializer ON")
                else if (avail) append("OS Spatializer Ready")
                else append("SW HRTF Active")
                if (headTrack) append(" · Head-Track")
                if (level > Spatializer.SPATIALIZER_IMMERSIVE_LEVEL_MULTICHANNEL) {
                    append(" · 3D Objects")
                } else if (level == Spatializer.SPATIALIZER_IMMERSIVE_LEVEL_MULTICHANNEL) {
                    append(" · Multi-Ch")
                }
            }
            SystemSpatialInfo(avail, on, headTrack, label)
        }.getOrDefault(SystemSpatialInfo())
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
            val route = _routeState.value
            val sysSp = inspectSystemSpatializer(appCtx)
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
                activeRoute = route.route,
                activeDeviceName = route.deviceName,
                bluetoothSafeBypassActive = route.isBluetooth || softwareSpatialRef != null,
                systemSpatializerAvailable = sysSp.available,
                systemSpatializerEnabled = sysSp.enabled,
                systemHeadTrackerAvailable = sysSp.headTracker,
                systemSpatializerLabel = sysSp.label,
            )

            AppDiagnostics.info(
                TAG,
                "Attached session $audioSessionId on ${route.route.label} (HW EQ=${latestCapabilities.equalizerBands}b, BT-Safe-Bypass=${latestCapabilities.bluetoothSafeBypassActive}, SoftwareDSP=${latestCapabilities.softwareDspAttached})",
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
        val route = _routeState.value
        val softwareDspActive = softwareDspRef != null
        val softwareSpatialActive = softwareSpatialRef != null
        val eqEnabled = isEqEnabled(context)

        // 1. Hardware Equalizer: bypass whenever SW DSP handles it OR Bluetooth is connected
        cachedEqualizer?.let { eqHolder ->
            runCatching {
                if (softwareDspActive || route.isBluetooth || !eqEnabled) {
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

        // 2. CRITICAL BT FIX: Disable hardware Virtualizer whenever SW 3D Spatializer is bound OR route is Bluetooth!
        // Samsung One UI / A2DP HALs mute or drop audio if HW Virtualizer / PresetReverb attach to BT AudioSink.
        virtualizer?.let { virt ->
            runCatching {
                val shouldEnableHwVirt = !softwareSpatialActive && !route.isBluetooth && isEqSpatialEnabled(context)
                if (shouldEnableHwVirt) {
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

        // 3. Hardware BassBoost: disabled on Bluetooth (SW low-shelf delivers full +8dB safely)
        bassBoost?.let { bb ->
            runCatching {
                val strength = getBassBoostStrength(context)
                val shouldEnableHwBass = strength > 0 && !route.isBluetooth
                if (shouldEnableHwBass) {
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

        // 4. CRITICAL BT FIX: Disable PresetReverb whenever SW 3D Spatializer handles reflections OR route is Bluetooth!
        presetReverb?.let { effect ->
            runCatching {
                val shouldEnableHwReverb = !softwareSpatialActive && !route.isBluetooth && isEqSpatialEnabled(context)
                val targetPreset = reverbPresetFor(getSpatialProfile(context))
                if (shouldEnableHwReverb && targetPreset != PresetReverb.PRESET_NONE) {
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

        // 5. Loudness Enhancer — hardware makeup disabled if software normalizer handles it or on Bluetooth
        loudnessEnhancer?.let { effect ->
            runCatching {
                val normalizerEnabled = isAudioNormalizerEnabled(context)
                val targetGainMb = getAudioNormalizerGainMb(context).coerceAtLeast(0)
                if (normalizerEnabled && !softwareDspActive && !route.isBluetooth) {
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

        val route = _routeState.value
        val sysSp = runCatching { latestCapabilities }.getOrNull()
        latestCapabilities = CapabilityReport(
            sessionId = 0,
            equalizerAvailable = false,
            hardwareSpatialAvailable = false,
            spatialStrengthSupported = false,
            bassBoostAvailable = false,
            roomAvailable = false,
            loudnessAvailable = false,
            softwareDspAttached = softwareDspRef != null || softwareSpatialRef != null,
            activeRoute = route.route,
            activeDeviceName = route.deviceName,
            bluetoothSafeBypassActive = route.isBluetooth || softwareSpatialRef != null,
            systemSpatializerAvailable = sysSp?.systemSpatializerAvailable ?: false,
            systemSpatializerEnabled = sysSp?.systemSpatializerEnabled ?: false,
            systemHeadTrackerAvailable = sysSp?.systemHeadTrackerAvailable ?: false,
            systemSpatializerLabel = sysSp?.systemSpatializerLabel ?: "SW HRTF Only",
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
