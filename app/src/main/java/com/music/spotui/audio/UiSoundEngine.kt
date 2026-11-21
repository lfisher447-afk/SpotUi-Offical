package com.music.spotui.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.music.spotui.data.preferences.ExperiencePreferences
import com.music.spotui.data.preferences.UiSoundStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Procedural, zero-asset low-latency UI sound synthesizer for tabs, toggles, sliders, and buttons.
 *
 * Uses `USAGE_ASSISTANCE_SONIFICATION` + `CONTENT_TYPE_SONIFICATION` so feedback chimes play
 * smoothly without interrupting active music playback focus or stealing ExoPlayer sessions.
 */
object UiSoundEngine {

    private const val SAMPLE_RATE = 24_000
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pcmCache = ConcurrentHashMap<String, ShortArray>()
    private val lastSliderTickMs = AtomicLong(0L)
    private val lastTabSoundMs = AtomicLong(0L)

    fun playTabSound(context: Context) {
        if (!ExperiencePreferences.isUiSoundsEnabled(context)) return
        if (!ExperiencePreferences.isTabSoundEnabled(context)) return
        val now = System.currentTimeMillis()
        if (now - lastTabSoundMs.get() < 55L) return
        lastTabSoundMs.set(now)

        val style = ExperiencePreferences.getUiSoundStyle(context)
        val vol = ExperiencePreferences.getUiSoundVolume(context)
        if (vol <= 0) return
        scope.launch {
            val pcm = when (style) {
                UiSoundStyle.CRISP -> getOrSynthesize("tab_crisp", 26) { t, dur ->
                    val env = exp(-t * 140.0)
                    val s = 0.65 * sin(2.0 * PI * 740.0 * t) + 0.35 * sin(2.0 * PI * 1480.0 * t)
                    s * env * fadeOutEdge(t, dur)
                }
                UiSoundStyle.WARM -> getOrSynthesize("tab_warm", 32) { t, dur ->
                    val env = exp(-t * 95.0)
                    val s = sin(2.0 * PI * 523.25 * t) * 0.75 + sin(2.0 * PI * 784.0 * t) * 0.25
                    s * env * fadeOutEdge(t, dur)
                }
                UiSoundStyle.MINIMAL -> getOrSynthesize("tab_min", 18) { t, dur ->
                    val env = exp(-t * 180.0)
                    sin(2.0 * PI * 640.0 * t) * 0.6 * env * fadeOutEdge(t, dur)
                }
            }
            playPcmBuffer(pcm, vol * 0.65f / 100f)
        }
    }

    fun playToggleSound(context: Context, enabled: Boolean) {
        if (!ExperiencePreferences.isUiSoundsEnabled(context)) return
        if (!ExperiencePreferences.isToggleSoundEnabled(context)) return
        val style = ExperiencePreferences.getUiSoundStyle(context)
        val vol = ExperiencePreferences.getUiSoundVolume(context)
        if (vol <= 0) return

        scope.launch {
            val pcm = if (enabled) {
                val baseHz = when (style) {
                    UiSoundStyle.CRISP -> 660.0
                    UiSoundStyle.WARM -> 523.25
                    UiSoundStyle.MINIMAL -> 587.33
                }
                getOrSynthesize("toggle_on_${style.key}", 46) { t, dur ->
                    val freq = if (t < 0.022) baseHz else baseHz * 1.498
                    val localT = if (t < 0.022) t else t - 0.022
                    val env = exp(-localT * 110.0)
                    (sin(2.0 * PI * freq * t) * 0.78 + sin(2.0 * PI * freq * 2.0 * t) * 0.22) * env * fadeOutEdge(t, dur)
                }
            } else {
                val baseHz = when (style) {
                    UiSoundStyle.CRISP -> 587.33
                    UiSoundStyle.WARM -> 440.0
                    UiSoundStyle.MINIMAL -> 493.88
                }
                getOrSynthesize("toggle_off_${style.key}", 42) { t, dur ->
                    val freq = if (t < 0.018) baseHz else baseHz * 0.75
                    val localT = if (t < 0.018) t else t - 0.018
                    val env = exp(-localT * 125.0)
                    sin(2.0 * PI * freq * t) * 0.75 * env * fadeOutEdge(t, dur)
                }
            }
            playPcmBuffer(pcm, vol * 0.72f / 100f)
        }
    }

    fun playSliderTick(context: Context, normalizedValue: Float) {
        if (!ExperiencePreferences.isUiSoundsEnabled(context)) return
        if (!ExperiencePreferences.isSliderSoundEnabled(context)) return
        val now = System.currentTimeMillis()
        if (now - lastSliderTickMs.get() < 40L) return
        lastSliderTickMs.set(now)

        val vol = ExperiencePreferences.getUiSoundVolume(context)
        if (vol <= 0) return
        val style = ExperiencePreferences.getUiSoundStyle(context)
        val bucket = (normalizedValue.coerceIn(0f, 1f) * 10f).toInt().coerceIn(0, 10)
        scope.launch {
            val base = when (style) {
                UiSoundStyle.CRISP -> 780.0
                UiSoundStyle.WARM -> 560.0
                UiSoundStyle.MINIMAL -> 660.0
            }
            val freq = base + (bucket * 48.0)
            val pcm = getOrSynthesize("slider_${style.key}_$bucket", 14) { t, dur ->
                val env = exp(-t * 260.0)
                sin(2.0 * PI * freq * t) * 0.55 * env * fadeOutEdge(t, dur)
            }
            playPcmBuffer(pcm, vol * 0.48f / 100f)
        }
    }

    fun playTapChime(context: Context) {
        if (!ExperiencePreferences.isUiSoundsEnabled(context)) return
        if (!ExperiencePreferences.isTabSoundEnabled(context)) return
        val vol = ExperiencePreferences.getUiSoundVolume(context)
        if (vol <= 0) return
        scope.launch {
            val pcm = getOrSynthesize("tap_chime", 16) { t, dur ->
                val env = exp(-t * 220.0)
                sin(2.0 * PI * 920.0 * t) * 0.5 * env * fadeOutEdge(t, dur)
            }
            playPcmBuffer(pcm, vol * 0.45f / 100f)
        }
    }

    private fun fadeOutEdge(t: Double, durationSec: Double): Double {
        val rem = (durationSec - t).coerceAtLeast(0.0)
        val edge = 0.003
        return if (rem < edge) rem / edge else 1.0
    }

    private inline fun getOrSynthesize(
        key: String,
        durationMs: Int,
        crossinline generator: (tSec: Double, totalSec: Double) -> Double,
    ): ShortArray = pcmCache.getOrPut(key) {
        val sampleCount = ((SAMPLE_RATE * durationMs) / 1000).coerceAtLeast(64)
        val totalSec = sampleCount.toDouble() / SAMPLE_RATE
        ShortArray(sampleCount) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val amp = generator(t, totalSec).coerceIn(-1.0, 1.0)
            (amp * 24_000.0).toInt().toShort()
        }
    }

    private fun playPcmBuffer(pcm: ShortArray, gain: Float) {
        if (pcm.isEmpty() || gain <= 0.01f) return
        runCatching {
            val byteCount = pcm.size * 2
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val format = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val track = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(byteCount)
                .build()
            track.write(pcm, 0, pcm.size)
            track.setVolume(gain.coerceIn(0f, 1f))
            track.play()
            val sleepMs = ((pcm.size * 1000L) / SAMPLE_RATE) + 25L
            Thread.sleep(sleepMs)
            runCatching { track.stop() }
            runCatching { track.release() }
        }
    }
}
