package com.music.spotui.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Stable Android system typography.
 *
 * The supplied custom font binaries were malformed and could trigger resource or
 * rendering failures on device. The app therefore uses the platform-supported
 * sans-serif family everywhere; aliases keep existing UI references source-safe.
 */
val SpotifyMix: FontFamily = FontFamily.SansSerif
val SpotifyMixTitle: FontFamily = FontFamily.SansSerif
val Montserrat: FontFamily = FontFamily.SansSerif

private val base = TextStyle(fontFamily = SpotifyMix)
private val title = TextStyle(fontFamily = SpotifyMixTitle, fontWeight = FontWeight.Bold)

val Typography = Typography(
    displayLarge = title, displayMedium = title, displaySmall = title,
    headlineLarge = title, headlineMedium = title, headlineSmall = title,
    titleLarge = base.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.copy(fontWeight = FontWeight.Bold),
    titleSmall = base.copy(fontWeight = FontWeight.Bold),
    bodyLarge = base.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = base, bodySmall = base,
    labelLarge = base, labelMedium = base, labelSmall = base,
)
