package com.music.spotui.ui.theme

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.palette.graphics.Palette
import org.json.JSONObject

/**
 * Persisted appearance configuration for the whole Spotui shell.
 *
 * The import format is intentionally constrained to named Material color roles so a
 * user can exchange a theme without allowing arbitrary executable content. Image
 * imports use Android Palette locally; the selected image is never uploaded.
 */
enum class ExpressiveThemePreset(
    val label: String,
    val description: String,
    val seed: Int,
) {
    SPOTUI_BLUE("Spotui Blue", "Focused blue with a cool, high-contrast music surface", 0xFF618DFF.toInt()),
    AURORA("Aurora", "Violet, cyan, and luminous night-sky highlights", 0xFF9A7BFF.toInt()),
    SUNSET("Sunset", "Warm coral and amber with soft dark surfaces", 0xFFFF6F61.toInt()),
    FOREST("Forest", "Deep green and mint for a calm listening environment", 0xFF46B989.toInt()),
    MONOCHROME("Monochrome", "Minimal neutral palette with a precise electric accent", 0xFFB8C1FF.toInt()),
    SYSTEM("System dynamic", "Android 12+ wallpaper colors when available", 0xFF618DFF.toInt()),
    CUSTOM_JSON("Custom JSON", "Validated user-imported Material role colors", 0xFF618DFF.toInt()),
    IMAGE_DERIVED("Image derived", "Palette generated locally from a selected image", 0xFF618DFF.toInt()),
}

data class SpotuiThemeSpec(
    val name: String,
    val preset: ExpressiveThemePreset,
    val primaryArgb: Int,
    val secondaryArgb: Int,
    val tertiaryArgb: Int,
    val backgroundArgb: Int,
    val surfaceArgb: Int,
    val onPrimaryArgb: Int,
    val onBackgroundArgb: Int,
    val dark: Boolean,
    val sourceDescription: String,
)

object ThemePreferences {
    private const val PREF = "spotui_theme_studio"
    private const val KEY_PRESET = "preset"
    private const val KEY_SPEC = "spec_json"
    private const val KEY_IMAGE_URI = "image_uri"
    private const val KEY_HIGH_CONTRAST = "high_contrast"
    private const val KEY_TEXT_SCALE = "text_scale"
    private const val KEY_REDUCE_MOTION = "reduce_motion"
    private const val KEY_MIN_TOUCH_TARGET = "minimum_touch_target"
    private const val KEY_MONO_AUDIO = "mono_audio"
    private const val KEY_AUDIO_BALANCE = "audio_balance"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun preset(context: Context): ExpressiveThemePreset = runCatching {
        ExpressiveThemePreset.valueOf(prefs(context).getString(KEY_PRESET, ExpressiveThemePreset.SPOTUI_BLUE.name).orEmpty())
    }.getOrDefault(ExpressiveThemePreset.SPOTUI_BLUE)

    fun setPreset(context: Context, preset: ExpressiveThemePreset) {
        prefs(context).edit().putString(KEY_PRESET, preset.name).apply()
        com.music.spotui.data.preferences.signalUiSettingsChanged()
    }

