package com.music.spotui.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.spotui.audio.PlaybackDiagnosticsEngine
import com.music.spotui.data.preferences.CrossfadeCurveShape
import com.music.spotui.data.preferences.ProAudioPreferences
import com.music.spotui.data.preferences.SpeedPitchPreset
import com.music.spotui.di.PlaybackAutomationManager
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.theme.AppPalette
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdvancedPlaybackSection(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        ProAudioPreferences.hydrateIfNeeded(context)
        PlaybackAutomationManager.ensureStarted(context)
    }

    val proState by ProAudioPreferences.state.collectAsStateWithLifecycle()
    val duckingGain by PlaybackAutomationManager.duckingGain.collectAsStateWithLifecycle()

    var diagnostics by remember { mutableStateOf(PlaybackDiagnosticsEngine.captureSnapshot(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            diagnostics = PlaybackDiagnosticsEngine.captureSnapshot(context)
            delay(1_200)
        }
    }

    var bookmarkNote by remember { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 1. Live Playback Signal Path Diagnostics Card
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF171C26),
            border = BorderStroke(1.dp, Color(0xFF2D364A)),
            modifier = Modifier.fillMaxWidth().testTag("advanced_diagnostics_card"),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Live Playback Signal Telemetry", color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                    IconButton(
                        onClick = { diagnostics = PlaybackDiagnosticsEngine.captureSnapshot(context) },
                        modifier = Modifier.size(48.dp).testTag("diagnostics_refresh_btn"),
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh signal telemetry", tint = AppPalette)
                    }
                }
                Text(
                    text = "Source: ${diagnostics.sourceLabel} (${diagnostics.qualityLabel}) · Route: ${diagnostics.outputRouteLabel} (${diagnostics.outputDeviceName})",
                    color = Color(0xFFD0D6E0),
                    fontSize = 11.5.sp,
                )
                Text(
                    text = "Session #${diagnostics.sessionId} · Buffer ${diagnostics.bufferProfileLabel} (${diagnostics.bufferedPct}%) · Corr ${String.format(Locale.US, "%.2f", diagnostics.spatialCorrelation)} · Duck ${String.format(Locale.US, "%.0f%%", duckingGain * 100f)}",
                    color = Color(0xFF9EA8BC),
                    fontSize = 11.sp,
                )
                Text(
                    text = "Offload: ${diagnostics.offloadReason} · Spatial: ${diagnostics.spatialSummary}",
                    color = Color(0xFF8AB4F8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!diagnostics.lastError.isNullOrBlank()) {
                    Text("Last Error: ${diagnostics.lastError}", color = Color(0xFFEF9A9A), fontSize = 11.sp)
                }
            }
        }

        // 2. Crossfade Curve Shape & Finish-Track Sleep Timer
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF1A1A20),
            border = BorderStroke(1.dp, Color(0xFF2D3140)),
            modifier = Modifier.fillMaxWidth().testTag("advanced_automation_card"),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Tune, contentDescription = "Crossfade curves", tint = AppPalette, modifier = Modifier.size(20.dp))
                    Text("DJ Crossfade Curve Shape", color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CrossfadeCurveShape.entries.forEach { shape ->
                        FilterChip(
                            selected = proState.crossfadeCurveShape == shape,
                            onClick = { ProAudioPreferences.setCrossfadeCurveShape(context, shape) },
                            label = { Text(shape.label, fontSize = 11.sp) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("crossfade_shape_${shape.id}"),
                        )
                    }
                }
                Text(proState.crossfadeCurveShape.summary, color = Color(0xFF9EA8BC), fontSize = 11.sp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.NightsStay, contentDescription = "Finish current track sleep timer", tint = AppPalette, modifier = Modifier.size(20.dp))
                        Column {
                            Text("Pause After Current Song (Smart Sleep)", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                            Text("Lets the playing song finish completely, then pauses before next track", color = Color(0xFF9EA8BC), fontSize = 11.sp)
                        }
                    }
                    Switch(
                        checked = proState.finishTrackSleepEnabled,
                        onCheckedChange = { PlaybackAutomationManager.setFinishTrackSleep(context, it) },
                        modifier = Modifier.testTag("finish_track_sleep_switch"),
                        colors = SwitchDefaults.colors(checkedThumbColor = AppPalette),
                    )
                }
            }
        }

        // 3. Speed / Pitch Mood Presets + Track Timestamp Bookmarks
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF1A1A20),
            border = BorderStroke(1.dp, Color(0xFF2D3140)),
            modifier = Modifier.fillMaxWidth().testTag("advanced_presets_bookmarks_card"),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Speed, contentDescription = "Speed and pitch presets", tint = AppPalette, modifier = Modifier.size(20.dp))
                    Text("Instant Speed & Pitch Profiles", color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SpeedPitchPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = proState.activeSpeedPitchPreset == preset,
                            onClick = {
                                PlaybackAutomationManager.applySpeedPitchPreset(context, preset)
                                Toast.makeText(context, "${preset.label} applied", Toast.LENGTH_SHORT).show()
                            },
                            label = { Text(preset.label, fontSize = 11.sp) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("speed_preset_${preset.id}"),
                        )
                    }
                }

                Text("Track Timestamp Bookmarks", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = bookmarkNote,
                        onValueChange = { bookmarkNote = it },
                        singleLine = true,
                        label = { Text("Bookmark Note (Optional)", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f).testTag("bookmark_note_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppPalette,
                            unfocusedBorderColor = Color(0xFF34343F),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                        ),
                    )
                    Button(
                        onClick = {
                            val snap = SongPlayer.getNowPlayingSnapshot(context)
                            val title = snap.title.ifBlank { "Current Track" }
                            val key = if (snap.songId != 0) "song:${snap.songId}" else title
                            PlaybackAutomationManager.addBookmark(
                                context = context,
                                trackKey = key,
                                trackTitle = title,
                                positionMs = SongPlayer.getCurrentPosition().coerceAtLeast(snap.positionMs),
                                note = bookmarkNote,
                            )
                            bookmarkNote = ""
                            Toast.makeText(context, "Saved timestamp bookmark", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AppPalette, contentColor = Color.Black),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("bookmark_add_btn"),
                    ) {
                        Icon(Icons.Rounded.BookmarkAdd, contentDescription = "Add timestamp bookmark", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Mark", fontWeight = FontWeight.Bold)
                    }
                }

                if (proState.bookmarks.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        proState.bookmarks.take(8).forEach { b ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF13151C),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(b.note, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                        Text("${b.trackTitle} · ${b.positionMs / 1000L}s", color = Color(0xFF9EA8BC), fontSize = 10.5.sp)
                                    }
                                    Button(
                                        onClick = { SongPlayer.seekTo(b.positionMs) },
                                        colors = ButtonDefaults.buttonColors(containerColor = AppPalette.copy(alpha = 0.2f), contentColor = AppPalette),
                                        modifier = Modifier.heightIn(min = 48.dp),
                                    ) {
                                        Text("Jump", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                    IconButton(
                                        onClick = { PlaybackAutomationManager.deleteBookmark(context, b.id) },
                                        modifier = Modifier.size(48.dp),
                                    ) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete bookmark", tint = Color(0xFFEF9A9A))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
