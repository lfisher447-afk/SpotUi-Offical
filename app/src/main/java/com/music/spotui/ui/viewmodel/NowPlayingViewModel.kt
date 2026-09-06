// File: app/src/main/java/com/music/spotui/ui/viewmodel/NowPlayingViewModel.kt
package com.music.spotui.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.spotui.data.models.TrackModel
import com.music.spotui.engine.playback.PlaybackController
import com.music.spotui.engine.playback.PlaybackState
import com.music.spotui.engine.playback.QueueManager
import com.music.spotui.engine.playback.QueueState
import com.music.spotui.engine.playback.SongPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** UI state for the full-screen clean now-playing experience. */
data class NowPlayingUiState(
    val playback: PlaybackState = PlaybackState(),
    val queue: QueueState = QueueState(),
)

/** Owns now-playing controls and exposes all screen state through one StateFlow. */
@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val playbackController: PlaybackController,
    private val queueManager: QueueManager,
    private val songPlayer: SongPlayer,
) : ViewModel() {
    /** Streams player and queue state together for a consistent Compose frame. */
    val uiState: StateFlow<NowPlayingUiState> = combine(songPlayer.state, queueManager.state) { playback, queue ->
        NowPlayingUiState(playback, queue)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NowPlayingUiState())

    /** Starts [track] after resolving an allowed stream. */
    fun play(track: TrackModel) {
        viewModelScope.launch { playbackController.play(track) }
    }

    /** Toggles pause and resume. */
    fun togglePlayback() = playbackController.toggle()

    /** Seeks to [positionMs]. */
    fun seekTo(positionMs: Long) = playbackController.seekTo(positionMs)

    /** Resolves and plays the next queued item. */
    fun next() {
        viewModelScope.launch { playbackController.playNext() }
    }

    /** Resolves and plays the previous queued item. */
    fun previous() {
        viewModelScope.launch { playbackController.playPrevious() }
    }

    /** Refreshes position state for a composable progress ticker. */
    fun refreshProgress() = songPlayer.refreshProgress()
}
