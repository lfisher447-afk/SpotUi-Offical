package com.music.spotui.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/** App-level Material 3 host driven by Theme Studio and accessibility preferences. */
@Composable
fun SpotuiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val themeRevision by com.music.spotui.data.preferences.uiSettingsUpdates.collectAsState()
    val spec = remember(themeRevision) { ThemePreferences.currentSpec(context) }
    val highContrast = remember(themeRevision) { ThemePreferences.highContrast(context) }
    val textScale = remember(themeRevision) { ThemePreferences.textScale(context) }
    val useSystem = dynamicColor && spec.preset == ExpressiveThemePreset.SYSTEM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        useSystem && (spec.dark || darkTheme) -> dynamicDarkColorScheme(context)
        useSystem -> dynamicLightColorScheme(context)
        spec.dark -> expressiveDarkScheme(spec)
        else -> expressiveLightScheme(spec)
    }.let { scheme -> if (highContrast) highContrastScheme(scheme) else scheme }

    val density = LocalDensity.current
    val scaledDensity = remember(density, textScale) {
        Density(density = density.density, fontScale = density.fontScale * textScale)
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            PaletteBridge.apply(colorScheme.primary, colorScheme.background, colorScheme.surface)
            (view.context as? Activity)?.window?.let { window ->
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !spec.dark
                    isAppearanceLightNavigationBars = !spec.dark
                }
            }
        }
    }

    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialTheme(colorScheme = colorScheme, typography = Typography) {
            ProvideTextStyle(
                value = androidx.compose.material3.LocalTextStyle.current.copy(
                    fontFamily = Montserrat,
                    letterSpacing = (-0.2).sp,
                ),
                content = content,
            )
        }
    }
}

private fun expressiveDarkScheme(spec: SpotuiThemeSpec): ColorScheme = darkColorScheme(
    primary = Color(spec.primaryArgb),
    onPrimary = Color(spec.onPrimaryArgb),
    primaryContainer = Color(spec.secondaryArgb).copy(alpha = 0.30f),
    onPrimaryContainer = Color.White,
    secondary = Color(spec.secondaryArgb),
    onSecondary = contrastFor(Color(spec.secondaryArgb)),
    tertiary = Color(spec.tertiaryArgb),
    onTertiary = contrastFor(Color(spec.tertiaryArgb)),
    background = Color(spec.backgroundArgb),
    onBackground = Color(spec.onBackgroundArgb),
    surface = Color(spec.surfaceArgb),
    onSurface = Color(spec.onBackgroundArgb),
    surfaceVariant = Color(spec.surfaceArgb).copy(alpha = 0.86f),
    outline = Color(spec.secondaryArgb).copy(alpha = 0.70f),
)

private fun expressiveLightScheme(spec: SpotuiThemeSpec): ColorScheme = lightColorScheme(
    primary = Color(spec.primaryArgb),
    onPrimary = Color(spec.onPrimaryArgb),
    primaryContainer = Color(spec.secondaryArgb).copy(alpha = 0.26f),
    onPrimaryContainer = contrastFor(Color(spec.secondaryArgb)),
    secondary = Color(spec.secondaryArgb),
    onSecondary = contrastFor(Color(spec.secondaryArgb)),
    tertiary = Color(spec.tertiaryArgb),
    onTertiary = contrastFor(Color(spec.tertiaryArgb)),
    background = Color(spec.backgroundArgb),
    onBackground = Color(spec.onBackgroundArgb),
    surface = Color(spec.surfaceArgb),
    onSurface = Color(spec.onBackgroundArgb),
    surfaceVariant = Color(spec.surfaceArgb).copy(alpha = 0.90f),
    outline = Color(spec.primaryArgb).copy(alpha = 0.58f),
)

private fun highContrastScheme(scheme: ColorScheme): ColorScheme = scheme.copy(
    onBackground = if (luma(scheme.background) > 0.50) Color.Black else Color.White,
    onSurface = if (luma(scheme.surface) > 0.50) Color.Black else Color.White,
    outline = scheme.primary.copy(alpha = 0.95f),
    surfaceVariant = if (luma(scheme.surface) > 0.50) Color(0xFFE5E7EB) else Color(0xFF1F2937),
)

private fun contrastFor(color: Color): Color = if (luma(color) > 0.58) Color.Black else Color.White
private fun luma(color: Color): Float = (0.2126f * color.red) + (0.7152f * color.green) + (0.0722f * color.blue)
