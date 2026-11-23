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
        title = "Accessibility Floater Play/Pause & Corner Resize (v2.5.3)",
        detail = "Fixed Play/Pause taps not toggling when MediaController was buffering or idle-stopped while Next/Prev worked. All floater transport buttons now route straight through PlaybackService & SongPlayer with instant haptic feedback and true corner-edge resize tracking.",
    ),
    PatchNoteItem(
        title = "3D Spatial Flatness & Audibility (v2.5.3)",
        detail = "Overhauled SpatialWideningAudioProcessor with stereo Haas micro-taps, upper-band Mid/Side expansion, tapped Early Reflections into the 3-stage Schroeder tank, stronger HRTF pinna peaks (+4.2 dB @ 3.2 kHz), removed stereo-collapsing default crossfeed, and boosted 8D Binaural Orbit LFO modulation.",
    ),
    PatchNoteItem(
        title = "Spotify WebView Login Anti-Bot / HTTP 400 Rejections (v2.5.3)",
        detail = "Reworked Login with a clean, vanilla Android WebView (removed UA spoofing and DOM injection scripts that triggered Spotify risk/400 errors), paired with 1-tap Chrome Sign-In, Cookie Paste, and Guest mode.",
    ),
    PatchNoteItem(
        title = "Sequential Downloads & Unpooled HTTP Bottlenecks (v2.5.2)",
        detail = "Replaced single-thread HttpURLConnection transfers and low-concurrency pools with MrBean's pooled HTTP/2 + 206 multi-range downloader, parallel DASH-FLAC segment batches, and non-blocking metadata prefetch.",
    ),
    PatchNoteItem(
        title = "Bluetooth False-Positive SCO Call Mode & Route Probe Log Spam",
        detail = "Fixed dual-profile Bluetooth headsets (A2DP + HFP) flagging 'SCO Call' when media A2DP was active, added real SCO communication-device release, and eliminated duplicate BT probe log entries.",
    ),
    PatchNoteItem(
        title = "NovaAc Fast Export Speed & Local Downloaded Playback",
        detail = "Streams audio single-pass with TeeDigest SHA-256 + 4 MiB buffers for multi-MB/s offline exports and reliable audio session attachment.",
    ),
)

private val v25Improvements = listOf(
    PatchNoteItem(
        title = "Material 3 Expressive Overhaul & Side-Tab Settings (v2.5.3)",
        detail = "Redesigned SpotUI with a luminous Material 3 Expressive palette, animated Navigation Rail side-tab switcher in Settings, expressive Mini-Player & Bottom Navigation dock, upgraded Home & Downloads hero cards, and sleeker Floater Studio UI.",
    ),
    PatchNoteItem(
        title = "Turbo 8x MrBean Download Engine (v2.5.3)",
        detail = "Upgraded connection pool (32 keep-alive sockets / 128 dispatcher), 512 KiB I/O buffers, 5 simultaneous tracks by default (up to 10), and 8 parallel HTTP 206 range slices kicking in for files >= 512 KiB with live peak MB/s tracking.",
    ),
    PatchNoteItem(
        title = "Dedicated 2000-Entry Categorized Live Logger (v2.5.3)",
        detail = "Expanded Dev Console into an 8-channel categorized telemetry stream (Playback, Audio DSP, Network/DL, Auth, Floater UI, Bluetooth, Modules, System) with real-time counters, live download throughput badge, and DSP Probe diagnostics.",
    ),
    PatchNoteItem(
        title = "16 Integrated SpotUI Modules + Health Probe (v2.5.3)",
        detail = "Added MrBean Turbo Engine, Media3 DSP Studio, Floater Accessibility Service, NovaAc Offline Vault, Glance Home Widgets, and Android Auto Browser alongside all 10 lyric/discovery submodules with 1-tap health verification.",
    ),
    PatchNoteItem(
        title = "M3 Expressive Accessibility Floater Studio (v2.5.3)",
        detail = "Direct In-App Volume slider (0-100%, OS volume untouched), mute/-5%/+5% step pills, expandable 7-band EQ + 3D Spatial + 8D Orbit drawer, smooth corner-drag resizing, and configurable haptics.",
    ),
    PatchNoteItem(
        title = "Tactile UI Sound Engine & Native PackageInstaller Updater",
        detail = "Low-latency PCM chimes on switches/sliders/tabs plus multi-range verified APK self-updates.",
    ),
)

/**
 * First-login / post-update "What's New & Patch Notes" UI modal displaying explicit root-cause
 * fixes and feature improvements for SpotUI v2.5.3.
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
