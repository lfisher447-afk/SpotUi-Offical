package com.music.spotui.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.music.spotui.data.preferences.AudioFocusMode
import com.music.spotui.data.preferences.BufferProfile
import com.music.spotui.data.preferences.getAudioFocusMode
import com.music.spotui.data.preferences.getBufferProfile
import com.music.spotui.data.preferences.getSeekStepSeconds
import com.music.spotui.data.preferences.isAudioOffloadEnabled
import com.music.spotui.data.preferences.isAutoResumeOnBluetooth
import com.music.spotui.data.preferences.isAutoSkipUnavailable
import com.music.spotui.data.preferences.isAutoplayRadioEnabled
import com.music.spotui.data.preferences.isFadeInOnPlay
import com.music.spotui.data.preferences.isFloatAudioOutput
import com.music.spotui.data.preferences.isMiniPlayerProgressVisible
import com.music.spotui.data.preferences.isPauseOnUnplugEnabled
import com.music.spotui.data.preferences.isPauseOnZeroVolume
import com.music.spotui.data.preferences.isSpectrumVisualizerEnabled
import com.music.spotui.data.preferences.setAudioFocusMode
import com.music.spotui.data.preferences.setAudioOffloadEnabled
import com.music.spotui.data.preferences.setAutoResumeOnBluetooth
import com.music.spotui.data.preferences.setAutoSkipUnavailable
import com.music.spotui.data.preferences.setAutoplayRadioEnabled
import com.music.spotui.data.preferences.setBufferProfile
import com.music.spotui.data.preferences.setFadeInOnPlay
import com.music.spotui.data.preferences.setFloatAudioOutput
import com.music.spotui.data.preferences.setMiniPlayerProgressVisible
import com.music.spotui.data.preferences.setPauseOnUnplugEnabled
import com.music.spotui.data.preferences.setPauseOnZeroVolume
import com.music.spotui.data.preferences.setSeekStepSeconds
import com.music.spotui.data.preferences.setSpectrumVisualizerEnabled
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.theme.AppPalette

