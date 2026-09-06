package com.music.spotui.data.preferences

import android.content.Context
import android.net.ConnectivityManager
import com.metrolist.music.constants.AudioQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * User-facing audio quality tiers (Spotify-style). Each maps to the YouTube format
 * selector ([AudioQuality]) and whether to attempt a lossless FLAC source first.
 */
enum class StreamQuality(
    val label: String,
    val detail: String,
    val audioQuality: AudioQuality,
    val lossless: Boolean,
) {
    LOW("Low", "Data saver — smallest size", AudioQuality.LOW, false),
    NORMAL("Normal", "Balanced for the network", AudioQuality.AUTO, false),
    HIGH("High", "Best compressed quality", AudioQuality.HIGH, false),
    LOSSLESS("Lossless", "FLAC when available, else High", AudioQuality.HIGH, true),
}

enum class LosslessTimeout(
    val label: String,
    val detail: String,
    val timeoutMs: Long,
) {
    SHORT("Short wait", "Fast (4s) cutoff", 4_000L),
    LONG("Long wait", "Patient (8s) cutoff", 8_000L),
}

/** Spatial processing profiles. Each profile is rendered through the device audio session. */
enum class SpatialProfile(val label: String, val detail: String) {
    STUDIO("Studio", "Subtle width with a focused center image"),
    WIDE("Wide", "Expanded stereo presentation"),
    IMMERSIVE("Immersive", "Maximum supported surround processing"),
    CINEMA("Cinema", "Large-room depth for albums and live sets"),
}

/** Primary navigation placement for the application shell. */
enum class NavigationMode(val label: String, val detail: String) {
    BOTTOM_BAR("Bottom bar", "Classic thumb-friendly navigation"),
    TOP_BAR("Top bar", "Tabs at the top of the screen"),
    SIDE_BAR("Side bar", "Expanded navigation drawer style"),
    NAVIGATION_RAIL("Navigation rail", "Compact vertical navigation rail"),
}

private const val PREF = "settings_prefs"
private const val KEY_WIFI_Q = "stream_quality_wifi"
private const val KEY_CELL_Q = "stream_quality_cellular"
private const val KEY_DL_Q = "download_quality"
private const val KEY_WIFI_LOSSLESS_TIMEOUT = "lossless_timeout_wifi"
private const val KEY_CELL_LOSSLESS_TIMEOUT = "lossless_timeout_cellular"
private const val KEY_DL_LOSSLESS_TIMEOUT = "lossless_timeout_download"
private const val KEY_PRELOAD = "preload_enabled"
private const val KEY_CROSSFADE_MS = "crossfade_duration_ms"
private const val KEY_CROSSFADE_DJ = "crossfade_dj_mode"
private const val KEY_GAPLESS_PLAYBACK = "gapless_playback_enabled"
private const val KEY_WEB_PLAYBACK = "web_playback_enabled"
private const val KEY_VIDEO_FALLBACK = "video_fallback_enabled"
private const val KEY_LIBRARY_GRID = "library_grid_view"
private const val KEY_AUTO_PLAY = "auto_play_startup"
private const val KEY_IGNORE_BATTERY_OPT = "ignore_battery_optimization"
private const val KEY_UPDATE_REPO_URL = "update_repo_url"
private const val KEY_SPATIAL_PROFILE = "audio_spatial_profile"
private const val KEY_SPATIAL_STRENGTH = "audio_spatial_strength"
private const val KEY_NORMALIZER_GAIN_MB = "audio_normalizer_gain_mb"
private const val KEY_NAVIGATION_MODE = "ui_navigation_mode"
private const val KEY_COMPACT_UI = "ui_compact_mode"
private const val KEY_CORNER_RADIUS_DP = "ui_corner_radius_dp"
private const val KEY_YT_MUSIC_WEB_ENABLED = "youtube_music_web_enabled"
private const val KEY_PLAYER_VIDEO_PREVIEW = "player_video_preview_enabled"
const val DEFAULT_UPDATE_REPO_URL = "https://github.com/H4zh4n/Spotui"

