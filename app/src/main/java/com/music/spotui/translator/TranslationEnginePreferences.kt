package com.music.spotui.translator

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class TranslationEngineBackend(
    val id: String,
    val label: String,
    val badge: String,
    val description: String,
    val requiresNetwork: Boolean,
) {
    HYBRID_CASCADE(
        id = "hybrid_cascade",
        label = "Smart Cascade",
        badge = "AUTO",
        description = "Gaming Lexicon (<1ms) -> ML Kit Offline -> Lingva -> LibreTranslate -> Google GTX",
        requiresNetwork = false,
    ),
    MLKIT_OFFLINE(
        id = "mlkit_offline",
        label = "Google ML Kit Offline",
        badge = "OFFLINE",
        description = "100% on-device neural translation with automatic language detection",
        requiresNetwork = false,
    ),
    GAMING_LEXICON(
        id = "gaming_lexicon",
        label = "Tactical Lexicon",
        badge = "<1MS",
        description = "Zero-latency FPS/MOBA squad callout dictionary with ML Kit fallback",
        requiresNetwork = false,
    ),
    LINGVA_MIRROR(
        id = "lingva_mirror",
        label = "Lingva Translate",
        badge = "HTTP",
        description = "Anonymous Google-quality translation via configurable Lingva instance",
        requiresNetwork = true,
    ),
    LIBRE_TRANSLATE(
        id = "libre_translate",
        label = "LibreTranslate",
        badge = "FOSS",
        description = "Open-source self-hosted or public LibreTranslate API mirror",
        requiresNetwork = true,
    ),
    MYMEMORY_API(
        id = "mymemory_api",
        label = "MyMemory TM",
        badge = "MEM",
        description = "Collaborative translation memory API with phrase fuzzy matching",
        requiresNetwork = true,
    ),
    GOOGLE_GTX(
        id = "google_gtx",
        label = "Google GTX Fast",
        badge = "FAST",
        description = "Low-latency streaming web translator endpoint",
        requiresNetwork = true,
    );

    companion object {
        fun fromId(id: String?): TranslationEngineBackend =
            entries.firstOrNull { it.id == id } ?: HYBRID_CASCADE
    }
}

enum class VoiceCaptureSource(
    val id: String,
    val label: String,
    val subtitle: String,
) {
    INTERNAL_GAME_AUDIO(
        id = "internal_game_audio",
        label = "Internal Game Audio",
        subtitle = "Captures team voice chat via MediaProjection (never locks your mic)",
    ),
    AUTO_SMART(
        id = "auto_smart",
        label = "Auto Smart Capture",
        subtitle = "Prefers internal game audio when permitted, falls back to speech recognizer",
    ),
    MICROPHONE_FALLBACK(
        id = "microphone_fallback",
        label = "Mic Speech Fallback",
        subtitle = "Uses Android SpeechRecognizer when game audio projection is off",
    );

    companion object {
        fun fromId(id: String?): VoiceCaptureSource =
            entries.firstOrNull { it.id == id } ?: INTERNAL_GAME_AUDIO
    }
}

data class TranslationConfigState(
    val engineBackend: TranslationEngineBackend = TranslationEngineBackend.HYBRID_CASCADE,
    val captureSource: VoiceCaptureSource = VoiceCaptureSource.INTERNAL_GAME_AUDIO,
    val voskModelKey: String = "en-us-small",
    val customVoskUrl: String = "",
    val lingvaMirrorUrl: String = DEFAULT_LINGVA_MIRROR,
    val libreTranslateUrl: String = DEFAULT_LIBRE_TRANSLATE_URL,
    val libreApiKey: String = "",
    val autoDetectThreshold: Float = 0.35f,
    val gamingLexiconEnabled: Boolean = true,
    val profanityMaskEnabled: Boolean = false,
    val autoHideSilenceMs: Long = 4_000L,
    val clickThroughLocked: Boolean = true,
    val showDualLineCaption: Boolean = true,
    val internalAgcEnabled: Boolean = true,
    val noiseGateLevel: Float = 0.014f,
    val excludeOwnMusicUid: Boolean = true,
    val teammateVoiceDuckingPct: Int = 20,
) {
    companion object {
        const val DEFAULT_LINGVA_MIRROR = "https://lingva.ml"
        const val DEFAULT_LIBRE_TRANSLATE_URL = "https://libretranslate.de/translate"
    }
}

object TranslationEnginePreferences {
    private const val PREF_NAME = "spotui_translation_engine_prefs"

