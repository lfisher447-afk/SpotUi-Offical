package com.music.spotui.audio

import android.content.Context
import com.music.spotui.data.preferences.ProAudioPreferences
import com.music.spotui.data.preferences.SpatialProfile
import com.music.spotui.data.preferences.setEqSpatialEnabled
import com.music.spotui.data.preferences.setHeadphoneCrossfeedEnabled
import com.music.spotui.data.preferences.setSpatial8dEnabled
import com.music.spotui.data.preferences.setSpatial8dIntensity
import com.music.spotui.data.preferences.setSpatialElevationDepth
import com.music.spotui.data.preferences.setSpatialProfile
import com.music.spotui.data.preferences.setSpatialRoomDelayMs
import com.music.spotui.data.preferences.setSpatialStrength

data class SpatialSoundstagePreset(
    val id: String,
    val name: String,
    val tagline: String,
    val profile: SpatialProfile,
    val widthStrength: Int, // 0..1000
    val roomDelayMs: Float,
    val advanced8dEnabled: Boolean,
    val advanced8dDepth: Float,
    val elevationDepth: Float,
    val azimuthSpread: Float,
    val reflectionDensity: Float,
    val airShimmer: Float,
    val subBassCrossoverHz: Float,
    val frontRearBalance: Float,
    val crossfeed: Boolean,
    val harmonicEnabled: Boolean,
    val tubeWarmth: Float,
    val airExciter: Float,
    val subHarmonicPunch: Float,
)

object SpatialSoundstagePresets {
    val presets: List<SpatialSoundstagePreset> = listOf(
        SpatialSoundstagePreset(
            id = "8d_concert_ref",
            name = "8D Concert Reference",
            tagline = "Zero-orbit stationary 8D stage · deep hall reflections & airy shimmer",
            profile = SpatialProfile.CONCERT_HALL,
            widthStrength = 820,
            roomDelayMs = 15.5f,
            advanced8dEnabled = true,
            advanced8dDepth = 1.05f,
            elevationDepth = 0.78f,
            azimuthSpread = 1.22f,
            reflectionDensity = 1.15f,
            airShimmer = 0.62f,
            subBassCrossoverHz = 105f,
            frontRearBalance = 0.60f,
            crossfeed = false,
            harmonicEnabled = true,
            tubeWarmth = 0.16f,
            airExciter = 0.28f,
            subHarmonicPunch = 0.12f,
        ),
        SpatialSoundstagePreset(
            id = "fps_footsteps",
            name = "FPS Footsteps Clarity",
            tagline = "Pinpoint Woodworth localization with crisp directional imaging",
            profile = SpatialProfile.BINAURAL_HEADPHONES,
            widthStrength = 640,
            roomDelayMs = 5.5f,
            advanced8dEnabled = true,
            advanced8dDepth = 0.68f,
            elevationDepth = 0.85f,
            azimuthSpread = 1.10f,
            reflectionDensity = 0.35f,
            airShimmer = 0.30f,
            subBassCrossoverHz = 135f,
            frontRearBalance = 0.80f,
            crossfeed = false,
            harmonicEnabled = true,
            tubeWarmth = 0.0f,
            airExciter = 0.45f,
            subHarmonicPunch = 0.0f,
        ),
        SpatialSoundstagePreset(
            id = "late_night_iem",
            name = "Late Night IEM Warm",
            tagline = "Fatigue-free headphone crossfeed with lush triode warmth",
            profile = SpatialProfile.STUDIO,
            widthStrength = 480,
            roomDelayMs = 8.0f,
            advanced8dEnabled = false,
            advanced8dDepth = 0.60f,
            elevationDepth = 0.48f,
            azimuthSpread = 0.85f,
            reflectionDensity = 0.55f,
            airShimmer = 0.25f,
            subBassCrossoverHz = 95f,
            frontRearBalance = 0.72f,
            crossfeed = true,
            harmonicEnabled = true,
            tubeWarmth = 0.42f,
            airExciter = 0.12f,
            subHarmonicPunch = 0.26f,
        ),
        SpatialSoundstagePreset(
            id = "harman_hologram",
            name = "Harman Wide Hologram",
            tagline = "Expansive outer-head panorama with anchored sub-bass punch",
            profile = SpatialProfile.IMMERSIVE,
            widthStrength = 760,
            roomDelayMs = 11.0f,
            advanced8dEnabled = true,
            advanced8dDepth = 0.92f,
            elevationDepth = 0.72f,
            azimuthSpread = 1.32f,
            reflectionDensity = 0.88f,
            airShimmer = 0.52f,
            subBassCrossoverHz = 110f,
            frontRearBalance = 0.58f,
            crossfeed = false,
            harmonicEnabled = true,
            tubeWarmth = 0.20f,
            airExciter = 0.30f,
            subHarmonicPunch = 0.20f,
        ),
        SpatialSoundstagePreset(
            id = "vocal_studio",
            name = "Vocal Intimate Studio",
            tagline = "Front-center vocals locked in phase with velvet acoustic framing",
            profile = SpatialProfile.STUDIO,
            widthStrength = 560,
            roomDelayMs = 6.5f,
            advanced8dEnabled = true,
            advanced8dDepth = 0.55f,
            elevationDepth = 0.62f,
            azimuthSpread = 0.82f,
            reflectionDensity = 0.48f,
            airShimmer = 0.38f,
            subBassCrossoverHz = 120f,
            frontRearBalance = 0.88f,
            crossfeed = false,
            harmonicEnabled = true,
            tubeWarmth = 0.26f,
            airExciter = 0.22f,
            subHarmonicPunch = 0.08f,
        ),
    )

    fun apply(context: Context, preset: SpatialSoundstagePreset) {
        val appCtx = context.applicationContext
        setEqSpatialEnabled(appCtx, true)
        setSpatialProfile(appCtx, preset.profile)
        setSpatialStrength(appCtx, preset.widthStrength)
        setSpatialRoomDelayMs(appCtx, preset.roomDelayMs)
        setSpatial8dEnabled(appCtx, preset.advanced8dEnabled)
        setSpatial8dIntensity(appCtx, preset.advanced8dDepth)
        setSpatialElevationDepth(appCtx, preset.elevationDepth)
        setHeadphoneCrossfeedEnabled(appCtx, preset.crossfeed)

        ProAudioPreferences.setAzimuthSpread(appCtx, preset.azimuthSpread)
        ProAudioPreferences.setReflectionDensity(appCtx, preset.reflectionDensity)
        ProAudioPreferences.setAirShimmer(appCtx, preset.airShimmer)
        ProAudioPreferences.setSubBassCrossoverHz(appCtx, preset.subBassCrossoverHz)
        ProAudioPreferences.setFrontRearBalance(appCtx, preset.frontRearBalance)
        ProAudioPreferences.setHarmonicEnabled(appCtx, preset.harmonicEnabled)
        ProAudioPreferences.setTubeWarmth(appCtx, preset.tubeWarmth)
        ProAudioPreferences.setAirExciter(appCtx, preset.airExciter)
        ProAudioPreferences.setSubHarmonicPunch(appCtx, preset.subHarmonicPunch)
        ProAudioPreferences.setActiveSpatialPresetId(appCtx, preset.id)
    }
}
