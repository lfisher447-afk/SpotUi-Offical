package com.music.spotui.engine

import android.content.Context
import androidx.room.Room
import com.music.spotui.data.dao.TrashBinDao
import com.music.spotui.data.db.AppDatabase
import com.music.spotui.data.entity.TrashBinEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Version 1.5.1 - TrashBinManager
 * Persistent blacklist manager that automatically skips blocked tracks, 
 * artists, or explicit genres instantly with Room Database backing.
 */
object TrashBinManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var dao: TrashBinDao? = null

    // In-memory set of blacklisted Spotify Track IDs
    private val _blacklistedTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val blacklistedTrackIds: StateFlow<Set<String>> = _blacklistedTrackIds.asStateFlow()

    // In-memory set of blacklisted Artist IDs
    private val _blacklistedArtistIds = MutableStateFlow<Set<String>>(emptySet())
    val blacklistedArtistIds: StateFlow<Set<String>> = _blacklistedArtistIds.asStateFlow()

    fun initialize(context: Context, trashBinDao: TrashBinDao? = null) {
        val resolvedDao = trashBinDao ?: runCatching {
            AppDatabase.getInstance(context).trashBinDao()
        }.getOrNull()

        dao = resolvedDao
        if (resolvedDao != null) {
            scope.launch {
                runCatching {
                    val tracks = resolvedDao.getAllBlacklistedTrackIds().toSet()
                    val artists = resolvedDao.getAllBlacklistedArtistIds().toSet()
                    _blacklistedTrackIds.value = tracks
                    _blacklistedArtistIds.value = artists
                }
            }
        }
    }

    fun blacklistTrack(trackId: String, name: String = "") {
        if (trackId.isBlank()) return
        _blacklistedTrackIds.value = _blacklistedTrackIds.value + trackId
        dao?.let { d ->
            scope.launch {
                runCatching {
                    d.insertItem(TrashBinEntity(id = trackId, type = "track", name = name))
                }
            }
        }
    }

    fun removeTrackFromBlacklist(trackId: String) {
        _blacklistedTrackIds.value = _blacklistedTrackIds.value - trackId
        dao?.let { d ->
            scope.launch {
                runCatching { d.deleteItem(trackId) }
            }
        }
    }

    fun isTrackBlacklisted(trackId: String): Boolean {
        return _blacklistedTrackIds.value.contains(trackId)
    }

    fun blacklistArtist(artistId: String, name: String = "") {
        if (artistId.isBlank()) return
        _blacklistedArtistIds.value = _blacklistedArtistIds.value + artistId
        dao?.let { d ->
            scope.launch {
                runCatching {
                    d.insertItem(TrashBinEntity(id = artistId, type = "artist", name = name))
                }
            }
        }
    }

    fun removeArtistFromBlacklist(artistId: String) {
        _blacklistedArtistIds.value = _blacklistedArtistIds.value - artistId
        dao?.let { d ->
            scope.launch {
                runCatching { d.deleteItem(artistId) }
            }
        }
    }

    fun isArtistBlacklisted(artistId: String): Boolean {
        return _blacklistedArtistIds.value.contains(artistId)
    }

    /**
     * Helper to check if a track should be skipped, based on track ID or artist ID.
     */
    fun shouldSkip(trackId: String, artistId: String = ""): Boolean {
        return isTrackBlacklisted(trackId) || (artistId.isNotBlank() && isArtistBlacklisted(artistId))
    }
}
