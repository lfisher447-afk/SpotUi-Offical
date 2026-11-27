package com.music.spotui

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.test.core.app.ApplicationProvider
import com.music.spotui.accessibility.GameTranslatorEngine
import com.music.spotui.audio.SpatialWideningAudioProcessor
import com.music.spotui.audio.SpotuiDspAudioProcessor
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.data.preferences.AudioFocusMode
import com.music.spotui.data.preferences.BufferProfile
import com.music.spotui.data.preferences.ProviderRoutingPreferences
import com.music.spotui.data.preferences.SpatialProfile
import com.music.spotui.data.preferences.getAudioFocusMode
import com.music.spotui.data.preferences.getAudioNormalizerTargetLufs
import com.music.spotui.data.preferences.getBassBoostStrength
import com.music.spotui.data.preferences.getBufferProfile
import com.music.spotui.data.preferences.getEqBrilliance
import com.music.spotui.data.preferences.getEqPreampDb
import com.music.spotui.data.preferences.getEqSubBass
import com.music.spotui.data.preferences.getSeekStepSeconds
import com.music.spotui.data.preferences.getSpatialProfile
import com.music.spotui.data.preferences.getSpatialRoomDelayMs
import com.music.spotui.data.preferences.getSpatial8dIntensity
import com.music.spotui.data.preferences.isAdvancedSettingsEnabled
import com.music.spotui.data.preferences.isAudioOffloadEnabled
import com.music.spotui.data.preferences.isAutoResumeOnBluetooth
import com.music.spotui.data.preferences.isAutoSkipUnavailable
import com.music.spotui.data.preferences.isAutoplayRadioEnabled
import com.music.spotui.data.preferences.isBatteryPromptDismissed
import com.music.spotui.data.preferences.isEqEnabled
import com.music.spotui.data.preferences.isFadeInOnPlay
import com.music.spotui.data.preferences.isFloatAudioOutput
import com.music.spotui.data.preferences.isHeadphoneCrossfeedEnabled
import com.music.spotui.data.preferences.isLimiterEnabled
import com.music.spotui.data.preferences.isMiniPlayerProgressVisible
import com.music.spotui.data.preferences.isPauseOnUnplugEnabled
import com.music.spotui.data.preferences.isPauseOnZeroVolume
import com.music.spotui.data.preferences.isSearchExplicitFilter
import com.music.spotui.data.preferences.isSpatial8dEnabled
import com.music.spotui.data.preferences.isSpectrumVisualizerEnabled
import com.music.spotui.data.preferences.setAdvancedSettingsEnabled
import com.music.spotui.data.preferences.setAudioFocusMode
import com.music.spotui.data.preferences.setAudioNormalizerTargetLufs
import com.music.spotui.data.preferences.setAudioOffloadEnabled
import com.music.spotui.data.preferences.setBassBoostStrength
import com.music.spotui.data.preferences.setBatteryPromptDismissed
import com.music.spotui.data.preferences.setBufferProfile
import com.music.spotui.data.preferences.setEqBrilliance
import com.music.spotui.data.preferences.setEqEnabled
import com.music.spotui.data.preferences.setEqPreampDb
import com.music.spotui.data.preferences.setEqSubBass
import com.music.spotui.data.preferences.setFadeInOnPlay
import com.music.spotui.data.preferences.setFloatAudioOutput
import com.music.spotui.data.preferences.setHeadphoneCrossfeedEnabled
import com.music.spotui.data.preferences.setLimiterEnabled
import com.music.spotui.data.preferences.setMiniPlayerProgressVisible
import com.music.spotui.data.preferences.setPauseOnUnplugEnabled
import com.music.spotui.data.preferences.setPauseOnZeroVolume
import com.music.spotui.data.preferences.setSearchExplicitFilter
import com.music.spotui.data.preferences.setSeekStepSeconds
import com.music.spotui.data.preferences.setSpatialProfile
import com.music.spotui.data.preferences.setSpatialRoomDelayMs
import com.music.spotui.data.preferences.setSpatial8dEnabled
import com.music.spotui.data.preferences.setSpatial8dIntensity
import com.music.spotui.data.preferences.setSpectrumVisualizerEnabled
import com.music.spotui.data.preferences.setAutoResumeOnBluetooth
import com.music.spotui.data.preferences.setAutoSkipUnavailable
import com.music.spotui.data.preferences.setAutoplayRadioEnabled
import com.music.spotui.deezer.DeezerCrypto
import com.music.spotui.di.CurrentSongState
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.notification.MediaSessionController
import com.music.spotui.ui.viewmodel.LibraryFilterType
import com.music.spotui.ui.viewmodel.LibrarySortOrder
import com.music.spotui.util.CheckSeverity
import com.music.spotui.util.StreamSanitizer
import com.music.spotui.util.SystemHealthChecker
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = MyApplication::class)
class PlaybackDspHealthUpgradeTest {

