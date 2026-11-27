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
import com.music.spotui.translator.GameVoiceTranslatorEngine
import com.music.spotui.translator.MultiEngineTranslationRouter
import com.music.spotui.translator.TranslationEnginePreferences
import com.music.spotui.translator.VoiceCaptureSource
import com.music.spotui.util.DevConsoleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    val targetLangCode: String = "en",
    val isTranslating: Boolean = false,
    val isListening: Boolean = false,
    val statusHint: String = "Ready · Auto-detect → EN",
    val updatedAtMs: Long = 0L,
)

internal data class TranslationHistoryEntry(
    val id: Long,
    val originalText: String,
    val translatedEnglish: String,
    val detectedLangCode: String,
    val targetLangCode: String = "en",
    val timestampMs: Long,
)

internal data class GameLangOption(
    val code: String,
    val shortLabel: String,
    val fullName: String,
    val speechLocale: String?,
)

/**
 * Foreground game detector + live speech-to-target teammate translator engine for the
 * Material 3 Expressive Floater, Top-Left Bubble overlay, and Settings Translator Studio.
 */
internal object GameTranslatorEngine {
    private const val TAG = "GameTranslator"
    private const val KEY_BUBBLE_ENABLED = "game_trans_bubble_enabled"
    private const val KEY_BUBBLE_WIDTH_DP = "game_trans_bubble_width_dp"
    private const val KEY_BUBBLE_TEXT_SIZE_SP = "game_trans_bubble_text_sp"
    private const val KEY_BUBBLE_OPACITY = "game_trans_bubble_opacity"
    private const val KEY_SOURCE_LANG = "game_trans_source_lang"
    private const val KEY_TARGET_LANG = "game_trans_target_lang"
    private const val KEY_ALWAYS_UNLOCK_TRANSLATOR = "game_trans_always_unlock"
    private const val KEY_CONTINUOUS_LISTEN = "game_trans_continuous_listen"

    val supportedLanguages: List<GameLangOption> = listOf(
        GameLangOption("auto", "AUTO", "Auto Detect", null),
        GameLangOption("en", "EN", "English", "en-US"),
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

    /** Target languages never include "auto". */
    val supportedTargetLanguages: List<GameLangOption>
        get() = supportedLanguages.filter { it.code != "auto" }

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var speechRecognizer: SpeechRecognizer? = null
    private var lastSubmittedPartial: String = ""
    private var translateJob: Job? = null
    private var forceDefaultLocaleOnce: Boolean = false
    @Volatile
    private var cachedAppContext: Context? = null

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

    private val _micRmsNormalized = MutableStateFlow(0f)
    val micRmsNormalizedFlow: StateFlow<Float> = _micRmsNormalized.asStateFlow()

    private val _speechEngineAvailable = MutableStateFlow(true)
    val speechEngineAvailableFlow: StateFlow<Boolean> = _speechEngineAvailable.asStateFlow()

    private val _speechEngineLabel = MutableStateFlow("Checking…")
    val speechEngineLabelFlow: StateFlow<String> = _speechEngineLabel.asStateFlow()

    private val _downloadedModels = MutableStateFlow<Set<String>>(emptySet())
    val downloadedModelsFlow: StateFlow<Set<String>> = _downloadedModels.asStateFlow()

    private val _modelDownloadBusy = MutableStateFlow<String?>(null)
    val modelDownloadBusyFlow: StateFlow<String?> = _modelDownloadBusy.asStateFlow()

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

    private val _targetLang = MutableStateFlow("en")
    val targetLangFlow: StateFlow<String> = _targetLang.asStateFlow()

    private val _alwaysUnlockTranslator = MutableStateFlow(false)
    val alwaysUnlockTranslatorFlow: StateFlow<Boolean> = _alwaysUnlockTranslator.asStateFlow()

    private val _continuousListen = MutableStateFlow(true)
    val continuousListenFlow: StateFlow<Boolean> = _continuousListen.asStateFlow()

    private val _bubbleState = MutableStateFlow(TranslationBubbleState())
    val bubbleStateFlow: StateFlow<TranslationBubbleState> = _bubbleState.asStateFlow()

    private val _history = MutableStateFlow<List<TranslationHistoryEntry>>(emptyList())
    val historyFlow: StateFlow<List<TranslationHistoryEntry>> = _history.asStateFlow()

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FLOATER_OVERLAY_PREFS_NAME, Context.MODE_PRIVATE)

