package com.music.spotui.data.api

import android.content.Context
import android.util.Log
import com.metrolist.spotify.Spotify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Fire-and-forget mirroring of local library actions to the user's REAL Spotify
 * account (web-player token): liking a song, saving an album or following an
 * artist here shows up in Spotify itself. Failures are logged, never surfaced —
 * the local action already succeeded.
 */
object SpotifySync {

    private val playlistWrites = ConcurrentHashMap<String, Mutex>()
    private const val TAG = "SpotifySync"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun cleanId(id: String, prefix: String): String =
        id.removePrefix(prefix)
            .removePrefix(prefix.removeSuffix(":"))
            .substringBefore("?")
            .substringBefore("|")
            .trim()

    fun cleanTrackId(id: String): String =
        cleanId(id, "spotify:track:")

    fun cleanPlaylistId(id: String): String =
        cleanId(id, "spotify:playlist:")

    fun setTrackSaved(context: Context, trackId: String, saved: Boolean) {
        val clean = cleanTrackId(trackId)
        setSaved(context, clean, "spotify:track:$clean", saved)
    }

    fun setAlbumSaved(context: Context, albumId: String, saved: Boolean) {
        val clean = cleanId(albumId, "spotify:album:")
        setSaved(context, clean, "spotify:album:$clean", saved)
    }

    fun setArtistFollowed(context: Context, artistId: String, followed: Boolean) {
        val clean = cleanId(artistId, "spotify:artist:")
        setSaved(context, clean, "spotify:artist:$clean", followed)
    }

