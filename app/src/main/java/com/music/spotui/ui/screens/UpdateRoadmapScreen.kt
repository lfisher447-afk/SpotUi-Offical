package com.music.spotui.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.AppPalette

private data class ReleaseEntry(
    val version: String,
    val title: String,
    val details: String,
)

private const val ROADMAP_INTEGRATION_DATE = "2026-08-17"

private val releaseHistory = listOf(
    ReleaseEntry("1.5.1", "Timed lyrics foundation", "Added corrected LRC timestamp parsing and sorted millisecond lyric streams."),
    ReleaseEntry("1.5.2", "Local library continuity", "Retained the local library and offline collection flow with safe stored metadata."),
    ReleaseEntry("1.5.3", "Playlist portability", "Added selected-track manifest export for playlist and album selections."),
    ReleaseEntry("1.5.4", "Collection resilience", "Preserved local artwork and collection continuity during source refreshes."),
    ReleaseEntry("1.5.5", "Equalizer integration", "Connected the equalizer preferences to the active Android audio session."),
    ReleaseEntry("1.5.6", "Spatial audio preference", "Added guarded spatial-audio support where the device exposes a compatible effect."),
    ReleaseEntry("1.5.7", "Audio-session lifecycle", "Attached and released playback effects with the Media3 audio session."),
    ReleaseEntry("1.5.8", "Crossfade continuity", "Preserved the existing crossfade controls and playback transition behavior."),
    ReleaseEntry("1.5.9", "Playback visuals", "Preserved player artwork, lyrics, queue, and visual playback surfaces."),
    ReleaseEntry("1.6.0", "System typography", "Removed packaged custom-font binaries and standardised all theme aliases on Android SansSerif."),
    ReleaseEntry("1.6.1", "Player configuration access", "Added a direct player-menu route to Audio & Equalizer controls."),
    ReleaseEntry("1.6.2", "Playback controls", "Retained queue, sleep timer, sharing, download, album, and artist controls."),
    ReleaseEntry("1.6.3", "Media integration", "Preserved media session, notification, deep-link, and Android Auto integration paths."),
    ReleaseEntry("1.6.4", "Collapsible settings", "Introduced expandable Audio, Storage, and Diagnostics setting sections."),
    ReleaseEntry("1.6.5", "Privacy boundaries", "Kept account credentials user-controlled and excluded them from persistent diagnostics."),
    ReleaseEntry("1.6.6", "Permission-aware features", "Kept microphone and recognition capabilities opt-in and permission-aware."),
    ReleaseEntry("1.6.7", "Sharing and Canvas", "Preserved sharing and Canvas discovery without storing provider credentials in logs."),
    ReleaseEntry("1.6.8", "Source fallback", "Retained source fallback controls with persisted diagnostics for failures."),
    ReleaseEntry("1.6.9", "Playback behavior", "Preserved existing queue and playback behavior while strengthening error capture."),
    ReleaseEntry("1.7.0", "Library organization", "Retained library and queue foundations for local playback organization."),
    ReleaseEntry("1.7.1", "Listening history", "Preserved the history surface and related navigation."),
    ReleaseEntry("1.7.2", "Radio and queue", "Preserved the existing queue and radio generation flows."),
    ReleaseEntry("1.7.3", "Lyrics support", "Retained lyrics and translation-cache paths with corrected parser compilation."),
    ReleaseEntry("1.7.4", "Persistent diagnostics", "Added bounded app-private diagnostics for launches, warnings, errors, and handled failures."),
    ReleaseEntry("1.7.5", "Build stability", "Constrained Gradle worker and memory defaults for predictable local builds."),
    ReleaseEntry("1.7.6", "Safe cache operations", "Replaced simulated cache reporting with real cache summaries and non-destructive cleanup."),
    ReleaseEntry("1.7.7", "Startup visibility", "Added application, provider warm-up, backup, and deep-link diagnostic capture."),
    ReleaseEntry("1.7.8", "Diagnostic console", "Routed developer-console warnings and errors into persistent diagnostics."),
    ReleaseEntry("1.7.9", "Application startup repair", "Restored Kotlin compilation to Android modules so the manifest application class is packaged into DEX."),
    ReleaseEntry("1.8.0", "Android API configuration", "Retained API 36 compile and target settings with explicit build configuration."),
    ReleaseEntry("1.8.1", "Audio navigation", "Kept direct navigation from player controls to audio configuration."),
    ReleaseEntry("1.8.2", "Settings usability", "Completed collapsible settings foundations and state preservation."),
    ReleaseEntry("1.8.3", "NovaAc compatibility", "Added encrypted NovaAc manifest headers and an in-app compatibility inspection flow."),
    ReleaseEntry("1.8.4", "Accessible state clarity", "Added descriptive collapse/expand and diagnostic control labels."),
    ReleaseEntry("1.8.5", "Release identity", "Set the app identity to version 1.8.9 with a monotonic Android version code."),
    ReleaseEntry("1.8.6", "Cache-manifest security", "Used Android Keystore-backed AES-GCM for NovaAc metadata manifests and redacted diagnostics."),
    ReleaseEntry("1.8.7", "Artifact checks", "Added source and APK integrity-verification records to the delivery workflow."),
    ReleaseEntry("1.8.8", "Integration preparation", "Consolidated cache safety, diagnostics, audio configuration, and system typography."),
    ReleaseEntry("1.8.9", "Stable integration candidate", "Completed the sequential release record, custom-font removal, startup-class repair, and final integration validation."),
    ReleaseEntry("1.9.5", "NovaAc full-audio archive", "Added direct playlist and liked-song archive export with audio coverage planning, passphrase portability, adaptive navigation, and expanded audio controls."),
    ReleaseEntry("1.9.9", "Reliability and library expansion", "Added sequential playlist downloads, fresh resolver fallback, five-band EQ, software spatial processing, optional YouTube Music companion, MrBean transport settings, and collapsible library groups."),
    ReleaseEntry("2.0.0", "Integrated media and expressive release", "Updated all Media3 modules to 1.11.0; added concrete MrBean Media3 transport, provider routing policy, NovaAc v5 integrity/container metadata, official Google OAuth YouTube playlist sync, accessibility audio controls, and Material 3 expressive Theme Studio with JSON and image-derived palettes."),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateRoadmapScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SpotUI update history", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF121218)),
            )
        },
        containerColor = AppBackground,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF17171F))
                    .border(1.dp, Color(0xFF2B2B35), RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Text("Sequential release integration", color = AppPalette, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Every release entry from 1.5.1 to 1.8.9 is recorded below. Entries were added to this in-app changelog on $ROADMAP_INTEGRATION_DATE.",
                    color = Color(0xFFCACAD2),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text("Typography: Android system SansSerif only; no packaged custom fonts.", color = Color(0xFF81C784), fontSize = 12.sp)
            }

            Spacer(Modifier.height(16.dp))
            releaseHistory.forEach { entry ->
                ReleaseEntryCard(entry)
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun ReleaseEntryCard(entry: ReleaseEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF17171F))
            .border(1.dp, Color(0xFF2B2B35), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AppPalette)
        Column(modifier = Modifier.weight(1f)) {
            Text("v${entry.version} · $ROADMAP_INTEGRATION_DATE", color = AppPalette, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Text(entry.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(entry.details, color = Color(0xFFCACAD2), fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}
