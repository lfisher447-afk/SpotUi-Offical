package com.music.spotui.translator

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Process
import androidx.core.content.ContextCompat
import com.music.spotui.accessibility.GameTranslatorEngine
import com.music.spotui.util.DevConsoleManager
import com.music.spotui.util.DevConsoleManager.LogLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel as VoskLogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.sqrt

data class CaptionState(
    val originalText: String = "",
    val translatedText: String = "",
    val detectedLangCode: String = "auto",
    val targetLangCode: String = "en",
    val engineUsed: TranslationEngineBackend = TranslationEngineBackend.HYBRID_CASCADE,
    val latencyMs: Long = 0L,
    val captureActive: Boolean = false,
    val captureModeLabel: String = "Idle",
    val voskReady: Boolean = false,
    val rmsLevel: Float = 0f,
    val captionVisible: Boolean = false,
    val lastSpeechAtMs: Long = 0L,
    val statusMessage: String = "Ready · Internal Game Audio",
    val lastError: String? = null,
)

/**
 * Low-latency, zero-mic-locking Live Game Voice Translator pipeline.
 *
 * Captures internal system/game audio (teammate voice chat in COD Mobile, PUBG, etc.)
 * via [AudioPlaybackCaptureConfiguration] + [MediaProjection] while excluding SpotUI's own UID,
 * transcribes speech locally on [Dispatchers.IO] via Vosk, auto-detects teammate language,
 * routes translation through [MultiEngineTranslationRouter], and hides captions automatically
 * after configurable silence (default 4,000 ms).
 */
object GameVoiceTranslatorEngine {
    private const val TAG = "GameVoiceTranslator"
    private const val SAMPLE_RATE_HZ = 16_000
    private const val PARTIAL_DEBOUNCE_MS = 380L
    private const val TEAMMATE_DUCK_HOLD_MS = 1_500L

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _captionState = MutableStateFlow(CaptionState())
    val captionState: StateFlow<CaptionState> = _captionState.asStateFlow()

    private val _teammateSpeaking = MutableStateFlow(false)
    val teammateSpeaking: StateFlow<Boolean> = _teammateSpeaking.asStateFlow()

    @Volatile
    private var mediaProjection: MediaProjection? = null

    @Volatile
    private var audioRecord: AudioRecord? = null

    @Volatile
    private var voskModel: Model? = null

    @Volatile
    private var voskRecognizer: Recognizer? = null

    private var captureJob: Job? = null
    private var silenceWatchdogJob: Job? = null
    private var translateJob: Job? = null

    @Volatile
    private var lastPartialTranslatedAtMs: Long = 0L

    @Volatile
    private var lastPartialPhrase: String = ""

    @Volatile
    private var lastVoiceEnergyAtMs: Long = 0L

    fun isCaptureActive(): Boolean = _captionState.value.captureActive

    fun requestInternalCapture(context: Context) {
        MediaProjectionPermissionActivity.launch(context.applicationContext)
    }

    fun startWithMediaProjection(
        context: Context,
        resultCode: Int,
        resultData: Intent,
    ) {
        val app = context.applicationContext
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            _captionState.value = _captionState.value.copy(
                captureActive = false,
                lastError = "Internal game audio capture requires Android 10 or newer",
                statusMessage = "Android 10+ required for internal game capture",
            )
            return
        }

        val hasRecordPerm = ContextCompat.checkSelfPermission(
            app,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasRecordPerm) {
            _captionState.value = _captionState.value.copy(
                captureActive = false,
                lastError = "RECORD_AUDIO permission missing",
                statusMessage = "Microphone API permission needed for AudioPlaybackCapture",
            )
            return
        }

        stopCaptureInternal(app, notifyService = false)

        val projectionManager = app.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        val projection = runCatching {
            projectionManager?.getMediaProjection(resultCode, resultData)
        }.onFailure { err ->
            DevConsoleManager.log(LogLevel.ERROR, TAG, "getMediaProjection failed: ${err.message}", err)
            _captionState.value = _captionState.value.copy(
                captureActive = false,
                lastError = err.message ?: "MediaProjection blocked by Android",
                statusMessage = "Could not bind MediaProjection (${err.message ?: "retry"})",
            )
        }.getOrNull() ?: return

