package com.music.spotui.data.api

import android.content.Context
import com.metrolist.spotify.models.SpotifyPlaylist

object LibraryCatalogCache {
    private var cachedList: List<SpotifyPlaylist>? = null

    fun invalidatePlaylist(context: Context, playlistId: String) {
        cachedList = null
    }

    fun peek(context: Context): List<SpotifyPlaylist>? = cachedList

    fun playlistRemovalConfirmed(context: Context, session: String, clean: String) {
        cachedList = cachedList?.filterNot { it.id == clean }
    }

    fun playlistsLoaded(context: Context, session: String, list: List<SpotifyPlaylist>) {
        cachedList = list
    }
}