    @After
    fun tearDown() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SongPlayer.release()
        SongPlayer.clearAbLoop()
        setEqEnabled(context, true)
        setEqSubBass(context, 50f)
        setEqBrilliance(context, 50f)
        setEqPreampDb(context, 0f)
        setBassBoostStrength(context, 0)
        setLimiterEnabled(context, true)
        setHeadphoneCrossfeedEnabled(context, false)
        setSpatialRoomDelayMs(context, 12f)
        setSpatial8dEnabled(context, false)
        setSpatial8dIntensity(context, 0.85f)
        setAudioNormalizerTargetLufs(context, -14f)
        setBufferProfile(context, BufferProfile.BALANCED)
        setAudioFocusMode(context, AudioFocusMode.PAUSE_AND_DUCK)
        setSeekStepSeconds(context, 10)
        setSearchExplicitFilter(context, false)
        setAdvancedSettingsEnabled(context, false)
        setAudioOffloadEnabled(context, true)
        setBatteryPromptDismissed(context, false)
        ProviderRoutingPreferences.setLosslessOrder(context, listOf("tidal", "qobuz", "amazon", "deezer"))
        listOf("tidal", "qobuz", "amazon", "deezer").forEach {
            ProviderRoutingPreferences.setProviderEnabled(context, it, true)
        }
        ProviderRoutingPreferences.setSoloProvider(context, false)
        GameTranslatorEngine.setSourceLanguage(context, "auto")
        GameTranslatorEngine.setTargetLanguage(context, "en")
        GameTranslatorEngine.setAlwaysUnlockTranslator(context, false)
        GameTranslatorEngine.setContinuousListen(context, true)
    }

    @Test
    fun softwareDspAndSpatial_preserveConfigOnReset_andTransformStereoPcmBuffers() {
        val dsp = SpotuiDspAudioProcessor()
        dsp.eqEnabled = true
        dsp.setFromNormalizedSliders(
            subBass = 90f,
            bass = 80f,
            lowMid = 50f,
            vocal = 65f,
            highMid = 55f,
            treble = 75f,
            brilliance = 85f,
        )
        dsp.preampDb = -2.5f
        dsp.bassBoostDb = 5f
        dsp.normalizerEnabled = true
        dsp.limiterEnabled = true

        val pcm16Format = AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT)
        assertEquals(pcm16Format, dsp.configure(pcm16Format))
        dsp.flush()

        // Simulate a track transition reset and verify user DSP configuration is preserved.
        dsp.reset()
        assertTrue(dsp.eqEnabled)
        assertTrue(dsp.hasActiveProcessing())
        assertEquals(-2.5f, dsp.preampDb, 0.01f)
        assertEquals(5f, dsp.bassBoostDb, 0.01f)
        assertTrue(dsp.getBandGainsDb()[0] > 5f)

        val pcmFloatFormat = AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_FLOAT)
        assertEquals(pcmFloatFormat, dsp.configure(pcmFloatFormat))
        dsp.flush()

        val frames = 256
        val inputFloats = ByteBuffer.allocateDirect(frames * 8).order(ByteOrder.nativeOrder())
        val originalLeft = FloatArray(frames)
        for (i in 0 until frames) {
            val s = (sin(i * 0.05) * 0.45).toFloat()
            originalLeft[i] = s
            inputFloats.putFloat(s)
            inputFloats.putFloat(s * 0.7f)
        }
        inputFloats.flip()

        dsp.queueInput(inputFloats)
        val outDsp = dsp.output.order(ByteOrder.nativeOrder())
        assertEquals(frames * 8, outDsp.remaining())
        var maxDelta = 0f
        for (i in 0 until frames) {
            val l = outDsp.float
            outDsp.float
            maxDelta = maxOf(maxDelta, abs(l - originalLeft[i]))
        }
        assertTrue("Expected audible DSP transformation, maxDelta=$maxDelta", maxDelta > 0.01f)

        val spatial = SpatialWideningAudioProcessor()
        spatial.enabled = true
        spatial.width = 0.8f
        spatial.profile = SpatialProfile.BINAURAL_HEADPHONES
        spatial.headphoneCrossfeed = true
        spatial.roomDelayMs = 14f
        assertEquals(pcm16Format, spatial.configure(pcm16Format))
        spatial.flush()

        // Resetting spatial processor across tracks must keep user settings intact.
        spatial.reset()
        assertTrue(spatial.enabled)
        assertEquals(SpatialProfile.BINAURAL_HEADPHONES, spatial.profile)
        assertTrue(spatial.headphoneCrossfeed)

        assertEquals(pcm16Format, spatial.configure(pcm16Format))
        spatial.flush()
        val inputPcm16 = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until frames) {
            inputPcm16.putShort((sin(i * 0.1) * 14_000).toInt().toShort())
            inputPcm16.putShort((sin(i * 0.1 + 1.1) * 12_000).toInt().toShort())
        }
        inputPcm16.flip()
        spatial.queueInput(inputPcm16)
        val outSpatial = spatial.output
        assertEquals(frames * 4, outSpatial.remaining())
        assertTrue(spatial.activeDescription.contains("Headphone 3D"))
    }

    @Test
    fun deezerCrypto_decryptsOnlyEveryThirdStripeChunk_andStreamSanitizerPermitsDeezerUri() {
        val trackId = "3135556"
        val key = DeezerCrypto.trackKeyBytes(trackId)
        assertEquals(16, key.size)

        val plainChunk = ByteArray(DeezerCrypto.CHUNK_SIZE) { ((it * 31) and 0xFF).toByte() }
        val cipher = Cipher.getInstance("Blowfish/CBC/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "Blowfish"), IvParameterSpec(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7)))
        val encryptedChunk0 = cipher.doFinal(plainChunk)

        val work0 = encryptedChunk0.copyOf()
        DeezerCrypto.decryptChunkInPlace(work0, DeezerCrypto.CHUNK_SIZE, chunkIndex = 0L, keyBytes = key)
        assertArrayEquals(plainChunk, work0)

        // Chunk index 1 is unencrypted on Deezer CDN and must not be modified.
        val unencryptedChunk1 = plainChunk.copyOf()
        DeezerCrypto.decryptChunkInPlace(unencryptedChunk1, DeezerCrypto.CHUNK_SIZE, chunkIndex = 1L, keyBytes = key)
        assertArrayEquals(plainChunk, unencryptedChunk1)

        val deezerUri = "deezer://track/$trackId?enc=1&url=abc"
        assertEquals(deezerUri, StreamSanitizer.sanitizeStreamUrl(deezerUri))
        assertTrue(StreamSanitizer.validateAudioStream(deezerUri))

        val stripped = StreamSanitizer.sanitizeStreamUrl("http://cdn.example.com/audio.flac?utm_source=test&token=xyz")
        assertEquals("https://cdn.example.com/audio.flac?token=xyz", stripped)
    }

    @Test
    fun settingsPreferences_andProviderRouting_persistAllDspPlaybackAndFilterValues() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        setEqEnabled(context, false)
        setEqSubBass(context, 78f)
        setEqBrilliance(context, 66f)
        setEqPreampDb(context, -4.5f)
        setBassBoostStrength(context, 650)
        setLimiterEnabled(context, false)
        setHeadphoneCrossfeedEnabled(context, true)
        setSpatialRoomDelayMs(context, 22f)
        setSpatialProfile(context, SpatialProfile.CONCERT_HALL)
        setAudioNormalizerTargetLufs(context, -11f)

        assertFalse(isEqEnabled(context))
        assertEquals(78f, getEqSubBass(context), 0.01f)
        assertEquals(66f, getEqBrilliance(context), 0.01f)
        assertEquals(-4.5f, getEqPreampDb(context), 0.01f)
        assertEquals(650, getBassBoostStrength(context))
        assertFalse(isLimiterEnabled(context))
        assertTrue(isHeadphoneCrossfeedEnabled(context))
        assertEquals(22f, getSpatialRoomDelayMs(context), 0.01f)
        assertEquals(SpatialProfile.CONCERT_HALL, getSpatialProfile(context))
        assertEquals(-11f, getAudioNormalizerTargetLufs(context), 0.01f)

        setBufferProfile(context, BufferProfile.HIGH_STABILITY)
        setAudioFocusMode(context, AudioFocusMode.STRICT_EXCLUSIVE)
        setSeekStepSeconds(context, 30)
        setAutoplayRadioEnabled(context, false)
        setAutoResumeOnBluetooth(context, false)
        setPauseOnUnplugEnabled(context, false)
        setPauseOnZeroVolume(context, true)
        setAutoSkipUnavailable(context, false)
        setFadeInOnPlay(context, false)
        setFloatAudioOutput(context, false)
        setSpectrumVisualizerEnabled(context, false)
        setMiniPlayerProgressVisible(context, false)
        setSearchExplicitFilter(context, true)

        assertEquals(BufferProfile.HIGH_STABILITY, getBufferProfile(context))
        assertEquals(AudioFocusMode.STRICT_EXCLUSIVE, getAudioFocusMode(context))
        assertEquals(30, getSeekStepSeconds(context))
        assertFalse(isAutoplayRadioEnabled(context))
        assertFalse(isAutoResumeOnBluetooth(context))
        assertFalse(isPauseOnUnplugEnabled(context))
        assertTrue(isPauseOnZeroVolume(context))
        assertFalse(isAutoSkipUnavailable(context))
        assertFalse(isFadeInOnPlay(context))
        assertFalse(isFloatAudioOutput(context))
        assertFalse(isSpectrumVisualizerEnabled(context))
        assertFalse(isMiniPlayerProgressVisible(context))
        assertTrue(isSearchExplicitFilter(context))

        ProviderRoutingPreferences.setLosslessOrder(context, listOf("qobuz", "deezer", "tidal", "amazon"))
        assertEquals(listOf("qobuz", "deezer", "tidal", "amazon"), ProviderRoutingPreferences.losslessOrder(context))
        val promoted = ProviderRoutingPreferences.setPrimaryProvider(context, "amazon")
        assertEquals("amazon", promoted.first())
    }

    @Test
    fun queueTools_abLoop_telemetry_andQualityRestartBehaviorWorkSafely() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val state = CurrentSongState(context)
        SongPlayer.bindState(state)

        val tracks = listOf(
            SongsModel(id = 1, title = "Track A", album = "Alb", singer = "Art", coverUri = "", url = "q1", spotifyTrackId = "sp1"),
            SongsModel(id = 2, title = "Track B", album = "Alb", singer = "Art", coverUri = "", url = "q2", spotifyTrackId = "sp2"),
            SongsModel(id = 3, title = "Track B Dup", album = "Alb", singer = "Art", coverUri = "", url = "q2", spotifyTrackId = "sp2"),
            SongsModel(id = 4, title = "Track C", album = "Alb", singer = "Art", coverUri = "", url = "q3", spotifyTrackId = "sp3"),
            SongsModel(id = 5, title = "Track D", album = "Alb", singer = "Art", coverUri = "", url = "q4", spotifyTrackId = "sp4"),
        )
        state.updateQueue(tracks)
        state.updateSongState("", "Track B", "Art", false, songId = 2, songIndex = 1, album = "Alb")

        val removedDuplicates = state.deduplicateQueue()
        assertEquals(1, removedDuplicates)
        assertEquals(4, state.queue.value.size)
        assertEquals(2, state.queue.value[state.songIndex.value].id)

        val shuffledTailCount = state.shuffleUpcoming()
        assertEquals(2, shuffledTailCount)
        assertEquals(2, state.queue.value[state.songIndex.value].id)

        val trimmed = state.trimPlayedHistory()
        assertEquals(1, trimmed)
        assertEquals(0, state.songIndex.value)
        assertEquals(2, state.queue.value.first().id)

        // A-B Loop validation
        SongPlayer.setAbLoopStart(15_000L)
        SongPlayer.setAbLoopEnd(15_200L) // < 500ms gap ignored
        assertEquals(15_000L, SongPlayer.abLoopStartMs)
        assertEquals(-1L, SongPlayer.abLoopEndMs)

        SongPlayer.setAbLoopEnd(45_000L)
        assertEquals(45_000L, SongPlayer.abLoopEndMs)

        val telemetry = SongPlayer.telemetrySnapshot(context)
        assertEquals(15_000L, telemetry.abLoopStartMs)
        assertEquals(45_000L, telemetry.abLoopEndMs)

        SongPlayer.clearAbLoop()
        assertEquals(-1L, SongPlayer.abLoopStartMs)
        assertEquals(-1L, SongPlayer.abLoopEndMs)

        // Restart when queue has an active track triggers quality re-resolution
        assertTrue(SongPlayer.restartCurrentTrackForQualityChange(context))

        // Restart when no active track loaded should safely return false
        state.updateQueue(emptyList())
        SongPlayer.release()
        assertFalse(SongPlayer.restartCurrentTrackForQualityChange(context))
    }

    @Test
    fun systemHealthChecker_returnsAll21DistinctChecksAndRemediations() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val report = SystemHealthChecker.runFullScan(context)

        assertEquals(21, report.checks.size)
        assertEquals(21, report.checks.map { it.id }.toSet().size)
        assertTrue(report.durationMs >= 1L)
        assertEquals(
            21,
            report.okCount + report.warnCount + report.failCount,
        )
        assertTrue(report.checks.all { it.title.isNotBlank() && it.summary.isNotBlank() })
        assertNotEquals(CheckSeverity.FAIL, report.checks.first { it.id == "crashes" }.severity)
    }

    @Test
    fun advancedSettings_oneUiOffload_metadataAndLibraryOptions_workSafely() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Default states
        assertFalse(isAdvancedSettingsEnabled(context))
        assertTrue(isAudioOffloadEnabled(context))
        assertFalse(isBatteryPromptDismissed(context))

        // Persistence round-trip
        setAdvancedSettingsEnabled(context, true)
        setAudioOffloadEnabled(context, false)
        setBatteryPromptDismissed(context, true)

        assertTrue(isAdvancedSettingsEnabled(context))
        assertFalse(isAudioOffloadEnabled(context))
        assertTrue(isBatteryPromptDismissed(context))

        // Hardware offload policy refresh + One UI metadata propagation do not throw without active player
        SongPlayer.refreshAudioOffloadPolicy(context)
        SongPlayer.setNowPlayingMeta(
            title = "Midnight City",
            artist = "M83",
            coverUri = "https://example.com/art.jpg",
            album = "Hurry Up, We're Dreaming",
            durationMs = 243_000L,
        )

        // MediaSessionController safe disconnected operations
        MediaSessionController.disconnect()
        assertFalse(MediaSessionController.state.value.connected)
        MediaSessionController.playPause()
        MediaSessionController.seekTo(12_000L)

        // Library filters and sort orders cover all modes cleanly
        assertEquals(4, LibraryFilterType.entries.size)
        assertEquals(4, LibrarySortOrder.entries.size)
        assertTrue(LibrarySortOrder.entries.all { it.label.isNotBlank() })
    }

    @Test
    fun backgroundWorkerThreads_neverCrashAccessingSongPlayerOrSessionResumableState() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SongPlayer.ensureCreated(context)
        SongPlayer.setNowPlayingMeta(
            title = "Midnight City",
            artist = "M83",
            coverUri = "https://example.com/art.jpg",
            album = "Hurry Up, We're Dreaming",
            durationMs = 243_000L,
        )
        SongPlayer.playSong("spotify:track:bgtest123|Midnight City M83", context, "song/99")
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        // Invoke every public query & control path from Dispatchers.IO / Dispatchers.Default
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            assertFalse(com.music.spotui.ui.notification.ServiceSessionHolder.isResumable(SongPlayer.exoPlayer) && !SongPlayer.isResumableState)
            SongPlayer.isPlaying()
            SongPlayer.getCurrentPosition()
            SongPlayer.getDuration()
            SongPlayer.isPrepared()
            SongPlayer.getPlaybackSpeed()
            val telemetry = SongPlayer.telemetrySnapshot(context)
            assertEquals("Midnight City", SongPlayer.nowPlayingMetaSnapshot().title)
            assertTrue(telemetry.sessionId >= 0)
            SongPlayer.refreshAudioEffects(context)
            SongPlayer.seekTo(12_000L)
            SongPlayer.togglePlay()
            SongPlayer.pause()
            SongPlayer.play()
            com.music.spotui.engine.MediaNotificationAdapter.showOrUpdateExitMiniNotification(context)
        }
        SystemHealthChecker.resetCooldownForTest()
        val report = SystemHealthChecker.runFullScan(context, skipNetworkProbesForTest = true)
        assertEquals(21, report.checks.size)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
    }

    @Test
    fun v256_advanced8dPreferences_legacyOrbitMigration_andTranslatorEngineSettingsWork() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(BuildConfig.VERSION_NAME.startsWith("2.5.7"))

        // Legacy orbit key migrates seamlessly to Advanced 8D and clears on write
        val rawPrefs = context.getSharedPreferences("spotui_settings", Context.MODE_PRIVATE)
        rawPrefs.edit().remove("audio_spatial_8d_advanced").putBoolean("audio_spatial_8d_orbit", true).apply()
        assertTrue(isSpatial8dEnabled(context))

        setSpatial8dEnabled(context, false)
        assertFalse(isSpatial8dEnabled(context))
        assertFalse(rawPrefs.contains("audio_spatial_8d_orbit"))

        setSpatial8dIntensity(context, 1.35f)
        assertEquals(1.35f, getSpatial8dIntensity(context), 0.01f)
        setSpatial8dIntensity(context, 9.99f)
        assertEquals(1.50f, getSpatial8dIntensity(context), 0.01f)

        // GameTranslatorEngine persistence + swap + target languages
        GameTranslatorEngine.hydratePreferences(context)
        GameTranslatorEngine.setSourceLanguage(context, "es")
        GameTranslatorEngine.setTargetLanguage(context, "ja")
        GameTranslatorEngine.setAlwaysUnlockTranslator(context, true)
        GameTranslatorEngine.setContinuousListen(context, false)
        assertEquals("es", GameTranslatorEngine.sourceLangFlow.value)
        assertEquals("ja", GameTranslatorEngine.targetLangFlow.value)
        assertTrue(GameTranslatorEngine.alwaysUnlockTranslatorFlow.value)
        assertFalse(GameTranslatorEngine.continuousListenFlow.value)

        GameTranslatorEngine.swapLanguages(context)
        assertEquals("ja", GameTranslatorEngine.sourceLangFlow.value)
        assertEquals("es", GameTranslatorEngine.targetLangFlow.value)
        assertTrue(GameTranslatorEngine.supportedTargetLanguages.none { it.code == "auto" })
    }
}
