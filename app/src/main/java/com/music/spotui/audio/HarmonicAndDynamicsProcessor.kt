package com.music.spotui.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

data class HarmonicDynamicsTelemetry(
    val active: Boolean = false,
    val peakDb: Float = -60f,
    val rmsDb: Float = -60f,
    val gainReductionDb: Float = 0f,
    val processedFrames: Long = 0L,
)

/**
 * Mastering-grade PCM audio processor adding:
 * 1. Asymmetric triode Tube Warmth (subtle even-order harmonic sheen)
 * 2. High-Shelf Air Exciter (> 7.5 kHz presence + sparkle)
 * 3. Sub-Harmonic Octave Punch (< 95 Hz clean low-end weight)
 * 4. Sample-accurate L/R Haas Micro-Alignment (-2.0 .. +2.0 ms)
 * 5. Soft-Knee Bus Compressor (ratio 1.0 .. 6.0x)
 */
@UnstableApi
class HarmonicAndDynamicsProcessor : BaseAudioProcessor() {

    @Volatile var enabled: Boolean = false
    @Volatile var tubeWarmth: Float = 0.18f
    @Volatile var airExciter: Float = 0.22f
    @Volatile var subHarmonicPunch: Float = 0.15f
    @Volatile var haasDelayAlignMs: Float = 0f
    @Volatile var compressorRatio: Float = 1.0f

    private var sampleRate: Int = 48_000
    private var channelCount: Int = 2
    private var encoding: Int = C.ENCODING_INVALID

    private val alignMask = 511
    private val alignBufL = FloatArray(alignMask + 1)
    private val alignBufR = FloatArray(alignMask + 1)
    private var alignPos = 0

    // Sub-bass LP & Air HP filter states
    private var subLpL = 0f
    private var subLpR = 0f
    private var prevInL = 0f
    private var prevInR = 0f
    private var airHpL = 0f
    private var airHpR = 0f

    // Sub-harmonic octave flip-flop tracker
    private var prevSignL = true
    private var prevSignR = true
    private var octavePhaseL = 1f
    private var octavePhaseR = 1f

    // Compressor & meter followers
    private var envFollower = 0f
    private var peakMeter = 1e-4f
    private var rmsPowerMeter = 1e-8f
    private var maxGainReductionDb = 0f
    private var telemetryCounter = 0

    @Volatile
    var processedFrames: Long = 0L
        private set

    private val _telemetry = MutableStateFlow(HarmonicDynamicsTelemetry())
    val telemetry: StateFlow<HarmonicDynamicsTelemetry> = _telemetry.asStateFlow()

    fun hasActiveProcessing(): Boolean {
        if (!enabled) return false
        return tubeWarmth > 0.01f ||
            airExciter > 0.01f ||
            subHarmonicPunch > 0.01f ||
            abs(haasDelayAlignMs) > 0.02f ||
            compressorRatio > 1.05f
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val supported = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_24BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_32BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        if (!supported) return AudioProcessor.AudioFormat.NOT_SET
        sampleRate = inputAudioFormat.sampleRate.coerceIn(8_000, 192_000)
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytes = inputBuffer.remaining()
        if (bytes == 0) return

        if (channelCount !in 1..2 || !hasActiveProcessing()) {
            _telemetry.value = _telemetry.value.copy(active = false, gainReductionDb = 0f)
            val output = replaceOutputBuffer(bytes)
            val pos = inputBuffer.position()
            for (i in 0 until bytes) output.put(inputBuffer.get(pos + i))
            inputBuffer.position(pos + bytes)
            output.flip()
            return
        }

        inputBuffer.order(ByteOrder.nativeOrder())
        val output = replaceOutputBuffer(bytes).order(ByteOrder.nativeOrder())

        when (encoding) {
            C.ENCODING_PCM_FLOAT -> processFloat(inputBuffer, output)
            C.ENCODING_PCM_16BIT -> processPcm16(inputBuffer, output)
            C.ENCODING_PCM_24BIT -> processPcm24(inputBuffer, output)
            C.ENCODING_PCM_32BIT -> processPcm32(inputBuffer, output)
            else -> {
                val pos = inputBuffer.position()
                for (i in 0 until bytes) output.put(inputBuffer.get(pos + i))
                inputBuffer.position(pos + bytes)
            }
        }
        output.flip()
        publishTelemetrySnapshot()
    }

    private fun processFloat(input: ByteBuffer, output: ByteBuffer) {
        if (channelCount == 2) {
            while (input.remaining() >= 8) {
                val (l, r) = processStereo(input.float, input.float)
                output.putFloat(l)
                output.putFloat(r)
                processedFrames++
            }
        } else {
            while (input.remaining() >= 4) {
                val (l, _) = processStereo(input.float, 0f)
                output.putFloat(l)
                processedFrames++
            }
        }
    }

