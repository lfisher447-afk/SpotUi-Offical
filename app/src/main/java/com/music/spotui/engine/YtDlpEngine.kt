package com.music.spotui.engine

import android.content.Context
import com.metrolist.innertube.models.Thumbnails
import com.metrolist.innertube.models.response.PlayerResponse
import com.metrolist.music.constants.AudioQuality
import com.music.spotui.data.preferences.ModuleLibsConfig
import com.music.spotui.data.preferences.SpotuiModuleId
import com.music.spotui.util.AppDiagnostics
import com.music.spotui.util.DevConsoleManager
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.math.roundToInt

/**
 * Embedded on-device `yt-dlp` + FFmpeg runtime powered by `youtubedl-android` (v0.18.1).
 *
 * Runs cleanly inside the Android APK without any desktop shell or external server,
 * acting as both an explicit dedicated stream extractor and the final resilient fallback
 * whenever InnerTube clients hit PoToken / bot / signature changes.
 */
object YtDlpEngine {
    enum class EngineState(val label: String) {
        UNINITIALIZED("Standby"),
        INITIALIZING("Initializing…"),
        READY("Ready"),
        FAILED("Init Failed"),
    }

    data class YtDlpStreamResult(
        val streamUrl: String,
        val format: PlayerResponse.StreamingData.Format,
        val videoDetails: PlayerResponse.VideoDetails?,
        val expiresInSeconds: Int = 21600,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _state = MutableStateFlow(EngineState.UNINITIALIZED)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _binaryVersion = MutableStateFlow<String?>(null)
    val binaryVersion: StateFlow<String?> = _binaryVersion.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    @Volatile
    private var initializedSuccessfully = false

    fun warmupAsync(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            initIfNeeded(appContext)
        }
    }

    suspend fun initIfNeeded(context: Context): Boolean {
        if (initializedSuccessfully && _state.value == EngineState.READY) {
            return true
        }
        return initMutex.withLock {
            if (initializedSuccessfully && _state.value == EngineState.READY) {
                return@withLock true
            }
            _state.value = EngineState.INITIALIZING
            withContext(Dispatchers.IO) {
                runCatching {
                    val appContext = context.applicationContext
                    YoutubeDL.getInstance().init(appContext)
                    runCatching { FFmpeg.getInstance().init(appContext) }
                        .onFailure {
                            AppDiagnostics.warning("YtDlpEngine", "FFmpeg bundle init skipped: ${it.message}")
                        }
                    val ver = runCatching { YoutubeDL.getInstance().version(appContext) }.getOrNull()
                    _binaryVersion.value = ver
                    _lastError.value = null
                    initializedSuccessfully = true
                    _state.value = EngineState.READY
                    DevConsoleManager.logInfo(
                        "YtDlpEngine",
                        "Embedded yt-dlp initialized inside APK (version=${ver ?: "bundled"})",
                    )
                    true
                }.getOrElse { err ->
                    initializedSuccessfully = false
                    _lastError.value = err.message ?: err.javaClass.simpleName
                    _state.value = EngineState.FAILED
                    AppDiagnostics.warning("YtDlpEngine", "Embedded yt-dlp init failed: ${err.message}", err)
                    false
                }
            }
        }
    }

