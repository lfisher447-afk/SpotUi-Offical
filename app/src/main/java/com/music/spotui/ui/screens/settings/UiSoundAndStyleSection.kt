package com.music.spotui.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.spotui.BuildConfig
import com.music.spotui.audio.UiSoundEngine
import com.music.spotui.data.preferences.ExperiencePreferences
import com.music.spotui.data.preferences.PlayerUiStyle
import com.music.spotui.data.preferences.UiSoundStyle
import com.music.spotui.ui.theme.AppPalette
import kotlin.math.roundToInt

/**
 * Tactile UI Sound Engine (tab, toggle, slider sounds), SongPlay UI Style (New v2.5 vs Legacy),
 * Home Live Clock seconds toggle, and manual Patch Notes trigger.
 */
@Composable
fun UiSoundAndStyleSection(
    onShowPatchNotes: () -> Unit,
) {
    val context = LocalContext.current
    val rev by ExperiencePreferences.experienceRevision.collectAsState()
    val playerUiStyle = remember(rev) { ExperiencePreferences.getPlayerUiStyle(context) }
    val uiSoundsEnabled = remember(rev) { ExperiencePreferences.isUiSoundsEnabled(context) }
    val tabSound = remember(rev) { ExperiencePreferences.isTabSoundEnabled(context) }
    val toggleSound = remember(rev) { ExperiencePreferences.isToggleSoundEnabled(context) }
    val sliderSound = remember(rev) { ExperiencePreferences.isSliderSoundEnabled(context) }
    val uiSoundVol = remember(rev) { ExperiencePreferences.getUiSoundVolume(context) }
    val uiSoundStyle = remember(rev) { ExperiencePreferences.getUiSoundStyle(context) }
    val clockShowSeconds = remember(rev) { ExperiencePreferences.isHomeClockSecondsEnabled(context) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A1A22))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── SongPlay UI Selector (New Expressive v2.5 vs Legacy Classic) ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = null,
                tint = AppPalette,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    "SongPlay Interface Style",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Switch anytime between the v2.5 Expressive player and Legacy Classic layout",
                    color = Color(0xFFB3B8C4),
                    fontSize = 11.sp,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PlayerUiStyle.entries.forEach { style ->
                val selected = playerUiStyle == style
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) AppPalette.copy(alpha = 0.16f) else Color(0xFF13141B))
                        .border(
                            1.dp,
                            if (selected) AppPalette else Color.White.copy(alpha = 0.08f),
                            RoundedCornerShape(12.dp),
                        )
                        .clickable {
                            ExperiencePreferences.setPlayerUiStyle(context, style)
                            UiSoundEngine.playToggleSound(context, style == PlayerUiStyle.NEW_EXPRESSIVE)
                        }
                        .testTag("player_ui_style_${style.key}")
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = style.label,
                            color = if (selected) AppPalette else Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = style.description,
                            color = Color(0xFF9EA5B5),
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                        )
                    }
                    Text(
                        text = if (selected) "ACTIVE" else "SELECT",
                        color = if (selected) Color.Black else Color(0xFF9EA5B5),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) AppPalette else Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }

        // ── Home Live Clock Option ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = AppPalette,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        "Home auto-location clock seconds",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Show live ticking seconds on the Home timezone clock card",
                        color = Color(0xFF9EA5B5),
                        fontSize = 11.sp,
                    )
                }
            }
            Switch(
                checked = clockShowSeconds,
                onCheckedChange = {
                    ExperiencePreferences.setHomeClockSecondsEnabled(context, it)
                    UiSoundEngine.playToggleSound(context, it)
                },
                modifier = Modifier.testTag("home_clock_seconds_switch"),
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AppPalette),
            )
        }

        // ── Tactile UI Sound Engine ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.VolumeUp,
                contentDescription = null,
                tint = AppPalette,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Tactile UI Sound Engine",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Synthesizes low-latency tab, switch & slider ticks without interrupting music",
                    color = Color(0xFFB3B8C4),
                    fontSize = 11.sp,
                )
            }
            Switch(
                checked = uiSoundsEnabled,
                onCheckedChange = {
                    ExperiencePreferences.setUiSoundsEnabled(context, it)
                    if (it) UiSoundEngine.playToggleSound(context, true)
                },
                modifier = Modifier.testTag("ui_sounds_master_switch"),
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AppPalette),
            )
        }

        if (uiSoundsEnabled) {
            UiSoundSubSwitch(
                title = "Tab & navigation chimes",
                subtitle = "Soft harmonic chime when switching bottom/side/rail tabs",
                checked = tabSound,
                testTag = "ui_sound_tab_switch",
            ) {
                ExperiencePreferences.setTabSoundEnabled(context, it)
                UiSoundEngine.playTabSound(context)
            }
            UiSoundSubSwitch(
                title = "Toggle & switch feedback",
                subtitle = "Ascending confirm tone when enabling switches, descending when off",
                checked = toggleSound,
                testTag = "ui_sound_toggle_switch",
            ) {
                ExperiencePreferences.setToggleSoundEnabled(context, it)
                UiSoundEngine.playToggleSound(context, it)
            }
            UiSoundSubSwitch(
                title = "Slider scrub micro-ticks",
                subtitle = "Pitch-tracked clicks while scrubbing player, EQ, or volume sliders",
                checked = sliderSound,
                testTag = "ui_sound_slider_switch",
            ) {
                ExperiencePreferences.setSliderSoundEnabled(context, it)
                UiSoundEngine.playToggleSound(context, it)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("UI sound level", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("$uiSoundVol%", color = AppPalette, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = uiSoundVol.toFloat(),
                onValueChange = {
                    val v = it.roundToInt().coerceIn(0, 100)
                    ExperiencePreferences.setUiSoundVolume(context, v)
                    UiSoundEngine.playSliderTick(context, v / 100f)
                },
                valueRange = 0f..100f,
                modifier = Modifier.testTag("ui_sound_volume_slider"),
                colors = SliderDefaults.colors(activeTrackColor = AppPalette, thumbColor = AppPalette),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                UiSoundStyle.entries.forEach { profile ->
                    val selected = uiSoundStyle == profile
                    Box(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (selected) AppPalette else Color.White.copy(alpha = 0.08f))
                            .clickable {
                                ExperiencePreferences.setUiSoundStyle(context, profile)
                                UiSoundEngine.playTapChime(context)
                            }
                            .testTag("ui_sound_style_${profile.key}")
                            .padding(horizontal = 14.dp, vertical = 12.dp),
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
        }

        Button(
            onClick = {
                UiSoundEngine.playTapChime(context)
                onShowPatchNotes()
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF252836),
                contentColor = Color.White,
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("settings_view_patch_notes_button"),
        ) {
            Icon(
                imageVector = Icons.Default.NewReleases,
                contentDescription = null,
                tint = AppPalette,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("View v${BuildConfig.VERSION_NAME.removeSuffix("-debug")} What's New & Patch Notes", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun UiSoundSubSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color(0xFF9EA5B5), fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AppPalette),
        )
    }
}
