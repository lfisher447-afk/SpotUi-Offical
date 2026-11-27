package com.music.spotui.translator

import android.content.Context
import com.music.spotui.data.api.TranslationApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class RoutedTranslationResult(
    val originalText: String,
    val translatedText: String,
    val detectedSourceLang: String,
    val targetLang: String,
    val engineUsed: TranslationEngineBackend,
    val latencyMs: Long,
)

data class EngineTelemetryStats(
    val backend: TranslationEngineBackend,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val lastLatencyMs: Long = 0L,
    val lastError: String? = null,
)

/**
 * Multi-library translation router supporting:
 * 1) Smart Cascade
 * 2) Google ML Kit Offline (with automatic on-device language identification)
 * 3) Tactical Gaming Callout Lexicon (<1ms)
 * 4) Lingva Translate (anonymous web mirror)
 * 5) LibreTranslate (open-source REST mirror)
 * 6) MyMemory Translation Memory API
 * 7) Google GTX fast endpoint
 */
object MultiEngineTranslationRouter {
    private val cache = ConcurrentHashMap<String, RoutedTranslationResult>()

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    private val _engineStats = MutableStateFlow<Map<TranslationEngineBackend, EngineTelemetryStats>>(emptyMap())
    val engineStats: StateFlow<Map<TranslationEngineBackend, EngineTelemetryStats>> = _engineStats.asStateFlow()

    suspend fun detectLanguageSmart(
        text: String,
        sourceHint: String,
        threshold: Float,
    ): String {
        val clean = text.trim()
        if (clean.isEmpty()) return "en"
        if (sourceHint != "auto" && sourceHint.isNotBlank()) {
            return sourceHint.lowercase(Locale.US)
        }
        GamingCalloutLexicon.lookupExact(clean, "auto")?.let { return it.detectedLangCode }
        return TranslationApi.detectLanguage(clean, confidenceThreshold = threshold)
    }

    suspend fun translate(
        context: Context,
        rawText: String,
        sourceHint: String = "auto",
        targetLang: String = "en",
        backendOverride: TranslationEngineBackend? = null,
    ): RoutedTranslationResult = withContext(Dispatchers.IO) {
        val config = TranslationEnginePreferences.hydrateIfNeeded(context)
        val clean = rawText.trim()
        val safeTarget = targetLang.lowercase(Locale.US).ifBlank { "en" }
        if (clean.isEmpty()) {
            return@withContext RoutedTranslationResult(
                originalText = clean,
                translatedText = "",
                detectedSourceLang = safeTarget,
                targetLang = safeTarget,
                engineUsed = backendOverride ?: config.engineBackend,
                latencyMs = 0L,
            )
        }

        val startMs = System.currentTimeMillis()
        val selectedBackend = backendOverride ?: config.engineBackend
        val detectedSource = detectLanguageSmart(clean, sourceHint, config.autoDetectThreshold)

        if (detectedSource == safeTarget) {
            val masked = GamingCalloutLexicon.applyProfanityMaskIfEnabled(clean, config.profanityMaskEnabled)
            return@withContext RoutedTranslationResult(
                originalText = clean,
                translatedText = masked,
                detectedSourceLang = detectedSource,
                targetLang = safeTarget,
                engineUsed = selectedBackend,
                latencyMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L),
            )
        }

        val cacheKey = "${selectedBackend.id}|$detectedSource|$safeTarget|$clean"
        cache[cacheKey]?.let { hit ->
            val masked = GamingCalloutLexicon.applyProfanityMaskIfEnabled(hit.translatedText, config.profanityMaskEnabled)
            return@withContext hit.copy(translatedText = masked, latencyMs = 1L)
        }

