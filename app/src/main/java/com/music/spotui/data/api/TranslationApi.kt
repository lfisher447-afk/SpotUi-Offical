package com.music.spotui.data.api

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.music.spotui.data.entity.LyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object TranslationApi {
    private const val TAG = "TranslationApi"

    /** In-memory cache: "sourceText|sourceLang|targetLang" → translatedText */
    private val cache = ConcurrentHashMap<String, String>()

    /** Reusable ML Kit translator clients keyed by "sourceLang|targetLang". */
    private val translatorPool = ConcurrentHashMap<String, Translator>()
    private val modelMutex = Mutex()

    /** Languages the user can pick from, shown in the UI picker (code → display name). */
    val languages: List<Pair<String, String>> = listOf(
        "af" to "Afrikaans",
        "ar" to "العربية",
        "be" to "Беларуская",
        "bg" to "Български",
        "bn" to "বাংলা",
        "ca" to "Català",
        "cs" to "Čeština",
        "cy" to "Cymraeg",
        "da" to "Dansk",
        "de" to "Deutsch",
        "el" to "Ελληνικά",
        "en" to "English",
        "es" to "Español",
        "et" to "Eesti",
        "fa" to "فارسی",
        "fi" to "Suomi",
        "fr" to "Français",
        "ga" to "Gaeilge",
        "gl" to "Galego",
        "gu" to "ગુજરાતી",
        "he" to "עברית",
        "hi" to "हिन्दी",
        "hr" to "Hrvatski",
        "hu" to "Magyar",
        "id" to "Indonesia",
        "is" to "Íslenska",
        "it" to "Italiano",
        "ja" to "日本語",
        "ka" to "ქართული",
        "kn" to "ಕನ್ನಡ",
        "ko" to "한국어",
        "lt" to "Lietuvių",
        "lv" to "Latviešu",
        "mk" to "Македонски",
        "ml" to "മലയാളം",
        "mr" to "मराठी",
        "ms" to "Melayu",
        "nb" to "Norsk bokmål",
        "nl" to "Nederlands",
        "pl" to "Polski",
        "pt" to "Português",
        "ro" to "Română",
        "ru" to "Русский",
        "sk" to "Slovenčina",
        "sl" to "Slovenščina",
        "sq" to "Shqip",
        "sv" to "Svenska",
        "sw" to "Kiswahili",
        "ta" to "தமிழ்",
        "te" to "తెలుగు",
        "th" to "ไทย",
        "tl" to "Tagalog",
        "tr" to "Türkçe",
        "uk" to "Українська",
        "ur" to "اردو",
        "vi" to "Tiếng Việt",
        "zh" to "中文",
    )

    fun displayName(code: String): String =
        languages.firstOrNull { it.first == code }?.second ?: code.uppercase(Locale.US)

    /**
     * Unicode script heuristic for ultra-short teammate callouts (e.g., "выходи", "يمين", "助けて")
     * where statistical ML Kit language-id sometimes returns "und".
     */
    fun inferScriptLanguageFallback(text: String): String? {
        for (ch in text) {
            when (ch.code) {
                in 0x0600..0x06FF -> return "ar" // Arabic
                in 0x0400..0x04FF -> return "ru" // Cyrillic
                in 0x3040..0x30FF -> return "ja" // Hiragana / Katakana
                in 0xAC00..0xD7AF -> return "ko" // Hangul
                in 0x4E00..0x9FFF -> return "zh" // CJK Ideographs
                in 0x0900..0x097F -> return "hi" // Devanagari
                in 0x0E00..0x0E7F -> return "th" // Thai
                in 0x0370..0x03FF -> return "el" // Greek
                in 0x0590..0x05FF -> return "he" // Hebrew
            }
        }
        return null
    }

    /**
     * Detect the language of [text] using ML Kit's on-device Language Identification API,
     * falling back to script detection on short bursts.
     */
    suspend fun detectLanguage(text: String, confidenceThreshold: Float = 0.38f): String {
        if (text.isBlank()) return "en"
        return try {
            val options = LanguageIdentificationOptions.Builder()
                .setConfidenceThreshold(confidenceThreshold)
                .build()
            val identifier = LanguageIdentification.getClient(options)
            val result = identifier.identifyLanguage(text).await<String>()
            runCatching { identifier.close() }
            if (result == "und" || languages.none { it.first == result }) {
                inferScriptLanguageFallback(text) ?: "en"
            } else {
                result
            }
        } catch (e: Exception) {
            Log.w(TAG, "Language detection failed", e)
            inferScriptLanguageFallback(text) ?: "en"
        }
    }

    /**
     * Returns the set of ML Kit language codes whose offline translation models are
     * currently stored locally on device.
     */
    suspend fun downloadedModelLanguages(): Set<String> = withContext(Dispatchers.IO) {
        runCatching {
            val models = RemoteModelManager.getInstance()
                .getDownloadedModels(TranslateRemoteModel::class.java)
                .await()
            models.mapNotNull { it.language.takeIf(String::isNotBlank) }.toSet()
        }.getOrDefault(emptySet())
    }

    suspend fun isPairDownloaded(sourceLang: String, targetLang: String): Boolean {
        val downloaded = downloadedModelLanguages()
        return sourceLang in downloaded && targetLang in downloaded
    }

    /**
     * Downloads a single offline language pack (pair with English when not English)
     * so instant zero-latency translation works even offline in-game.
     */
    suspend fun downloadLanguageModel(
        langCode: String,
        allowCellular: Boolean = true,
    ): Set<String> = withContext(Dispatchers.IO) {
        val normalized = langCode.lowercase(Locale.US)
        val mlKitCode = TranslateLanguage.fromLanguageTag(normalized) ?: normalized
        val conditions = DownloadConditions.Builder().apply {
            if (!allowCellular) requireWifi()
        }.build()
        val remoteModel = TranslateRemoteModel.Builder(mlKitCode).build()
        RemoteModelManager.getInstance().download(remoteModel, conditions).await()
        Log.d(TAG, "Offline pack downloaded: $mlKitCode")
        downloadedModelLanguages()
    }

    suspend fun preloadLanguagePair(
        sourceLang: String,
        targetLang: String,
        allowCellular: Boolean = true,
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            ensureModel(sourceLang, targetLang, allowCellular = allowCellular)
            true
        }.getOrElse { err ->
            Log.w(TAG, "Preload $sourceLang → $targetLang failed: ${err.message}")
            false
        }
    }

    /**
     * Ensure the ML Kit model for [sourceLang] → [targetLang] is downloaded and return a cached [Translator].
     */
    suspend fun ensureModel(
        sourceLang: String,
        targetLang: String,
        allowCellular: Boolean = false,
    ): Translator = withContext(Dispatchers.IO) {
        require(languages.any { it.first == sourceLang }) { "Unsupported source language: $sourceLang" }
        require(languages.any { it.first == targetLang }) { "Unsupported target language: $targetLang" }

        val key = "$sourceLang|$targetLang"
        modelMutex.withLock {
            val translator = translatorPool.getOrPut(key) {
                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(sourceLang)
                    .setTargetLanguage(targetLang)
                    .build()
                Translation.getClient(options)
            }

            val conditions = DownloadConditions.Builder().apply {
                if (!allowCellular) requireWifi()
            }.build()
            translator.downloadModelIfNeeded(conditions).await()
            Log.d(TAG, "Model ready: $sourceLang → $targetLang")
            translator
        }
    }

    /**
     * Auto-detects the language (when [sourceLangHint] is `"auto"`) and translates
     * a spoken/chat phrase into [targetLang] via ML Kit on-device models (works over Wi-Fi or cellular).
     * Returns `Pair(detectedSourceLangCode, translatedText)`.
     */
    suspend fun translatePhrase(
        text: String,
        sourceLangHint: String = "auto",
        targetLang: String = "en",
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return@withContext targetLang to ""

        val safeTarget = targetLang.lowercase(Locale.US)
            .takeIf { lang -> languages.any { it.first == lang } } ?: "en"

        val detected = if (sourceLangHint == "auto" || sourceLangHint.isBlank()) {
            detectLanguage(cleaned, confidenceThreshold = 0.35f)
        } else {
            sourceLangHint.lowercase(Locale.US)
        }

        if (detected == safeTarget || languages.none { it.first == detected }) {
            return@withContext detected to cleaned
        }

        val cacheKey = "$cleaned|$detected|$safeTarget"
        cache[cacheKey]?.let { cached -> return@withContext detected to cached }

        val translator = ensureModel(detected, safeTarget, allowCellular = true)
        val translated = translator.translate(cleaned).await().orEmpty().trim()
        if (translated.isNotEmpty()) {
            cache[cacheKey] = translated
            detected to translated
        } else {
            detected to cleaned
        }
    }

    suspend fun translatePhraseToEnglish(
        text: String,
        sourceLangHint: String = "auto",
    ): Pair<String, String> = translatePhrase(
        text = text,
        sourceLangHint = sourceLangHint,
        targetLang = "en",
    )

    /**
     * Translate all non-blank lyric lines through a pooled ML Kit [Translator] in one pass.
     */
    suspend fun translateLines(
        lines: List<LyricLine>,
        sourceLang: String,
        targetLang: String = Locale.getDefault().language,
    ): List<LyricLine> = withContext(Dispatchers.IO) {
        val translator = ensureModel(sourceLang, targetLang, allowCellular = false)

        lines.map { line ->
            if (line.text.isBlank()) return@map line

            val cacheKey = "${line.text}|$sourceLang|$targetLang"
            cache[cacheKey]?.let { cached -> return@map line.copy(translatedText = cached) }

            val translated = translator.translate(line.text).await()
            if (!translated.isNullOrBlank()) {
                cache[cacheKey] = translated
                line.copy(translatedText = translated)
            } else {
                line
            }
        }
    }

    fun closeCachedTranslators() {
        translatorPool.values.forEach { runCatching { it.close() } }
        translatorPool.clear()
    }
}
