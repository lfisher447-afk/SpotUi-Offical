package com.music.spotui

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.music.spotui.audio.SpatialWideningAudioProcessor
import com.music.spotui.audio.SpotuiDspAudioProcessor
import com.music.spotui.data.preferences.SpatialProfile
import com.music.spotui.data.preferences.detectAudioContainerAndMime
import com.music.spotui.ui.components.ThanksgivingClockCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

class UpgradeFeaturesTest {

    @Test
    fun detectAudioContainerAndMime_neverEmitsWildcardMimeAndSniffsHeaders() {
        val flacHeader = byteArrayOf(0x66, 0x4C, 0x61, 0x43, 0x00, 0x00)
        assertEquals("flac" to "audio/flac", detectAudioContainerAndMime(flacHeader, "bin"))

        val mp3Header = byteArrayOf(0x49, 0x44, 0x33, 0x03)
        assertEquals("mp3" to "audio/mpeg", detectAudioContainerAndMime(mp3Header, "tmp"))

        val webmHeader = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(), 0x93.toByte())
        assertEquals("webm" to "audio/webm", detectAudioContainerAndMime(webmHeader, "dat"))

        val m4aHeader = byteArrayOf(0x00, 0x00, 0x00, 0x20, 0x66, 0x74, 0x79, 0x70)
        assertEquals("m4a" to "audio/mp4", detectAudioContainerAndMime(m4aHeader, "unknown"))

        val oggOpusHeader = byteArrayOf(0x4F, 0x67, 0x67, 0x53, 0x00)
        assertEquals("opus" to "audio/ogg", detectAudioContainerAndMime(oggOpusHeader, "opus"))

        val fallback = detectAudioContainerAndMime(byteArrayOf(), "totally_unknown")
        assertNotEquals("audio/*", fallback.second)
        assertEquals("audio/mp4", fallback.second)
    }

    @Test
    fun thanksgivingCalculator_computesExact2026DateCountdownAndRollover() {
        assertEquals(
            LocalDate.of(2026, Month.NOVEMBER, 26),
            ThanksgivingClockCalculator.thanksgivingDateForYear(2026),
        )
        assertEquals(
            LocalDate.of(2027, Month.NOVEMBER, 25),
            ThanksgivingClockCalculator.thanksgivingDateForYear(2027),
        )

        val zone = ZoneId.of("America/Chicago")
        val before = ThanksgivingClockCalculator.calculate(ZonedDateTime.of(2026, 11, 18, 12, 0, 0, 0, zone))
        assertFalse(before.isThanksgivingDay)
        assertEquals(2026, before.targetYear)
        assertEquals(7L, before.days)
        assertEquals(12L, before.hours)

        val onDay = ThanksgivingClockCalculator.calculate(ZonedDateTime.of(2026, 11, 26, 14, 30, 0, 0, zone))
        assertTrue(onDay.isThanksgivingDay)
        assertEquals(0L, onDay.days)
        assertEquals(1f, onDay.harvestProgress, 0.001f)

        val afterDay = ThanksgivingClockCalculator.calculate(ZonedDateTime.of(2026, 11, 27, 9, 0, 0, 0, zone))
        assertFalse(afterDay.isThanksgivingDay)
        assertEquals(2027, afterDay.targetYear)
        assertEquals(LocalDate.of(2027, Month.NOVEMBER, 25), afterDay.targetDate)
    }

    @Test
    fun spatialWideningProcessor_producesNonZeroFiniteAudioOnBluetoothAndRecoversFromNaN() {
        val processor = SpatialWideningAudioProcessor().apply {
            enabled = true
            width = 0.85f
            profile = SpatialProfile.IMMERSIVE
            autoHeadphoneHrtf = true
            roomDelayMs = 14f
        }
        processor.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_FLOAT))
        processor.flush()

        // Feed stereo sine + deliberate NaN burst to verify no silent/NaN lockup on Bluetooth output
        val frames = 512
        val input = ByteBuffer.allocateDirect(frames * 8).order(ByteOrder.nativeOrder())
        for (i in 0 until frames) {
            if (i in 20..22) {
                input.putFloat(Float.NaN)
                input.putFloat(Float.POSITIVE_INFINITY)
            } else {
                val l = kotlin.math.sin(i * 0.08).toFloat() * 0.5f
                val r = kotlin.math.cos(i * 0.05).toFloat() * 0.4f
                input.putFloat(l)
                input.putFloat(r)
            }
        }
        input.flip()

        processor.queueInput(input)
        val out = processor.output.order(ByteOrder.nativeOrder())
        assertEquals(frames * 8, out.remaining())

        var nonZeroEnergy = 0.0
        while (out.remaining() >= 8) {
            val l = out.float
            val r = out.float
            assertTrue("Left sample must remain finite", l.isFinite())
            assertTrue("Right sample must remain finite", r.isFinite())
            assertTrue("Limiter must keep output <= 1.0", abs(l) <= 1.0f && abs(r) <= 1.0f)
            nonZeroEnergy += abs(l) + abs(r)
        }
        assertTrue("3D Spatial processor must produce audible energy (not silence)", nonZeroEnergy > 50.0)

        // Ensure config persists across reset()
        processor.reset()
        assertTrue(processor.enabled)
        assertTrue(processor.autoHeadphoneHrtf)
        assertEquals(SpatialProfile.IMMERSIVE, processor.profile)
    }

    @Test
    fun spotuiDspProcessor_remainsFiniteWhenFedNaNSamples() {
        val dsp = SpotuiDspAudioProcessor().apply {
            eqEnabled = true
            setBandGainDb(1, 6f)
            bassBoostDb = 6f
            normalizerEnabled = true
        }
        dsp.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_FLOAT))
        dsp.flush()

        val input = ByteBuffer.allocateDirect(128 * 8).order(ByteOrder.nativeOrder())
        for (i in 0 until 128) {
            if (i < 4) {
                input.putFloat(Float.NaN)
                input.putFloat(Float.NEGATIVE_INFINITY)
            } else {
                input.putFloat(0.25f)
                input.putFloat(-0.25f)
            }
        }
        input.flip()
        dsp.queueInput(input)
        val out = dsp.output.order(ByteOrder.nativeOrder())
        while (out.remaining() >= 8) {
            val l = out.float
            val r = out.float
            assertTrue(l.isFinite() && r.isFinite())
        }
    }
}
