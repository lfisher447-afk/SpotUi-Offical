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
 * Bridge for legacy Compose screens that still reference AppPalette/AppBackground
 * directly. Values are observable Compose state, so existing screens recompose when
 * Theme Studio changes the active Material color system.
 */
object PaletteBridge {
    var accent by mutableStateOf(Color(0xFF618DFF))
    var background by mutableStateOf(Color(0xFF0B0B0F))
    var gridBackground by mutableStateOf(Color(0xFF2A2A2A))

    fun apply(accentColor: Color, backgroundColor: Color, surfaceColor: Color) {
        accent = accentColor
        background = backgroundColor
        gridBackground = surfaceColor
    }
}

val AppPalette: Color get() = PaletteBridge.accent
val AppBackground: Color get() = PaletteBridge.background
val GridBackground: Color get() = PaletteBridge.gridBackground
