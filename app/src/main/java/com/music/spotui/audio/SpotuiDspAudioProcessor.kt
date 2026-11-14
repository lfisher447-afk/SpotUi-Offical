package com.music.spotui.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Software multi-band Parametric Biquad Equalizer + Preamp + Sub-Bass Boost +
 * RMS Loudness Normalizer + Soft-Knee Peak Limiter for ExoPlayer (PCM-16 & PCM-FLOAT).
 *
 * Guarantees crisp, audible EQ, Bass Boost, and Volume Normalization on every device
 * (including Bluetooth, USB DACs, and One UI HALs where platform `Equalizer` /
 * `LoudnessEnhancer` have limited bands or fail to attach).
 *
 * Importantly, `onReset()` clears filter delay lines WITHOUT resetting user DSP configuration,
 * so EQ curves persist reliably across track transitions, seeks, and background advances.
 */
@UnstableApi
class SpotuiDspAudioProcessor : BaseAudioProcessor() {

    companion object {
        // 7-band studio EQ center frequencies (Hz):
        // Sub-Bass (32), Bass (64), Low-Mid (250), Vocal (1000), Presence (4000), Treble (10000), Brilliance (16000)
        val BAND_FREQUENCIES_HZ = floatArrayOf(32f, 64f, 250f, 1000f, 4000f, 10000f, 16000f)
        private const val NUM_BANDS = 7
    }