    fun hydratePreferences(context: Context) {
        cachedAppContext = context.applicationContext
        TranslationEnginePreferences.hydrateIfNeeded(context.applicationContext)
        val p = prefs(context)
        _bubbleEnabled.value = p.getBoolean(KEY_BUBBLE_ENABLED, true)
        _bubbleWidthDp.value = p.getFloat(KEY_BUBBLE_WIDTH_DP, 248f).coerceIn(160f, 420f)
        _bubbleTextSizeSp.value = p.getFloat(KEY_BUBBLE_TEXT_SIZE_SP, 14f).coerceIn(11f, 24f)
        _bubbleOpacity.value = p.getFloat(KEY_BUBBLE_OPACITY, 0.90f).coerceIn(0.55f, 0.98f)
        _sourceLang.value = p.getString(KEY_SOURCE_LANG, "auto").orEmpty()
            .takeIf { lang -> supportedLanguages.any { it.code == lang } } ?: "auto"
        _targetLang.value = p.getString(KEY_TARGET_LANG, "en").orEmpty()
            .takeIf { lang -> supportedTargetLanguages.any { it.code == lang } } ?: "en"
        _alwaysUnlockTranslator.value = p.getBoolean(KEY_ALWAYS_UNLOCK_TRANSLATOR, false)
        _continuousListen.value = p.getBoolean(KEY_CONTINUOUS_LISTEN, true)
        _bubbleState.value = _bubbleState.value.copy(
            targetLangCode = _targetLang.value,
            statusHint = "Ready · ${currentSourceLabel()} → ${currentTargetLabel()}",
        )
        refreshMicPermission(context)
        refreshTranslatorReadiness(context)
    }

