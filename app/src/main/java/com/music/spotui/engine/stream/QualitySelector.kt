// File: app/src/main/java/com/music/spotui/engine/stream/QualitySelector.kt
package com.music.spotui.engine.stream

import javax.inject.Inject
import javax.inject.Singleton

/** User-selectable stream quality tiers. */
enum class StreamQuality(val preferredBitrateKbps: Int) {
    DATA_SAVER(96),
    BALANCED(160),
    HIGH(256),
    LOSSLESS(1_000),
}

/** A candidate whose bitrate can be ranked against a requested quality tier. */
data class StreamCandidate(
    val url: String,
    val bitrateKbps: Int,
    val source: String,
)

/** Selects a stable, bandwidth-appropriate stream from provider candidates. */
@Singleton
class QualitySelector @Inject constructor() {
    /** Returns the closest candidate at or below [quality], falling back to the lowest available bitrate. */
    fun select(candidates: List<StreamCandidate>, quality: StreamQuality): StreamCandidate? {
        val usable = candidates.filter { it.url.isNotBlank() && it.bitrateKbps > 0 }
        return usable.filter { it.bitrateKbps <= quality.preferredBitrateKbps }
            .maxByOrNull(StreamCandidate::bitrateKbps)
            ?: usable.minByOrNull(StreamCandidate::bitrateKbps)
    }
}