/** Off (0s) … 12s. 0 disables crossfade. */
const val CROSSFADE_MIN_MS = 0
const val CROSSFADE_MAX_MS = 12000
const val CROSSFADE_DEFAULT_MS = 6000

private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

private val uiSettingsRevision = MutableStateFlow(0)
val uiSettingsUpdates = uiSettingsRevision.asStateFlow()
private fun notifyUiSettingsChanged() { uiSettingsRevision.value += 1 }

/** Allows appearance/accessibility modules to request an app-wide settings recomposition. */
fun signalUiSettingsChanged() { notifyUiSettingsChanged() }

private fun readQ(c: Context, key: String, def: StreamQuality): StreamQuality =
    runCatching { StreamQuality.valueOf(prefs(c).getString(key, def.name)!!) }.getOrDefault(def)

private fun writeQ(c: Context, key: String, q: StreamQuality) =
    prefs(c).edit().putString(key, q.name).apply()

fun getWifiQuality(c: Context): StreamQuality = readQ(c, KEY_WIFI_Q, StreamQuality.HIGH)
fun setWifiQuality(c: Context, q: StreamQuality) = writeQ(c, KEY_WIFI_Q, q)

fun getCellularQuality(c: Context): StreamQuality = readQ(c, KEY_CELL_Q, StreamQuality.NORMAL)
fun setCellularQuality(c: Context, q: StreamQuality) = writeQ(c, KEY_CELL_Q, q)

fun getDownloadQuality(c: Context): StreamQuality = readQ(c, KEY_DL_Q, StreamQuality.LOSSLESS)
fun setDownloadQuality(c: Context, q: StreamQuality) = writeQ(c, KEY_DL_Q, q)

fun getWifiLosslessTimeout(c: Context): LosslessTimeout =
    runCatching { LosslessTimeout.valueOf(prefs(c).getString(KEY_WIFI_LOSSLESS_TIMEOUT, LosslessTimeout.SHORT.name)!!) }
        .getOrDefault(LosslessTimeout.SHORT)

fun setWifiLosslessTimeout(c: Context, timeout: LosslessTimeout) =
    prefs(c).edit().putString(KEY_WIFI_LOSSLESS_TIMEOUT, timeout.name).apply()

fun getCellularLosslessTimeout(c: Context): LosslessTimeout =
    runCatching { LosslessTimeout.valueOf(prefs(c).getString(KEY_CELL_LOSSLESS_TIMEOUT, LosslessTimeout.SHORT.name)!!) }
        .getOrDefault(LosslessTimeout.SHORT)

fun setCellularLosslessTimeout(c: Context, timeout: LosslessTimeout) =
    prefs(c).edit().putString(KEY_CELL_LOSSLESS_TIMEOUT, timeout.name).apply()

fun getDownloadLosslessTimeout(c: Context): LosslessTimeout =
    runCatching { LosslessTimeout.valueOf(prefs(c).getString(KEY_DL_LOSSLESS_TIMEOUT, LosslessTimeout.LONG.name)!!) }
        .getOrDefault(LosslessTimeout.LONG)

fun setDownloadLosslessTimeout(c: Context, timeout: LosslessTimeout) =
    prefs(c).edit().putString(KEY_DL_LOSSLESS_TIMEOUT, timeout.name).apply()

fun getLosslessTimeout(c: Context): LosslessTimeout = currentLosslessTimeout(c)

/** Library layout: false = rows (default), true = Spotify-style 3-column grid. */
fun isLibraryGridView(c: Context): Boolean = prefs(c).getBoolean(KEY_LIBRARY_GRID, false)
fun setLibraryGridView(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_LIBRARY_GRID, v).apply()

fun isPreloadEnabled(c: Context): Boolean = prefs(c).getBoolean(KEY_PRELOAD, true)
fun setPreloadEnabled(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_PRELOAD, v).apply()

