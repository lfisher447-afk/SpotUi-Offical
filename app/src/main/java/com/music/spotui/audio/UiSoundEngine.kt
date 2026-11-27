package com.music.spotui.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool
import com.music.spotui.data.preferences.ExperiencePreferences
import com.music.spotui.data.preferences.UiSoundStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Low-latency procedural UI sound synthesizer for tabs, toggles, sliders, and buttons.
 *
 * Uses a persistent [SoundPool] preloaded with synthesized PCM WAV assets routed on
 * `USAGE_MEDIA` + `CONTENT_TYPE_SONIFICATION` (without requesting audio focus), so clicks
 * and chimes play reliably at zero-allocation latency over speaker, wired headphones, and
 * Bluetooth headsets without truncating in the Android audio HAL.
 */
object UiSoundEngine {

    private const val SAMPLE_RATE = 24_000
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pcmCache = ConcurrentHashMap<String, ShortArray>()
    private val soundIdByKey = ConcurrentHashMap<String, Int>()
    private val loadedSoundIds = ConcurrentHashMap<Int, Boolean>()
    private val pendingGainBySoundId = ConcurrentHashMap<Int, Float>()
    private val loadingKeys = ConcurrentHashMap.newKeySet<String>()

    private val lastSliderTickMs = AtomicLong(0L)
    private val lastTabSoundMs = AtomicLong(0L)
    private val lastTapChimeMs = AtomicLong(0L)
    private val warmupStarted = AtomicBoolean(false)

    @Volatile
    private var soundPool: SoundPool? = null
    private val poolLock = Any()

    /**
     * Preloads the primary UI sound bank into [SoundPool] in the background so the very
     * first button or tab tap fires with <5ms latency.
     */
    fun warmup(context: Context) {
        val appCtx = context.applicationContext
        if (!ExperiencePreferences.isUiSoundsEnabled(appCtx)) return
        if (!warmupStarted.compareAndSet(false, true)) return
        scope.launch {
            ensureSoundPool()
            val style = ExperiencePreferences.getUiSoundStyle(appCtx)
            preloadKey(appCtx, "tap_${style.key}", 38) { t, dur -> tapWave(style, t, dur) }
            preloadKey(appCtx, "tab_${style.key}", 52) { t, dur -> tabWave(style, t, dur) }
            preloadKey(appCtx, "toggle_on_${style.key}", 68) { t, dur -> toggleOnWave(style, t, dur) }
            preloadKey(appCtx, "toggle_off_${style.key}", 58) { t, dur -> toggleOffWave(style, t, dur) }
        }
    }

    fun playTabSound(context: Context) {
        val appCtx = context.applicationContext
        if (!ExperiencePreferences.isUiSoundsEnabled(appCtx)) return
        if (!ExperiencePreferences.isTabSoundEnabled(appCtx)) return
        val now = System.currentTimeMillis()
        if (now - lastTabSoundMs.get() < 45L) return
        lastTabSoundMs.set(now)

        val style = ExperiencePreferences.getUiSoundStyle(appCtx)
        val vol = ExperiencePreferences.getUiSoundVolume(appCtx)
        if (vol <= 0) return
        val gain = (vol / 100f * 0.85f).coerceIn(0.05f, 1.0f)
        triggerSound(appCtx, "tab_${style.key}", 52, gain) { t, dur -> tabWave(style, t, dur) }
    }

    fun playToggleSound(context: Context, enabled: Boolean) {
        val appCtx = context.applicationContext
        if (!ExperiencePreferences.isUiSoundsEnabled(appCtx)) return
        if (!ExperiencePreferences.isToggleSoundEnabled(appCtx)) return
        val style = ExperiencePreferences.getUiSoundStyle(appCtx)
        val vol = ExperiencePreferences.getUiSoundVolume(appCtx)
        if (vol <= 0) return
        val gain = (vol / 100f * 0.90f).coerceIn(0.05f, 1.0f)
        if (enabled) {
            triggerSound(appCtx, "toggle_on_${style.key}", 68, gain) { t, dur ->
                toggleOnWave(style, t, dur)
            }
        } else {
            triggerSound(appCtx, "toggle_off_${style.key}", 58, gain) { t, dur ->
                toggleOffWave(style, t, dur)
            }
        }
    }

    fun playSliderTick(context: Context, normalizedValue: Float) {
        val appCtx = context.applicationContext
        if (!ExperiencePreferences.isUiSoundsEnabled(appCtx)) return
        if (!ExperiencePreferences.isSliderSoundEnabled(appCtx)) return
        val now = System.currentTimeMillis()
        if (now - lastSliderTickMs.get() < 38L) return
        lastSliderTickMs.set(now)

        val vol = ExperiencePreferences.getUiSoundVolume(appCtx)
        if (vol <= 0) return
        val style = ExperiencePreferences.getUiSoundStyle(appCtx)
        val bucket = (normalizedValue.coerceIn(0f, 1f) * 10f).toInt().coerceIn(0, 10)
        val gain = (vol / 100f * 0.65f).coerceIn(0.04f, 0.95f)
        val base = when (style) {
            UiSoundStyle.CRISP -> 780.0
            UiSoundStyle.WARM -> 560.0
            UiSoundStyle.MINIMAL -> 660.0
        }
        val freq = base + (bucket * 48.0)
        triggerSound(appCtx, "slider_${style.key}_$bucket", 24, gain) { t, dur ->
            val env = exp(-t * 170.0)
            sin(2.0 * PI * freq * t) * 0.72 * env * fadeOutEdge(t, dur)
        }
    }

