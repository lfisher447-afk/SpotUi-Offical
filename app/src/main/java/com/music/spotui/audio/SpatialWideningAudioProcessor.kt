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
import kotlin.math.tanh

/**
 * Studio-grade 3D binaural soundstage, acoustic early-reflection matrix, and
 * accessibility stereo processor.
 *
 * Engineered to sound natural and uninterrupted over Bluetooth A2DP/BLE headsets,
 * wired headphones, USB DACs, and phone speakers:
 * - Sample-by-sample NaN/Infinity guard prevents decoder glitches from locking IIR filters.
 * - Click-free exponential smoothing on enable/width/crossfeed/balance changes.
 * - Sub-115 Hz mono-bass anchoring preserves kick & sub-bass punch while widening mids/highs.
 * - 4-tap prime-spaced diffuse early reflections with high-frequency air-absorption damping.
 * - True ITD (~0.28 ms) + head-shadow ILD (~750 Hz) HRTF crossfeed for Bluetooth/headphones.
 * - Soft-knee headroom limiter keeps Bluetooth AAC/SBC/LDAC encoders free from digital clipping.
 */
@UnstableApi
class SpatialWideningAudioProcessor : BaseAudioProcessor() {
    @Volatile var enabled: Boolean = false
    @Volatile var width: Float = 0f
    @Volatile var profile: SpatialProfile = SpatialProfile.IMMERSIVE
    @Volatile var monoMix: Boolean = false
    @Volatile var balance: Float = 0f
    @Volatile var headphoneCrossfeed: Boolean = false
    @Volatile var autoHeadphoneHrtf: Boolean = false
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

    // One-pole lowpass state for head-shadow acoustic crossfeed, air-absorption damping & bass anchoring
    private var lpL = 0f
    private var lpR = 0f
    private var airLpL = 0f
    private var airLpR = 0f
    private var bassLpL = 0f
    private var bassLpR = 0f

    // Smooth gain ramp states (eliminates clicks/pops when toggling 3D or changing width mid-song)
    private var smoothedMix = 0f
    private var smoothedWidth = 0f
    private var smoothedCrossfeed = 0f
    private var smoothedBalance = 0f

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

        val explicitHrtf = headphoneCrossfeed || (enabled && (profile == SpatialProfile.BINAURAL_HEADPHONES || autoHeadphoneHrtf))
        val targetActive = channelCount == 2 && (enabled || monoMix || explicitHrtf || abs(balance) > 0.001f)
        val stillRamping = channelCount == 2 && (smoothedMix > 0.002f || smoothedCrossfeed > 0.002f || abs(smoothedBalance) > 0.002f)

        if (!targetActive && !stillRamping) {
            activeDescription = if (channelCount == 2) "Stereo bypass" else "Bypass: ${channelCount}-channel source"
            val output = replaceOutputBuffer(bytes)
            copyBuffer(inputBuffer, output, bytes)
            output.flip()
            return
        }

        activeDescription = buildString {
            when {
                monoMix -> append("Mono mix")
                enabled -> {
                    val label = if (profile.label.contains("3D", ignoreCase = true)) profile.label else "${profile.label} 3D"
                    append("$label (${(width.coerceIn(0f, 1f) * 100).roundToInt()}%)")
                }
                headphoneCrossfeed -> append("Headphone HRTF Crossfeed")
                else -> append("Stereo balance")
            }
            if ((headphoneCrossfeed || autoHeadphoneHrtf) && enabled) append(" · HRTF")
            if (abs(balance) > 0.001f) {
                append(if (balance > 0f) " · R ${(balance * 100).roundToInt()}%" else " · L ${(-balance * 100).roundToInt()}%")
            }
        }

        val targetMix = if (enabled && !monoMix) 1f else 0f
        val targetWidth = width.coerceIn(0f, 1f)
        val targetCf = when {
            monoMix -> 0f
            headphoneCrossfeed -> 1f
            enabled && profile == SpatialProfile.BINAURAL_HEADPHONES -> 1f
            enabled && autoHeadphoneHrtf -> 0.52f
            else -> 0f
        }
        val targetBal = balance.coerceIn(-1f, 1f)

