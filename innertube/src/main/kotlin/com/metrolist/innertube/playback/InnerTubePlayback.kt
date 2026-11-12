package com.metrolist.innertube.playback

import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.YouTubeClient
import com.metrolist.innertube.models.getContinuation
import com.metrolist.innertube.models.response.GetQueueResponse
import com.metrolist.innertube.models.response.NextResponse
import com.metrolist.innertube.models.response.PlayerResponse
import com.metrolist.innertube.network.AuthSessionStore
import com.metrolist.innertube.network.InnerTubeHttp
import com.metrolist.innertube.pages.NextPage
import com.metrolist.innertube.pages.NextResult
import io.ktor.client.call.body
import kotlin.random.Random

class InnerTubePlayback(
    private val http: InnerTubeHttp,
    private val sessionStore: AuthSessionStore = AuthSessionStore,
) {
    suspend fun player(
        videoId: String,
        playlistId: String? = null,
        client: YouTubeClient,
        signatureTimestamp: Int? = null,
        poToken: String? = null,
    ): Result<PlayerResponse> =
        runCatching {
            val authState = sessionStore.currentAuthState()
            val effectivePoToken = authState.resolvePlayerPoToken(client, poToken)
            http.player(
                client = client,
                videoId = videoId,
                playlistId = playlistId,
                signatureTimestamp = signatureTimestamp,
                poToken = effectivePoToken,
            ).body<PlayerResponse>()
        }

    suspend fun registerPlayback(
        playlistId: String? = null,
        playbackTracking: String,
        client: YouTubeClient = YouTubeClient.WEB_REMIX,
    ): Result<Unit> =
        runCatching {
            val cpn = buildString(16) {
                val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"
                repeat(16) {
                    append(alphabet[Random.Default.nextInt(0, alphabet.length)])
                }
            }
            http.registerPlayback(
                url = playbackTracking,
                cpn = cpn,
                playlistId = playlistId,
                client = client,
            )
            Unit
        }

    suspend fun next(
        endpoint: WatchEndpoint,
        continuation: String? = null,
        client: YouTubeClient = YouTubeClient.WEB_REMIX,
    ): Result<NextResult> =
        runCatching {
            val response = http.next(
                client = client,
                videoId = endpoint.videoId,
                playlistId = endpoint.playlistId,
                playlistSetVideoId = endpoint.playlistSetVideoId,
                index = endpoint.index,
                params = endpoint.params,
                continuation = continuation,
            ).body<NextResponse>()

            val tabs = response.contents.singleColumnMusicWatchNextResultsRenderer
                ?.tabbedRenderer
                ?.watchNextTabbedResultsRenderer
                ?.tabs

            val firstTab = tabs?.firstOrNull()

            val playlistPanelRenderer = response.continuationContents?.playlistPanelContinuation
                ?: firstTab?.tabRenderer?.content?.musicQueueRenderer?.content?.playlistPanelRenderer
                ?: throw IllegalStateException("Missing playlistPanelRenderer in next response")

            val title = firstTab
                ?.tabRenderer
                ?.content
                ?.musicQueueRenderer
                ?.header
                ?.musicQueueHeaderRenderer
                ?.subtitle
                ?.runs
                ?.firstOrNull()
                ?.text

            val items = playlistPanelRenderer.contents.mapNotNull { content ->
                content.playlistPanelVideoRenderer
                    ?.let(NextPage::fromPlaylistPanelVideoRenderer)
                    ?.let { song -> song to content.playlistPanelVideoRenderer.selected }
            }
            val songs = items.map { it.first }
            val currentIndex = items.indexOfFirst { it.second }.takeIf { it != -1 }
            val lyricsEndpoint = tabs?.getOrNull(1)?.tabRenderer?.endpoint?.browseEndpoint
            val relatedEndpoint = tabs?.getOrNull(2)?.tabRenderer?.endpoint?.browseEndpoint

            val automixEndpoint = playlistPanelRenderer.contents
                .lastOrNull()
                ?.automixPreviewVideoRenderer
                ?.content
                ?.automixPlaylistVideoRenderer
                ?.navigationEndpoint
                ?.watchPlaylistEndpoint

            if (automixEndpoint != null) {
                val automixResult = next(automixEndpoint, null, client).getOrThrow()
                return@runCatching automixResult.copy(
                    title = title,
                    items = songs + automixResult.items,
                    lyricsEndpoint = lyricsEndpoint,
                    relatedEndpoint = relatedEndpoint,
                    currentIndex = currentIndex,
                    endpoint = automixEndpoint,
                )
            }

            NextResult(
                title = title,
                items = songs,
                currentIndex = currentIndex,
                lyricsEndpoint = lyricsEndpoint,
                relatedEndpoint = relatedEndpoint,
                continuation = playlistPanelRenderer.continuations?.getContinuation(),
                endpoint = endpoint,
            )
        }

    suspend fun queue(
        videoIds: List<String>? = null,
        playlistId: String? = null,
        maxQueueSize: Int = 1000,
    ): Result<List<com.metrolist.innertube.models.SongItem>> =
        runCatching {
            require(videoIds == null || videoIds.size <= maxQueueSize) {
                "Queue size ${videoIds?.size} exceeds maxQueueSize $maxQueueSize"
            }
            http.getQueue(YouTubeClient.WEB_REMIX, videoIds, playlistId)
                .body<GetQueueResponse>()
                .queueDatas
                .mapNotNull { data ->
                    data.content.playlistPanelVideoRenderer?.let(NextPage::fromPlaylistPanelVideoRenderer)
                }
        }
}
