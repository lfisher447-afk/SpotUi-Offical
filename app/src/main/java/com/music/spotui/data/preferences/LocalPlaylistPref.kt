package com.music.spotui.data.preferences

import android.content.Context
import com.music.spotui.data.entity.SongsModel

data class LocalPlaylist(
    val id: String,
    val name: String,
    val songs: List<SongsModel> = emptyList(),
    val coverUri: String = "",
    val syncedSpotifyId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

object LocalPlaylistPref {
    fun getLocalPlaylists(context: Context): List<LocalPlaylist> = emptyList()
    fun getLocalPlaylist(context: Context, id: String): LocalPlaylist? = null
    fun savePlaylist(context: Context, playlist: LocalPlaylist) {}
}
