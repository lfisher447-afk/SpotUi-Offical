package com.music.spotui.accessibility

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.music.spotui.data.api.TranslationApi
import com.music.spotui.data.preferences.FLOATER_OVERLAY_PREFS_NAME
import com.music.spotui.util.DevConsoleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

internal data class TranslationBubbleState(
    val originalText: String = "",
    val translatedEnglish: String = "",
    val detectedLangCode: String = "auto",
    val isTranslating: Boolean = false,
    val isListening: Boolean = false,
    val statusHint: String = "Ready · Auto-detect → English",
    val updatedAtMs: Long = 0L,
)

internal data class TranslationHistoryEntry(
    val id: Long,
    val originalText: String,
    val translatedEnglish: String,
    val detectedLangCode: String,
    val timestampMs: Long,
)

internal data class GameLangOption(
    val code: String,
    val shortLabel: String,
    val fullName: String,
    val speechLocale: String?,
)

/**
 * Foreground game detector + live speech-to-English teammate translator engine for the
 * Material 3 Expressive Floater & Top-Left Bubble overlay.
 */
internal object GameTranslatorEngine {
    private const val TAG = "GameTranslator"
    private const val KEY_BUBBLE_ENABLED = "game_trans_bubble_enabled"
    private const val KEY_BUBBLE_WIDTH_DP = "game_trans_bubble_width_dp"
    private const val KEY_BUBBLE_TEXT_SIZE_SP = "game_trans_bubble_text_sp"
    private const val KEY_BUBBLE_OPACITY = "game_trans_bubble_opacity"
    private const val KEY_SOURCE_LANG = "game_trans_source_lang"

    val supportedLanguages: List<GameLangOption> = listOf(
        GameLangOption("auto", "AUTO", "Auto Detect", null),
        GameLangOption("es", "ES", "Spanish", "es-ES"),
        GameLangOption("ar", "AR", "Arabic", "ar-SA"),
        GameLangOption("fr", "FR", "French", "fr-FR"),
        GameLangOption("pt", "PT", "Portuguese", "pt-BR"),
        GameLangOption("ru", "RU", "Russian", "ru-RU"),
        GameLangOption("de", "DE", "German", "de-DE"),
        GameLangOption("ja", "JA", "Japanese", "ja-JP"),
        GameLangOption("ko", "KO", "Korean", "ko-KR"),
        GameLangOption("zh", "ZH", "Chinese", "zh-CN"),
        GameLangOption("hi", "HI", "Hindi", "hi-IN"),
        GameLangOption("tr", "TR", "Turkish", "tr-TR"),
    )

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var speechRecognizer: SpeechRecognizer? = null
    private var lastSubmittedPartial: String = ""

    private val _isGameActive = MutableStateFlow(false)
    val isGameActiveFlow: StateFlow<Boolean> = _isGameActive.asStateFlow()

    private val _activeGamePackage = MutableStateFlow<String?>(null)
    val activeGamePackageFlow: StateFlow<String?> = _activeGamePackage.asStateFlow()

    private val _activeGameLabel = MutableStateFlow<String?>(null)
    val activeGameLabelFlow: StateFlow<String?> = _activeGameLabel.asStateFlow()

    private val _hasMicPermission = MutableStateFlow(false)
    val hasMicPermissionFlow: StateFlow<Boolean> = _hasMicPermission.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListeningFlow: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _bubbleEnabled = MutableStateFlow(true)
    val bubbleEnabledFlow: StateFlow<Boolean> = _bubbleEnabled.asStateFlow()

    private val _bubbleWidthDp = MutableStateFlow(248f)
    val bubbleWidthDpFlow: StateFlow<Float> = _bubbleWidthDp.asStateFlow()

    private val _bubbleTextSizeSp = MutableStateFlow(14f)
    val bubbleTextSizeSpFlow: StateFlow<Float> = _bubbleTextSizeSp.asStateFlow()

    private val _bubbleOpacity = MutableStateFlow(0.90f)
    val bubbleOpacityFlow: StateFlow<Float> = _bubbleOpacity.asStateFlow()

