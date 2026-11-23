package com.music.spotui.data.api

import android.content.Context
import android.util.Log
import com.metrolist.spotify.Spotify
import com.music.spotui.util.DevConsoleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Mirrors local library actions to the user's real Spotify account and performs
 * full native account library synchronization (`Sync All`) into SpotUI's Home,
 * Library, Liked Songs, and Followed Artists caches.
 */
object SpotifySync {

    private const val TAG = "SpotifySync"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    data class SpotifySyncSummary(
        val playlistsCount: Int,
        val albumsCount: Int,
        val likedSongsCount: Int,
        val artistsCount: Int,
        val detail: String,
    )

    private val _syncRevision = MutableStateFlow(0)
    val syncRevision: StateFlow<Int> = _syncRevision.asStateFlow()

    @Volatile
    var lastSyncDetail: String = ""
        private set

    fun isSessionReady(context: Context): Boolean =
        SpotifySession.spDc(context.applicationContext).isNotBlank()

    suspend fun syncAllFromSession(context: Context): Result<SpotifySyncSummary> = runCatching {
        val app = context.applicationContext
        val okToken = SpotifyTokenProvider.ensureToken(app)
        require(okToken) { "Sign in to Spotify first so a valid session token is active." }

        Api.HomeCache.clear()
        val api = Api(app)

        var playlistsCount = 0
        var albumsCount = 0
        var likedCount = 0
        var artistsCount = 0

        coroutineScope {
            val libJob = async(Dispatchers.IO) {
                var finalEntries = emptyList<com.music.spotui.data.entity.LibraryEntry>()
                api.getLibrary().collect { res ->
                    if (res is Response.Success) {
                        finalEntries = res.data.orEmpty()
                    }
                }
                playlistsCount = finalEntries.count {
                    it.isPlaylist && it.spotifyId != Api.LIKED_SONGS_ID && it.spotifyId != Api.DOWNLOADS_ID
                }
                albumsCount = finalEntries.count { !it.isPlaylist }
            }
            val likedJob = async(Dispatchers.IO) {
                api.getLikedSongs().collect { res ->
                    if (res is Response.Success) {
                        likedCount = res.data.orEmpty().size
                    }
                }
            }
            val artistsJob = async(Dispatchers.IO) {
                artistsCount = runCatching { api.getFollowedArtists().size }.getOrDefault(0)
            }
            val homeJob = async(Dispatchers.IO) {
                runCatching { api.getHomeFeed().collect {} }
            }
            val newAlbumsJob = async(Dispatchers.IO) {
                runCatching { api.getAlbums().collect {} }
            }
            val topArtistsJob = async(Dispatchers.IO) {
                runCatching { api.getArtists().collect {} }
            }
            libJob.await()
            likedJob.await()
            artistsJob.await()
            homeJob.await()
            newAlbumsJob.await()
            topArtistsJob.await()
        }

        val summaryText =
            "Synced $playlistsCount playlists · $albumsCount albums · $likedCount liked · $artistsCount artists"
        lastSyncDetail = summaryText
        _syncRevision.value = _syncRevision.value + 1
        DevConsoleManager.logAuth(summaryText)
        SpotifySyncSummary(
            playlistsCount = playlistsCount,
            albumsCount = albumsCount,
            likedSongsCount = likedCount,
            artistsCount = artistsCount,
            detail = summaryText,
        )
    }.onFailure { err ->
        DevConsoleManager.logWarning("SpotifySync", "Full Spotify sync failed: ${err.message}")
    }

    fun setTrackSaved(context: Context, trackId: String, saved: Boolean) =
        setSaved(context, trackId, "spotify:track:$trackId", saved)

    fun setAlbumSaved(context: Context, albumId: String, saved: Boolean) =
        setSaved(context, albumId, "spotify:album:$albumId", saved)

    fun setArtistFollowed(context: Context, artistId: String, followed: Boolean) =
        setSaved(context, artistId, "spotify:artist:$artistId", followed)

