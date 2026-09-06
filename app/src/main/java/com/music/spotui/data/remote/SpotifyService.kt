// File: app/src/main/java/com/music/spotui/data/remote/SpotifyService.kt
package com.music.spotui.data.remote

import android.content.Context
import com.metrolist.spotify.Spotify
import com.music.spotui.data.api.SpotifyTokenProvider
import com.music.spotui.util.Result
import com.music.spotui.util.runSuspendCatching
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/** Minimal Spotify Web API client used for user-authorized metadata search. */
@Singleton
class SpotifyService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: HttpClient,
) {
    /** Searches Spotify tracks after renewing the user's authorized session token. */
    suspend fun searchTracks(query: String, limit: Int = 20): Result<List<SpotifyTrackDto>> =
        runSuspendCatching("Unable to search Spotify right now") {
            check(query.isNotBlank()) { "A search query is required" }
            check(SpotifyTokenProvider.ensureToken(context)) { "Connect Spotify in Settings to search" }
            val token = requireNotNull(Spotify.accessToken) { "Spotify access token is unavailable" }
            client.get("https://api.spotify.com/v1/search") {
                header("Authorization", "Bearer $token")
                parameter("q", query.trim())
                parameter("type", "track")
                parameter("limit", limit.coerceIn(1, 50))
            }.body<SpotifySearchResponse>().tracks.items
        }
}

/** Spotify search response subset required by the local catalog. */
@Serializable
data class SpotifySearchResponse(val tracks: SpotifyTrackPage)

/** A page of Spotify tracks. */
@Serializable
data class SpotifyTrackPage(val items: List<SpotifyTrackDto> = emptyList())

/** A provider track returned by the Spotify Web API. */
@Serializable
data class SpotifyTrackDto(
    val id: String,
    val name: String,
    val artists: List<SpotifyArtistDto> = emptyList(),
    val album: SpotifyAlbumDto? = null,
    @SerialName("duration_ms") val durationMs: Long = 0L,
    val explicit: Boolean = false,
    @SerialName("external_ids") val externalIds: Map<String, String> = emptyMap(),
)

/** An artist value nested in a Spotify response. */
@Serializable
data class SpotifyArtistDto(val id: String, val name: String)

/** Album subset nested in a Spotify track response. */
@Serializable
data class SpotifyAlbumDto(
    val id: String,
    val name: String,
    val images: List<SpotifyImageDto> = emptyList(),
)

/** Artwork image metadata nested in a Spotify response. */
@Serializable
data class SpotifyImageDto(val url: String)
