package com.metrolist.innertube.network

import com.metrolist.innertube.models.Context
import com.metrolist.innertube.models.YouTubeClient
import com.metrolist.innertube.models.body.AccountMenuBody
import com.metrolist.innertube.models.body.Action
import com.metrolist.innertube.models.body.BrowseBody
import com.metrolist.innertube.models.body.CreatePlaylistBody
import com.metrolist.innertube.models.body.EditPlaylistBody
import com.metrolist.innertube.models.body.FeedbackBody
import com.metrolist.innertube.models.body.GetQueueBody
import com.metrolist.innertube.models.body.GetSearchSuggestionsBody
import com.metrolist.innertube.models.body.GetTranscriptBody
import com.metrolist.innertube.models.body.LikeBody
import com.metrolist.innertube.models.body.NextBody
import com.metrolist.innertube.models.body.PlayerBody
import com.metrolist.innertube.models.body.PlaylistDeleteBody
import com.metrolist.innertube.models.body.SearchBody
import com.metrolist.innertube.models.body.SubscribeBody
import com.metrolist.innertube.utils.sapisidAuthorizationHeader
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.userAgent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.Protocol
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

fun interface InnerTubeTimingHook {
    fun onRequest(
        endpoint: String,
        method: String,
        durationMs: Long,
        statusCode: Int?,
        error: String?,
    )
}

