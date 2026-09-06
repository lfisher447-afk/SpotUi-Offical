/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import android.util.Log
import androidx.media3.common.PlaybackException
import com.metrolist.innertube.NewPipeExtractor
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YouTubeClient
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_CREATOR
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_NO_SDK
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_43_32
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_61_48
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_VR_NO_AUTH
import com.metrolist.innertube.models.YouTubeClient.Companion.IOS
import com.metrolist.innertube.models.YouTubeClient.Companion.IPADOS
import com.metrolist.innertube.models.YouTubeClient.Companion.MOBILE
import com.metrolist.innertube.models.YouTubeClient.Companion.TVHTML5
import com.metrolist.innertube.models.YouTubeClient.Companion.TVHTML5_SIMPLY_EMBEDDED_PLAYER
import com.metrolist.innertube.models.YouTubeClient.Companion.WEB
import com.metrolist.innertube.models.YouTubeClient.Companion.WEB_CREATOR
import com.metrolist.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.metrolist.innertube.models.response.PlayerResponse
import com.metrolist.music.constants.AudioQuality
import com.metrolist.music.utils.cipher.CipherDeobfuscator
import com.metrolist.music.utils.potoken.PoTokenGenerator
import com.metrolist.music.utils.potoken.PoTokenResult
import com.metrolist.music.utils.sabr.EjsNTransformSolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import timber.log.Timber
import java.util.concurrent.TimeUnit

object YTPlayerUtils {
    private const val logTag = "YTPlayerUtils"
    private const val TAG = "YTPlayerUtils"
    
    /** Max seconds to wait for signature-timestamp resolution before giving up. */
    private const val SIG_FUTURE_TIMEOUT_SEC = 5L
    /** Max seconds to wait for PoToken generation before giving up. */
    private const val POT_FUTURE_TIMEOUT_SEC = 5L

    /** Dedicated Coroutine Scope for background operations (replaces dangerous GlobalScope) */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val httpClient = OkHttpClient.Builder()
        .proxy(YouTube.proxy)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /** Fast 5-second probe client to prevent multi-client fallbacks from stalling playback. */
    private val probeHttpClient = httpClient.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .build()

    private val poTokenGenerator = PoTokenGenerator()

    val MAIN_CLIENT: YouTubeClient = WEB_REMIX

    val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> = arrayOf(
        IOS,
        IPADOS,
        MOBILE,
        ANDROID_NO_SDK,
        TVHTML5_SIMPLY_EMBEDDED_PLAYER,
        TVHTML5,
        ANDROID_VR_1_43_32,
        ANDROID_VR_1_61_48,
        ANDROID_CREATOR,
        ANDROID_VR_NO_AUTH,
        WEB,
        WEB_CREATOR
    )

    data class PlaybackData(
        val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
        val videoDetails: PlayerResponse.VideoDetails?,
        val playbackTracking: PlayerResponse.PlaybackTracking?,
        val format: PlayerResponse.StreamingData.Format,
        val streamUrl: String,
        val streamExpiresInSeconds: Int,
    )