    private const val KEY_ENGINE_BACKEND = "engine_backend"
    private const val KEY_CAPTURE_SOURCE = "capture_source"
    private const val KEY_VOSK_MODEL_KEY = "vosk_model_key"
    private const val KEY_CUSTOM_VOSK_URL = "custom_vosk_url"
    private const val KEY_LINGVA_MIRROR_URL = "lingva_mirror_url"
    private const val KEY_LIBRE_URL = "libre_translate_url"
    private const val KEY_LIBRE_KEY = "libre_api_key"
    private const val KEY_AUTO_DETECT_THRESHOLD = "auto_detect_threshold"
    private const val KEY_GAMING_LEXICON = "gaming_lexicon_enabled"
    private const val KEY_PROFANITY_MASK = "profanity_mask_enabled"
    private const val KEY_AUTO_HIDE_SILENCE_MS = "auto_hide_silence_ms"
    private const val KEY_CLICK_THROUGH_LOCKED = "click_through_locked"
    private const val KEY_DUAL_LINE_CAPTION = "dual_line_caption"
    private const val KEY_INTERNAL_AGC = "internal_agc_enabled"
    private const val KEY_NOISE_GATE = "noise_gate_level"
    private const val KEY_EXCLUDE_OWN_UID = "exclude_own_music_uid"
    private const val KEY_VOICE_DUCKING_PCT = "teammate_voice_ducking_pct"

    private val _state = MutableStateFlow(TranslationConfigState())
    val state: StateFlow<TranslationConfigState> = _state.asStateFlow()

    @Volatile
    private var hydrated = false

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun hydrateIfNeeded(context: Context): TranslationConfigState {
        if (hydrated) return _state.value
        synchronized(this) {
            if (hydrated) return _state.value
            val loaded = load(context)
            _state.value = loaded
            hydrated = true
            return loaded
        }
    }

    fun load(context: Context): TranslationConfigState {
        val p = prefs(context)
        return TranslationConfigState(
            engineBackend = TranslationEngineBackend.fromId(p.getString(KEY_ENGINE_BACKEND, null)),
            captureSource = VoiceCaptureSource.fromId(p.getString(KEY_CAPTURE_SOURCE, null)),
            voskModelKey = p.getString(KEY_VOSK_MODEL_KEY, "en-us-small") ?: "en-us-small",
            customVoskUrl = p.getString(KEY_CUSTOM_VOSK_URL, "").orEmpty(),
            lingvaMirrorUrl = p.getString(KEY_LINGVA_MIRROR_URL, TranslationConfigState.DEFAULT_LINGVA_MIRROR)
                ?.ifBlank { TranslationConfigState.DEFAULT_LINGVA_MIRROR }
                ?: TranslationConfigState.DEFAULT_LINGVA_MIRROR,
            libreTranslateUrl = p.getString(KEY_LIBRE_URL, TranslationConfigState.DEFAULT_LIBRE_TRANSLATE_URL)
                ?.ifBlank { TranslationConfigState.DEFAULT_LIBRE_TRANSLATE_URL }
                ?: TranslationConfigState.DEFAULT_LIBRE_TRANSLATE_URL,
            libreApiKey = p.getString(KEY_LIBRE_KEY, "").orEmpty(),
            autoDetectThreshold = p.getFloat(KEY_AUTO_DETECT_THRESHOLD, 0.35f).coerceIn(0.10f, 0.85f),
            gamingLexiconEnabled = p.getBoolean(KEY_GAMING_LEXICON, true),
            profanityMaskEnabled = p.getBoolean(KEY_PROFANITY_MASK, false),
            autoHideSilenceMs = p.getLong(KEY_AUTO_HIDE_SILENCE_MS, 4_000L).coerceIn(1_500L, 15_000L),
            clickThroughLocked = p.getBoolean(KEY_CLICK_THROUGH_LOCKED, true),
            showDualLineCaption = p.getBoolean(KEY_DUAL_LINE_CAPTION, true),
            internalAgcEnabled = p.getBoolean(KEY_INTERNAL_AGC, true),
            noiseGateLevel = p.getFloat(KEY_NOISE_GATE, 0.014f).coerceIn(0.002f, 0.12f),
            excludeOwnMusicUid = p.getBoolean(KEY_EXCLUDE_OWN_UID, true),
            teammateVoiceDuckingPct = p.getInt(KEY_VOICE_DUCKING_PCT, 20).coerceIn(0, 75),
        )
    }

