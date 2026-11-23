package com.music.spotui.data.preferences

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Player interface visual mode configurable in Advanced Settings.
 */
enum class PlayerUiStyle(val key: String, val label: String, val description: String) {
    NEW_EXPRESSIVE(
        key = "new_expressive",
        label = "New Expressive UI (v2.5)",
        description = "Layered ambient halo, glassmorphic telemetry pill, direct volume/3D/playlist dock & expressive transport",
    ),
    LEGACY_CLASSIC(
        key = "legacy_classic",
        label = "Legacy Classic UI",
        description = "Original compact SpotUI classic layout with clean standard controls",
    ),
}

enum class UiSoundStyle(val key: String, val label: String) {
    CRISP("crisp", "Crisp Studio"),
    WARM("warm", "Warm Acoustic"),
    MINIMAL("minimal", "Minimal Soft"),
}

enum class SpotuiModuleId(
    val id: String,
    val displayName: String,
    val gradlePath: String,
    val category: String,
    val summary: String,
) {
    SPOTIFY(
        id = "spotify",
        displayName = "Spotify Metadata & Canvas",
        gradlePath = "modules/app/libs/spotify (:spotify)",
        category = "Core Metadata",
        summary = "GraphQL home/search/playlists, Canvas video & SpotiFlac lossless routing",
    ),
    INNERTUBE(
        id = "innertube",
        displayName = "InnerTube Stream Engine",
        gradlePath = "modules/app/libs/innertube (:innertube)",
        category = "Streaming",
        summary = "YouTube & YT Music multi-client resolver, PoToken & synced library pagination",
    ),
    LRCLIB(
        id = "lrclib",
        displayName = "LRCLIB Millisecond Lyrics",
        gradlePath = "modules/app/libs/lrclib (:lrclib)",
        category = "Lyrics",
        summary = "Keyless millisecond-synced LRC database with duration scoring",
    ),
    BETTERLYRICS(
        id = "betterlyrics",
        displayName = "BetterLyrics Word-Sync TTML",
        gradlePath = "modules/app/libs/betterlyrics (:betterlyrics)",
        category = "Lyrics",
        summary = "TTML karaoke & word-by-word timed lyric parser",
    ),
    KUGOU(
        id = "kugou",
        displayName = "KuGou Synced KRC Lyrics",
        gradlePath = "modules/app/libs/kugou (:kugou)",
        category = "Lyrics",
        summary = "High-coverage CJK & international time-synced lyric fetcher",
    ),
    SIMPMUSIC(
        id = "simpmusic",
        displayName = "SimpMusic Lyrics API",
        gradlePath = "modules/app/libs/simpmusic (:simpmusic)",
        category = "Lyrics",
        summary = "Video-ID keyed community synced lyrics mirror",
    ),
    PAXSENIX(
        id = "paxsenix",
        displayName = "Paxsenix Multi-Source Lyrics",
        gradlePath = "modules/app/libs/paxsenix (:paxsenix)",
        category = "Lyrics",
        summary = "Apple Music, Musixmatch, Netease & Spotify cross-provider lyrics relay",
    ),
    LASTFM(
        id = "lastfm",
        displayName = "Last.fm Scrobbler & Love",
        gradlePath = "modules/app/libs/lastfm (:lastfm)",
        category = "Scrobbling",
        summary = "Real-time Now Playing updates, auto-scrobbling & track love sync",
    ),
    SHAZAMKIT(
        id = "shazamkit",
        displayName = "ShazamKit Audio Signature",
        gradlePath = "modules/app/libs/shazamkit (:shazamkit)",
        category = "Discovery",
        summary = "Acoustic fingerprint recognition request manager & signature cache",
    ),
    FASTLANE(
        id = "fastlane",
        displayName = "Fastlane Store Metadata & Release",
        gradlePath = "modules/app/libs/fastlane (CI metadata)",
        category = "Release & Store",
        summary = "Automated Appfile, Fastfile & localized Google Play store release descriptors",
    ),
    MRBEAN_ENGINE(
        id = "mrbean_engine",
        displayName = "MrBean HTTP/2 Turbo Transport",
        gradlePath = "org.eclipse.Mrbean.client",
        category = "Network & DL",
        summary = "32-socket connection pool, HTTP/2 multiplexing, TTL DNS cache & 8x 206 workers",
    ),
    MEDIA3_DSP(
        id = "media3_dsp",
        displayName = "3D Binaural & 7-Band Float DSP",
        gradlePath = "com.music.spotui.audio",
        category = "Audio Engine",
        summary = "64-bit Biquad EQ, Haas 3D Spatializer, Pinna HRTF, 8D Orbit & Soft Limiter",
    ),
    FLOATER_A11Y(
        id = "floater_a11y",
        displayName = "Accessibility Floater Studio",
        gradlePath = "com.music.spotui.accessibility",
        category = "Overlay UI",
        summary = "Corner-resizable floating player, in-app volume mixer & 3D/EQ quick drawer",
    ),
    NOVAA_ARCHIVE(
        id = "novaa_archive",
        displayName = "NovaAc Single-Pass Vault",
        gradlePath = "com.music.spotui.backup",
        category = "Storage & Crypto",
        summary = "Single-pass AEAD encrypted library & lossless track backup archive engine",
    ),
    GLANCE_WIDGETS(
        id = "glance_widgets",
        displayName = "Glance Now-Playing Widgets",
        gradlePath = "com.music.spotui.widget",
        category = "Widgets",
        summary = "Jetpack Glance home screen player widgets synced via PlaybackService",
    ),
    ANDROID_AUTO(
        id = "android_auto",
        displayName = "MediaLibrary & Android Auto",
        gradlePath = "com.music.spotui.ui.notification",
        category = "Car & Cast",
        summary = "Browsable Android Auto catalog, One UI Now Bar & Media3 notification session",
    );

    companion object {
        val LYRICS_PROVIDERS: List<SpotuiModuleId> = listOf(
            LRCLIB,
            BETTERLYRICS,
            SIMPMUSIC,
            PAXSENIX,
            KUGOU,
        )

        fun fromId(id: String): SpotuiModuleId? =
            entries.firstOrNull { it.id.equals(id.trim(), ignoreCase = true) }
    }
}

