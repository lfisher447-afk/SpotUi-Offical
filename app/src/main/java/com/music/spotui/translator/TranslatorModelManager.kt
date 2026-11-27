package com.music.spotui.translator

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

data class VoskModelPreset(
    val key: String,
    val langCode: String,
    val title: String,
    val approxSizeMb: Int,
    val downloadUrl: String,
)

data class VoskStorageInfo(
    val isReady: Boolean,
    val modelDirPath: String,
    val sizeMb: Float,
    val activeModelKey: String,
)

/**
 * Runtime manager for offline Vosk speech recognition models (stored inside `context.filesDir/vosk-model`)
 * and on-device Google ML Kit translation packs. Keeps the APK slim while giving users 1-tap offline packs.
 */
object TranslatorModelManager {
    private const val VOSK_DIR_NAME = "vosk-model"
    private const val VOSK_TEMP_DIR_NAME = "vosk-model-downloading"
    private const val KEY_INSTALLED_VOSK = "installed_vosk_key"

    val curatedVoskModels: List<VoskModelPreset> = listOf(
        VoskModelPreset(
            key = "en-us-small",
            langCode = "en",
            title = "English (US) · Fast 40MB",
            approxSizeMb = 40,
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
        ),
        VoskModelPreset(
            key = "es-small",
            langCode = "es",
            title = "Spanish · Small 39MB",
            approxSizeMb = 39,
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip",
        ),
        VoskModelPreset(
            key = "ru-small",
            langCode = "ru",
            title = "Russian · Small 45MB",
            approxSizeMb = 45,
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip",
        ),
        VoskModelPreset(
            key = "fr-small",
            langCode = "fr",
            title = "French · Small 41MB",
            approxSizeMb = 41,
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-fr-0.22.zip",
        ),
        VoskModelPreset(
            key = "pt-small",
            langCode = "pt",
            title = "Portuguese · Small 31MB",
            approxSizeMb = 31,
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-pt-0.3.zip",
        ),
        VoskModelPreset(
            key = "de-small",
            langCode = "de",
            title = "German · Small 45MB",
            approxSizeMb = 45,
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-de-0.15.zip",
        ),
        VoskModelPreset(
            key = "ar-small",
            langCode = "ar",
            title = "Arabic · Tunisien 318MB",
            approxSizeMb = 318,
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-ar-tn-0.1-linto.zip",
        ),
        VoskModelPreset(
            key = "ja-small",
            langCode = "ja",
            title = "Japanese · Small 48MB",
            approxSizeMb = 48,
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-ja-0.22.zip",
        ),
        VoskModelPreset(
            key = "ko-small",
            langCode = "ko",
            title = "Korean · Small 82MB",
            approxSizeMb = 82,
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-ko-0.22.zip",
        ),
        VoskModelPreset(
            key = "zh-small",
            langCode = "zh",
            title = "Chinese · Small 42MB",
            approxSizeMb = 42,
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip",
        ),
        VoskModelPreset(
            key = "tr-small",
            langCode = "tr",
            title = "Turkish · Small 35MB",
            approxSizeMb = 35,
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-tr-0.3.zip",
        ),
        VoskModelPreset(
            key = "hi-small",
            langCode = "hi",
            title = "Hindi · Small 42MB",
            approxSizeMb = 42,
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip",
        ),
    )

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun voskModelDir(context: Context): File = File(context.applicationContext.filesDir, VOSK_DIR_NAME)

    fun isVoskModelValid(modelDir: File): Boolean {
        if (!modelDir.exists() || !modelDir.isDirectory) return false
        val amDir = File(modelDir, "am")
        val confDir = File(modelDir, "conf")
        val graphDir = File(modelDir, "graph")
        return amDir.isDirectory && confDir.isDirectory &&
            (graphDir.isDirectory || File(modelDir, "ivector").isDirectory) &&
            (amDir.listFiles()?.isNotEmpty() == true)
    }

    fun hasValidVoskModel(context: Context): Boolean = isVoskModelValid(voskModelDir(context))

    fun inspectVoskStorage(context: Context): VoskStorageInfo {
        val dir = voskModelDir(context)
        val valid = isVoskModelValid(dir)
        val bytes = if (dir.exists()) folderByteSize(dir) else 0L
        val prefs = context.applicationContext.getSharedPreferences("spotui_model_mgr", Context.MODE_PRIVATE)
        val installedKey = prefs.getString(KEY_INSTALLED_VOSK, "").orEmpty()
        return VoskStorageInfo(
            isReady = valid,
            modelDirPath = dir.absolutePath,
            sizeMb = bytes / (1024f * 1024f),
            activeModelKey = if (valid) installedKey else "",
        )
    }

    fun presetForKeyOrLanguage(keyOrLang: String): VoskModelPreset =
        curatedVoskModels.firstOrNull {
            it.key.equals(keyOrLang, ignoreCase = true) || it.langCode.equals(keyOrLang, ignoreCase = true)
        } ?: curatedVoskModels.first()

    suspend fun downloadAndExtractVoskModel(
        context: Context,
        modelUrl: String,
        modelKey: String = "custom",
        onProgress: (Int) -> Unit = {},
    ): Result<File> = withContext(Dispatchers.IO) {
        val cleanUrl = modelUrl.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            return@withContext Result.failure(IllegalArgumentException("Invalid Vosk model URL"))
        }

        val appContext = context.applicationContext
        val targetDir = voskModelDir(appContext)
        val tempZip = File(appContext.cacheDir, "vosk_download_tmp.zip")
        val stagingDir = File(appContext.filesDir, VOSK_TEMP_DIR_NAME)

