package com.music.spotui.audio

import android.content.Context
import com.music.spotui.data.preferences.setBassBoostStrength
import com.music.spotui.data.preferences.setEqBass
import com.music.spotui.data.preferences.setEqBrilliance
import com.music.spotui.data.preferences.setEqEnabled
import com.music.spotui.data.preferences.setEqHighMid
import com.music.spotui.data.preferences.setEqLowMid
import com.music.spotui.data.preferences.setEqPreampDb
import com.music.spotui.data.preferences.setEqPreset
import com.music.spotui.data.preferences.setEqSubBass
import com.music.spotui.data.preferences.setEqTreble
import com.music.spotui.data.preferences.setEqVocal

/**
 * Shared 7-band parametric EQ preset catalog used by both Settings UI and the
 * Accessibility Floating Studio Console so presets stay synchronized across surfaces.
 */
data class EqPresetCurve(
    val name: String,
    /** SubBass(32Hz), Bass(64Hz), LowMid(250Hz), Vocal(1kHz), Presence(4kHz), Treble(10kHz), Brilliance(16kHz) in 0..100 */
    val bands: FloatArray,
)

object EqPresetCatalog {
    val presets: List<EqPresetCurve> = listOf(
        EqPresetCurve("Flat", floatArrayOf(50f, 50f, 50f, 50f, 50f, 50f, 50f)),
        EqPresetCurve("Bass Boost", floatArrayOf(88f, 84f, 58f, 48f, 50f, 46f, 44f)),
        EqPresetCurve("Vocal Boost", floatArrayOf(42f, 44f, 52f, 84f, 74f, 56f, 50f)),
        EqPresetCurve("Electronic", floatArrayOf(82f, 76f, 44f, 48f, 62f, 78f, 82f)),
        EqPresetCurve("Classical", floatArrayOf(48f, 46f, 54f, 68f, 64f, 66f, 70f)),
        EqPresetCurve("Pop", floatArrayOf(58f, 62f, 52f, 66f, 68f, 64f, 58f)),
        EqPresetCurve("Rock", floatArrayOf(74f, 76f, 46f, 54f, 68f, 74f, 68f)),
        EqPresetCurve("Jazz", floatArrayOf(56f, 60f, 58f, 64f, 58f, 64f, 60f)),
        EqPresetCurve("R&B / Hip-Hop", floatArrayOf(86f, 78f, 52f, 54f, 58f, 54f, 52f)),
        EqPresetCurve("Acoustic", floatArrayOf(52f, 56f, 58f, 70f, 66f, 60f, 56f)),
        EqPresetCurve("Late Night", floatArrayOf(66f, 62f, 50f, 62f, 52f, 44f, 40f)),
        EqPresetCurve("Treble Sparkle", floatArrayOf(48f, 48f, 50f, 56f, 68f, 82f, 88f)),
    )

    fun findByName(name: String): EqPresetCurve? =
        presets.firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun applyPreset(context: Context, preset: EqPresetCurve) {
        val appCtx = context.applicationContext
        setEqPreset(appCtx, preset.name)
        setEqSubBass(appCtx, preset.bands[0])
        setEqBass(appCtx, preset.bands[1])
        setEqLowMid(appCtx, preset.bands[2])
        setEqVocal(appCtx, preset.bands[3])
        setEqHighMid(appCtx, preset.bands[4])
        setEqTreble(appCtx, preset.bands[5])
        setEqBrilliance(appCtx, preset.bands[6])
        if (!com.music.spotui.data.preferences.isEqEnabled(appCtx)) {
            setEqEnabled(appCtx, true)
        }
    }

    fun resetFlat(context: Context) {
        val appCtx = context.applicationContext
        setEqEnabled(appCtx, true)
        setEqPreset(appCtx, "Flat")
        setEqSubBass(appCtx, 50f)
        setEqBass(appCtx, 50f)
        setEqLowMid(appCtx, 50f)
        setEqVocal(appCtx, 50f)
        setEqHighMid(appCtx, 50f)
        setEqTreble(appCtx, 50f)
        setEqBrilliance(appCtx, 50f)
        setEqPreampDb(appCtx, 0f)
        setBassBoostStrength(appCtx, 0)
    }
}