    private fun processPcm16(input: ByteBuffer, output: ByteBuffer) {
        if (channelCount == 2) {
            while (input.remaining() >= 4) {
                val lIn = input.short.toFloat() / Short.MAX_VALUE
                val rIn = input.short.toFloat() / Short.MAX_VALUE
                val (l, r) = processStereo(lIn, rIn)
                output.putShort((l.coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt().toShort())
                output.putShort((r.coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt().toShort())
                processedFrames++
            }
        } else {
            while (input.remaining() >= 2) {
                val s = input.short.toFloat() / Short.MAX_VALUE
                val (l, _) = processStereo(s, s)
                output.putShort((l.coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt().toShort())
                processedFrames++
            }
        }
    }

    private fun processPcm24(input: ByteBuffer, output: ByteBuffer) {
        if (channelCount == 2) {
            while (input.remaining() >= 6) {
                val lIn = readPcm24(input)
                val rIn = readPcm24(input)
                val (l, r) = processStereo(lIn, rIn)
                writePcm24(output, l)
                writePcm24(output, r)
                processedFrames++
            }
        } else {
            while (input.remaining() >= 3) {
                val s = readPcm24(input)
                val (l, _) = processStereo(s, s)
                writePcm24(output, l)
                processedFrames++
            }
        }
    }

    private fun processPcm32(input: ByteBuffer, output: ByteBuffer) {
        if (channelCount == 2) {
            while (input.remaining() >= 8) {
                val lIn = (input.int.toDouble() / 2147483647.0).toFloat()
                val rIn = (input.int.toDouble() / 2147483647.0).toFloat()
                val (l, r) = processStereo(lIn, rIn)
                output.putInt((l.coerceIn(-1f, 1f).toDouble() * 2147483647.0).roundToInt())
                output.putInt((r.coerceIn(-1f, 1f).toDouble() * 2147483647.0).roundToInt())
                processedFrames++
            }
        } else {
            while (input.remaining() >= 4) {
                val s = (input.int.toDouble() / 2147483647.0).toFloat()
                val (l, _) = processStereo(s, s)
                output.putInt((l.coerceIn(-1f, 1f).toDouble() * 2147483647.0).roundToInt())
                processedFrames++
            }
        }
    }

    private fun processStereo(rawL: Float, rawR: Float): Pair<Float, Float> {
        val inL = if (rawL.isFinite()) rawL.coerceIn(-2f, 2f) else 0f
        val inR = if (rawR.isFinite()) rawR.coerceIn(-2f, 2f) else 0f

        val sr = sampleRate.toFloat().coerceAtLeast(8_000f)

        // 1. Sub-harmonic low-end tracker (< 95 Hz)
        val subAlpha = ((2.0 * PI * 95.0) / sr).toFloat().coerceIn(0.002f, 0.2f)
        subLpL += (inL - subLpL) * subAlpha
        subLpR += (inR - subLpR) * subAlpha

        val signL = subLpL >= 0f
        if (signL != prevSignL && signL) octavePhaseL = -octavePhaseL
        prevSignL = signL
        val signR = subLpR >= 0f
        if (signR != prevSignR && signR) octavePhaseR = -octavePhaseR
        prevSignR = signR

        val subAmount = subHarmonicPunch.coerceIn(0f, 1f) * 0.24f
        var l = inL + abs(subLpL) * octavePhaseL * subAmount + subLpL * (subAmount * 0.45f)
        var r = inR + abs(subLpR) * octavePhaseR * subAmount + subLpR * (subAmount * 0.45f)

        // 2. High-frequency Air Exciter (> 7.5 kHz)
        val airAlpha = (sr / (sr + (2.0 * PI * 7500.0))).toFloat().coerceIn(0.45f, 0.96f)
        airHpL = airAlpha * (airHpL + inL - prevInL)
        airHpR = airAlpha * (airHpR + inR - prevInR)
        prevInL = inL
        prevInR = inR
        val exciterAmt = airExciter.coerceIn(0f, 1f) * 0.42f
        if (exciterAmt > 0.001f) {
            l += tanh((airHpL * 2.2f).toDouble()).toFloat() * exciterAmt
            r += tanh((airHpR * 2.2f).toDouble()).toFloat() * exciterAmt
        }

        // 3. Asymmetric triode Tube Warmth (subtle even + odd harmonics)
        val warmth = tubeWarmth.coerceIn(0f, 1f)
        if (warmth > 0.005f) {
            val drive = 1f + warmth * 1.45f
            val bias = 0.045f * warmth
            val satL = tanh(((l * drive) + bias).toDouble()).toFloat() - bias * 0.85f
            val satR = tanh(((r * drive) + bias).toDouble()).toFloat() - bias * 0.85f
            l += (satL - l) * (warmth * 0.52f)
            r += (satR - r) * (warmth * 0.52f)
        }

        // 4. L/R Haas Micro-Alignment (-2.0 .. +2.0 ms)
        alignBufL[alignPos] = l
        alignBufR[alignPos] = r
        val alignMs = haasDelayAlignMs.coerceIn(-2f, 2f)
        if (channelCount == 2 && abs(alignMs) > 0.02f) {
            val delaySamples = (abs(alignMs) / 1000f) * sr
            if (alignMs < 0f) {
                l = readAlignInterp(alignBufL, delaySamples)
            } else {
                r = readAlignInterp(alignBufR, delaySamples)
            }
        }
        alignPos = (alignPos + 1) and alignMask

        // 5. Soft-knee Bus Compressor (ratio 1.0 .. 6.0x)
        val ratio = compressorRatio.coerceIn(1f, 6f)
        if (ratio > 1.05f) {
            val peakIn = max(abs(l), abs(r))
            val attack = (90f / sr).coerceIn(0.002f, 0.08f)
            val release = (14f / sr).coerceIn(0.0002f, 0.02f)
            val coeff = if (peakIn > envFollower) attack else release
            envFollower += (peakIn - envFollower) * coeff

            val threshold = 0.316f // ~ -10 dBFS
            if (envFollower > threshold) {
                val envDb = (20.0 * log10(envFollower.toDouble())).toFloat()
                val threshDb = -10.0f
                val overDb = (envDb - threshDb).coerceAtLeast(0f)
                val targetDb = threshDb + overDb / ratio
                val gainDb = (targetDb - envDb).coerceIn(-16f, 0f)
                val gainLin = 10.0.pow(gainDb / 20.0).toFloat()
                val makeup = 1f + ((ratio - 1f) * 0.04f).coerceAtMost(0.22f)
                l *= gainLin * makeup
                r *= gainLin * makeup
                maxGainReductionDb = max(maxGainReductionDb, -gainDb)
            } else {
                maxGainReductionDb *= 0.996f
            }
        } else {
            maxGainReductionDb = 0f
        }

        // Metering
        val absPeak = max(abs(l), abs(r))
        peakMeter = max(absPeak, peakMeter * 0.9992f)
        val instPower = (l * l + r * r) * 0.5f
        rmsPowerMeter += (instPower - rmsPowerMeter) * 0.002f

        return l.coerceIn(-0.998f, 0.998f) to r.coerceIn(-0.998f, 0.998f)
    }

    private fun readAlignInterp(buf: FloatArray, delaySamples: Float): Float {
        val d = delaySamples.coerceIn(0f, (alignMask - 2).toFloat())
        val i0 = d.toInt()
        val frac = d - i0
        val s0 = buf[(alignPos - i0) and alignMask]
        val s1 = buf[(alignPos - i0 - 1) and alignMask]
        return s0 + (s1 - s0) * frac
    }

    private fun publishTelemetrySnapshot() {
        telemetryCounter++
        if (telemetryCounter % 4 != 0) return
        val pkDb = (20.0 * log10(peakMeter.coerceAtLeast(1e-4f).toDouble())).toFloat().coerceIn(-60f, 0f)
        val rmsDb = (10.0 * log10(rmsPowerMeter.coerceAtLeast(1e-7f).toDouble())).toFloat().coerceIn(-60f, 0f)
        _telemetry.value = HarmonicDynamicsTelemetry(
            active = true,
            peakDb = pkDb,
            rmsDb = rmsDb,
            gainReductionDb = maxGainReductionDb.coerceIn(0f, 16f),
            processedFrames = processedFrames,
        )
    }

    private fun readPcm24(buf: ByteBuffer): Float {
        val b0 = buf.get().toInt() and 0xFF
        val b1 = buf.get().toInt() and 0xFF
        val b2 = buf.get().toInt()
        val raw = b0 or (b1 shl 8) or (b2 shl 16)
        return raw.toFloat() / 8388607f
    }

    private fun writePcm24(buf: ByteBuffer, sample: Float) {
        val q = (sample.coerceIn(-1f, 1f) * 8388607f).roundToInt().coerceIn(-8388608, 8388607)
        buf.put((q and 0xFF).toByte())
        buf.put(((q ushr 8) and 0xFF).toByte())
        buf.put(((q ushr 16) and 0xFF).toByte())
    }

    override fun onFlush() {
        super.onFlush()
        clearBuffers()
    }

    override fun onReset() {
        super.onReset()
        clearBuffers()
        channelCount = 2
        encoding = C.ENCODING_INVALID
    }

    private fun clearBuffers() {
        alignBufL.fill(0f)
        alignBufR.fill(0f)
        alignPos = 0
        subLpL = 0f
        subLpR = 0f
        prevInL = 0f
        prevInR = 0f
        airHpL = 0f
        airHpR = 0f
        envFollower = 0f
        maxGainReductionDb = 0f
    }
}
