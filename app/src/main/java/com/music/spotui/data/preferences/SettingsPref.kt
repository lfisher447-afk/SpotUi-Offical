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

/** Spatial processing profiles. Each profile is rendered through the device audio session & PCM DSP. */
enum class SpatialProfile(val label: String, val detail: String) {
    STUDIO("Studio", "Subtle width with a focused center image"),
    WIDE("Wide", "Expanded stereo presentation"),
    IMMERSIVE("Immersive", "Maximum supported surround processing"),
    CINEMA("Cinema", "Large-room depth for albums and live sets"),
    CONCERT_HALL("Concert Hall", "Natural acoustic reflections with warm stage width"),
    BINAURAL_HEADPHONES("Headphone 3D", "HRTF crossfeed & ear-delay imaging for headphones"),
}

enum class BufferProfile(val label: String, val detail: String, val minMs: Int, val maxMs: Int, val playbackMs: Int, val rebufferMs: Int) {
    LOW_LATENCY("Low Latency", "Instant start & fast seeking", 1500, 12000, 750, 1500),
    BALANCED("Balanced", "Smooth streaming with smart memory use", 4000, 30000, 1500, 3000),
    HIGH_STABILITY("High Stability", "Extra anti-stutter buffer for background & weak networks", 8000, 60000, 2500, 5000),
}