    private fun setSaved(context: Context, id: String, uri: String, saved: Boolean) {
        if (id.isBlank()) return
        val kind = uri.removePrefix("spotify:").substringBefore(':')
        val session = SpotifySession.spDc(context)
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            playlistWrites.computeIfAbsent(uri) { Mutex() }.withLock {
                SpotifyPendingWrites.enqueue(context.applicationContext, kind, id, saved = saved, expectedSession = session)
            }
        }
    }

    private fun writePlaylist(context: Context, playlistId: String, trackId: String, saved: Boolean, onDone: (Boolean) -> Unit) {
        val app = context.applicationContext
        val session = SpotifySession.spDc(app)
        val mutation = SavedTrackLibrary.optimistic(app, playlistId, trackId, saved)
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            playlistWrites.computeIfAbsent(playlistId) { Mutex() }.withLock {
                val accepted = SpotifyPendingWrites.enqueue(app, "playlist", playlistId, trackId, saved, expectedSession = session)
                if (SpotifySession.spDc(app) == session) {
                    SavedTrackLibrary.settle(mutation, playlistId, trackId, saved, accepted)
                    if (accepted) Api.HomeCache.invalidatePlaylist(playlistId)
                    onDone(accepted)
                } else { SavedTrackLibrary.ensureLoaded(app); onDone(false) }
            }
        }
    }

    fun addTrackToPlaylist(context: Context, playlistId: String, trackId: String, onDone: (Boolean) -> Unit = {}) {
        val id = cleanPlaylistId(playlistId); val track = cleanTrackId(trackId)
        if (id.isBlank() || track.isBlank()) { onDone(false); return }
        writePlaylist(context, id, track, true, onDone)
    }

    fun removeTrackFromPlaylist(context: Context, playlistId: String, trackId: String, onDone: (Boolean) -> Unit = {}) {
        val id = cleanPlaylistId(playlistId); val track = cleanTrackId(trackId)
        if (id.isBlank() || track.isBlank()) { onDone(false); return }
        writePlaylist(context, id, track, false, onDone)
    }

    internal fun pendingPlaylistIds(context: Context, playlistId: String, remote: Set<String>): Set<String> =
        remote.toMutableSet().apply {
            SpotifyPendingWrites.entries(context).filter { it.kind == "playlist" && it.target == playlistId }.forEach {
                if (it.saved) add(it.track) else remove(it.track)
            }
        }

    /** Creates a new playlist on Spotify and adds the track to it. */
    fun createPlaylistWithTrack(context: Context, name: String, trackId: String, public: Boolean = false, onDone: (Boolean) -> Unit = {}) {
        if (name.isBlank()) { onDone(false); return }
        val cleanTr = cleanTrackId(trackId)
        val app = context.applicationContext
        scope.launch {
            if (!SpotifyTokenProvider.ensureToken(app)) { onDone(false); return@launch }
            val playlist = Spotify.createPlaylist(name, public).getOrNull()
            if (playlist == null || playlist.id.isBlank()) {
                Log.w(TAG, "createPlaylist '$name' failed"); onDone(false); return@launch
            }
            val ok = cleanTr.isBlank() ||
                Spotify.addTracksToPlaylist(playlist.id, listOf("spotify:track:$cleanTr")).isSuccess
            if (ok && cleanTr.isNotBlank()) {
                membershipCache[playlist.id] = ConcurrentHashMap.newKeySet<String>().apply { add(cleanTr) }
            }
            invalidatePlaylistCatalog()
            SavedTrackLibrary.created(app, playlist.id, name, if (ok && cleanTr.isNotBlank()) setOf(cleanTr) else emptySet())
            Api.HomeCache.library = null
            onDone(ok)
        }
    }

    // ── Playlist membership (which playlists contain a track) ──
    // Cached per playlist for the app session so the "Saved in" sheet can show
    // check marks without re-fetching on every open.
    private val membershipCache = ConcurrentHashMap<String, MutableSet<String>>()

    private val membershipFetchedAt = ConcurrentHashMap<String, Long>()

    private val catalogWork = com.music.spotui.util.SharedWork<String, List<com.metrolist.spotify.models.SpotifyPlaylist>>(scope)
    private var catalogAccount: String? = null
    private var catalogAt = 0L
    private var catalog = emptyList<com.metrolist.spotify.models.SpotifyPlaylist>()

    @Synchronized
    internal fun invalidatePlaylistCatalog() {
        catalogAccount = null
        catalogAt = 0L
    }

    internal fun cachedPlaylistMembership(context: Context, id: String): Boolean? {
        val clean = cleanPlaylistId(id)
        return SpotifyPendingWrites.desired(context, "playlist_library", clean)
            ?: LibraryCatalogCache.peek(context)?.any { it.isPlaylist && it.spotifyId == clean }
    }

    internal suspend fun playlistMembership(context: Context, id: String): Boolean {
        val clean = cleanPlaylistId(id)
        SpotifyPendingWrites.desired(context, "playlist_library", clean)?.let { return it }
        val saved = allLibraryPlaylists(context).any { it.id == clean }
        return SpotifyPendingWrites.desired(context, "playlist_library", clean) ?: saved
    }

    internal suspend fun savePlaylist(context: Context, id: String): Boolean {
        val clean = cleanPlaylistId(id)
        if (!clean.matches(Regex("[A-Za-z0-9]{22}"))) return false
        val session = SpotifySession.spDc(context)
        return playlistWrites.computeIfAbsent("library:$clean") { Mutex() }.withLock {
            if (!SpotifyPendingWrites.enqueue(context, "playlist_library", clean, saved = true, expectedSession = session)) {
                return@withLock false
            }
            Api.HomeCache.library = null
            com.music.spotui.sync.OwnLibraryChanges.changed()
            true
        }
    }

    internal suspend fun removePlaylistConfirmed(context: Context, id: String) = withContext(Dispatchers.IO) {
        val clean = cleanPlaylistId(id)
        require(clean.matches(Regex("[A-Za-z0-9]{22}")))
        val session = SpotifySession.spDc(context)
        playlistWrites.computeIfAbsent("library:$clean") { Mutex() }.withLock {
            val confirmed = kotlinx.coroutines.withTimeoutOrNull(30_000L) {
                SpotifyPendingWrites.removePlaylistConfirmed(context, session, clean)
                true
            }
            check(SpotifySession.spDc(context) == session) { "La cuenta de Spotify ha cambiado." }
            check(confirmed == true) { "Spotify todavía no ha confirmado la eliminación. Vuelve a intentarlo." }
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                check(SpotifySession.spDc(context) == session) { "La cuenta de Spotify ha cambiado." }
                LibraryCatalogCache.playlistRemovalConfirmed(context, session, clean)
                com.music.spotui.data.preferences.FavoritePlaylistsPref.removeShortcut(context, clean)
                Api.HomeCache.invalidatePlaylist(clean)
                Api.HomeCache.library = null
                com.music.spotui.sync.OwnLibraryChanges.changed()
                com.music.spotui.data.preferences.LocalPlaylistPref.getLocalPlaylists(context)
                    .filter { it.syncedSpotifyId == clean }.forEach { LocalSpotifyMirror.refresh(context, it.id) }
            }
        }
    }

    internal suspend fun writePlaylistMembership(
        context: Context, session: String, userId: String, playlistId: String, saved: Boolean,
    ) {
        suspend fun matches(): Boolean {
            check(SpotifySession.spDc(context) == session) { "La cuenta de Spotify ha cambiado." }
            return allLibraryPlaylists(context, forceRefresh = true).any { it.id == playlistId } == saved
        }
        if (matches()) return
        check(SpotifySession.spDc(context) == session) { "La cuenta de Spotify ha cambiado." }
        Spotify.setPlaylistInLibrary(userId, playlistId, saved).getOrThrow()
        repeat(4) { attempt ->
            if (attempt > 0) kotlinx.coroutines.delay(400L * attempt)
            if (matches()) return
        }
        error("Spotify aún no confirma el cambio en tu biblioteca.")
    }

    /** Shared complete catalog: membership and Library never repeat the same concurrent scan. */
    suspend fun allLibraryPlaylists(context: Context, forceRefresh: Boolean = false): List<com.metrolist.spotify.models.SpotifyPlaylist> {
        val session = SpotifySession.spDc(context)
        synchronized(this) {
            if (!forceRefresh && catalogAccount == session && android.os.SystemClock.elapsedRealtime() - catalogAt in 0..30_000L) return catalog
        }
        return catalogWork.await(session) {
            check(SpotifyTokenProvider.ensureToken(context.applicationContext)) { "Spotify session unavailable" }
            val result = mutableListOf<com.metrolist.spotify.models.SpotifyPlaylist>()
            var offset = 0
            var total: Int? = null
            do {
                val page = Spotify.myPlaylists(limit = 50, offset = offset).getOrThrow()
                check(page.offset == offset && (total == null || total == page.total)) { "Playlist library changed during pagination" }
                total = page.total
                result.addAll(page.items)
                val next = page.offset + page.limit.coerceAtLeast(1)
                check(next > offset) { "Playlist pagination did not advance" }
                offset = next
            } while (offset < page.total)
            val complete = result.distinctBy { it.id }
            check(complete.size == result.size) { "Playlist library changed during pagination" }
            synchronized(this) {
                if (SpotifySession.spDc(context) == session) {
                    catalogAccount = session; catalog = complete; catalogAt = android.os.SystemClock.elapsedRealtime()
                    LibraryCatalogCache.playlistsLoaded(context, session, complete)
                }
            }
            complete
        }
    }

    /** Only complete successful scans are cached, briefly, to allow remote edits. */
    suspend fun playlistTrackIds(context: Context, playlistId: String, forceRefresh: Boolean = false): Set<String> {
        val cleanPl = cleanPlaylistId(playlistId)
        if (cleanPl.isBlank()) return emptySet()
        val age = android.os.SystemClock.elapsedRealtime() - (membershipFetchedAt[cleanPl] ?: 0L)
        if (!forceRefresh && age in 0L..60_000L && membershipFetchedAt.containsKey(cleanPl)) {
            membershipCache[cleanPl]?.let { return pendingPlaylistIds(context, cleanPl, it.toSet()) }
        }
        check(SpotifyTokenProvider.ensureToken(context.applicationContext)) { "Spotify session unavailable" }
        val ids = mutableSetOf<String>()
        var offset = 0
        while (true) {
            val page = Spotify.playlistTracks(cleanPl, limit = 100, offset = offset).getOrThrow()
            page.items.forEach { item ->
                item.track?.id?.let { cleanTrackId(it) }?.takeIf { it.isNotBlank() }?.let(ids::add)
            }
            // The parser can omit unavailable entries: advance by the raw page
            // window, never by the number of playable tracks after filtering.
            val next = page.offset + page.limit.coerceAtLeast(1)
            check(next > offset) { "Track pagination did not advance" }
            offset = next
            if (offset >= page.total) break
        }
        val set = ConcurrentHashMap.newKeySet<String>().apply { addAll(ids) }
        membershipCache[cleanPl] = set
        membershipFetchedAt[cleanPl] = android.os.SystemClock.elapsedRealtime()
        return pendingPlaylistIds(context, cleanPl, set.toSet())
    }
}
