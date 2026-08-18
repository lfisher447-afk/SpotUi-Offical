package com.music.spotui.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Version 1.5.1 - TrashBinManager
 * Persistent blacklist manager that automatically skips blocked tracks, 
 * artists, or explicit genres instantly.
 */
object TrashBinManager {
    
    // In-memory set of blacklisted Spotify Track IDs
    private val _blacklistedTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val blacklistedTrackIds: StateFlow<Set<String>> = _blacklistedTrackIds

    // In-memory set of blacklisted Artist IDs
    private val _blacklistedArtistIds = MutableStateFlow<Set<String>>(emptySet())
    val blacklistedArtistIds: StateFlow<Set<String>> = _blacklistedArtistIds

    fun blacklistTrack(trackId: String) {
        if (trackId.isBlank()) return
        _blacklistedTrackIds.value = _blacklistedTrackIds.value + trackId
    }

    fun removeTrackFromBlacklist(trackId: String) {
        _blacklistedTrackIds.value = _blacklistedTrackIds.value - trackId
    }

    fun isTrackBlacklisted(trackId: String): Boolean {
        return _blacklistedTrackIds.value.contains(trackId)
    }

    fun blacklistArtist(artistId: String) {
        if (artistId.isBlank()) return
        _blacklistedArtistIds.value = _blacklistedArtistIds.value + artistId
    }

    fun removeArtistFromBlacklist(artistId: String) {
        _blacklistedArtistIds.value = _blacklistedArtistIds.value - artistId
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