enum class AudioFocusMode(val label: String, val detail: String) {
    PAUSE_AND_DUCK("Standard (Pause & Duck)", "Pause for calls, duck briefly for notifications"),
    STRICT_EXCLUSIVE("Strict Focus", "Always pause when another app requests audio"),
    MIX_IGNORE_FOCUS("Mix / Ignore Focus", "Keep playing over notifications & navigation prompts"),
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
const val DEFAULT_UPDATE_REPO_URL = "https://github.com/lfisher447-afk/SpotUi-Offical"

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

private fun readQ(c: Context, key: String, def: StreamQuality): StreamQuality {
    val raw = prefs(c).getString(key, def.name)
    return raw?.let { runCatching { StreamQuality.valueOf(it) }.getOrNull() } ?: def
}

private fun writeQ(c: Context, key: String, q: StreamQuality) =
    prefs(c).edit().putString(key, q.name).apply()

fun getWifiQuality(c: Context): StreamQuality = readQ(c, KEY_WIFI_Q, StreamQuality.HIGH)
fun setWifiQuality(c: Context, q: StreamQuality) = writeQ(c, KEY_WIFI_Q, q)

fun getCellularQuality(c: Context): StreamQuality = readQ(c, KEY_CELL_Q, StreamQuality.NORMAL)
fun setCellularQuality(c: Context, q: StreamQuality) = writeQ(c, KEY_CELL_Q, q)

fun getDownloadQuality(c: Context): StreamQuality = readQ(c, KEY_DL_Q, StreamQuality.LOSSLESS)
fun setDownloadQuality(c: Context, q: StreamQuality) = writeQ(c, KEY_DL_Q, q)

fun getWifiLosslessTimeout(c: Context): LosslessTimeout {
    val raw = prefs(c).getString(KEY_WIFI_LOSSLESS_TIMEOUT, LosslessTimeout.SHORT.name)
    return raw?.let { runCatching { LosslessTimeout.valueOf(it) }.getOrNull() } ?: LosslessTimeout.SHORT
}

fun setWifiLosslessTimeout(c: Context, timeout: LosslessTimeout) =
    prefs(c).edit().putString(KEY_WIFI_LOSSLESS_TIMEOUT, timeout.name).apply()

fun getCellularLosslessTimeout(c: Context): LosslessTimeout {
    val raw = prefs(c).getString(KEY_CELL_LOSSLESS_TIMEOUT, LosslessTimeout.SHORT.name)
    return raw?.let { runCatching { LosslessTimeout.valueOf(it) }.getOrNull() } ?: LosslessTimeout.SHORT
}

fun setCellularLosslessTimeout(c: Context, timeout: LosslessTimeout) =
    prefs(c).edit().putString(KEY_CELL_LOSSLESS_TIMEOUT, timeout.name).apply()

fun getDownloadLosslessTimeout(c: Context): LosslessTimeout {
    val raw = prefs(c).getString(KEY_DL_LOSSLESS_TIMEOUT, LosslessTimeout.LONG.name)
    return raw?.let { runCatching { LosslessTimeout.valueOf(it) }.getOrNull() } ?: LosslessTimeout.LONG
}

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
fun setYoutubeCookie(c: Context, v: String) {
    prefs(c).edit().putString(KEY_YT_COOKIE, v).apply()
    com.metrolist.innertube.network.AuthSessionStore.updateFromWebViewCookies(v.ifBlank { null })
}
fun isYoutubeLoggedIn(c: Context): Boolean =
    com.metrolist.innertube.utils.hasYouTubeLoginCookie(getYoutubeCookie(c))

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

fun getUpdateRepoUrl(c: Context): String {
    val saved = prefs(c).getString(KEY_UPDATE_REPO_URL, DEFAULT_UPDATE_REPO_URL).orEmpty().trim()
    if (saved.isBlank() || saved.trimEnd('/').equals("https://github.com/H4zh4n/Spotui", ignoreCase = true)) {
        return DEFAULT_UPDATE_REPO_URL
    }
    return saved
}

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

fun getSpatialProfile(c: Context): SpatialProfile {
    val raw = prefs(c).getString(KEY_SPATIAL_PROFILE, SpatialProfile.IMMERSIVE.name)
    return raw?.let { runCatching { SpatialProfile.valueOf(it) }.getOrNull() } ?: SpatialProfile.IMMERSIVE
}
fun setSpatialProfile(c: Context, profile: SpatialProfile) = prefs(c).edit().putString(KEY_SPATIAL_PROFILE, profile.name).apply()

/** Framework virtualizer strength: 0–1000. */
fun getSpatialStrength(c: Context): Int = prefs(c).getInt(KEY_SPATIAL_STRENGTH, 900).coerceIn(0, 1000)
fun setSpatialStrength(c: Context, strength: Int) = prefs(c).edit().putInt(KEY_SPATIAL_STRENGTH, strength.coerceIn(0, 1000)).apply()

fun getNavigationMode(c: Context): NavigationMode {
    val raw = prefs(c).getString(KEY_NAVIGATION_MODE, NavigationMode.BOTTOM_BAR.name)
    return raw?.let { runCatching { NavigationMode.valueOf(it) }.getOrNull() } ?: NavigationMode.BOTTOM_BAR
}
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

private const val KEY_EXPLICIT_CONTENT_ALLOWED = "explicit_content_allowed"
private const val KEY_PREFER_EXPLICIT_VERSIONS = "prefer_explicit_versions"
private const val KEY_EXPLICIT_BADGE_VISIBLE = "explicit_badge_visible"
private const val KEY_ONE_UI_HEADER_ENABLED = "one_ui_header_enabled"
private const val KEY_ONE_UI_SQUIRCLE_ICONS = "one_ui_squircle_icons"

fun isExplicitContentAllowed(c: Context): Boolean = prefs(c).getBoolean(KEY_EXPLICIT_CONTENT_ALLOWED, true)
fun setExplicitContentAllowed(c: Context, allowed: Boolean) {
    prefs(c).edit().putBoolean(KEY_EXPLICIT_CONTENT_ALLOWED, allowed).apply()
    notifyUiSettingsChanged()
}

fun isPreferExplicitVersions(c: Context): Boolean = prefs(c).getBoolean(KEY_PREFER_EXPLICIT_VERSIONS, true)
fun setPreferExplicitVersions(c: Context, prefer: Boolean) {
    prefs(c).edit().putBoolean(KEY_PREFER_EXPLICIT_VERSIONS, prefer).apply()
    notifyUiSettingsChanged()
}

fun isExplicitBadgeVisible(c: Context): Boolean = prefs(c).getBoolean(KEY_EXPLICIT_BADGE_VISIBLE, true)
fun setExplicitBadgeVisible(c: Context, visible: Boolean) {
    prefs(c).edit().putBoolean(KEY_EXPLICIT_BADGE_VISIBLE, visible).apply()
    notifyUiSettingsChanged()
}

fun isOneUiHeaderEnabled(c: Context): Boolean = prefs(c).getBoolean(KEY_ONE_UI_HEADER_ENABLED, true)
fun setOneUiHeaderEnabled(c: Context, enabled: Boolean) {
    prefs(c).edit().putBoolean(KEY_ONE_UI_HEADER_ENABLED, enabled).apply()
    notifyUiSettingsChanged()
}

fun isOneUiSquircleIcons(c: Context): Boolean = prefs(c).getBoolean(KEY_ONE_UI_SQUIRCLE_ICONS, true)
fun setOneUiSquircleIcons(c: Context, enabled: Boolean) {
    prefs(c).edit().putBoolean(KEY_ONE_UI_SQUIRCLE_ICONS, enabled).apply()
    notifyUiSettingsChanged()
}

fun getGeminiApiKey(c: Context): String = prefs(c).getString(KEY_GEMINI_API_KEY, "").orEmpty()
fun setGeminiApiKey(c: Context, key: String) = prefs(c).edit().putString(KEY_GEMINI_API_KEY, key.trim()).apply()

fun isGeminiFreeMode(c: Context): Boolean = prefs(c).getBoolean(KEY_GEMINI_FREE_MODE, true)
fun setGeminiFreeMode(c: Context, enabled: Boolean) = prefs(c).edit().putBoolean(KEY_GEMINI_FREE_MODE, enabled).apply()

fun getGeminiModel(c: Context): String = prefs(c).getString(KEY_GEMINI_MODEL, "gemini-2.5-flash").orEmpty().ifBlank { "gemini-2.5-flash" }
fun setGeminiModel(c: Context, model: String) = prefs(c).edit().putString(KEY_GEMINI_MODEL, model).apply()

private const val KEY_DOH_PRESET = "network_doh_preset"
private const val KEY_WEB_CLIENT_POTOKEN = "innertube_web_client_potoken"
private const val KEY_PROXY_URLS = "network_proxy_urls"
private const val KEY_IP_ROTATION = "network_ip_rotation_enabled"
private const val KEY_STREAM_BYPASS_PROXY = "network_stream_bypass_proxy"

fun getDnsPreset(c: Context): com.metrolist.innertube.network.DnsOverHttpsPreset =
    com.metrolist.innertube.network.DnsOverHttpsPreset.fromName(prefs(c).getString(KEY_DOH_PRESET, null))

fun setDnsPreset(c: Context, preset: com.metrolist.innertube.network.DnsOverHttpsPreset) {
    prefs(c).edit().putString(KEY_DOH_PRESET, preset.name).apply()
    com.metrolist.innertube.network.AuthSessionStore.dnsPreset = preset
}

fun isWebClientPoTokenEnabled(c: Context): Boolean =
    prefs(c).getBoolean(KEY_WEB_CLIENT_POTOKEN, true)

fun setWebClientPoTokenEnabled(c: Context, enabled: Boolean) {
    prefs(c).edit().putBoolean(KEY_WEB_CLIENT_POTOKEN, enabled).apply()
    com.metrolist.innertube.network.AuthSessionStore.webClientPoTokenEnabled = enabled
}

fun getProxyUrls(c: Context): String =
    prefs(c).getString(KEY_PROXY_URLS, "").orEmpty()

fun setProxyUrls(c: Context, urls: String) {
    prefs(c).edit().putString(KEY_PROXY_URLS, urls.trim()).apply()
    com.metrolist.innertube.network.AuthSessionStore.applyProxyConfiguration(urls)
}

fun isIpRotationEnabled(c: Context): Boolean =
    prefs(c).getBoolean(KEY_IP_ROTATION, false)

fun setIpRotationEnabled(c: Context, enabled: Boolean) {
    prefs(c).edit().putBoolean(KEY_IP_ROTATION, enabled).apply()
    com.metrolist.innertube.network.AuthSessionStore.ipRotationEnabled = enabled
}

fun isStreamBypassProxyEnabled(c: Context): Boolean =
    prefs(c).getBoolean(KEY_STREAM_BYPASS_PROXY, false)

fun setStreamBypassProxyEnabled(c: Context, enabled: Boolean) {
    prefs(c).edit().putBoolean(KEY_STREAM_BYPASS_PROXY, enabled).apply()
    com.metrolist.innertube.network.AuthSessionStore.streamBypassProxy = enabled
}

fun applyNetworkAndAuthSettings(c: Context) {
    com.metrolist.innertube.network.AuthSessionStore.dnsPreset = getDnsPreset(c)
    com.metrolist.innertube.network.AuthSessionStore.webClientPoTokenEnabled = isWebClientPoTokenEnabled(c)
    com.metrolist.innertube.network.AuthSessionStore.ipRotationEnabled = isIpRotationEnabled(c)
    com.metrolist.innertube.network.AuthSessionStore.streamBypassProxy = isStreamBypassProxyEnabled(c)
    com.metrolist.innertube.network.AuthSessionStore.applyProxyConfiguration(getProxyUrls(c))
    val savedCookie = getYoutubeCookie(c).trim()
    if (savedCookie.isNotEmpty() && com.metrolist.innertube.utils.hasYouTubeLoginCookie(savedCookie)) {
        com.metrolist.innertube.network.AuthSessionStore.updateFromWebViewCookies(savedCookie)
    } else {
        com.metrolist.innertube.network.AuthSessionStore.cookie = null
    }
}

private const val KEY_PLAYBACK_SPEED = "audio_playback_speed"
private const val KEY_PLAYBACK_PITCH = "audio_playback_pitch"
private const val KEY_SKIP_SILENCE = "audio_skip_silence"

fun getPlaybackSpeedPref(c: Context): Float = prefs(c).getFloat(KEY_PLAYBACK_SPEED, 1.0f).coerceIn(0.5f, 2.0f)
fun setPlaybackSpeedPref(c: Context, speed: Float) =
    prefs(c).edit().putFloat(KEY_PLAYBACK_SPEED, speed.coerceIn(0.5f, 2.0f)).apply()

fun getPlaybackPitchPref(c: Context): Float = prefs(c).getFloat(KEY_PLAYBACK_PITCH, 1.0f).coerceIn(0.75f, 1.25f)
fun setPlaybackPitchPref(c: Context, pitch: Float) =
    prefs(c).edit().putFloat(KEY_PLAYBACK_PITCH, pitch.coerceIn(0.75f, 1.25f)).apply()

fun isSkipSilenceEnabled(c: Context): Boolean = prefs(c).getBoolean(KEY_SKIP_SILENCE, false)
fun setSkipSilenceEnabled(c: Context, enabled: Boolean) =
    prefs(c).edit().putBoolean(KEY_SKIP_SILENCE, enabled).apply()

private const val KEY_DEEZER_ARL = "lossless_deezer_arl"
private const val KEY_DEEZER_TIER = "lossless_deezer_tier"

fun getDeezerArl(c: Context): String = prefs(c).getString(KEY_DEEZER_ARL, "").orEmpty()
fun setDeezerArl(c: Context, arl: String) {
    val clean = arl.trim()
    prefs(c).edit().putString(KEY_DEEZER_ARL, clean).apply()
    com.metrolist.spotify.SpotiFlac.deezerArl = clean.ifBlank { null }
    com.music.spotui.deezer.DeezerSession.setArl(clean)
    if (clean.isBlank()) setDeezerTier(c, "")
}

fun getDeezerTier(c: Context): String = prefs(c).getString(KEY_DEEZER_TIER, "").orEmpty()
fun setDeezerTier(c: Context, tier: String) =
    prefs(c).edit().putString(KEY_DEEZER_TIER, tier.trim()).apply()

private const val KEY_CUSTOM_LOSSLESS_MIRROR = "lossless_custom_mirror_url"
fun getCustomLosslessMirrorUrl(c: Context): String = prefs(c).getString(KEY_CUSTOM_LOSSLESS_MIRROR, "").orEmpty()
fun setCustomLosslessMirrorUrl(c: Context, url: String) {
    val clean = url.trim().trimEnd('/')
    prefs(c).edit().putString(KEY_CUSTOM_LOSSLESS_MIRROR, clean).apply()
    com.metrolist.spotify.SpotiFlac.customMirrorBaseUrl = clean.ifBlank { null }
}

private const val KEY_STABLE_UPDATES_ONLY = "update_stable_only"
private const val KEY_AUTO_DOWNLOAD_UPDATES = "update_auto_download_in_app"
fun isStableUpdatesOnly(c: Context): Boolean = prefs(c).getBoolean(KEY_STABLE_UPDATES_ONLY, true)
fun setStableUpdatesOnly(c: Context, value: Boolean) =
    prefs(c).edit().putBoolean(KEY_STABLE_UPDATES_ONLY, value).apply()

fun isAutoDownloadUpdatesEnabled(c: Context): Boolean = prefs(c).getBoolean(KEY_AUTO_DOWNLOAD_UPDATES, true)
fun setAutoDownloadUpdatesEnabled(c: Context, value: Boolean) =
    prefs(c).edit().putBoolean(KEY_AUTO_DOWNLOAD_UPDATES, value).apply()

// ── Extended Equalizer, Preamp, Limiter & Spatial DSP Preferences ──
private const val KEY_EQ_ENABLED = "audio_eq_enabled"
private const val KEY_EQ_SUB_BASS = "audio_eq_sub_bass"
private const val KEY_EQ_BRILLIANCE = "audio_eq_brilliance"
private const val KEY_EQ_PREAMP_DB = "audio_eq_preamp_db"
private const val KEY_BASS_BOOST_STRENGTH = "audio_bass_boost_strength"
private const val KEY_LIMITER_ENABLED = "audio_soft_limiter_enabled"
private const val KEY_HEADPHONE_CROSSFEED = "audio_headphone_crossfeed"
private const val KEY_SPATIAL_ROOM_DELAY_MS = "audio_spatial_room_delay_ms"
private const val KEY_NORMALIZER_TARGET_LUFS = "audio_normalizer_target_lufs"

fun isEqEnabled(c: Context): Boolean = prefs(c).getBoolean(KEY_EQ_ENABLED, true)
fun setEqEnabled(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_EQ_ENABLED, v).apply()

fun getEqSubBass(c: Context): Float = prefs(c).getFloat(KEY_EQ_SUB_BASS, 50f).coerceIn(0f, 100f)
fun setEqSubBass(c: Context, v: Float) = prefs(c).edit().putFloat(KEY_EQ_SUB_BASS, v.coerceIn(0f, 100f)).apply()

fun getEqBrilliance(c: Context): Float = prefs(c).getFloat(KEY_EQ_BRILLIANCE, 50f).coerceIn(0f, 100f)
fun setEqBrilliance(c: Context, v: Float) = prefs(c).edit().putFloat(KEY_EQ_BRILLIANCE, v.coerceIn(0f, 100f)).apply()

fun getEqPreampDb(c: Context): Float = prefs(c).getFloat(KEY_EQ_PREAMP_DB, 0f).coerceIn(-12f, 6f)
fun setEqPreampDb(c: Context, db: Float) = prefs(c).edit().putFloat(KEY_EQ_PREAMP_DB, db.coerceIn(-12f, 6f)).apply()

fun getBassBoostStrength(c: Context): Int = prefs(c).getInt(KEY_BASS_BOOST_STRENGTH, 0).coerceIn(0, 1000)
fun setBassBoostStrength(c: Context, v: Int) = prefs(c).edit().putInt(KEY_BASS_BOOST_STRENGTH, v.coerceIn(0, 1000)).apply()

fun isLimiterEnabled(c: Context): Boolean = prefs(c).getBoolean(KEY_LIMITER_ENABLED, true)
fun setLimiterEnabled(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_LIMITER_ENABLED, v).apply()

fun isHeadphoneCrossfeedEnabled(c: Context): Boolean = prefs(c).getBoolean(KEY_HEADPHONE_CROSSFEED, false)
fun setHeadphoneCrossfeedEnabled(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_HEADPHONE_CROSSFEED, v).apply()

fun getSpatialRoomDelayMs(c: Context): Float = prefs(c).getFloat(KEY_SPATIAL_ROOM_DELAY_MS, 12f).coerceIn(2f, 28f)
fun setSpatialRoomDelayMs(c: Context, ms: Float) = prefs(c).edit().putFloat(KEY_SPATIAL_ROOM_DELAY_MS, ms.coerceIn(2f, 28f)).apply()

fun getAudioNormalizerTargetLufs(c: Context): Float = prefs(c).getFloat(KEY_NORMALIZER_TARGET_LUFS, -14f).coerceIn(-24f, -8f)
fun setAudioNormalizerTargetLufs(c: Context, lufs: Float) = prefs(c).edit().putFloat(KEY_NORMALIZER_TARGET_LUFS, lufs.coerceIn(-24f, -8f)).apply()

// ── Playback Engine, Focus, Buffer & Search Preferences ──
private const val KEY_AUTOPLAY_RADIO = "playback_autoplay_radio"
private const val KEY_AUTO_RESUME_BT = "playback_auto_resume_bt"
private const val KEY_PAUSE_ON_UNPLUG = "playback_pause_on_unplug"
private const val KEY_PAUSE_ON_ZERO_VOL = "playback_pause_zero_volume"
private const val KEY_AUTO_SKIP_UNAVAILABLE = "playback_auto_skip_unavailable"
private const val KEY_FADE_IN_ON_PLAY = "playback_fade_in_on_play"
private const val KEY_FLOAT_AUDIO_OUTPUT = "playback_float_audio_output"
private const val KEY_SEEK_STEP_SEC = "playback_seek_step_sec"
private const val KEY_BUFFER_PROFILE = "playback_buffer_profile"
private const val KEY_AUDIO_FOCUS_MODE = "playback_audio_focus_mode"
private const val KEY_SPECTRUM_VISUALIZER = "ui_spectrum_visualizer"
private const val KEY_MINI_PLAYER_PROGRESS = "ui_mini_player_progress"
private const val KEY_SEARCH_EXPLICIT_FILTER = "search_explicit_filter_only"

fun isAutoplayRadioEnabled(c: Context): Boolean = prefs(c).getBoolean(KEY_AUTOPLAY_RADIO, true)
fun setAutoplayRadioEnabled(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_AUTOPLAY_RADIO, v).apply()

fun isAutoResumeOnBluetooth(c: Context): Boolean = prefs(c).getBoolean(KEY_AUTO_RESUME_BT, true)
fun setAutoResumeOnBluetooth(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_AUTO_RESUME_BT, v).apply()

fun isPauseOnUnplugEnabled(c: Context): Boolean = prefs(c).getBoolean(KEY_PAUSE_ON_UNPLUG, true)
fun setPauseOnUnplugEnabled(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_PAUSE_ON_UNPLUG, v).apply()

fun isPauseOnZeroVolume(c: Context): Boolean = prefs(c).getBoolean(KEY_PAUSE_ON_ZERO_VOL, false)
fun setPauseOnZeroVolume(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_PAUSE_ON_ZERO_VOL, v).apply()

fun isAutoSkipUnavailable(c: Context): Boolean = prefs(c).getBoolean(KEY_AUTO_SKIP_UNAVAILABLE, true)
fun setAutoSkipUnavailable(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_AUTO_SKIP_UNAVAILABLE, v).apply()

fun isFadeInOnPlay(c: Context): Boolean = prefs(c).getBoolean(KEY_FADE_IN_ON_PLAY, true)
fun setFadeInOnPlay(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_FADE_IN_ON_PLAY, v).apply()

fun isFloatAudioOutput(c: Context): Boolean = prefs(c).getBoolean(KEY_FLOAT_AUDIO_OUTPUT, true)
fun setFloatAudioOutput(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_FLOAT_AUDIO_OUTPUT, v).apply()

fun getSeekStepSeconds(c: Context): Int = prefs(c).getInt(KEY_SEEK_STEP_SEC, 10).coerceIn(5, 60)
fun setSeekStepSeconds(c: Context, sec: Int) = prefs(c).edit().putInt(KEY_SEEK_STEP_SEC, sec.coerceIn(5, 60)).apply()

fun getBufferProfile(c: Context): BufferProfile {
    val raw = prefs(c).getString(KEY_BUFFER_PROFILE, BufferProfile.BALANCED.name)
    return raw?.let { runCatching { BufferProfile.valueOf(it) }.getOrNull() } ?: BufferProfile.BALANCED
}
fun setBufferProfile(c: Context, profile: BufferProfile) =
    prefs(c).edit().putString(KEY_BUFFER_PROFILE, profile.name).apply()

fun getAudioFocusMode(c: Context): AudioFocusMode {
    val raw = prefs(c).getString(KEY_AUDIO_FOCUS_MODE, AudioFocusMode.PAUSE_AND_DUCK.name)
    return raw?.let { runCatching { AudioFocusMode.valueOf(it) }.getOrNull() } ?: AudioFocusMode.PAUSE_AND_DUCK
}
fun setAudioFocusMode(c: Context, mode: AudioFocusMode) =
    prefs(c).edit().putString(KEY_AUDIO_FOCUS_MODE, mode.name).apply()

fun isSpectrumVisualizerEnabled(c: Context): Boolean = prefs(c).getBoolean(KEY_SPECTRUM_VISUALIZER, true)
fun setSpectrumVisualizerEnabled(c: Context, v: Boolean) {
    prefs(c).edit().putBoolean(KEY_SPECTRUM_VISUALIZER, v).apply()
    notifyUiSettingsChanged()
}

fun isMiniPlayerProgressVisible(c: Context): Boolean = prefs(c).getBoolean(KEY_MINI_PLAYER_PROGRESS, true)
fun setMiniPlayerProgressVisible(c: Context, v: Boolean) {
    prefs(c).edit().putBoolean(KEY_MINI_PLAYER_PROGRESS, v).apply()
    notifyUiSettingsChanged()
}

fun isSearchExplicitFilter(c: Context): Boolean = prefs(c).getBoolean(KEY_SEARCH_EXPLICIT_FILTER, false)
fun setSearchExplicitFilter(c: Context, v: Boolean) =
    prefs(c).edit().putBoolean(KEY_SEARCH_EXPLICIT_FILTER, v).apply()

private const val KEY_ADVANCED_SETTINGS_ENABLED = "ui_advanced_settings_enabled"
private const val KEY_AUDIO_OFFLOAD_ENABLED = "playback_audio_offload_enabled"
private const val KEY_BATTERY_PROMPT_DISMISSED = "oneui_battery_prompt_dismissed"

fun isAdvancedSettingsEnabled(c: Context): Boolean =
    prefs(c).getBoolean(KEY_ADVANCED_SETTINGS_ENABLED, false)

fun setAdvancedSettingsEnabled(c: Context, enabled: Boolean) =
    prefs(c).edit().putBoolean(KEY_ADVANCED_SETTINGS_ENABLED, enabled).apply()

fun isAudioOffloadEnabled(c: Context): Boolean =
    prefs(c).getBoolean(KEY_AUDIO_OFFLOAD_ENABLED, true)

fun setAudioOffloadEnabled(c: Context, enabled: Boolean) =
    prefs(c).edit().putBoolean(KEY_AUDIO_OFFLOAD_ENABLED, enabled).apply()

fun isBatteryPromptDismissed(c: Context): Boolean =
    prefs(c).getBoolean(KEY_BATTERY_PROMPT_DISMISSED, false)

fun setBatteryPromptDismissed(c: Context, dismissed: Boolean) =
    prefs(c).edit().putBoolean(KEY_BATTERY_PROMPT_DISMISSED, dismissed).apply()

private const val KEY_AUTO_EXPORT_MUSIC = "download_auto_export_music"
private const val KEY_APP_VOLUME_PERCENT = "playback_app_volume_percent"
private const val KEY_BACKGROUND_EXIT_NOTIFICATION = "playback_background_exit_notification"
private const val KEY_YT_AUTO_SYNC_PLAYLISTS = "youtube_auto_sync_playlists"

fun isAutoExportToMusicEnabled(c: Context): Boolean =
    prefs(c).getBoolean(KEY_AUTO_EXPORT_MUSIC, false)

fun setAutoExportToMusicEnabled(c: Context, enabled: Boolean) =
    prefs(c).edit().putBoolean(KEY_AUTO_EXPORT_MUSIC, enabled).apply()

/** Independent in-app playback volume (0–100%). Does NOT mutate Android OS media stream volume. */
fun getAppVolumePercent(c: Context): Int =
    prefs(c).getInt(KEY_APP_VOLUME_PERCENT, 100).coerceIn(0, 100)

fun setAppVolumePercent(c: Context, percent: Int) =
    prefs(c).edit().putInt(KEY_APP_VOLUME_PERCENT, percent.coerceIn(0, 100)).apply()

/** Shows a quick Play/Pause mini notification when leaving the app without reopening the UI. */
fun isBackgroundExitNotificationEnabled(c: Context): Boolean =
    prefs(c).getBoolean(KEY_BACKGROUND_EXIT_NOTIFICATION, true)

fun setBackgroundExitNotificationEnabled(c: Context, enabled: Boolean) =
    prefs(c).edit().putBoolean(KEY_BACKGROUND_EXIT_NOTIFICATION, enabled).apply()

fun isAutoSyncYoutubePlaylistsEnabled(c: Context): Boolean =
    prefs(c).getBoolean(KEY_YT_AUTO_SYNC_PLAYLISTS, true)

fun setAutoSyncYoutubePlaylistsEnabled(c: Context, enabled: Boolean) =
    prefs(c).edit().putBoolean(KEY_YT_AUTO_SYNC_PLAYLISTS, enabled).apply()