@OptIn(ExperimentalEncodingApi::class, ExperimentalSerializationApi::class)
class InnerTubeHttp(
    private val sessionStore: AuthSessionStore = AuthSessionStore,
) {
    private val clientLock = Any()

    @Volatile
    private var currentFingerprint: String = sessionStore.currentNetworkFingerprint()

    @Volatile
    private var httpClient: HttpClient = createClient()

    @Volatile
    var timingHook: InnerTubeTimingHook? = null

    private companion object {
        const val PLAYBACK_TELEMETRY_VER = "2"
        const val YOUTUBE_API_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX3"
    }

    private fun getOrRebuildClient(): HttpClient {
        val latestFingerprint = sessionStore.currentNetworkFingerprint()
        if (latestFingerprint == currentFingerprint) {
            return httpClient
        }
        return synchronized(clientLock) {
            if (latestFingerprint != currentFingerprint) {
                runCatching { httpClient.close() }
                httpClient = createClient()
                currentFingerprint = latestFingerprint
            }
            httpClient
        }
    }

    fun invalidateClient() {
        synchronized(clientLock) {
            runCatching { httpClient.close() }
            httpClient = createClient()
            currentFingerprint = sessionStore.currentNetworkFingerprint()
        }
    }

    private fun createClient(): HttpClient =
        HttpClient(OkHttp) {
            expectSuccess = true

            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        explicitNulls = false
                        encodeDefaults = true
                        isLenient = true
                    },
                )
            }

            install(ContentEncoding) {
                gzip(0.9F)
                deflate(0.8F)
            }

            engine {
                config {
                    connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
                    connectTimeout(30, TimeUnit.SECONDS)
                    readTimeout(60, TimeUnit.SECONDS)
                    writeTimeout(60, TimeUnit.SECONDS)
                    protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
                    retryOnConnectionFailure(true)
                    cache(
                        okhttp3.Cache(
                            directory = File(System.getProperty("java.io.tmpdir"), "http_cache"),
                            maxSize = 50L * 1024L * 1024L,
                        ),
                    )
                    sessionStore.applyOkHttpSettings(this, forStream = false)
                }
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 60_000
            }

            defaultRequest {
                url(YouTubeClient.API_URL_YOUTUBE_MUSIC)
                header("Accept", "application/json")
                header("Accept-Language", "en-US,en;q=0.9")
                header("Cache-Control", "no-cache")
            }
        }

    private fun HttpRequestBuilder.ytClient(
        client: YouTubeClient,
        setLogin: Boolean = false,
        authState: PlaybackAuthState = sessionStore.currentAuthState(),
    ) {
        val origin = client.requestOrigin()
        val referer = client.requestReferer()
        contentType(ContentType.Application.Json)
        headers {
            append("X-Goog-Api-Format-Version", "1")
            append("X-YouTube-Client-Name", client.clientId)
            append("X-YouTube-Client-Version", client.clientVersion)
            append("X-Origin", origin)
            append("Origin", origin)
            append("Referer", referer)
            authState.visitorData?.let { append("X-Goog-Visitor-Id", it) }
            if (setLogin && client.loginSupported) {
                authState.delegatedSessionId?.let { append("X-Goog-PageId", it) }
                authState.cookie?.let { cookieValue ->
                    append("cookie", cookieValue)
                    sapisidAuthorizationHeader(cookieValue, origin)?.let { authHeader ->
                        append("Authorization", authHeader)
                    }
                }
            }
        }
        userAgent(client.userAgent)
        parameter("prettyPrint", false)
    }

    private suspend inline fun <T> executeTracked(
        endpoint: String,
        method: String = "POST",
        maxAttempts: Int = 3,
        initialDelay: Long = 500L,
        factor: Double = 2.0,
        crossinline block: suspend (HttpClient) -> T,
    ): T {
        val start = System.currentTimeMillis()
        var currentDelay = initialDelay
        var attempt = 0
        while (true) {
            try {
                val result = block(getOrRebuildClient())
                val status = (result as? HttpResponse)?.status?.value
                timingHook?.onRequest(
                    endpoint = endpoint,
                    method = method,
                    durationMs = (System.currentTimeMillis() - start).coerceAtLeast(0L),
                    statusCode = status,
                    error = null,
                )
                return result
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (io: IOException) {
                attempt++
                sessionStore.onProxyRequestFailed()
                if (attempt >= maxAttempts) {
                    timingHook?.onRequest(
                        endpoint = endpoint,
                        method = method,
                        durationMs = (System.currentTimeMillis() - start).coerceAtLeast(0L),
                        statusCode = null,
                        error = io.message ?: io.javaClass.simpleName,
                    )
                    throw io
                }
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong()
            } catch (t: Throwable) {
                timingHook?.onRequest(
                    endpoint = endpoint,
                    method = method,
                    durationMs = (System.currentTimeMillis() - start).coerceAtLeast(0L),
                    statusCode = null,
                    error = t.message ?: t.javaClass.simpleName,
                )
                throw t
            }
        }
    }

    suspend fun search(
        client: YouTubeClient,
        query: String? = null,
        params: String? = null,
        continuation: String? = null,
    ): HttpResponse {
        val authState = sessionStore.currentAuthState()
        return executeTracked("search") { http ->
            http.post(client.requestApiUrl("search")) {
                ytClient(client, setLogin = false, authState = authState)
                setBody(
                    SearchBody(
                        context = client.toContext(sessionStore.locale, authState.visitorData, null),
                        query = query,
                        params = params,
                    ),
                )
                parameter("continuation", continuation)
                parameter("ctoken", continuation)
            }
        }
    }

    suspend fun player(
        client: YouTubeClient,
        videoId: String,
        playlistId: String?,
        signatureTimestamp: Int?,
        poToken: String? = null,
    ): HttpResponse {
        val authState = sessionStore.currentAuthState()
        val resolvedPoToken = authState.resolvePlayerPoToken(client, poToken)
        return executeTracked("player") { http ->
            http.post(client.requestApiUrl("player")) {
                ytClient(client, setLogin = client.loginSupported, authState = authState)
                setBody(
                    PlayerBody(
                        context = client.toContext(sessionStore.locale, authState.visitorData, authState.dataSyncId).let { ctx ->
                            if (client.isEmbedded) {
                                ctx.copy(
                                    thirdParty = Context.ThirdParty(
                                        embedUrl = "https://www.youtube.com/watch?v=$videoId",
                                    ),
                                )
                            } else {
                                ctx
                            }
                        },
                        videoId = videoId,
                        playlistId = playlistId,
                        playbackContext = if (client.useSignatureTimestamp && signatureTimestamp != null) {
                            PlayerBody.PlaybackContext(
                                PlayerBody.PlaybackContext.ContentPlaybackContext(signatureTimestamp),
                            )
                        } else {
                            null
                        },
                        serviceIntegrityDimensions = if (resolvedPoToken != null && (client.useWebPoTokens || authState.webClientPoTokenEnabled)) {
                            PlayerBody.ServiceIntegrityDimensions(resolvedPoToken)
                        } else {
                            null
                        },
                    ),
                )
            }
        }
    }

    suspend fun registerPlayback(
        url: String,
        cpn: String,
        playlistId: String?,
        client: YouTubeClient = YouTubeClient.WEB_REMIX,
    ): HttpResponse {
        val authState = sessionStore.currentAuthState()
        return executeTracked("registerPlayback", method = "GET") { http ->
            http.get(url) {
                ytClient(client, setLogin = true, authState = authState)
                parameter("c", client.clientName)
                parameter("cpn", cpn)
                parameter("ver", PLAYBACK_TELEMETRY_VER)
                if (playlistId != null) {
                    parameter("list", playlistId)
                    parameter("referrer", "https://music.youtube.com/playlist?list=$playlistId")
                }
            }
        }
    }

    suspend fun browse(
        client: YouTubeClient,
        browseId: String? = null,
        params: String? = null,
        continuation: String? = null,
        setLogin: Boolean = false,
    ): HttpResponse {
        val authState = sessionStore.currentAuthState()
        val useAuth = setLogin || sessionStore.useLoginForBrowse
        return executeTracked("browse") { http ->
            http.post(client.requestApiUrl("browse")) {
                ytClient(client, setLogin = useAuth, authState = authState)
                setBody(
                    BrowseBody(
                        context = client.toContext(
                            sessionStore.locale,
                            authState.visitorData,
                            if (useAuth) authState.dataSyncId else null,
                        ),
                        browseId = browseId,
                        params = params,
                        continuation = continuation,
                    ),
                )
            }
        }
    }

    suspend fun next(
        client: YouTubeClient,
        videoId: String?,
        playlistId: String?,
        playlistSetVideoId: String?,
        index: Int?,
        params: String?,
        continuation: String? = null,
    ): HttpResponse {
        val authState = sessionStore.currentAuthState()
        return executeTracked("next") { http ->
            http.post(client.requestApiUrl("next")) {
                ytClient(client, setLogin = true, authState = authState)
                setBody(
                    NextBody(
                        context = client.toContext(sessionStore.locale, authState.visitorData, authState.dataSyncId),
                        videoId = videoId,
                        playlistId = playlistId,
                        playlistSetVideoId = playlistSetVideoId,
                        index = index,
                        params = params,
                        continuation = continuation,
                    ),
                )
            }
        }
    }

    suspend fun feedback(
        client: YouTubeClient,
        tokens: List<String>,
    ): HttpResponse {
        val authState = sessionStore.currentAuthState()
        return executeTracked("feedback") { http ->
            http.post(client.requestApiUrl("feedback")) {
                ytClient(client, setLogin = true, authState = authState)
                setBody(
                    FeedbackBody(
                        context = client.toContext(sessionStore.locale, authState.visitorData, authState.dataSyncId),
                        feedbackTokens = tokens,
                    ),
                )
            }
        }
    }

    suspend fun getSearchSuggestions(
        client: YouTubeClient,
        input: String,
    ): HttpResponse {
        val authState = sessionStore.currentAuthState()
        return executeTracked("music/get_search_suggestions") { http ->
            http.post(client.requestApiUrl("music/get_search_suggestions")) {
                ytClient(client, setLogin = false, authState = authState)
                setBody(
                    GetSearchSuggestionsBody(
                        context = client.toContext(sessionStore.locale, authState.visitorData, null),
                        input = input,
                    ),
                )
            }
        }
    }

    suspend fun getQueue(
        client: YouTubeClient,
        videoIds: List<String>?,
        playlistId: String?,
    ): HttpResponse {
        val authState = sessionStore.currentAuthState()
        return executeTracked("music/get_queue") { http ->
            http.post(client.requestApiUrl("music/get_queue")) {
                ytClient(client, setLogin = false, authState = authState)
                setBody(
                    GetQueueBody(
                        context = client.toContext(sessionStore.locale, authState.visitorData, null),
                        videoIds = videoIds,
                        playlistId = playlistId,
                    ),
                )
            }
        }
    }

    suspend fun getTranscript(
        client: YouTubeClient,
        videoId: String,
    ): HttpResponse =
        executeTracked("get_transcript") { http ->
            http.post("https://music.youtube.com/youtubei/v1/get_transcript") {
                parameter("key", YOUTUBE_API_KEY)
                headers {
                    append("Content-Type", "application/json")
                }
                setBody(
                    GetTranscriptBody(
                        context = client.toContext(sessionStore.locale, null, null),
                        params = Base64.Default.encode("\n${11.toChar()}$videoId".encodeToByteArray()),
                    ),
                )
            }
        }

    suspend fun getSwJsData(): HttpResponse =
        executeTracked("sw.js_data", method = "GET") { http ->
            http.get("https://music.youtube.com/sw.js_data")
        }

    suspend fun accountMenu(client: YouTubeClient): HttpResponse {
        val authState = sessionStore.currentAuthState()
        return executeTracked("account/account_menu") { http ->
            http.post(client.requestApiUrl("account/account_menu")) {
                ytClient(client, setLogin = true, authState = authState)
                setBody(AccountMenuBody(client.toContext(sessionStore.locale, authState.visitorData, authState.dataSyncId)))
            }
        }
    }

    suspend fun likeVideo(
        client: YouTubeClient,
        videoId: String,
    ): HttpResponse = executeLike("like/like", client, LikeBody.Target.video(videoId))

    suspend fun unlikeVideo(
        client: YouTubeClient,
        videoId: String,
    ): HttpResponse = executeLike("like/removelike", client, LikeBody.Target.video(videoId))

    suspend fun likePlaylist(
        client: YouTubeClient,
        playlistId: String,
    ): HttpResponse = executeLike("like/like", client, LikeBody.Target.playlist(playlistId))

    suspend fun unlikePlaylist(
        client: YouTubeClient,
        playlistId: String,
    ): HttpResponse = executeLike("like/removelike", client, LikeBody.Target.playlist(playlistId))

    private suspend fun executeLike(
        endpoint: String,
        client: YouTubeClient,
        target: LikeBody.Target,
    ): HttpResponse {
        val authState = sessionStore.currentAuthState()
        return executeTracked(endpoint) { http ->
            http.post(client.requestApiUrl(endpoint)) {
                ytClient(client, setLogin = true, authState = authState)
                setBody(
                    LikeBody(
                        context = client.toContext(sessionStore.locale, authState.visitorData, authState.dataSyncId),
                        target = target,
                    ),
                )
            }
        }
    }

    suspend fun subscribeChannel(
        client: YouTubeClient,
        channelId: String,
        params: String? = null,
    ): HttpResponse = executeSubscribe("subscription/subscribe", client, channelId, params)

    suspend fun unsubscribeChannel(
        client: YouTubeClient,
        channelId: String,
        params: String? = null,
    ): HttpResponse = executeSubscribe("subscription/unsubscribe", client, channelId, params)

    private suspend fun executeSubscribe(
        endpoint: String,
        client: YouTubeClient,
        channelId: String,
        params: String?,
    ): HttpResponse {
        val authState = sessionStore.currentAuthState()
        return executeTracked(endpoint) { http ->
            http.post(client.requestApiUrl(endpoint)) {
                ytClient(client, setLogin = true, authState = authState)
                setBody(
                    SubscribeBody(
                        context = client.toContext(sessionStore.locale, authState.visitorData, authState.dataSyncId),
                        channelIds = listOf(channelId),
                        params = params,
                    ),
                )
            }
        }
    }

    suspend fun addToPlaylist(
        client: YouTubeClient,
        playlistId: String,
        videoId: String,
    ): HttpResponse =
        editPlaylist(
            client = client,
            playlistId = playlistId.removePrefix("VL"),
            actions = listOf(Action.AddVideoAction(addedVideoId = videoId)),
        )

    suspend fun addPlaylistToPlaylist(
        client: YouTubeClient,
        playlistId: String,
        addPlaylistId: String,
    ): HttpResponse =
        editPlaylist(
            client = client,
            playlistId = playlistId.removePrefix("VL"),
            actions = listOf(Action.AddPlaylistAction(addedFullListId = addPlaylistId)),
        )

    suspend fun removeFromPlaylist(
        client: YouTubeClient,
        playlistId: String,
        videoId: String,
        setVideoId: String,
    ): HttpResponse =
        editPlaylist(
            client = client,
            playlistId = playlistId.removePrefix("VL"),
            actions = listOf(Action.RemoveVideoAction(removedVideoId = videoId, setVideoId = setVideoId)),
        )

    suspend fun moveSongPlaylist(
        client: YouTubeClient,
        playlistId: String,
        setVideoId: String,
        successorSetVideoId: String?,
    ): HttpResponse =
        editPlaylist(
            client = client,
            playlistId = playlistId,
            actions = listOf(Action.MoveVideoAction(movedSetVideoIdSuccessor = successorSetVideoId, setVideoId = setVideoId)),
        )

    suspend fun renamePlaylist(
        client: YouTubeClient,
        playlistId: String,
        name: String,
    ): HttpResponse =
        editPlaylist(
            client = client,
            playlistId = playlistId,
            actions = listOf(Action.RenamePlaylistAction(playlistName = name)),
        )

    suspend fun setThumbnailPlaylist(
        client: YouTubeClient,
        playlistId: String,
        blobId: String,
    ): HttpResponse =
        editPlaylist(
            client = client,
            playlistId = playlistId,
            actions = listOf(
                Action.SetCustomThumbnailAction(
                    addedCustomThumbnail = Action.SetCustomThumbnailAction.AddedCustomThumbnail(
                        playlistScottyEncryptedBlobId = blobId,
                    ),
                ),
            ),
        )

    suspend fun removeThumbnailPlaylist(
        client: YouTubeClient,
        playlistId: String,
    ): HttpResponse =
        editPlaylist(
            client = client,
            playlistId = playlistId,
            actions = listOf(Action.RemoveCustomThumbnailAction()),
        )

    private suspend fun editPlaylist(
        client: YouTubeClient,
        playlistId: String,
        actions: List<Action>,
    ): HttpResponse {
        val authState = sessionStore.currentAuthState()
        return executeTracked("browse/edit_playlist") { http ->
            http.post(client.requestApiUrl("browse/edit_playlist")) {
                ytClient(client, setLogin = true, authState = authState)
                setBody(
                    EditPlaylistBody(
                        context = client.toContext(sessionStore.locale, authState.visitorData, authState.dataSyncId),
                        playlistId = playlistId,
                        actions = actions,
                    ),
                )
            }
        }
    }

    suspend fun createPlaylist(
        client: YouTubeClient,
        title: String,
    ): HttpResponse {
        val authState = sessionStore.currentAuthState()
        return executeTracked("playlist/create") { http ->
            http.post(client.requestApiUrl("playlist/create")) {
                ytClient(client, setLogin = true, authState = authState)
                setBody(
                    CreatePlaylistBody(
                        context = client.toContext(sessionStore.locale, authState.visitorData, authState.dataSyncId),
                        title = title,
                    ),
                )
            }
        }
    }

    suspend fun deletePlaylist(
        client: YouTubeClient,
        playlistId: String,
    ): HttpResponse {
        val authState = sessionStore.currentAuthState()
        return executeTracked("playlist/delete") { http ->
            http.post(client.requestApiUrl("playlist/delete")) {
                ytClient(client, setLogin = true, authState = authState)
                setBody(
                    PlaylistDeleteBody(
                        context = client.toContext(sessionStore.locale, authState.visitorData, authState.dataSyncId),
                        playlistId = playlistId,
                    ),
                )
            }
        }
    }

    suspend fun getUploadCustomThumbnailLink(
        client: YouTubeClient,
        contentLength: Int,
    ): HttpResponse {
        val authState = sessionStore.currentAuthState()
        return executeTracked("playlist_custom_thumbnail_start") { http ->
            http.post("https://music.youtube.com/playlist_image_upload/playlist_custom_thumbnail") {
                ytClient(client, setLogin = true, authState = authState)
                headers {
                    append("X-Goog-Upload-Command", "start")
                    append("X-Goog-Upload-Protocol", "resumable")
                    append("X-Goog-Upload-Header-Content-Length", contentLength.toString())
                }
            }
        }
    }

    suspend fun uploadCustomThumbnail(
        client: YouTubeClient,
        uploadId: String,
        image: ByteArray,
    ): HttpResponse {
        val authState = sessionStore.currentAuthState()
        return executeTracked("playlist_custom_thumbnail_upload") { http ->
            http.post("https://music.youtube.com/playlist_image_upload/playlist_custom_thumbnail") {
                ytClient(client, setLogin = true, authState = authState)
                parameter("upload_id", uploadId)
                parameter("upload_protocol", "resumable")
                headers {
                    append("X-Goog-Upload-Command", "upload, finalize")
                    append("X-Goog-Upload-Offset", "0")
                }
                setBody(image)
            }
        }
    }

    suspend fun returnYouTubeDislike(videoId: String): HttpResponse =
        executeTracked("returnyoutubedislike", method = "GET") { http ->
            http.get("https://returnyoutubedislikeapi.com/Votes") {
                parameter("videoId", videoId)
                contentType(ContentType.Application.Json)
            }
        }

    suspend fun initSongUpload(
        filename: String,
        contentLength: Long,
    ): HttpResponse {
        val authState = sessionStore.currentAuthState()
        val authUser = "0"
        return executeTracked("usermusic_upload_start") { http ->
            http.post("https://upload.youtube.com/upload/usermusic/http?authuser=$authUser") {
                headers {
                    append("X-Goog-Upload-Command", "start")
                    append("X-Goog-Upload-Protocol", "resumable")
                    append("X-Goog-Upload-Header-Content-Length", contentLength.toString())
                    append("X-Goog-AuthUser", authUser)
                    append("Origin", YouTubeClient.ORIGIN_YOUTUBE_MUSIC)
                    authState.cookie?.let { cookieValue ->
                        append("cookie", cookieValue)
                        sapisidAuthorizationHeader(cookieValue, YouTubeClient.ORIGIN_YOUTUBE_MUSIC)?.let { auth ->
                            append("Authorization", auth)
                        }
                    }
                }
                contentType(ContentType.Application.FormUrlEncoded)
                setBody("filename=$filename")
            }
        }
    }

    suspend fun uploadSongData(
        uploadUrl: String,
        data: ByteArray,
        onProgress: ((Float) -> Unit)? = null,
    ): HttpResponse {
        val authState = sessionStore.currentAuthState()
        return executeTracked("usermusic_upload_data") { http ->
            http.post(uploadUrl) {
                headers {
                    append("X-Goog-Upload-Command", "upload, finalize")
                    append("X-Goog-Upload-Offset", "0")
                    append("X-Goog-AuthUser", "0")
                    append("Origin", YouTubeClient.ORIGIN_YOUTUBE_MUSIC)
                    authState.cookie?.let { cookieValue ->
                        append("cookie", cookieValue)
                        sapisidAuthorizationHeader(cookieValue, YouTubeClient.ORIGIN_YOUTUBE_MUSIC)?.let { auth ->
                            append("Authorization", auth)
                        }
                    }
                }
                contentType(ContentType.Application.OctetStream)
                setBody(data)
                onUpload { bytesSentTotal, contentLength ->
                    contentLength?.let { total ->
                        if (total > 0) {
                            onProgress?.invoke(bytesSentTotal.toFloat() / total.toFloat())
                        }
                    }
                }
            }
        }
    }

    suspend fun deletePrivatelyOwnedEntity(entityId: String): HttpResponse {
        val authState = sessionStore.currentAuthState()
        val context = YouTubeClient.WEB_REMIX.toContext(sessionStore.locale, authState.visitorData, null)
        val requestBody = """{"context":${Json.encodeToString(context)},"entityId":"$entityId"}"""
        return executeTracked("music/delete_privately_owned_entity") { http ->
            http.post("https://music.youtube.com/youtubei/v1/music/delete_privately_owned_entity") {
                contentType(ContentType.Application.Json)
                headers {
                    append("Referer", YouTubeClient.REFERER_YOUTUBE_MUSIC)
                    append("Origin", YouTubeClient.ORIGIN_YOUTUBE_MUSIC)
                    authState.cookie?.let { cookieValue ->
                        append("cookie", cookieValue)
                        sapisidAuthorizationHeader(cookieValue, YouTubeClient.ORIGIN_YOUTUBE_MUSIC)?.let { auth ->
                            append("Authorization", auth)
                        }
                    }
                }
                parameter("key", YOUTUBE_API_KEY)
                parameter("prettyPrint", false)
                setBody(requestBody)
            }
        }
    }
}
