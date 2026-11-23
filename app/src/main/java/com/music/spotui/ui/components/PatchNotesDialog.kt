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
        title = "Sequential Downloads & Unpooled HTTP Bottlenecks (v2.5.2)",
        detail = "Replaced single-thread HttpURLConnection transfers and low-concurrency pools with MrBean's pooled HTTP/2 + 206 multi-range downloader, parallel DASH-FLAC segment batches, and non-blocking metadata prefetch.",
    ),
    PatchNoteItem(
        title = "3D Spatial Comb-Filtering & Head-Stage Flatness (v2.5.2)",
        detail = "Replaced single-delay Haas widening with a 3-stage cascaded Schroeder all-pass decorrelator tank, sub-120 Hz mono-bass anchor, Woodworth spherical-head HRTF, and automatic correlation guard.",
    ),
    PatchNoteItem(
        title = "Spotify WebView Login HTTP 402 / 400 & Gist Rate-Limit Failures",
        detail = "Fixed password-login URL hints, added 1-tap Auto-Recover UA & cookie cycling for HTTP 402/400/403 risk challenges, cached TOTP secrets with raw Gist fallback, and added #session fallback token extraction.",
    ),
    PatchNoteItem(
        title = "Bluetooth False-Positive SCO Call Mode & Route Probe Log Spam",
        detail = "Fixed dual-profile Bluetooth headsets (A2DP + HFP) flagging 'SCO Call' when media A2DP was active, added real SCO communication-device release, and eliminated duplicate BT probe log entries.",
    ),
    PatchNoteItem(
        title = "Spotify Login \"Incorrect Password\" & Native Redirect Bug",
        detail = "Fixed an issue where spoofing a Windows Desktop Chrome User-Agent inside Android WebView triggered Spotify's anti-bot heuristic ('Incorrect username or password' on valid accounts), and Native Login discarded credentials instead of auto-filling.",
    ),
    PatchNoteItem(
        title = "Invisible Login & Cookie Exchange Failures",
        detail = "Fixed an issue where WebView HTTP errors, CAPTCHA/DOM alerts, and SpotifyAuth token exchange errors went unlogged instead of appearing in Live Diagnostics.",
    ),
    PatchNoteItem(
        title = "NovaAc Fast Export Speed (~556 KB/s cap)",
        detail = "Fixed an issue where double-pass SHA-256 hashing, per-frame 512 KiB buffers, and forced GZIP deflation on compressed audio was causing NovaAc exports to crawl at ~556 KB/s.",
    ),
    PatchNoteItem(
        title = "Search \"Add to Playlist\" Button",
        detail = "Fixed an issue where tapping Save-to-Playlist on search results with empty Spotify URIs was causing silent no-ops instead of saving to local playlists.",
    ),
    PatchNoteItem(
        title = "Bluetooth & 3D Spatial Audio Conflicts",
        detail = "Fixed an issue where A2DP hardware offload and aggressive preamp/spatial mixing were causing silent or muted playback on Bluetooth headsets.",
    ),
    PatchNoteItem(
        title = "Downloaded Song Local Playback",
        detail = "Fixed an issue where exported offline tracks could fail URI resolution or miss audio session attachment during local playback.",
    ),
    PatchNoteItem(
        title = "Unexposed modules/app/libs Submodules",
        detail = "Fixed an issue where BetterLyrics, Paxsenix, LrcLib, KuGou, SimpMusic, Last.fm, ShazamKit, and Fastlane were omitted from Gradle/Settings and unreachable in-app.",
    ),
)

private val v25Improvements = listOf(
    PatchNoteItem(
        title = "Eclipse MrBean Turbo Downloader (1–10 Concurrent Tracks) (v2.5.2)",
        detail = "Configure 1–10 simultaneous song downloads live from Downloads or Settings, paired with up to 6 parallel HTTP 206 FileChannel range workers per track, HTTP/2 multiplexing, TTL DNS caching, and live MB/s telemetry.",
    ),
    PatchNoteItem(
        title = "3D Spatial Soundstage v2 + 8D Binaural Orbit (v2.5.2)",
        detail = "Added cascaded Schroeder all-pass room acoustics, Woodworth HRTF + Pinna elevation cues, optional 8D Binaural Orbit LFO (0.10–1.50 Hz), phase correlation protection, and Android 13–17 Spatializer telemetry.",
    ),
    PatchNoteItem(
        title = "Native Android PackageInstaller Auto-Updater (v2.5.2)",
        detail = "Downloads GitHub Release APKs via HTTP/2 multi-range slices with live MB/s & ETA telemetry, verifies PK header + SHA-256 checksums, and installs in-place through Android's native PackageInstaller Session API with live progress & status callbacks.",
    ),
    PatchNoteItem(
        title = "Hardened WebView Spotify Auth + #session Token Bridge",
        detail = "Strips legacy password-endpoint query hints, adds OAuth/main WebView HTTP 402/400 Auto-Recover cycling, passes sp_key in token minting, and seeds verified WebView session tokens seamlessly.",
    ),
    PatchNoteItem(
        title = "Authentic Android Chrome Auth + Email Code + Live Logs",
        detail = "Made Spotify sign-in reliable without external cookie extensions -> How it works: strips '; wv' from your real device Chromium UA, supports OAuth popups & 1-tap Email Code login, parses Cookie-Editor JSON, and logs HTTP/DOM/Token faults live inside Sign In.",
    ),
    PatchNoteItem(
        title = "Multi-MB/s Single-Pass NovaAc Streaming",
        detail = "Made NovaAc Fast Mode run in megabytes/sec -> How it works: streams audio in a single pass with inline TeeDigest SHA-256, 2 MiB AEAD cipher frames, 4 MiB buffers, and NO_COMPRESSION audio framing.",
    ),
    PatchNoteItem(
        title = "New Expressive SongPlay UI + Legacy Toggle",
        detail = "Made the SongPlay player screen richer & toggleable -> How it works: added an ambient artwork halo, live BT/DSP telemetry pill, and instant volume/3D dock while keeping Legacy Classic selectable in Advanced Settings.",
    ),
    PatchNoteItem(
        title = "All 10 modules/app/libs Submodules Configurable",
        detail = "Made every submodule live & customizable -> How it works: ModuleLibsBridge routes prioritized waterfall lyrics, Last.fm scrobbling, Shazam cache cleanup, and Fastlane channel flags straight from Advanced Settings.",
    ),
    PatchNoteItem(
        title = "Bluetooth Specs, Conflict Fixer & Button Fault Logger",
        detail = "Made diagnostics catch BT & UI faults -> How it works: probes active A2DP/LE codecs (LDAC/aptX/AAC/LC3), highlights SpotUI-caused conflicts with 1-tap fixes, and tracks faulty button taps in Dev Console.",
    ),
    PatchNoteItem(
        title = "Tactile UI Sound Engine & Auto-Location Home Clock",
        detail = "Made navigation, switches, sliders & home feel alive -> How it works: synthesizes low-latency PCM tab/toggle/slider chimes via SONIFICATION audio focus and ticks your local timezone clock live on Home.",
    ),
)

/**
 * First-login / post-update "What's New & Patch Notes" UI modal displaying explicit root-cause
 * fixes and feature improvements for SpotUI v2.5.2.
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