    /**
     * Custom player response intended to use for playback.
     * Metadata like audioConfig and videoDetails are from [MAIN_CLIENT].
     * Format & stream can be from [MAIN_CLIENT] or [STREAM_FALLBACK_CLIENTS].
     */
    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        skipValidation: Boolean = false,
    ): Result<PlaybackData> = runCatching {
        Timber.tag(TAG).d("=== PLAYER RESPONSE FOR PLAYBACK ===")
        Timber.tag(TAG).d("videoId: $videoId")
        Timber.tag(TAG).d("playlistId: $playlistId")
        Timber.tag(TAG).d("audioQuality: $audioQuality")

        // Check if this is an uploaded/privately owned track
        val isUploadedTrack = playlistId == "MLPT" || playlistId?.contains("MLPT") == true
        Timber.tag(TAG).d("Content type detection (preliminary):")
        Timber.tag(TAG).d("  isUploadedTrack (from playlistId): $isUploadedTrack")

        val isLoggedIn = YouTube.cookie != null
        Timber.tag(TAG).d("Authentication status: ${if (isLoggedIn) "LOGGED_IN" else "ANONYMOUS"}")

        val sessionId = if (isLoggedIn) YouTube.dataSyncId else YouTube.visitorData
        val mainClientNeedsPoToken = MAIN_CLIENT.useWebPoTokens

        // Run signature-timestamp and PoToken generation natively in parallel Coroutines
        val (signatureTimestamp, poToken) = coroutineScope {
            val sigDeferred = async(Dispatchers.IO) {
                getSignatureTimestampOrNull(videoId)
            }
            val potDeferred: kotlinx.coroutines.Deferred<PoTokenResult?>? = if (mainClientNeedsPoToken && sessionId != null) {
                async(Dispatchers.IO) {
                    Timber.tag(logTag).d("Generating PoToken for WEB_REMIX with sessionId")
                    runCatching { 
                        poTokenGenerator.getWebClientPoToken(videoId, sessionId)?.also {
                            Timber.tag(logTag).d("PoToken generated successfully")
                        }
                    }.getOrNull()
                }
            } else null

            val sigResult = withTimeoutOrNull(TimeUnit.SECONDS.toMillis(SIG_FUTURE_TIMEOUT_SEC)) {
                sigDeferred.await()
            } ?: run {
                Timber.tag(logTag).w("Signature timestamp timed out")
                SignatureTimestampResult(null, isAgeRestricted = false)
            }

            val potResult = withTimeoutOrNull(TimeUnit.SECONDS.toMillis(POT_FUTURE_TIMEOUT_SEC)) {
                potDeferred?.await()
            } ?: run {
                Timber.tag(logTag).w("PoToken timed out")
                null
            }

            Pair(sigResult, potResult)
        }

        Timber.tag(logTag).d("Signature timestamp: ${signatureTimestamp.timestamp}")

        // If MAIN_CLIENT needs a PoToken but we couldn't get one (WebView missing, JS
        // blocked, network hostile), WEB_REMIX will return streams that 403 on play.
        // Skip it and go straight to the fallback chain.
        val skipMainClient = mainClientNeedsPoToken && poToken == null
        if (skipMainClient) {
            Timber.tag(TAG).w("PoToken unavailable — skipping MAIN_CLIENT and using fallback chain directly")
        }

        var mainPlayerResponse: PlayerResponse? = if (skipMainClient) null else {
            Timber.tag(logTag).d("Attempting to get player response using MAIN_CLIENT: ${MAIN_CLIENT.clientName}")
            YouTube.player(videoId, playlistId, MAIN_CLIENT, signatureTimestamp.timestamp, poToken?.playerRequestPoToken).getOrNull()
        }

        // Debug uploaded track response
        if (mainPlayerResponse != null && (isUploadedTrack || playlistId?.contains("MLPT") == true)) {
            println("[PLAYBACK_DEBUG] Main player response status: ${mainPlayerResponse.playabilityStatus.status}")
            println("[PLAYBACK_DEBUG] Playability reason: ${mainPlayerResponse.playabilityStatus.reason}")
            println("[PLAYBACK_DEBUG] Video details: title=${mainPlayerResponse.videoDetails?.title}, videoId=${mainPlayerResponse.videoDetails?.videoId}")
            println("[PLAYBACK_DEBUG] Streaming data null? ${mainPlayerResponse.streamingData == null}")
            println("[PLAYBACK_DEBUG] Adaptive formats count: ${mainPlayerResponse.streamingData?.adaptiveFormats?.size ?: 0}")
        }

        var usedAgeRestrictedClient: YouTubeClient? = null
        val wasOriginallyAgeRestricted: Boolean

        val mainStatus = mainPlayerResponse?.playabilityStatus?.status
        val mainReason = mainPlayerResponse?.playabilityStatus?.reason.orEmpty()

        val isBotDetection = mainReason.contains("bot", ignoreCase = true) ||
                mainReason.contains("confirm you", ignoreCase = true) ||
                mainReason.contains("Sign in to confirm", ignoreCase = true) ||
                mainReason.contains("Log in to confirm", ignoreCase = true)

        if (isBotDetection) {
            Timber.tag(TAG).w("Bot detection triggered on MAIN_CLIENT (reason: $mainReason). Falling back to non-PoToken client chain.")
            // Asynchronously refresh visitorData so future sessions obtain a clean session token
            scope.launch {
                runCatching { YouTube.visitorData = YouTube.visitorData().getOrNull() ?: YouTube.visitorData }
            }
        }

        // Only classify as age-restricted if it is NOT bot detection and has age check status / reason
        val isAgeRestrictedFromResponse = !isBotDetection && (
            signatureTimestamp.isAgeRestricted ||
            mainStatus in listOf("AGE_CHECK_REQUIRED", "AGE_VERIFICATION_REQUIRED", "CONTENT_CHECK_REQUIRED") ||
            (mainStatus == "LOGIN_REQUIRED" && (mainReason.contains("age", ignoreCase = true) || mainReason.contains("verify", ignoreCase = true)))
        )
        wasOriginallyAgeRestricted = isAgeRestrictedFromResponse

        if (isAgeRestrictedFromResponse && isLoggedIn) {
            // Age-restricted: use WEB_CREATOR directly (no NewPipe needed from here)
            Timber.tag(logTag).d("Age-restricted detected, using WEB_CREATOR")
            Timber.tag(TAG).i("Age-restricted: using WEB_CREATOR for videoId=$videoId")
            val creatorResponse = YouTube.player(videoId, playlistId, WEB_CREATOR, null, null).getOrNull()
            if (creatorResponse?.playabilityStatus?.status == "OK") {
                Timber.tag(logTag).d("WEB_CREATOR works for age-restricted content")
                mainPlayerResponse = creatorResponse
                usedAgeRestrictedClient = WEB_CREATOR
            }
        }

        var format: PlayerResponse.StreamingData.Format? = null
        var streamUrl: String? = null
        var streamExpiresInSeconds: Int? = null
        var streamPlayerResponse: PlayerResponse? = null
        val retryMainPlayerResponse: PlayerResponse? = if (usedAgeRestrictedClient != null) mainPlayerResponse else null

        // Check current status
        val isAgeRestricted = isAgeRestrictedFromResponse

        if (isAgeRestricted) {
            Timber.tag(logTag).d("Content is age-restricted (status: $mainStatus), will try fallback clients")
            Timber.tag(TAG).i("Age-restricted content detected: videoId=$videoId, status=$mainStatus")
        }

        // Check if this is a privately owned track (uploaded song)
        val isPrivateTrack = mainPlayerResponse?.videoDetails?.musicVideoType == "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"

        // For private tracks: use TVHTML5 (index 1) with PoToken + n-transform
        // For age-restricted: skip main client, start with fallbacks
        // For normal content: standard order
        val startIndex = when {
            isPrivateTrack -> 1  // TVHTML5
            isAgeRestricted -> 0
            skipMainClient -> 0  // MAIN_CLIENT streams unplayable without PoToken
            mainPlayerResponse == null || mainPlayerResponse.playabilityStatus.status != "OK" -> 0
            else -> -1
        }

        for (clientIndex in (startIndex until STREAM_FALLBACK_CLIENTS.size)) {
            // reset for each client
            format = null
            streamUrl = null
            streamExpiresInSeconds = null

            // decide which client to use for streams and load its player response
            val client: YouTubeClient
            if (clientIndex == -1) {
                // try with streams from main client first (use retry response if available)
                client = MAIN_CLIENT
                streamPlayerResponse = retryMainPlayerResponse ?: mainPlayerResponse
                Timber.tag(logTag).d("Trying stream from MAIN_CLIENT: ${client.clientName}")
            } else {
                // after main client use fallback clients
                client = STREAM_FALLBACK_CLIENTS[clientIndex]
                Timber.tag(logTag).d("Trying fallback client ${clientIndex + 1}/${STREAM_FALLBACK_CLIENTS.size}: ${client.clientName}")

                if (client.loginRequired && !isLoggedIn && YouTube.cookie == null) {
                    // skip client if it requires login but user is not logged in
                    Timber.tag(logTag).d("Skipping client ${client.clientName} - requires login but user is not logged in")
                    continue
                }

                Timber.tag(logTag).d("Fetching player response for fallback client: ${client.clientName}")
                // Only pass poToken for clients that support it
                val clientPoToken = if (client.useWebPoTokens) poToken?.playerRequestPoToken else null
                // Skip signature timestamp for age-restricted (faster), use it for normal content
                val clientSigTimestamp = if (wasOriginallyAgeRestricted) null else signatureTimestamp.timestamp
                streamPlayerResponse =
                    YouTube.player(videoId, playlistId, client, clientSigTimestamp, clientPoToken).getOrNull()
            }

            // YouTube content substitution guard: a client with a mismatched
            // session can return a playable response for a DIFFERENT video (the
            // classic "plays the wrong song" bug). Never accept streams whose
            // videoId doesn't match what we asked for.
            val returnedVideoId = streamPlayerResponse?.videoDetails?.videoId
            if (returnedVideoId != null && returnedVideoId != videoId) {
                Timber.tag(TAG).w(
                    "Client ${if (clientIndex == -1) MAIN_CLIENT.clientName else STREAM_FALLBACK_CLIENTS[clientIndex].clientName} " +
                        "returned WRONG video: $returnedVideoId != $videoId — skipping",
                )
                continue
            }

            // process current client response
            if (streamPlayerResponse?.playabilityStatus?.status == "OK") {
                Timber.tag(logTag).d("Player response status OK for client: ${if (clientIndex == -1) MAIN_CLIENT.clientName else STREAM_FALLBACK_CLIENTS[clientIndex].clientName}")

                // Skip NewPipe for age-restricted content (NewPipe doesn't use our auth)
                val responseToUse = if (wasOriginallyAgeRestricted) {
                    Timber.tag(logTag).d("Skipping NewPipe for age-restricted content")
                    streamPlayerResponse
                } else {
                    // Try to get streams using newPipePlayer method
                    val newPipeResponse = YouTube.newPipePlayer(videoId, streamPlayerResponse)
                    newPipeResponse ?: streamPlayerResponse
                }

                format = findFormat(responseToUse, audioQuality, connectivityManager)

                if (format == null) {
                    Timber.tag(logTag).d("No suitable format found for client: ${if (clientIndex == -1) MAIN_CLIENT.clientName else STREAM_FALLBACK_CLIENTS[clientIndex].clientName}")
                    continue
                }

                Timber.tag(logTag).d("Format found: ${format.mimeType}, bitrate: ${format.bitrate}")

                streamUrl = findUrlOrNull(format, videoId, responseToUse, skipNewPipe = wasOriginallyAgeRestricted)
                if (streamUrl == null) {
                    Timber.tag(logTag).d("Stream URL not found for format")
                    continue
                }

                // Apply n-transform for throttle parameter handling
                val currentClient = if (clientIndex == -1) {
                    usedAgeRestrictedClient ?: MAIN_CLIENT
                } else {
                    STREAM_FALLBACK_CLIENTS[clientIndex]
                }

                // Check if this is a privately owned track
                val isPrivatelyOwnedTrack = streamPlayerResponse.videoDetails?.musicVideoType == "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"
                val musicVideoType = streamPlayerResponse.videoDetails?.musicVideoType

                Timber.tag(TAG).d("=== N-TRANSFORM DECISION ===")
                Timber.tag(TAG).d("Content type analysis:")
                Timber.tag(TAG).d("  musicVideoType: $musicVideoType")
                Timber.tag(TAG).d("  isPrivatelyOwnedTrack: $isPrivatelyOwnedTrack")
                Timber.tag(TAG).d("  isUploadedTrack (from playlistId): $isUploadedTrack")
                Timber.tag(TAG).d("  wasOriginallyAgeRestricted: $wasOriginallyAgeRestricted")
                Timber.tag(TAG).d("Client analysis:")
                Timber.tag(TAG).d("  currentClient: ${currentClient.clientName}")
                Timber.tag(TAG).d("  useWebPoTokens: ${currentClient.useWebPoTokens}")

                // Apply n-transform and PoToken for web clients OR for private tracks (including TVHTML5)
                val hasNParam = streamUrl.contains(Regex("[?&]n="))
                val needsNTransform = hasNParam || currentClient.useWebPoTokens ||
                    currentClient.clientName in listOf("WEB", "WEB_REMIX", "WEB_CREATOR", "TVHTML5", "TVHTML5_SIMPLY_EMBEDDED_PLAYER") ||
                    isPrivatelyOwnedTrack

                Timber.tag(TAG).d("N-transform decision:")
                Timber.tag(TAG).d("  needsNTransform: $needsNTransform")
                Timber.tag(TAG).d("  Reason: hasNParam=$hasNParam, useWebPoTokens=${currentClient.useWebPoTokens}, " +
                    "isPrivatelyOwnedTrack=$isPrivatelyOwnedTrack")

                if (needsNTransform) {
                    try {
                        Timber.tag(TAG).d("Applying n-transform to stream URL...")
                        Timber.tag(TAG).d("  Original URL length: ${streamUrl.length}")
                        Timber.tag(TAG).d("  Original URL preview: ${streamUrl.take(100)}...")

                        val originalUrl = streamUrl
                        // Use CipherDeobfuscator for n-transform
                        streamUrl = CipherDeobfuscator.transformNParamInUrl(streamUrl)
                        
                        // Robust secondary SABR fallback if standard method fails
                        if (hasNParam && streamUrl == originalUrl) {
                            Timber.tag(TAG).d("CipherDeobfuscator left n-param unchanged, trying EjsNTransformSolver fallback...")
                            streamUrl = EjsNTransformSolver.transformNParamInUrl(originalUrl)
                        }

                        Timber.tag(TAG).d("  Transformed URL length: ${streamUrl.length}")
                        Timber.tag(TAG).d("  URL changed: ${originalUrl != streamUrl}")

                        // Append pot= parameter with streaming data poToken
                        val needsPoToken = (currentClient.useWebPoTokens || isPrivatelyOwnedTrack) && poToken?.streamingDataPoToken != null
                        Timber.tag(TAG).d("PoToken decision:")
                        Timber.tag(TAG).d("  needsPoToken: $needsPoToken")
                        Timber.tag(TAG).d("  hasStreamingDataPoToken: ${poToken?.streamingDataPoToken != null}")

                        if (needsPoToken) {
                            Timber.tag(TAG).d("Appending pot= parameter to stream URL")
                            val separator = if ("?" in streamUrl) "&" else "?"
                            streamUrl = "${streamUrl}${separator}pot=${Uri.encode(poToken.streamingDataPoToken)}"
                            Timber.tag(TAG).d("  Final URL length (with pot): ${streamUrl.length}")
                        }
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "N-transform or pot append failed: ${e.message}")
                        Timber.tag(TAG).e("Stack trace: ${e.stackTraceToString().take(500)}")
                        // Continue with original URL
                    }
                } else {
                    Timber.tag(TAG).d("Skipping n-transform (not required for this client/content)")
                }

                streamExpiresInSeconds = streamPlayerResponse.streamingData?.expiresInSeconds
                if (streamExpiresInSeconds == null) {
                    Timber.tag(logTag).d("Stream expiration time not found")
                    continue
                }

                Timber.tag(logTag).d("Stream expires in: $streamExpiresInSeconds seconds")

                // Check if this is a privately owned track (uploaded song)
                val isPrivatelyOwned = streamPlayerResponse.videoDetails?.musicVideoType == "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"

                if (clientIndex == STREAM_FALLBACK_CLIENTS.size - 1 || isPrivatelyOwned) {
                    /** skip [validateStatus] for last client or private tracks */
                    if (isPrivatelyOwned) {
                        Timber.tag(logTag).d("Skipping validation for privately owned track: ${currentClient.clientName}")
                        println("[PLAYBACK_DEBUG] Using stream without validation for PRIVATELY_OWNED_TRACK")
                    } else {
                        Timber.tag(logTag).d("Using last fallback client without validation: ${STREAM_FALLBACK_CLIENTS[clientIndex].clientName}")
                    }
                    Timber.tag(TAG)
                        .i("Playback: client=${currentClient.clientName}, videoId=$videoId, private=$isPrivatelyOwned")
                    break
                }

                if (skipValidation || validateStatus(streamUrl)) {
                    // working stream found
                    Timber.tag(logTag).d("Stream validated successfully with client: ${currentClient.clientName}")
                    // Log for release builds
                    Timber.tag(TAG).i("Playback: client=${currentClient.clientName}, videoId=$videoId")
                    break
                } else {
                    Timber.tag(logTag).d("Stream validation failed for client: ${currentClient.clientName}")
                }
            } else {
                Timber.tag(logTag).d("Player response status not OK: ${streamPlayerResponse?.playabilityStatus?.status}, reason: ${streamPlayerResponse?.playabilityStatus?.reason}")
            }
        }

        if (streamPlayerResponse == null) {
            Timber.tag(logTag).e("Bad stream player response - all clients failed")
            if (isUploadedTrack) {
                println("[PLAYBACK_DEBUG] FAILURE: All clients failed for uploaded track videoId=$videoId")
            }
            throw Exception("Bad stream player response")
        }

        if (streamPlayerResponse.playabilityStatus.status != "OK") {
            val errorReason = streamPlayerResponse.playabilityStatus.reason
            Timber.tag(logTag).e("Playability status not OK: $errorReason")
            if (isUploadedTrack) {
                println("[PLAYBACK_DEBUG] FAILURE: Playability not OK for uploaded track - status=${streamPlayerResponse.playabilityStatus.status}, reason=$errorReason")
            }
            throw PlaybackException(
                errorReason,
                null,
                PlaybackException.ERROR_CODE_REMOTE_ERROR
            )
        }

        if (streamExpiresInSeconds == null) {
            Timber.tag(logTag).e("Missing stream expire time")
            throw Exception("Missing stream expire time")
        }

        if (format == null) {
            Timber.tag(logTag).e("Could not find format")
            throw Exception("Could not find format")
        }

        if (streamUrl == null) {
            Timber.tag(logTag).e("Could not find stream url")
            throw Exception("Could not find stream url")
        }

        Timber.tag(logTag).d("Successfully obtained playback data with format: ${format.mimeType}, bitrate: ${format.bitrate}")
        if (isUploadedTrack) {
            println("[PLAYBACK_DEBUG] SUCCESS: Got playback data for uploaded track - format=${format.mimeType}, streamUrl=${streamUrl.take(100)}...")
        }
        PlaybackData(
            streamPlayerResponse?.playerConfig?.audioConfig ?: mainPlayerResponse?.playerConfig?.audioConfig,
            streamPlayerResponse?.videoDetails ?: mainPlayerResponse?.videoDetails,
            streamPlayerResponse?.playbackTracking ?: mainPlayerResponse?.playbackTracking,
            format,
            streamUrl,
            streamExpiresInSeconds,
        )
    }.onFailure { e ->
        println("[PLAYBACK_DEBUG] EXCEPTION during playback for videoId=$videoId: ${e::class.simpleName}: ${e.message}")
        e.printStackTrace()
    }
    
    /**
     * Simple player response intended to use for metadata only.
     * Stream URLs of this response might not work so don't use them.
     */
    suspend fun playerResponseForMetadata(
        videoId: String,
        playlistId: String? = null,
    ): Result<PlayerResponse> {
        Timber.tag(logTag).d("Fetching metadata-only player response for videoId: $videoId using MAIN_CLIENT: ${MAIN_CLIENT.clientName}")
        return YouTube.player(videoId, playlistId, client = WEB_REMIX) // ANDROID_VR does not work with history
            .onSuccess { Timber.tag(logTag).d("Successfully fetched metadata") }
            .onFailure { Timber.tag(logTag).e(it, "Failed to fetch metadata") }
    }

    private fun findFormat(
        playerResponse: PlayerResponse,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
    ): PlayerResponse.StreamingData.Format? {
        Timber.tag(logTag).d("Finding format with audioQuality: $audioQuality, network metered: ${connectivityManager.isActiveNetworkMetered}")

        val format = playerResponse.streamingData?.adaptiveFormats
            ?.filter { it.isAudio && it.isOriginal }
            ?.maxByOrNull {
                it.bitrate * when (audioQuality) {
                    AudioQuality.AUTO -> if (connectivityManager.isActiveNetworkMetered) -1 else 1
                    AudioQuality.HIGH -> 1
                    AudioQuality.LOW -> -1
                } + (if (it.mimeType.startsWith("audio/webm")) 10240 else 0) // prefer opus stream
            }

        if (format != null) {
            Timber.tag(logTag).d("Selected format: ${format.mimeType}, bitrate: ${format.bitrate}")
        } else {
            Timber.tag(logTag).d("No suitable audio format found")
        }

        return format
    }
    
    /**
     * Checks if the stream url returns a successful status using a dedicated fast-probe client.
     *
     * Why the leniency: on slow mobile networks HEAD can time out or be rejected by edge
     * CDNs (405/403/410 on HEAD while GET works). If we treat those as "failed" we skip a
     * stream that actually plays. Rules here:
     *  - 2xx (including 206 Partial Content) → valid
     *  - IOException (timeout/reset) → treat as valid; ExoPlayer has its own retry and
     *    killing the client here just cascades us down the fallback chain for no reason
     *  - 403 / 404 / 410 → invalid
     */
    internal fun validateStatus(url: String): Boolean {
        Timber.tag(logTag).d("Validating stream URL status via GET Range bytes=0-0")
        try {
            val requestBuilder = okhttp3.Request.Builder()
                .get()
                .addHeader("Range", "bytes=0-0")
                .url(url)

            YouTube.cookie?.let { cookie ->
                requestBuilder.addHeader("Cookie", cookie)
            }

            val response = probeHttpClient.newCall(requestBuilder.build()).execute()
            response.close()
            
            val code = response.code
            val accepted = (response.isSuccessful || code == 206) && code != 403 && code != 404 && code != 410
            Timber.tag(logTag).d("Stream URL validation: code=$code accepted=$accepted")
            return accepted
        } catch (e: java.io.IOException) {
            // Network timeout / reset while probing. The stream URL itself may still
            // be fine — let ExoPlayer attempt GET rather than burning a fallback client.
            Timber.tag(logTag).w(e, "Stream URL probe failed (IO); accepting optimistically")
            return true
        } catch (e: Exception) {
            Timber.tag(logTag).e(e, "Stream URL validation failed with exception")
            reportException(e)
        }
        return false
    }
    
    data class SignatureTimestampResult(
        val timestamp: Int?,
        val isAgeRestricted: Boolean
    )

    private fun getSignatureTimestampOrNull(videoId: String): SignatureTimestampResult {
        Timber.tag(logTag).d("Getting signature timestamp for videoId: $videoId")
        val result = NewPipeExtractor.getSignatureTimestamp(videoId)
        return result.fold(
            onSuccess = { timestamp ->
                Timber.tag(logTag).d("Signature timestamp obtained: $timestamp")
                SignatureTimestampResult(timestamp, isAgeRestricted = false)
            },
            onFailure = { error ->
                val isAgeRestricted = error.message?.contains("age-restricted", ignoreCase = true) == true ||
                    error.cause?.message?.contains("age-restricted", ignoreCase = true) == true
                if (isAgeRestricted) {
                    Timber.tag(logTag).d("Age-restricted content detected from NewPipe")
                    Timber.tag(TAG).i("Age-restricted detected early via NewPipe: videoId=$videoId")
                } else {
                    Timber.tag(logTag).e(error, "Failed to get signature timestamp")
                    reportException(error)
                }
                SignatureTimestampResult(null, isAgeRestricted)
            }
        )
    }

    private suspend fun findUrlOrNull(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        playerResponse: PlayerResponse,
        skipNewPipe: Boolean = false
    ): String? {
        Timber.tag(logTag).d("Finding stream URL for format: ${format.mimeType}, videoId: $videoId, skipNewPipe: $skipNewPipe")

        // First check if format already has a URL
        if (!format.url.isNullOrEmpty()) {
            Timber.tag(logTag).d("Using URL from format directly")
            return format.url
        }

        // Try custom cipher deobfuscation for signatureCipher formats
        val signatureCipher = format.signatureCipher ?: format.cipher
        if (!signatureCipher.isNullOrEmpty()) {
            Timber.tag(logTag).d("Format has signatureCipher, using custom deobfuscation")
            val customDeobfuscatedUrl = CipherDeobfuscator.deobfuscateStreamUrl(signatureCipher, videoId)
            if (customDeobfuscatedUrl != null) {
                Timber.tag(logTag).d("Stream URL obtained via custom cipher deobfuscation")
                return customDeobfuscatedUrl
            }
            Timber.tag(logTag).d("Custom cipher deobfuscation failed")
        }

        // Skip NewPipe for age-restricted content
        if (skipNewPipe) {
            Timber.tag(logTag).d("Skipping NewPipe methods for age-restricted content")
            return null
        }

        // Try to get URL using NewPipeExtractor signature deobfuscation
        val deobfuscatedUrl = NewPipeExtractor.getStreamUrl(format, videoId)
        if (deobfuscatedUrl != null) {
            Timber.tag(logTag).d("Stream URL obtained via NewPipe deobfuscation")
            return deobfuscatedUrl
        }

        // Fallback: try to get URL from StreamInfo
        Timber.tag(logTag).d("Trying StreamInfo fallback for URL")
        val streamUrls = YouTube.getNewPipeStreamUrls(videoId)
        if (streamUrls.isNotEmpty()) {
            val streamUrl = streamUrls.find { it.first == format.itag }?.second
            if (streamUrl != null) {
                Timber.tag(logTag).d("Stream URL obtained from StreamInfo")
                return streamUrl
            }

            // If exact itag not found, try to find any audio stream
            val audioStream = streamUrls.find { urlPair ->
                playerResponse.streamingData?.adaptiveFormats?.any {
                    it.itag == urlPair.first && it.isAudio
                } == true
            }?.second

            if (audioStream != null) {
                Timber.tag(logTag).d("Audio stream URL obtained from StreamInfo (different itag)")
                return audioStream
            }
        }

        Timber.tag(logTag).e("Failed to get stream URL")
        return null
    }

    fun forceRefreshForVideo(videoId: String) {
        Timber.tag(logTag).d("Force refreshing for videoId: $videoId")
    }

    fun resetSession(context: Context) {
        Timber.tag(logTag).d("Resetting Player Utils Sessions")
        poTokenGenerator.reset()
        
        runCatching {
            val spotUiPrefs = Class.forName("com.music.spotui.data.preferences")
            val methodStreams = spotUiPrefs.getMethod("clearAllCachedStreams", Context::class.java)
            val methodVideos = spotUiPrefs.getMethod("clearAllResolvedVideos", Context::class.java)
            methodStreams.invoke(null, context)
            methodVideos.invoke(null, context)
        }
        
        scope.launch {
            runCatching { YouTube.visitorData = YouTube.visitorData().getOrNull() }
        }
    }

    /** Ensure gracefully failing exception analytics */
    private fun reportException(e: Throwable) {
        // Optional implementation logic depending on Crashlytics/Sentry setups.
        Log.e(TAG, "Reported Exception:", e)
    }
}
