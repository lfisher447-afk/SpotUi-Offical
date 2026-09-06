// File: app/src/main/java/com/music/spotui/engine/LyricsSynchronizer.kt
package com.music.spotui.engine

import com.music.spotui.engine.playback.SongPlayer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Computes the active synced-lyric line from the player position. */
@Singleton
class LyricsSynchronizer @Inject constructor(
    private val songPlayer: SongPlayer,
) {
    private val mutableActiveLineIndex = MutableStateFlow(-1)

    /** Streams the index of the active lyric line, or -1 when none is active. */
    val activeLineIndex: StateFlow<Int> = mutableActiveLineIndex.asStateFlow()

    /** Updates active-line state using [lyrics] and the current Media3 position. */
    fun synchronize(lyrics: String) {
        val lines = LrcParser.parseLrc(lyrics)
        val position = songPlayer.state.value.positionMs
        mutableActiveLineIndex.value = lines.indexOfLast { it.timeMs <= position }
    }
}
