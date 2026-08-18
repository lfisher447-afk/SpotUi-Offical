package com.music.spotui.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout

/**
 * Version 1.5.1 - BannerAdStripper
 * Compose view layout modifier removing promoted banner ads and sponsored brand cards from home feeds.
 */
fun Modifier.stripBannerAds(isSponsored: Boolean = false): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        if (isSponsored) {
            // Render nothing, take up zero space
            layout(0, 0) {}
        } else {
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) {
                placeable.placeRelative(0, 0)
            }
        }
    }
)