    @Volatile var eqEnabled: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                coefficientsDirty = true
            }
        }

    // Gains stored in dB (-12f..+12f)
    private val bandGainsDb = FloatArray(NUM_BANDS) { 0f }

    @Volatile var preampDb: Float = 0f
        set(value) {
            field = value.coerceIn(-12f, 6f)
        }

    @Volatile var bassBoostDb: Float = 0f
        set(value) {
            val clamped = value.coerceIn(0f, 9f)
            if (field != clamped) {
                field = clamped
                coefficientsDirty = true
            }
        }

    @Volatile var limiterEnabled: Boolean = true
    @Volatile var normalizerEnabled: Boolean = false
    @Volatile var normalizerBoostDb: Float = 0f // 0..+6 dB makeup + adaptive RMS leveling

    @Volatile private var coefficientsDirty = true

    private var sampleRate = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID

    // Stereo Biquad coefficients & state arrays for 7 EQ bands + 1 dedicated BassBoost low-shelf filter
    private val totalFilters = NUM_BANDS + 1
    private val b0 = DoubleArray(totalFilters) { 1.0 }
    private val b1 = DoubleArray(totalFilters)
    private val b2 = DoubleArray(totalFilters)
    private val a1 = DoubleArray(totalFilters)
    private val a2 = DoubleArray(totalFilters)
    private val bandActive = BooleanArray(totalFilters)

    // Delay lines per channel (left=0, right=1)
    private val x1 = Array(2) { DoubleArray(totalFilters) }
    private val x2 = Array(2) { DoubleArray(totalFilters) }
    private val y1 = Array(2) { DoubleArray(totalFilters) }
    private val y2 = Array(2) { DoubleArray(totalFilters) }

    // Smooth RMS follower for automatic loudness normalization
    private var rmsEstimate = 0.1
    private var autoGainSmoothed = 1.0

    fun setBandGainDb(index: Int, gainDb: Float) {
        if (index !in 0 until NUM_BANDS) return
        val clamped = gainDb.coerceIn(-12f, 12f)
        if (abs(bandGainsDb[index] - clamped) > 0.01f) {
            bandGainsDb[index] = clamped
            coefficientsDirty = true
        }
    }

    fun getBandGainsDb(): FloatArray = bandGainsDb.copyOf()

    /** Maps legacy 0..100 UI sliders cleanly into -12..+12 dB per band. */
    fun setFromNormalizedSliders(
        subBass: Float,
        bass: Float,
        lowMid: Float,
        vocal: Float,
        highMid: Float,
        treble: Float,
        brilliance: Float,
    ) {
        fun sliderToDb(v: Float): Float = ((v.coerceIn(0f, 100f) - 50f) / 50f) * 12f
        setBandGainDb(0, sliderToDb(subBass))
        setBandGainDb(1, sliderToDb(bass))
        setBandGainDb(2, sliderToDb(lowMid))
        setBandGainDb(3, sliderToDb(vocal))
        setBandGainDb(4, sliderToDb(highMid))
        setBandGainDb(5, sliderToDb(treble))
        setBandGainDb(6, sliderToDb(brilliance))
    }

    fun hasActiveProcessing(): Boolean {
        val anyEqGain = eqEnabled && bandGainsDb.any { abs(it) > 0.15f }
        return anyEqGain || bassBoostDb > 0.15f || abs(preampDb) > 0.15f || normalizerEnabled
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding !in setOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT)) {
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

        if (sampleRate <= 0 || channelCount !in 1..2 || !hasActiveProcessing()) {
            val out = replaceOutputBuffer(remaining)
            copyBuffer(inputBuffer, out, remaining)
            out.flip()
            return
        }

        if (coefficientsDirty) {
            recomputeCoefficients()
            coefficientsDirty = false
        }

        inputBuffer.order(ByteOrder.nativeOrder())
        val output = replaceOutputBuffer(remaining).order(ByteOrder.nativeOrder())
        val preampLinear = 10.0.pow(preampDb.toDouble() / 20.0)

        when (encoding) {
            C.ENCODING_PCM_FLOAT -> {
                if (channelCount == 2) processStereoFloat(inputBuffer, output, preampLinear)
                else processMonoFloat(inputBuffer, output, preampLinear)
            }
            C.ENCODING_PCM_16BIT -> {
                if (channelCount == 2) processStereoPcm16(inputBuffer, output, preampLinear)
                else processMonoPcm16(inputBuffer, output, preampLinear)
            }
            else -> copyBuffer(inputBuffer, output, remaining)
        }
        output.flip()
    }

    private fun processMonoPcm16(input: ByteBuffer, output: ByteBuffer, preampLinear: Double) {
        while (input.remaining() >= 2) {
            val s = input.short.toDouble() / Short.MAX_VALUE
            val processed = processChannelSample(0, s * preampLinear)
            val norm = applyNormalizerAndLimiter(processed, processed).first
            output.putShort((norm.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).roundToInt().toShort())
        }
    }

    private fun processStereoPcm16(input: ByteBuffer, output: ByteBuffer, preampLinear: Double) {
        while (input.remaining() >= 4) {
            val lIn = input.short.toDouble() / Short.MAX_VALUE
            val rIn = input.short.toDouble() / Short.MAX_VALUE
            val lEq = processChannelSample(0, lIn * preampLinear)
            val rEq = processChannelSample(1, rIn * preampLinear)
            val (lOut, rOut) = applyNormalizerAndLimiter(lEq, rEq)
            output.putShort((lOut.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).roundToInt().toShort())
            output.putShort((rOut.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).roundToInt().toShort())
        }
    }

    private fun processMonoFloat(input: ByteBuffer, output: ByteBuffer, preampLinear: Double) {
        while (input.remaining() >= 4) {
            val s = input.float.toDouble()
            val processed = processChannelSample(0, s * preampLinear)
            val norm = applyNormalizerAndLimiter(processed, processed).first
            output.putFloat(norm.coerceIn(-1.0, 1.0).toFloat())
        }
    }

    private fun processStereoFloat(input: ByteBuffer, output: ByteBuffer, preampLinear: Double) {
        while (input.remaining() >= 8) {
            val lIn = input.float.toDouble()
            val rIn = input.float.toDouble()
            val lEq = processChannelSample(0, lIn * preampLinear)
            val rEq = processChannelSample(1, rIn * preampLinear)
            val (lOut, rOut) = applyNormalizerAndLimiter(lEq, rEq)
            output.putFloat(lOut.coerceIn(-1.0, 1.0).toFloat())
            output.putFloat(rOut.coerceIn(-1.0, 1.0).toFloat())
        }
    }

    private fun processChannelSample(ch: Int, initialSample: Double): Double {
        var sample = initialSample
        val x1c = x1[ch]
        val x2c = x2[ch]
        val y1c = y1[ch]
        val y2c = y2[ch]

        for (i in 0 until totalFilters) {
            if (!bandActive[i]) continue
            val y = b0[i] * sample + b1[i] * x1c[i] + b2[i] * x2c[i] - a1[i] * y1c[i] - a2[i] * y2c[i]
            x2c[i] = x1c[i]
            x1c[i] = sample
            y2c[i] = y1c[i]
            y1c[i] = y
            sample = y
        }
        return sample
    }

    private fun applyNormalizerAndLimiter(lIn: Double, rIn: Double): Pair<Double, Double> {
        var l = lIn
        var r = rIn
        if (normalizerEnabled) {
            val power = (l * l + r * r) * 0.5
            rmsEstimate = rmsEstimate * 0.9992 + power * 0.0008
            val rms = sqrt(rmsEstimate).coerceAtLeast(0.015)
            val targetRms = 0.22 * 10.0.pow(normalizerBoostDb.toDouble() / 20.0)
            val desiredGain = (targetRms / rms).coerceIn(0.65, 2.6)
            val alpha = if (desiredGain < autoGainSmoothed) 0.015 else 0.0006
            autoGainSmoothed = autoGainSmoothed * (1.0 - alpha) + desiredGain * alpha
            l *= autoGainSmoothed
            r *= autoGainSmoothed
        }
        if (limiterEnabled) {
            // Soft-knee saturation above 0.88 prevents digital clipping when EQ bands boost
            val threshold = 0.88
            if (abs(l) > threshold) {
                val excess = abs(l) - threshold
                l = (if (l >= 0) 1.0 else -1.0) * (threshold + (1.0 - threshold) * tanh(excess / (1.0 - threshold)))
            }
            if (abs(r) > threshold) {
                val excess = abs(r) - threshold
                r = (if (r >= 0) 1.0 else -1.0) * (threshold + (1.0 - threshold) * tanh(excess / (1.0 - threshold)))
            }
        }
        return l to r
    }

    private fun recomputeCoefficients() {
        val sr = sampleRate.toDouble().coerceAtLeast(8000.0)
        for (i in 0 until NUM_BANDS) {
            val gainDb = if (eqEnabled) bandGainsDb[i].toDouble() else 0.0
            if (abs(gainDb) < 0.1) {
                bandActive[i] = false
                continue
            }
            bandActive[i] = true
            when (i) {
                0 -> computeLowShelf(i, BAND_FREQUENCIES_HZ[i].toDouble(), sr, gainDb, slope = 0.85)
                NUM_BANDS - 1 -> computeHighShelf(i, BAND_FREQUENCIES_HZ[i].toDouble().coerceAtMost(sr * 0.45), sr, gainDb, slope = 0.85)
                else -> computePeaking(i, BAND_FREQUENCIES_HZ[i].toDouble().coerceAtMost(sr * 0.45), sr, gainDb, q = 1.15)
            }
        }
        // Dedicated BassBoost filter at index NUM_BANDS (85 Hz low shelf)
        val bb = bassBoostDb.toDouble()
        if (bb > 0.1) {
            bandActive[NUM_BANDS] = true
            computeLowShelf(NUM_BANDS, 85.0, sr, bb, slope = 0.9)
        } else {
            bandActive[NUM_BANDS] = false
        }
    }

    private fun computePeaking(idx: Int, freq: Double, sr: Double, gainDb: Double, q: Double) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * freq / sr
        val sinW0 = sin(w0)
        val cosW0 = cos(w0)
        val alpha = sinW0 / (2.0 * q)

        val rawB0 = 1.0 + alpha * a
        val rawB1 = -2.0 * cosW0
        val rawB2 = 1.0 - alpha * a
        val rawA0 = 1.0 + alpha / a
        val rawA1 = -2.0 * cosW0
        val rawA2 = 1.0 - alpha / a

        b0[idx] = rawB0 / rawA0
        b1[idx] = rawB1 / rawA0
        b2[idx] = rawB2 / rawA0
        a1[idx] = rawA1 / rawA0
        a2[idx] = rawA2 / rawA0
    }

    private fun computeLowShelf(idx: Int, freq: Double, sr: Double, gainDb: Double, slope: Double) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * freq / sr
        val sinW0 = sin(w0)
        val cosW0 = cos(w0)
        val alpha = sinW0 / 2.0 * sqrt((a + 1.0 / a) * (1.0 / slope - 1.0) + 2.0)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha

        val rawB0 = a * ((a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha)
        val rawB1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosW0)
        val rawB2 = a * ((a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha)
        val rawA0 = (a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha
        val rawA1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosW0)
        val rawA2 = (a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha

        b0[idx] = rawB0 / rawA0
        b1[idx] = rawB1 / rawA0
        b2[idx] = rawB2 / rawA0
        a1[idx] = rawA1 / rawA0
        a2[idx] = rawA2 / rawA0
    }

    private fun computeHighShelf(idx: Int, freq: Double, sr: Double, gainDb: Double, slope: Double) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * freq / sr
        val sinW0 = sin(w0)
        val cosW0 = cos(w0)
        val alpha = sinW0 / 2.0 * sqrt((a + 1.0 / a) * (1.0 / slope - 1.0) + 2.0)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha

        val rawB0 = a * ((a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha)
        val rawB1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosW0)
        val rawB2 = a * ((a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha)
        val rawA0 = (a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha
        val rawA1 = 2.0 * ((a - 1.0) - (a + 1.0) * cosW0)
        val rawA2 = (a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha

        b0[idx] = rawB0 / rawA0
        b1[idx] = rawB1 / rawA0
        b2[idx] = rawB2 / rawA0
        a1[idx] = rawA1 / rawA0
        a2[idx] = rawA2 / rawA0
    }

    private fun clearDelayLines() {
        for (ch in 0..1) {
            x1[ch].fill(0.0)
            x2[ch].fill(0.0)
            y1[ch].fill(0.0)
            y2[ch].fill(0.0)
        }
        autoGainSmoothed = 1.0
    }

    private fun copyBuffer(src: ByteBuffer, dst: ByteBuffer, size: Int) {
        if (src === dst) {
            dst.position(0)
            dst.limit(size)
            return
        }
        val pos = src.position()
        for (i in 0 until size) dst.put(src.get(pos + i))
        src.position(pos + size)
    }

    override fun onFlush() {
        super.onFlush()
        clearDelayLines()
    }

    override fun onReset() {
        super.onReset()
        // CRITICAL: Only clear runtime format & filter delay state. Never wipe user EQ / Bass / Normalizer settings!
        clearDelayLines()
        sampleRate = 0
        channelCount = 0
        encoding = C.ENCODING_INVALID
    }
}