    private inline fun update(context: Context, transform: (TranslationConfigState) -> TranslationConfigState) {
        val next = transform(hydrateIfNeeded(context))
        _state.update { next }
        hydrated = true
        prefs(context).edit()
            .putString(KEY_ENGINE_BACKEND, next.engineBackend.id)
            .putString(KEY_CAPTURE_SOURCE, next.captureSource.id)
            .putString(KEY_VOSK_MODEL_KEY, next.voskModelKey)
            .putString(KEY_CUSTOM_VOSK_URL, next.customVoskUrl)
            .putString(KEY_LINGVA_MIRROR_URL, next.lingvaMirrorUrl)
            .putString(KEY_LIBRE_URL, next.libreTranslateUrl)
            .putString(KEY_LIBRE_KEY, next.libreApiKey)
            .putFloat(KEY_AUTO_DETECT_THRESHOLD, next.autoDetectThreshold)
            .putBoolean(KEY_GAMING_LEXICON, next.gamingLexiconEnabled)
            .putBoolean(KEY_PROFANITY_MASK, next.profanityMaskEnabled)
            .putLong(KEY_AUTO_HIDE_SILENCE_MS, next.autoHideSilenceMs)
            .putBoolean(KEY_CLICK_THROUGH_LOCKED, next.clickThroughLocked)
            .putBoolean(KEY_DUAL_LINE_CAPTION, next.showDualLineCaption)
            .putBoolean(KEY_INTERNAL_AGC, next.internalAgcEnabled)
            .putFloat(KEY_NOISE_GATE, next.noiseGateLevel)
            .putBoolean(KEY_EXCLUDE_OWN_UID, next.excludeOwnMusicUid)
            .putInt(KEY_VOICE_DUCKING_PCT, next.teammateVoiceDuckingPct)
            .apply()
    }

    fun setEngineBackend(context: Context, backend: TranslationEngineBackend) =
        update(context) { it.copy(engineBackend = backend) }

    fun setCaptureSource(context: Context, source: VoiceCaptureSource) =
        update(context) { it.copy(captureSource = source) }

    fun setVoskModelKey(context: Context, key: String) =
        update(context) { it.copy(voskModelKey = key) }

    fun setCustomVoskUrl(context: Context, url: String) =
        update(context) { it.copy(customVoskUrl = url.trim()) }

    fun setLingvaMirrorUrl(context: Context, url: String) =
        update(context) { it.copy(lingvaMirrorUrl = url.trim().ifBlank { TranslationConfigState.DEFAULT_LINGVA_MIRROR }) }

    fun setLibreTranslateConfig(context: Context, url: String, apiKey: String) =
        update(context) {
            it.copy(
                libreTranslateUrl = url.trim().ifBlank { TranslationConfigState.DEFAULT_LIBRE_TRANSLATE_URL },
                libreApiKey = apiKey.trim(),
            )
        }

    fun setAutoDetectThreshold(context: Context, threshold: Float) =
        update(context) { it.copy(autoDetectThreshold = threshold.coerceIn(0.10f, 0.85f)) }

    fun setGamingLexiconEnabled(context: Context, enabled: Boolean) =
        update(context) { it.copy(gamingLexiconEnabled = enabled) }

    fun setProfanityMaskEnabled(context: Context, enabled: Boolean) =
        update(context) { it.copy(profanityMaskEnabled = enabled) }

    fun setAutoHideSilenceMs(context: Context, ms: Long) =
        update(context) { it.copy(autoHideSilenceMs = ms.coerceIn(1_500L, 15_000L)) }

    fun setClickThroughLocked(context: Context, locked: Boolean) =
        update(context) { it.copy(clickThroughLocked = locked) }

    fun setShowDualLineCaption(context: Context, show: Boolean) =
        update(context) { it.copy(showDualLineCaption = show) }

    fun setInternalAgcEnabled(context: Context, enabled: Boolean) =
        update(context) { it.copy(internalAgcEnabled = enabled) }

    fun setNoiseGateLevel(context: Context, level: Float) =
        update(context) { it.copy(noiseGateLevel = level.coerceIn(0.002f, 0.12f)) }

    fun setExcludeOwnMusicUid(context: Context, exclude: Boolean) =
        update(context) { it.copy(excludeOwnMusicUid = exclude) }

    fun setTeammateVoiceDuckingPct(context: Context, pct: Int) =
        update(context) { it.copy(teammateVoiceDuckingPct = pct.coerceIn(0, 75)) }
}
