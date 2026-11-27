package com.music.spotui.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650A4)
val PurpleGrey40 = Color(0xFF625B71)
val Pink40 = Color(0xFF7D5260)

/**
 * Bridge for Compose screens that reference AppPalette / AppBackground / AppSecondary
 * directly. Values are observable Compose state, so screens recompose immediately when
 * Theme Studio changes the active Material 3 color system.
 */
object PaletteBridge {
    var accent by mutableStateOf(Color(0xFF38BDF8))
    var secondary by mutableStateOf(Color(0xFF2CE69B))
    var tertiary by mutableStateOf(Color(0xFFA78BFA))
    var background by mutableStateOf(Color(0xFF070B14))
    var gridBackground by mutableStateOf(Color(0xFF141B2D))

    fun apply(
        accentColor: Color,
        backgroundColor: Color,
        surfaceColor: Color,
        secondaryColor: Color = accentColor,
        tertiaryColor: Color = accentColor,
    ) {
        accent = accentColor
        secondary = secondaryColor
        tertiary = tertiaryColor
        background = backgroundColor
        gridBackground = surfaceColor
    }
}

val AppPalette: Color get() = PaletteBridge.accent
val AppSecondary: Color get() = PaletteBridge.secondary
val AppTertiary: Color get() = PaletteBridge.tertiary
val AppBackground: Color get() = PaletteBridge.background
val GridBackground: Color get() = PaletteBridge.gridBackground
