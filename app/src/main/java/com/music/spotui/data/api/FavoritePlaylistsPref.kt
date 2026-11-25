package com.music.spotui.data.api

import android.content.Context

object FavoritePlaylistsPref {
    fun isFavorite(context: Context, playlistId: String): Boolean = false
    fun setFavorite(context: Context, playlistId: String, favorite: Boolean) {}
}