        val projectionCallback = object : MediaProjection.Callback() {
            override fun onStop() {
                DevConsoleManager.log(LogLevel.INFO, TAG, "MediaProjection stopped by system")
                stopCapture(app)
            }
        }
        runCatching { projection.registerCallback(projectionCallback, null) }
        mediaProjection = projection

        val config = TranslationEnginePreferences.hydrateIfNeeded(app)
        val targetCode = GameTranslatorEngine.targetLangFlow.value
        val voskDir = TranslatorModelManager.voskModelDir(app)
        val hasVosk = TranslatorModelManager.isVoskModelValid(voskDir)

        _captionState.value = _captionState.value.copy(
            captureActive = true,
            captureModeLabel = if (hasVosk) "Internal Game Audio · Vosk" else "Internal Audio (Model Needed)",
            voskReady = hasVosk,
            targetLangCode = targetCode,
            engineUsed = config.engineBackend,
            lastError = null,
            statusMessage = if (hasVosk) {
                "Capturing internal game audio (Mic free)"
            } else {
                "Internal capture active · Download Vosk speech pack in Settings → Translations"
            },
        )

        ensureSilenceWatchdog(app)

        captureJob = engineScope.launch {
            runInternalCaptureLoop(app, projection)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun runInternalCaptureLoop(
        app: Context,
        projection: MediaProjection,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

        var agcGain = 1.0f
        try {
            val config = TranslationEnginePreferences.hydrateIfNeeded(app)
            val playbackConfigBuilder = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            if (config.excludeOwnMusicUid) {
                playbackConfigBuilder.excludeUid(Process.myUid())
            }
            val playbackConfig = playbackConfigBuilder.build()

            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE_HZ)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()

            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(3200)
            val bufferBytes = (minBuf * 2).coerceAtLeast(4096)

            val record = AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferBytes)
                .setAudioPlaybackCaptureConfig(playbackConfig)
                .build()
            audioRecord = record

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("AudioRecord failed to initialize for AudioPlaybackCapture")
            }

            // Initialize Vosk model if already extracted in filesDir/vosk-model
            val modelDir = TranslatorModelManager.voskModelDir(app)
            if (TranslatorModelManager.isVoskModelValid(modelDir)) {
                runCatching {
                    LibVosk.setLogLevel(VoskLogLevel.WARNINGS)
                    val loaded = Model(modelDir.absolutePath)
                    voskModel = loaded
                    voskRecognizer = Recognizer(loaded, SAMPLE_RATE_HZ.toFloat())
                    _captionState.value = _captionState.value.copy(
                        voskReady = true,
                        captureModeLabel = "Internal Game Audio · Vosk",
                    )
                }.onFailure { err ->
                    DevConsoleManager.log(LogLevel.WARNING, TAG, "Vosk model init failed: ${err.message}", err)
                    _captionState.value = _captionState.value.copy(
                        voskReady = false,
                        lastError = "Vosk init failed: ${err.message}",
                    )
                }
            }

            record.startRecording()
            DevConsoleManager.log(LogLevel.INFO, TAG, "AudioPlaybackCapture active at ${SAMPLE_RATE_HZ}Hz mono")

            val readShorts = ShortArray(bufferBytes / 2)
            val byteBuf = ByteBuffer.allocate(readShorts.size * 2).order(ByteOrder.LITTLE_ENDIAN)

