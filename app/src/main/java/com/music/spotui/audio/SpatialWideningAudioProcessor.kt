package com.music.spotui.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.music.spotui.data.preferences.SpatialProfile
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
 * SpotUI 3D Holographic Binaural Soundstage & 8D Orbit Audio Processor (v2.5.4).
 *
 * Delivers an unmistakable, studio-grade out-of-head 3D soundstage over headphones,
 * Bluetooth earbuds, and stereo speakers without hollowness or bass smear:
 * 1. Sub-100 Hz Mono-Bass Anchor keeps kick/sub punch tight and centered.
 * 2. Bold Mid/Side Soundstage Expansion + Asymmetric Haas Micro-Delay widens the stereo
 *    horizon vividly beyond the ears as [width] and [profile] increase.
 * 3. Tapped Early-Reflection Acoustic Matrix + 3-Stage Schroeder All-Pass Diffuser builds
 *    genuine physical hall/cinema/room depth scaled by [roomDelayMs] and [elevationDepth].
 * 4. Outer-Ear Pinna HRTF Contours (+concha presence ~3.2 kHz / elevation notch ~7.6 kHz)
 *    externalize instruments out in front and above the listener.
 * 5. Deep 360-degree 8D Binaural Orbit LFO sweeps constant-power pan, fractional Woodworth
 *    ITD ear delay, and rear-hemisphere head-shadowing when [orbit8dEnabled] is active.
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
    @Volatile var orbit8dEnabled: Boolean = false
    @Volatile var orbitSpeedHz: Float = 0.32f
    @Volatile var elevationDepth: Float = 0.70f
    @Volatile var correlationGuard: Boolean = true

    @Volatile var activeDescription: String = "Bypassed"
        private set
    @Volatile var processedFrames: Long = 0L
        private set
    @Volatile var currentCorrelation: Float = 1f
        private set

    private var sampleRate = 48_000
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID

    // Circular stereo delay buffers (~42ms+ at up to 192kHz -> 8192 samples power-of-two)
    private val bufMask = 8191
    private val delayL = FloatArray(bufMask + 1)
    private val delayR = FloatArray(bufMask + 1)
    private val reflectBufL = FloatArray(bufMask + 1)
    private val reflectBufR = FloatArray(bufMask + 1)
    private var writePos = 0

    // 3-Stage Cascaded Schroeder All-Pass Decorrelator Tanks (pre-allocated, zero GC)
    private val apMask1 = 1023
    private val apMask2 = 2047
    private val apMask3 = 4095
    private val apL1 = FloatArray(apMask1 + 1)
    private val apR1 = FloatArray(apMask1 + 1)
    private val apL2 = FloatArray(apMask2 + 1)
    private val apR2 = FloatArray(apMask2 + 1)
    private val apL3 = FloatArray(apMask3 + 1)
    private val apR3 = FloatArray(apMask3 + 1)
    private var apPos1 = 0
    private var apPos2 = 0
    private var apPos3 = 0

    // Multi-band & acoustic one-pole filters
    private var bassLpL = 0f
    private var bassLpR = 0f
    private var airLpL = 0f
    private var airLpR = 0f

    // Biquad head-shadow (contralateral ILD low-pass) & outer-ear Pinna elevation filters
    private val shadowFilterL = ScalarBiquad()
    private val shadowFilterR = ScalarBiquad()
    private val pinnaPeakL = ScalarBiquad()
    private val pinnaPeakR = ScalarBiquad()
    private val pinnaNotchL = ScalarBiquad()
    private val pinnaNotchR = ScalarBiquad()

    // Real-time stereo correlation estimator
    private var corrLR = 0f
    private var corrPower = 1e-6f

    // 8D Binaural Orbit LFO phase (radians) & block coefficient update cadence
    private var orbitPhase = 0.0
    private var biquadUpdateCounter = 0
    private var lastFilterSampleRate = 0
    private var lastFilterProfile: SpatialProfile? = null
    private var lastFilterElevation = -1f

    // Smooth control gain ramps (~6ms) eliminating clicks/zipper noise
    private var smoothedMix = 0f
    private var smoothedWidth = 0f
    private var smoothedCrossfeed = 0f
    private var smoothedBalance = 0f
    private var smoothedOrbit = 0f
    private var smoothedElevation = 0.70f

    fun telemetrySummary(): String {
        val srKhz = if (sampleRate > 0) "${sampleRate / 1000}kHz" else "--"
        val encLabel = when (encoding) {
            C.ENCODING_PCM_16BIT -> "16-bit"
            C.ENCODING_PCM_24BIT -> "24-bit"
            C.ENCODING_PCM_32BIT -> "32-bit"
            C.ENCODING_PCM_FLOAT -> "Float32"
            else -> "Idle"
        }
        val corrStr = if (enabled && !monoMix) " · corr ${String.format("%.2f", currentCorrelation)}" else ""
        return "$activeDescription · $srKhz $encLabel$corrStr · ${processedFrames / 1000}k frames"
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val supported = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_24BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_32BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        if (!supported) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate.coerceIn(8000, 192000)
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding
        forceUpdateStaticBiquads()
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytes = inputBuffer.remaining()
        if (bytes == 0) return

        val explicitHrtf = headphoneCrossfeed
        val targetActive = channelCount == 2 && (enabled || monoMix || explicitHrtf || abs(balance) > 0.001f)
        val stillRamping = channelCount == 2 && (smoothedMix > 0.002f || smoothedCrossfeed > 0.002f || abs(smoothedBalance) > 0.002f)

        if (!targetActive && !stillRamping) {
            activeDescription = if (channelCount == 2) "Stereo bypass" else "Bypass: ${channelCount}-channel source"
            val output = replaceOutputBuffer(bytes)
            copyBuffer(inputBuffer, output, bytes)
            output.flip()
            return
        }

        if (sampleRate != lastFilterSampleRate || profile != lastFilterProfile || abs(elevationDepth - lastFilterElevation) > 0.01f) {
            forceUpdateStaticBiquads()
        }

        activeDescription = buildString {
            when {
                monoMix -> append("Mono mix")
                enabled -> {
                    val label = if (profile.label.contains("3D", ignoreCase = true)) profile.label else "${profile.label} 3D"
                    append("$label (${(width.coerceIn(0f, 1f) * 100).roundToInt()}%)")
                    if (orbit8dEnabled) append(" · 8D Orbit")
                }
                headphoneCrossfeed -> append("HRTF Crossfeed")
                else -> append("Stereo balance")
            }
            if (headphoneCrossfeed && enabled) append(" · Crossfeed")
            if (abs(balance) > 0.001f) {
                append(if (balance > 0f) " · R ${(balance * 100).roundToInt()}%" else " · L ${(-balance * 100).roundToInt()}%")
            }
        }

        val targetMix = if (enabled && !monoMix) 1f else 0f
        // Guarantee perceptible 3D floor even when slider is around 25-40%
        val targetWidth = width.coerceIn(0f, 1f)
        // Crossfeed only narrows/blends when explicitly toggled (or gently as natural Binaural cross-ear anchor)
        val targetCf = when {
            monoMix -> 0f
            headphoneCrossfeed -> 1f
            enabled && profile == SpatialProfile.BINAURAL_HEADPHONES -> 0.35f
            else -> 0f
        }
        val targetBal = balance.coerceIn(-1f, 1f)
        val targetOrbit = if (enabled && !monoMix && orbit8dEnabled) 1f else 0f
        val targetElev = elevationDepth.coerceIn(0f, 1f)

        inputBuffer.order(ByteOrder.nativeOrder())
        val output = replaceOutputBuffer(bytes).order(ByteOrder.nativeOrder())
        when (encoding) {
            C.ENCODING_PCM_FLOAT -> processFloat(inputBuffer, output, targetMix, targetWidth, targetCf, targetBal, targetOrbit, targetElev)
            C.ENCODING_PCM_16BIT -> processPcm16(inputBuffer, output, targetMix, targetWidth, targetCf, targetBal, targetOrbit, targetElev)
            C.ENCODING_PCM_24BIT -> processPcm24(inputBuffer, output, targetMix, targetWidth, targetCf, targetBal, targetOrbit, targetElev)
            C.ENCODING_PCM_32BIT -> processPcm32(inputBuffer, output, targetMix, targetWidth, targetCf, targetBal, targetOrbit, targetElev)
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
        targetOrbit: Float,
        targetElev: Float,
    ) {
        while (input.remaining() >= 8) {
            val (left, right) = transform(input.float, input.float, targetMix, targetWidth, targetCf, targetBal, targetOrbit, targetElev)
            output.putFloat(left)
            output.putFloat(right)
            processedFrames++
        }
    }

    private fun processPcm16(
        input: ByteBuffer,
        output: ByteBuffer,
        targetMix: Float,
        targetWidth: Float,
        targetCf: Float,
        targetBal: Float,
        targetOrbit: Float,
        targetElev: Float,
    ) {
        while (input.remaining() >= 4) {
            val left = input.short.toFloat() / Short.MAX_VALUE
            val right = input.short.toFloat() / Short.MAX_VALUE
            val (outLeft, outRight) = transform(left, right, targetMix, targetWidth, targetCf, targetBal, targetOrbit, targetElev)
            output.putShort(toPcm16(outLeft))
            output.putShort(toPcm16(outRight))
            processedFrames++
        }
    }

    private fun processPcm24(
        input: ByteBuffer,
        output: ByteBuffer,
        targetMix: Float,
        targetWidth: Float,
        targetCf: Float,
        targetBal: Float,
        targetOrbit: Float,
        targetElev: Float,
    ) {
        while (input.remaining() >= 6) {
            val left = readPcm24(input)
            val right = readPcm24(input)
            val (outLeft, outRight) = transform(left, right, targetMix, targetWidth, targetCf, targetBal, targetOrbit, targetElev)
            writePcm24(output, outLeft)
            writePcm24(output, outRight)
            processedFrames++
        }
    }

    private fun processPcm32(
        input: ByteBuffer,
        output: ByteBuffer,
        targetMix: Float,
        targetWidth: Float,
        targetCf: Float,
        targetBal: Float,
        targetOrbit: Float,
        targetElev: Float,
    ) {
        while (input.remaining() >= 8) {
            val left = (input.int.toDouble() / 2147483647.0).toFloat()
            val right = (input.int.toDouble() / 2147483647.0).toFloat()
            val (outLeft, outRight) = transform(left, right, targetMix, targetWidth, targetCf, targetBal, targetOrbit, targetElev)
            output.putInt((outLeft.coerceIn(-1f, 1f).toDouble() * 2147483647.0).roundToInt())
            output.putInt((outRight.coerceIn(-1f, 1f).toDouble() * 2147483647.0).roundToInt())
            processedFrames++
        }
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

    private fun readDelayInterp(buffer: FloatArray, delaySamples: Float): Float {
        val d = delaySamples.coerceIn(0f, (bufMask - 2).toFloat())
        val i0 = d.toInt()
        val frac = d - i0
        val idx0 = (writePos - i0) and bufMask
        val idx1 = (writePos - i0 - 1) and bufMask
        val s0 = buffer[idx0]
        val s1 = buffer[idx1]
        return s0 + (s1 - s0) * frac
    }

    private fun transform(
        rawLeft: Float,
        rawRight: Float,
        targetMix: Float,
        targetWidth: Float,
        targetCf: Float,
        targetBal: Float,
        targetOrbit: Float,
        targetElev: Float,
    ): Pair<Float, Float> {
        val left = if (rawLeft.isFinite()) rawLeft.coerceIn(-2f, 2f) else 0f
        val right = if (rawRight.isFinite()) rawRight.coerceIn(-2f, 2f) else 0f

        val sr = sampleRate.toFloat().coerceAtLeast(8000f)
        val ctrlAlpha = (280f / sr).coerceIn(0.002f, 0.06f)
        smoothedMix += (targetMix - smoothedMix) * ctrlAlpha
        smoothedWidth += (targetWidth - smoothedWidth) * ctrlAlpha
        smoothedCrossfeed += (targetCf - smoothedCrossfeed) * ctrlAlpha
        smoothedBalance += (targetBal - smoothedBalance) * ctrlAlpha
        smoothedOrbit += (targetOrbit - smoothedOrbit) * ctrlAlpha
        smoothedElevation += (targetElev - smoothedElevation) * ctrlAlpha

        // Real-time stereo correlation tracker (~25ms window)
        val corrAlpha = (250f / sr).coerceIn(0.001f, 0.04f)
        corrLR += ((left * right) - corrLR) * corrAlpha
        corrPower += (((left * left + right * right) * 0.5f) - corrPower) * corrAlpha
        val instantCorr = (corrLR / (corrPower + 1e-6f)).coerceIn(-1f, 1f)
        currentCorrelation = instantCorr

        delayL[writePos] = left
        delayR[writePos] = right

        // Sub-100 Hz bass anchor filter & air absorption LP
        val bassAlpha = (640f / sr).coerceIn(0.003f, 0.13f)
        val airAlpha = (14500f / sr).coerceIn(0.08f, 0.65f)
        bassLpL += (left - bassLpL) * bassAlpha
        bassLpR += (right - bassLpR) * bassAlpha

        val shadowL = shadowFilterL.process(left)
        val shadowR = shadowFilterR.process(right)

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
                val widthCurve = (0.30f + 0.70f * smoothedWidth) * smoothedMix
                val baseIntensity = (widthCurve * profileMultiplier(profile)).coerceIn(0f, 1.55f)
                // Only restrain when source is genuinely out-of-phase (corr < -0.15)
                val corrFactor = if (correlationGuard && instantCorr < -0.15f) {
                    (1f + (instantCorr + 0.15f) * 0.65f).coerceIn(0.45f, 1f)
                } else {
                    1f
                }
                val intensity = (baseIntensity * corrFactor).coerceIn(0f, 1.55f)

                val mid = (left + right) * 0.5f
                val side = (left - right) * 0.5f
                val lowMono = (bassLpL + bassLpR) * 0.5f
                val lowSide = (bassLpL - bassLpR) * 0.5f
                val sideHigh = side - lowSide

                // 1. Bold M/S stage expansion + Haas stereo micro-delay externalization
                val haasMs = profileHaasMs(profile) * (0.35f + 0.65f * smoothedWidth)
                val haasDelaySamples = ((haasMs / 1000f) * sr).coerceIn(1f, 240f)
                val delayedHighR = readDelayInterp(delayR, haasDelaySamples) - lowMono
                val delayedHighL = readDelayInterp(delayL, haasDelaySamples * 1.18f) - lowMono
                val haasInject = (0.28f * intensity).coerceIn(0f, 0.42f)

                val expandedSide = side + sideHigh * (intensity * 1.48f)
                val centerFocusBoost = profileCenterFocus(profile) * intensity.coerceAtMost(1f)

                var stageL = mid * (1f + centerFocusBoost * 0.08f) + expandedSide +
                    (lowMono - bassLpL) * 0.42f * intensity - delayedHighR * haasInject
                var stageR = mid * (1f + centerFocusBoost * 0.08f) - expandedSide +
                    (lowMono - bassLpR) * 0.42f * intensity - delayedHighL * haasInject

                // Write clean high-passed stage into early-reflection ring buffer
                val reflInL = stageL - lowMono
                val reflInR = stageR - lowMono
                reflectBufL[writePos] = reflInL
                reflectBufR[writePos] = reflInR

                // 2. Audible Early Reflections + 3-Stage Schroeder Diffuser Tank (creates real physical room depth)
                val srScale = sr / 48000f
                val roomScale = (roomDelayMs / 12f).coerceIn(0.35f, 2.5f) * profileRoomFactor(profile)
                val tap1L = readDelayInterp(reflectBufL, 336f * srScale * roomScale)
                val tap1R = readDelayInterp(reflectBufR, 408f * srScale * roomScale)
                val tap2L = readDelayInterp(reflectBufR, 624f * srScale * roomScale)
                val tap2R = readDelayInterp(reflectBufL, 744f * srScale * roomScale)
                val tap3L = readDelayInterp(reflectBufL, 1008f * srScale * roomScale)
                val tap3R = readDelayInterp(reflectBufR, 1176f * srScale * roomScale)

                val earlyL = tap1L * 0.46f + tap2L * 0.34f + tap3L * 0.20f
                val earlyR = tap1R * 0.46f + tap2R * 0.34f + tap3R * 0.20f

                val dL1 = (149f * srScale * roomScale).roundToInt().coerceIn(19, apMask1 - 2)
                val dR1 = (173f * srScale * roomScale).roundToInt().coerceIn(23, apMask1 - 2)
                val dL2 = (313f * srScale * roomScale).roundToInt().coerceIn(41, apMask2 - 2)
                val dR2 = (349f * srScale * roomScale).roundToInt().coerceIn(47, apMask2 - 2)
                val dL3 = (541f * srScale * roomScale).roundToInt().coerceIn(71, apMask3 - 2)
                val dR3 = (593f * srScale * roomScale).roundToInt().coerceIn(79, apMask3 - 2)

                val apOutL1 = stepAllPass(apL1, apMask1, apPos1, dL1, earlyL, 0.54f)
                val apOutR1 = stepAllPass(apR1, apMask1, apPos1, dR1, earlyR, 0.54f)
                val apOutL2 = stepAllPass(apL2, apMask2, apPos2, dL2, apOutL1, 0.48f)
                val apOutR2 = stepAllPass(apR2, apMask2, apPos2, dR2, apOutR1, 0.48f)
                val apOutL3 = stepAllPass(apL3, apMask3, apPos3, dL3, apOutL2, 0.42f)
                val apOutR3 = stepAllPass(apR3, apMask3, apPos3, dR3, apOutR2, 0.42f)

                airLpL += ((apOutL3 * 0.58f + earlyL * 0.42f) - airLpL) * airAlpha
                airLpR += ((apOutR3 * 0.58f + earlyR * 0.42f) - airLpR) * airAlpha

                val wetRoom = (profileReflectionAmount(profile) * (0.42f + 0.78f * smoothedElevation) * intensity).coerceIn(0f, 0.62f)
                stageL = stageL * (1f - wetRoom * 0.16f) + (airLpL * 0.76f + airLpR * 0.24f) * wetRoom
                stageR = stageR * (1f - wetRoom * 0.16f) + (airLpR * 0.76f + airLpL * 0.24f) * wetRoom

                // 3. Outer-ear Pinna HRTF contour (+concha presence ~3.2 kHz + elevation notch ~7.6 kHz)
                val pinnaMix = (smoothedElevation * (0.60f + 0.40f * smoothedMix)).coerceIn(0f, 1f)
                if (pinnaMix > 0.02f) {
                    val pL = pinnaNotchL.process(pinnaPeakL.process(stageL))
                    val pR = pinnaNotchR.process(pinnaPeakR.process(stageR))
                    stageL += (pL - stageL) * pinnaMix * 0.90f
                    stageR += (pR - stageR) * pinnaMix * 0.90f
                }

                // 4. Deep 360° 8D Binaural Orbit LFO (unmistakable head-orbiting pan, ITD, and rear darkening)
                if (smoothedOrbit > 0.002f) {
                    val hz = orbitSpeedHz.coerceIn(0.10f, 1.50f).toDouble()
                    orbitPhase += (2.0 * PI * hz) / sr.toDouble()
                    if (orbitPhase >= 2.0 * PI) orbitPhase -= 2.0 * PI

                    val azSin = sin(orbitPhase).toFloat() // -1 (Left) .. +1 (Right)
                    val azCos = cos(orbitPhase).toFloat() // +1 (Front) .. -1 (Rear)
                    val orbitDepth = smoothedOrbit * (0.65f + 0.35f * smoothedWidth)

                    // Pronounced Woodworth binaural ear delay (0 .. 0.95ms ITD)
                    val maxItdSamples = (0.00095f * sr).coerceIn(3f, 160f)
                    val delayLeftSamples = if (azSin > 0f) azSin * maxItdSamples * smoothedOrbit else 0f
                    val delayRightSamples = if (azSin < 0f) (-azSin) * maxItdSamples * smoothedOrbit else 0f

                    // Constant-power circular azimuth pan law (deep ear-to-ear sweep)
                    val panShift = (azSin * 0.76f * orbitDepth).coerceIn(-0.82f, 0.82f)
                    val leftGain = sqrt((1f - panShift).coerceIn(0.14f, 1.85f))
                    val rightGain = sqrt((1f + panShift).coerceIn(0.14f, 1.85f))
                    val rearShadow = ((1f - azCos) * 0.5f * 0.42f * smoothedOrbit).coerceIn(0f, 0.45f)

                    val itdL = readDelayInterp(delayL, delayLeftSamples)
                    val itdR = readDelayInterp(delayR, delayRightSamples)
                    val stageHighL = stageL - lowMono
                    val stageHighR = stageR - lowMono
                    val itdBlend = 0.65f * smoothedOrbit
                    val orbL = (stageHighL * (1f - itdBlend) + (itdL - lowMono) * itdBlend) * leftGain
                    val orbR = (stageHighR * (1f - itdBlend) + (itdR - lowMono) * itdBlend) * rightGain

                    stageL = lowMono + orbL * (1f - rearShadow) + (shadowL - lowMono) * rearShadow
                    stageR = lowMono + orbR * (1f - rearShadow) + (shadowR - lowMono) * rearShadow

                    if (++biquadUpdateCounter >= 96) {
                        biquadUpdateCounter = 0
                        updateDynamicOrbitPinna(sr, azCos, smoothedElevation)
                    }
                }

                // Makeup gain preserving perceived punch when ambient room spreads energy
                val loudnessMakeup = 1f + intensity.coerceAtMost(1.2f) * 0.06f
                l = stageL * loudnessMakeup
                r = stageR * loudnessMakeup
            }

            // Optional Headphone Crossfeed (only active when explicitly enabled or Binaural gentle anchor)
            if (smoothedCrossfeed > 0.0005f) {
                val itDelayMs = if (profile == SpatialProfile.BINAURAL_HEADPHONES) 0.42f else 0.30f
                val itdSamples = (itDelayMs / 1000f) * sr
                val crossRtoL = readDelayInterp(delayR, itdSamples)
                val crossLtoR = readDelayInterp(delayL, itdSamples)
                val cfGain = (0.20f * smoothedCrossfeed).coerceIn(0f, 0.24f)
                val bleedL = shadowR * 0.64f + crossRtoL * 0.36f
                val bleedR = shadowL * 0.64f + crossLtoR * 0.36f
                l = l * (1f - cfGain * 0.34f) + bleedL * cfGain
                r = r * (1f - cfGain * 0.34f) + bleedR * cfGain
            }
        }

        writePos = (writePos + 1) and bufMask
        apPos1 = (apPos1 + 1) and apMask1
        apPos2 = (apPos2 + 1) and apMask2
        apPos3 = (apPos3 + 1) and apMask3

        if (smoothedBalance > 0.0005f) {
            l *= 1f - smoothedBalance
        } else if (smoothedBalance < -0.0005f) {
            r *= 1f + smoothedBalance
        }

        l = softLimit(l)
        r = softLimit(r)
        return l to r
    }

    private inline fun stepAllPass(
        buf: FloatArray,
        mask: Int,
        writeIdx: Int,
        delayLen: Int,
        input: Float,
        g: Float,
    ): Float {
        val readIdx = (writeIdx - delayLen) and mask
        val delayed = buf[readIdx]
        val v = (input + g * delayed).coerceIn(-4f, 4f)
        buf[writeIdx] = v
        return delayed - g * v
    }

    private fun forceUpdateStaticBiquads() {
        val sr = sampleRate.toFloat().coerceAtLeast(8000f)
        lastFilterSampleRate = sampleRate
        lastFilterProfile = profile
        lastFilterElevation = elevationDepth

        val shadowHz = if (profile == SpatialProfile.BINAURAL_HEADPHONES) 820f else 980f
        shadowFilterL.configureLowPass(sr, shadowHz, 0.707f)
        shadowFilterR.configureLowPass(sr, shadowHz, 0.707f)

        // Pronounced outer-ear concha presence (+4.2 dB @ 3.2 kHz scaled by elevationDepth)
        val peakGainDb = 4.2f * elevationDepth.coerceIn(0f, 1f)
        pinnaPeakL.configurePeaking(sr, 3200f, 1.15f, peakGainDb)
        pinnaPeakR.configurePeaking(sr, 3200f, 1.15f, peakGainDb)

        // Outer-ear Pinna elevation notch (-4.8 dB @ 7.6 kHz)
        val notchDb = -4.8f * elevationDepth.coerceIn(0f, 1f)
        pinnaNotchL.configurePeaking(sr, 7600f, 1.85f, notchDb)
        pinnaNotchR.configurePeaking(sr, 7600f, 1.85f, notchDb)
    }

    private fun updateDynamicOrbitPinna(sr: Float, azCos: Float, elev: Float) {
        val rearBias = ((1f - azCos) * 0.5f).coerceIn(0f, 1f)
        val dynamicNotchDb = -(2.4f + 4.8f * rearBias) * elev
        pinnaNotchL.configurePeaking(sr, 7600f - rearBias * 1200f, 1.8f, dynamicNotchDb)
        pinnaNotchR.configurePeaking(sr, 7600f - rearBias * 1200f, 1.8f, dynamicNotchDb)
    }

    private fun softLimit(x: Float): Float {
        if (!x.isFinite()) return 0f
        val threshold = 0.90f
        val ax = abs(x)
        if (ax <= threshold) return x
        val excess = ax - threshold
        val shaped = threshold + (1f - threshold) * tanh(excess / (1f - threshold))
        return if (x >= 0f) shaped.coerceAtMost(0.998f) else (-shaped).coerceAtLeast(-0.998f)
    }

    private fun copyBuffer(src: ByteBuffer, dst: ByteBuffer, size: Int) {
        val position = src.position()
        for (index in 0 until size) dst.put(src.get(position + index))
        src.position(position + size)
    }

    private fun toPcm16(value: Float): Short =
        (value.coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt().toShort()

    private fun profileMultiplier(profile: SpatialProfile): Float = when (profile) {
        SpatialProfile.STUDIO -> 0.85f
        SpatialProfile.WIDE -> 1.28f
        SpatialProfile.IMMERSIVE -> 1.38f
        SpatialProfile.CINEMA -> 1.15f
        SpatialProfile.CONCERT_HALL -> 1.24f
        SpatialProfile.BINAURAL_HEADPHONES -> 1.34f
    }

    private fun profileHaasMs(profile: SpatialProfile): Float = when (profile) {
        SpatialProfile.STUDIO -> 0.45f
        SpatialProfile.WIDE -> 1.15f
        SpatialProfile.IMMERSIVE -> 1.40f
        SpatialProfile.CINEMA -> 1.75f
        SpatialProfile.CONCERT_HALL -> 2.15f
        SpatialProfile.BINAURAL_HEADPHONES -> 0.72f
    }

    private fun profileCenterFocus(profile: SpatialProfile): Float = when (profile) {
        SpatialProfile.STUDIO -> 0.65f
        SpatialProfile.WIDE -> 0.25f
        SpatialProfile.IMMERSIVE -> 0.35f
        SpatialProfile.CINEMA -> 0.55f
        SpatialProfile.CONCERT_HALL -> 0.30f
        SpatialProfile.BINAURAL_HEADPHONES -> 0.45f
    }

    private fun profileReflectionAmount(profile: SpatialProfile): Float = when (profile) {
        SpatialProfile.STUDIO -> 0.16f
        SpatialProfile.WIDE -> 0.26f
        SpatialProfile.IMMERSIVE -> 0.38f
        SpatialProfile.CINEMA -> 0.48f
        SpatialProfile.CONCERT_HALL -> 0.56f
        SpatialProfile.BINAURAL_HEADPHONES -> 0.36f
    }

    private fun profileRoomFactor(profile: SpatialProfile): Float = when (profile) {
        SpatialProfile.STUDIO -> 0.58f
        SpatialProfile.WIDE -> 0.92f
        SpatialProfile.IMMERSIVE -> 1.15f
        SpatialProfile.CINEMA -> 1.48f
        SpatialProfile.CONCERT_HALL -> 1.85f
        SpatialProfile.BINAURAL_HEADPHONES -> 0.82f
    }

    private fun clearDelayState() {
        delayL.fill(0f)
        delayR.fill(0f)
        reflectBufL.fill(0f)
        reflectBufR.fill(0f)
        apL1.fill(0f); apR1.fill(0f)
        apL2.fill(0f); apR2.fill(0f)
        apL3.fill(0f); apR3.fill(0f)
        writePos = 0
        apPos1 = 0; apPos2 = 0; apPos3 = 0
        bassLpL = 0f; bassLpR = 0f
        airLpL = 0f; airLpR = 0f
        shadowFilterL.resetState(); shadowFilterR.resetState()
        pinnaPeakL.resetState(); pinnaPeakR.resetState()
        pinnaNotchL.resetState(); pinnaNotchR.resetState()
    }

    override fun onFlush() {
        super.onFlush()
        clearDelayState()
    }

    override fun onReset() {
        super.onReset()
        clearDelayState()
        channelCount = 0
        encoding = C.ENCODING_INVALID
    }

    private class ScalarBiquad {
        private var b0 = 1f
        private var b1 = 0f
        private var b2 = 0f
        private var a1 = 0f
        private var a2 = 0f
        private var z1 = 0f
        private var z2 = 0f

        fun resetState() {
            z1 = 0f
            z2 = 0f
        }

        fun configureLowPass(sampleRate: Float, cutoffHz: Float, q: Float) {
            val w0 = (2.0 * PI * cutoffHz.coerceIn(40f, sampleRate * 0.45f) / sampleRate).toDouble()
            val cosW0 = cos(w0)
            val sinW0 = sin(w0)
            val alpha = sinW0 / (2.0 * q.coerceAtLeast(0.2f))
            val a0 = 1.0 + alpha
            b0 = (((1.0 - cosW0) * 0.5) / a0).toFloat()
            b1 = ((1.0 - cosW0) / a0).toFloat()
            b2 = b0
            a1 = ((-2.0 * cosW0) / a0).toFloat()
            a2 = ((1.0 - alpha) / a0).toFloat()
        }

        fun configurePeaking(sampleRate: Float, centerHz: Float, q: Float, gainDb: Float) {
            if (abs(gainDb) < 0.05f) {
                b0 = 1f; b1 = 0f; b2 = 0f; a1 = 0f; a2 = 0f
                return
            }
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = (2.0 * PI * centerHz.coerceIn(60f, sampleRate * 0.45f) / sampleRate).toDouble()
            val cosW0 = cos(w0)
            val sinW0 = sin(w0)
            val alpha = sinW0 / (2.0 * q.coerceAtLeast(0.2f))
            val a0 = 1.0 + alpha / a
            b0 = ((1.0 + alpha * a) / a0).toFloat()
            b1 = ((-2.0 * cosW0) / a0).toFloat()
            b2 = ((1.0 - alpha * a) / a0).toFloat()
            a1 = ((-2.0 * cosW0) / a0).toFloat()
            a2 = ((1.0 - alpha / a) / a0).toFloat()
        }

        fun process(input: Float): Float {
            val out = b0 * input + z1
            z1 = b1 * input - a1 * out + z2
            z2 = b2 * input - a2 * out
            return if (out.isFinite()) out else input
        }
    }
}
