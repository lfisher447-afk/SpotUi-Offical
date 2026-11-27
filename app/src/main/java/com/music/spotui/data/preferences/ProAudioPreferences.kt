package com.music.spotui.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

enum class OffloadPolicy(
    val id: String,
    val label: String,
    val subtitle: String,
) {
    AUTO_SAFE(
        id = "auto_safe",
        label = "Auto-Safe Guard",
        subtitle = "Disables hardware offload whenever mic, VoIP, BT SCO, game capture, or DSP is active",
    ),
    ALWAYS_SOFTWARE_PCM(
        id = "always_sw_pcm",
        label = "Always Software PCM",
        subtitle = "Forces zero-dropout in-process PCM pipeline (best for gaming & custom DSP)",
    ),
    PREFER_OFFLOAD(
        id = "prefer_offload",
        label = "Prefer Offload (Mic-Safe)",
        subtitle = "Allows hardware offload on clean stereo playback while blocking during active mic/capture",
    );

    companion object {
        fun fromId(id: String?): OffloadPolicy = entries.firstOrNull { it.id == id } ?: AUTO_SAFE
    }
}

enum class CrossfadeCurveShape(
    val id: String,
    val label: String,
    val summary: String,
) {
    EQUAL_POWER(
        id = "equal_power",
        label = "Equal Power",
        summary = "Smooth cos/sin curve keeping combined loudness flat",
    ),
    S_CURVE(
        id = "s_curve",
        label = "DJ S-Curve",
        summary = "Steep mid-blend transition ideal for beatmatching",
    ),
    LINEAR(
        id = "linear",
        label = "Linear Fade",
        summary = "Classic straight cross-slope mix",
    ),
    FAST_CUT(
        id = "fast_cut",
        label = "Fast Cut",
        summary = "Holds incoming high until final 25% quick swap",
    );

    companion object {
        fun fromId(id: String?): CrossfadeCurveShape =
            entries.firstOrNull { it.id == id } ?: EQUAL_POWER
    }
}

enum class SpeedPitchPreset(
    val id: String,
    val label: String,
    val speed: Float,
    val pitch: Float,
) {
    NORMAL("normal", "Original (1.0x)", 1.0f, 1.0f),
    PODCAST_125("podcast_125", "Crisp 1.25x", 1.25f, 1.0f),
    HYPER_150("hyper_150", "Fast 1.5x", 1.50f, 1.0f),
    NIGHTCORE("nightcore", "Nightcore", 1.22f, 1.18f),
    SLOWED_REVERB("slowed", "Slowed Chill", 0.85f, 0.90f),
    CHOPPED_STUDY("study_092", "Focus 0.92x", 0.92f, 1.0f);

    companion object {
        fun fromId(id: String?): SpeedPitchPreset = entries.firstOrNull { it.id == id } ?: NORMAL
    }
}

data class TrackBookmark(
    val id: String,
    val trackKey: String,
    val trackTitle: String,
    val positionMs: Long,
    val note: String,
    val createdAtMs: Long,
)

data class ProAudioConfigState(
    val offloadPolicy: OffloadPolicy = OffloadPolicy.AUTO_SAFE,
    // Extended stationary 8D stage calibration (zero orbit)
    val azimuthSpread: Float = 1.0f,
    val reflectionDensity: Float = 1.0f,
    val airShimmer: Float = 0.45f,
    val subBassCrossoverHz: Float = 110f,
    val frontRearBalance: Float = 0.55f,
    // Harmonic & Dynamics Processor
    val harmonicEnabled: Boolean = false,
    val tubeWarmth: Float = 0.18f,
    val airExciter: Float = 0.22f,
    val subHarmonicPunch: Float = 0.15f,
    val haasDelayAlignMs: Float = 0f,
    val compressorRatio: Float = 1.0f,
    // Playback Automation & Presets
    val activeSpatialPresetId: String = "custom",
    val crossfadeCurveShape: CrossfadeCurveShape = CrossfadeCurveShape.EQUAL_POWER,
    val finishTrackSleepEnabled: Boolean = false,
    val finishTrackFadeOutMs: Int = 2_500,
    val activeSpeedPitchPreset: SpeedPitchPreset = SpeedPitchPreset.NORMAL,
    val bookmarks: List<TrackBookmark> = emptyList(),
)

