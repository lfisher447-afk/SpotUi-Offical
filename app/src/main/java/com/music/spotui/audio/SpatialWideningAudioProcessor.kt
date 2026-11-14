package com.music.spotui.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.music.spotui.data.preferences.SpatialProfile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * High-precision stereo spatializer & accessibility audio processor.
 *
 * Combines Mid/Side ambience expansion with sample-rate-aware Haas inter-aural
 * micro-delays, low-frequency center anchoring (so bass punch stays tight), early
 * room reflections for Cinema/Concert Hall modes, optional HRTF headphone crossfeed,
 * mono fold-down, and stereo balance.
 *
 * Supports both PCM-16 and PCM-FLOAT, and preserves all user spatial configuration
 * across `onReset()` track changes & seeks.
 */
@UnstableApi
class SpatialWideningAudioProcessor : BaseAudioProcessor() {
    @Volatile var enabled: Boolean = false
    @Volatile var width: Float = 0f
    @Volatile var profile: SpatialProfile = SpatialProfile.IMMERSIVE
    @Volatile var monoMix: Boolean = false
    @Volatile var balance: Float = 0f
    @Volatile var headphoneCrossfeed: Boolean = false
    @Volatile var roomDelayMs: Float = 12f
    @Volatile var activeDescription: String = "Bypassed"
        private set

    private var sampleRate = 48_000
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID

    // Circular stereo delay buffers (~40ms max at up to 192kHz -> 8192 samples power-of-two)
    private val bufMask = 8191
    private val delayL = FloatArray(bufMask + 1)
    private val delayR = FloatArray(bufMask + 1)
    private var writePos = 0