/**
 * YouTube account cookie (captured from an in-app WebView login). Passed to the
 * InnerTube client so age-restricted / login-required videos resolve. Empty when
 * not signed in — the app then uses anonymous YouTube access.
 */
private const val KEY_YT_COOKIE = "youtube_cookie"
fun getYoutubeCookie(c: Context): String = prefs(c).getString(KEY_YT_COOKIE, "").orEmpty()
fun setYoutubeCookie(c: Context, v: String) = prefs(c).edit().putString(KEY_YT_COOKIE, v).apply()
fun isYoutubeLoggedIn(c: Context): Boolean = getYoutubeCookie(c).contains("SAPISID")

/** Optional user-authorized YouTube Music web companion. Login occurs in the user-facing web view; no account secret is stored by this preference. */
fun isYoutubeMusicWebEnabled(c: Context): Boolean = prefs(c).getBoolean(KEY_YT_MUSIC_WEB_ENABLED, false)
fun setYoutubeMusicWebEnabled(c: Context, enabled: Boolean) = prefs(c).edit().putBoolean(KEY_YT_MUSIC_WEB_ENABLED, enabled).apply()

/** Controls whether the player exposes the opt-in video preview surface. */
fun isPlayerVideoPreviewEnabled(c: Context): Boolean = prefs(c).getBoolean(KEY_PLAYER_VIDEO_PREVIEW, false)
fun setPlayerVideoPreviewEnabled(c: Context, enabled: Boolean) = prefs(c).edit().putBoolean(KEY_PLAYER_VIDEO_PREVIEW, enabled).apply()

/**
 * Play audio through Spotify's own web player in a hidden WebView (real Spotify
 * streaming, no decryption/bypass). DEFAULT OFF (and hidden from Settings) — the
 * YouTube/FLAC engine is the primary source; this path is real-time only with no
 * download/crossfade support.
 */
fun isWebPlaybackEnabled(c: Context): Boolean = prefs(c).getBoolean(KEY_WEB_PLAYBACK, false)
fun setWebPlaybackEnabled(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_WEB_PLAYBACK, v).apply()

fun isAutoPlayEnabled(c: Context): Boolean = prefs(c).getBoolean(KEY_AUTO_PLAY, false)
fun setAutoPlayEnabled(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_AUTO_PLAY, v).apply()

fun isVideoFallbackEnabled(c: Context): Boolean = prefs(c).getBoolean(KEY_VIDEO_FALLBACK, true)
fun setVideoFallbackEnabled(c: Context, v: Boolean) =
    prefs(c).edit().putBoolean(KEY_VIDEO_FALLBACK, v).apply()

/**
 * Crossfade overlap length in ms (0 = off). When > 0, the end of each track is blended
 * into the start of the next over this window.
 */
fun getCrossfadeMs(c: Context): Int = prefs(c).getInt(KEY_CROSSFADE_MS, 0)
fun setCrossfadeMs(c: Context, ms: Int) =
    prefs(c).edit().putInt(KEY_CROSSFADE_MS, ms.coerceIn(CROSSFADE_MIN_MS, CROSSFADE_MAX_MS)).apply()

fun isCrossfadeEnabled(c: Context): Boolean = getCrossfadeMs(c) > 0

/** Prepares and promotes the next queue stream at the end of a track without an audible fade. */
fun isGaplessPlaybackEnabled(c: Context): Boolean = prefs(c).getBoolean(KEY_GAPLESS_PLAYBACK, true)
fun setGaplessPlaybackEnabled(c: Context, enabled: Boolean) =
    prefs(c).edit().putBoolean(KEY_GAPLESS_PLAYBACK, enabled).apply()

/** DJ-style mixing: low-pass the outgoing track and high-pass the incoming one during the blend. */
fun isCrossfadeDjMode(c: Context): Boolean = prefs(c).getBoolean(KEY_CROSSFADE_DJ, false)
fun setCrossfadeDjMode(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_CROSSFADE_DJ, v).apply()