object ProAudioPreferences {
    private const val PREF_NAME = "spotui_pro_audio_prefs"

    private const val KEY_OFFLOAD_POLICY = "offload_policy"
    private const val KEY_AZIMUTH_SPREAD = "spatial_8d_azimuth_spread"
    private const val KEY_REFLECTION_DENSITY = "spatial_8d_reflection_density"
    private const val KEY_AIR_SHIMMER = "spatial_8d_air_shimmer"
    private const val KEY_SUB_BASS_XOVER_HZ = "spatial_8d_sub_bass_xover_hz"
    private const val KEY_FRONT_REAR_BALANCE = "spatial_8d_front_rear_balance"

    private const val KEY_HARMONIC_ENABLED = "harmonic_enabled"
    private const val KEY_TUBE_WARMTH = "harmonic_tube_warmth"
    private const val KEY_AIR_EXCITER = "harmonic_air_exciter"
    private const val KEY_SUB_HARMONIC_PUNCH = "harmonic_sub_punch"
    private const val KEY_HAAS_DELAY_ALIGN_MS = "harmonic_haas_align_ms"
    private const val KEY_COMPRESSOR_RATIO = "harmonic_compressor_ratio"

    private const val KEY_ACTIVE_SPATIAL_PRESET = "active_spatial_preset_id"
    private const val KEY_CROSSFADE_CURVE = "crossfade_curve_shape"
    private const val KEY_FINISH_TRACK_SLEEP = "finish_track_sleep_enabled"
    private const val KEY_FINISH_TRACK_FADEOUT_MS = "finish_track_fadeout_ms"
    private const val KEY_SPEED_PITCH_PRESET = "speed_pitch_preset"
    private const val KEY_BOOKMARKS_JSON = "track_bookmarks_json"

    private val _state = MutableStateFlow(ProAudioConfigState())
    val state: StateFlow<ProAudioConfigState> = _state.asStateFlow()

    @Volatile
    private var hydrated = false

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun hydrateIfNeeded(context: Context): ProAudioConfigState {
        if (hydrated) return _state.value
        synchronized(this) {
            if (hydrated) return _state.value
            val loaded = load(context)
            _state.value = loaded
            hydrated = true
            return loaded
        }
    }

    private fun load(context: Context): ProAudioConfigState {
        val p = prefs(context)
        return ProAudioConfigState(
            offloadPolicy = OffloadPolicy.fromId(p.getString(KEY_OFFLOAD_POLICY, OffloadPolicy.AUTO_SAFE.id)),
            azimuthSpread = p.getFloat(KEY_AZIMUTH_SPREAD, 1.0f).coerceIn(0.4f, 1.5f),
            reflectionDensity = p.getFloat(KEY_REFLECTION_DENSITY, 1.0f).coerceIn(0.2f, 1.5f),
            airShimmer = p.getFloat(KEY_AIR_SHIMMER, 0.45f).coerceIn(0f, 1f),
            subBassCrossoverHz = p.getFloat(KEY_SUB_BASS_XOVER_HZ, 110f).coerceIn(75f, 160f),
            frontRearBalance = p.getFloat(KEY_FRONT_REAR_BALANCE, 0.55f).coerceIn(0f, 1f),
            harmonicEnabled = p.getBoolean(KEY_HARMONIC_ENABLED, false),
            tubeWarmth = p.getFloat(KEY_TUBE_WARMTH, 0.18f).coerceIn(0f, 1f),
            airExciter = p.getFloat(KEY_AIR_EXCITER, 0.22f).coerceIn(0f, 1f),
            subHarmonicPunch = p.getFloat(KEY_SUB_HARMONIC_PUNCH, 0.15f).coerceIn(0f, 1f),
            haasDelayAlignMs = p.getFloat(KEY_HAAS_DELAY_ALIGN_MS, 0f).coerceIn(-2f, 2f),
            compressorRatio = p.getFloat(KEY_COMPRESSOR_RATIO, 1.0f).coerceIn(1f, 6f),
            activeSpatialPresetId = p.getString(KEY_ACTIVE_SPATIAL_PRESET, "custom").orEmpty().ifBlank { "custom" },
            crossfadeCurveShape = CrossfadeCurveShape.fromId(p.getString(KEY_CROSSFADE_CURVE, CrossfadeCurveShape.EQUAL_POWER.id)),
            finishTrackSleepEnabled = p.getBoolean(KEY_FINISH_TRACK_SLEEP, false),
            finishTrackFadeOutMs = p.getInt(KEY_FINISH_TRACK_FADEOUT_MS, 2_500).coerceIn(0, 8_000),
            activeSpeedPitchPreset = SpeedPitchPreset.fromId(p.getString(KEY_SPEED_PITCH_PRESET, SpeedPitchPreset.NORMAL.id)),
            bookmarks = decodeBookmarks(p.getString(KEY_BOOKMARKS_JSON, null)),
        )
    }

