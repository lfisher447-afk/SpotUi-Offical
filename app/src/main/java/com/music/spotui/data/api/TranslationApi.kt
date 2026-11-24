package com.music.spotui.data.api

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.music.spotui.data.entity.LyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

object TranslationApi {
    private const val TAG = "TranslationApi"

    /** In-memory cache: "sourceText|sourceLang|targetLang" → translatedText */
    private val cache = java.util.concurrent.ConcurrentHashMap<String, String>()

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
        languages.firstOrNull { it.first == code }?.second ?: code.uppercase()

    /**
     * Detect the language of [text] using ML Kit's on-device Language Identification API.
     */
    suspend fun detectLanguage(text: String, confidenceThreshold: Float = 0.42f): String {
        if (text.isBlank()) return "en"
        return try {
            val options = LanguageIdentificationOptions.Builder()
                .setConfidenceThreshold(confidenceThreshold)
                .build()
            val identifier = LanguageIdentification.getClient(options)
            val result = identifier.identifyLanguage(text).await<String>()
            runCatching { identifier.close() }
            if (result == "und" || languages.none { it.first == result }) "en" else result
        } catch (e: Exception) {
            Log.w(TAG, "Language detection failed", e)
            "en"
        }
    }

    /**
     * Ensure the ML Kit model for [sourceLang] → [targetLang] is downloaded.
     */
    suspend fun ensureModel(
        sourceLang: String,
        targetLang: String,
        allowCellular: Boolean = false,
    ): Translator = withContext(Dispatchers.IO) {
        require(languages.any { it.first == sourceLang }) { "Unsupported source language: $sourceLang" }
        require(languages.any { it.first == targetLang }) { "Unsupported target language: $targetLang" }

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLang)
            .setTargetLanguage(targetLang)
            .build()
        val translator = Translation.getClient(options)

        val conditions = DownloadConditions.Builder().apply {
            if (!allowCellular) requireWifi()
        }.build()
        translator.downloadModelIfNeeded(conditions).await()
        Log.d(TAG, "Model ready: $sourceLang → $targetLang")
        translator
    }

    /**
     * Auto-detects the language (when [sourceLangHint] is `"auto"`) and translates
     * a spoken/chat phrase into English via ML Kit on-device models (works over Wi-Fi or cellular).
     * Returns `Pair(detectedSourceLangCode, englishText)`.
     */
    suspend fun translatePhraseToEnglish(
        text: String,
        sourceLangHint: String = "auto",
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return@withContext "en" to ""

        val detected = if (sourceLangHint == "auto" || sourceLangHint.isBlank()) {
            detectLanguage(cleaned, confidenceThreshold = 0.35f)
        } else {
            sourceLangHint.lowercase(Locale.US)
        }

        if (detected == "en" || languages.none { it.first == detected }) {
            return@withContext detected to cleaned
        }

        val cacheKey = "$cleaned|$detected|en"
        cache[cacheKey]?.let { cached -> return@withContext detected to cached }

        val translator = ensureModel(detected, "en", allowCellular = true)
        try {
            val translated = translator.translate(cleaned).await().orEmpty().trim()
            if (translated.isNotEmpty()) {
                cache[cacheKey] = translated
                detected to translated
            } else {
                detected to cleaned
            }
        } finally {
            translator.close()
        }
    }

    /**
     * Translate all non-blank lyric lines through a single ML Kit [Translator] in one pass.
     */
    suspend fun translateLines(
        lines: List<LyricLine>,
        sourceLang: String,
        targetLang: String = Locale.getDefault().language,
    ): List<LyricLine> = withContext(Dispatchers.IO) {
        val translator = ensureModel(sourceLang, targetLang, allowCellular = false)

        try {
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
        } finally {
            translator.close()
        }
    }
}
