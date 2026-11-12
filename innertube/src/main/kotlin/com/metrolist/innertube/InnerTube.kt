package com.metrolist.innertube

import com.metrolist.innertube.models.MediaInfo
import com.metrolist.innertube.models.ReturnYouTubeDislikeResponse
import com.metrolist.innertube.models.YouTubeClient
import com.metrolist.innertube.models.YouTubeLocale
import com.metrolist.innertube.models.response.NextResponse
import com.metrolist.innertube.network.AuthSessionStore
import com.metrolist.innertube.network.InnerTubeHttp
import com.metrolist.innertube.network.InnerTubeTimingHook
import com.metrolist.innertube.network.PlaybackAuthState
import io.ktor.client.call.body
import java.net.Proxy

/**
 * Facade providing InnerTube endpoint access backed by [AuthSessionStore] and [InnerTubeHttp].
 */
class InnerTube(
    internal val http: InnerTubeHttp = InnerTubeHttp(AuthSessionStore),
) {
    var locale: YouTubeLocale
        get() = AuthSessionStore.locale
        set(value) {
            AuthSessionStore.locale = value
        }

    var visitorData: String?
        get() = AuthSessionStore.visitorData
        set(value) {
            AuthSessionStore.visitorData = value
        }

    var dataSyncId: String?
        get() = AuthSessionStore.dataSyncId
        set(value) {
            AuthSessionStore.dataSyncId = value
        }

    var cookie: String?
        get() = AuthSessionStore.cookie
        set(value) {
            AuthSessionStore.cookie = value
        }

    var poToken: String?
        get() = AuthSessionStore.poToken
        set(value) {
            AuthSessionStore.poToken = value
        }

    var poTokenGvs: String?
        get() = AuthSessionStore.poTokenGvs
        set(value) {
            AuthSessionStore.poTokenGvs = value
        }

    var poTokenPlayer: String?
        get() = AuthSessionStore.poTokenPlayer
        set(value) {
            AuthSessionStore.poTokenPlayer = value
        }

    var webClientPoTokenEnabled: Boolean
        get() = AuthSessionStore.webClientPoTokenEnabled
        set(value) {
            AuthSessionStore.webClientPoTokenEnabled = value
        }

    var proxy: Proxy?
        get() = AuthSessionStore.proxy
        set(value) {
            AuthSessionStore.proxy = value
        }

    var proxyAuth: String?
        get() = AuthSessionStore.proxyAuth
        set(value) {
            AuthSessionStore.proxyAuth = value
        }

    var useLoginForBrowse: Boolean
        get() = AuthSessionStore.useLoginForBrowse
        set(value) {
            AuthSessionStore.useLoginForBrowse = value
        }

    var timingHook: InnerTubeTimingHook?
        get() = http.timingHook
        set(value) {
            http.timingHook = value
        }

    fun applyAuthState(state: PlaybackAuthState): PlaybackAuthState = AuthSessionStore.applyAuthState(state)

    fun invalidateClient() = http.invalidateClient()

    suspend fun search(
        client: YouTubeClient,
        query: String? = null,
        params: String? = null,
        continuation: String? = null,
    ) = http.search(client, query, params, continuation)

    suspend fun player(
        client: YouTubeClient,
        videoId: String,
        playlistId: String?,
        signatureTimestamp: Int?,
        poToken: String? = null,
    ) = http.player(client, videoId, playlistId, signatureTimestamp, poToken)

    suspend fun registerPlayback(
        url: String,
        cpn: String,
        playlistId: String?,
        client: YouTubeClient = YouTubeClient.WEB_REMIX,
    ) = http.registerPlayback(url, cpn, playlistId, client)

    suspend fun browse(
        client: YouTubeClient,
        browseId: String? = null,
        params: String? = null,
        continuation: String? = null,
        setLogin: Boolean = false,
    ) = http.browse(client, browseId, params, continuation, setLogin)

    suspend fun next(
        client: YouTubeClient,
        videoId: String?,
        playlistId: String?,
        playlistSetVideoId: String?,
        index: Int?,
        params: String?,
        continuation: String? = null,
    ) = http.next(client, videoId, playlistId, playlistSetVideoId, index, params, continuation)

    suspend fun feedback(
        client: YouTubeClient,
        tokens: List<String>,
    ) = http.feedback(client, tokens)

    suspend fun getSearchSuggestions(
        client: YouTubeClient,
        input: String,
    ) = http.getSearchSuggestions(client, input)

    suspend fun getQueue(
        client: YouTubeClient,
        videoIds: List<String>?,
        playlistId: String?,
    ) = http.getQueue(client, videoIds, playlistId)

    suspend fun getTranscript(
        client: YouTubeClient,
        videoId: String,
    ) = http.getTranscript(client, videoId)

    suspend fun getSwJsData() = http.getSwJsData()

    suspend fun accountMenu(client: YouTubeClient) = http.accountMenu(client)

    suspend fun likeVideo(
        client: YouTubeClient,
        videoId: String,
    ) = http.likeVideo(client, videoId)

    suspend fun unlikeVideo(
        client: YouTubeClient,
        videoId: String,
    ) = http.unlikeVideo(client, videoId)

    suspend fun subscribeChannel(
        client: YouTubeClient,
        channelId: String,
        params: String? = null,
    ) = http.subscribeChannel(client, channelId, params)

    suspend fun unsubscribeChannel(
        client: YouTubeClient,
        channelId: String,
        params: String? = null,
    ) = http.unsubscribeChannel(client, channelId, params)

    suspend fun likePlaylist(
        client: YouTubeClient,
        playlistId: String,
    ) = http.likePlaylist(client, playlistId)

    suspend fun unlikePlaylist(
        client: YouTubeClient,
        playlistId: String,
    ) = http.unlikePlaylist(client, playlistId)

    suspend fun addToPlaylist(
        client: YouTubeClient,
        playlistId: String,
        videoId: String,
    ) = http.addToPlaylist(client, playlistId, videoId)

    suspend fun addPlaylistToPlaylist(
        client: YouTubeClient,
        playlistId: String,
        addPlaylistId: String,
    ) = http.addPlaylistToPlaylist(client, playlistId, addPlaylistId)

    suspend fun removeFromPlaylist(
        client: YouTubeClient,
        playlistId: String,
        videoId: String,
        setVideoId: String,
    ) = http.removeFromPlaylist(client, playlistId, videoId, setVideoId)

    suspend fun moveSongPlaylist(
        client: YouTubeClient,
        playlistId: String,
        setVideoId: String,
        successorSetVideoId: String?,
    ) = http.moveSongPlaylist(client, playlistId, setVideoId, successorSetVideoId)

    suspend fun createPlaylist(
        client: YouTubeClient,
        title: String,
    ) = http.createPlaylist(client, title)

    suspend fun renamePlaylist(
        client: YouTubeClient,
        playlistId: String,
        name: String,
    ) = http.renamePlaylist(client, playlistId, name)

    suspend fun getUploadCustomThumbnailLink(
        client: YouTubeClient,
        contentLength: Int,
    ) = http.getUploadCustomThumbnailLink(client, contentLength)

    suspend fun uploadCustomThumbnail(
        client: YouTubeClient,
        uploadId: String,
        image: ByteArray,
    ) = http.uploadCustomThumbnail(client, uploadId, image)

    suspend fun setThumbnailPlaylist(
        client: YouTubeClient,
        playlistId: String,
        blobId: String,
    ) = http.setThumbnailPlaylist(client, playlistId, blobId)

    suspend fun removeThumbnailPlaylist(
        client: YouTubeClient,
        playlistId: String,
    ) = http.removeThumbnailPlaylist(client, playlistId)

    suspend fun deletePlaylist(
        client: YouTubeClient,
        playlistId: String,
    ) = http.deletePlaylist(client, playlistId)

    suspend fun initSongUpload(
        filename: String,
        contentLength: Long,
    ) = http.initSongUpload(filename, contentLength)

    suspend fun uploadSongData(
        uploadUrl: String,
        data: ByteArray,
        onProgress: ((Float) -> Unit)? = null,
    ) = http.uploadSongData(uploadUrl, data, onProgress)

    suspend fun deletePrivatelyOwnedEntity(entityId: String) = http.deletePrivatelyOwnedEntity(entityId)

    suspend fun getMediaInfo(videoId: String): Result<MediaInfo> =
        runCatching {
            val response = next(client = YouTubeClient.WEB, videoId, null, null, null, null, null).body<NextResponse>()

            val baseForInfo =
                response.contents.twoColumnWatchNextResults
                    ?.results
                    ?.results
                    ?.content
                    ?.find { it?.videoSecondaryInfoRenderer != null }
                    ?.videoSecondaryInfoRenderer

            val baseForTitle =
                response.contents.twoColumnWatchNextResults
                    ?.results
                    ?.results
                    ?.content
                    ?.find { it?.videoPrimaryInfoRenderer != null }
                    ?.videoPrimaryInfoRenderer

            val returnYouTubeDislikeResponse =
                http.returnYouTubeDislike(videoId).body<ReturnYouTubeDislikeResponse>()

            MediaInfo(
                videoId = videoId,
                title = baseForTitle?.title?.runs?.firstOrNull()?.text,
                author = baseForInfo?.owner?.videoOwnerRenderer?.title?.runs?.firstOrNull()?.text,
                authorId = baseForInfo?.owner?.videoOwnerRenderer?.navigationEndpoint?.browseEndpoint?.browseId,
                authorThumbnail = baseForInfo?.owner?.videoOwnerRenderer?.thumbnail?.thumbnails
                    ?.find { it.height == 48 }
                    ?.url
                    ?.replace("s48", "s960"),
                description = baseForInfo?.attributedDescription?.content,
                subscribers = baseForInfo?.owner?.videoOwnerRenderer?.subscriberCountText?.simpleText?.split(" ")?.firstOrNull(),
                uploadDate = baseForTitle?.dateText?.simpleText,
                viewCount = returnYouTubeDislikeResponse.viewCount,
                like = returnYouTubeDislikeResponse.likes,
                dislike = returnYouTubeDislikeResponse.dislikes,
            )
        }
}
