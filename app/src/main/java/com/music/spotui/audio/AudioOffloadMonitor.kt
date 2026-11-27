package com.music.spotui.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.music.spotui.data.preferences.OffloadPolicy
import com.music.spotui.data.preferences.ProAudioPreferences
import com.music.spotui.translator.GameVoiceTranslatorEngine
import com.music.spotui.util.DevConsoleManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

data class OffloadSignals(
    val userWantsOffload: Boolean,
    val policy: OffloadPolicy,
    val activeRecordingCount: Int,
    val inVoiceCommunicationMode: Boolean,
    val bluetoothScoActive: Boolean,
    val internalGameCaptureActive: Boolean,
    val softwareDspActive: Boolean,
    val floatOutputEnabled: Boolean,
    val appVolumePercent: Int,
    val playbackSpeed: Float,
    val playbackPitch: Float,
)

data class OffloadDecision(
    val allowHardwareOffload: Boolean,
    val reason: String,
)

data class OffloadMonitorSnapshot(
    val activeRecordingCount: Int = 0,
    val inVoiceCommunicationMode: Boolean = false,
    val bluetoothScoActive: Boolean = false,
    val internalGameCaptureActive: Boolean = false,
    val hardwareOffloadAllowed: Boolean = false,
    val reason: String = "Software PCM (Safe)",
    val policy: OffloadPolicy = OffloadPolicy.AUTO_SAFE,
)

/**
 * Monitors real-time microphone recordings, VoIP/call modes, Bluetooth SCO, and
 * internal MediaProjection game audio capture so Android hardware Audio Offload
 * is automatically disabled before HALs can drop or mute audio.
 */
object AudioOffloadMonitor {
    private val _state = MutableStateFlow(OffloadMonitorSnapshot())
    val state: StateFlow<OffloadMonitorSnapshot> = _state.asStateFlow()

    @Volatile
    private var recordingCallbackRegistered = false

    @Volatile
    private var activeRecordingCount = 0

    @Volatile
    var onOffloadEnvironmentChanged: ((Context) -> Unit)? = null

    fun ensureStarted(context: Context) {
        val appCtx = context.applicationContext
        if (recordingCallbackRegistered) return
        synchronized(this) {
            if (recordingCallbackRegistered) return
            val am = appCtx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            recordingCallbackRegistered = true
            runCatching {
                val activeConfigs = am.activeRecordingConfigurations
                activeRecordingCount = activeConfigs.size
                am.registerAudioRecordingCallback(
                    object : AudioManager.AudioRecordingCallback() {
                        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>?) {
                            val nextCount = configs?.size ?: 0
                            val changed = nextCount != activeRecordingCount
                            activeRecordingCount = nextCount
                            if (changed) {
                                DevConsoleManager.logDsp("AudioOffloadMonitor: active recording count -> $nextCount")
                            }
                            onOffloadEnvironmentChanged?.invoke(appCtx)
                        }
                    },
                    Handler(Looper.getMainLooper()),
                )
            }.onFailure {
                recordingCallbackRegistered = false
            }
        }
    }

    fun inspectEnvironment(
        context: Context,
        userWantsOffload: Boolean,
        softwareDspActive: Boolean,
        floatOutputEnabled: Boolean,
        appVolumePercent: Int,
        playbackSpeed: Float,
        playbackPitch: Float,
    ): OffloadDecision {
        val appCtx = context.applicationContext
        ensureStarted(appCtx)
        val policy = ProAudioPreferences.hydrateIfNeeded(appCtx).offloadPolicy
        val am = runCatching { appCtx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }.getOrNull()
        val mode = runCatching { am?.mode ?: AudioManager.MODE_NORMAL }.getOrDefault(AudioManager.MODE_NORMAL)
        val inVoiceComm = mode == AudioManager.MODE_IN_COMMUNICATION ||
            mode == AudioManager.MODE_IN_CALL ||
            mode == AudioManager.MODE_RINGTONE
        val scoActive = runCatching {
            val commType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am?.communicationDevice?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            } else {
                false
            }
            @Suppress("DEPRECATION")
            commType || (am?.isBluetoothScoOn == true)
        }.getOrDefault(false)
        val gameCaptureActive = GameVoiceTranslatorEngine.isCaptureActive()
        val liveRecordingCount = runCatching {
            am?.activeRecordingConfigurations?.size ?: activeRecordingCount
        }.getOrDefault(activeRecordingCount)
        activeRecordingCount = liveRecordingCount

        val signals = OffloadSignals(
            userWantsOffload = userWantsOffload,
            policy = policy,
            activeRecordingCount = liveRecordingCount,
            inVoiceCommunicationMode = inVoiceComm,
            bluetoothScoActive = scoActive,
            internalGameCaptureActive = gameCaptureActive,
            softwareDspActive = softwareDspActive,
            floatOutputEnabled = floatOutputEnabled,
            appVolumePercent = appVolumePercent,
            playbackSpeed = playbackSpeed,
            playbackPitch = playbackPitch,
        )
        val decision = evaluateOffloadDecision(signals)
        _state.value = OffloadMonitorSnapshot(
            activeRecordingCount = liveRecordingCount,
            inVoiceCommunicationMode = inVoiceComm,
            bluetoothScoActive = scoActive,
            internalGameCaptureActive = gameCaptureActive,
            hardwareOffloadAllowed = decision.allowHardwareOffload,
            reason = decision.reason,
            policy = policy,
        )
        return decision
    }

    /**
     * Pure deterministic offload safety gate (unit-tested directly).
     */
    fun evaluateOffloadDecision(signals: OffloadSignals): OffloadDecision {
        if (signals.policy == OffloadPolicy.ALWAYS_SOFTWARE_PCM) {
            return OffloadDecision(false, "Software PCM Forced (Zero-Dropout Policy)")
        }
        if (!signals.userWantsOffload) {
            return OffloadDecision(false, "Hardware Offload Disabled by User")
        }
        if (signals.internalGameCaptureActive) {
            return OffloadDecision(false, "Bypassed: Internal Game Voice Capture Active")
        }
        if (signals.activeRecordingCount > 0) {
            return OffloadDecision(false, "Bypassed: Active Mic Recording (${signals.activeRecordingCount})")
        }
        if (signals.inVoiceCommunicationMode) {
            return OffloadDecision(false, "Bypassed: VoIP / Voice Chat Mode")
        }
        if (signals.bluetoothScoActive) {
            return OffloadDecision(false, "Bypassed: Bluetooth SCO Mic Active")
        }
        if (signals.softwareDspActive) {
            return OffloadDecision(false, "Bypassed: Software EQ / 8D / Harmonic DSP Active")
        }
        if (signals.appVolumePercent in 0 until 100) {
            return OffloadDecision(false, "Bypassed: In-App Volume (${signals.appVolumePercent}%)")
        }
        if (abs(signals.playbackSpeed - 1.0f) > 0.01f || abs(signals.playbackPitch - 1.0f) > 0.01f) {
            return OffloadDecision(false, "Bypassed: Speed / Pitch Tuning Active")
        }
        if (signals.policy == OffloadPolicy.AUTO_SAFE && signals.floatOutputEnabled) {
            return OffloadDecision(false, "Bypassed: 32-bit Float PCM Chain Active")
        }
        return OffloadDecision(true, "Hardware Audio Offload Active")
    }
}
