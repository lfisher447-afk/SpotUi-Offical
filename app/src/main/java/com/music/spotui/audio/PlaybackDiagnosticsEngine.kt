package com.music.spotui.audio

import android.content.Context
import com.music.spotui.data.preferences.ProAudioPreferences
import com.music.spotui.data.preferences.getBufferProfile
import com.music.spotui.di.PlaybackAutomationManager
import com.music.spotui.di.SongPlayer

data class PlaybackDiagnosticsSnapshot(
    val sourceLabel: String = "Idle",
    val qualityLabel: String = "--",
    val outputRouteLabel: String = "Device Speaker",
    val outputDeviceName: String = "Device Speaker",
    val sessionId: Int = 0,
    val bufferProfileLabel: String = "Balanced",
    val bufferedPct: Int = 0,
    val offloadAllowed: Boolean = false,
    val offloadReason: String = "Software PCM",
    val activeMicCount: Int = 0,
    val voipActive: Boolean = false,
    val internalCaptureActive: Boolean = false,
    val spatialSummary: String = "Bypassed",
    val spatialCorrelation: Float = 1f,
    val harmonicActive: Boolean = false,
    val harmonicPeakDb: Float = -60f,
    val harmonicRmsDb: Float = -60f,
    val translatorDuckingGain: Float = 1f,
    val lastError: String? = null,
)

object PlaybackDiagnosticsEngine {
    fun captureSnapshot(context: Context): PlaybackDiagnosticsSnapshot {
        val appCtx = context.applicationContext
        val telemetry = SongPlayer.telemetrySnapshot(appCtx)
        val caps = AudioEffectController.capabilityReport()
        val offload = AudioOffloadMonitor.state.value
        val harmonic = SongPlayer.harmonicTelemetry()
        val pro = ProAudioPreferences.hydrateIfNeeded(appCtx)
        return PlaybackDiagnosticsSnapshot(
            sourceLabel = telemetry.activeSource.ifBlank { "Idle" },
            qualityLabel = telemetry.currentQuality.ifBlank { "Auto" },
            outputRouteLabel = caps.activeRoute.label,
            outputDeviceName = caps.activeDeviceName,
            sessionId = telemetry.sessionId,
            bufferProfileLabel = getBufferProfile(appCtx).label,
            bufferedPct = telemetry.bufferedPercentage,
            offloadAllowed = offload.hardwareOffloadAllowed,
            offloadReason = offload.reason,
            activeMicCount = offload.activeRecordingCount,
            voipActive = offload.inVoiceCommunicationMode || offload.bluetoothScoActive,
            internalCaptureActive = offload.internalGameCaptureActive,
            spatialSummary = SongPlayer.dspTelemetrySummary(),
            spatialCorrelation = SongPlayer.spatialCorrelation(),
            harmonicActive = pro.harmonicEnabled && harmonic.active,
            harmonicPeakDb = harmonic.peakDb,
            harmonicRmsDb = harmonic.rmsDb,
            translatorDuckingGain = PlaybackAutomationManager.duckingGain.value,
            lastError = telemetry.lastError,
        )
    }
}