    fun playTapChime(context: Context) {
        val appCtx = context.applicationContext
        if (!ExperiencePreferences.isUiSoundsEnabled(appCtx)) return
        if (!ExperiencePreferences.isTabSoundEnabled(appCtx)) return
        val now = System.currentTimeMillis()
        if (now - lastTapChimeMs.get() < 32L) return
        lastTapChimeMs.set(now)

        val style = ExperiencePreferences.getUiSoundStyle(appCtx)
        val vol = ExperiencePreferences.getUiSoundVolume(appCtx)
        if (vol <= 0) return
        val gain = (vol / 100f * 0.85f).coerceIn(0.05f, 1.0f)
        triggerSound(appCtx, "tap_${style.key}", 38, gain) { t, dur -> tapWave(style, t, dur) }
    }

    private fun tapWave(style: UiSoundStyle, t: Double, dur: Double): Double = when (style) {
        UiSoundStyle.CRISP -> {
            val env = exp(-t * 110.0)
            val s = 0.65 * sin(2.0 * PI * 960.0 * t) + 0.35 * sin(2.0 * PI * 1920.0 * t)
            s * 0.90 * env * fadeOutEdge(t, dur)
        }
        UiSoundStyle.WARM -> {
            val env = exp(-t * 90.0)
            val s = 0.75 * sin(2.0 * PI * 680.0 * t) + 0.25 * sin(2.0 * PI * 1020.0 * t)
            s * 0.88 * env * fadeOutEdge(t, dur)
        }
        UiSoundStyle.MINIMAL -> {
            val env = exp(-t * 125.0)
            sin(2.0 * PI * 820.0 * t) * 0.78 * env * fadeOutEdge(t, dur)
        }
    }

    private fun tabWave(style: UiSoundStyle, t: Double, dur: Double): Double = when (style) {
        UiSoundStyle.CRISP -> {
            val env = exp(-t * 95.0)
            val s = 0.65 * sin(2.0 * PI * 740.0 * t) + 0.35 * sin(2.0 * PI * 1480.0 * t)
            s * 0.92 * env * fadeOutEdge(t, dur)
        }
        UiSoundStyle.WARM -> {
            val env = exp(-t * 75.0)
            val s = 0.75 * sin(2.0 * PI * 523.25 * t) + 0.25 * sin(2.0 * PI * 784.0 * t)
            s * 0.90 * env * fadeOutEdge(t, dur)
        }
        UiSoundStyle.MINIMAL -> {
            val env = exp(-t * 115.0)
            sin(2.0 * PI * 640.0 * t) * 0.80 * env * fadeOutEdge(t, dur)
        }
    }

    private fun toggleOnWave(style: UiSoundStyle, t: Double, dur: Double): Double {
        val baseHz = when (style) {
            UiSoundStyle.CRISP -> 660.0
            UiSoundStyle.WARM -> 523.25
            UiSoundStyle.MINIMAL -> 587.33
        }
        val freq = if (t < 0.026) baseHz else baseHz * 1.498
        val localT = if (t < 0.026) t else t - 0.026
        val env = exp(-localT * 85.0)
        return (sin(2.0 * PI * freq * t) * 0.78 + sin(2.0 * PI * freq * 2.0 * t) * 0.22) * 0.92 * env * fadeOutEdge(t, dur)
    }

    private fun toggleOffWave(style: UiSoundStyle, t: Double, dur: Double): Double {
        val baseHz = when (style) {
            UiSoundStyle.CRISP -> 587.33
            UiSoundStyle.WARM -> 440.0
            UiSoundStyle.MINIMAL -> 493.88
        }
        val freq = if (t < 0.022) baseHz else baseHz * 0.75
        val localT = if (t < 0.022) t else t - 0.022
        val env = exp(-localT * 95.0)
        return sin(2.0 * PI * freq * t) * 0.85 * env * fadeOutEdge(t, dur)
    }

    private fun fadeOutEdge(t: Double, durationSec: Double): Double {
        val rem = (durationSec - t).coerceAtLeast(0.0)
        val edge = 0.004
        return if (rem < edge) rem / edge else 1.0
    }