object ExperiencePreferences {
    private const val PREF_NAME = "spotui_experience_prefs"

    private const val KEY_PLAYER_UI_STYLE = "player_ui_style"
    private const val KEY_UI_SOUNDS_ENABLED = "ui_sounds_enabled"
    private const val KEY_TAB_SOUND_ENABLED = "tab_sound_enabled"
    private const val KEY_TOGGLE_SOUND_ENABLED = "toggle_sound_enabled"
    private const val KEY_SLIDER_SOUND_ENABLED = "slider_sound_enabled"
    private const val KEY_UI_SOUND_VOLUME = "ui_sound_volume"
    private const val KEY_UI_SOUND_STYLE = "ui_sound_style"
    private const val KEY_LAST_PATCH_NOTES_VERSION = "last_seen_patch_notes_version"
    private const val KEY_CLOCK_SHOW_SECONDS = "home_clock_show_seconds"

    private val _experienceRevision = MutableStateFlow(0)
    val experienceRevision: StateFlow<Int> = _experienceRevision.asStateFlow()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private fun bumpRevision() {
        _experienceRevision.value = _experienceRevision.value + 1
    }

    fun getPlayerUiStyle(context: Context): PlayerUiStyle {
        val raw = prefs(context).getString(KEY_PLAYER_UI_STYLE, PlayerUiStyle.NEW_EXPRESSIVE.key)
        return PlayerUiStyle.entries.firstOrNull { it.key == raw } ?: PlayerUiStyle.NEW_EXPRESSIVE
    }

    fun setPlayerUiStyle(context: Context, style: PlayerUiStyle) {
        prefs(context).edit().putString(KEY_PLAYER_UI_STYLE, style.key).apply()
        bumpRevision()
    }