    private val _sourceLang = MutableStateFlow("auto")
    val sourceLangFlow: StateFlow<String> = _sourceLang.asStateFlow()

    private val _bubbleState = MutableStateFlow(TranslationBubbleState())
    val bubbleStateFlow: StateFlow<TranslationBubbleState> = _bubbleState.asStateFlow()

    private val _history = MutableStateFlow<List<TranslationHistoryEntry>>(emptyList())
    val historyFlow: StateFlow<List<TranslationHistoryEntry>> = _history.asStateFlow()

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FLOATER_OVERLAY_PREFS_NAME, Context.MODE_PRIVATE)

    fun hydratePreferences(context: Context) {
        val p = prefs(context)
        _bubbleEnabled.value = p.getBoolean(KEY_BUBBLE_ENABLED, true)
        _bubbleWidthDp.value = p.getFloat(KEY_BUBBLE_WIDTH_DP, 248f).coerceIn(160f, 420f)
        _bubbleTextSizeSp.value = p.getFloat(KEY_BUBBLE_TEXT_SIZE_SP, 14f).coerceIn(11f, 24f)
        _bubbleOpacity.value = p.getFloat(KEY_BUBBLE_OPACITY, 0.90f).coerceIn(0.55f, 0.98f)
        _sourceLang.value = p.getString(KEY_SOURCE_LANG, "auto").orEmpty().ifBlank { "auto" }
        refreshMicPermission(context)
    }

    fun refreshMicPermission(context: Context): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            context.applicationContext,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        _hasMicPermission.value = granted
        return granted
    }

    fun setBubbleEnabled(context: Context, enabled: Boolean) {
        _bubbleEnabled.value = enabled
        prefs(context).edit().putBoolean(KEY_BUBBLE_ENABLED, enabled).apply()
    }

    fun setBubbleWidthDp(context: Context, widthDp: Float) {
        val clamped = widthDp.coerceIn(160f, 420f)
        _bubbleWidthDp.value = clamped
        prefs(context).edit().putFloat(KEY_BUBBLE_WIDTH_DP, clamped).apply()
    }

    fun setBubbleTextSizeSp(context: Context, sizeSp: Float) {
        val clamped = sizeSp.coerceIn(11f, 24f)
        _bubbleTextSizeSp.value = clamped
        prefs(context).edit().putFloat(KEY_BUBBLE_TEXT_SIZE_SP, clamped).apply()
    }

    fun setBubbleOpacity(context: Context, opacity: Float) {
        val clamped = opacity.coerceIn(0.55f, 0.98f)
        _bubbleOpacity.value = clamped
        prefs(context).edit().putFloat(KEY_BUBBLE_OPACITY, clamped).apply()
    }

    fun setSourceLanguage(context: Context, code: String) {
        val normalized = supportedLanguages.firstOrNull { it.code == code }?.code ?: "auto"
        _sourceLang.value = normalized
        prefs(context).edit().putString(KEY_SOURCE_LANG, normalized).apply()
        if (_isListening.value) {
            stopListening()
            startListening(context)
        } else {
            val label = supportedLanguages.firstOrNull { it.code == normalized }?.fullName ?: "Auto Detect"
            _bubbleState.value = _bubbleState.value.copy(
                statusHint = "$label → English",
            )
        }
    }

    fun clearHistory() {
        _history.value = emptyList()
        _bubbleState.value = TranslationBubbleState(
            isListening = _isListening.value,
            statusHint = "Ready · Auto-detect → English",
        )
    }

    /**
     * Evaluates the foreground window's package from [AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED].
     * Ignores transient OS chrome (SystemUI, IME keyboards, SpotUI itself, launchers, volume dialogs)
     * so switching into a game reliably activates Game Mode and leaving to non-game apps hides it.
     */
    fun updateForegroundPackage(context: Context, rawPackage: CharSequence?) {
        val pkg = rawPackage?.toString()?.trim().orEmpty()
        if (pkg.isEmpty() || isTransientSystemUi(context, pkg)) return

        val (isGame, appLabel) = inspectGamePackage(context, pkg)
        val prevPackage = _activeGamePackage.value
        val prevIsGame = _isGameActive.value

        if (prevPackage == pkg && prevIsGame == isGame) return

        _activeGamePackage.value = pkg
        _activeGameLabel.value = appLabel
        _isGameActive.value = isGame

        if (prevIsGame != isGame) {
            DevConsoleManager.log(
                DevConsoleManager.LogLevel.INFO,
                TAG,
                if (isGame) "Game detected: $appLabel ($pkg) — Translation tab unlocked"
                else "Non-game app foreground ($pkg) — Translation tab hidden",
            )
        }

        if (!isGame && _isListening.value) {
            stopListening()
        }
    }

    private fun isTransientSystemUi(context: Context, pkg: String): Boolean {
        if (pkg == context.packageName) return false // Leaving to SpotUI itself exits game mode
        val lower = pkg.lowercase(Locale.US)
        if (lower == "com.android.systemui" ||
            lower == "android" ||
            lower.contains("inputmethod") ||
            lower.contains("keyboard") ||
            lower.contains("gboard") ||
            lower.contains("honeyboard") ||
            lower.contains("swiftkey") ||
            lower.contains("permissioncontroller") ||
            lower.contains("packageinstaller")
        ) {
            return true
        }
        return false
    }

    private fun inspectGamePackage(context: Context, pkg: String): Pair<Boolean, String> {
        val pm = context.packageManager
        val info = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(pkg, 0)
            }
        }.getOrNull()

        val label = info?.let {
            runCatching { pm.getApplicationLabel(it)?.toString() }.getOrNull()
        }?.takeIf { !it.isNullOrBlank() } ?: pkg

        if (info == null) return false to label

        if (info.category == ApplicationInfo.CATEGORY_GAME) {
            return true to label
        }

        @Suppress("DEPRECATION")
        if ((info.flags and ApplicationInfo.FLAG_IS_GAME) != 0) {
            return true to label
        }

        val lower = pkg.lowercase(Locale.US)
        val knownGamePrefix = KNOWN_GAME_PACKAGES.any { lower.startsWith(it) }
        return (knownGamePrefix && info.category == ApplicationInfo.CATEGORY_UNDEFINED) to label
    }

    fun startListening(context: Context) {
        val app = context.applicationContext
        if (!refreshMicPermission(app)) {
            _isListening.value = false
            _bubbleState.value = _bubbleState.value.copy(
                isListening = false,
                statusHint = "Microphone permission needed for live teammate voice translation",
            )
            return
        }
        mainHandler.post {
            if (!SpeechRecognizer.isRecognitionAvailable(app)) {
                _isListening.value = false
                _bubbleState.value = _bubbleState.value.copy(
                    isListening = false,
                    statusHint = "Speech recognition service unavailable on device",
                )
                return@post
            }
            _isListening.value = true
            _bubbleState.value = _bubbleState.value.copy(
                isListening = true,
                statusHint = "Listening for teammate (${currentSourceLabel()}) → EN…",
            )
            beginRecognitionSessionLocked(app)
        }
    }

    fun stopListening() {
        _isListening.value = false
        _bubbleState.value = _bubbleState.value.copy(
            isListening = false,
            statusHint = "Mic paused · Ready (${currentSourceLabel()} → EN)",
        )
        mainHandler.post {
            mainHandler.removeCallbacksAndMessages(RECOGNIZER_RESTART_TOKEN)
            runCatching { speechRecognizer?.stopListening() }
            runCatching { speechRecognizer?.destroy() }
            speechRecognizer = null
        }
    }

    private fun currentSourceLabel(): String =
        supportedLanguages.firstOrNull { it.code == _sourceLang.value }?.shortLabel ?: "AUTO"

    private fun beginRecognitionSessionLocked(app: Context) {
        if (!_isListening.value) return
        runCatching { speechRecognizer?.destroy() }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(app)
        speechRecognizer = recognizer

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _bubbleState.value = _bubbleState.value.copy(
                    isListening = true,
                    statusHint = "Listening (${currentSourceLabel()} → EN)…",
                )
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                val retryMs = when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        refreshMicPermission(app)
                        stopListening()
                        return
                    }
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 350L
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 650L
                    else -> 1_100L
                }
                scheduleRestartIfListening(app, retryMs)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val phrase = matches?.firstOrNull()?.trim().orEmpty()
                if (phrase.isNotEmpty()) {
                    lastSubmittedPartial = phrase
                    translateTextNow(phrase, recordHistory = true)
                }
                scheduleRestartIfListening(app, 260L)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = matches?.firstOrNull()?.trim().orEmpty()
                if (partial.length >= 4 && partial != lastSubmittedPartial) {
                    lastSubmittedPartial = partial
                    translateTextNow(partial, recordHistory = false)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val langOption = supportedLanguages.firstOrNull { it.code == _sourceLang.value }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            langOption?.speechLocale?.let { bcp47 ->
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, bcp47)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, bcp47)
            }
        }
        runCatching { recognizer.startListening(intent) }.onFailure {
            scheduleRestartIfListening(app, 1_200L)
        }
    }

    private fun scheduleRestartIfListening(app: Context, delayMs: Long) {
        if (!_isListening.value) return
        mainHandler.removeCallbacksAndMessages(RECOGNIZER_RESTART_TOKEN)
        mainHandler.postAtTime(
            {
                if (_isListening.value) {
                    beginRecognitionSessionLocked(app)
                }
            },
            RECOGNIZER_RESTART_TOKEN,
            android.os.SystemClock.uptimeMillis() + delayMs,
        )
    }

    fun translateTextNow(phrase: String, recordHistory: Boolean = true) {
        val cleaned = phrase.trim()
        if (cleaned.isEmpty()) return
        val sourceHint = _sourceLang.value

        _bubbleState.value = _bubbleState.value.copy(
            originalText = cleaned,
            isTranslating = true,
            statusHint = "Detecting & translating…",
        )

        engineScope.launch {
            runCatching {
                TranslationApi.translatePhraseToEnglish(cleaned, sourceHint)
            }.onSuccess { (detectedCode, englishText) ->
                val finalEnglish = englishText.ifBlank { cleaned }
                val now = System.currentTimeMillis()
                _bubbleState.value = _bubbleState.value.copy(
                    originalText = cleaned,
                    translatedEnglish = finalEnglish,
                    detectedLangCode = detectedCode,
                    isTranslating = false,
                    statusHint = if (_isListening.value) "Listening (${detectedCode.uppercase(Locale.US)} → EN)" else "Translated (${detectedCode.uppercase(Locale.US)} → EN)",
                    updatedAtMs = now,
                )
                if (recordHistory) {
                    val entry = TranslationHistoryEntry(
                        id = now,
                        originalText = cleaned,
                        translatedEnglish = finalEnglish,
                        detectedLangCode = detectedCode,
                        timestampMs = now,
                    )
                    val updated = (listOf(entry) + _history.value)
                        .distinctBy { it.originalText }
                        .take(15)
                    _history.value = updated
                }
            }.onFailure { err ->
                _bubbleState.value = _bubbleState.value.copy(
                    isTranslating = false,
                    statusHint = "Model downloading or offline: ${err.message ?: "retry"}",
                )
            }
        }
    }

    fun destroy() {
        stopListening()
        engineScope.cancel()
    }

    private const val RECOGNIZER_RESTART_TOKEN = "SpotuiSpeechRestart"

    private val KNOWN_GAME_PACKAGES = listOf(
        "com.tencent.ig",
        "com.pubg.",
        "com.activision.callofduty",
        "com.garena.",
        "com.mobile.legends",
        "com.supercell.",
        "com.riotgames.",
        "com.mihoyo.",
        "com.hoyoverse.",
        "com.epicgames.",
        "com.roblox.",
        "com.innersloth.",
        "com.ea.gp.",
        "com.gameloft.",
        "com.mojang.",
        "com.dts.freefire",
        "com.proximabeta.",
        "com.levelinfinite.",
        "com.kiloo.",
        "com.king.",
        "com.nianticlabs.",
    )
}