    private inline fun getOrSynthesize(
        key: String,
        durationMs: Int,
        crossinline generator: (tSec: Double, totalSec: Double) -> Double,
    ): ShortArray = pcmCache.getOrPut(key) {
        val sampleCount = ((SAMPLE_RATE * durationMs) / 1000).coerceAtLeast(128)
        val totalSec = sampleCount.toDouble() / SAMPLE_RATE
        ShortArray(sampleCount) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val amp = generator(t, totalSec).coerceIn(-1.0, 1.0)
            (amp * 28_000.0).toInt().toShort()
        }
    }

    private fun ensureSoundPool(): SoundPool? {
        soundPool?.let { return it }
        synchronized(poolLock) {
            soundPool?.let { return it }
            return runCatching {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                SoundPool.Builder()
                    .setMaxStreams(6)
                    .setAudioAttributes(attrs)
                    .build()
                    .also { pool ->
                        pool.setOnLoadCompleteListener { sp, sampleId, status ->
                            if (status == 0) {
                                loadedSoundIds[sampleId] = true
                                pendingGainBySoundId.remove(sampleId)?.let { pendingGain ->
                                    sp.play(sampleId, pendingGain, pendingGain, 1, 0, 1.0f)
                                }
                            } else {
                                pendingGainBySoundId.remove(sampleId)
                            }
                        }
                        soundPool = pool
                    }
            }.getOrNull()
        }
    }

    private inline fun triggerSound(
        context: Context,
        key: String,
        durationMs: Int,
        gain: Float,
        crossinline generator: (tSec: Double, totalSec: Double) -> Double,
    ) {
        val pool = ensureSoundPool()
        val existingId = soundIdByKey[key]
        if (pool != null && existingId != null && loadedSoundIds[existingId] == true) {
            // Fast path: non-blocking play directly on SoundPool binder thread
            val stream = runCatching { pool.play(existingId, gain, gain, 1, 0, 1.0f) }.getOrDefault(0)
            if (stream != 0) return
        }
        scope.launch {
            val loadedId = preloadKey(context, key, durationMs, generator)
            val sp = ensureSoundPool()
            if (sp != null && loadedId != null) {
                if (loadedSoundIds[loadedId] == true) {
                    val played = runCatching { sp.play(loadedId, gain, gain, 1, 0, 1.0f) }.getOrDefault(0)
                    if (played != 0) return@launch
                } else {
                    // SoundPool is still finishing async load; OnLoadCompleteListener will play it
                    pendingGainBySoundId[loadedId] = gain
                    return@launch
                }
            }
            val pcm = getOrSynthesize(key, durationMs, generator)
            playPcmBufferFallback(pcm, gain)
        }
    }

    private inline fun preloadKey(
        context: Context,
        key: String,
        durationMs: Int,
        crossinline generator: (tSec: Double, totalSec: Double) -> Double,
    ): Int? {
        soundIdByKey[key]?.let { return it }
        if (!loadingKeys.add(key)) return soundIdByKey[key]
        return try {
            val sp = ensureSoundPool() ?: return null
            val pcm = getOrSynthesize(key, durationMs, generator)
            val dir = File(context.cacheDir, "spotui_ui_sounds").apply { mkdirs() }
            val wavFile = File(dir, "$key.wav")
            if (!wavFile.exists() || wavFile.length() < 44L) {
                writePcm16MonoWav(wavFile, pcm, SAMPLE_RATE)
            }
            val id = runCatching { sp.load(wavFile.absolutePath, 1) }.getOrNull() ?: 0
            if (id > 0) {
                soundIdByKey[key] = id
                id
            } else {
                null
            }
        } finally {
            loadingKeys.remove(key)
        }
    }

    private fun writePcm16MonoWav(target: File, pcm: ShortArray, sampleRate: Int) {
        val dataBytes = pcm.size * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + dataBytes)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // PCM chunk size
            putShort(1) // PCM format
            putShort(1) // Mono
            putInt(sampleRate)
            putInt(sampleRate * 2) // ByteRate
            putShort(2) // BlockAlign
            putShort(16) // BitsPerSample
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataBytes)
        }
        val body = ByteBuffer.allocate(dataBytes).order(ByteOrder.LITTLE_ENDIAN).apply {
            for (sample in pcm) putShort(sample)
        }
        FileOutputStream(target).use { out ->
            out.write(header.array())
            out.write(body.array())
        }
    }

    private fun playPcmBufferFallback(pcm: ShortArray, gain: Float) {
        if (pcm.isEmpty() || gain <= 0.01f) return
        runCatching {
            val byteCount = pcm.size * 2
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val format = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(byteCount)
            val track = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(minBuf)
                .build()
            track.write(pcm, 0, pcm.size)
            track.setVolume(gain.coerceIn(0f, 1f))
            track.play()
            // Hold open long enough so Android audio HAL + Bluetooth pipelines never truncate tail
            val drainMs = (((pcm.size * 1000L) / SAMPLE_RATE) + 220L).coerceAtLeast(240L)
            Thread.sleep(drainMs)
            runCatching { track.stop() }
            runCatching { track.release() }
        }
    }
}