    private fun setSaved(context: Context, id: String, uri: String, saved: Boolean) {
        if (id.isBlank()) return
        val app = context.applicationContext
        scope.launch {
            if (!SpotifyTokenProvider.ensureToken(app)) {
                Log.w(TAG, "no token — skipped syncing $uri saved=$saved")
                return@launch
            }
            val result =
                if (saved) Spotify.addToLibrary(listOf(uri))
                else Spotify.removeFromLibrary(listOf(uri))
            result.fold(
                onSuccess = { Log.d(TAG, "synced $uri saved=$saved") },
                onFailure = { Log.w(TAG, "failed syncing $uri saved=$saved", it) },
            )
        }
    }

    /** Adds a track to one of the user's playlists on Spotify itself. */
    fun addTrackToPlaylist(context: Context, playlistId: String, trackId: String, onDone: (Boolean) -> Unit = {}) {
        if (playlistId.isBlank() || trackId.isBlank()) { onDone(false); return }
        val app = context.applicationContext
        scope.launch {
            val ok = SpotifyTokenProvider.ensureToken(app) &&
                Spotify.addTracksToPlaylist(playlistId, listOf("spotify:track:$trackId")).isSuccess
            if (ok) membershipCache[playlistId]?.add(trackId)
            if (!ok) Log.w(TAG, "failed adding track $trackId to playlist $playlistId")
            onDone(ok)
        }
    }

    /** Removes every occurrence of a track from one of the user's playlists on
     *  Spotify itself (resolves the playlist-scoped item uids first). */
    fun removeTrackFromPlaylist(context: Context, playlistId: String, trackId: String, onDone: (Boolean) -> Unit = {}) {
        if (playlistId.isBlank() || trackId.isBlank()) { onDone(false); return }
        val app = context.applicationContext
        scope.launch {
            if (!SpotifyTokenProvider.ensureToken(app)) { onDone(false); return@launch }
            val uri = "spotify:track:$trackId"
            val refs = Spotify.playlistTracks(playlistId, limit = 100).getOrNull()
                ?.items.orEmpty()
                .filter { it.track?.id == trackId || it.track?.uri == uri }
                .mapNotNull { pt -> pt.uid?.let { Spotify.PlaylistItemRef(uri = uri, uid = it) } }
            val ok = refs.isNotEmpty() &&
                Spotify.removeTracksFromPlaylist(playlistId, refs).isSuccess
            if (ok) membershipCache[playlistId]?.remove(trackId)
            if (!ok) Log.w(TAG, "failed removing track $trackId from playlist $playlistId")
            onDone(ok)
        }
    }

    /** Creates a new playlist on Spotify and adds the track to it. */
    fun createPlaylistWithTrack(context: Context, name: String, trackId: String, public: Boolean = false, onDone: (Boolean) -> Unit = {}) {
        if (name.isBlank()) { onDone(false); return }
        val app = context.applicationContext
        scope.launch {
            if (!SpotifyTokenProvider.ensureToken(app)) { onDone(false); return@launch }
            val playlist = Spotify.createPlaylist(name, public).getOrNull()
            if (playlist == null || playlist.id.isBlank()) {
                Log.w(TAG, "createPlaylist '$name' failed"); onDone(false); return@launch
            }
            val ok = trackId.isBlank() ||
                Spotify.addTracksToPlaylist(playlist.id, listOf("spotify:track:$trackId")).isSuccess
            if (ok && trackId.isNotBlank()) {
                membershipCache[playlist.id] = mutableSetOf(trackId)
            }
            onDone(ok)
        }
    }

    // ── Playlist membership (which playlists contain a track) ──
    // Cached per playlist for the app session so the "Saved in" sheet can show
    // check marks without re-fetching on every open.
    private val membershipCache = ConcurrentHashMap<String, MutableSet<String>>()

    /** Track ids contained in [playlistId] (first call fetches, then cached). */
    suspend fun playlistTrackIds(context: Context, playlistId: String): Set<String> {
        membershipCache[playlistId]?.let { return it }
        if (!SpotifyTokenProvider.ensureToken(context.applicationContext)) return emptySet()
        val ids = Spotify.playlistTracks(playlistId, limit = 100).getOrNull()
            ?.items.orEmpty()
            .mapNotNull { it.track?.id?.ifBlank { null } }
            .toMutableSet()
        val set = ConcurrentHashMap.newKeySet<String>().apply { addAll(ids) }
        membershipCache[playlistId] = set
        return set
    }
}
