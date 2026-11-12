package com.music.spotui.util

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.navigation.NavController
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.utils.YouTubeUrlParser
import com.metrolist.spotify.Spotify
import com.music.spotui.data.api.SpotifyTokenProvider
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.navigation.Routes
import com.music.spotui.ui.navigation.albumRoute
import com.music.spotui.ui.navigation.artistRoute
import com.music.spotui.ui.navigation.playlistRoute
import com.music.spotui.ui.navigation.showRoute
import com.music.spotui.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Handles incoming Spotify/YouTube deep links, ACTION_SEND shares,
 * MEDIA_PLAY_FROM_SEARCH voice queries, and local audio ACTION_VIEW intents.
 */
object DeepLinkHandler {
    private const val TAG = "DeepLinkHandler"

    sealed class IncomingAction {
        data class DeepLinkUri(val uri: Uri) : IncomingAction()
        data class LocalAudioUri(val uri: Uri, val mimeType: String? = null) : IncomingAction()
        data class SharedText(val text: String) : IncomingAction()
        data class PlayFromSearch(
            val query: String,
            val title: String? = null,
            val artist: String? = null,
            val album: String? = null,
        ) : IncomingAction()
    }

    private val _deepLinkFlow = MutableSharedFlow<Uri>(
        replay = 0,
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val deepLinkFlow = _deepLinkFlow.asSharedFlow()

    private val _incomingActionFlow = MutableSharedFlow<IncomingAction>(
        replay = 0,
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val incomingActionFlow = _incomingActionFlow.asSharedFlow()

    @Volatile
    private var pendingUri: Uri? = null

    @Volatile
    private var pendingAction: IncomingAction? = null

    fun parseIntent(intent: Intent?): IncomingAction? {
        if (intent == null) return null
        val action = intent.action
        val type = intent.type?.lowercase()

        when (action) {
            "android.media.action.MEDIA_PLAY_FROM_SEARCH" -> {
                val query = intent.getStringExtra(SearchManager.QUERY)?.trim().orEmpty()
                val title = intent.getStringExtra(MediaStore.EXTRA_MEDIA_TITLE)?.trim()
                val artist = intent.getStringExtra(MediaStore.EXTRA_MEDIA_ARTIST)?.trim()
                val album = intent.getStringExtra(MediaStore.EXTRA_MEDIA_ALBUM)?.trim()
                val effectiveQuery = query.ifBlank {
                    listOfNotNull(title, artist, album)
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                }
                return IncomingAction.PlayFromSearch(
                    query = effectiveQuery,
                    title = title,
                    artist = artist,
                    album = album,
                )
            }

            Intent.ACTION_SEND -> {
                if (type == null || type.startsWith("text/")) {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
                        ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim()
                    if (!sharedText.isNullOrBlank()) {
                        val extractedUrl = extractFirstSupportedUrl(sharedText)
                        return if (extractedUrl != null) {
                            IncomingAction.DeepLinkUri(Uri.parse(extractedUrl))
                        } else {
                            IncomingAction.SharedText(sharedText)
                        }
                    }
                }
                return null
            }
        }

        val data = intent.data ?: return null
        val scheme = data.scheme?.lowercase()
        val host = data.host?.lowercase()

        // Local audio playback (ACTION_VIEW with audio/* or content/file scheme)
        if (action == Intent.ACTION_VIEW &&
            (type?.startsWith("audio/") == true || scheme == "content" || scheme == "file")
        ) {
            return IncomingAction.LocalAudioUri(data, type)
        }

        val isSpotifyLink = (scheme == "spotify") ||
            (host != null && (host == "open.spotify.com" || host.endsWith(".spotify.com") || host.contains("spotify")))
        val isYouTubeLink = YouTubeUrlParser.isYouTubeUrl(data.toString())

        return if (isSpotifyLink || isYouTubeLink) {
            IncomingAction.DeepLinkUri(data)
        } else {
            null
        }
    }

    fun handleIntent(intent: Intent?) {
        val parsed = parseIntent(intent) ?: return
        Log.d(TAG, "Received intent action: $parsed")
        if (parsed is IncomingAction.DeepLinkUri) {
            pendingUri = parsed.uri
            _deepLinkFlow.tryEmit(parsed.uri)
        }
        pendingAction = parsed
        _incomingActionFlow.tryEmit(parsed)
    }

    fun consumePendingUri(): Uri? {
        val uri = pendingUri
        pendingUri = null
        return uri
    }

    fun consumePendingAction(): IncomingAction? {
        val action = pendingAction
        pendingAction = null
        if (action is IncomingAction.DeepLinkUri) {
            pendingUri = null
        }
        return action
    }

    fun processAction(
        action: IncomingAction,
        context: Context,
        navController: NavController,
        playerViewModel: PlayerViewModel,
        scope: CoroutineScope,
    ) {
        when (action) {
            is IncomingAction.DeepLinkUri ->
                processUri(action.uri, context, navController, playerViewModel, scope)

            is IncomingAction.LocalAudioUri ->
                processLocalAudioUri(action.uri, context, navController, playerViewModel, scope)

            is IncomingAction.SharedText ->
                processPlayFromSearch(
                    query = action.text,
                    title = null,
                    artist = null,
                    context = context,
                    navController = navController,
                    playerViewModel = playerViewModel,
                    scope = scope,
                )

            is IncomingAction.PlayFromSearch ->
                processPlayFromSearch(
                    query = action.query,
                    title = action.title,
                    artist = action.artist,
                    context = context,
                    navController = navController,
                    playerViewModel = playerViewModel,
                    scope = scope,
                )
        }
    }

    fun processUri(
        uri: Uri,
        context: Context,
        navController: NavController,
        playerViewModel: PlayerViewModel,
        scope: CoroutineScope,
    ) {
        scope.launch(Dispatchers.IO) {
            val targetUri = resolveShortenedUriIfNeeded(uri)
            val ytParsed = YouTubeUrlParser.parse(targetUri.toString())
            if (ytParsed != null) {
                processYouTubeUrl(ytParsed, context, navController, playerViewModel)
                return@launch
            }

            val link = SpotifyDeepLink.parse(targetUri)
            if (link == null) {
                Log.w(TAG, "Unrecognized deep link: $targetUri")
                return@launch
            }

            Log.d(TAG, "Processing Spotify deep link: $link")

            when (link) {
                is SpotifyDeepLink.Track -> {
                    val song = resolveTrackInfo(link.id, context)
                    playResolvedSong(song, context, navController, playerViewModel)
                }

                is SpotifyDeepLink.Playlist -> {
                    withContext(Dispatchers.Main) {
                        navController.navigate(playlistRoute(link.id)) {
                            launchSingleTop = true
                        }
                    }
                }

                is SpotifyDeepLink.Album -> {
                    val album = if (SpotifyTokenProvider.ensureToken(context)) {
                        runCatching { Spotify.album(link.id).getOrNull() }.getOrNull()
                    } else null

                    val albumName: String
                    val artistName: String
                    if (album != null) {
                        albumName = album.name
                        artistName = album.artists.firstOrNull()?.name.orEmpty()
                    } else {
                        val webMeta = fetchSpotifyEmbedMeta("album", link.id)
                        if (webMeta != null) {
                            albumName = webMeta.title
                            artistName = webMeta.artist
                        } else {
                            val oembed = fetchOEmbedMetadata("https://open.spotify.com/album/${link.id}")
                            albumName = oembed?.title?.ifBlank { null } ?: link.id
                            artistName = oembed?.authorName.orEmpty()
                        }
                    }

                    withContext(Dispatchers.Main) {
                        navController.navigate(albumRoute(albumName, artistName)) {
                            launchSingleTop = true
                        }
                    }
                }

                is SpotifyDeepLink.Artist -> {
                    val artist = if (SpotifyTokenProvider.ensureToken(context)) {
                        runCatching { Spotify.artist(link.id).getOrNull() }.getOrNull()
                    } else null

                    val artistName = if (artist != null) {
                        artist.name
                    } else {
                        val webMeta = fetchSpotifyEmbedMeta("artist", link.id)
                        if (webMeta != null && webMeta.title.isNotBlank()) {
                            webMeta.title
                        } else {
                            val oembed = fetchOEmbedMetadata("https://open.spotify.com/artist/${link.id}")
                            oembed?.title?.ifBlank { null } ?: "Artist"
                        }
                    }

                    withContext(Dispatchers.Main) {
                        navController.navigate(artistRoute(artistName, link.id)) {
                            launchSingleTop = true
                        }
                    }
                }

                is SpotifyDeepLink.Show -> {
                    val show = if (SpotifyTokenProvider.ensureToken(context)) {
                        runCatching { Spotify.show(link.id).getOrNull() }.getOrNull()
                    } else null

                    val showName = if (show != null) {
                        show.name
                    } else {
                        val webMeta = fetchSpotifyEmbedMeta("show", link.id)
                        if (webMeta != null && webMeta.title.isNotBlank()) {
                            webMeta.title
                        } else {
                            val oembed = fetchOEmbedMetadata("https://open.spotify.com/show/${link.id}")
                            oembed?.title.orEmpty()
                        }
                    }

                    withContext(Dispatchers.Main) {
                        navController.navigate(showRoute(link.id, showName)) {
                            launchSingleTop = true
                        }
                    }
                }

                is SpotifyDeepLink.Episode -> {
                    val song = resolveEpisodeInfo(link.id, context)
                    playResolvedSong(song, context, navController, playerViewModel)
                }
            }
        }
    }

    private suspend fun processYouTubeUrl(
        parsed: YouTubeUrlParser.ParsedUrl,
        context: Context,
        navController: NavController,
        playerViewModel: PlayerViewModel,
    ) {
        when (parsed) {
            is YouTubeUrlParser.ParsedUrl.Video -> {
                val playerInfo = runCatching {
                    YouTube.player(
                        videoId = parsed.id,
                        client = com.metrolist.innertube.models.YouTubeClient.WEB_REMIX,
                    ).getOrNull()
                }.getOrNull()
                val details = playerInfo?.videoDetails
                val song = SongsModel(
                    id = (("yt:" + parsed.id).hashCode() and 0x7fffffff),
                    title = details?.title?.ifBlank { null } ?: "YouTube Track",
                    album = "",
                    singer = details?.author.orEmpty(),
                    coverUri = details?.thumbnail?.thumbnails?.lastOrNull()?.url
                        ?: "https://i.ytimg.com/vi/${parsed.id}/hqdefault.jpg",
                    url = "https://music.youtube.com/watch?v=${parsed.id}",
                    durationMs = (details?.lengthSeconds?.toIntOrNull() ?: 0) * 1000,
                )
                playResolvedSong(song, context, navController, playerViewModel)
            }

            is YouTubeUrlParser.ParsedUrl.Playlist,
            is YouTubeUrlParser.ParsedUrl.Album -> {
                val songs = runCatching {
                    YouTube.playlist(parsed.id).getOrNull()?.songs.orEmpty()
                }.getOrDefault(emptyList())
                val first = songs.firstOrNull()
                if (first != null) {
                    val queue = songs.map { it.toSongsModel() }
                    withContext(Dispatchers.Main) {
                        playerViewModel.updateQueue(queue)
                        val lead = queue.first()
                        playerViewModel.updateSongState(
                            lead.coverUri, lead.title, lead.singer, true, lead.id, 0, lead.album,
                        )
                        SongPlayer.invalidateResolvedStream(lead.url)
                        SongPlayer.playSong(lead.url, context)
                        navigateToPlayer(navController)
                    }
                }
            }

            is YouTubeUrlParser.ParsedUrl.Artist -> {
                withContext(Dispatchers.Main) {
                    navController.navigate(Routes.Search.route) { launchSingleTop = true }
                }
            }
        }
    }

    private fun processLocalAudioUri(
        uri: Uri,
        context: Context,
        navController: NavController,
        playerViewModel: PlayerViewModel,
        scope: CoroutineScope,
    ) {
        scope.launch(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            var title = queryDisplayName(context, uri)?.substringBeforeLast('.') ?: "Local Audio"
            var artist = "Local File"
            var album = ""
            var durationMs = 0
            var coverUri = ""

            runCatching {
                retriever.setDataSource(context, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?.trim()?.takeIf(String::isNotEmpty)?.let { title = it }
                val metaArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?.trim()?.takeIf(String::isNotEmpty)
                    ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                        ?.trim()?.takeIf(String::isNotEmpty)
                if (metaArtist != null) artist = metaArtist
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    ?.trim()?.takeIf(String::isNotEmpty)?.let { album = it }
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toIntOrNull() ?: 0
                retriever.embeddedPicture?.let { artBytes ->
                    val artFile = File(context.cacheDir, "local_art_${uri.toString().hashCode()}.jpg")
                    artFile.writeBytes(artBytes)
                    coverUri = Uri.fromFile(artFile).toString()
                }
            }.onFailure { err ->
                AppDiagnostics.warning(TAG, "Local audio metadata extraction failed for $uri", err)
            }
            runCatching { retriever.release() }

            val song = SongsModel(
                id = (("local:" + uri.toString()).hashCode() and 0x7fffffff),
                title = title,
                album = album,
                singer = artist,
                coverUri = coverUri,
                url = uri.toString(),
                durationMs = durationMs,
            )
            playResolvedSong(song, context, navController, playerViewModel)
        }
    }

    private fun processPlayFromSearch(
        query: String,
        title: String?,
        artist: String?,
        context: Context,
        navController: NavController,
        playerViewModel: PlayerViewModel,
        scope: CoroutineScope,
    ) {
        if (query.isBlank()) {
            scope.launch(Dispatchers.Main) {
                if (playerViewModel.currentSongTitle.value.isNotBlank()) {
                    SongPlayer.play()
                    navigateToPlayer(navController)
                } else {
                    navController.navigate(Routes.Search.route) { launchSingleTop = true }
                }
            }
            return
        }

        scope.launch(Dispatchers.IO) {
            val cleanQuery = listOfNotNull(title?.takeIf(String::isNotBlank), artist?.takeIf(String::isNotBlank))
                .takeIf { it.isNotEmpty() }
                ?.joinToString(" ")
                ?: query

            val spotifyTrack = if (SpotifyTokenProvider.ensureToken(context)) {
                runCatching { Spotify.search(cleanQuery, types = listOf("track"), limit = 5).getOrNull()?.tracks?.items?.firstOrNull() }
                    .getOrNull()
            } else {
                null
            }

            if (spotifyTrack != null) {
                val singer = spotifyTrack.artists.joinToString(", ") { it.name }
                val song = SongsModel(
                    id = (("track:" + spotifyTrack.id).hashCode() and 0x7fffffff),
                    title = spotifyTrack.name.take(128),
                    album = spotifyTrack.album?.name.orEmpty(),
                    singer = singer,
                    coverUri = spotifyTrack.album?.images?.firstOrNull()?.url.orEmpty(),
                    url = SongPlayer.buildSpotifyPlayQuery(spotifyTrack.id, spotifyTrack.name, singer),
                    spotifyTrackId = spotifyTrack.id,
                    explicit = spotifyTrack.explicit,
                    durationMs = spotifyTrack.durationMs,
                    artistIds = spotifyTrack.artists.joinToString(",") { it.id.orEmpty() },
                )
                playResolvedSong(song, context, navController, playerViewModel)
                return@launch
            }

            val ytItems = runCatching {
                YouTube.search(cleanQuery, YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items.orEmpty()
            }.getOrDefault(emptyList())
            val ytSong = ytItems.filterIsInstance<SongItem>().firstOrNull()
            if (ytSong != null) {
                playResolvedSong(ytSong.toSongsModel(), context, navController, playerViewModel)
            } else {
                withContext(Dispatchers.Main) {
                    navController.navigate(Routes.Search.route) { launchSingleTop = true }
                }
            }
        }
    }

    private suspend fun playResolvedSong(
        song: SongsModel,
        context: Context,
        navController: NavController,
        playerViewModel: PlayerViewModel,
    ) {
        withContext(Dispatchers.Main) {
            playerViewModel.updateQueue(listOf(song))
            playerViewModel.updateSongState(
                song.coverUri,
                song.title,
                song.singer,
                true,
                song.id,
                0,
                song.album,
            )
            SongPlayer.invalidateResolvedStream(song.url)
            SongPlayer.playSong(song.url, context)
            navigateToPlayer(navController)
        }
    }

    private fun SongItem.toSongsModel(): SongsModel =
        SongsModel(
            id = (("yt:" + id).hashCode() and 0x7fffffff),
            title = title,
            album = album?.name.orEmpty(),
            singer = artists.joinToString(", ") { it.name },
            coverUri = thumbnail,
            url = "https://music.youtube.com/watch?v=$id",
            explicit = explicit,
            durationMs = (duration ?: 0) * 1000,
        )

    private fun queryDisplayName(context: Context, uri: Uri): String? =
        runCatching {
            if (uri.scheme == "content") {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) return@runCatching cursor.getString(idx)
                    }
                }
            }
            uri.lastPathSegment
        }.getOrNull()

    private fun extractFirstSupportedUrl(text: String): String? {
        val urlRegex = Regex("""(spotify:[a-zA-Z0-9:_/?=&%-]+|https?://[^\s<>"']+)""", RegexOption.IGNORE_CASE)
        return urlRegex.findAll(text)
            .map { it.value.trimEnd('.', ',', ')', ']', '!') }
            .firstOrNull { candidate ->
                candidate.startsWith("spotify:", ignoreCase = true) ||
                    candidate.contains("spotify.com", ignoreCase = true) ||
                    candidate.contains("spotify.link", ignoreCase = true) ||
                    YouTubeUrlParser.isYouTubeUrl(candidate)
            }
    }

    private fun navigateToPlayer(navController: NavController) {
        if (navController.currentDestination?.route != Routes.Player.route) {
            navController.navigate(Routes.Player.route) {
                launchSingleTop = true
            }
        }
    }

    private suspend fun resolveTrackInfo(trackId: String, context: Context): SongsModel {
        if (SpotifyTokenProvider.ensureToken(context)) {
            val track = runCatching { Spotify.track(trackId).getOrNull() }.getOrNull()
            if (track != null) {
                val singer = track.artists.joinToString(", ") { it.name }
                val cover = track.album?.images?.firstOrNull()?.url ?: ""
                return SongsModel(
                    id = (("track:" + trackId).hashCode() and 0x7fffffff),
                    title = track.name.take(128),
                    album = track.album?.name ?: "",
                    singer = singer,
                    coverUri = cover,
                    url = SongPlayer.buildSpotifyPlayQuery(trackId, track.name, singer),
                    spotifyTrackId = trackId,
                    explicit = track.explicit,
                    durationMs = track.durationMs,
                    artistIds = track.artists.joinToString(",") { it.id.orEmpty() },
                )
            }
        }

        val webMeta = fetchSpotifyEmbedMeta("track", trackId)
        if (webMeta != null && webMeta.title.isNotBlank()) {
            return SongsModel(
                id = (("track:" + trackId).hashCode() and 0x7fffffff),
                title = webMeta.title,
                album = "",
                singer = webMeta.artist,
                coverUri = webMeta.coverUri,
                url = SongPlayer.buildSpotifyPlayQuery(trackId, webMeta.title, webMeta.artist),
                spotifyTrackId = trackId,
                explicit = webMeta.explicit,
                durationMs = webMeta.durationMs,
            )
        }

        val oembed = fetchOEmbedMetadata("https://open.spotify.com/track/$trackId")
        val title = oembed?.title?.ifBlank { null } ?: "Track $trackId"
        val singer = oembed?.authorName.orEmpty()
        val cover = oembed?.thumbnailUrl.orEmpty()

        return SongsModel(
            id = (("track:" + trackId).hashCode() and 0x7fffffff),
            title = title,
            album = "",
            singer = singer,
            coverUri = cover,
            url = SongPlayer.buildSpotifyPlayQuery(trackId, title, singer),
            spotifyTrackId = trackId,
        )
    }

    private fun resolveEpisodeInfo(episodeId: String, context: Context): SongsModel {
        val webMeta = fetchSpotifyEmbedMeta("episode", episodeId)
        val title = webMeta?.title?.ifBlank { null } ?: "Podcast Episode"
        val singer = webMeta?.artist.orEmpty()
        val cover = webMeta?.coverUri.orEmpty()

        return SongsModel(
            id = (("episode:$episodeId").hashCode() and 0x7fffffff),
            title = title,
            album = "Podcast",
            singer = singer,
            coverUri = cover,
            url = "episode:$episodeId",
            spotifyTrackId = episodeId,
            durationMs = webMeta?.durationMs ?: 0,
        )
    }

    private data class SpotifyWebMeta(
        val title: String,
        val artist: String,
        val coverUri: String,
        val durationMs: Int = 0,
        val explicit: Boolean = false,
    )

    private fun fetchSpotifyEmbedMeta(type: String, id: String): SpotifyWebMeta? {
        return try {
            val url = "https://open.spotify.com/embed/$type/$id"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")

            if (conn.responseCode == 200) {
                val html = conn.inputStream.bufferedReader().use { it.readText() }
                val scriptMatch = Regex("""<script[^>]*id=["']__NEXT_DATA__["'][^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL).find(html)
                if (scriptMatch != null) {
                    val jsonText = scriptMatch.groupValues[1]
                    val root = JSONObject(jsonText)
                    val props = root.optJSONObject("props")
                    val pageProps = props?.optJSONObject("pageProps")
                    val state = pageProps?.optJSONObject("state")
                    val data = state?.optJSONObject("data")
                    val entity = data?.optJSONObject("entity")
                    if (entity != null) {
                        val title = entity.optString("title", "").ifBlank { entity.optString("name", "") }

                        val artistsArray = entity.optJSONArray("artists")
                        val artistList = mutableListOf<String>()
                        if (artistsArray != null) {
                            for (i in 0 until artistsArray.length()) {
                                val aObj = artistsArray.optJSONObject(i)
                                val name = aObj?.optString("name", "")
                                if (!name.isNullOrBlank()) artistList.add(name)
                            }
                        }
                        val artist = artistList.joinToString(", ")
                        val durationMs = entity.optInt("duration", 0)
                        val isExplicit = entity.optBoolean("isExplicit", false)

                        var coverUri = ""
                        val visualIdentity = entity.optJSONObject("visualIdentity")
                        val images = visualIdentity?.optJSONArray("image")
                        if (images != null && images.length() > 0) {
                            coverUri = images.optJSONObject(0)?.optString("url", "").orEmpty()
                        }
                        if (coverUri.isBlank()) {
                            val coverArt = entity.optJSONObject("coverArt")
                            val sources = coverArt?.optJSONArray("sources")
                            if (sources != null && sources.length() > 0) {
                                coverUri = sources.optJSONObject(0)?.optString("url", "").orEmpty()
                            }
                        }

                        if (title.isNotBlank()) {
                            return SpotifyWebMeta(
                                title = title,
                                artist = artist,
                                coverUri = coverUri,
                                durationMs = durationMs,
                                explicit = isExplicit,
                            )
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch Spotify embed meta for $type/$id", e)
            null
        }
    }

    private data class OEmbedMetadata(
        val title: String,
        val authorName: String,
        val thumbnailUrl: String,
    )

    private fun fetchOEmbedMetadata(spotifyUrl: String): OEmbedMetadata? {
        return try {
            val encodedUrl = URLEncoder.encode(spotifyUrl, "UTF-8")
            val oembedEndpoint = "https://open.spotify.com/oembed?url=$encodedUrl"
            val conn = URL(oembedEndpoint).openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            if (conn.responseCode == 200) {
                val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonText)
                val title = json.optString("title", "").trim()
                val author = json.optString("author_name", "").trim()
                val thumbnail = json.optString("thumbnail_url", "").trim()
                if (title.isNotBlank() || author.isNotBlank()) {
                    OEmbedMetadata(title, author, thumbnail)
                } else null
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch oEmbed metadata for $spotifyUrl", e)
            null
        }
    }

    private fun resolveShortenedUriIfNeeded(uri: Uri): Uri {
        val host = uri.host?.lowercase() ?: return uri
        if (host.contains("link")) {
            try {
                val conn = URL(uri.toString()).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                conn.requestMethod = "HEAD"
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (!location.isNullOrBlank()) {
                    return Uri.parse(location)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to resolve redirect for $uri", e)
            }
        }
        return uri
    }
}
