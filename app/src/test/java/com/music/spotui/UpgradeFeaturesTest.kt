package com.music.spotui

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.metrolist.innertube.utils.YouTubeUrlParser
import com.metrolist.spotify.SpotiFlac
import com.music.spotui.audio.SpatialWideningAudioProcessor
import com.music.spotui.audio.SpotuiDspAudioProcessor
import com.music.spotui.data.api.TranslationApi
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.data.preferences.SpatialProfile
import com.music.spotui.data.preferences.alternativeStreamKey
import com.music.spotui.data.preferences.alternativeStreamKeyForUrl
import com.music.spotui.data.preferences.detectAudioContainerAndMime
import com.music.spotui.data.preferences.normalizeYouTubeVideoId
import com.music.spotui.ui.components.ChristmasClockCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun christmasCalculator_computesExact2026DateCountdownEveAndRollover() {
        assertEquals(
            LocalDate.of(2026, Month.DECEMBER, 25),
            ChristmasClockCalculator.christmasDateForYear(2026),
        )
        assertEquals(
            LocalDate.of(2027, Month.DECEMBER, 25),
            ChristmasClockCalculator.christmasDateForYear(2027),
        )

        val zone = ZoneId.of("America/Chicago")
        val before = ChristmasClockCalculator.calculate(ZonedDateTime.of(2026, 11, 26, 12, 0, 0, 0, zone))
        assertFalse(before.isChristmasDay)
        assertFalse(before.isChristmasEve)
        assertEquals(2026, before.targetYear)
        assertEquals(28L, before.days)
        assertEquals(12L, before.hours)

        val eve = ChristmasClockCalculator.calculate(ZonedDateTime.of(2026, 12, 24, 18, 0, 0, 0, zone))
        assertFalse(eve.isChristmasDay)
        assertTrue(eve.isChristmasEve)
        assertEquals(0L, eve.days)
        assertEquals(6L, eve.hours)

        val onDay = ChristmasClockCalculator.calculate(ZonedDateTime.of(2026, 12, 25, 14, 30, 0, 0, zone))
        assertTrue(onDay.isChristmasDay)
        assertEquals(0L, onDay.days)
        assertEquals(1f, onDay.holidayProgress, 0.001f)

        val afterDay = ChristmasClockCalculator.calculate(ZonedDateTime.of(2026, 12, 26, 9, 0, 0, 0, zone))
        assertFalse(afterDay.isChristmasDay)
        assertEquals(2027, afterDay.targetYear)
        assertEquals(LocalDate.of(2027, Month.DECEMBER, 25), afterDay.targetDate)
    }

    @Test
    fun youtubeUrlParser_andAlternativeKeys_extractExactVideoIdsWithoutCollisions() {
        val cases = mapOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ" to "dQw4w9WgXcQ",
            "https://music.youtube.com/watch?v=9bZkp7q19f0&si=xyz" to "9bZkp7q19f0",
            "https://m.youtube.com/watch?list=PL123&v=kJQP7kiw5Fk" to "kJQP7kiw5Fk",
            "https://youtu.be/fJ9rUzIMcZQ?t=42" to "fJ9rUzIMcZQ",
            "https://www.youtube.com/shorts/aqz-KE-bpKQ" to "aqz-KE-bpKQ",
            "https://www.youtube.com/live/jfKfPfyJRdk?feature=share" to "jfKfPfyJRdk",
            "https://www.youtube-nocookie.com/embed/L_jWHffIx5E" to "L_jWHffIx5E",
            "https://www.youtube.com/watch?prev=ABCDEFGHIJK&v=12345678901" to "12345678901",
        )
        cases.forEach { (url, expectedId) ->
            assertEquals("Failed extracting URL: $url", expectedId, YouTubeUrlParser.extractVideoId(url))
            assertEquals("Failed normalizing URL: $url", expectedId, normalizeYouTubeVideoId(url))
        }
        assertEquals("dQw4w9WgXcQ", normalizeYouTubeVideoId("  dQw4w9WgXcQ  "))
        assertNull(normalizeYouTubeVideoId("not a youtube id"))

        val unsavedSongA = SongsModel(
            id = 0,
            title = "Track A",
            album = "",
            singer = "Artist",
            coverUri = "",
            url = "spotify:track:AAA|Track A Artist",
        )
        val unsavedSongB = SongsModel(
            id = 0,
            title = "Track B",
            album = "",
            singer = "Artist",
            coverUri = "",
            url = "spotify:track:BBB|Track B Artist",
        )
        assertNotEquals(alternativeStreamKey(unsavedSongA), alternativeStreamKey(unsavedSongB))
        assertEquals(alternativeStreamKeyForUrl(unsavedSongA.url), alternativeStreamKey(unsavedSongA))
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

    @Test
    fun spatialWideningProcessor_advanced8dIsStationaryAndProducesFiniteNonZeroAudio() {
        val processor = SpatialWideningAudioProcessor().apply {
            enabled = true
            width = 0.85f
            profile = SpatialProfile.BINAURAL_HEADPHONES
            autoHeadphoneHrtf = true
            advanced8dEnabled = true
            advanced8dDepth = 1.15f
            elevationDepth = 0.8f
        }
        processor.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_FLOAT))
        processor.flush()

        val frames = 256
        val input = ByteBuffer.allocateDirect(frames * 8).order(ByteOrder.nativeOrder())
        for (i in 0 until frames) {
            input.putFloat(kotlin.math.sin(i * 0.06).toFloat() * 0.45f)
            input.putFloat(kotlin.math.cos(i * 0.04).toFloat() * 0.40f)
        }
        input.flip()

        processor.queueInput(input)
        val out = processor.output.order(ByteOrder.nativeOrder())
        assertEquals(frames * 8, out.remaining())
        assertTrue(processor.activeDescription.contains("Advanced 8D"))
        assertTrue(processor.spatialModeLabel.contains("Advanced 8D"))

        var energy = 0.0
        while (out.remaining() >= 8) {
            val l = out.float
            val r = out.float
            assertTrue(l.isFinite() && r.isFinite())
            assertTrue(abs(l) <= 1.0f && abs(r) <= 1.0f)
            energy += abs(l) + abs(r)
        }
        assertTrue("Expected audible Advanced 8D energy, got $energy", energy > 25.0)
    }

    @Test
    fun spotFlac_extractTrackIdAndInspectBinarySignatures_handleAllFormats() {
        assertEquals(
            "0VjIjW4GlUZAMYd2vX5bxg",
            SpotiFlac.extractSpotifyTrackId("https://open.spotify.com/track/0VjIjW4GlUZAMYd2vX5bxg?si=abc"),
        )
        assertEquals(
            "0VjIjW4GlUZAMYd2vX5bxg",
            SpotiFlac.extractSpotifyTrackId("https://open.spotify.com/intl-es/track/0VjIjW4GlUZAMYd2vX5bxg"),
        )
        assertEquals(
            "0VjIjW4GlUZAMYd2vX5bxg",
            SpotiFlac.extractSpotifyTrackId("spotify:track:0VjIjW4GlUZAMYd2vX5bxg"),
        )
        assertEquals(
            "0VjIjW4GlUZAMYd2vX5bxg",
            SpotiFlac.extractSpotifyTrackId("  0VjIjW4GlUZAMYd2vX5bxg  "),
        )
        assertNull(SpotiFlac.extractSpotifyTrackId("not-a-valid-spotify-track"))

        assertTrue(
            SpotiFlac.inspectBinaryMagicSignature(byteArrayOf(0x66, 0x4C, 0x61, 0x43, 0x00))
                .contains("fLaC"),
        )
        assertTrue(
            SpotiFlac.inspectBinaryMagicSignature(byteArrayOf(0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70))
                .contains("ftyp"),
        )
        assertTrue(
            SpotiFlac.inspectBinaryMagicSignature("<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\"/>".encodeToByteArray())
                .contains("<MPD>"),
        )
        assertTrue(
            SpotiFlac.inspectBinaryMagicSignature(byteArrayOf(0x49, 0x44, 0x33, 0x04))
                .contains("ID3v2"),
        )
        assertEquals("Empty response", SpotiFlac.inspectBinaryMagicSignature(byteArrayOf()))
    }

    @Test
    fun translationApi_scriptFallback_detectsShortNonLatinCallouts() {
        assertEquals("ar", TranslationApi.inferScriptLanguageFallback("يمين"))
        assertEquals("ru", TranslationApi.inferScriptLanguageFallback("выходи"))
        assertEquals("ja", TranslationApi.inferScriptLanguageFallback("助けて"))
        assertEquals("ko", TranslationApi.inferScriptLanguageFallback("도와줘"))
        assertNull(TranslationApi.inferScriptLanguageFallback("push mid"))
    }
}