    // One-pole lowpass state for head-shadow acoustic crossfeed & bass anchoring
    private var lpL = 0f
    private var lpR = 0f
    private var bassLpL = 0f
    private var bassLpR = 0f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding !in setOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT)) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate.coerceIn(8000, 192000)
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytes = inputBuffer.remaining()
        if (bytes == 0) return
        val shouldProcess = channelCount == 2 && (enabled || monoMix || headphoneCrossfeed || abs(balance) > 0.001f)
        if (!shouldProcess) {
            activeDescription = if (channelCount == 2) "Stereo bypass" else "Bypass: ${channelCount}-channel source"
            val output = replaceOutputBuffer(bytes)
            copyBuffer(inputBuffer, output, bytes)
            output.flip()
            return
        }

        activeDescription = buildString {
            when {
                monoMix -> append("Mono mix")
                enabled -> append("${profile.label} 3D (${(width.coerceIn(0f, 1f) * 100).roundToInt()}%)")
                headphoneCrossfeed -> append("Headphone Crossfeed")
                else -> append("Stereo balance")
            }
            if (headphoneCrossfeed && enabled) append(" + Crossfeed")
            if (abs(balance) > 0.001f) {
                append(if (balance > 0f) " · R ${(balance * 100).roundToInt()}%" else " · L ${(-balance * 100).roundToInt()}%")
            }
        }
        inputBuffer.order(ByteOrder.nativeOrder())
        val output = replaceOutputBuffer(bytes).order(ByteOrder.nativeOrder())
        when (encoding) {
            C.ENCODING_PCM_FLOAT -> processFloat(inputBuffer, output)
            C.ENCODING_PCM_16BIT -> processPcm16(inputBuffer, output)
            else -> copyBuffer(inputBuffer, output, bytes)
        }
        output.flip()
    }

    private fun processFloat(input: ByteBuffer, output: ByteBuffer) {
        while (input.remaining() >= 8) {
            val (left, right) = transform(input.float, input.float)
            output.putFloat(left)
            output.putFloat(right)
        }
    }

    private fun processPcm16(input: ByteBuffer, output: ByteBuffer) {
        while (input.remaining() >= 4) {
            val left = input.short.toFloat() / Short.MAX_VALUE
            val right = input.short.toFloat() / Short.MAX_VALUE
            val (outLeft, outRight) = transform(left, right)
            output.putShort(toPcm16(outLeft))
            output.putShort(toPcm16(outRight))
        }
    }

    private fun transform(left: Float, right: Float): Pair<Float, Float> {
        delayL[writePos] = left
        delayR[writePos] = right

        // Head-shadow low-passed signals (~750Hz) and low-end anchor (~120Hz)
        lpL += (left - lpL) * 0.09f
        lpR += (right - lpR) * 0.09f
        bassLpL += (left - bassLpL) * 0.015f
        bassLpR += (right - bassLpR) * 0.015f

        var l: Float
        var r: Float
        if (monoMix) {
            val mono = (left + right) * 0.5f
            l = mono
            r = mono
        } else {
            l = left
            r = right
            if (enabled) {
                val intensity = (width.coerceIn(0f, 1f) * profileMultiplier(profile)).coerceIn(0f, 1.0f)
                val mid = (left + right) * 0.5f
                val side = (left - right) * 0.5f
                // Keep sub-120Hz mono-anchored so bass remains punchy while upper mids/highs widen cleanly
                val lowMono = (bassLpL + bassLpR) * 0.5f
                val sideHigh = side - ((bassLpL - bassLpR) * 0.5f)
                val expandedSide = side + sideHigh * intensity * 0.92f

                var widL = mid + expandedSide * 0.98f + (lowMono - bassLpL) * 0.25f * intensity
                var widR = mid - expandedSide * 0.98f + (lowMono - bassLpR) * 0.25f * intensity

                // Add early acoustic reflections for hall / cinema / binaural modes
                val tapMs = (roomDelayMs * profileRoomFactor(profile)).coerceIn(2f, 34f)
                val tapSamples = ((tapMs / 1000f) * sampleRate).roundToInt().coerceIn(8, bufMask - 1)
                val refTap = profileReflectionAmount(profile) * intensity
                if (refTap > 0.001f) {
                    val delayedL = delayL[(writePos - tapSamples) and bufMask]
                    val delayedR = delayR[(writePos - (tapSamples * 13 / 10).coerceAtMost(bufMask)) and bufMask]
                    widL += delayedR * refTap - delayedL * (refTap * 0.25f)
                    widR += delayedL * refTap - delayedR * (refTap * 0.25f)
                }

                val crossfeedAmt = profileCrossfeed(profile) * intensity
                l = widL * (1f - crossfeedAmt) + lpR * crossfeedAmt
                r = widR * (1f - crossfeedAmt) + lpL * crossfeedAmt

                val safetyGain = 1f / (1f + intensity * 0.22f)
                l *= safetyGain
                r *= safetyGain
            }

            if (headphoneCrossfeed || profile == SpatialProfile.BINAURAL_HEADPHONES) {
                val hrtfDelay = ((0.26f / 1000f) * sampleRate).roundToInt().coerceIn(4, 64)
                val crossL = delayR[(writePos - hrtfDelay) and bufMask]
                val crossR = delayL[(writePos - hrtfDelay) and bufMask]
                val cf = if (profile == SpatialProfile.BINAURAL_HEADPHONES) 0.22f else 0.16f
                l = l * (1f - cf * 0.5f) + (lpR * 0.65f + crossL * 0.35f) * cf
                r = r * (1f - cf * 0.5f) + (lpL * 0.65f + crossR * 0.35f) * cf
            }
        }

        writePos = (writePos + 1) and bufMask

        val b = balance.coerceIn(-1f, 1f)
        if (b > 0f) l *= 1f - b else if (b < 0f) r *= 1f + b
        return l.coerceIn(-1f, 1f) to r.coerceIn(-1f, 1f)
    }

    private fun copyBuffer(src: ByteBuffer, dst: ByteBuffer, size: Int) {
        val position = src.position()
        for (index in 0 until size) dst.put(src.get(position + index))
        src.position(position + size)
    }

    private fun toPcm16(value: Float): Short =
        (value.coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt().toShort()

    private fun profileMultiplier(profile: SpatialProfile): Float = when (profile) {
        SpatialProfile.STUDIO -> 0.45f
        SpatialProfile.WIDE -> 0.78f
        SpatialProfile.IMMERSIVE -> 1.0f
        SpatialProfile.CINEMA -> 0.72f
        SpatialProfile.CONCERT_HALL -> 0.85f
        SpatialProfile.BINAURAL_HEADPHONES -> 0.88f
    }

    private fun profileCrossfeed(profile: SpatialProfile): Float = when (profile) {
        SpatialProfile.STUDIO -> 0.04f
        SpatialProfile.WIDE -> 0.025f
        SpatialProfile.IMMERSIVE -> 0.03f
        SpatialProfile.CINEMA -> 0.06f
        SpatialProfile.CONCERT_HALL -> 0.05f
        SpatialProfile.BINAURAL_HEADPHONES -> 0.14f
    }

    private fun profileReflectionAmount(profile: SpatialProfile): Float = when (profile) {
        SpatialProfile.STUDIO -> 0.03f
        SpatialProfile.WIDE -> 0.05f
        SpatialProfile.IMMERSIVE -> 0.09f
        SpatialProfile.CINEMA -> 0.15f
        SpatialProfile.CONCERT_HALL -> 0.18f
        SpatialProfile.BINAURAL_HEADPHONES -> 0.11f
    }

    private fun profileRoomFactor(profile: SpatialProfile): Float = when (profile) {
        SpatialProfile.STUDIO -> 0.45f
        SpatialProfile.WIDE -> 0.8f
        SpatialProfile.IMMERSIVE -> 1.0f
        SpatialProfile.CINEMA -> 1.35f
        SpatialProfile.CONCERT_HALL -> 1.65f
        SpatialProfile.BINAURAL_HEADPHONES -> 0.65f
    }

    private fun clearDelayState() {
        delayL.fill(0f)
        delayR.fill(0f)
        writePos = 0
        lpL = 0f
        lpR = 0f
        bassLpL = 0f
        bassLpR = 0f
    }

    override fun onFlush() {
        super.onFlush()
        clearDelayState()
    }

    override fun onReset() {
        super.onReset()
        // CRITICAL FIX: Never clear enabled, width, profile, monoMix, balance, headphoneCrossfeed, or roomDelayMs here!
        // ExoPlayer calls onReset() when preparing a new track or resetting AudioSink; clearing config broke EQ/Spatial!
        clearDelayState()
        channelCount = 0
        encoding = C.ENCODING_INVALID
    }
}
