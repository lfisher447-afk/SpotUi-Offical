package com.music.spotui.widget

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class NowPlayingWidgetState(
    val songId: Int = -1,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artworkUrl: String = "",
    val isPlaying: Boolean = false,
    val isLiked: Boolean = false,
    val updatedAtMs: Long = 0L,
) {
    val hasTrack: Boolean get() = title.isNotBlank()
}

/**
 * Process-safe persistence for the home-screen widget state so widgets render the last active
 * track and recover cleanly across service idle stops or process death.
 */
object NowPlayingWidgetStateStore {

    private const val PREFS_NAME = "spotui_widget_state"
    private const val KEY_JSON = "state_json"

    private val _stateFlow = MutableStateFlow<NowPlayingWidgetState?>(null)
    val stateFlow: StateFlow<NowPlayingWidgetState?> = _stateFlow.asStateFlow()

    @Synchronized
    fun getState(context: Context): NowPlayingWidgetState? {
        _stateFlow.value?.let { return it }
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_JSON, null) ?: return null
        val parsed = runCatching {
            val o = JSONObject(raw)
            val title = o.optString("title")
            if (title.isBlank()) return@runCatching null
            NowPlayingWidgetState(
                songId = o.optInt("songId", -1),
                title = title,
                artist = o.optString("artist"),
                album = o.optString("album"),
                artworkUrl = o.optString("artworkUrl"),
                isPlaying = o.optBoolean("isPlaying", false),
                isLiked = o.optBoolean("isLiked", false),
                updatedAtMs = o.optLong("updatedAtMs", 0L),
            )
        }.getOrNull()
        _stateFlow.value = parsed
        return parsed
    }

    @Synchronized
    fun saveState(context: Context, state: NowPlayingWidgetState) {
        _stateFlow.value = state
        val json = JSONObject().apply {
            put("songId", state.songId)
            put("title", state.title)
            put("artist", state.artist)
            put("album", state.album)
            put("artworkUrl", state.artworkUrl)
            put("isPlaying", state.isPlaying)
            put("isLiked", state.isLiked)
            put("updatedAtMs", state.updatedAtMs)
        }.toString()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_JSON, json)
            .apply()
    }

    @Synchronized
    fun markStopped(context: Context): NowPlayingWidgetState? {
        val current = getState(context) ?: return null
        if (!current.isPlaying) return current
        val paused = current.copy(isPlaying = false, updatedAtMs = System.currentTimeMillis())
        saveState(context, paused)
        return paused
    }
}

/** Optional in-process hook so Like taps immediately sync open composables & session custom layout. */
object WidgetActionRouter {
    @Volatile
    var onToggleLike: ((songId: Int, isLikedNow: Boolean) -> Unit)? = null
}