    suspend fun updateBinary(context: Context): Result<String> = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        if (!initIfNeeded(appContext)) {
            return@withContext Result.failure(IllegalStateException(_lastError.value ?: "yt-dlp not initialized"))
        }
        runCatching {
            val status = YoutubeDL.getInstance().updateYoutubeDL(
                appContext,
                YoutubeDL.UpdateChannel.STABLE,
            )
            val newVer = runCatching { YoutubeDL.getInstance().version(appContext) }.getOrNull()
            _binaryVersion.value = newVer
            val msg = "yt-dlp update status=${status?.name ?: "OK"} (version=${newVer ?: "unknown"})"
            DevConsoleManager.logInfo("YtDlpEngine", msg)
            msg
        }.onFailure { err ->
            DevConsoleManager.logWarning("YtDlpEngine", "yt-dlp binary update failed: ${err.message}")
        }
    }

    suspend fun resolveAudioStream(
        context: Context,
        videoId: String,
        audioQuality: AudioQuality,
    ): YtDlpStreamResult? = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        if (!ModuleLibsConfig.isModuleEnabled(appContext, SpotuiModuleId.YTDLP)) {
            return@withContext null
        }
        if (!initIfNeeded(appContext)) {
            return@withContext null
        }

        val formatSelector = when (audioQuality) {
            AudioQuality.LOW -> "worstaudio[ext=webm]/worstaudio[ext=m4a]/bestaudio[abr<=96]/bestaudio"
            AudioQuality.HIGH, AudioQuality.AUTO -> "bestaudio[ext=webm]/bestaudio[ext=m4a]/bestaudio/best"
        }

        val targetUrl = "https://www.youtube.com/watch?v=$videoId"
        val startedAt = System.currentTimeMillis()
        runCatching {
            val request = YoutubeDLRequest(targetUrl).apply {
                addOption("--no-playlist")
                addOption("--no-warnings")
                addOption("-j")
                addOption("-f", formatSelector)
                addOption("--extractor-args", "youtube:player_client=android_vr,mweb,web")
            }
            val response = YoutubeDL.getInstance().execute(request, null, null)
            val rawOut = response.out.trim()
            val jsonLine = rawOut.lineSequence().lastOrNull { it.trimStart().startsWith("{") }
                ?: error("Empty JSON metadata from yt-dlp for $videoId")

            val root = json.parseToJsonElement(jsonLine).jsonObject
            val streamUrl = root.str("url")
                ?.takeIf { it.startsWith("http") }
                ?: error("yt-dlp did not return a direct stream URL for $videoId")

            val ext = root.str("ext") ?: "webm"
            val acodec = root.str("acodec") ?: if (ext == "m4a") "mp4a.40.2" else "opus"
            val mimeType = when (ext.lowercase()) {
                "webm", "opus" -> "audio/webm; codecs=\"$acodec\""
                "m4a", "mp4" -> "audio/mp4; codecs=\"$acodec\""
                "mp3" -> "audio/mpeg"
                else -> "audio/$ext; codecs=\"$acodec\""
            }
            val abrKbps = root["abr"]?.jsonPrimitive?.content?.toDoubleOrNull()
                ?: root["tbr"]?.jsonPrimitive?.content?.toDoubleOrNull()
                ?: 128.0
            val bitrateBps = (abrKbps * 1000.0).roundToInt().coerceAtLeast(32_000)
            val itag = root.str("format_id")?.toIntOrNull()
                ?: if (ext.equals("m4a", ignoreCase = true)) 140 else 251
            val durationSec = root["duration"]?.jsonPrimitive?.intOrNull ?: 0
            val contentLength = root["filesize"]?.jsonPrimitive?.longOrNull
                ?: root["filesize_approx"]?.jsonPrimitive?.longOrNull
            val sampleRate = root["asr"]?.jsonPrimitive?.intOrNull ?: 48_000
            val channels = root["audio_channels"]?.jsonPrimitive?.intOrNull ?: 2
            val loudnessDb = root["loudness"]?.jsonPrimitive?.content?.toDoubleOrNull()

            val format = PlayerResponse.StreamingData.Format(
                itag = itag,
                url = streamUrl,
                mimeType = mimeType,
                bitrate = bitrateBps,
                width = null,
                height = null,
                contentLength = contentLength,
                quality = "tiny",
                fps = null,
                qualityLabel = null,
                averageBitrate = bitrateBps,
                audioQuality = "AUDIO_QUALITY_MEDIUM",
                approxDurationMs = if (durationSec > 0) (durationSec * 1000L).toString() else null,
                audioSampleRate = sampleRate,
                audioChannels = channels,
                loudnessDb = loudnessDb,
                lastModified = null,
                signatureCipher = null,
                cipher = null,
                audioTrack = null,
            )

            val thumbUrl = root.str("thumbnail")
            val videoDetails = PlayerResponse.VideoDetails(
                videoId = root.str("id") ?: videoId,
                title = root.str("title"),
                author = root.str("uploader") ?: root.str("channel"),
                channelId = root.str("channel_id").orEmpty(),
                lengthSeconds = durationSec.toString(),
                musicVideoType = null,
                viewCount = root.str("view_count"),
                thumbnail = Thumbnails(
                    thumbnails = if (!thumbUrl.isNullOrBlank()) {
                        listOf(com.metrolist.innertube.models.Thumbnail(url = thumbUrl, width = 480, height = 360))
                    } else {
                        emptyList()
                    },
                ),
            )

            val elapsed = System.currentTimeMillis() - startedAt
            DevConsoleManager.logInfo(
                "YtDlpEngine",
                "Resolved $videoId in ${elapsed}ms via embedded yt-dlp (ext=$ext abr=${abrKbps.roundToInt()}kbps)",
            )

            YtDlpStreamResult(
                streamUrl = streamUrl,
                format = format,
                videoDetails = videoDetails,
                expiresInSeconds = 21_600,
            )
        }.onFailure { err ->
            DevConsoleManager.logWarning("YtDlpEngine", "yt-dlp failed for $videoId: ${err.message}")
        }.getOrNull()
    }

    private fun JsonObject.str(key: String): String? =
        this[key]?.jsonPrimitive?.takeIf { it.isString || it.content != "null" }?.content
}

private inline fun <T> YtDlpEngine.withLockError(block: () -> T): T = block()
