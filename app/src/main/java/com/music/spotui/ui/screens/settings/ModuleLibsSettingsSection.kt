package com.music.spotui.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.spotui.audio.UiSoundEngine
import com.music.spotui.data.api.ModuleLibsBridge
import com.music.spotui.data.preferences.ModuleLibsConfig
import com.music.spotui.data.preferences.SpotuiModuleId
import com.music.spotui.ui.theme.AppPalette
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Full configuration panel inside Advanced Settings exposing all 10 submodules in
 * `modules/app/libs` (Spotify, InnerTube, LRCLIB, BetterLyrics, KuGou, SimpMusic,
 * Paxsenix, Last.fm, ShazamKit, and Fastlane).
 */
@Composable
fun ModuleLibsSettingsSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val revision by ModuleLibsConfig.moduleRevision.collectAsState()

    val statuses = remember(revision) { ModuleLibsBridge.collectStatusSummary(context) }
    val lyricsOrder = remember(revision) { ModuleLibsConfig.getLyricsModuleOrder(context) }
    val kugouTrad = remember(revision) { ModuleLibsConfig.isKugouTraditionalChinese(context) }
    val paxsenixMulti = remember(revision) { ModuleLibsConfig.isPaxsenixMultiEndpoint(context) }
    val lastFmScrobble = remember(revision) { ModuleLibsConfig.isLastFmScrobbleEnabled(context) }
    val lastFmThreshold = remember(revision) { ModuleLibsConfig.getLastFmScrobbleThresholdPercent(context) }
    val shazamCache = remember(revision) { ModuleLibsConfig.isShazamCacheEnabled(context) }
    val fastlaneChannel = remember(revision) { ModuleLibsConfig.getFastlaneChannel(context) }

    var lastFmApiKey by remember { mutableStateOf(ModuleLibsConfig.getLastFmApiKey(context)) }
    var lastFmSecret by remember { mutableStateOf(ModuleLibsConfig.getLastFmSecret(context)) }
    var lastFmUserInput by remember { mutableStateOf(ModuleLibsConfig.getLastFmUsername(context)) }
    var lastFmPassword by remember { mutableStateOf("") }
    var lastFmBusy by remember { mutableStateOf(false) }
    var lastFmNote by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A1A22))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Extension,
                contentDescription = null,
                tint = AppPalette,
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    "modules/app/libs Suite (${SpotuiModuleId.entries.size} Modules)",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Inspect, toggle & configure every integrated Gradle library submodule",
                    color = Color(0xFFB3B8C4),
                    fontSize = 11.sp,
                )
            }
        }

        // ── All 10 Submodules Inventory + Enable Toggles ──
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            statuses.forEach { st ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF12131A))
                        .border(
                            1.dp,
                            if (st.enabled) AppPalette.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f),
                            RoundedCornerShape(10.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = st.id.displayName,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = st.id.category.uppercase(),
                                color = AppPalette,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(AppPalette.copy(alpha = 0.14f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        Text(
                            text = "${st.id.gradlePath} · ${st.summary}",
                            color = Color(0xFF9EA5B5),
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                        )
                    }
                    Switch(
                        checked = st.enabled,
                        onCheckedChange = { checked ->
                            ModuleLibsConfig.setModuleEnabled(context, st.id, checked)
                            UiSoundEngine.playToggleSound(context, checked)
                        },
                        modifier = Modifier.testTag("module_toggle_${st.id.id}"),
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AppPalette),
                    )
                }
            }
        }

        HorizontalDivider(color = Color(0xFF282A36))

        // ── Lyrics Waterfall Priority Order ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Lyrics Module Waterfall Order", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("Priority cascade for synced LRC/TTML resolution", color = Color(0xFF9EA5B5), fontSize = 11.sp)
            }
            TextButton(
                onClick = {
                    UiSoundEngine.playTapChime(context)
                    ModuleLibsConfig.resetLyricsOrder(context)
                },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = AppPalette, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Reset", color = AppPalette, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            lyricsOrder.forEachIndexed { index, modId ->
                val modEnum = SpotuiModuleId.fromId(modId)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF12131A))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "#${index + 1}",
                        color = AppPalette,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.width(28.dp),
                    )
                    Text(
                        text = modEnum?.displayName ?: modId,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        enabled = index > 0,
                        onClick = {
                            UiSoundEngine.playTapChime(context)
                            ModuleLibsConfig.moveLyricsModule(context, modId, up = true)
                        },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Move $modId up",
                            tint = if (index > 0) Color.White else Color(0xFF4A4E5D),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(
                        enabled = index < lyricsOrder.lastIndex,
                        onClick = {
                            UiSoundEngine.playTapChime(context)
                            ModuleLibsConfig.moveLyricsModule(context, modId, up = false)
                        },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "Move $modId down",
                            tint = if (index < lyricsOrder.lastIndex) Color.White else Color(0xFF4A4E5D),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        // ── KuGou & Paxsenix Options ──
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text("KuGou Traditional Chinese lyrics", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("Prefer Traditional Chinese characters from :kugou", color = Color(0xFF9EA5B5), fontSize = 11.sp)
            }
            Switch(
                checked = kugouTrad,
                onCheckedChange = {
                    ModuleLibsConfig.setKugouTraditionalChinese(context, it)
                    UiSoundEngine.playToggleSound(context, it)
                },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AppPalette),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text("Paxsenix Multi-Endpoint relay", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("Query Apple Music + NetEase + Spotify + Musixmatch mirrors in :paxsenix", color = Color(0xFF9EA5B5), fontSize = 11.sp)
            }
            Switch(
                checked = paxsenixMulti,
                onCheckedChange = {
                    ModuleLibsConfig.setPaxsenixMultiEndpoint(context, it)
                    UiSoundEngine.playToggleSound(context, it)
                },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AppPalette),
            )
        }

        HorizontalDivider(color = Color(0xFF282A36))

        // ── Last.fm Scrobbler Config ──
        Text("Last.fm Module (:lastfm)", color = AppPalette, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text("Auto-Scrobble & Now Playing", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("Reports active tracks and likes to Last.fm when signed in", color = Color(0xFF9EA5B5), fontSize = 11.sp)
            }
            Switch(
                checked = lastFmScrobble,
                onCheckedChange = {
                    ModuleLibsConfig.setLastFmScrobbleEnabled(context, it)
                    UiSoundEngine.playToggleSound(context, it)
                },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AppPalette),
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Scrobble trigger threshold", color = Color.White, fontSize = 12.sp)
            Text("$lastFmThreshold%", color = AppPalette, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = lastFmThreshold.toFloat(),
            onValueChange = {
                val pct = it.roundToInt().coerceIn(25, 90)
                ModuleLibsConfig.setLastFmScrobbleThresholdPercent(context, pct)
                UiSoundEngine.playSliderTick(context, (pct - 25) / 65f)
            },
            valueRange = 25f..90f,
            colors = SliderDefaults.colors(activeTrackColor = AppPalette, thumbColor = AppPalette),
        )

        OutlinedTextField(
            value = lastFmApiKey,
            onValueChange = { lastFmApiKey = it },
            label = { Text("Last.fm API Key", color = Color(0xFFB3B8C4)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = AppPalette,
                unfocusedBorderColor = Color(0xFF343746),
            ),
        )
        OutlinedTextField(
            value = lastFmSecret,
            onValueChange = { lastFmSecret = it },
            label = { Text("Last.fm Shared Secret", color = Color(0xFFB3B8C4)) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = AppPalette,
                unfocusedBorderColor = Color(0xFF343746),
            ),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = lastFmUserInput,
                onValueChange = { lastFmUserInput = it },
                label = { Text("Username", color = Color(0xFFB3B8C4)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = AppPalette,
                    unfocusedBorderColor = Color(0xFF343746),
                ),
            )
            OutlinedTextField(
                value = lastFmPassword,
                onValueChange = { lastFmPassword = it },
                label = { Text("Password", color = Color(0xFFB3B8C4)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = AppPalette,
                    unfocusedBorderColor = Color(0xFF343746),
                ),
            )
        }
        Button(
            enabled = !lastFmBusy,
            onClick = {
                UiSoundEngine.playTapChime(context)
                lastFmBusy = true
                scope.launch {
                    val res = ModuleLibsBridge.authenticateLastFm(
                        context = context,
                        apiKey = lastFmApiKey,
                        secret = lastFmSecret,
                        username = lastFmUserInput,
                        password = lastFmPassword,
                    )
                    lastFmBusy = false
                    lastFmPassword = ""
                    lastFmNote = res.fold(
                        onSuccess = { "Connected to Last.fm as $it" },
                        onFailure = { "Last.fm error: ${it.message ?: "authentication failed"}" },
                    )
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = AppPalette, contentColor = Color.Black),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("lastfm_connect_button"),
        ) {
            Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (lastFmBusy) "Authenticating…" else "Connect Last.fm Session", fontWeight = FontWeight.Bold)
        }
        if (lastFmNote.isNotBlank()) {
            Text(lastFmNote, color = Color(0xFF8BE9FD), fontSize = 11.sp)
        }

        HorizontalDivider(color = Color(0xFF282A36))

        // ── ShazamKit & Fastlane Release Metadata ──
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("ShazamKit Signature Cache (:shazamkit)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(ModuleLibsBridge.shazamQueueSummary(), color = Color(0xFF9EA5B5), fontSize = 11.sp)
            }
            Switch(
                checked = shazamCache,
                onCheckedChange = {
                    ModuleLibsConfig.setShazamCacheEnabled(context, it)
                    UiSoundEngine.playToggleSound(context, it)
                },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AppPalette),
            )
        }
        TextButton(
            onClick = {
                UiSoundEngine.playTapChime(context)
                ModuleLibsBridge.clearShazamCache()
                Toast.makeText(context, "Cleared ShazamKit cache & pending queue", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Icon(Icons.Default.CleaningServices, contentDescription = null, tint = AppPalette, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Clear ShazamKit Cache", color = AppPalette, fontWeight = FontWeight.Bold)
        }

        Text("Fastlane Release Metadata Track (fastlane)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("production", "beta", "nightly").forEach { channel ->
                val selected = fastlaneChannel == channel
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) AppPalette else Color.White.copy(alpha = 0.08f))
                        .clickable {
                            ModuleLibsConfig.setFastlaneChannel(context, channel)
                            UiSoundEngine.playTapChime(context)
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        channel.replaceFirstChar { it.uppercase() },
                        color = if (selected) Color.Black else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