    fun refreshMicPermission(context: Context): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            context.applicationContext,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        _hasMicPermission.value = granted
        return granted
    }

    fun refreshTranslatorReadiness(context: Context) {
        val app = context.applicationContext
        val standardAvail = runCatching { SpeechRecognizer.isRecognitionAvailable(app) }.getOrDefault(false)
        val onDeviceAvail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(app) }.getOrDefault(false)
        } else {
            false
        }
        _speechEngineAvailable.value = standardAvail || onDeviceAvail
        _speechEngineLabel.value = when {
            standardAvail && onDeviceAvail -> "Cloud + On-Device Speech Ready"
            standardAvail -> "System Speech Service Ready"
            onDeviceAvail -> "On-Device Speech Ready"
            else -> "No Speech Recognition Service"
        }
        engineScope.launch {
            _downloadedModels.value = TranslationApi.downloadedModelLanguages()
        }
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

    fun setAlwaysUnlockTranslator(context: Context, enabled: Boolean) {
        _alwaysUnlockTranslator.value = enabled
        prefs(context).edit().putBoolean(KEY_ALWAYS_UNLOCK_TRANSLATOR, enabled).apply()
        if (!enabled && !_isGameActive.value && _isListening.value) {
            stopListening()
        }
    }

    fun setContinuousListen(context: Context, continuous: Boolean) {
        _continuousListen.value = continuous
        prefs(context).edit().putBoolean(KEY_CONTINUOUS_LISTEN, continuous).apply()
    }

    fun setSourceLanguage(context: Context, code: String) {
        val normalized = supportedLanguages.firstOrNull { it.code == code }?.code ?: "auto"
        _sourceLang.value = normalized
        forceDefaultLocaleOnce = false
        prefs(context).edit().putString(KEY_SOURCE_LANG, normalized).apply()
        if (_isListening.value) {
            stopListening()
            startListening(context)
        } else {
            _bubbleState.value = _bubbleState.value.copy(
                statusHint = "Ready · ${currentSourceLabel()} → ${currentTargetLabel()}",
            )
        }
    }

    fun setTargetLanguage(context: Context, code: String) {
        val normalized = supportedTargetLanguages.firstOrNull { it.code == code }?.code ?: "en"
        _targetLang.value = normalized
        prefs(context).edit().putString(KEY_TARGET_LANG, normalized).apply()
        _bubbleState.value = _bubbleState.value.copy(
            targetLangCode = normalized,
            statusHint = "Ready · ${currentSourceLabel()} → ${currentTargetLabel()}",
        )
        preloadModelPair(context, _sourceLang.value, normalized)
    }

    fun swapLanguages(context: Context) {
        val currentSrc = _sourceLang.value
        val currentTgt = _targetLang.value
        if (currentSrc == "auto") return
        _sourceLang.value = currentTgt
        _targetLang.value = currentSrc
        prefs(context).edit()
            .putString(KEY_SOURCE_LANG, currentTgt)
            .putString(KEY_TARGET_LANG, currentSrc)
            .apply()
        _bubbleState.value = _bubbleState.value.copy(
            targetLangCode = currentSrc,
            statusHint = "Swapped · ${currentSourceLabel()} → ${currentTargetLabel()}",
        )
        if (_isListening.value) {
            stopListening()
            startListening(context)
        }
    }

    fun downloadLanguagePack(context: Context, langCode: String) {
        val normalized = langCode.lowercase(Locale.US)
        if (normalized == "auto" || _modelDownloadBusy.value != null) return
        _modelDownloadBusy.value = normalized
        engineScope.launch {
            runCatching {
                val updated = TranslationApi.downloadLanguageModel(normalized, allowCellular = true)
                _downloadedModels.value = updated
            }.onSuccess {
                _bubbleState.value = _bubbleState.value.copy(
                    statusHint = "Offline pack ready: ${normalized.uppercase(Locale.US)}",
                )
            }.onFailure { err ->
                DevConsoleManager.log(DevConsoleManager.LogLevel.WARNING, TAG, "Pack download failed ($normalized): ${err.message}")
            }
            _modelDownloadBusy.value = null
            refreshTranslatorReadiness(context)
        }
    }

    fun preloadModelPair(context: Context, sourceLang: String, targetLang: String) {
        if (sourceLang == "auto" || sourceLang == targetLang) {
            if (targetLang != "en") downloadLanguagePack(context, targetLang)
            return
        }
        engineScope.launch {
            _modelDownloadBusy.value = targetLang
            runCatching {
                TranslationApi.preloadLanguagePair(sourceLang, targetLang, allowCellular = true)
                _downloadedModels.value = TranslationApi.downloadedModelLanguages()
            }
            _modelDownloadBusy.value = null
        }
    }

    fun clearHistory() {
        _history.value = emptyList()
        _bubbleState.value = TranslationBubbleState(
            targetLangCode = _targetLang.value,
            isListening = _isListening.value,
            statusHint = "Ready · ${currentSourceLabel()} → ${currentTargetLabel()}",
        )
    }

    /**
     * Inspects all currently open windows provided by [AccessibilityService] (e.g., on
     * service bind or floater reveal) so already-running foreground games are detected immediately.
     */
    fun inspectActiveWindows(context: Context, windows: List<Pair<String?, String?>>) {
        val candidates = windows.filter { (pkg, _) ->
            val clean = pkg?.trim().orEmpty()
            clean.isNotEmpty() && !isTransientSystemUi(context, clean)
        }
        if (candidates.isEmpty()) return

        val detectedGame = candidates.firstOrNull { (pkg, cls) ->
            inspectGamePackage(context, pkg.orEmpty(), cls.orEmpty()).first
        }
        if (detectedGame != null) {
            updateForegroundPackage(context, detectedGame.first, detectedGame.second)
        } else {
            val firstNonSpotUi = candidates.firstOrNull { (pkg, _) -> pkg != context.packageName }
                ?: candidates.first()
            updateForegroundPackage(context, firstNonSpotUi.first, firstNonSpotUi.second)
        }
    }

    /**
     * Evaluates the foreground window's package & activity class from
     * [AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED]. Ignores transient OS chrome
     * (SystemUI, IME keyboards, volume/permission dialogs, OEM game overlay chrome)
     * so switching into a game reliably activates Game Mode and leaving to non-game apps hides it.
     */
    fun updateForegroundPackage(
        context: Context,
        rawPackage: CharSequence?,
        rawClassName: CharSequence? = null,
    ) {
        val pkg = rawPackage?.toString()?.trim().orEmpty()
        if (pkg.isEmpty() || isTransientSystemUi(context, pkg)) return

        val cls = rawClassName?.toString()?.trim().orEmpty()
        val (isGame, appLabel) = inspectGamePackage(context, pkg, cls)
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
                if (isGame) "Game detected: $appLabel ($pkg / $cls) — Translation tab unlocked"
                else "Non-game app foreground ($pkg) — Game Mode exited",
            )
        }

        if (!isGame && !_alwaysUnlockTranslator.value && _isListening.value) {
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
            lower.contains("packageinstaller") ||
            lower.contains("gametools") ||
            lower.contains("gamebooster") ||
            lower.contains("samsung.android.game.gos")
        ) {
            return true
        }
        return false
    }

    private fun inspectGamePackage(
        context: Context,
        pkg: String,
        className: String,
    ): Pair<Boolean, String> {
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
        }?.takeIf { !it.isNullOrBlank() } ?: inferFriendlyAppLabel(pkg)

        if (info != null) {
            if (info.category == ApplicationInfo.CATEGORY_GAME) {
                return true to label
            }
            @Suppress("DEPRECATION")
            if ((info.flags and ApplicationInfo.FLAG_IS_GAME) != 0) {
                return true to label
            }
            // Explicit non-game Android categories (Audio, Video, Social, Maps, Productivity, News, Image)
            if (info.category != ApplicationInfo.CATEGORY_UNDEFINED) {
                return false to label
            }
        }

        val lowerPkg = pkg.lowercase(Locale.US)
        val lowerCls = className.lowercase(Locale.US)

        if (NON_GAME_EXCLUSIONS.any { lowerPkg.startsWith(it) }) {
            return false to label
        }

        val matchesKnownGamePkg = KNOWN_GAME_PACKAGES.any { lowerPkg.startsWith(it) } ||
            GAME_PACKAGE_KEYWORDS.any { lowerPkg.contains(it) }
        val matchesGameEngineClass = GAME_ENGINE_CLASS_HINTS.any { lowerCls.contains(it) }

        return (matchesKnownGamePkg || matchesGameEngineClass) to label
    }

    private fun inferFriendlyAppLabel(pkg: String): String {
        val lower = pkg.lowercase(Locale.US)
        KNOWN_GAME_FRIENDLY_NAMES.entries.firstOrNull { lower.startsWith(it.key) }?.let { return it.value }
        val parts = pkg.split('.').filter { it.isNotBlank() && it !in setOf("com", "org", "net", "io", "gp", "android", "app", "mobile", "games", "game") }
        val raw = (parts.lastOrNull() ?: pkg.substringAfterLast('.')).replace('_', ' ')
        return raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
            .ifBlank { pkg }
    }

    fun updateExternalRms(norm: Float) {
        _micRmsNormalized.value = norm.coerceIn(0f, 1f)
    }

    fun publishExternalTranslation(
        original: String,
        translated: String,
        detectedLang: String,
        targetLang: String,
        statusHint: String,
        recordHistory: Boolean,
    ) {
        val now = System.currentTimeMillis()
        _bubbleState.value = _bubbleState.value.copy(
            originalText = original,
            translatedEnglish = translated,
            detectedLangCode = detectedLang,
            targetLangCode = targetLang,
            isTranslating = false,
            isListening = _isListening.value || GameVoiceTranslatorEngine.isCaptureActive(),
            statusHint = statusHint,
            updatedAtMs = now,
        )
        if (recordHistory && original.isNotBlank()) {
            val entry = TranslationHistoryEntry(
                id = now,
                originalText = original,
                translatedEnglish = translated,
                detectedLangCode = detectedLang,
                targetLangCode = targetLang,
                timestampMs = now,
            )
            _history.value = (listOf(entry) + _history.value)
                .distinctBy { "${it.originalText}|${it.targetLangCode}" }
                .take(20)
        }
    }

    fun startListening(context: Context) {
        val app = context.applicationContext
        cachedAppContext = app
        val config = TranslationEnginePreferences.hydrateIfNeeded(app)

        // Zero-mic-locking Internal Game Audio path when enabled on Android 10+
        if (config.captureSource != VoiceCaptureSource.MICROPHONE_FALLBACK &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        ) {
            GameVoiceTranslatorEngine.requestInternalCapture(app)
            return
        }

        if (!refreshMicPermission(app)) {
            _isListening.value = false
            _micRmsNormalized.value = 0f
            _bubbleState.value = _bubbleState.value.copy(
                isListening = false,
                statusHint = "Microphone permission needed for fallback speech mode",
            )
            return
        }
        mainHandler.post {
            val standardAvail = runCatching { SpeechRecognizer.isRecognitionAvailable(app) }.getOrDefault(false)
            val onDeviceAvail = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(app) }.getOrDefault(false)
            _speechEngineAvailable.value = standardAvail || onDeviceAvail

            if (!standardAvail && !onDeviceAvail) {
                _isListening.value = false
                _micRmsNormalized.value = 0f
                _bubbleState.value = _bubbleState.value.copy(
                    isListening = false,
                    statusHint = "Use Internal Game Audio capture or enable Speech Service",
                )
                return@post
            }
            forceDefaultLocaleOnce = false
            _isListening.value = true
            _bubbleState.value = _bubbleState.value.copy(
                isListening = true,
                targetLangCode = _targetLang.value,
                statusHint = "Listening (${currentSourceLabel()} → ${currentTargetLabel()})…",
            )
            beginRecognitionSessionLocked(app, preferOnDevice = !standardAvail && onDeviceAvail)
        }
    }

    fun stopListening() {
        _isListening.value = false
        _micRmsNormalized.value = 0f
        cachedAppContext?.let { ctx ->
            if (GameVoiceTranslatorEngine.isCaptureActive()) {
                GameVoiceTranslatorEngine.stopCapture(ctx)
            }
        }
        _bubbleState.value = _bubbleState.value.copy(
            isListening = false,
            statusHint = "Capture paused · Ready (${currentSourceLabel()} → ${currentTargetLabel()})",
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

    private fun currentTargetLabel(): String =
        supportedTargetLanguages.firstOrNull { it.code == _targetLang.value }?.shortLabel
            ?: _targetLang.value.uppercase(Locale.US)

    private fun beginRecognitionSessionLocked(app: Context, preferOnDevice: Boolean = false) {
        if (!_isListening.value) return
        runCatching { speechRecognizer?.destroy() }
        val recognizer = if (preferOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(app)
        } else {
            SpeechRecognizer.createSpeechRecognizer(app)
        }
        speechRecognizer = recognizer

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _bubbleState.value = _bubbleState.value.copy(
                    isListening = true,
                    statusHint = "Listening (${currentSourceLabel()} → ${currentTargetLabel()})…",
                )
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                val norm = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                _micRmsNormalized.value = norm
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _micRmsNormalized.value = 0f
            }

            override fun onError(error: Int) {
                _micRmsNormalized.value = 0f
                when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        refreshMicPermission(app)
                        stopListening()
                        return
                    }
                    SpeechRecognizer.ERROR_AUDIO,
                    SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> {
                        _isListening.value = false
                        _bubbleState.value = _bubbleState.value.copy(
                            isListening = false,
                            statusHint = if (error == SpeechRecognizer.ERROR_AUDIO) {
                                "Mic busy (another app holds mic input)"
                            } else {
                                "Speech rate limit · Tap Listen to resume"
                            },
                        )
                        return
                    }
                    12, // ERROR_LANGUAGE_NOT_SUPPORTED
                    13 -> { // ERROR_LANGUAGE_UNAVAILABLE
                        if (!forceDefaultLocaleOnce) {
                            forceDefaultLocaleOnce = true
                            _bubbleState.value = _bubbleState.value.copy(
                                statusHint = "Locale unavailable · switching to Auto mic…",
                            )
                            scheduleRestartIfListening(app, 320L, preferOnDevice = false)
                        } else {
                            _isListening.value = false
                            _bubbleState.value = _bubbleState.value.copy(
                                isListening = false,
                                statusHint = "Language voice model unavailable · type below",
                            )
                        }
                        return
                    }
                }
                val retryMs = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 350L
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 700L
                    else -> 1_100L
                }
                if (_continuousListen.value) {
                    scheduleRestartIfListening(app, retryMs, preferOnDevice)
                } else {
                    stopListening()
                }
            }

            override fun onResults(results: Bundle?) {
                _micRmsNormalized.value = 0f
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val phrase = matches?.firstOrNull()?.trim().orEmpty()
                if (phrase.isNotEmpty()) {
                    lastSubmittedPartial = phrase
                    translateTextNow(phrase, recordHistory = true)
                }
                if (_continuousListen.value) {
                    scheduleRestartIfListening(app, 260L, preferOnDevice)
                } else {
                    _isListening.value = false
                    _bubbleState.value = _bubbleState.value.copy(isListening = false)
                }
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

        val langOption = if (forceDefaultLocaleOnce) null else supportedLanguages.firstOrNull { it.code == _sourceLang.value }
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
            scheduleRestartIfListening(app, 1_200L, preferOnDevice)
        }
    }

    private fun scheduleRestartIfListening(app: Context, delayMs: Long, preferOnDevice: Boolean = false) {
        if (!_isListening.value) return
        mainHandler.removeCallbacksAndMessages(RECOGNIZER_RESTART_TOKEN)
        mainHandler.postAtTime(
            {
                if (_isListening.value) {
                    beginRecognitionSessionLocked(app, preferOnDevice = preferOnDevice)
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
        val targetCode = _targetLang.value
        val targetLabel = currentTargetLabel()

        _bubbleState.value = _bubbleState.value.copy(
            originalText = cleaned,
            targetLangCode = targetCode,
            isTranslating = true,
            statusHint = "Detecting & translating → $targetLabel…",
        )

        translateJob?.cancel()
        translateJob = engineScope.launch {
            val appCtx = cachedAppContext
            if (appCtx != null) {
                runCatching {
                    MultiEngineTranslationRouter.translate(
                        context = appCtx,
                        rawText = cleaned,
                        sourceHint = sourceHint,
                        targetLang = targetCode,
                    )
                }.onSuccess { routed ->
                    val statusText = "${routed.engineUsed.badge} · ${routed.detectedSourceLang.uppercase(Locale.US)} → $targetLabel (${routed.latencyMs}ms)"
                    publishExternalTranslation(
                        original = cleaned,
                        translated = routed.translatedText.ifBlank { cleaned },
                        detectedLang = routed.detectedSourceLang,
                        targetLang = targetCode,
                        statusHint = statusText,
                        recordHistory = recordHistory,
                    )
                    _downloadedModels.value = TranslationApi.downloadedModelLanguages()
                }.onFailure { err ->
                    _bubbleState.value = _bubbleState.value.copy(
                        isTranslating = false,
                        statusHint = "Model downloading or offline: ${err.message ?: "retry"}",
                    )
                }
            } else {
                runCatching {
                    TranslationApi.translatePhrase(cleaned, sourceHint, targetCode)
                }.onSuccess { (detectedCode, translatedText) ->
                    val finalOutput = translatedText.ifBlank { cleaned }
                    val detectedUpper = detectedCode.uppercase(Locale.US)
                    publishExternalTranslation(
                        original = cleaned,
                        translated = finalOutput,
                        detectedLang = detectedCode,
                        targetLang = targetCode,
                        statusHint = "Translated ($detectedUpper → $targetLabel)",
                        recordHistory = recordHistory,
                    )
                    _downloadedModels.value = TranslationApi.downloadedModelLanguages()
                }.onFailure { err ->
                    _bubbleState.value = _bubbleState.value.copy(
                        isTranslating = false,
                        statusHint = "Model downloading or offline: ${err.message ?: "retry"}",
                    )
                }
            }
        }
    }

    fun destroy() {
        stopListening()
        translateJob?.cancel()
        engineScope.cancel()
    }

    private const val RECOGNIZER_RESTART_TOKEN = "SpotuiSpeechRestart"

    private val KNOWN_GAME_PACKAGES = listOf(
        "com.tencent.ig",
        "com.tencent.tmgp",
        "com.pubg.",
        "com.rekoo.pubgm",
        "com.vng.pubgmobile",
        "com.activision.",
        "com.garena.",
        "com.mobile.legends",
        "com.supercell.",
        "com.riotgames.",
        "com.mihoyo.",
        "com.hoyoverse.",
        "com.kurogame.",
        "com.epicgames.",
        "com.roblox.",
        "com.innersloth.",
        "com.ea.gp.",
        "com.ea.game.",
        "com.gameloft.",
        "com.mojang.",
        "com.dts.freefire",
        "com.proximabeta.",
        "com.levelinfinite.",
        "com.kiloo.",
        "com.king.",
        "com.nianticlabs.",
        "com.netmarble.",
        "com.netease.",
        "com.nexon.",
        "com.krafton.",
        "com.bandainamco",
        "com.sega.",
        "com.capcom.",
        "com.square_enix",
        "com.zynga.",
        "com.rovio.",
        "com.halfbrick.",
        "com.miniclip.",
        "com.outfit7.",
        "com.devsisters.",
        "com.lilithgame",
        "com.yostar.",
        "com.bhvr.",
        "com.playrix.",
        "com.fingersoft.",
        "com.nekki.",
        "com.madfingergames.",
        "com.axlebolt.",
        "com.criticalforce",
        "com.firsttouchgames.",
        "com.konami.",
        "com.pearlabyss.",
        "com.gravity.",
        "com.kakaogames.",
        "com.smilegate.",
        "com.com2us.",
        "com.ncsoft.",
        "com.rockstargames.",
        "com.ubisoft.",
        "com.bethsoft.",
        "com.valvesoftware.",
        "com.ngame.",
        "com.plarium.",
        "com.igg.",
        "com.scopely.",
        "com.jamcity.",
        "com.kabam.",
        "com.machinezone.",
        "com.playdemic.",
        "com.tocaboca.",
        "com.habby.",
        "com.tap4fun.",
        "com.lilith.",
        "com.sunborn.",
        "com.hypergryph.",
        "com.papegames.",
        "jp.co.ponos.",
        "com.nvidia.geforcenow",
        "com.microsoft.xcloud",
        "com.brawl",
    )

    private val GAME_PACKAGE_KEYWORDS = listOf(
        ".game.",
        ".games.",
        "pubgm",
        "freefire",
        "callofduty",
        "mobilelegends",
        "brawlstars",
        "clashroyale",
        "clashofclans",
        "genshin",
        "honkai",
        "valorant",
        "minecraft",
        "fortnite",
        "brawl",
        "standoff2",
        "efootball",
        "fc24",
        "fifamobile",
        "apexlegends",
        "arenabreakout",
        "deltaforce",
    )

    private val GAME_ENGINE_CLASS_HINTS = listOf(
        "unity3d.player",
        "unityplayer",
        "gameactivity",
        "sdlactivity",
        "unrealengine",
        "ue4",
        "godotengine",
        "cocos2dx",
        "libgdx",
        "nativeactivity",
        "epicgames",
        "roblox",
        "krafton",
        "pubg",
        "freefire",
    )

    private val NON_GAME_EXCLUSIONS = listOf(
        "com.android.",
        "com.google.android.",
        "com.samsung.android.",
        "com.sec.android.",
        "com.miui.",
        "com.oneplus.",
        "com.oppo.",
        "com.vivo.",
        "com.spotify.",
        "com.whatsapp",
        "com.discord",
        "org.telegram.",
        "com.instagram.",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.",
        "com.netflix.",
        "com.amazon.",
        "com.reddit.",
        "com.twitter.",
        "com.facebook.",
        "com.music.spotui",
    )

    private val KNOWN_GAME_FRIENDLY_NAMES = mapOf(
        "com.tencent.ig" to "PUBG Mobile",
        "com.pubg." to "PUBG Mobile",
        "com.activision.callofduty" to "Call of Duty: Mobile",
        "com.dts.freefire" to "Free Fire",
        "com.mobile.legends" to "Mobile Legends",
        "com.riotgames.league.wildrift" to "Wild Rift",
        "com.riotgames.league.teamfighttactics" to "TFT",
        "com.hoyoverse." to "HoYoverse Game",
        "com.mihoyo.genshinimpact" to "Genshin Impact",
        "com.roblox." to "Roblox",
        "com.epicgames." to "Fortnite / Epic",
        "com.mojang.minecraft" to "Minecraft",
        "com.supercell.brawlstars" to "Brawl Stars",
        "com.supercell.clashroyale" to "Clash Royale",
        "com.supercell.clashofclans" to "Clash of Clans",
        "com.innersloth.spacemafia" to "Among Us",
        "com.axlebolt.standoff2" to "Standoff 2",
    )
}
