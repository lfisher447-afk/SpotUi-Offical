package com.music.spotui.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.music.spotui.data.preferences.SpatialProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

class Stationary8dAndHarmonicTest {

    private fun buildStereoSineFloatBuffer(frames: Int, freqHz: Float = 440f, sampleRate: Int = 48_000): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(frames * 2 * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until frames) {
            val l = (0.35f * sin(2.0 * PI * freqHz * i / sampleRate)).toFloat()
            val r = (0.28f * sin(2.0 * PI * (freqHz * 1.25f) * i / sampleRate)).toFloat()
            buf.putFloat(l)
            buf.putFloat(r)
        }
        buf.flip()
        return buf
    }

    @Test
    fun stationary8dProducesFiniteNonZeroStereoAcrossExtendedParams() {
        val proc = SpatialWideningAudioProcessor().apply {
            enabled = true
            width = 0.85f
            profile = SpatialProfile.CONCERT_HALL
            roomDelayMs = 14f
            advanced8dEnabled = true
            advanced8dDepth = 1.0f
            elevationDepth = 0.78f
            azimuthSpread = 1.25f
            reflectionDensity = 1.15f
            airShimmer = 0.62f
            subBassCrossoverHz = 115f
            frontRearBalance = 0.60f
            correlationGuard = true
        }
        val format = AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_FLOAT)
        val outFmt = proc.configure(format)
        assertEquals(2, outFmt.channelCount)
        proc.flush()

        val input = buildStereoSineFloatBuffer(frames = 2048)
        proc.queueInput(input)
        val out = proc.output
        assertTrue(out.remaining() >= 2048 * 2 * 4)

        var maxAbs = 0f
        var count = 0
        while (out.hasRemaining()) {
            val sample = out.float
            assertTrue("Sample must be finite", sample.isFinite())
            val a = kotlin.math.abs(sample)
            if (a > maxAbs) maxAbs = a
            count++
        }
        assertTrue("Expected processed stereo samples", count > 1000)
        assertTrue("Expected non-zero audio amplitude, got $maxAbs", maxAbs > 0.01f)
        assertTrue("Correlation must remain bounded", proc.currentCorrelation.isFinite())
    }

    @Test
    fun harmonicAndDynamicsProcessorProcessesFloatAnd16BitPcmCleanly() {
        val harmonic = HarmonicAndDynamicsProcessor().apply {
            enabled = true
            tubeWarmth = 0.35f
            airExciter = 0.40f
            subHarmonicPunch = 0.25f
            haasDelayAlignMs = 0.75f
            compressorRatio = 2.8f
        }
        assertTrue(harmonic.hasActiveProcessing())

        val fmt = AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_FLOAT)
        harmonic.configure(fmt)
        harmonic.flush()

        val input = buildStereoSineFloatBuffer(frames = 1536, freqHz = 220f)
        harmonic.queueInput(input)
        val out = harmonic.output
        assertTrue(out.hasRemaining())

        var maxAbs = 0f
        while (out.hasRemaining()) {
            val v = out.float
            assertTrue("Harmonic output must be finite", v.isFinite())
            if (kotlin.math.abs(v) > maxAbs) maxAbs = kotlin.math.abs(v)
        }
        assertTrue("Expected audible output, got $maxAbs", maxAbs > 0.01f)
        assertTrue(harmonic.processedFrames >= 1536L)
    }
}
