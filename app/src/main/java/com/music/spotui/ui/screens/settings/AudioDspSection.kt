package com.music.spotui.ui.screens.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.spotui.audio.AudioEffectController
import com.music.spotui.data.preferences.SpatialProfile
import com.music.spotui.data.preferences.getAudioNormalizerGainMb
import com.music.spotui.data.preferences.getAudioNormalizerTargetLufs
import com.music.spotui.data.preferences.getBassBoostStrength
import com.music.spotui.data.preferences.getEqBass
import com.music.spotui.data.preferences.getEqBrilliance
import com.music.spotui.data.preferences.getEqHighMid
import com.music.spotui.data.preferences.getEqLowMid
import com.music.spotui.data.preferences.getEqPreampDb
import com.music.spotui.data.preferences.getEqPreset
import com.music.spotui.data.preferences.getEqSubBass
import com.music.spotui.data.preferences.getEqTreble
import com.music.spotui.data.preferences.getEqVocal
import com.music.spotui.data.preferences.getSpatialElevationDepth
import com.music.spotui.data.preferences.getSpatialOrbitSpeed
import com.music.spotui.data.preferences.getSpatialProfile
import com.music.spotui.data.preferences.getSpatialRoomDelayMs
import com.music.spotui.data.preferences.getSpatialStrength
import com.music.spotui.data.preferences.isAudioNormalizerEnabled
import com.music.spotui.data.preferences.isEqEnabled
import com.music.spotui.data.preferences.isEqSpatialEnabled
import com.music.spotui.data.preferences.isHeadphoneCrossfeedEnabled
import com.music.spotui.data.preferences.isLimiterEnabled
import com.music.spotui.data.preferences.isSpatial8dOrbitEnabled
import com.music.spotui.data.preferences.isSpatialCorrelationGuardEnabled
import com.music.spotui.data.preferences.setAudioNormalizerEnabled
import com.music.spotui.data.preferences.setAudioNormalizerGainMb
import com.music.spotui.data.preferences.setAudioNormalizerTargetLufs
import com.music.spotui.data.preferences.setBassBoostStrength
import com.music.spotui.data.preferences.setEqBass
import com.music.spotui.data.preferences.setEqBrilliance
import com.music.spotui.data.preferences.setEqEnabled
import com.music.spotui.data.preferences.setEqHighMid
import com.music.spotui.data.preferences.setEqLowMid
import com.music.spotui.data.preferences.setEqPreampDb
import com.music.spotui.data.preferences.setEqPreset
import com.music.spotui.data.preferences.setEqSpatialEnabled
import com.music.spotui.data.preferences.setEqSubBass
import com.music.spotui.data.preferences.setEqTreble
import com.music.spotui.data.preferences.setEqVocal
import com.music.spotui.data.preferences.setHeadphoneCrossfeedEnabled
import com.music.spotui.data.preferences.setLimiterEnabled
import com.music.spotui.data.preferences.setSpatial8dOrbitEnabled
import com.music.spotui.data.preferences.setSpatialCorrelationGuardEnabled
import com.music.spotui.data.preferences.setSpatialElevationDepth
import com.music.spotui.data.preferences.setSpatialOrbitSpeed
import com.music.spotui.data.preferences.setSpatialProfile
import com.music.spotui.data.preferences.setSpatialRoomDelayMs
import com.music.spotui.data.preferences.setSpatialStrength
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.theme.AppPalette
import kotlin.math.roundToInt

private data class EqPresetCurve(
    val name: String,
    val bands: FloatArray, // SubBass, Bass, LowMid, Vocal, Presence, Treble, Brilliance (0..100)
)

