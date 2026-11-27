package com.metrolist.spotify.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class SpotifyTrack(
    val id: String = "",
    val name: String = "",
    val artists: List<SpotifySimpleArtist> = emptyList(),
    val album: SpotifySimpleAlbum? = null,
    @SerialName("duration_ms") val durationMs: Int = 0,
    val explicit: Boolean = false,
    @SerialName("is_local") val isLocal: Boolean = false,
    @SerialName("preview_url") val previewUrl: String? = null,
    @SerialName("track_number") val trackNumber: Int? = null,
    val uri: String? = null,
    val popularity: Int? = null,
    @SerialName("external_ids") val externalIds: SpotifyExternalIds? = null,
) {
    val isrc: String? get() = externalIds?.isrc
}

@Serializable
data class SpotifyExternalIds(
    val isrc: String? = null,
    val ean: String? = null,
    val upc: String? = null,
)

@Serializable
data class SpotifySimpleArtist(
    val id: String? = null,
    val name: String = "",
    val uri: String? = null,
)

@Serializable
data class SpotifySimpleAlbum(
    val id: String = "",
    val name: String = "",
    val images: List<SpotifyImage> = emptyList(),
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("album_type") val albumType: String? = null,
    val artists: List<SpotifySimpleArtist> = emptyList(),
    val uri: String? = null,
)

/**
 * Wrapper for the /me/tracks endpoint which returns SavedTrack objects
 */
@Serializable
data class SpotifySavedTrack(
    @SerialName("added_at") val addedAt: String? = null,
    val track: SpotifyTrack,
)

@Serializable
data class SpotifyTrackCreditArtist(
    val name: String = "",
    val uri: String? = null,
    val imageUri: String? = null,
    val subroles: List<String> = emptyList(),
)

@Serializable
data class SpotifyTrackCreditRole(
    val roleTitle: String = "",
    val artists: List<SpotifyTrackCreditArtist> = emptyList(),
)

@Serializable
data class SpotifyTrackCredits(
    val trackUri: String = "",
    val trackTitle: String = "",
    val roleCredits: List<SpotifyTrackCreditRole> = emptyList(),
    val sourceNames: List<String> = emptyList(),
)

private fun JsonObject.creditStr(key: String): String? =
    try {
        this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull
    } catch (_: Exception) {
        null
    }

private fun JsonObject.creditArr(key: String): JsonArray? =
    try {
        this[key]?.takeIf { it !is JsonNull }?.jsonArray
    } catch (_: Exception) {
        null
    }

fun parseTrackCredits(root: JsonObject): SpotifyTrackCredits {
    val trackUri = root.creditStr("trackUri").orEmpty()
    val trackTitle = root.creditStr("trackTitle").orEmpty()
    val roleCredits = root.creditArr("roleCredits")?.mapNotNull { roleElem ->
        val roleObj = runCatching { roleElem.jsonObject }.getOrNull() ?: return@mapNotNull null
        val roleTitle = roleObj.creditStr("roleTitle").orEmpty()
        val artists = roleObj.creditArr("artists")?.mapNotNull { artistElem ->
            val artistObj = runCatching { artistElem.jsonObject }.getOrNull() ?: return@mapNotNull null
            val name = artistObj.creditStr("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val subroles = artistObj.creditArr("subroles")?.mapNotNull { sr ->
                runCatching { sr.jsonPrimitive.contentOrNull }.getOrNull()?.takeIf { it.isNotBlank() }
            }.orEmpty()
            SpotifyTrackCreditArtist(
                name = name,
                uri = artistObj.creditStr("uri"),
                imageUri = artistObj.creditStr("imageUri"),
                subroles = subroles,
            )
        }.orEmpty()
        if (roleTitle.isBlank() && artists.isEmpty()) return@mapNotNull null
        SpotifyTrackCreditRole(
            roleTitle = roleTitle,
            artists = artists,
        )
    }.orEmpty()

    val sourceNames = root.creditArr("sourceNames")?.mapNotNull { src ->
        runCatching { src.jsonPrimitive.contentOrNull }.getOrNull()?.takeIf { it.isNotBlank() }
    }.orEmpty()

    return SpotifyTrackCredits(
        trackUri = trackUri,
        trackTitle = trackTitle,
        roleCredits = roleCredits,
        sourceNames = sourceNames,
    )
}