    private fun updateAndPersist(
        context: Context,
        notifyDsp: Boolean = false,
        editPrefs: SharedPreferences.Editor.() -> Unit,
        transform: (ProAudioConfigState) -> ProAudioConfigState,
    ) {
        hydrateIfNeeded(context)
        prefs(context).edit().apply(editPrefs).apply()
        _state.update(transform)
        if (notifyDsp) {
            signalDspSettingsChanged()
        }
    }

    fun setOffloadPolicy(context: Context, policy: OffloadPolicy) =
        updateAndPersist(context, notifyDsp = true, { putString(KEY_OFFLOAD_POLICY, policy.id) }) {
            it.copy(offloadPolicy = policy)
        }

    fun setAzimuthSpread(context: Context, value: Float) {
        val c = value.coerceIn(0.4f, 1.5f)
        updateAndPersist(context, notifyDsp = true, { putFloat(KEY_AZIMUTH_SPREAD, c) }) {
            it.copy(azimuthSpread = c, activeSpatialPresetId = "custom")
        }
    }

    fun setReflectionDensity(context: Context, value: Float) {
        val c = value.coerceIn(0.2f, 1.5f)
        updateAndPersist(context, notifyDsp = true, { putFloat(KEY_REFLECTION_DENSITY, c) }) {
            it.copy(reflectionDensity = c, activeSpatialPresetId = "custom")
        }
    }

    fun setAirShimmer(context: Context, value: Float) {
        val c = value.coerceIn(0f, 1f)
        updateAndPersist(context, notifyDsp = true, { putFloat(KEY_AIR_SHIMMER, c) }) {
            it.copy(airShimmer = c, activeSpatialPresetId = "custom")
        }
    }

    fun setSubBassCrossoverHz(context: Context, hz: Float) {
        val c = hz.coerceIn(75f, 160f)
        updateAndPersist(context, notifyDsp = true, { putFloat(KEY_SUB_BASS_XOVER_HZ, c) }) {
            it.copy(subBassCrossoverHz = c, activeSpatialPresetId = "custom")
        }
    }

    fun setFrontRearBalance(context: Context, value: Float) {
        val c = value.coerceIn(0f, 1f)
        updateAndPersist(context, notifyDsp = true, { putFloat(KEY_FRONT_REAR_BALANCE, c) }) {
            it.copy(frontRearBalance = c, activeSpatialPresetId = "custom")
        }
    }

    fun setHarmonicEnabled(context: Context, enabled: Boolean) =
        updateAndPersist(context, notifyDsp = true, { putBoolean(KEY_HARMONIC_ENABLED, enabled) }) {
            it.copy(harmonicEnabled = enabled)
        }

    fun setTubeWarmth(context: Context, value: Float) {
        val c = value.coerceIn(0f, 1f)
        updateAndPersist(context, notifyDsp = true, { putFloat(KEY_TUBE_WARMTH, c) }) {
            it.copy(tubeWarmth = c)
        }
    }