    fun highContrast(context: Context): Boolean = prefs(context).getBoolean(KEY_HIGH_CONTRAST, false)
    fun setHighContrast(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_HIGH_CONTRAST, enabled).apply()
        com.music.spotui.data.preferences.signalUiSettingsChanged()
    }

    fun textScale(context: Context): Float = prefs(context).getFloat(KEY_TEXT_SCALE, 1f).coerceIn(0.85f, 1.45f)
    fun setTextScale(context: Context, scale: Float) {
        prefs(context).edit().putFloat(KEY_TEXT_SCALE, scale.coerceIn(0.85f, 1.45f)).apply()
        com.music.spotui.data.preferences.signalUiSettingsChanged()
    }

    fun reduceMotion(context: Context): Boolean = prefs(context).getBoolean(KEY_REDUCE_MOTION, false)
    fun setReduceMotion(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_REDUCE_MOTION, enabled).apply()
        com.music.spotui.data.preferences.signalUiSettingsChanged()
    }

    fun minimumTouchTarget(context: Context): Boolean = prefs(context).getBoolean(KEY_MIN_TOUCH_TARGET, false)
    fun setMinimumTouchTarget(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_MIN_TOUCH_TARGET, enabled).apply()
        com.music.spotui.data.preferences.signalUiSettingsChanged()
    }

    fun monoAudio(context: Context): Boolean = prefs(context).getBoolean(KEY_MONO_AUDIO, false)
    fun setMonoAudio(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_MONO_AUDIO, enabled).apply()
        com.music.spotui.data.preferences.signalUiSettingsChanged()
    }

    /** -1 = full left, 0 = centered, +1 = full right. Applied by the PCM accessibility processor. */
    fun audioBalance(context: Context): Float = prefs(context).getFloat(KEY_AUDIO_BALANCE, 0f).coerceIn(-1f, 1f)
    fun setAudioBalance(context: Context, value: Float) {
        prefs(context).edit().putFloat(KEY_AUDIO_BALANCE, value.coerceIn(-1f, 1f)).apply()
        com.music.spotui.data.preferences.signalUiSettingsChanged()
    }

    fun selectedImageUri(context: Context): String = prefs(context).getString(KEY_IMAGE_URI, "").orEmpty()

    fun currentSpec(context: Context): SpotuiThemeSpec {
        val selected = preset(context)
        val custom = prefs(context).getString(KEY_SPEC, null)?.let(::decodeSpec)
        return when (selected) {
            ExpressiveThemePreset.CUSTOM_JSON,
            ExpressiveThemePreset.IMAGE_DERIVED -> custom ?: presetSpec(ExpressiveThemePreset.SPOTUI_BLUE)
            else -> presetSpec(selected)
        }
    }

    fun importJson(context: Context, source: Uri): Result<SpotuiThemeSpec> = runCatching {
        val raw = context.contentResolver.openInputStream(source)?.bufferedReader()?.use { it.readText() }
            ?: error("Unable to read the theme file")
        val spec = parseUserJson(raw)
        saveCustomSpec(context, spec, ExpressiveThemePreset.CUSTOM_JSON, "Imported JSON theme")
        spec
    }

    fun importImage(context: Context, source: Uri): Result<SpotuiThemeSpec> = runCatching {
        val bitmap = context.contentResolver.openInputStream(source)?.use(BitmapFactory::decodeStream)
            ?: error("Unable to decode the selected image")
        val palette = Palette.from(bitmap).maximumColorCount(24).generate()
        val primary = palette.vibrantSwatch?.rgb
            ?: palette.dominantSwatch?.rgb
            ?: palette.mutedSwatch?.rgb
            ?: error("The selected image has no usable color palette")
        val secondary = palette.lightVibrantSwatch?.rgb ?: palette.lightMutedSwatch?.rgb ?: lighten(primary, 0.22f)
        val tertiary = palette.darkVibrantSwatch?.rgb ?: palette.darkMutedSwatch?.rgb ?: rotateHue(primary, 42f)
        val spec = fromSeed(
            name = "Image-derived theme",
            preset = ExpressiveThemePreset.IMAGE_DERIVED,
            seed = primary,
            secondaryOverride = secondary,
            tertiaryOverride = tertiary,
            description = "Derived locally from the selected image",
        )
        prefs(context).edit().putString(KEY_IMAGE_URI, source.toString()).apply()
        saveCustomSpec(context, spec, ExpressiveThemePreset.IMAGE_DERIVED, spec.sourceDescription)
        spec
    }

    fun resetCustomTheme(context: Context) {
        prefs(context).edit().remove(KEY_SPEC).remove(KEY_IMAGE_URI).putString(KEY_PRESET, ExpressiveThemePreset.SPOTUI_BLUE.name).apply()
        com.music.spotui.data.preferences.signalUiSettingsChanged()
    }

    private fun saveCustomSpec(context: Context, spec: SpotuiThemeSpec, preset: ExpressiveThemePreset, description: String) {
        val canonical = JSONObject().apply {
            put("name", spec.name)
            put("primary", colorString(spec.primaryArgb))
            put("secondary", colorString(spec.secondaryArgb))
            put("tertiary", colorString(spec.tertiaryArgb))
            put("background", colorString(spec.backgroundArgb))
            put("surface", colorString(spec.surfaceArgb))
            put("onPrimary", colorString(spec.onPrimaryArgb))
            put("onBackground", colorString(spec.onBackgroundArgb))
            put("dark", spec.dark)
            put("sourceDescription", description)
        }.toString()
        prefs(context).edit().putString(KEY_SPEC, canonical).putString(KEY_PRESET, preset.name).apply()
        com.music.spotui.data.preferences.signalUiSettingsChanged()
    }

    private fun decodeSpec(raw: String): SpotuiThemeSpec? = runCatching { parseUserJson(raw) }.getOrNull()

    private fun parseUserJson(raw: String): SpotuiThemeSpec {
        val json = JSONObject(raw)
        val name = json.optString("name", "Custom Material theme").take(64).ifBlank { "Custom Material theme" }
        val primary = parseColor(json.requireString("primary"))
        val secondary = parseColor(json.optString("secondary", colorString(lighten(primary, 0.18f))))
        val tertiary = parseColor(json.optString("tertiary", colorString(rotateHue(primary, 42f))))
        val dark = json.optBoolean("dark", true)
        val background = parseColor(json.optString("background", colorString(if (dark) darken(primary, 0.90f) else lighten(primary, 0.93f))))
        val surface = parseColor(json.optString("surface", colorString(if (dark) darken(primary, 0.82f) else lighten(primary, 0.86f))))
        val onPrimary = parseColor(json.optString("onPrimary", colorString(contrastColor(primary))))
        val onBackground = parseColor(json.optString("onBackground", colorString(contrastColor(background))))
        return SpotuiThemeSpec(name, ExpressiveThemePreset.CUSTOM_JSON, primary, secondary, tertiary, background, surface, onPrimary, onBackground, dark, json.optString("sourceDescription", "Imported JSON theme"))
    }

    private fun JSONObject.requireString(name: String): String = optString(name).takeIf { it.isNotBlank() }
        ?: error("The theme JSON must provide a $name color")

    fun presetSpec(preset: ExpressiveThemePreset): SpotuiThemeSpec = when (preset) {
        ExpressiveThemePreset.SYSTEM -> fromSeed("System dynamic", preset, preset.seed, description = "Android wallpaper colors when available")
        ExpressiveThemePreset.AURORA -> fromSeed("Aurora", preset, preset.seed, secondaryOverride = 0xFF56D7F1.toInt(), tertiaryOverride = 0xFFFF8FE9.toInt(), description = preset.description)
        ExpressiveThemePreset.SUNSET -> fromSeed("Sunset", preset, preset.seed, secondaryOverride = 0xFFFFB15C.toInt(), tertiaryOverride = 0xFFFFA5B4.toInt(), description = preset.description)
        ExpressiveThemePreset.FOREST -> fromSeed("Forest", preset, preset.seed, secondaryOverride = 0xFF9EE8C9.toInt(), tertiaryOverride = 0xFF87BFFF.toInt(), description = preset.description)
        ExpressiveThemePreset.MONOCHROME -> fromSeed("Monochrome", preset, preset.seed, secondaryOverride = 0xFFD4D8E8.toInt(), tertiaryOverride = 0xFF9AA7FF.toInt(), description = preset.description)
        else -> fromSeed("Spotui Blue", ExpressiveThemePreset.SPOTUI_BLUE, ExpressiveThemePreset.SPOTUI_BLUE.seed, description = ExpressiveThemePreset.SPOTUI_BLUE.description)
    }

    private fun fromSeed(
        name: String,
        preset: ExpressiveThemePreset,
        seed: Int,
        secondaryOverride: Int? = null,
        tertiaryOverride: Int? = null,
        description: String,
    ): SpotuiThemeSpec = SpotuiThemeSpec(
        name = name,
        preset = preset,
        primaryArgb = seed,
        secondaryArgb = secondaryOverride ?: lighten(seed, 0.18f),
        tertiaryArgb = tertiaryOverride ?: rotateHue(seed, 42f),
        backgroundArgb = darken(seed, 0.91f),
        surfaceArgb = darken(seed, 0.82f),
        onPrimaryArgb = contrastColor(seed),
        onBackgroundArgb = 0xFFFFFFFF.toInt(),
        dark = true,
        sourceDescription = description,
    )

    private fun parseColor(value: String): Int {
        val clean = value.trim().removePrefix("#")
        require(clean.length == 6 || clean.length == 8) { "Colors must use #RRGGBB or #AARRGGBB" }
        require(clean.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) { "Invalid color value: $value" }
        return android.graphics.Color.parseColor("#$clean")
    }

    private fun colorString(color: Int): String = String.format("#%08X", color)

    private fun lighten(color: Int, amount: Float): Int = blend(color, 0xFFFFFFFF.toInt(), amount)
    private fun darken(color: Int, amount: Float): Int = blend(color, 0xFF000000.toInt(), amount)
    private fun blend(from: Int, to: Int, amount: Float): Int {
        val f = amount.coerceIn(0f, 1f)
        fun c(a: Int, b: Int, shift: Int) = (((a ushr shift) and 0xFF) * (1f - f) + ((b ushr shift) and 0xFF) * f).toInt().coerceIn(0, 255)
        return (c(from, to, 24) shl 24) or (c(from, to, 16) shl 16) or (c(from, to, 8) shl 8) or c(from, to, 0)
    }

    private fun rotateHue(color: Int, degrees: Float): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color, hsv)
        hsv[0] = (hsv[0] + degrees + 360f) % 360f
        hsv[1] = (hsv[1] * 0.78f + 0.18f).coerceIn(0f, 1f)
        hsv[2] = (hsv[2] * 1.08f).coerceIn(0f, 1f)
        return android.graphics.Color.HSVToColor(android.graphics.Color.alpha(color), hsv)
    }

    private fun contrastColor(color: Int): Int {
        val luma = (0.2126 * android.graphics.Color.red(color) + 0.7152 * android.graphics.Color.green(color) + 0.0722 * android.graphics.Color.blue(color)) / 255.0
        return if (luma > 0.58) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }
}
