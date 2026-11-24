package com.music.spotui.util

import android.content.Context
import com.music.spotui.data.preferences.isExplicitContentAllowed
import com.music.spotui.data.preferences.isPreferExplicitVersions

/**
 * Unified Explicit & Clean-Edit detector for Spotify tracks, InnerTube items,
 * and offline/alternative streams.
 */
object ExplicitContentDetector {

    private val cleanEditRegex = Regex(
        """(?i)(?:\[(?:clean(?:\s+(?:version|edit))?|radio\s+edit|edited(?:\s+version)?|censored|safe(?:\s+edit)?)\]|\((?:clean(?:\s+(?:version|edit))?|radio\s+edit|edited(?:\s+version)?|censored|safe(?:\s+edit)?)\)|[-–—]\s*(?:clean(?:\s+(?:version|edit))?|radio\s+edit|edited(?:\s+version)?)\s*$|\b(?:clean\s+version|radio\s+edit|edited\s+version)\b)""",
    )

    private val explicitMarkerRegex = Regex(
        """(?i)(?:\[(?:e|explicit(?:\s+(?:version|edit))?|dirty|uncensored|uncut)\]|\((?:e|explicit(?:\s+(?:version|edit))?|dirty|uncensored|uncut)\)|[-–—]\s*(?:explicit(?:\s+(?:version|edit))?|dirty|uncensored)\s*$|\b(?:explicit\s+version|parental\s+advisory)\b)""",
    )

    /** True when [text] explicitly labels the recording as a clean / radio-edited cut. */
    fun isCleanEditText(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return cleanEditRegex.containsMatchIn(text)
    }

    /** True when [text] carries explicit / dirty / uncensored / [E] markers. */
    fun hasExplicitKeywordText(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return explicitMarkerRegex.containsMatchIn(text)
    }

    /**
     * Combines provider metadata ([apiExplicit]) with title / album / label markers.
     * Explicit clean-edit suffixes like `(Clean)` or `[Radio Edit]` take precedence
     * so clean edits are never misflagged as explicit.
     */
    fun detectExplicit(
        apiExplicit: Boolean,
        title: String?,
        album: String? = null,
        overrideLabel: String? = null,
    ): Boolean {
        if (isCleanEditText(title) || isCleanEditText(overrideLabel)) {
            return false
        }
        if (apiExplicit) {
            return true
        }
        return hasExplicitKeywordText(title) ||
            hasExplicitKeywordText(album) ||
            hasExplicitKeywordText(overrideLabel)
    }

    /**
     * Resolves whether playback should prefer explicit or clean recordings given the
     * user's content-filter and preference toggles.
     */
    fun effectiveWantExplicit(
        allowExplicit: Boolean,
        preferExplicit: Boolean,
        spotifyExplicitFlag: Boolean?,
    ): Boolean? = when {
        !allowExplicit -> false
        spotifyExplicitFlag != null -> spotifyExplicitFlag
        preferExplicit -> true
        else -> null
    }

    fun effectiveWantExplicit(
        context: Context,
        spotifyExplicitFlag: Boolean?,
    ): Boolean? = effectiveWantExplicit(
        allowExplicit = isExplicitContentAllowed(context),
        preferExplicit = isPreferExplicitVersions(context),
        spotifyExplicitFlag = spotifyExplicitFlag,
    )

    fun shouldBlockPlayback(
        context: Context,
        apiExplicit: Boolean,
        title: String?,
        album: String? = null,
    ): Boolean {
        if (isExplicitContentAllowed(context)) return false
        return detectExplicit(apiExplicit = apiExplicit, title = title, album = album)
    }
}
