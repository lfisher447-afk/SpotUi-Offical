// File: app/src/main/java/com/music/spotui/data/repository/SearchRepository.kt
package com.music.spotui.data.repository

import com.music.spotui.data.db.SearchHistoryDao
import com.music.spotui.data.db.TrackDao
import com.music.spotui.data.mapper.SpotifyMapper.toModel
import com.music.spotui.data.mapper.YouTubeMapper.toModel
import com.music.spotui.data.models.SearchHistoryModel
import com.music.spotui.data.models.TrackModel
import com.music.spotui.data.remote.SpotifyService
import com.music.spotui.data.remote.YouTubeService
import com.music.spotui.util.Constants
import com.music.spotui.util.Result
import com.music.spotui.util.normalizedSearchQuery
import com.music.spotui.util.runSuspendCatching
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Remote and local search gateway with a bounded on-device history. */
interface SearchRepository {
    /** Streams recent search-history values. */
    fun observeHistory(): Flow<List<SearchHistoryModel>>

    /** Searches user-authorized Spotify first, then falls back to YouTube Music. */
    suspend fun searchTracks(query: String): Result<List<TrackModel>>

    /** Clears all persisted search history. */
    suspend fun clearHistory(): Result<Unit>
}

/** Default implementation of [SearchRepository]. */
@Singleton
class SearchRepositoryImpl @Inject constructor(
    private val historyDao: SearchHistoryDao,
    private val spotifyService: SpotifyService,
    private val trackDao: TrackDao,
    private val youTubeService: YouTubeService,
) : SearchRepository {
    override fun observeHistory(): Flow<List<SearchHistoryModel>> =
        historyDao.observeRecent(Constants.MAX_SEARCH_HISTORY)

    override suspend fun searchTracks(query: String): Result<List<TrackModel>> {
        val normalized = query.normalizedSearchQuery()
        if (normalized.isBlank()) return Result.Success(emptyList())
        val spotifyResult = spotifyService.searchTracks(normalized)
        val tracksResult = when (spotifyResult) {
            is Result.Success -> Result.Success(spotifyResult.value.map { it.toModel() })
            is Result.Failure -> when (val youtubeResult = youTubeService.searchTracks(normalized)) {
                is Result.Success -> Result.Success(youtubeResult.value.map { it.toModel() })
                is Result.Failure -> youtubeResult
                Result.Loading -> Result.Loading
            }
            Result.Loading -> Result.Loading
        }
        if (tracksResult is Result.Success) {
            recordHistory(normalized, query.trim())
            trackDao.upsertAll(tracksResult.value)
        }
        return tracksResult
    }

    override suspend fun clearHistory(): Result<Unit> =
        runSuspendCatching("Unable to clear search history") { historyDao.clear() }

    private suspend fun recordHistory(normalizedQuery: String, displayQuery: String) {
        historyDao.upsert(
            SearchHistoryModel(
                normalizedQuery = normalizedQuery,
                query = displayQuery,
                lastSearchedAtEpochMs = System.currentTimeMillis(),
            ),
        )
        historyDao.trimTo(Constants.MAX_SEARCH_HISTORY)
    }
}
