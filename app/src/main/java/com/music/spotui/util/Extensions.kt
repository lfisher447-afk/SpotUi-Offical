// File: app/src/main/java/com/music/spotui/util/Extensions.kt
package com.music.spotui.util

import com.music.spotui.data.entity.SongsModel
import com.music.spotui.data.models.TrackModel
import java.util.Locale

/** Converts a persisted track into the legacy player model during the migration period. */
fun TrackModel.toLegacySong(): SongsModel = SongsModel(
    id = id.hashCode(),
    title = title,
    album = albumName,
    singer = artistNames.joinToString(", "),
    coverUri = artworkUrl.orEmpty(),
    url = streamUrl.orEmpty(),
    spotifyTrackId = providerId,
    explicit = explicit,
    durationMs = durationMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
    artistIds = artistIds.joinToString(","),
)

/** Normalizes user-entered search text before persistence or remote lookup. */
fun String.normalizedSearchQuery(): String = trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

/** Returns the identifiers in this list's visible order. */
fun List<TrackModel>.ids(): List<String> = map(TrackModel::id)
