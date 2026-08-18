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
 * Device-independent PCM spatial and accessibility processor.
 *
 * This stage sits inside both ExoPlayer audio sinks. It supports the PCM-16 and
 * float formats normally emitted by Media3, so a user does not lose audible
 * spatial processing simply because float output is selected. The same stage
 * applies mono mix and left/right balance before the signal reaches AudioTrack.
 */
@UnstableApi
class SpatialWideningAudioProcessor : BaseAudioProcessor() {
    @Volatile var enabled: Boolean = false
    @Volatile var width: Float = 0f
    @Volatile var profile: SpatialProfile = SpatialProfile.STUDIO
    @Volatile var monoMix: Boolean = false
    @Volatile var balance: Float = 0f
    @Volatile var activeDescription: String = "Bypassed"
        private set

    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding !in setOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT)) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytes = inputBuffer.remaining()
        if (bytes == 0) return
        val shouldProcess = channelCount == 2 && (enabled || monoMix || abs(balance) > 0.001f)
        if (!shouldProcess) {
            activeDescription = if (channelCount == 2) "Stereo bypass" else "Bypass: ${channelCount}-channel source"
            val output = replaceOutputBuffer(bytes)
            copyBuffer(inputBuffer, output, bytes)
            output.flip()
            return
        }

        activeDescription = buildString {
            if (monoMix) append("Mono mix") else if (enabled) append("${profile.label} spatial ${((width.coerceIn(0f, 1f)) * 100).roundToInt()}%") else append("Stereo balance")
            if (abs(balance) > 0.001f) append(if (balance > 0f) " · right ${(balance * 100).roundToInt()}%" else " · left ${(-balance * 100).roundToInt()}%")
        }
        inputBuffer.order(ByteOrder.nativeOrder())
        val output = replaceOutputBuffer(bytes).order(ByteOrder.nativeOrder())
        when (encoding) {
            C.ENCODING_PCM_FLOAT -> processFloat(inputBuffer, output)
            C.ENCODING_PCM_16BIT -> processPcm16(inputBuffer, output)
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
        var l: Float
        var r: Float
        if (monoMix) {
            val mono = (left + right) * 0.5f
            l = mono
            r = mono
        } else if (enabled) {
            val intensity = (width.coerceIn(0f, 1f) * profileMultiplier(profile)).coerceIn(0f, 0.98f)
            val mid = (left + right) * 0.5f
            val side = (left - right) * 0.5f * (1f + intensity)
            val crossfeed = profileCrossfeed(profile) * intensity
            l = (mid + side) * (1f - crossfeed) + right * crossfeed
            r = (mid - side) * (1f - crossfeed) + left * crossfeed
            // Compensate the increase in side energy at high width values.
            val safetyGain = 1f / (1f + intensity * 0.18f)
            l *= safetyGain
            r *= safetyGain
        } else {
            l = left
            r = right
        }
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
        SpatialProfile.STUDIO -> 0.42f
        SpatialProfile.WIDE -> 0.74f
        SpatialProfile.IMMERSIVE -> 1.0f
        SpatialProfile.CINEMA -> 0.64f
    }

    private fun profileCrossfeed(profile: SpatialProfile): Float = when (profile) {
        SpatialProfile.STUDIO -> 0.018f
        SpatialProfile.WIDE -> 0.012f
        SpatialProfile.IMMERSIVE -> 0.008f
        SpatialProfile.CINEMA -> 0.028f
    }

    override fun onReset() {
        super.onReset()
        enabled = false
        width = 0f
        profile = SpatialProfile.STUDIO
        monoMix = false
        balance = 0f
        channelCount = 0
        encoding = C.ENCODING_INVALID
        activeDescription = "Bypassed"
    }
}
