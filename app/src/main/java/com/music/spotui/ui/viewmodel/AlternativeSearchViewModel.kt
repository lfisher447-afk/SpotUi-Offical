package com.music.spotui.ui.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.utils.YTPlayerUtils
import com.music.spotui.data.preferences.AltStreamSearchMode
import com.music.spotui.data.preferences.getAltStreamSearchMode
import com.music.spotui.data.preferences.isExplicitContentAllowed
import com.music.spotui.data.preferences.isPreferExplicitVersions
import com.music.spotui.data.preferences.setAltStreamSearchMode
import com.music.spotui.util.ExplicitContentDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AlternativeSearchViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    var searchResults by mutableStateOf<List<SongItem>>(emptyList())
        private set
    var isSearching by mutableStateOf(false)
        private set
    var searchQuery by mutableStateOf("")
        private set
    var searchMode by mutableStateOf(getAltStreamSearchMode(context))
        private set
    var statusNote by mutableStateOf<String?>(null)
        private set
    var previewingVideoId by mutableStateOf<String?>(null)
        private set
    var isResolvingPreview by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var previewPlayer: ExoPlayer? = null
    private var searchJob: Job? = null
    private var resolveJob: Job? = null
    private var lastInitQuery: String = ""

    fun initQuery(title: String, artist: String) {
        val query = "$title $artist".trim()
        searchMode = getAltStreamSearchMode(context)
        if (query == lastInitQuery && searchResults.isNotEmpty()) return
        lastInitQuery = query
        searchQuery = query
        searchResults = emptyList()
        error = null
        statusNote = null
        launchSearch(query, searchMode)
    }

    fun updateQuery(query: String) {
        searchQuery = query
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350)
            executeSearch(query, searchMode)
        }
    }

    fun setMode(mode: AltStreamSearchMode) {
        if (searchMode == mode && searchResults.isNotEmpty()) return
        searchMode = mode
        setAltStreamSearchMode(context, mode)
        launchSearch(searchQuery, mode)
    }

    fun retrySearch() {
        launchSearch(searchQuery, searchMode)
    }

    private fun launchSearch(query: String, mode: AltStreamSearchMode) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            executeSearch(query, mode)
        }
    }

    private suspend fun executeSearch(query: String, mode: AltStreamSearchMode) {
        if (query.isBlank()) {
            searchResults = emptyList()
            statusNote = null
            error = null
            isSearching = false
            return
        }
        isSearching = true
        error = null
        statusNote = null
        try {
            val allowExplicit = isExplicitContentAllowed(context)
            val preferExplicit = isPreferExplicitVersions(context)

            val outcome = withContext(Dispatchers.IO) {
                when (mode) {
                    AltStreamSearchMode.MUSIC -> {
                        val res = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG)
                        val songs = res.getOrNull()?.items?.filterIsInstance<SongItem>().orEmpty()
                        SearchOutcome(
                            items = songs,
                            note = "${songs.size} Music results",
                            failure = res.exceptionOrNull(),
                        )
                    }

                    AltStreamSearchMode.VIDEO -> {
                        val res = YouTube.search(query, YouTube.SearchFilter.FILTER_VIDEO)
                        val videos = res.getOrNull()?.items?.filterIsInstance<SongItem>().orEmpty()
                        SearchOutcome(
                            items = videos,
                            note = "${videos.size} Video results",
                            failure = res.exceptionOrNull(),
                        )
                    }

                    AltStreamSearchMode.BOTH -> coroutineScope {
                        val musicDeferred = async { YouTube.search(query, YouTube.SearchFilter.FILTER_SONG) }
                        val videoDeferred = async { YouTube.search(query, YouTube.SearchFilter.FILTER_VIDEO) }
                        val musicRes = musicDeferred.await()
                        val videoRes = videoDeferred.await()
                        val musicItems = musicRes.getOrNull()?.items?.filterIsInstance<SongItem>().orEmpty()
                        val videoItems = videoRes.getOrNull()?.items?.filterIsInstance<SongItem>().orEmpty()
                        val merged = interleaveDistinct(musicItems, videoItems)
                        val note = buildString {
                            append("${musicItems.size} Music · ${videoItems.size} Video")
                            if (musicRes.isFailure || videoRes.isFailure) append(" (partial)")
                        }
                        val combinedFailure = if (merged.isEmpty()) {
                            musicRes.exceptionOrNull() ?: videoRes.exceptionOrNull()
                        } else {
                            null
                        }
                        SearchOutcome(items = merged, note = note, failure = combinedFailure)
                    }

                    AltStreamSearchMode.AUTO -> {
                        val musicRes = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG)
                        val musicItems = musicRes.getOrNull()?.items?.filterIsInstance<SongItem>().orEmpty()
                        if (musicItems.size >= 4) {
                            SearchOutcome(
                                items = musicItems,
                                note = "${musicItems.size} Music results (Auto)",
                                failure = null,
                            )
                        } else {
                            val videoRes = YouTube.search(query, YouTube.SearchFilter.FILTER_VIDEO)
                            val videoItems = videoRes.getOrNull()?.items?.filterIsInstance<SongItem>().orEmpty()
                            val merged = interleaveDistinct(musicItems, videoItems)
                            SearchOutcome(
                                items = merged,
                                note = "Auto expanded: ${musicItems.size} Music + ${videoItems.size} Video",
                                failure = if (merged.isEmpty()) (musicRes.exceptionOrNull() ?: videoRes.exceptionOrNull()) else null,
                            )
                        }
                    }
                }
            }

            if (outcome.failure != null && outcome.items.isEmpty()) {
                Log.w(TAG, "YouTube search failed (mode=$mode)", outcome.failure)
                error = "Search failed (${outcome.failure.message ?: "network error"})"
                searchResults = emptyList()
                statusNote = null
            } else {
                val processed = rankAndFilterByExplicitPreference(
                    items = outcome.items,
                    allowExplicit = allowExplicit,
                    preferExplicit = preferExplicit,
                )
                searchResults = processed
                statusNote = if (!allowExplicit && processed.size < outcome.items.size) {
                    "${outcome.note} · ${outcome.items.size - processed.size} explicit hidden"
                } else {
                    outcome.note
                }
            }
        } finally {
            isSearching = false
        }
    }

    private fun interleaveDistinct(primary: List<SongItem>, secondary: List<SongItem>): List<SongItem> {
        val seen = HashSet<String>(primary.size + secondary.size)
        val out = ArrayList<SongItem>(primary.size + secondary.size)
        val maxLen = maxOf(primary.size, secondary.size)
        for (i in 0 until maxLen) {
            primary.getOrNull(i)?.let { item ->
                if (seen.add(item.id)) out.add(item)
            }
            secondary.getOrNull(i)?.let { item ->
                if (seen.add(item.id)) out.add(item)
            }
        }
        return out
    }

    private fun rankAndFilterByExplicitPreference(
        items: List<SongItem>,
        allowExplicit: Boolean,
        preferExplicit: Boolean,
    ): List<SongItem> {
        if (!allowExplicit) {
            val nonExplicit = items.filterNot { item ->
                ExplicitContentDetector.detectExplicit(
                    apiExplicit = item.explicit,
                    title = item.title,
                    album = item.album?.name,
                )
            }
            if (nonExplicit.isNotEmpty()) return nonExplicit
        }
        return if (preferExplicit) {
            items.sortedByDescending { item ->
                ExplicitContentDetector.detectExplicit(
                    apiExplicit = item.explicit,
                    title = item.title,
                    album = item.album?.name,
                )
            }
        } else {
            items
        }
    }

    private data class SearchOutcome(
        val items: List<SongItem>,
        val note: String?,
        val failure: Throwable?,
    )

    fun preview(song: SongItem) {
        if (previewingVideoId == song.id) {
            stopPreview()
            return
        }
        stopPreview()
        previewingVideoId = song.id
        isResolvingPreview = true
        com.music.spotui.di.SongPlayer.pause()
        resolveJob = viewModelScope.launch {
            val playback = withContext(Dispatchers.IO) {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val quality = com.music.spotui.data.preferences.currentStreamingQuality(context)
                YTPlayerUtils.playerResponseForPlayback(
                    videoId = song.id,
                    audioQuality = quality.audioQuality,
                    connectivityManager = cm,
                    skipValidation = true,
                )
            }
            playback.fold(
                onSuccess = { data ->
                    val player = getOrCreatePreviewPlayer()
                    withContext(Dispatchers.Main) {
                        player.setMediaItem(MediaItem.fromUri(data.streamUrl))
                        player.prepare()
                        player.play()
                    }
                    isResolvingPreview = false
                },
                onFailure = { e ->
                    Log.w(TAG, "Preview resolve failed for ${song.id}", e)
                    previewingVideoId = null
                    isResolvingPreview = false
                },
            )
        }
    }

    fun stopPreview() {
        resolveJob?.cancel()
        resolveJob = null
        previewPlayer?.let { p ->
            p.stop()
            p.release()
        }
        previewPlayer = null
        previewingVideoId = null
        isResolvingPreview = false
    }

    private fun getOrCreatePreviewPlayer(): ExoPlayer {
        previewPlayer?.let { return it }
        val p = ExoPlayer.Builder(context)
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .build(),
                false, // don't handle audio focus — this is transient preview
            )
            .setHandleAudioBecomingNoisy(false)
            .build()
        previewPlayer = p
        return p
    }

    override fun onCleared() {
        stopPreview()
    }

    companion object {
        private const val TAG = "AltSearchVM"
    }
}