            while (engineScope.isActive && _captionState.value.captureActive) {
                val liveConfig = TranslationEnginePreferences.state.value
                val count = record.read(readShorts, 0, readShorts.size)
                if (count < 0) {
                    if (count == AudioRecord.ERROR_INVALID_OPERATION || count == AudioRecord.ERROR_DEAD_OBJECT) {
                        throw IllegalStateException("AudioRecord stream ended ($count)")
                    }
                    delay(20L)
                    continue
                }
                if (count == 0) {
                    delay(10L)
                    continue
                }

                // Compute normalized RMS of game/voice audio
                var sumSq = 0.0
                for (i in 0 until count) {
                    val s = readShorts[i] / 32768.0
                    sumSq += s * s
                }
                val rms = sqrt(sumSq / count.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f)
                val now = System.currentTimeMillis()
                val aboveGate = rms >= liveConfig.noiseGateLevel

                if (aboveGate) {
                    lastVoiceEnergyAtMs = now
                    if (!_teammateSpeaking.value) _teammateSpeaking.value = true
                } else if (_teammateSpeaking.value && now - lastVoiceEnergyAtMs >= TEAMMATE_DUCK_HOLD_MS) {
                    _teammateSpeaking.value = false
                }

                // Soft AGC gain so quiet teammates come through cleanly without clipping
                if (liveConfig.internalAgcEnabled && aboveGate && rms > 0.004f) {
                    val desiredGain = (0.14f / rms).coerceIn(0.75f, 4.5f)
                    agcGain = agcGain * 0.85f + desiredGain * 0.15f
                } else if (!aboveGate) {
                    agcGain = agcGain * 0.92f + 1.0f * 0.08f
                }

                byteBuf.clear()
                for (i in 0 until count) {
                    val sample = if (!aboveGate) {
                        0
                    } else {
                        (readShorts[i] * agcGain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    }
                    byteBuf.putShort(sample.toShort())
                }
                val pcmBytes = byteBuf.array()
                val pcmByteCount = count * 2

                _captionState.value = _captionState.value.copy(rmsLevel = (rms * 3.5f).coerceIn(0f, 1f))
                GameTranslatorEngine.updateExternalRms((rms * 3.5f).coerceIn(0f, 1f))

                val rec = voskRecognizer
                if (rec != null) {
                    val isFinal = rec.acceptWaveForm(pcmBytes, pcmByteCount)
                    if (isFinal) {
                        val spoken = extractVoskField(rec.result, "text")
                        if (spoken.isNotBlank()) {
                            lastPartialPhrase = ""
                            submitRecognizedPhrase(app, spoken, isFinal = true)
                        }
                    } else {
                        val partial = extractVoskField(rec.partialResult, "partial")
                        if (
                            partial.length >= 4 &&
                            partial != lastPartialPhrase &&
                            now - lastPartialTranslatedAtMs >= PARTIAL_DEBOUNCE_MS
                        ) {
                            lastPartialPhrase = partial
                            lastPartialTranslatedAtMs = now
                            submitRecognizedPhrase(app, partial, isFinal = false)
                        }
                    }
                } else if (TranslatorModelManager.isVoskModelValid(modelDir)) {
                    // Hot-load newly downloaded Vosk model without stopping capture
                    runCatching {
                        val loaded = Model(modelDir.absolutePath)
                        voskModel = loaded
                        voskRecognizer = Recognizer(loaded, SAMPLE_RATE_HZ.toFloat())
                        _captionState.value = _captionState.value.copy(
                            voskReady = true,
                            captureModeLabel = "Internal Game Audio · Vosk",
                            statusMessage = "Vosk offline model loaded · Listening",
                        )
                    }
                }
            }
        } catch (_: CancellationException) {
            // Expected on clean stop
        } catch (t: Throwable) {
            DevConsoleManager.log(LogLevel.ERROR, TAG, "Internal audio capture loop error: ${t.message}", t)
            _captionState.value = _captionState.value.copy(
                captureActive = false,
                lastError = t.message ?: t.javaClass.simpleName,
                statusMessage = "Internal audio stopped: ${t.message ?: "permission/device error"}",
            )
        } finally {
            _teammateSpeaking.value = false
            releaseCaptureResources()
        }
    }

    internal fun extractVoskField(jsonText: String?, key: String): String {
        if (jsonText.isNullOrBlank()) return ""
        return runCatching {
            JSONObject(jsonText).optString(key).trim()
        }.getOrDefault("")
    }

    fun translateManualText(
        context: Context,
        rawText: String,
        sourceHint: String = GameTranslatorEngine.sourceLangFlow.value,
        targetLang: String = GameTranslatorEngine.targetLangFlow.value,
        recordHistory: Boolean = true,
    ) {
        val cleaned = rawText.trim()
        if (cleaned.isEmpty()) return
        val app = context.applicationContext
        ensureSilenceWatchdog(app)
        translateJob?.cancel()
        translateJob = engineScope.launch {
            executeTranslationAndPublish(
                context = app,
                phrase = cleaned,
                sourceHint = sourceHint,
                targetLang = targetLang,
                isFinal = recordHistory,
            )
        }
    }

    private fun submitRecognizedPhrase(
        app: Context,
        phrase: String,
        isFinal: Boolean,
    ) {
        val sourceHint = GameTranslatorEngine.sourceLangFlow.value
        val targetLang = GameTranslatorEngine.targetLangFlow.value
        translateJob?.cancel()
        translateJob = engineScope.launch {
            executeTranslationAndPublish(
                context = app,
                phrase = phrase,
                sourceHint = sourceHint,
                targetLang = targetLang,
                isFinal = isFinal,
            )
        }
    }

    private suspend fun executeTranslationAndPublish(
        context: Context,
        phrase: String,
        sourceHint: String,
        targetLang: String,
        isFinal: Boolean,
    ) {
        val routed = MultiEngineTranslationRouter.translate(
            context = context,
            rawText = phrase,
            sourceHint = sourceHint,
            targetLang = targetLang,
        )
        val now = System.currentTimeMillis()
        _captionState.value = _captionState.value.copy(
            originalText = routed.originalText,
            translatedText = routed.translatedText,
            detectedLangCode = routed.detectedSourceLang,
            targetLangCode = routed.targetLang,
            engineUsed = routed.engineUsed,
            latencyMs = routed.latencyMs,
            captionVisible = true,
            lastSpeechAtMs = now,
            statusMessage = "${routed.engineUsed.badge} · ${routed.detectedSourceLang.uppercase(Locale.US)} → ${routed.targetLang.uppercase(Locale.US)} (${routed.latencyMs}ms)",
            lastError = null,
        )
        GameTranslatorEngine.publishExternalTranslation(
            original = routed.originalText,
            translated = routed.translatedText,
            detectedLang = routed.detectedSourceLang,
            targetLang = routed.targetLang,
            statusHint = "${routed.engineUsed.badge} · ${routed.detectedSourceLang.uppercase(Locale.US)} → ${routed.targetLang.uppercase(Locale.US)}",
            recordHistory = isFinal,
        )
    }

    private fun ensureSilenceWatchdog(context: Context) {
        if (silenceWatchdogJob?.isActive == true) return
        silenceWatchdogJob = engineScope.launch {
            while (isActive) {
                delay(350L)
                val snap = _captionState.value
                val autoHideMs = TranslationEnginePreferences.hydrateIfNeeded(context).autoHideSilenceMs
                if (snap.captionVisible && snap.lastSpeechAtMs > 0L) {
                    val silenceAge = System.currentTimeMillis() - snap.lastSpeechAtMs
                    if (silenceAge >= autoHideMs) {
                        _captionState.value = _captionState.value.copy(captionVisible = false)
                    }
                }
                if (!snap.captureActive && !snap.captionVisible) {
                    break
                }
            }
        }
    }

    fun shouldHideCaptionAfterSilence(
        lastSpeechAtMs: Long,
        nowMs: Long,
        autoHideSilenceMs: Long,
    ): Boolean = lastSpeechAtMs > 0L && (nowMs - lastSpeechAtMs) >= autoHideSilenceMs

    fun stopCapture(context: Context) {
        stopCaptureInternal(context.applicationContext, notifyService = true)
    }

    private fun stopCaptureInternal(app: Context, notifyService: Boolean) {
        captureJob?.cancel()
        captureJob = null
        translateJob?.cancel()
        translateJob = null
        _teammateSpeaking.value = false
        releaseCaptureResources()
        _captionState.value = _captionState.value.copy(
            captureActive = false,
            rmsLevel = 0f,
            captureModeLabel = "Idle",
            statusMessage = "Internal audio capture paused",
        )
        if (notifyService) {
            runCatching {
                com.music.spotui.ui.notification.PlaybackService.demoteMediaProjection(app)
            }
        }
    }

    private fun releaseCaptureResources() {
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null

        runCatching { voskRecognizer?.close() }
        voskRecognizer = null

        runCatching { voskModel?.close() }
        voskModel = null

        runCatching { mediaProjection?.stop() }
        mediaProjection = null
    }
}
