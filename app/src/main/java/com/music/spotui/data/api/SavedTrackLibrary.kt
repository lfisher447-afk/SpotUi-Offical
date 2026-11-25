package com.music.spotui.data.api

import android.content.Context

object SavedTrackLibrary {
    data class Mutation(val id: String)
    fun optimistic(context: Context, playlistId: String, trackId: String, saved: Boolean): Mutation = Mutation(playlistId)
    fun settle(mutation: Mutation, playlistId: String, trackId: String, saved: Boolean, accepted: Boolean) {}
    fun ensureLoaded(context: Context) {}
    fun created(context: Context, playlistId: String, name: String, tracks: Set<String>) {}
}
