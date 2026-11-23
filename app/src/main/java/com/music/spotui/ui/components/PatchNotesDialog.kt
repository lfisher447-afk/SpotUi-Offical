package com.music.spotui.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.BuildCircle
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.music.spotui.BuildConfig
import com.music.spotui.audio.UiSoundEngine
import com.music.spotui.ui.theme.AppPalette

private data class PatchNoteItem(
    val title: String,
    val detail: String,
)

private val v25IssuesFixed = listOf(
    PatchNoteItem(
        title = "Samsung One UI Game Booster Floater & Now Playing Repair (v2.5.4)",
        detail = "Fixed missing active/paused track info in the accessibility floater and repaired Play/Pause, Next, Previous, Shuffle, Loop, and Queue tap routing across cold-start, buffering, and background playback states.",
    ),
    PatchNoteItem(
        title = "Spotify Login HTTP 400 / Storage Access Denial (v2.5.4)",
        detail = "Stripped WebView `; wv` / legacy version tokens that caused accounts.spotify.com/login/code/send HTTP 400 errors and requestStorageAccess denials, added XOR TOTP v61 secret decoding, and added Auto-Recover cycling.",
    ),
    PatchNoteItem(
        title = "InnerTube Stream Stalls & Client 403 Loops (v2.5.4)",
        detail = "Added per-client 90s circuit-breaker cooldowns, HTTP 429/5xx exponential backoff retries, MWEB/ANDROID_MUSIC/2026 client presets, NewPipe fallback activation, and HTML VISITOR_DATA extraction fallback.",
    ),
    PatchNoteItem(
        title = "Accessibility Floater Play/Pause & Corner Resize (v2.5.3)",
        detail = "All floater transport buttons now route straight through PlaybackService & SongPlayer with instant haptic feedback and smooth bottom-corner resize dragging.",
    ),
    PatchNoteItem(
        title = "3D Spatial Flatness & Audibility (v2.5.3)",
        detail = "Overhauled SpatialWideningAudioProcessor with stereo Haas micro-taps, upper-band Mid/Side expansion, tapped Early Reflections into the 3-stage Schroeder tank, stronger HRTF pinna peaks, and boosted 8D Binaural Orbit modulation.",
    ),
    PatchNoteItem(
        title = "Sequential Downloads & Unpooled HTTP Bottlenecks (v2.5.2)",
        detail = "Replaced single-thread HttpURLConnection transfers and low-concurrency pools with MrBean's pooled HTTP/2 + 206 multi-range downloader, parallel DASH-FLAC segment batches, and non-blocking metadata prefetch.",
    ),
    PatchNoteItem(
        title = "Bluetooth False-Positive SCO Call Mode & Route Probe Log Spam",
        detail = "Fixed dual-profile Bluetooth headsets (A2DP + HFP) flagging 'SCO Call' when media A2DP was active, added real SCO communication-device release, and eliminated duplicate BT probe log entries.",
    ),
)