private val studioEqPresets = listOf(
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

@Composable
fun AudioDspSection(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    var eqEnabled by remember { mutableStateOf(isEqEnabled(context)) }
    var eqPreset by remember { mutableStateOf(getEqPreset(context)) }
    var eqSubBass by remember { mutableStateOf(getEqSubBass(context)) }
    var eqBass by remember { mutableStateOf(getEqBass(context)) }
    var eqLowMid by remember { mutableStateOf(getEqLowMid(context)) }
    var eqVocal by remember { mutableStateOf(getEqVocal(context)) }
    var eqHighMid by remember { mutableStateOf(getEqHighMid(context)) }
    var eqTreble by remember { mutableStateOf(getEqTreble(context)) }
    var eqBrilliance by remember { mutableStateOf(getEqBrilliance(context)) }

    var preampDb by remember { mutableStateOf(getEqPreampDb(context)) }
    var bassBoostStrength by remember { mutableStateOf(getBassBoostStrength(context).toFloat()) }
    var limiterEnabled by remember { mutableStateOf(isLimiterEnabled(context)) }

    var audioNormalizer by remember { mutableStateOf(isAudioNormalizerEnabled(context)) }
    var normalizerGainMb by remember { mutableStateOf(getAudioNormalizerGainMb(context).toFloat()) }
    var normalizerTargetLufs by remember { mutableStateOf(getAudioNormalizerTargetLufs(context)) }

    var eqSpatial by remember { mutableStateOf(isEqSpatialEnabled(context)) }
    var spatialProfile by remember { mutableStateOf(getSpatialProfile(context)) }
    var spatialStrength by remember { mutableStateOf(getSpatialStrength(context).toFloat()) }
    var roomDelayMs by remember { mutableStateOf(getSpatialRoomDelayMs(context)) }
    var spatialElevationDepth by remember { mutableStateOf(getSpatialElevationDepth(context)) }
    var spatial8dOrbit by remember { mutableStateOf(isSpatial8dOrbitEnabled(context)) }
    var spatialOrbitSpeed by remember { mutableStateOf(getSpatialOrbitSpeed(context)) }
    var spatialCorrelationGuard by remember { mutableStateOf(isSpatialCorrelationGuardEnabled(context)) }
    var headphoneCrossfeed by remember { mutableStateOf(isHeadphoneCrossfeedEnabled(context)) }

    val applyLiveDsp = remember {
        {
            SongPlayer.refreshAudioEffects(context)
        }
    }

    val routeInfo by AudioEffectController.routeState.collectAsState()
    val appVolumePercent by SongPlayer.appVolumePercentFlow.collectAsState()
    var lastNonZeroVolume by remember { mutableIntStateOf(if (appVolumePercent > 0) appVolumePercent else 80) }
    var dspTelemetry by remember { mutableStateOf(SongPlayer.dspTelemetrySummary()) }
    LaunchedEffect(eqEnabled, eqSpatial, spatial8dOrbit, headphoneCrossfeed, routeInfo) {
        while (true) {
            dspTelemetry = SongPlayer.dspTelemetrySummary()
            delay(1200L)
        }
    }
    val capabilities = remember(
        eqEnabled, eqSubBass, eqBass, eqLowMid, eqVocal, eqHighMid, eqTreble, eqBrilliance,
        preampDb, bassBoostStrength, limiterEnabled, audioNormalizer, eqSpatial, spatialProfile,
        spatialStrength, roomDelayMs, spatialElevationDepth, spatial8dOrbit, spatialOrbitSpeed,
        spatialCorrelationGuard, headphoneCrossfeed, routeInfo,
    ) {
        AudioEffectController.capabilityReport()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1A1A20))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // In-App Volume Changer (0–100%, Non-OS)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF12161D))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    IconButton(
                        onClick = {
                            if (appVolumePercent > 0) {
                                lastNonZeroVolume = appVolumePercent
                                SongPlayer.setAppVolumePercent(context, 0)
                            } else {
                                SongPlayer.setAppVolumePercent(context, lastNonZeroVolume.coerceIn(10, 100))
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("in_app_volume_mute_button"),
                    ) {
                        Icon(
                            imageVector = if (appVolumePercent == 0) {
                                Icons.AutoMirrored.Filled.VolumeOff
                            } else {
                                Icons.AutoMirrored.Filled.VolumeUp
                            },
                            contentDescription = if (appVolumePercent == 0) "Unmute in-app audio" else "Mute in-app audio",
                            tint = if (appVolumePercent == 0) Color(0xFFFF8B94) else AppPalette,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Column {
                        Text(
                            "In-App Volume",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Internal app gain only — does not alter Android OS volume",
                            color = Color(0xFFB3B8C4),
                            fontSize = 11.sp,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppPalette.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        "$appVolumePercent%",
                        color = AppPalette,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TextButton(
                    onClick = {
                        val next = (appVolumePercent - 5).coerceIn(0, 100)
                        if (next > 0) lastNonZeroVolume = next
                        SongPlayer.setAppVolumePercent(context, next)
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("-5%", color = Color(0xFFD6DAE6), fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = appVolumePercent.toFloat(),
                    onValueChange = {
                        val pct = it.roundToInt().coerceIn(0, 100)
                        if (pct > 0) lastNonZeroVolume = pct
                        SongPlayer.setAppVolumePercent(context, pct)
                    },
                    valueRange = 0f..100f,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("in_app_volume_slider"),
                    colors = SliderDefaults.colors(
                        activeTrackColor = AppPalette,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                        thumbColor = AppPalette,
                    ),
                )
                TextButton(
                    onClick = {
                        val next = (appVolumePercent + 5).coerceIn(0, 100)
                        if (next > 0) lastNonZeroVolume = next
                        SongPlayer.setAppVolumePercent(context, next)
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("+5%", color = Color(0xFFD6DAE6), fontWeight = FontWeight.Bold)
                }
            }
        }

        // Master DSP Status Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF12161D))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Studio 7-Band Parametric EQ",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (capabilities.softwareDspAttached) {
                        "In-process 64-bit Biquad DSP active (Session #${capabilities.sessionId})"
                    } else {
                        "Configured & ready — attaches automatically on play"
                    },
                    color = if (capabilities.softwareDspAttached) Color(0xFF81C784) else Color(0xFFB3B8C4),
                    fontSize = 11.sp,
                )
            }
            Switch(
                checked = eqEnabled,
                onCheckedChange = {
                    eqEnabled = it
                    setEqEnabled(context, it)
                    applyLiveDsp()
                },
                modifier = Modifier.testTag("master_eq_enable_switch"),
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AppPalette),
            )
        }

        // Live Parametric Frequency Curve Visualizer Canvas
        EqResponseCurveCanvas(
            bands = listOf(eqSubBass, eqBass, eqLowMid, eqVocal, eqHighMid, eqTreble, eqBrilliance),
            enabled = eqEnabled,
            accentColor = AppPalette,
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF111319)),
        )

        // 12 Presets Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            studioEqPresets.forEach { preset ->
                val isSelected = eqPreset == preset.name
                Box(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) AppPalette else Color.White.copy(alpha = 0.08f))
                        .clickable {
                            eqPreset = preset.name
                            setEqPreset(context, preset.name)
                            eqSubBass = preset.bands[0]
                            eqBass = preset.bands[1]
                            eqLowMid = preset.bands[2]
                            eqVocal = preset.bands[3]
                            eqHighMid = preset.bands[4]
                            eqTreble = preset.bands[5]
                            eqBrilliance = preset.bands[6]
                            setEqSubBass(context, eqSubBass)
                            setEqBass(context, eqBass)
                            setEqLowMid(context, eqLowMid)
                            setEqVocal(context, eqVocal)
                            setEqHighMid(context, eqHighMid)
                            setEqTreble(context, eqTreble)
                            setEqBrilliance(context, eqBrilliance)
                            if (!eqEnabled) {
                                eqEnabled = true
                                setEqEnabled(context, true)
                            }
                            applyLiveDsp()
                        }
                        .testTag("eq_preset_${preset.name.lowercase().replace(" ", "_")}")
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        preset.name,
                        color = if (isSelected) Color.Black else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // 7 Parametric EQ Band Sliders
        EqBandSlider("Sub-Bass · 32 Hz", eqSubBass, "eq_band_sub_bass_slider") { v, persist ->
            eqSubBass = v
            eqPreset = "Custom"
            setEqSubBass(context, v)
            if (persist) setEqPreset(context, "Custom")
            applyLiveDsp()
        }
        EqBandSlider("Bass · 64 Hz", eqBass, "eq_band_bass_slider") { v, persist ->
            eqBass = v
            eqPreset = "Custom"
            setEqBass(context, v)
            if (persist) setEqPreset(context, "Custom")
            applyLiveDsp()
        }
        EqBandSlider("Low-Mid · 250 Hz", eqLowMid, "eq_band_low_mid_slider") { v, persist ->
            eqLowMid = v
            eqPreset = "Custom"
            setEqLowMid(context, v)
            if (persist) setEqPreset(context, "Custom")
            applyLiveDsp()
        }
        EqBandSlider("Vocal · 1 kHz", eqVocal, "eq_band_vocal_slider") { v, persist ->
            eqVocal = v
            eqPreset = "Custom"
            setEqVocal(context, v)
            if (persist) setEqPreset(context, "Custom")
            applyLiveDsp()
        }
        EqBandSlider("Presence · 4 kHz", eqHighMid, "eq_band_presence_slider") { v, persist ->
            eqHighMid = v
            eqPreset = "Custom"
            setEqHighMid(context, v)
            if (persist) setEqPreset(context, "Custom")
            applyLiveDsp()
        }
        EqBandSlider("Treble · 10 kHz", eqTreble, "eq_band_treble_slider") { v, persist ->
            eqTreble = v
            eqPreset = "Custom"
            setEqTreble(context, v)
            if (persist) setEqPreset(context, "Custom")
            applyLiveDsp()
        }
        EqBandSlider("Brilliance · 16 kHz", eqBrilliance, "eq_band_brilliance_slider") { v, persist ->
            eqBrilliance = v
            eqPreset = "Custom"
            setEqBrilliance(context, v)
            if (persist) setEqPreset(context, "Custom")
            applyLiveDsp()
        }

        // Preamp, Hardware+Software Bass Boost & Soft Peak Limiter
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF22222B))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Preamp Headroom", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("${String.format("%+.1f", preampDb)} dB", color = AppPalette, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = preampDb,
                onValueChange = {
                    preampDb = it
                    setEqPreampDb(context, it)
                    applyLiveDsp()
                },
                valueRange = -12f..6f,
                modifier = Modifier.testTag("eq_preamp_slider"),
                colors = SliderDefaults.colors(activeTrackColor = AppPalette, thumbColor = AppPalette),
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Sub-Harmonic Bass Boost", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("${(bassBoostStrength / 10f).roundToInt()}%", color = AppPalette, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = bassBoostStrength,
                onValueChange = {
                    bassBoostStrength = it
                    setBassBoostStrength(context, it.roundToInt())
                    applyLiveDsp()
                },
                valueRange = 0f..1000f,
                modifier = Modifier.testTag("bass_boost_strength_slider"),
                colors = SliderDefaults.colors(activeTrackColor = AppPalette, thumbColor = AppPalette),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Soft-Knee Peak Limiter", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Prevents clipping when stacking heavy bass/EQ boosts", color = Color.Gray, fontSize = 11.sp)
                }
                Switch(
                    checked = limiterEnabled,
                    onCheckedChange = {
                        limiterEnabled = it
                        setLimiterEnabled(context, it)
                        applyLiveDsp()
                    },
                    modifier = Modifier.testTag("soft_limiter_switch"),
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AppPalette),
                )
            }
        }

        // Loudness Normalization
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF22222B))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("RMS Volume Normalization", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Smooth real-time gain leveling across quiet & loud albums", color = Color.Gray, fontSize = 11.sp)
                }
                Switch(
                    checked = audioNormalizer,
                    onCheckedChange = {
                        audioNormalizer = it
                        setAudioNormalizerEnabled(context, it)
                        applyLiveDsp()
                    },
                    modifier = Modifier.testTag("normalizer_enable_switch"),
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AppPalette),
                )
            }
            if (audioNormalizer) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Makeup Intensity", color = Color.White, fontSize = 12.sp)
                    Text("${normalizerGainMb.roundToInt()} mB", color = AppPalette, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = normalizerGainMb,
                    onValueChange = {
                        normalizerGainMb = it
                        setAudioNormalizerGainMb(context, it.roundToInt())
                        applyLiveDsp()
                    },
                    valueRange = 0f..600f,
                    modifier = Modifier.testTag("normalizer_intensity_slider"),
                    colors = SliderDefaults.colors(activeTrackColor = AppPalette, thumbColor = AppPalette),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Target Loudness Reference", color = Color.White, fontSize = 12.sp)
                    Text("${normalizerTargetLufs.roundToInt()} LUFS", color = AppPalette, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = normalizerTargetLufs,
                    onValueChange = {
                        normalizerTargetLufs = it
                        setAudioNormalizerTargetLufs(context, it)
                        applyLiveDsp()
                    },
                    valueRange = -24f..-8f,
                    modifier = Modifier.testTag("normalizer_lufs_slider"),
                    colors = SliderDefaults.colors(activeTrackColor = AppPalette, thumbColor = AppPalette),
                )
            }
        }

        // 3D Spatial Audio & Binaural Headphone Stage
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF22222B))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("3D Spatial Soundstage Engine", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (routeInfo.isBluetooth) {
                            "BT-Safe SW 3D active on ${routeInfo.deviceName} (Woodworth HRTF + All-Pass)"
                        } else {
                            "Cascaded Schroeder All-Pass, Mono-Bass Anchor (<120Hz) & Woodworth HRTF"
                        },
                        color = if (routeInfo.isBluetooth) Color(0xFF81C784) else Color.Gray,
                        fontSize = 11.sp,
                    )
                }
                Switch(
                    checked = eqSpatial,
                    onCheckedChange = {
                        eqSpatial = it
                        setEqSpatialEnabled(context, it)
                        applyLiveDsp()
                    },
                    modifier = Modifier.testTag("spatial_audio_enable_switch"),
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AppPalette),
                )
            }

            // Live Audio Route & Bluetooth Safe + OS Spatializer Telemetry Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Output: ${routeInfo.route.label} (${routeInfo.deviceName})",
                    color = Color(0xFFD6DAE6),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = buildString {
                        append(if (routeInfo.isBluetooth) "BT Safe" else if (routeInfo.isPersonalListening) "HRTF" else "PCM")
                        append(" · ")
                        append(capabilities.systemSpatializerLabel)
                    },
                    color = AppPalette,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = dspTelemetry,
                color = Color(0xFF9FB0C7),
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 2.dp),
            )

            if (eqSpatial) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SpatialProfile.entries.forEach { profile ->
                        val selected = spatialProfile == profile
                        Box(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (selected) AppPalette else Color.White.copy(alpha = 0.08f))
                                .clickable {
                                    spatialProfile = profile
                                    setSpatialProfile(context, profile)
                                    applyLiveDsp()
                                }
                                .testTag("spatial_profile_${profile.name.lowercase()}")
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                profile.label,
                                color = if (selected) Color.Black else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                Text(spatialProfile.detail, color = Color(0xFFB3B3B3), fontSize = 11.sp)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Spatial Width & Depth", color = Color.White, fontSize = 13.sp)
                    Text("${(spatialStrength / 10f).roundToInt()}%", color = AppPalette, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = spatialStrength,
                    onValueChange = {
                        spatialStrength = it
                        setSpatialStrength(context, it.roundToInt())
                        applyLiveDsp()
                    },
                    valueRange = 0f..1000f,
                    modifier = Modifier.testTag("spatial_depth_slider"),
                    colors = SliderDefaults.colors(activeTrackColor = AppPalette, thumbColor = AppPalette),
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Early Room Reflection Delay", color = Color.White, fontSize = 13.sp)
                    Text("${String.format("%.1f", roomDelayMs)} ms", color = AppPalette, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = roomDelayMs,
                    onValueChange = {
                        roomDelayMs = it
                        setSpatialRoomDelayMs(context, it)
                        applyLiveDsp()
                    },
                    valueRange = 2f..28f,
                    modifier = Modifier.testTag("spatial_room_delay_slider"),
                    colors = SliderDefaults.colors(activeTrackColor = AppPalette, thumbColor = AppPalette),
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Pinna Elevation & All-Pass Depth", color = Color.White, fontSize = 13.sp)
                    Text("${(spatialElevationDepth * 100f).roundToInt()}%", color = AppPalette, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = spatialElevationDepth,
                    onValueChange = {
                        spatialElevationDepth = it
                        setSpatialElevationDepth(context, it)
                        applyLiveDsp()
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.testTag("spatial_elevation_depth_slider"),
                    colors = SliderDefaults.colors(activeTrackColor = AppPalette, thumbColor = AppPalette),
                )

                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("8D Binaural Orbit (360° Head Stage)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Smooth spherical-head azimuth panning around the listener", color = Color.Gray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = spatial8dOrbit,
                        onCheckedChange = {
                            spatial8dOrbit = it
                            setSpatial8dOrbitEnabled(context, it)
                            applyLiveDsp()
                        },
                        modifier = Modifier.testTag("spatial_8d_orbit_switch"),
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AppPalette),
                    )
                }

                if (spatial8dOrbit) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("8D Orbit Rotation Speed", color = Color.White, fontSize = 13.sp)
                        Text("${String.format("%.2f", spatialOrbitSpeed)} Hz", color = AppPalette, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = spatialOrbitSpeed,
                        onValueChange = {
                            spatialOrbitSpeed = it
                            setSpatialOrbitSpeed(context, it)
                            applyLiveDsp()
                        },
                        valueRange = 0.10f..1.50f,
                        modifier = Modifier.testTag("spatial_8d_speed_slider"),
                        colors = SliderDefaults.colors(activeTrackColor = AppPalette, thumbColor = AppPalette),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Phase-Safe Correlation Guard", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Prevents out-of-phase hollowness on mono/speaker playback", color = Color.Gray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = spatialCorrelationGuard,
                        onCheckedChange = {
                            spatialCorrelationGuard = it
                            setSpatialCorrelationGuardEnabled(context, it)
                            applyLiveDsp()
                        },
                        modifier = Modifier.testTag("spatial_correlation_guard_switch"),
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AppPalette),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Headphone HRTF Crossfeed", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Natural speaker-like interaural bleed to reduce headphone fatigue", color = Color.Gray, fontSize = 11.sp)
                }
                Switch(
                    checked = headphoneCrossfeed,
                    onCheckedChange = {
                        headphoneCrossfeed = it
                        setHeadphoneCrossfeedEnabled(context, it)
                        applyLiveDsp()
                    },
                    modifier = Modifier.testTag("headphone_crossfeed_switch"),
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AppPalette),
                )
            }
        }

        // Hardware + Software Capabilities Diagnostics Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when {
                    capabilities.sessionId <= 0 -> "${routeInfo.route.label} · SW Biquad EQ & 3D PCM Engine ready"
                    routeInfo.isBluetooth || capabilities.bluetoothSafeBypassActive ->
                        "${routeInfo.deviceName} · SW 7-Band DSP + 3D PCM Active (HW Virtualizer safely bypassed)"
                    capabilities.hardwareSpatialAvailable -> "HW AudioFX (${capabilities.equalizerBands}b EQ) + SW DSP Active"
                    else -> "SW 7-Band Biquad DSP & 3D Soundstage Active"
                },
                color = AppPalette,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    eqEnabled = true
                    eqPreset = "Flat"
                    eqSubBass = 50f; eqBass = 50f; eqLowMid = 50f; eqVocal = 50f
                    eqHighMid = 50f; eqTreble = 50f; eqBrilliance = 50f
                    preampDb = 0f; bassBoostStrength = 0f
                    setEqEnabled(context, true)
                    setEqPreset(context, "Flat")
                    setEqSubBass(context, 50f); setEqBass(context, 50f); setEqLowMid(context, 50f)
                    setEqVocal(context, 50f); setEqHighMid(context, 50f); setEqTreble(context, 50f)
                    setEqBrilliance(context, 50f); setEqPreampDb(context, 0f); setBassBoostStrength(context, 0)
                    applyLiveDsp()
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("reset_eq_flat_button"),
            ) {
                Text("Reset Flat", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EqBandSlider(
    label: String,
    value: Float,
    tag: String,
    onChange: (Float, persistPreset: Boolean) -> Unit,
) {
    val db = ((value.coerceIn(0f, 100f) - 50f) / 50f) * 12f
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                "${String.format("%+.1f", db)} dB",
                color = AppPalette,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Slider(
            value = value,
            onValueChange = { onChange(it, false) },
            onValueChangeFinished = { onChange(value, true) },
            valueRange = 0f..100f,
            modifier = Modifier.testTag(tag),
            colors = SliderDefaults.colors(
                activeTrackColor = AppPalette,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                thumbColor = AppPalette,
            ),
        )
    }
}

@Composable
private fun EqResponseCurveCanvas(
    bands: List<Float>,
    enabled: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val midY = h * 0.5f

        // Subtle reference grid lines (+6 dB, 0 dB, -6 dB)
        drawLine(Color.White.copy(alpha = 0.07f), Offset(0f, h * 0.25f), Offset(w, h * 0.25f), 1f)
        drawLine(Color.White.copy(alpha = 0.16f), Offset(0f, midY), Offset(w, midY), 1.5f)
        drawLine(Color.White.copy(alpha = 0.07f), Offset(0f, h * 0.75f), Offset(w, h * 0.75f), 1f)

        if (bands.isEmpty()) return@Canvas
        val padX = w * 0.06f
        val usableW = (w - padX * 2f).coerceAtLeast(1f)
        val stepX = if (bands.size > 1) usableW / (bands.size - 1) else usableW

        val pts = bands.mapIndexed { idx, v ->
            val norm = if (enabled) ((v.coerceIn(0f, 100f) - 50f) / 50f) else 0f
            val x = padX + idx * stepX
            val y = (midY - norm * (h * 0.38f)).coerceIn(8f, h - 8f)
            Offset(x, y)
        }

        val curvePath = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            for (i in 0 until pts.lastIndex) {
                val p0 = pts[i]
                val p1 = pts[i + 1]
                val cx = (p0.x + p1.x) * 0.5f
                cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
            }
        }

        val fillPath = Path().apply {
            addPath(curvePath)
            lineTo(pts.last().x, h)
            lineTo(pts.first().x, h)
            close()
        }

        val lineColor = if (enabled) accentColor else Color(0xFF666A78)
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                listOf(lineColor.copy(alpha = 0.30f), lineColor.copy(alpha = 0.02f)),
            ),
        )
        drawPath(
            path = curvePath,
            color = lineColor,
            style = Stroke(width = 3.5f, cap = StrokeCap.Round),
        )
        pts.forEach { pt ->
            drawCircle(color = lineColor, radius = 5.5f, center = pt)
        }
    }
}
