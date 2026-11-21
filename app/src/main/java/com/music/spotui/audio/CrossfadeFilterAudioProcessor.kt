package com.music.spotui.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Media3 [AudioProcessor] applying a real-time Biquad low-pass / high-pass filter to the
 * audio stream, for DJ-style crossfade transitions.
 *
 * - Supports PCM 16-bit, 24-bit (3B LE), 32-bit, and 32-bit float (mono and stereo).
 * - Coefficient recalculation is lazy: only when cutoff or filter type changes.
 */
@UnstableApi
class CrossfadeFilterAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var enabled: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                if (!value) filter.reset()
            }
        }

    @Volatile
    var cutoffFrequencyHz: Float = 20000f
        set(value) {
            if (field != value) {
                field = value
                coefficientsDirty = true
            }
        }

    @Volatile
    var filterType: BiquadFilter.FilterType = BiquadFilter.FilterType.LOW_PASS
        set(value) {
            if (field != value) {
                field = value
                coefficientsDirty = true
            }
        }

    private val filter = BiquadFilter()

    @Volatile
    private var coefficientsDirty = true

    private var sampleRate = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val supported = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_24BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_32BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        if (!supported) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding
        coefficientsDirty = true
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!enabled || sampleRate == 0) {
            val output = replaceOutputBuffer(remaining)
            copyBuffer(inputBuffer, output, remaining)
            output.flip()
            return
        }

        if (coefficientsDirty) {
            filter.updateCoefficients(
                cutoffHz = cutoffFrequencyHz,
                sampleRate = sampleRate,
                type = filterType,
            )
            coefficientsDirty = false
        }

        inputBuffer.order(ByteOrder.nativeOrder())
        val output = replaceOutputBuffer(remaining).order(ByteOrder.nativeOrder())

        when (encoding) {
            C.ENCODING_PCM_FLOAT -> when (channelCount) {
                1 -> processMonoFloat(inputBuffer, output)
                2 -> processStereoFloat(inputBuffer, output)
                else -> copyBuffer(inputBuffer, output, remaining)
            }
            C.ENCODING_PCM_16BIT -> when (channelCount) {
                1 -> processMonoPcm16(inputBuffer, output)
                2 -> processStereoPcm16(inputBuffer, output)
                else -> copyBuffer(inputBuffer, output, remaining)
            }
            C.ENCODING_PCM_24BIT -> when (channelCount) {
                1 -> processMonoPcm24(inputBuffer, output)
                2 -> processStereoPcm24(inputBuffer, output)
                else -> copyBuffer(inputBuffer, output, remaining)
            }
            C.ENCODING_PCM_32BIT -> when (channelCount) {
                1 -> processMonoPcm32(inputBuffer, output)
                2 -> processStereoPcm32(inputBuffer, output)
                else -> copyBuffer(inputBuffer, output, remaining)
            }
            else -> copyBuffer(inputBuffer, output, remaining)
        }

        output.flip()
    }

    private fun copyBuffer(src: ByteBuffer, dst: ByteBuffer, size: Int) {
        if (src === dst) {
            dst.position(0)
            dst.limit(size)
            return
        }
        val pos = src.position()
        for (i in 0 until size) {
            dst.put(src.get(pos + i))
        }
        src.position(pos + size)
    }

    private fun processMonoPcm16(input: ByteBuffer, output: ByteBuffer) {
        while (input.remaining() >= 2) {
            val sample = input.short.toDouble() / Short.MAX_VALUE
            val filtered = filter.processSampleMono(sample)
            output.putShort((filtered.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).roundToInt().toShort())
        }
    }

    private fun processStereoPcm16(input: ByteBuffer, output: ByteBuffer) {
        while (input.remaining() >= 4) {
            val left = input.short.toDouble() / Short.MAX_VALUE
            val right = input.short.toDouble() / Short.MAX_VALUE
            val (filteredL, filteredR) = filter.processStereo(left, right)
            output.putShort((filteredL.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).roundToInt().toShort())
            output.putShort((filteredR.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).roundToInt().toShort())
        }
    }

    private fun readPcm24(buf: ByteBuffer): Double {
        val b0 = buf.get().toInt() and 0xFF
        val b1 = buf.get().toInt() and 0xFF
        val b2 = buf.get().toInt()
        val raw = b0 or (b1 shl 8) or (b2 shl 16)
        return raw.toDouble() / 8388607.0
    }

    private fun writePcm24(buf: ByteBuffer, sample: Double) {
        val q = (sample.coerceIn(-1.0, 1.0) * 8388607.0).roundToInt().coerceIn(-8388608, 8388607)
        buf.put((q and 0xFF).toByte())
        buf.put(((q ushr 8) and 0xFF).toByte())
        buf.put(((q ushr 16) and 0xFF).toByte())
    }

    private fun processMonoPcm24(input: ByteBuffer, output: ByteBuffer) {
        while (input.remaining() >= 3) {
            val filtered = filter.processSampleMono(readPcm24(input))
            writePcm24(output, filtered)
        }
    }

    private fun processStereoPcm24(input: ByteBuffer, output: ByteBuffer) {
        while (input.remaining() >= 6) {
            val (fL, fR) = filter.processStereo(readPcm24(input), readPcm24(input))
            writePcm24(output, fL)
            writePcm24(output, fR)
        }
    }

    private fun processMonoPcm32(input: ByteBuffer, output: ByteBuffer) {
        while (input.remaining() >= 4) {
            val filtered = filter.processSampleMono(input.int.toDouble() / 2147483647.0)
            output.putInt((filtered.coerceIn(-1.0, 1.0) * 2147483647.0).roundToInt())
        }
    }

    private fun processStereoPcm32(input: ByteBuffer, output: ByteBuffer) {
        while (input.remaining() >= 8) {
            val (fL, fR) = filter.processStereo(input.int.toDouble() / 2147483647.0, input.int.toDouble() / 2147483647.0)
            output.putInt((fL.coerceIn(-1.0, 1.0) * 2147483647.0).roundToInt())
            output.putInt((fR.coerceIn(-1.0, 1.0) * 2147483647.0).roundToInt())
        }
    }

    private fun processMonoFloat(input: ByteBuffer, output: ByteBuffer) {
        while (input.remaining() >= 4) {
            val filtered = filter.processSampleMono(input.float.toDouble())
            output.putFloat(filtered.coerceIn(-1.0, 1.0).toFloat())
        }
    }

    private fun processStereoFloat(input: ByteBuffer, output: ByteBuffer) {
        while (input.remaining() >= 8) {
            val (fL, fR) = filter.processStereo(input.float.toDouble(), input.float.toDouble())
            output.putFloat(fL.coerceIn(-1.0, 1.0).toFloat())
            output.putFloat(fR.coerceIn(-1.0, 1.0).toFloat())
        }
    }

    override fun onFlush() {
        super.onFlush()
        filter.reset()
    }

    override fun onReset() {
        super.onReset()
        enabled = false
        cutoffFrequencyHz = 20000f
        filterType = BiquadFilter.FilterType.LOW_PASS
        filter.reset()
    }
}
