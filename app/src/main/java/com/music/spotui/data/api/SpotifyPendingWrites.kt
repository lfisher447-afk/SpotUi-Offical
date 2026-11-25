package com.music.spotui.data.api

import android.content.Context

data class PendingWriteEntry(
    val kind: String,
    val target: String,
    val trackId: String? = null,
    val track: String = trackId.orEmpty(),
    val saved: Boolean = true
)

object SpotifyPendingWrites {
    fun enqueue(context: Context, kind: String, target: String, trackId: String? = null, saved: Boolean = true, expectedSession: String? = null): Boolean {
        return true
    }
    fun enqueue(context: Context, kind: String, id: String, saved: Boolean = true, expectedSession: String? = null): Boolean {
        return true
    }
    fun entries(context: Context): List<PendingWriteEntry> = emptyList()
    fun desired(context: Context, kind: String, target: String): Any? = null
    fun removePlaylistConfirmed(context: Context, session: String?, target: String) {}
}