        val order = resolveCascadeOrder(selectedBackend, config.gamingLexiconEnabled)
        for (engine in order) {
            val attemptStart = System.currentTimeMillis()
            val candidate = runCatching {
                executeSingleBackend(
                    backend = engine,
                    text = clean,
                    sourceLang = detectedSource,
                    targetLang = safeTarget,
                    config = config,
                )
            }.onFailure { err ->
                recordFailure(engine, err.message ?: err.javaClass.simpleName)
            }.getOrNull()

            if (!candidate.isNullOrBlank()) {
                val attemptLatency = (System.currentTimeMillis() - attemptStart).coerceAtLeast(1L)
                recordSuccess(engine, attemptLatency)
                val finalMasked = GamingCalloutLexicon.applyProfanityMaskIfEnabled(candidate, config.profanityMaskEnabled)
                val result = RoutedTranslationResult(
                    originalText = clean,
                    translatedText = finalMasked,
                    detectedSourceLang = detectedSource,
                    targetLang = safeTarget,
                    engineUsed = engine,
                    latencyMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L),
                )
                cache[cacheKey] = result
                return@withContext result
            }
        }

        RoutedTranslationResult(
            originalText = clean,
            translatedText = GamingCalloutLexicon.applyProfanityMaskIfEnabled(clean, config.profanityMaskEnabled),
            detectedSourceLang = detectedSource,
            targetLang = safeTarget,
            engineUsed = selectedBackend,
            latencyMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L),
        )
    }

    internal fun resolveCascadeOrder(
        selected: TranslationEngineBackend,
        gamingLexiconEnabled: Boolean,
    ): List<TranslationEngineBackend> {
        val tail = listOf(
            TranslationEngineBackend.MLKIT_OFFLINE,
            TranslationEngineBackend.LINGVA_MIRROR,
            TranslationEngineBackend.GOOGLE_GTX,
            TranslationEngineBackend.LIBRE_TRANSLATE,
            TranslationEngineBackend.MYMEMORY_API,
        )
        return when (selected) {
            TranslationEngineBackend.HYBRID_CASCADE -> buildList {
                if (gamingLexiconEnabled) add(TranslationEngineBackend.GAMING_LEXICON)
                addAll(tail)
            }
            TranslationEngineBackend.GAMING_LEXICON -> listOf(
                TranslationEngineBackend.GAMING_LEXICON,
                TranslationEngineBackend.MLKIT_OFFLINE,
                TranslationEngineBackend.GOOGLE_GTX,
            )
            else -> buildList {
                add(selected)
                if (gamingLexiconEnabled) add(TranslationEngineBackend.GAMING_LEXICON)
                tail.filterTo(this) { it != selected }
            }
        }
    }

    private suspend fun executeSingleBackend(
        backend: TranslationEngineBackend,
        text: String,
        sourceLang: String,
        targetLang: String,
        config: TranslationConfigState,
    ): String? = when (backend) {
        TranslationEngineBackend.HYBRID_CASCADE -> null
        TranslationEngineBackend.GAMING_LEXICON -> {
            if (targetLang != "en") null
            else GamingCalloutLexicon.lookupExact(text, sourceLang)?.englishMeaning
        }
        TranslationEngineBackend.MLKIT_OFFLINE -> {
            val (_, translated) = TranslationApi.translatePhrase(text, sourceLang, targetLang)
            translated.takeIf { it.isNotBlank() && !it.equals(text, ignoreCase = true) }
        }
        TranslationEngineBackend.LINGVA_MIRROR -> queryLingva(config.lingvaMirrorUrl, text, sourceLang, targetLang)
        TranslationEngineBackend.LIBRE_TRANSLATE -> queryLibreTranslate(
            config.libreTranslateUrl,
            config.libreApiKey,
            text,
            sourceLang,
            targetLang,
        )
        TranslationEngineBackend.MYMEMORY_API -> queryMyMemory(text, sourceLang, targetLang)
        TranslationEngineBackend.GOOGLE_GTX -> queryGoogleGtx(text, sourceLang, targetLang)
    }

    internal fun parseLingvaJson(body: String): String? = runCatching {
        JSONObject(body).optString("translation").trim().takeIf { it.isNotEmpty() }
    }.getOrNull()

    internal fun parseLibreJson(body: String): String? = runCatching {
        JSONObject(body).optString("translatedText").trim().takeIf { it.isNotEmpty() }
    }.getOrNull()

    internal fun parseMyMemoryJson(body: String): String? = runCatching {
        JSONObject(body).optJSONObject("responseData")
            ?.optString("translatedText")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    internal fun parseGoogleGtxJson(body: String): String? = runCatching {
        val root = JSONArray(body)
        val sentences = root.optJSONArray(0) ?: return null
        val sb = StringBuilder()
        for (i in 0 until sentences.length()) {
            val chunk = sentences.optJSONArray(i)?.optString(0).orEmpty()
            sb.append(chunk)
        }
        sb.toString().trim().takeIf { it.isNotEmpty() }
    }.getOrNull()

    private fun queryLingva(
        mirrorBaseUrl: String,
        text: String,
        sourceLang: String,
        targetLang: String,
    ): String? {
        val base = mirrorBaseUrl.trimEnd('/')
        val encoded = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
        val url = "$base/api/v1/$sourceLang/$targetLang/$encoded"
        val req = Request.Builder().url(url).get().build()
        return httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Lingva HTTP ${resp.code}")
            parseLingvaJson(resp.body?.string().orEmpty())
        }
    }

    private fun queryLibreTranslate(
        endpointUrl: String,
        apiKey: String,
        text: String,
        sourceLang: String,
        targetLang: String,
    ): String? {
        val payload = JSONObject().apply {
            put("q", text)
            put("source", sourceLang)
            put("target", targetLang)
            put("format", "text")
            if (apiKey.isNotBlank()) put("api_key", apiKey)
        }
        val req = Request.Builder()
            .url(endpointUrl)
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        return httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("LibreTranslate HTTP ${resp.code}")
            parseLibreJson(resp.body?.string().orEmpty())
        }
    }

    private fun queryMyMemory(
        text: String,
        sourceLang: String,
        targetLang: String,
    ): String? {
        val encoded = URLEncoder.encode(text, "UTF-8")
        val url = "https://api.mymemory.translated.net/get?q=$encoded&langpair=$sourceLang|$targetLang"
        val req = Request.Builder().url(url).get().build()
        return httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("MyMemory HTTP ${resp.code}")
            parseMyMemoryJson(resp.body?.string().orEmpty())
        }
    }

    private fun queryGoogleGtx(
        text: String,
        sourceLang: String,
        targetLang: String,
    ): String? {
        val encoded = URLEncoder.encode(text, "UTF-8")
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sourceLang&tl=$targetLang&dt=t&q=$encoded"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "SpotUI/2.5.6")
            .get()
            .build()
        return httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("GTX HTTP ${resp.code}")
            parseGoogleGtxJson(resp.body?.string().orEmpty())
        }
    }

    private fun recordSuccess(backend: TranslationEngineBackend, latencyMs: Long) {
        val current = _engineStats.value.toMutableMap()
        val prev = current[backend] ?: EngineTelemetryStats(backend)
        current[backend] = prev.copy(
            successCount = prev.successCount + 1,
            lastLatencyMs = latencyMs,
            lastError = null,
        )
        _engineStats.value = current
    }

    private fun recordFailure(backend: TranslationEngineBackend, error: String) {
        val current = _engineStats.value.toMutableMap()
        val prev = current[backend] ?: EngineTelemetryStats(backend)
        current[backend] = prev.copy(
            failureCount = prev.failureCount + 1,
            lastError = error,
        )
        _engineStats.value = current
    }

    fun clearCache() {
        cache.clear()
    }
}