fun isIgnoreBatteryOptimization(c: Context): Boolean = prefs(c).getBoolean(KEY_IGNORE_BATTERY_OPT, false)
fun setIgnoreBatteryOptimization(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_IGNORE_BATTERY_OPT, v).apply()

fun getUpdateRepoUrl(c: Context): String =
    prefs(c).getString(KEY_UPDATE_REPO_URL, DEFAULT_UPDATE_REPO_URL).orEmpty().ifBlank { DEFAULT_UPDATE_REPO_URL }

fun setUpdateRepoUrl(c: Context, url: String) =
    prefs(c).edit().putString(KEY_UPDATE_REPO_URL, url).apply()

fun resetUpdateRepoUrl(c: Context) =
    prefs(c).edit().remove(KEY_UPDATE_REPO_URL).apply()

/**
 * The streaming quality to use for the *current* active network: the cellular setting
 * on a metered connection (mobile data / metered hotspot), the Wi-Fi setting otherwise.
 */
fun currentStreamingQuality(c: Context): StreamQuality {
    val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    return if (cm.isActiveNetworkMetered) getCellularQuality(c) else getWifiQuality(c)
}

fun currentLosslessTimeout(c: Context): LosslessTimeout {
    val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    return if (cm.isActiveNetworkMetered) getCellularLosslessTimeout(c) else getWifiLosslessTimeout(c)
}

private const val KEY_EQ_PRESET = "audio_eq_preset"
private const val KEY_EQ_BASS = "audio_eq_bass"
private const val KEY_EQ_VOCAL = "audio_eq_vocal"
private const val KEY_EQ_TREBLE = "audio_eq_treble"
private const val KEY_EQ_LOW_MID = "audio_eq_low_mid"
private const val KEY_EQ_HIGH_MID = "audio_eq_high_mid"
private const val KEY_EQ_SPATIAL = "audio_eq_spatial"
private const val KEY_AUDIO_NORMALIZER = "audio_volume_normalizer"

fun getEqPreset(c: Context): String = prefs(c).getString(KEY_EQ_PRESET, "Flat").orEmpty()
fun setEqPreset(c: Context, v: String) = prefs(c).edit().putString(KEY_EQ_PRESET, v).apply()

fun getEqBass(c: Context): Float = prefs(c).getFloat(KEY_EQ_BASS, 50f)
fun setEqBass(c: Context, v: Float) = prefs(c).edit().putFloat(KEY_EQ_BASS, v).apply()

fun getEqVocal(c: Context): Float = prefs(c).getFloat(KEY_EQ_VOCAL, 50f)
fun setEqVocal(c: Context, v: Float) = prefs(c).edit().putFloat(KEY_EQ_VOCAL, v).apply()

fun getEqTreble(c: Context): Float = prefs(c).getFloat(KEY_EQ_TREBLE, 50f)
fun setEqTreble(c: Context, v: Float) = prefs(c).edit().putFloat(KEY_EQ_TREBLE, v).apply()

fun getEqLowMid(c: Context): Float = prefs(c).getFloat(KEY_EQ_LOW_MID, 50f)
fun setEqLowMid(c: Context, v: Float) = prefs(c).edit().putFloat(KEY_EQ_LOW_MID, v.coerceIn(0f, 100f)).apply()

fun getEqHighMid(c: Context): Float = prefs(c).getFloat(KEY_EQ_HIGH_MID, 50f)
fun setEqHighMid(c: Context, v: Float) = prefs(c).edit().putFloat(KEY_EQ_HIGH_MID, v.coerceIn(0f, 100f)).apply()

fun isEqSpatialEnabled(c: Context): Boolean = prefs(c).getBoolean(KEY_EQ_SPATIAL, false)
fun setEqSpatialEnabled(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_EQ_SPATIAL, v).apply()

fun isAudioNormalizerEnabled(c: Context): Boolean = prefs(c).getBoolean(KEY_AUDIO_NORMALIZER, true)
fun setAudioNormalizerEnabled(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_AUDIO_NORMALIZER, v).apply()