@Composable
fun PlaybackAndToolsSection(
    onOpenDeveloperConsole: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    var bufferProfile by remember { mutableStateOf(getBufferProfile(context)) }
    var audioFocusMode by remember { mutableStateOf(getAudioFocusMode(context)) }
    var autoplayRadio by remember { mutableStateOf(isAutoplayRadioEnabled(context)) }
    var autoResumeBt by remember { mutableStateOf(isAutoResumeOnBluetooth(context)) }
    var pauseUnplug by remember { mutableStateOf(isPauseOnUnplugEnabled(context)) }
    var pauseZeroVol by remember { mutableStateOf(isPauseOnZeroVolume(context)) }
    var autoSkipError by remember { mutableStateOf(isAutoSkipUnavailable(context)) }
    var fadeInPlay by remember { mutableStateOf(isFadeInOnPlay(context)) }
    var floatAudio by remember { mutableStateOf(isFloatAudioOutput(context)) }
    var audioOffload by remember { mutableStateOf(isAudioOffloadEnabled(context)) }
    var seekStepSec by remember { mutableStateOf(getSeekStepSeconds(context)) }
    var spectrumVisualizer by remember { mutableStateOf(isSpectrumVisualizerEnabled(context)) }
    var miniPlayerProgress by remember { mutableStateOf(isMiniPlayerProgressVisible(context)) }

    var abStart by remember { mutableStateOf(SongPlayer.abLoopStartMs) }
    var abEnd by remember { mutableStateOf(SongPlayer.abLoopEndMs) }
    var sleepEndAt by remember { mutableStateOf(SongPlayer.sleepTimerEndAt) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1A1A20))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Playback Engine & Buffer Tuning", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)

        Text("Media3 Buffer Profile", color = Color(0xFFB3B3B3), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BufferProfile.entries.forEach { prof ->
                val selected = bufferProfile == prof
                Box(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) AppPalette else Color.White.copy(alpha = 0.08f))
                        .clickable {
                            bufferProfile = prof
                            setBufferProfile(context, prof)
                        }
                        .testTag("buffer_profile_${prof.name.lowercase()}")
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        prof.label,
                        color = if (selected) Color.Black else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Text(bufferProfile.detail, color = Color(0xFF9FA9BC), fontSize = 11.sp)

        Text("Audio Focus Policy", color = Color(0xFFB3B3B3), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AudioFocusMode.entries.forEach { mode ->
                val selected = audioFocusMode == mode
                Box(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) AppPalette else Color.White.copy(alpha = 0.08f))
                        .clickable {
                            audioFocusMode = mode
                            setAudioFocusMode(context, mode)
                            SongPlayer.refreshAudioFocusPolicy(context)
                        }
                        .testTag("audio_focus_${mode.name.lowercase()}")
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        mode.label,
                        color = if (selected) Color.Black else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Text(audioFocusMode.detail, color = Color(0xFF9FA9BC), fontSize = 11.sp)

        Text("Quick Seek Jump Step", color = Color(0xFFB3B3B3), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(5, 10, 15, 30).forEach { sec ->
                val selected = seekStepSec == sec
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) AppPalette else Color(0xFF232530))
                        .clickable {
                            seekStepSec = sec
                            setSeekStepSeconds(context, sec)
                        }
                        .testTag("seek_step_${sec}s_chip"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${sec}s", color = if (selected) Color.Black else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Toggle Switches
        EngineToggleRow(
            title = "Infinite Autoplay Radio",
            subtitle = "Automatically extends queue with Spotify recommendations when queue finishes in background",
            checked = autoplayRadio,
            tag = "autoplay_radio_switch",
        ) {
            autoplayRadio = it
            setAutoplayRadioEnabled(context, it)
        }
        EngineToggleRow(
            title = "Resume on Bluetooth A2DP Connect",
            subtitle = "Auto-resumes playback when headphones or car audio connect",
            checked = autoResumeBt,
            tag = "bt_auto_resume_switch",
        ) {
            autoResumeBt = it
            setAutoResumeOnBluetooth(context, it)
        }
        EngineToggleRow(
            title = "Pause on Headphone Unplug",
            subtitle = "Pauses audio immediately when wired/BT output disconnects",
            checked = pauseUnplug,
            tag = "pause_on_unplug_switch",
        ) {
            pauseUnplug = it
            setPauseOnUnplugEnabled(context, it)
        }
        EngineToggleRow(
            title = "Pause at Zero Media Volume",
            subtitle = "Automatically pauses track if hardware volume is muted to 0",
            checked = pauseZeroVol,
            tag = "pause_zero_vol_switch",
        ) {
            pauseZeroVol = it
            setPauseOnZeroVolume(context, it)
        }
        EngineToggleRow(
            title = "Auto-Skip Unavailable Tracks",
            subtitle = "Advances to next queued song cleanly if a stream throws a fatal source error",
            checked = autoSkipError,
            tag = "auto_skip_unavailable_switch",
        ) {
            autoSkipError = it
            setAutoSkipUnavailable(context, it)
        }
        EngineToggleRow(
            title = "Soft Fade-In on Resume",
            subtitle = "Gentle 280ms amplitude ramp prevents speaker pops",
            checked = fadeInPlay,
            tag = "fade_in_on_play_switch",
        ) {
            fadeInPlay = it
            setFadeInOnPlay(context, it)
        }
        EngineToggleRow(
            title = "32-bit Float PCM DSP Path",
            subtitle = "Maximum internal headroom for FLAC & multi-band Parametric EQ",
            checked = floatAudio,
            tag = "float_audio_output_switch",
        ) {
            floatAudio = it
            setFloatAudioOutput(context, it)
        }
        EngineToggleRow(
            title = "Hardware Audio Offload (Gapless + Speed)",
            subtitle = "Uses low-power Media3 DSP offload with gapless and speed support when available",
            checked = audioOffload,
            tag = "audio_offload_switch",
        ) {
            audioOffload = it
            setAudioOffloadEnabled(context, it)
            SongPlayer.refreshAudioOffloadPolicy(context)
        }
        EngineToggleRow(
            title = "Spectrum Visualizer Overlay",
            subtitle = "Display reactive spectrum bars inside Now Playing UI",
            checked = spectrumVisualizer,
            tag = "spectrum_visualizer_switch",
        ) {
            spectrumVisualizer = it
            setSpectrumVisualizerEnabled(context, it)
        }
        EngineToggleRow(
            title = "Mini-Player Progress Bar",
            subtitle = "Show sleek real-time seek progress indicator above mini-player",
            checked = miniPlayerProgress,
            tag = "mini_player_progress_switch",
        ) {
            miniPlayerProgress = it
            setMiniPlayerProgressVisible(context, it)
        }

        // Sleep Timer Quick Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF22222B))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val remainingMin = if (sleepEndAt > System.currentTimeMillis()) {
                ((sleepEndAt - System.currentTimeMillis()) / 60_000L + 1).toInt()
            } else 0
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Sleep Timer", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (remainingMin > 0) "$remainingMin min remaining" else "Off",
                    color = if (remainingMin > 0) AppPalette else Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(15, 30, 45, 60, 0).forEach { mins ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF2E303C))
                            .clickable {
                                if (mins == 0) {
                                    SongPlayer.cancelSleepTimer()
                                    sleepEndAt = 0L
                                } else {
                                    SongPlayer.setSleepTimer(mins * 60_000L)
                                    sleepEndAt = SongPlayer.sleepTimerEndAt
                                }
                            }
                            .testTag("sleep_timer_${mins}m_button"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (mins == 0) "Off" else "${mins}m",
                            color = if (mins == 0) Color(0xFFFF8B94) else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // A-B Loop & Quick Queue Toolkit
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF22222B))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("A-B Loop & Quick Queue Tools", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(
                when {
                    abStart >= 0L && abEnd > abStart -> "Active loop: ${formatMs(abStart)} -> ${formatMs(abEnd)}"
                    abStart >= 0L -> "Point A locked at ${formatMs(abStart)} — tap Set B to engage loop"
                    else -> "Repeat any section cleanly between custom A and B timestamps"
                },
                color = Color(0xFFB3B3B3),
                fontSize = 11.sp,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        SongPlayer.setAbLoopStart()
                        abStart = SongPlayer.abLoopStartMs
                        abEnd = SongPlayer.abLoopEndMs
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E303C), contentColor = Color.White),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("ab_loop_set_a_button"),
                ) { Text("Set A", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                Button(
                    onClick = {
                        SongPlayer.setAbLoopEnd()
                        abStart = SongPlayer.abLoopStartMs
                        abEnd = SongPlayer.abLoopEndMs
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppPalette, contentColor = Color.Black),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("ab_loop_set_b_button"),
                ) { Text("Set B", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                TextButton(
                    onClick = {
                        SongPlayer.clearAbLoop()
                        abStart = -1L
                        abEnd = -1L
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("ab_loop_clear_button"),
                ) { Text("Clear", color = Color(0xFFFF8B94), fontSize = 12.sp) }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = {
                        val removed = SongPlayer.deduplicateQueue()
                        Toast.makeText(context, "Removed $removed duplicate track(s)", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("queue_deduplicate_button"),
                ) { Text("Deduplicate", color = AppPalette, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                TextButton(
                    onClick = {
                        val shuffled = SongPlayer.shuffleUpcomingQueue()
                        Toast.makeText(context, "Shuffled $shuffled upcoming track(s)", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("queue_shuffle_upcoming_button"),
                ) { Text("Shuffle Next", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                TextButton(
                    onClick = {
                        val trimmed = SongPlayer.trimPlayedQueueHistory()
                        Toast.makeText(context, "Trimmed $trimmed played track(s)", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("queue_trim_played_button"),
                ) { Text("Trim Played", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            }
        }

        // Home Screen Widgets Pinning Card (Android 8+)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF22222B))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Home Screen Widgets", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(
                "Add interactive Glance Now Playing widgets (2×2 Compact or 4×2 Full Bar with artwork & favorites) directly to your launcher.",
                color = Color(0xFFB3B3B3),
                fontSize = 11.sp,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        val awm = android.appwidget.AppWidgetManager.getInstance(context)
                        val comp = com.music.spotui.widget.GlanceWidgetUpdater.compactComponent(context)
                        if (awm.isRequestPinAppWidgetSupported) {
                            awm.requestPinAppWidget(comp, null, null)
                        } else {
                            Toast.makeText(context, "Add Spotui Mini Player via your launcher's widget picker.", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E303C), contentColor = Color.White),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("widget_pin_compact_button"),
                ) { Text("Pin 2×2 Mini", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                Button(
                    onClick = {
                        val awm = android.appwidget.AppWidgetManager.getInstance(context)
                        val comp = com.music.spotui.widget.GlanceWidgetUpdater.expandedComponent(context)
                        if (awm.isRequestPinAppWidgetSupported) {
                            awm.requestPinAppWidget(comp, null, null)
                        } else {
                            Toast.makeText(context, "Add Spotui Now Playing via your launcher's widget picker.", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppPalette, contentColor = Color.Black),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("widget_pin_expanded_button"),
                ) { Text("Pin 4×2 Full", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }

        // Direct Shortcut to 21-Point Health Scanner & Logger
        Button(
            onClick = onOpenDeveloperConsole,
            colors = ButtonDefaults.buttonColors(containerColor = AppPalette, contentColor = Color.Black),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("open_health_scanner_from_playback_button"),
        ) {
            Text("Run 21-Point System Health Check & Logger", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EngineToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color(0xFFB3B3B3), fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AppPalette,
                uncheckedThumbColor = Color(0xFFB3B3B3),
                uncheckedTrackColor = Color(0xFF333333),
            ),
        )
    }
}

private fun formatMs(ms: Long): String {
    if (ms < 0L) return "0:00"
    val totalSec = ms / 1000L
    val m = totalSec / 60L
    val s = totalSec % 60L
    return String.format("%d:%02d", m, s)
}