    fun isUiSoundsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_UI_SOUNDS_ENABLED, true)

    fun setUiSoundsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_UI_SOUNDS_ENABLED, enabled).apply()
        bumpRevision()
    }

    fun isTabSoundEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TAB_SOUND_ENABLED, true)

    fun setTabSoundEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_TAB_SOUND_ENABLED, enabled).apply()
        bumpRevision()
    }

    fun isToggleSoundEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TOGGLE_SOUND_ENABLED, true)

    fun setToggleSoundEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_TOGGLE_SOUND_ENABLED, enabled).apply()
        bumpRevision()
    }

    fun isSliderSoundEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SLIDER_SOUND_ENABLED, true)

    fun setSliderSoundEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SLIDER_SOUND_ENABLED, enabled).apply()
        bumpRevision()
    }

    fun getUiSoundVolume(context: Context): Int =
        prefs(context).getInt(KEY_UI_SOUND_VOLUME, 70).coerceIn(0, 100)

    fun setUiSoundVolume(context: Context, percent: Int) {
        prefs(context).edit().putInt(KEY_UI_SOUND_VOLUME, percent.coerceIn(0, 100)).apply()
        bumpRevision()
    }

    fun getUiSoundStyle(context: Context): UiSoundStyle {
        val raw = prefs(context).getString(KEY_UI_SOUND_STYLE, UiSoundStyle.CRISP.key)
        return UiSoundStyle.entries.firstOrNull { it.key == raw } ?: UiSoundStyle.CRISP
    }

    fun setUiSoundStyle(context: Context, style: UiSoundStyle) {
        prefs(context).edit().putString(KEY_UI_SOUND_STYLE, style.key).apply()
        bumpRevision()
    }

    fun isHomeClockSecondsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CLOCK_SHOW_SECONDS, true)

    fun setHomeClockSecondsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_CLOCK_SHOW_SECONDS, enabled).apply()
        bumpRevision()
    }

    fun getLastSeenPatchNotesVersion(context: Context): String =
        prefs(context).getString(KEY_LAST_PATCH_NOTES_VERSION, "").orEmpty()

    fun setLastSeenPatchNotesVersion(context: Context, version: String) {
        prefs(context).edit().putString(KEY_LAST_PATCH_NOTES_VERSION, version).apply()
        bumpRevision()
    }

    fun shouldShowPatchNotes(context: Context, currentVersion: String): Boolean {
        val cleanCurrent = currentVersion.removeSuffix("-debug").trim()
        val seen = getLastSeenPatchNotesVersion(context).removeSuffix("-debug").trim()
        return cleanCurrent.isNotBlank() && seen != cleanCurrent
    }
}

object ModuleLibsConfig {
    private const val PREF_NAME = "spotui_module_libs_config"
    private const val KEY_LYRICS_ORDER = "lyrics_module_order"
    private const val KEY_KUGOU_TRADITIONAL = "kugou_traditional_chinese"
    private const val KEY_PAXSENIX_MULTI_ENDPOINT = "paxsenix_multi_endpoint"
    private const val KEY_LASTFM_SCROBBLE = "lastfm_scrobble_enabled"
    private const val KEY_LASTFM_NOW_PLAYING = "lastfm_now_playing"
    private const val KEY_LASTFM_API_KEY = "lastfm_api_key"
    private const val KEY_LASTFM_SECRET = "lastfm_secret"
    private const val KEY_LASTFM_SESSION = "lastfm_session_key"
    private const val KEY_LASTFM_USERNAME = "lastfm_username"
    private const val KEY_LASTFM_THRESHOLD = "lastfm_scrobble_threshold"
    private const val KEY_SHAZAM_CACHE_ENABLED = "shazam_cache_enabled"
    private const val KEY_FASTLANE_CHANNEL = "fastlane_release_channel"

    val defaultLyricsOrder = listOf("lrclib", "betterlyrics", "simpmusic", "paxsenix", "kugou")

    private val _moduleRevision = MutableStateFlow(0)
    val moduleRevision: StateFlow<Int> = _moduleRevision.asStateFlow()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private fun bump() {
        _moduleRevision.value = _moduleRevision.value + 1
    }

    fun isModuleEnabled(context: Context, module: SpotuiModuleId): Boolean =
        prefs(context).getBoolean("module_enabled_${module.id}", true)

    fun setModuleEnabled(context: Context, module: SpotuiModuleId, enabled: Boolean) {
        // Core streaming engine stays protected unless intentionally switched by user in diagnostics
        prefs(context).edit().putBoolean("module_enabled_${module.id}", enabled).apply()
        bump()
    }