/** Device effect strength is expressed in millibels; 0–600 keeps the effect useful without an aggressive gain jump. */
fun getAudioNormalizerGainMb(c: Context): Int = prefs(c).getInt(KEY_NORMALIZER_GAIN_MB, 250).coerceIn(0, 600)
fun setAudioNormalizerGainMb(c: Context, v: Int) = prefs(c).edit().putInt(KEY_NORMALIZER_GAIN_MB, v.coerceIn(0, 600)).apply()

fun getSpatialProfile(c: Context): SpatialProfile =
    runCatching { SpatialProfile.valueOf(prefs(c).getString(KEY_SPATIAL_PROFILE, SpatialProfile.IMMERSIVE.name)!!) }
        .getOrDefault(SpatialProfile.IMMERSIVE)
fun setSpatialProfile(c: Context, profile: SpatialProfile) = prefs(c).edit().putString(KEY_SPATIAL_PROFILE, profile.name).apply()

/** Framework virtualizer strength: 0–1000. */
fun getSpatialStrength(c: Context): Int = prefs(c).getInt(KEY_SPATIAL_STRENGTH, 900).coerceIn(0, 1000)
fun setSpatialStrength(c: Context, strength: Int) = prefs(c).edit().putInt(KEY_SPATIAL_STRENGTH, strength.coerceIn(0, 1000)).apply()

fun getNavigationMode(c: Context): NavigationMode =
    runCatching { NavigationMode.valueOf(prefs(c).getString(KEY_NAVIGATION_MODE, NavigationMode.BOTTOM_BAR.name)!!) }
        .getOrDefault(NavigationMode.BOTTOM_BAR)
fun setNavigationMode(c: Context, mode: NavigationMode) {
    prefs(c).edit().putString(KEY_NAVIGATION_MODE, mode.name).apply()
    notifyUiSettingsChanged()
}

fun isCompactUiEnabled(c: Context): Boolean = prefs(c).getBoolean(KEY_COMPACT_UI, false)
fun setCompactUiEnabled(c: Context, v: Boolean) {
    prefs(c).edit().putBoolean(KEY_COMPACT_UI, v).apply()
    notifyUiSettingsChanged()
}

/** Global shell and settings surface rounding, exposed as a 0–48 dp user preference. */
fun getCornerRadiusDp(c: Context): Float = prefs(c).getFloat(KEY_CORNER_RADIUS_DP, 16f).coerceIn(0f, 48f)
fun setCornerRadiusDp(c: Context, value: Float) {
    prefs(c).edit().putFloat(KEY_CORNER_RADIUS_DP, value.coerceIn(0f, 48f)).apply()
    notifyUiSettingsChanged()
}

private const val KEY_GEMINI_API_KEY = "gemini_api_key"
private const val KEY_GEMINI_FREE_MODE = "gemini_free_mode"
private const val KEY_GEMINI_MODEL = "gemini_model_selection"

fun getGeminiApiKey(c: Context): String = prefs(c).getString(KEY_GEMINI_API_KEY, "").orEmpty()
fun setGeminiApiKey(c: Context, key: String) = prefs(c).edit().putString(KEY_GEMINI_API_KEY, key.trim()).apply()

fun isGeminiFreeMode(c: Context): Boolean = prefs(c).getBoolean(KEY_GEMINI_FREE_MODE, true)
fun setGeminiFreeMode(c: Context, enabled: Boolean) = prefs(c).edit().putBoolean(KEY_GEMINI_FREE_MODE, enabled).apply()

fun getGeminiModel(c: Context): String = prefs(c).getString(KEY_GEMINI_MODEL, "gemini-2.5-flash").orEmpty().ifBlank { "gemini-2.5-flash" }
fun setGeminiModel(c: Context, model: String) = prefs(c).edit().putString(KEY_GEMINI_MODEL, model).apply()