        runCatching {
            onProgress(1)
            val request = Request.Builder()
                .url(cleanUrl)
                .header("User-Agent", "SpotUI/2.5.7")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code} downloading Vosk model")
                }
                val body = response.body ?: throw IOException("Empty response downloading Vosk model")
                val totalBytes = body.contentLength()
                body.byteStream().use { input ->
                    FileOutputStream(tempZip).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var copied = 0L
                        var lastPct = 1
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            if (totalBytes > 0L) {
                                val pct = ((copied * 75L) / totalBytes).toInt().coerceIn(1, 75)
                                if (pct != lastPct) {
                                    lastPct = pct
                                    onProgress(pct)
                                }
                            }
                        }
                    }
                }
            }

            onProgress(80)
            stagingDir.deleteRecursively()
            stagingDir.mkdirs()

            tempZip.inputStream().use { raw ->
                extractZipSafely(raw, stagingDir)
            }
            onProgress(94)

            val resolvedRoot = unwrapSingleTopDirectory(stagingDir)
            if (!isVoskModelValid(resolvedRoot)) {
                throw IOException("Downloaded archive is missing required Vosk model folders (am/conf/graph)")
            }

            targetDir.deleteRecursively()
            if (resolvedRoot == stagingDir) {
                if (!stagingDir.renameTo(targetDir)) {
                    stagingDir.copyRecursively(targetDir, overwrite = true)
                    stagingDir.deleteRecursively()
                }
            } else {
                if (!resolvedRoot.renameTo(targetDir)) {
                    resolvedRoot.copyRecursively(targetDir, overwrite = true)
                }
                stagingDir.deleteRecursively()
            }

            appContext.getSharedPreferences("spotui_model_mgr", Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_INSTALLED_VOSK, modelKey)
                .apply()
            TranslationEnginePreferences.setVoskModelKey(appContext, modelKey)
            onProgress(100)
            targetDir
        }.also {
            runCatching { tempZip.delete() }
            if (it.isFailure) {
                runCatching { stagingDir.deleteRecursively() }
            }
        }
    }

    /**
     * Extracts a Vosk zip stream safely into [destDir] guarding against zip-slip path traversal.
     */
    internal fun extractZipSafely(zipInput: InputStream, destDir: File) {
        val canonicalDest = destDir.canonicalPath + File.separator
        ZipInputStream(BufferedInputStream(zipInput)).use { zis ->
            val buf = ByteArray(16 * 1024)
            while (true) {
                val entry = zis.nextEntry ?: break
                val outFile = File(destDir, entry.name)
                val outCanonical = outFile.canonicalPath
                if (!outCanonical.startsWith(canonicalDest)) {
                    throw SecurityException("Blocked unsafe zip entry: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos ->
                        while (true) {
                            val n = zis.read(buf)
                            if (n < 0) break
                            fos.write(buf, 0, n)
                        }
                    }
                }
                zis.closeEntry()
            }
        }
    }

    internal fun unwrapSingleTopDirectory(stagingDir: File): File {
        if (isVoskModelValid(stagingDir)) return stagingDir
        val children = stagingDir.listFiles()?.filter { it.name != "__MACOSX" }.orEmpty()
        return if (children.size == 1 && children.first().isDirectory) {
            children.first()
        } else {
            stagingDir
        }
    }

    fun deleteVoskModel(context: Context): Boolean {
        val dir = voskModelDir(context)
        val ok = dir.deleteRecursively()
        context.applicationContext.getSharedPreferences("spotui_model_mgr", Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_INSTALLED_VOSK)
            .apply()
        return ok
    }

    suspend fun downloadedMlKitLanguages(): Set<String> = withContext(Dispatchers.IO) {
        runCatching {
            val models = RemoteModelManager.getInstance()
                .getDownloadedModels(TranslateRemoteModel::class.java)
                .await()
            models.mapNotNull { it.language.takeIf(String::isNotBlank) }.toSet()
        }.getOrDefault(emptySet())
    }

    suspend fun ensureMlKitTranslationModel(
        sourceLanguage: String = TranslateLanguage.SPANISH,
        targetLanguage: String = TranslateLanguage.ENGLISH,
        allowCellular: Boolean = true,
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val srcTag = TranslateLanguage.fromLanguageTag(sourceLanguage.lowercase(Locale.US))
                ?: TranslateLanguage.SPANISH
            val tgtTag = TranslateLanguage.fromLanguageTag(targetLanguage.lowercase(Locale.US))
                ?: TranslateLanguage.ENGLISH
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(srcTag)
                .setTargetLanguage(tgtTag)
                .build()
            val translator = Translation.getClient(options)
            val conditions = DownloadConditions.Builder().apply {
                if (!allowCellular) requireWifi()
            }.build()
            translator.downloadModelIfNeeded(conditions).await()
            runCatching { translator.close() }
            true
        }
    }

    suspend fun ensureTargetLanguageModel(
        targetLanguage: String = TranslateLanguage.ENGLISH,
        allowCellular: Boolean = true,
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val tgtTag = TranslateLanguage.fromLanguageTag(targetLanguage.lowercase(Locale.US))
                ?: TranslateLanguage.ENGLISH
            val remote = TranslateRemoteModel.Builder(tgtTag).build()
            val conditions = DownloadConditions.Builder().apply {
                if (!allowCellular) requireWifi()
            }.build()
            RemoteModelManager.getInstance().download(remote, conditions).await()
            true
        }
    }

    private fun folderByteSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var total = 0L
        dir.walkTopDown().forEach { f ->
            if (f.isFile) total += f.length()
        }
        return total
    }
}
