package com.music.spotui.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.spotui.audio.AudioOffloadMonitor
import com.music.spotui.audio.EqualizerProfileStore
import com.music.spotui.audio.SpatialSoundstagePresets
import com.music.spotui.data.preferences.OffloadPolicy
import com.music.spotui.data.preferences.ProAudioPreferences
import com.music.spotui.data.preferences.getEqBass
import com.music.spotui.data.preferences.getEqBrilliance
import com.music.spotui.data.preferences.getEqHighMid
import com.music.spotui.data.preferences.getEqLowMid
import com.music.spotui.data.preferences.getEqPreampDb
import com.music.spotui.data.preferences.getEqSubBass
import com.music.spotui.data.preferences.getEqTreble
import com.music.spotui.data.preferences.getEqVocal
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.theme.AppPalette
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProAudioChainSection(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        ProAudioPreferences.hydrateIfNeeded(context)
        EqualizerProfileStore.hydrateIfNeeded(context)
        AudioOffloadMonitor.ensureStarted(context)
    }

    val proState by ProAudioPreferences.state.collectAsStateWithLifecycle()
    val offloadSnapshot by AudioOffloadMonitor.state.collectAsStateWithLifecycle()
    val customSlots by EqualizerProfileStore.customSlots.collectAsStateWithLifecycle()

    var harmonicTelemetry by remember { mutableStateOf(SongPlayer.harmonicTelemetry()) }
    LaunchedEffect(proState.harmonicEnabled) {
        while (true) {
            harmonicTelemetry = SongPlayer.harmonicTelemetry()
            delay(650)
        }
    }

    var newSlotName by remember { mutableStateOf("") }
    var jsonTransferBox by remember { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 1. Live Audio Offload Guard & Policy Card
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF171C26),
            border = BorderStroke(
                1.dp,
                if (offloadSnapshot.hardwareOffloadAllowed) Color(0xFF4CAF50).copy(alpha = 0.55f) else AppPalette.copy(alpha = 0.45f),
            ),
            modifier = Modifier.fillMaxWidth().testTag("pro_offload_guard_card"),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Shield, contentDescription = "Audio offload guard", tint = AppPalette, modifier = Modifier.size(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Audio Offload Safety Guard", color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                        Text(offloadSnapshot.reason, color = Color(0xFFB7C0D1), fontSize = 11.5.sp)
                    }
                    Surface(
                        color = if (offloadSnapshot.hardwareOffloadAllowed) Color(0xFF1E3A24) else Color(0xFF282E3E),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = if (offloadSnapshot.hardwareOffloadAllowed) "OFFLOAD ON" else "SW PCM",
                            color = if (offloadSnapshot.hardwareOffloadAllowed) Color(0xFF81C784) else Color(0xFF90CAF9),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }

                Text(
                    text = "Mic Recordings: ${offloadSnapshot.activeRecordingCount} · VoIP/Call: ${if (offloadSnapshot.inVoiceCommunicationMode) "Yes" else "No"} · BT SCO: ${if (offloadSnapshot.bluetoothScoActive) "On" else "Off"} · Game Capture: ${if (offloadSnapshot.internalGameCaptureActive) "Active" else "Idle"}",
                    color = Color(0xFF9EA8BC),
                    fontSize = 10.5.sp,
                )

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OffloadPolicy.entries.forEach { policy ->
                        FilterChip(
                            selected = proState.offloadPolicy == policy,
                            onClick = {
                                ProAudioPreferences.setOffloadPolicy(context, policy)
                                SongPlayer.refreshAudioOffloadPolicy(context)
                            },
                            label = { Text(policy.label, fontSize = 11.sp) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("offload_policy_${policy.id}"),
                        )
                    }
                }
            }
        }

        // 2. Stationary 8D Stage Calibration + One-Tap Soundstage Presets
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF1A1A20),
            border = BorderStroke(1.dp, Color(0xFF2D3140)),
            modifier = Modifier.fillMaxWidth().testTag("pro_8d_calibration_card"),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Headphones, contentDescription = "Stationary 8D calibration", tint = AppPalette, modifier = Modifier.size(20.dp))
                    Column {
                        Text("Stationary 8D Holographic Calibration", color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                        Text("Zero-orbit binaural stage · locked L/R phantom localization", color = Color(0xFF9EA8BC), fontSize = 11.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SpatialSoundstagePresets.presets.forEach { preset ->
                        val selected = proState.activeSpatialPresetId == preset.id
                        FilterChip(
                            selected = selected,
                            onClick = {
                                SpatialSoundstagePresets.apply(context, preset)
                                SongPlayer.refreshAudioEffects(context)
                            },
                            label = { Text(preset.name, fontSize = 11.sp) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("spatial_preset_${preset.id}"),
                        )
                    }
                }

                ProAudioSlider(
                    label = "Azimuth Outer-Head Spread",
                    valueText = "${(proState.azimuthSpread * 100).roundToInt()}%",
                    value = proState.azimuthSpread,
                    range = 0.4f..1.5f,
                    tag = "slider_8d_azimuth",
                ) {
                    ProAudioPreferences.setAzimuthSpread(context, it)
                    SongPlayer.refreshAudioEffects(context)
                }

                ProAudioSlider(
                    label = "Early Reflection Density",
                    valueText = "${(proState.reflectionDensity * 100).roundToInt()}%",
                    value = proState.reflectionDensity,
                    range = 0.2f..1.5f,
                    tag = "slider_8d_reflection_density",
                ) {
                    ProAudioPreferences.setReflectionDensity(context, it)
                    SongPlayer.refreshAudioEffects(context)
                }

                ProAudioSlider(
                    label = "High-Frequency Air Shimmer",
                    valueText = "${(proState.airShimmer * 100).roundToInt()}%",
                    value = proState.airShimmer,
                    range = 0f..1f,
                    tag = "slider_8d_air_shimmer",
                ) {
                    ProAudioPreferences.setAirShimmer(context, it)
                    SongPlayer.refreshAudioEffects(context)
                }

                ProAudioSlider(
                    label = "Mono-Anchor Sub-Bass Crossover",
                    valueText = "${proState.subBassCrossoverHz.roundToInt()} Hz",
                    value = proState.subBassCrossoverHz,
                    range = 75f..160f,
                    tag = "slider_8d_sub_xover",
                ) {
                    ProAudioPreferences.setSubBassCrossoverHz(context, it)
                    SongPlayer.refreshAudioEffects(context)
                }

                ProAudioSlider(
                    label = "Front / Rear Stage Focus",
                    valueText = "${(proState.frontRearBalance * 100).roundToInt()}% Front",
                    value = proState.frontRearBalance,
                    range = 0f..1f,
                    tag = "slider_8d_front_rear",
                ) {
                    ProAudioPreferences.setFrontRearBalance(context, it)
                    SongPlayer.refreshAudioEffects(context)
                }
            }
        }

        // 3. Harmonic & Dynamics Mastering Processor
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF1A1A20),
            border = BorderStroke(1.dp, Color(0xFF2D3140)),
            modifier = Modifier.fillMaxWidth().testTag("pro_harmonic_card"),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.AutoFixHigh, contentDescription = "Harmonic mastering", tint = AppPalette, modifier = Modifier.size(20.dp))
                        Column {
                            Text("Harmonic & Dynamics Rack", color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (harmonicTelemetry.active) {
                                    "Peak ${String.format(Locale.US, "%.1f", harmonicTelemetry.peakDb)}dB · RMS ${String.format(Locale.US, "%.1f", harmonicTelemetry.rmsDb)}dB · GR ${String.format(Locale.US, "%.1f", harmonicTelemetry.gainReductionDb)}dB"
                                } else {
                                    "Triode warmth, air exciter, sub-harmonic punch, Haas align & bus comp"
                                },
                                color = Color(0xFF9EA8BC),
                                fontSize = 10.5.sp,
                            )
                        }
                    }
                    Switch(
                        checked = proState.harmonicEnabled,
                        onCheckedChange = {
                            ProAudioPreferences.setHarmonicEnabled(context, it)
                            SongPlayer.refreshAudioEffects(context)
                        },
                        modifier = Modifier.testTag("harmonic_master_switch"),
                        colors = SwitchDefaults.colors(checkedThumbColor = AppPalette),
                    )
                }

                ProAudioSlider(
                    label = "Triode Tube Warmth",
                    valueText = "${(proState.tubeWarmth * 100).roundToInt()}%",
                    value = proState.tubeWarmth,
                    range = 0f..1f,
                    tag = "slider_tube_warmth",
                ) {
                    ProAudioPreferences.setTubeWarmth(context, it)
                    SongPlayer.refreshAudioEffects(context)
                }

                ProAudioSlider(
                    label = "High-Shelf Air Exciter (>7.5kHz)",
                    valueText = "${(proState.airExciter * 100).roundToInt()}%",
                    value = proState.airExciter,
                    range = 0f..1f,
                    tag = "slider_air_exciter",
                ) {
                    ProAudioPreferences.setAirExciter(context, it)
                    SongPlayer.refreshAudioEffects(context)
                }

                ProAudioSlider(
                    label = "Sub-Harmonic Punch (<95Hz)",
                    valueText = "${(proState.subHarmonicPunch * 100).roundToInt()}%",
                    value = proState.subHarmonicPunch,
                    range = 0f..1f,
                    tag = "slider_sub_punch",
                ) {
                    ProAudioPreferences.setSubHarmonicPunch(context, it)
                    SongPlayer.refreshAudioEffects(context)
                }

                ProAudioSlider(
                    label = "Haas L/R Micro-Align",
                    valueText = "${String.format(Locale.US, "%+.2f", proState.haasDelayAlignMs)} ms",
                    value = proState.haasDelayAlignMs,
                    range = -2f..2f,
                    tag = "slider_haas_align",
                ) {
                    ProAudioPreferences.setHaasDelayAlignMs(context, it)
                    SongPlayer.refreshAudioEffects(context)
                }

                ProAudioSlider(
                    label = "Soft-Knee Bus Compressor",
                    valueText = "${String.format(Locale.US, "%.1f", proState.compressorRatio)}:1",
                    value = proState.compressorRatio,
                    range = 1f..6f,
                    tag = "slider_compressor_ratio",
                ) {
                    ProAudioPreferences.setCompressorRatio(context, it)
                    SongPlayer.refreshAudioEffects(context)
                }
            }
        }

        // 4. Custom 7-Band EQ Slots & JSON Import/Export
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF1A1A20),
            border = BorderStroke(1.dp, Color(0xFF2D3140)),
            modifier = Modifier.fillMaxWidth().testTag("pro_eq_slots_card"),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.GraphicEq, contentDescription = "Pro EQ slots", tint = AppPalette, modifier = Modifier.size(20.dp))
                    Text("Reference & Custom EQ Slots (JSON Sync)", color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    EqualizerProfileStore.proReferenceSlots.forEach { slot ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                EqualizerProfileStore.applySlot(context, slot)
                                SongPlayer.refreshAudioEffects(context)
                                Toast.makeText(context, "Applied ${slot.name}", Toast.LENGTH_SHORT).show()
                            },
                            label = { Text(slot.name, fontSize = 11.sp) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("eq_ref_${slot.id}"),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = newSlotName,
                        onValueChange = { newSlotName = it },
                        singleLine = true,
                        label = { Text("Save Current EQ As…", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f).testTag("eq_slot_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppPalette,
                            unfocusedBorderColor = Color(0xFF34343F),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                        ),
                    )
                    Button(
                        onClick = {
                            val bands = listOf(
                                getEqSubBass(context),
                                getEqBass(context),
                                getEqLowMid(context),
                                getEqVocal(context),
                                getEqHighMid(context),
                                getEqTreble(context),
                                getEqBrilliance(context),
                            )
                            val saved = EqualizerProfileStore.saveCustomSlot(
                                context = context,
                                name = newSlotName,
                                bands = bands,
                                preampDb = getEqPreampDb(context),
                            )
                            newSlotName = ""
                            jsonTransferBox = EqualizerProfileStore.exportSlotJson(saved)
                            Toast.makeText(context, "Saved slot ${saved.name}", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AppPalette, contentColor = Color.Black),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("eq_save_slot_btn"),
                    ) {
                        Icon(Icons.Rounded.Save, contentDescription = "Save EQ slot", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }

                if (customSlots.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        customSlots.forEach { slot ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF13151C),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .clickable {
                                                EqualizerProfileStore.applySlot(context, slot)
                                                SongPlayer.refreshAudioEffects(context)
                                                Toast.makeText(context, "Loaded ${slot.name}", Toast.LENGTH_SHORT).show()
                                            },
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Text(slot.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text("Preamp ${slot.preampDb} dB", color = Color(0xFF9EA8BC), fontSize = 10.5.sp)
                                    }
                                    IconButton(
                                        onClick = {
                                            val json = EqualizerProfileStore.exportSlotJson(slot)
                                            jsonTransferBox = json
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            cm?.setPrimaryClip(ClipData.newPlainText("SpotUI EQ", json))
                                            Toast.makeText(context, "Copied EQ JSON to clipboard", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(48.dp),
                                    ) {
                                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy EQ JSON", tint = AppPalette)
                                    }
                                    IconButton(
                                        onClick = { EqualizerProfileStore.deleteCustomSlot(context, slot.id) },
                                        modifier = Modifier.size(48.dp),
                                    ) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete EQ slot", tint = Color(0xFFEF9A9A))
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = jsonTransferBox,
                        onValueChange = { jsonTransferBox = it },
                        singleLine = true,
                        label = { Text("Paste EQ JSON to Import", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f).testTag("eq_json_import_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppPalette,
                            unfocusedBorderColor = Color(0xFF34343F),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                        ),
                    )
                    OutlinedButton(
                        onClick = {
                            val imported = EqualizerProfileStore.importSlotJson(context, jsonTransferBox)
                            if (imported != null) {
                                EqualizerProfileStore.applySlot(context, imported)
                                SongPlayer.refreshAudioEffects(context)
                                Toast.makeText(context, "Imported & applied ${imported.name}", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Invalid EQ JSON format", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("eq_json_import_btn"),
                    ) {
                        Text("Import", color = AppPalette, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProAudioSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    tag: String,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            Text(valueText, color = AppPalette, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.testTag(tag),
            colors = SliderDefaults.colors(activeTrackColor = AppPalette, thumbColor = AppPalette),
        )
    }
}
