// File: app/src/main/java/com/music/spotui/ui/viewmodel/DownloadsViewModel.kt
package com.music.spotui.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.spotui.data.models.DownloadState
import com.music.spotui.data.models.TrackModel
import com.music.spotui.engine.download.DownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Owns clean download-screen state and user actions. */
@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadManager: DownloadManager,
) : ViewModel() {
    /** Streams tracks paired with their offline state. */
    val downloads: StateFlow<List<Pair<TrackModel, DownloadState>>> = downloadManager.observeDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Queues [track] for sequential offline download. */
    fun download(track: TrackModel) {
        viewModelScope.launch { downloadManager.enqueue(track) }
    }

    /** Cancels [trackId] if it is queued or downloading. */
    fun cancel(trackId: String) {
        viewModelScope.launch { downloadManager.cancel(trackId) }
    }
}
