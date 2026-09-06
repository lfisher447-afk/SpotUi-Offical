// File: app/src/main/java/com/music/spotui/ui/viewmodel/QueueViewModel.kt
package com.music.spotui.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.spotui.engine.playback.PlaybackController
import com.music.spotui.engine.playback.QueueManager
import com.music.spotui.engine.playback.QueueState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Exposes queue state and routes queue interactions to the clean playback engine. */
@HiltViewModel
class QueueViewModel @Inject constructor(
    private val playbackController: PlaybackController,
    private val queueManager: QueueManager,
) : ViewModel() {
    /** Streams the active queue. */
    val queue: StateFlow<QueueState> = queueManager.state

    /** Selects and plays a queue item by [index]. */
    fun play(index: Int) {
        val track = queueManager.select(index) ?: return
        viewModelScope.launch { playbackController.play(track) }
    }

    /** Reorders a queue item. */
    fun move(fromIndex: Int, toIndex: Int) = queueManager.move(fromIndex, toIndex)

    /** Removes one queue item. */
    fun remove(trackId: String) = queueManager.remove(trackId)
}
