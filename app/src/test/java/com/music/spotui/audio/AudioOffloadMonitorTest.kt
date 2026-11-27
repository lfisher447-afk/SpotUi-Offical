package com.music.spotui.audio

import com.music.spotui.data.preferences.OffloadPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioOffloadMonitorTest {

    private fun cleanBaseline(
        policy: OffloadPolicy = OffloadPolicy.AUTO_SAFE,
        floatOutput: Boolean = false,
    ) = OffloadSignals(
        userWantsOffload = true,
        policy = policy,
        activeRecordingCount = 0,
        inVoiceCommunicationMode = false,
        bluetoothScoActive = false,
        internalGameCaptureActive = false,
        softwareDspActive = false,
        floatOutputEnabled = floatOutput,
        appVolumePercent = 100,
        playbackSpeed = 1.0f,
        playbackPitch = 1.0f,
    )

    @Test
    fun blocksOffloadDuringActiveMicRecording() {
        val d = AudioOffloadMonitor.evaluateOffloadDecision(cleanBaseline().copy(activeRecordingCount = 1))
        assertFalse(d.allowHardwareOffload)
        assertTrue(d.reason.contains("Mic Recording", ignoreCase = true))
    }

    @Test
    fun blocksOffloadDuringVoipModeAndBtSco() {
        val voip = AudioOffloadMonitor.evaluateOffloadDecision(cleanBaseline().copy(inVoiceCommunicationMode = true))
        assertFalse(voip.allowHardwareOffload)
        assertTrue(voip.reason.contains("VoIP", ignoreCase = true))

        val sco = AudioOffloadMonitor.evaluateOffloadDecision(cleanBaseline().copy(bluetoothScoActive = true))
        assertFalse(sco.allowHardwareOffload)
        assertTrue(sco.reason.contains("Bluetooth SCO", ignoreCase = true))
    }

    @Test
    fun blocksOffloadDuringInternalGameCapture() {
        val d = AudioOffloadMonitor.evaluateOffloadDecision(cleanBaseline().copy(internalGameCaptureActive = true))
        assertFalse(d.allowHardwareOffload)
        assertTrue(d.reason.contains("Internal Game", ignoreCase = true))
    }

    @Test
    fun blocksOffloadWhenSoftwareDspVolumeSpeedOrAlwaysSwPcm() {
        assertFalse(AudioOffloadMonitor.evaluateOffloadDecision(cleanBaseline().copy(softwareDspActive = true)).allowHardwareOffload)
        assertFalse(AudioOffloadMonitor.evaluateOffloadDecision(cleanBaseline().copy(appVolumePercent = 70)).allowHardwareOffload)
        assertFalse(AudioOffloadMonitor.evaluateOffloadDecision(cleanBaseline().copy(playbackSpeed = 1.25f)).allowHardwareOffload)
        assertFalse(AudioOffloadMonitor.evaluateOffloadDecision(cleanBaseline().copy(playbackPitch = 0.90f)).allowHardwareOffload)
        assertFalse(AudioOffloadMonitor.evaluateOffloadDecision(cleanBaseline(policy = OffloadPolicy.ALWAYS_SOFTWARE_PCM)).allowHardwareOffload)
        assertFalse(AudioOffloadMonitor.evaluateOffloadDecision(cleanBaseline(policy = OffloadPolicy.AUTO_SAFE, floatOutput = true)).allowHardwareOffload)
    }

    @Test
    fun allowsOffloadWhenCleanStereo100PercentAndNoMicOrDsp() {
        val autoSafeClean = AudioOffloadMonitor.evaluateOffloadDecision(cleanBaseline(policy = OffloadPolicy.AUTO_SAFE, floatOutput = false))
        assertTrue(autoSafeClean.allowHardwareOffload)

        val preferClean = AudioOffloadMonitor.evaluateOffloadDecision(cleanBaseline(policy = OffloadPolicy.PREFER_OFFLOAD, floatOutput = true))
        assertTrue(preferClean.allowHardwareOffload)
    }
}
