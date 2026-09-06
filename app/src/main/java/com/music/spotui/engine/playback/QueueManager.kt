// File: app/src/main/java/com/music/spotui/engine/playback/QueueManager.kt
package com.music.spotui.engine.playback

import com.music.spotui.data.models.TrackModel
import com.music.spotui.util.Logger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Immutable queue snapshot exposed to controllers and presentation state. */
data class QueueState(
    val tracks: List<TrackModel> = emptyList(),
    val currentIndex: Int = -1,
) {
    /** Returns the current track, or null when the queue is empty. */
    fun current(): TrackModel? = tracks.getOrNull(currentIndex)
}

/** Owns queue ordering and current-selection state independently of the Media3 player. */
@Singleton
class QueueManager @Inject constructor() {
    private val mutableState = MutableStateFlow(QueueState())

    /** Streams the current immutable queue state. */
    val state: StateFlow<QueueState> = mutableState.asStateFlow()

    /** Replaces the queue and selects [startIndex] when it points to an item. */
    fun replace(tracks: List<TrackModel>, startIndex: Int = 0) {
        val distinctTracks = tracks.distinctBy(TrackModel::id)
        val selected = if (distinctTracks.isEmpty()) -1 else startIndex.coerceIn(0, distinctTracks.lastIndex)
        mutableState.value = QueueState(distinctTracks, selected)
    }

    /** Appends [tracks] without duplicating identifiers already in the queue. */
    fun append(tracks: List<TrackModel>) {
        val current = mutableState.value
        val merged = (current.tracks + tracks).distinctBy(TrackModel::id)
        mutableState.value = current.copy(tracks = merged, currentIndex = current.currentIndex.coerceAtMost(merged.lastIndex))
    }

    /** Selects [index], returning its track or null when it is invalid. */
    fun select(index: Int): TrackModel? {
        val current = mutableState.value
        val selected = current.tracks.getOrNull(index) ?: return null
        mutableState.value = current.copy(currentIndex = index)
        return selected
    }

    /** Advances to the next track and returns it, or null at the queue boundary. */
    fun advance(): TrackModel? = select(mutableState.value.currentIndex + 1)

    /** Moves to the previous track and returns it, or null at the queue boundary. */
    fun rewind(): TrackModel? = select(mutableState.value.currentIndex - 1)

    /** Removes [trackId], keeping the current selection stable whenever possible. */
    fun remove(trackId: String) {
        val current = mutableState.value
        val removedIndex = current.tracks.indexOfFirst { it.id == trackId }
        if (removedIndex < 0) return
        val tracks = current.tracks.filterNot { it.id == trackId }
        val index = when {
            tracks.isEmpty() -> -1
            removedIndex < current.currentIndex -> current.currentIndex - 1
            current.currentIndex >= tracks.size -> tracks.lastIndex
            else -> current.currentIndex
        }
        mutableState.value = QueueState(tracks, index)
        Logger.debug("QueueManager", "Removed track from queue")
    }

    /** Moves an item from [fromIndex] to [toIndex] when both indexes are valid. */
    fun move(fromIndex: Int, toIndex: Int) {
        val current = mutableState.value
        if (fromIndex !in current.tracks.indices || toIndex !in current.tracks.indices || fromIndex == toIndex) return
        val reordered = current.tracks.toMutableList()
        val moved = reordered.removeAt(fromIndex)
        reordered.add(toIndex, moved)
        val activeId = current.current()?.id
        mutableState.value = QueueState(reordered, reordered.indexOfFirst { it.id == activeId })
    }

    /** Clears every item from the queue. */
    fun clear() {
        mutableState.value = QueueState()
    }
}