        inputBuffer.order(ByteOrder.nativeOrder())
        val output = replaceOutputBuffer(bytes).order(ByteOrder.nativeOrder())
        when (encoding) {
            C.ENCODING_PCM_FLOAT -> processFloat(inputBuffer, output, targetMix, targetWidth, targetCf, targetBal)
            C.ENCODING_PCM_16BIT -> processPcm16(inputBuffer, output, targetMix, targetWidth, targetCf, targetBal)
            else -> copyBuffer(inputBuffer, output, bytes)
        }
        output.flip()
    }

    private fun processFloat(
        input: ByteBuffer,
        output: ByteBuffer,
        targetMix: Float,
        targetWidth: Float,
        targetCf: Float,
        targetBal: Float,
    ) {
        while (input.remaining() >= 8) {
            val (left, right) = transform(input.float, input.float, targetMix, targetWidth, targetCf, targetBal)
            output.putFloat(left)
            output.putFloat(right)
        }
    }

    private fun processPcm16(
        input: ByteBuffer,
        output: ByteBuffer,
        targetMix: Float,
        targetWidth: Float,
        targetCf: Float,
        targetBal: Float,
    ) {
        while (input.remaining() >= 4) {
            val left = input.short.toFloat() / Short.MAX_VALUE
            val right = input.short.toFloat() / Short.MAX_VALUE
            val (outLeft, outRight) = transform(left, right, targetMix, targetWidth, targetCf, targetBal)
            output.putShort(toPcm16(outLeft))
            output.putShort(toPcm16(outRight))
        }
    }

    private fun transform(
        rawLeft: Float,
        rawRight: Float,
        targetMix: Float,
        targetWidth: Float,
        targetCf: Float,
        targetBal: Float,
    ): Pair<Float, Float> {
        val left = if (rawLeft.isFinite()) rawLeft.coerceIn(-2f, 2f) else 0f
        val right = if (rawRight.isFinite()) rawRight.coerceIn(-2f, 2f) else 0f

        // Smooth control parameters (~6ms response at 48 kHz)
        val ctrlAlpha = (240f / sampleRate.toFloat()).coerceIn(0.002f, 0.05f)
        smoothedMix += (targetMix - smoothedMix) * ctrlAlpha
        smoothedWidth += (targetWidth - smoothedWidth) * ctrlAlpha
        smoothedCrossfeed += (targetCf - smoothedCrossfeed) * ctrlAlpha
        smoothedBalance += (targetBal - smoothedBalance) * ctrlAlpha

        delayL[writePos] = left
        delayR[writePos] = right

        // Sample-rate-scaled one-pole filter coefficients
        val sr = sampleRate.toFloat().coerceAtLeast(8000f)
        val headShadowAlpha = (4700f / sr).coerceIn(0.02f, 0.45f) // ~750Hz ILD cutoff
        val bassAlpha = (720f / sr).coerceIn(0.004f, 0.12f)        // ~115Hz sub-bass anchor
        val airAlpha = (14000f / sr).coerceIn(0.08f, 0.65f)        // ~2.3kHz air damping for reflections

        lpL += (left - lpL) * headShadowAlpha
        lpR += (right - lpR) * headShadowAlpha
        bassLpL += (left - bassLpL) * bassAlpha
        bassLpR += (right - bassLpR) * bassAlpha

        var l: Float
        var r: Float
        if (monoMix) {
            val mono = (left + right) * 0.5f
            l = mono
            r = mono
        } else {
            l = left
            r = right
            if (smoothedMix > 0.0005f) {
                val intensity = (smoothedWidth * profileMultiplier(profile) * smoothedMix).coerceIn(0f, 1.0f)
                val mid = (left + right) * 0.5f
                val side = (left - right) * 0.5f

                // Keep sub-115 Hz anchored in center so widening highs/upper-mids never thins out bass punch
                val lowMono = (bassLpL + bassLpR) * 0.5f
                val sideHigh = side - ((bassLpL - bassLpR) * 0.5f)
                val expandedSide = side + sideHigh * intensity * 0.96f
                val centerFocusBoost = profileCenterFocus(profile) * intensity

                var widL = mid * (1f + centerFocusBoost * 0.08f) + expandedSide + (lowMono - bassLpL) * 0.30f * intensity
                var widR = mid * (1f + centerFocusBoost * 0.08f) - expandedSide + (lowMono - bassLpR) * 0.30f * intensity

                // Multi-tap prime-spaced diffuse early reflections with HF air absorption
                val baseMs = (roomDelayMs * profileRoomFactor(profile)).coerceIn(2f, 34f)
                val refTap = profileReflectionAmount(profile) * intensity
                if (refTap > 0.001f) {
                    val t1 = ((baseMs * 0.58f / 1000f) * sr).roundToInt().coerceIn(8, bufMask - 1)
                    val t2 = ((baseMs * 1.00f / 1000f) * sr).roundToInt().coerceIn(12, bufMask - 1)
                    val t3 = ((baseMs * 1.37f / 1000f) * sr).roundToInt().coerceIn(16, bufMask - 1)
                    val t4 = ((baseMs * 1.79f / 1000f) * sr).roundToInt().coerceIn(20, bufMask - 1)

                    val refRawL =
                        delayR[(writePos - t1) and bufMask] * 0.42f +
                            delayL[(writePos - t2) and bufMask] * -0.24f +
                            delayR[(writePos - t3) and bufMask] * 0.22f +
                            delayL[(writePos - t4) and bufMask] * -0.14f
                    val refRawR =
                        delayL[(writePos - t1) and bufMask] * 0.42f +
                            delayR[(writePos - t2) and bufMask] * -0.24f +
                            delayL[(writePos - t3) and bufMask] * 0.22f +
                            delayR[(writePos - t4) and bufMask] * -0.14f

                    airLpL += (refRawL - airLpL) * airAlpha
                    airLpR += (refRawR - airLpR) * airAlpha

                    widL += airLpL * refTap
                    widR += airLpR * refTap
                }

                // Gentle speaker acoustic crossfeed baked into wide/cinema modes
                val profileCf = profileCrossfeed(profile) * intensity
                l = widL * (1f - profileCf) + lpR * profileCf
                r = widR * (1f - profileCf) + lpL * profileCf

                val safetyGain = 1f / (1f + intensity * 0.20f)
                l *= safetyGain
                r *= safetyGain
            }

            if (smoothedCrossfeed > 0.0005f) {
                val hrtfDelay = ((0.28f / 1000f) * sr).roundToInt().coerceIn(4, 96)
                val crossRtoL = delayR[(writePos - hrtfDelay) and bufMask]
                val crossLtoR = delayL[(writePos - hrtfDelay) and bufMask]
                val cfGain = (0.18f * smoothedCrossfeed).coerceIn(0f, 0.24f)
                l = l * (1f - cfGain * 0.45f) + (lpR * 0.68f + crossRtoL * 0.32f) * cfGain
                r = r * (1f - cfGain * 0.45f) + (lpL * 0.68f + crossLtoR * 0.32f) * cfGain
            }
        }

        writePos = (writePos + 1) and bufMask

        if (smoothedBalance > 0.0005f) {
            l *= 1f - smoothedBalance
        } else if (smoothedBalance < -0.0005f) {
            r *= 1f + smoothedBalance
        }

        // Soft-knee headroom limiter keeps Bluetooth SBC/AAC/aptX encoders clean with zero hard-clipping
        l = softLimit(l)
        r = softLimit(r)
        return l to r
    }

    private fun softLimit(x: Float): Float {
        if (!x.isFinite()) return 0f
        val threshold = 0.90f
        val ax = abs(x)
        if (ax <= threshold) return x
        val excess = ax - threshold
        val shaped = threshold + (1f - threshold) * tanh(excess / (1f - threshold))
        return if (x >= 0f) shaped.coerceAtMost(0.999f) else (-shaped).coerceAtLeast(-0.999f)
    }

    private fun copyBuffer(src: ByteBuffer, dst: ByteBuffer, size: Int) {
        val position = src.position()
        for (index in 0 until size) dst.put(src.get(position + index))
        src.position(position + size)
    }

    private fun toPcm16(value: Float): Short =
        (value.coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt().toShort()

    private fun profileMultiplier(profile: SpatialProfile): Float = when (profile) {
        SpatialProfile.STUDIO -> 0.50f
        SpatialProfile.WIDE -> 0.80f
        SpatialProfile.IMMERSIVE -> 1.0f
        SpatialProfile.CINEMA -> 0.76f
        SpatialProfile.CONCERT_HALL -> 0.88f
        SpatialProfile.BINAURAL_HEADPHONES -> 0.92f
    }

    private fun profileCenterFocus(profile: SpatialProfile): Float = when (profile) {
        SpatialProfile.STUDIO -> 0.45f
        SpatialProfile.WIDE -> 0.15f
        SpatialProfile.IMMERSIVE -> 0.25f
        SpatialProfile.CINEMA -> 0.35f
        SpatialProfile.CONCERT_HALL -> 0.20f
        SpatialProfile.BINAURAL_HEADPHONES -> 0.30f
    }

    private fun profileCrossfeed(profile: SpatialProfile): Float = when (profile) {
        SpatialProfile.STUDIO -> 0.035f
        SpatialProfile.WIDE -> 0.025f
        SpatialProfile.IMMERSIVE -> 0.03f
        SpatialProfile.CINEMA -> 0.055f
        SpatialProfile.CONCERT_HALL -> 0.05f
        SpatialProfile.BINAURAL_HEADPHONES -> 0.10f
    }

    private fun profileReflectionAmount(profile: SpatialProfile): Float = when (profile) {
        SpatialProfile.STUDIO -> 0.05f
        SpatialProfile.WIDE -> 0.08f
        SpatialProfile.IMMERSIVE -> 0.14f
        SpatialProfile.CINEMA -> 0.21f
        SpatialProfile.CONCERT_HALL -> 0.26f
        SpatialProfile.BINAURAL_HEADPHONES -> 0.16f
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
        airLpL = 0f
        airLpR = 0f
        bassLpL = 0f
        bassLpR = 0f
    }

    override fun onFlush() {
        super.onFlush()
        clearDelayState()
    }

    override fun onReset() {
        super.onReset()
        // CRITICAL: Never clear user spatial configuration across track changes or AudioSink resets!
        clearDelayState()
        channelCount = 0
        encoding = C.ENCODING_INVALID
    }
}
