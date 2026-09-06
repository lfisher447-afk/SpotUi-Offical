// File: app/src/main/java/com/music/spotui/ui/viewmodel/PlaylistDetailViewModel.kt
package com.music.spotui.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.spotui.data.repository.PlaylistDetails
import com.music.spotui.data.repository.PlaylistRepository
import com.music.spotui.engine.playback.PlaybackController
import com.music.spotui.util.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** State for a single clean-layer playlist detail screen. */
data class PlaylistDetailUiState(
    val details: PlaylistDetails? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

/** Loads playlist details and routes play/reorder actions to clean domain services. */
@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val playbackController: PlaybackController,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(PlaylistDetailUiState())
    private var observeJob: Job? = null

    /** Streams this screen's immutable state. */
    val uiState: StateFlow<PlaylistDetailUiState> = mutableUiState.asStateFlow()

    /** Begins observing [playlistId], cancelling a prior detail subscription. */
    fun load(playlistId: String) {
        observeJob?.cancel()
        mutableUiState.value = PlaylistDetailUiState(isLoading = true)
        observeJob = viewModelScope.launch {
            playlistRepository.observeDetails(playlistId).collect { details ->
                mutableUiState.value = PlaylistDetailUiState(details = details, isLoading = false)
            }
        }
    }

    /** Starts playback from [startIndex] in the currently observed playlist. */
    fun playAll(startIndex: Int = 0) {
        val tracks = mutableUiState.value.details?.tracks.orEmpty()
        viewModelScope.launch {
            when (val result = playbackController.playQueue(tracks, startIndex)) {
                is Result.Failure -> mutableUiState.value = mutableUiState.value.copy(error = result.userMessage)
                Result.Loading, is Result.Success -> Unit
            }
        }
    }

    /** Persists the displayed playlist's new [trackIds] order. */
    fun reorder(trackIds: List<String>) {
        val playlistId = mutableUiState.value.details?.playlist?.id ?: return
        viewModelScope.launch {
            when (val result = playlistRepository.updateTrackOrder(playlistId, trackIds)) {
                is Result.Failure -> mutableUiState.value = mutableUiState.value.copy(error = result.userMessage)
                Result.Loading, is Result.Success -> Unit
            }
        }
    }
}
