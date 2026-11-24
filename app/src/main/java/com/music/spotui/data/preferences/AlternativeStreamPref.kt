package com.music.spotui.data.preferences

import android.content.Context
import android.net.Uri
import com.music.spotui.data.entity.SongsModel
import org.json.JSONObject

data class AlternativeStream(
    val type: String,
    val value: String,
    val label: String = "",
) {
    val isYouTube: Boolean get() = type == TYPE_YOUTUBE
    val isLocal: Boolean get() = type == TYPE_LOCAL

    companion object {
        const val TYPE_YOUTUBE = "youtube"
        const val TYPE_LOCAL = "local"
    }
}

private const val PREF = "AlternativeStreams"

fun alternativeStreamKey(song: SongsModel): String =
    song.spotifyTrackId.takeIf { it.isNotBlank() }?.let { "spotify:$it" } ?: "song:${song.id}"

fun alternativeStreamKeyForSpotifyId(spotifyTrackId: String): String =
    "spotify:$spotifyTrackId"

private fun AlternativeStream.toJson(): String = JSONObject().apply {
    put("type", type)
    put("value", value)
    put("label", label)
}.toString()

private fun parseAlternativeStream(raw: String): AlternativeStream? = runCatching {
    val obj = JSONObject(raw)
    AlternativeStream(
        type = obj.optString("type"),
        value = obj.optString("value"),
        label = obj.optString("label"),
    ).takeIf { it.value.isNotBlank() && (it.isYouTube || it.isLocal) }
}.getOrNull()

fun getAlternativeStream(context: Context, key: String): AlternativeStream? =
    context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        .getString(key, null)
        ?.let(::parseAlternativeStream)

fun setYouTubeAlternativeStream(context: Context, key: String, videoId: String) {
    val stream = AlternativeStream(
        type = AlternativeStream.TYPE_YOUTUBE,
        value = videoId,
        label = "YouTube video",
    )
    context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        .edit()
        .putString(key, stream.toJson())
        .apply()
}

fun setLocalAlternativeStream(context: Context, key: String, uri: Uri, label: String = "") {
    val stream = AlternativeStream(
        type = AlternativeStream.TYPE_LOCAL,
        value = uri.toString(),
        label = label.ifBlank { uri.lastPathSegment.orEmpty() },
    )
    context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        .edit()
        .putString(key, stream.toJson())
        .apply()
}

fun clearAlternativeStream(context: Context, key: String) {
    context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        .edit()
        .remove(key)
        .apply()
}

/**
 * Search filter preference for Alternative Stream resolution and YouTube picker.
 */
enum class AltStreamSearchMode(
    val label: String,
    val shortLabel: String,
    val description: String,
) {
    AUTO(
        label = "Auto (Music + Video fallback)",
        shortLabel = "Auto",
        description = "Searches Music first and automatically expands to Videos when sparse",
    ),
    MUSIC(
        label = "Music only",
        shortLabel = "Music",
        description = "Searches official YouTube Music audio tracks only",
    ),
    VIDEO(
        label = "Video only",
        shortLabel = "Video",
        description = "Searches YouTube music videos, live uploads, and lyric videos",
    ),
    BOTH(
        label = "Both (Music + Video)",
        shortLabel = "Both",
        description = "Queries Music & Video catalogs simultaneously and merges results",
    );

    companion object {
        fun fromName(raw: String?): AltStreamSearchMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: AUTO
    }
}

private const val KEY_ALT_STREAM_SEARCH_MODE = "alt_stream_search_mode"

fun getAltStreamSearchMode(context: Context): AltStreamSearchMode =
    AltStreamSearchMode.fromName(
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_ALT_STREAM_SEARCH_MODE, AltStreamSearchMode.AUTO.name)
    )

fun setAltStreamSearchMode(context: Context, mode: AltStreamSearchMode) {
    context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_ALT_STREAM_SEARCH_MODE, mode.name)
        .apply()
}