    fun setAirExciter(context: Context, value: Float) {
        val c = value.coerceIn(0f, 1f)
        updateAndPersist(context, notifyDsp = true, { putFloat(KEY_AIR_EXCITER, c) }) {
            it.copy(airExciter = c)
        }
    }

    fun setSubHarmonicPunch(context: Context, value: Float) {
        val c = value.coerceIn(0f, 1f)
        updateAndPersist(context, notifyDsp = true, { putFloat(KEY_SUB_HARMONIC_PUNCH, c) }) {
            it.copy(subHarmonicPunch = c)
        }
    }

    fun setHaasDelayAlignMs(context: Context, ms: Float) {
        val c = ms.coerceIn(-2f, 2f)
        updateAndPersist(context, notifyDsp = true, { putFloat(KEY_HAAS_DELAY_ALIGN_MS, c) }) {
            it.copy(haasDelayAlignMs = c)
        }
    }

    fun setCompressorRatio(context: Context, ratio: Float) {
        val c = ratio.coerceIn(1f, 6f)
        updateAndPersist(context, notifyDsp = true, { putFloat(KEY_COMPRESSOR_RATIO, c) }) {
            it.copy(compressorRatio = c)
        }
    }

    fun setActiveSpatialPresetId(context: Context, id: String) =
        updateAndPersist(context, notifyDsp = false, { putString(KEY_ACTIVE_SPATIAL_PRESET, id) }) {
            it.copy(activeSpatialPresetId = id)
        }

    fun setCrossfadeCurveShape(context: Context, shape: CrossfadeCurveShape) =
        updateAndPersist(context, notifyDsp = false, { putString(KEY_CROSSFADE_CURVE, shape.id) }) {
            it.copy(crossfadeCurveShape = shape)
        }

    fun setFinishTrackSleepEnabled(context: Context, enabled: Boolean) =
        updateAndPersist(context, notifyDsp = false, { putBoolean(KEY_FINISH_TRACK_SLEEP, enabled) }) {
            it.copy(finishTrackSleepEnabled = enabled)
        }

    fun setFinishTrackFadeOutMs(context: Context, ms: Int) {
        val c = ms.coerceIn(0, 8_000)
        updateAndPersist(context, notifyDsp = false, { putInt(KEY_FINISH_TRACK_FADEOUT_MS, c) }) {
            it.copy(finishTrackFadeOutMs = c)
        }
    }

    fun setActiveSpeedPitchPreset(context: Context, preset: SpeedPitchPreset) =
        updateAndPersist(context, notifyDsp = false, { putString(KEY_SPEED_PITCH_PRESET, preset.id) }) {
            it.copy(activeSpeedPitchPreset = preset)
        }

    fun saveBookmarks(context: Context, bookmarks: List<TrackBookmark>) {
        val trimmed = bookmarks.take(40)
        val json = encodeBookmarks(trimmed)
        updateAndPersist(context, notifyDsp = false, { putString(KEY_BOOKMARKS_JSON, json) }) {
            it.copy(bookmarks = trimmed)
        }
    }

    private fun encodeBookmarks(list: List<TrackBookmark>): String {
        val arr = JSONArray()
        list.forEach { b ->
            arr.put(
                JSONObject().apply {
                    put("id", b.id)
                    put("trackKey", b.trackKey)
                    put("trackTitle", b.trackTitle)
                    put("positionMs", b.positionMs)
                    put("note", b.note)
                    put("createdAtMs", b.createdAtMs)
                },
            )
        }
        return arr.toString()
    }

    private fun decodeBookmarks(raw: String?): List<TrackBookmark> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id")
                    val trackKey = obj.optString("trackKey")
                    if (id.isBlank() || trackKey.isBlank()) continue
                    add(
                        TrackBookmark(
                            id = id,
                            trackKey = trackKey,
                            trackTitle = obj.optString("trackTitle"),
                            positionMs = obj.optLong("positionMs", 0L).coerceAtLeast(0L),
                            note = obj.optString("note", "Bookmark"),
                            createdAtMs = obj.optLong("createdAtMs", 0L),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