    fun getLyricsModuleOrder(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_LYRICS_ORDER, null).orEmpty()
        val parsed = raw.split(",").map { it.trim().lowercase() }.filter { it in defaultLyricsOrder }
        val missing = defaultLyricsOrder.filter { it !in parsed }
        return if (parsed.isEmpty()) defaultLyricsOrder else parsed + missing
    }

    fun getLyricsModuleIdsOrder(context: Context): List<SpotuiModuleId> =
        getLyricsModuleOrder(context).mapNotNull { SpotuiModuleId.fromId(it) }

    fun moveLyricsModule(context: Context, id: String, up: Boolean): List<String> {
        val list = getLyricsModuleOrder(context).toMutableList()
        val idx = list.indexOf(id)
        if (idx < 0) return list
        val target = if (up) idx - 1 else idx + 1
        if (target !in list.indices) return list
        list.add(target, list.removeAt(idx))
        prefs(context).edit().putString(KEY_LYRICS_ORDER, list.joinToString(",")).apply()
        bump()
        return list
    }

    fun resetLyricsOrder(context: Context) {
        prefs(context).edit().putString(KEY_LYRICS_ORDER, defaultLyricsOrder.joinToString(",")).apply()
        bump()
    }

    fun isKugouTraditionalChinese(context: Context): Boolean =
        prefs(context).getBoolean(KEY_KUGOU_TRADITIONAL, false)

    fun setKugouTraditionalChinese(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_KUGOU_TRADITIONAL, enabled).apply()
        bump()
    }

    fun isPaxsenixMultiEndpoint(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PAXSENIX_MULTI_ENDPOINT, true)

    fun setPaxsenixMultiEndpoint(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_PAXSENIX_MULTI_ENDPOINT, enabled).apply()
        bump()
    }

    fun isLastFmScrobbleEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LASTFM_SCROBBLE, false)

    fun setLastFmScrobbleEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_LASTFM_SCROBBLE, enabled).apply()
        bump()
    }

    fun isLastFmNowPlayingEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LASTFM_NOW_PLAYING, true)

    fun setLastFmNowPlayingEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_LASTFM_NOW_PLAYING, enabled).apply()
        bump()
    }

    fun getLastFmApiKey(context: Context): String =
        prefs(context).getString(KEY_LASTFM_API_KEY, "").orEmpty()

    fun getLastFmSecret(context: Context): String =
        prefs(context).getString(KEY_LASTFM_SECRET, "").orEmpty()

    fun getLastFmSessionKey(context: Context): String =
        prefs(context).getString(KEY_LASTFM_SESSION, "").orEmpty()

    fun getLastFmUsername(context: Context): String =
        prefs(context).getString(KEY_LASTFM_USERNAME, "").orEmpty()

    fun setLastFmCredentials(
        context: Context,
        apiKey: String,
        secret: String,
        sessionKey: String = getLastFmSessionKey(context),
        username: String = getLastFmUsername(context),
    ) {
        prefs(context).edit()
            .putString(KEY_LASTFM_API_KEY, apiKey.trim())
            .putString(KEY_LASTFM_SECRET, secret.trim())
            .putString(KEY_LASTFM_SESSION, sessionKey.trim())
            .putString(KEY_LASTFM_USERNAME, username.trim())
            .apply()
        bump()
    }

    fun getLastFmScrobbleThresholdPercent(context: Context): Int =
        prefs(context).getInt(KEY_LASTFM_THRESHOLD, 50).coerceIn(25, 90)

    fun setLastFmScrobbleThresholdPercent(context: Context, percent: Int) {
        prefs(context).edit().putInt(KEY_LASTFM_THRESHOLD, percent.coerceIn(25, 90)).apply()
        bump()
    }

    fun isShazamCacheEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHAZAM_CACHE_ENABLED, true)

    fun setShazamCacheEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHAZAM_CACHE_ENABLED, enabled).apply()
        bump()
    }

    fun getFastlaneChannel(context: Context): String =
        prefs(context).getString(KEY_FASTLANE_CHANNEL, "production").orEmpty()

    fun setFastlaneChannel(context: Context, channel: String) {
        prefs(context).edit().putString(KEY_FASTLANE_CHANNEL, channel).apply()
        bump()
    }
}
