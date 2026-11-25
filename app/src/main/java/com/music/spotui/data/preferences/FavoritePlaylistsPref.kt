package com.music.spotui.data.preferences

import android.content.Context

object FavoritePlaylistsPref {
    fun isFavorite(context: Context, playlistId: String): Boolean = false
    fun setFavorite(context: Context, playlistId: String, favorite: Boolean) {}
    fun removeShortcut(context: Context, playlistId: String) {}
}