private val v25Improvements = listOf(
    PatchNoteItem(
        title = "One UI Game Booster SpotUI Floater Studio (v2.5.4)",
        detail = "Redesigned the floating overlay with a One UI Game Booster aesthetic featuring a collapsible docked sidebar rail (MEDIA, SOUND, PRO), 5 tabbed panels (Player, Queue, EQ, 3D, Tools), circular tile controls, shuffle/loop/like pills, 0-100% In-App Volume slider, corner-drag resize, and tactile vibration.",
    ),
    PatchNoteItem(
        title = "Embedded APK-Native yt-dlp + FFmpeg Engine (v2.5.4)",
        detail = "Integrated on-device youtubedl-android 0.18.1 + FFmpeg directly inside the APK (no PC or external server required) with primary extraction toggle, live binary updater, and automatic fallback when InnerTube clients fail.",
    ),
    PatchNoteItem(
        title = "Material 3 Expressive Overhaul & Side-Tab Settings (v2.5.3)",
        detail = "Redesigned SpotUI with a luminous Material 3 Expressive palette, animated Navigation Rail side-tab switcher in Settings, expressive Mini-Player & Bottom Navigation dock, and upgraded Home & Downloads hero cards.",
    ),
    PatchNoteItem(
        title = "Turbo 8x MrBean Download Engine (v2.5.3)",
        detail = "Upgraded connection pool (32 keep-alive sockets / 128 dispatcher), 512 KiB I/O buffers, 5 simultaneous tracks by default (up to 10), and 8 parallel HTTP 206 range slices kicking in for files >= 512 KiB with live peak MB/s tracking.",
    ),
    PatchNoteItem(
        title = "Dedicated 2000-Entry Categorized Live Logger (v2.5.3)",
        detail = "Expanded Dev Console into an 8-channel categorized telemetry stream (Playback, Audio DSP, Network/DL, Auth, Floater UI, Bluetooth, Modules, System) with real-time counters and live throughput badges.",
    ),
    PatchNoteItem(
        title = "17 Integrated SpotUI Modules + Health Probe (v2.5.4)",
        detail = "Added Embedded yt-dlp + FFmpeg runtime alongside MrBean Turbo Engine, Media3 DSP Studio, Floater Accessibility Service, NovaAc Vault, Glance Widgets, Android Auto, and all 10 lyric/discovery submodules.",
    ),
)

/**
 * First-login / post-update "What's New & Patch Notes" UI modal displaying explicit root-cause
 * fixes and feature improvements for SpotUI v2.5.4.
 */
@Composable
fun PatchNotesDialog(
    onDismiss: () -> Unit,
    onOpenDiagnostics: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scroll = rememberScrollState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .testTag("patch_notes_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF121318),
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .border(
                        width = 1.dp,
                        color = AppPalette.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(24.dp),
                    )
                    .padding(20.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(AppPalette, Color(0xFFF58B2B)),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.NewReleases,
                            contentDescription = "SpotUI Patch Notes",
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SpotUI v${BuildConfig.VERSION_NAME}",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text = "What's New, Root-Cause Fixes & Architecture Notes",
                            color = Color(0xFFB3B8C4),
                            fontSize = 12.sp,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(scroll),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = "Issues Fixed",
                            tint = Color(0xFFFF7A59),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Issues Found & Fixed",
                            color = Color(0xFFFFA58C),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    v25IssuesFixed.forEach { item ->
                        PatchNoteCard(
                            title = item.title,
                            body = item.detail,
                            accent = Color(0xFFFF7A59),
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Improvements",
                            tint = AppPalette,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Improvements & How They Work",
                            color = AppPalette,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    v25Improvements.forEach { item ->
                        PatchNoteCard(
                            title = item.title,
                            body = item.detail,
                            accent = AppPalette,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (onOpenDiagnostics != null || onOpenSettings != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (onOpenDiagnostics != null) {
                            OutlinedButton(
                                onClick = {
                                    UiSoundEngine.playTapChime(context)
                                    onDismiss()
                                    onOpenDiagnostics()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("patch_notes_bt_diagnostics_button"),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BuildCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF8BE9FD),
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("BT & Faults", color = Color.White, fontSize = 12.sp)
                            }
                        }
                        if (onOpenSettings != null) {
                            OutlinedButton(
                                onClick = {
                                    UiSoundEngine.playTapChime(context)
                                    onDismiss()
                                    onOpenSettings()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("patch_notes_settings_button"),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = AppPalette,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Modules & UI", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Button(
                    onClick = {
                        UiSoundEngine.playTapChime(context)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppPalette,
                        contentColor = Color.Black,
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("patch_notes_continue_button"),
                ) {
                    Text(
                        text = "Got It — Start Listening",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun PatchNoteCard(
    title: String,
    body: String,
    accent: Color,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1A1C24))
            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = body,
            color = Color(0xFFD0D5E0),
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
    }
}
