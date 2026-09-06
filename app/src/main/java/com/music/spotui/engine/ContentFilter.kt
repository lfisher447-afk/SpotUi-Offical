// File: app/src/main/java/com/music/spotui/engine/ContentFilter.kt
package com.music.spotui.engine

import com.music.spotui.data.models.TrackModel
import javax.inject.Inject
import javax.inject.Singleton

/** Removes sponsored, podcast-like, and explicitly blocked tracks from clean-layer results. */
@Singleton
class ContentFilter @Inject constructor() {
    /** Returns tracks that are music items and are not present in the supplied blacklist. */
    fun filter(tracks: List<TrackModel>, blacklistedTrackIds: Set<String>): List<TrackModel> = tracks.filter { track ->
        track.id !in blacklistedTrackIds &&
            !track.title.contains("sponsored", ignoreCase = true) &&
            !track.title.contains("podcast", ignoreCase = true)
    }
}
