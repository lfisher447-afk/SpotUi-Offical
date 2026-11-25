@file:Suppress("TooManyFunctions", "LongMethod", "MagicNumber")

package com.music.spotui.accessibility

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode as AnimRepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SpatialAudio
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.spotui.audio.EqPresetCatalog
import com.music.spotui.data.preferences.SpatialProfile
import com.music.spotui.di.RepeatMode
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
internal fun FloaterPlayerPanel(
    state: FloaterUiModel,
    callbacks: FloaterCallbacks,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    var seeking by remember { mutableStateOf(false) }
    var dragPos by remember { mutableFloatStateOf(0f) }
    val dur = state.durationMs.coerceAtLeast(1L)
    val sliderPos = if (seeking) dragPos else (state.positionMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ExpressivePanelCard(title = "Active Output Route", accentColor = cs.primary) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = cs.primaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Headphones,
                            contentDescription = null,
                            tint = cs.onPrimaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.audioDeviceName,
                        color = cs.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${state.audioRouteLabel} · ${state.sourceLabel} · ${state.qualityLabel}",
                        color = cs.onSurfaceVariant,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        ExpressivePanelCard(title = "Track Scrubber", accentColor = cs.secondary) {
            Slider(
                value = sliderPos,
                onValueChange = {
                    callbacks.onInteraction()
                    seeking = true
                    dragPos = it
                },
                onValueChangeFinished = {
                    seeking = false
                    callbacks.onHaptic(false)
                    callbacks.onSeekTo((dragPos * dur).toLong())
                },
                colors = SliderDefaults.colors(
                    thumbColor = cs.primary,
                    activeTrackColor = cs.primary,
                    inactiveTrackColor = cs.surfaceContainerHighest,
                ),
                modifier = Modifier.fillMaxWidth().testTag("floater_track_scrubber"),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatFloaterDuration(if (seeking) (dragPos * dur).toLong() else state.positionMs),
                    color = cs.primary,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = formatFloaterDuration(state.durationMs),
                    color = cs.onSurfaceVariant,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = { callbacks.onHaptic(false); callbacks.onSeekBy(-10_000L) },
                    shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, topEnd = 10.dp, bottomEnd = 10.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("floater_rew10"),
                ) {
                    Icon(Icons.Rounded.Replay10, contentDescription = "Rewind 10s", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("-10s", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
                FilledTonalButton(
                    onClick = { callbacks.onHaptic(false); callbacks.onSeekBy(10_000L) },
                    shape = RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp, topEnd = 20.dp, bottomEnd = 20.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("floater_fwd10"),
                ) {
                    Icon(Icons.Rounded.Forward10, contentDescription = "Forward 10s", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("+10s", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        ExpressivePanelCard(title = "Queue Modes", accentColor = cs.tertiary) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.isShuffled,
                    onClick = { callbacks.onHaptic(true); callbacks.onToggleShuffle() },
                    leadingIcon = { Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(15.dp)) },
                    label = { Text(if (state.isShuffled) "Shuffle On" else "Shuffle", fontSize = 11.5.sp) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                )
                FilterChip(
                    selected = state.repeatMode != RepeatMode.OFF,
                    onClick = { callbacks.onHaptic(true); callbacks.onCycleRepeat() },
                    leadingIcon = {
                        Icon(
                            if (state.repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                        )
                    },
                    label = {
                        Text(
                            when (state.repeatMode) {
                                RepeatMode.OFF -> "Loop Off"
                                RepeatMode.ALL -> "Loop All"
                                RepeatMode.ONE -> "Loop One"
                            },
                            fontSize = 11.5.sp,
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                )
            }
        }
    }
}

@Composable
internal fun FloaterQueuePanel(
    state: FloaterUiModel,
    callbacks: FloaterCallbacks,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Up Next (${state.queue.size})",
                color = cs.onSurface,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(
                onClick = { callbacks.onHaptic(false); callbacks.onShuffleUpcomingQueue() },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.heightIn(min = 48.dp).testTag("floater_queue_shuffle"),
            ) {
                Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle upcoming", modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Mix", fontSize = 11.sp)
            }
            FilledTonalButton(
                onClick = { callbacks.onHaptic(false); callbacks.onDeduplicateQueue() },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.heightIn(min = 48.dp).testTag("floater_queue_dedupe"),
            ) {
                Icon(Icons.Rounded.ClearAll, contentDescription = "Remove duplicates", modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Dedupe", fontSize = 11.sp)
            }
        }

        if (state.queue.isEmpty()) {
            Surface(
                color = cs.surfaceContainerHigh,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = cs.primary)
                    Text("Queue is empty", color = cs.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("Start any track in SpotUI to inspect & jump the queue here.", color = cs.onSurfaceVariant, fontSize = 11.sp)
                }
            }
        } else {
            val preview = state.queue.take(30)
            preview.forEachIndexed { idx, song ->
                val isCurrent = idx == state.activeQueueIndex
                Surface(
                    onClick = { callbacks.onHaptic(true); callbacks.onPlayQueueIndex(idx) },
                    color = if (isCurrent) cs.primaryContainer else cs.surfaceContainerHigh,
                    shape = RoundedCornerShape(16.dp),
                    border = if (isCurrent) BorderStroke(1.2.dp, cs.primary.copy(alpha = 0.65f)) else null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("floater_queue_item_$idx"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = if (isCurrent) Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = if (isCurrent) cs.onPrimaryContainer else cs.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title.ifBlank { "Unknown Track" },
                                color = if (isCurrent) cs.onPrimaryContainer else cs.onSurface,
                                fontSize = 12.sp,
                                fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = song.singer.ifBlank { "Artist" },
                                color = if (isCurrent) cs.onPrimaryContainer.copy(alpha = 0.78f) else cs.onSurfaceVariant,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun FloaterEqPanel(
    state: FloaterUiModel,
    callbacks: FloaterCallbacks,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val bandValues = listOf(
        state.bandSubBass,
        state.bandBass,
        state.bandLowMid,
        state.bandVocal,
        state.bandHighMid,
        state.bandTreble,
        state.bandBrilliance,
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ExpressiveToggleCard(
            title = "7-Band Studio Equalizer",
            subtitle = "Preset: ${state.eqPresetName}",
            checked = state.eqEnabled,
            onCheckedChange = { callbacks.onHaptic(true); callbacks.onToggleEq(it) },
            testTag = "floater_eq_master_switch",
        )

        // Interactive Live 7-Band Frequency Curve Visualizer
        ExpressivePanelCard(title = "Frequency Response Curve", accentColor = cs.primary) {
            EqResponseCurveCanvas(
                bands = bandValues,
                enabled = state.eqEnabled,
                primary = cs.primary,
                tertiary = cs.tertiary,
                gridColor = cs.outlineVariant.copy(alpha = 0.4f),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            EqPresetCatalog.presets.forEach { curve ->
                val selected = state.eqPresetName.equals(curve.name, ignoreCase = true)
                FilterChip(
                    selected = selected,
                    onClick = { callbacks.onHaptic(true); callbacks.onSelectEqPreset(curve.name) },
                    label = { Text(curve.name, fontSize = 11.sp) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }

        ExpressivePanelCard(title = "Parametric Bands", accentColor = cs.primary) {
            val bands = listOf(
                Triple("32Hz Sub", 0, state.bandSubBass),
                Triple("64Hz Bass", 1, state.bandBass),
                Triple("250Hz Warmth", 2, state.bandLowMid),
                Triple("1kHz Vocal", 3, state.bandVocal),
                Triple("4kHz Presence", 4, state.bandHighMid),
                Triple("10kHz Treble", 5, state.bandTreble),
                Triple("16kHz Brilliance", 6, state.bandBrilliance),
            )
            bands.forEach { (label, index, value) ->
                val dbText = formatEqSliderDb(value)
                ExpressiveSliderRow(
                    label = label,
                    valueText = dbText,
                    value = value / 100f,
                    onValueChange = { callbacks.onInteraction(); callbacks.onEqBandChange(index, it * 100f) },
                )
            }
        }

        ExpressivePanelCard(title = "Dynamics & Preamp", accentColor = cs.tertiary) {
            ExpressiveSliderRow(
                label = "Bass Boost",
                valueText = "${(state.bassBoostStrength / 10f).roundToInt()}%",
                value = state.bassBoostStrength / 1000f,
                onValueChange = { callbacks.onInteraction(); callbacks.onBassBoostChange((it * 1000f).roundToInt()) },
            )
            ExpressiveSliderRow(
                label = "Preamp Gain",
                valueText = String.format(Locale.US, "%+.1f dB", state.preampDb),
                value = ((state.preampDb + 12f) / 18f).coerceIn(0f, 1f),
                onValueChange = { callbacks.onInteraction(); callbacks.onPreampChange(-12f + it * 18f) },
            )
            ExpressiveToggleCard(
                title = "Volume Normalizer",
                subtitle = "Steady loudness across tracks",
                checked = state.normalizerEnabled,
                onCheckedChange = { callbacks.onHaptic(false); callbacks.onToggleNormalizer(it) },
            )
            ExpressiveToggleCard(
                title = "Soft Clip Limiter",
                subtitle = "Protects headroom on heavy bass boosts",
                checked = state.limiterEnabled,
                onCheckedChange = { callbacks.onHaptic(false); callbacks.onToggleLimiter(it) },
            )
            OutlinedButton(
                onClick = { callbacks.onHaptic(true); callbacks.onResetEqFlat() },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("floater_eq_reset_flat"),
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Reset EQ Flat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun FloaterSpatial3dPanel(
    state: FloaterUiModel,
    callbacks: FloaterCallbacks,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ExpressiveToggleCard(
            title = "3D Spatial Audio Stage",
            subtitle = "Active on ${state.audioRouteLabel}",
            checked = state.spatialEnabled,
            onCheckedChange = { callbacks.onHaptic(true); callbacks.onToggleSpatial(it) },
            testTag = "floater_spatial_master_switch",
        )

        // Interactive 3D Binaural Soundstage Radar
        ExpressivePanelCard(title = "Binaural Soundstage Radar", accentColor = cs.secondary) {
            SpatialSoundstageRadarCanvas(
                enabled = state.spatialEnabled,
                orbit8dEnabled = state.orbit8dEnabled,
                orbitSpeedHz = state.orbit8dSpeedHz,
                strengthFraction = (state.spatialStrength / 1000f).coerceIn(0f, 1f),
                primary = cs.primary,
                tertiary = cs.tertiary,
                gridColor = cs.outlineVariant.copy(alpha = 0.45f),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SpatialProfile.entries.forEach { profile ->
                val selected = state.spatialProfile == profile
                FilterChip(
                    selected = selected,
                    onClick = { callbacks.onHaptic(true); callbacks.onSelectSpatialProfile(profile) },
                    leadingIcon = { Icon(Icons.Rounded.SpatialAudio, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    label = { Text(profile.label, fontSize = 11.sp) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }

        ExpressivePanelCard(title = "Stage Geometry", accentColor = cs.primary) {
            ExpressiveSliderRow(
                label = "3D Width / Depth",
                valueText = "${(state.spatialStrength / 10f).roundToInt()}%",
                value = state.spatialStrength / 1000f,
                onValueChange = { callbacks.onInteraction(); callbacks.onSpatialStrengthChange((it * 1000f).roundToInt()) },
            )
            ExpressiveSliderRow(
                label = "Room Reflections",
                valueText = "${state.spatialRoomDelayMs.roundToInt()} ms",
                value = ((state.spatialRoomDelayMs - 2f) / 26f).coerceIn(0f, 1f),
                onValueChange = { callbacks.onInteraction(); callbacks.onSpatialDelayChange(2f + it * 26f) },
            )
            ExpressiveSliderRow(
                label = "Elevation / Height",
                valueText = "${(state.spatialElevationDepth * 100f).roundToInt()}%",
                value = state.spatialElevationDepth.coerceIn(0f, 1f),
                onValueChange = { callbacks.onInteraction(); callbacks.onSpatialElevationChange(it) },
            )
        }

        ExpressivePanelCard(title = "8D Orbit & Headphone Safe", accentColor = cs.secondary) {
            ExpressiveToggleCard(
                title = "8D Circular Auto-Orbit",
                subtitle = "Slow binaural azimuth rotation",
                checked = state.orbit8dEnabled,
                onCheckedChange = { callbacks.onHaptic(true); callbacks.onToggle8dOrbit(it) },
                testTag = "floater_8d_switch",
            )
            if (state.orbit8dEnabled) {
                ExpressiveSliderRow(
                    label = "Orbit Rotation Rate",
                    valueText = String.format(Locale.US, "%.2f Hz", state.orbit8dSpeedHz),
                    value = ((state.orbit8dSpeedHz - 0.10f) / 1.40f).coerceIn(0f, 1f),
                    onValueChange = { callbacks.onInteraction(); callbacks.onOrbitSpeedChange(0.10f + it * 1.40f) },
                )
            }
            ExpressiveToggleCard(
                title = "Headphone Crossfeed",
                subtitle = "Natural speaker blend for earphones",
                checked = state.crossfeedEnabled,
                onCheckedChange = { callbacks.onHaptic(false); callbacks.onToggleCrossfeed(it) },
            )
            ExpressiveToggleCard(
                title = "Mono Correlation Guard",
                subtitle = "Prevents vocal phase hollowing",
                checked = state.correlationGuardEnabled,
                onCheckedChange = { callbacks.onHaptic(false); callbacks.onToggleCorrelationGuard(it) },
            )
        }
    }
}

@Composable
internal fun FloaterToolsPanel(
    state: FloaterUiModel,
    callbacks: FloaterCallbacks,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ExpressivePanelCard(title = "Tempo & Pitch", accentColor = cs.primary) {
            ExpressiveSliderRow(
                label = "Playback Speed",
                valueText = String.format(Locale.US, "%.2fx", state.speed),
                value = ((state.speed - 0.5f) / 1.5f).coerceIn(0f, 1f),
                onValueChange = { callbacks.onInteraction(); callbacks.onSpeedChange(0.5f + it * 1.5f) },
            )
            ExpressiveSliderRow(
                label = "Vocal Pitch",
                valueText = String.format(Locale.US, "%.2fx", state.pitch),
                value = ((state.pitch - 0.75f) / 0.50f).coerceIn(0f, 1f),
                onValueChange = { callbacks.onInteraction(); callbacks.onPitchChange(0.75f + it * 0.50f) },
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(
                    onClick = { callbacks.onHaptic(false); callbacks.onResetSpeedPitch() },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Text("Reset 1.0x", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                FilterChip(
                    selected = state.skipSilence,
                    onClick = { callbacks.onHaptic(false); callbacks.onToggleSkipSilence(!state.skipSilence) },
                    label = { Text("Skip Silence", fontSize = 11.sp) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                )
            }
        }

        ExpressivePanelCard(title = "Sleep Timer & A-B Loop", accentColor = cs.secondary) {
            val sleepLabel = if (state.sleepTimerRemainingMs > 0L) {
                "Remaining: ${formatFloaterDuration(state.sleepTimerRemainingMs)}"
            } else {
                "Timer off"
            }
            Text(sleepLabel, color = cs.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(0 to "Off", 10 to "10m", 15 to "15m", 30 to "30m", 60 to "60m").forEach { (mins, label) ->
                    FilledTonalButton(
                        onClick = { callbacks.onHaptic(true); callbacks.onSetSleepTimer(mins) },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                        Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
            val abLabel = when {
                state.abLoopStartMs >= 0L && state.abLoopEndMs > state.abLoopStartMs ->
                    "Looping ${formatFloaterDuration(state.abLoopStartMs)} → ${formatFloaterDuration(state.abLoopEndMs)}"
                state.abLoopStartMs >= 0L -> "Point A: ${formatFloaterDuration(state.abLoopStartMs)} (tap Set B)"
                else -> "A-B section loop inactive"
            }
            Text(abLabel, color = cs.onSurfaceVariant, fontSize = 11.sp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(
                    onClick = { callbacks.onHaptic(false); callbacks.onSetAbStart() },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text("Set A", fontSize = 11.sp) }
                FilledTonalButton(
                    onClick = { callbacks.onHaptic(false); callbacks.onSetAbEnd() },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text("Set B", fontSize = 11.sp) }
                OutlinedButton(
                    onClick = { callbacks.onHaptic(false); callbacks.onClearAbLoop() },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text("Clear", fontSize = 11.sp) }
            }
        }

        ExpressivePanelCard(title = "Overlay Preferences", accentColor = cs.tertiary) {
            ExpressiveToggleCard(
                title = "Tactile Haptics",
                subtitle = "Vibrates on transport, corner resize & toggles",
                checked = state.hapticsEnabled,
                onCheckedChange = { callbacks.onToggleHaptics(it) },
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val hideLabel = if (state.autoHideMs == 0L) "Auto-Hide: Stay" else "Auto-Hide: ${state.autoHideMs / 1000L}s"
                FilledTonalButton(
                    onClick = { callbacks.onHaptic(false); callbacks.onCycleAutoHide() },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Rounded.Timer, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(hideLabel, fontSize = 10.5.sp, maxLines = 1)
                }
                OutlinedButton(
                    onClick = { callbacks.onHaptic(true); callbacks.onResetGeometry() },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Text("Reset Size", fontSize = 10.5.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
internal fun FloaterGameTranslatePanel(
    state: FloaterUiModel,
    translationBubble: TranslationBubbleState,
    history: List<TranslationHistoryEntry>,
    hasMicPermission: Boolean,
    isListening: Boolean,
    sourceLangCode: String,
    bubbleEnabled: Boolean,
    bubbleWidthDp: Float,
    bubbleTextSizeSp: Float,
    bubbleOpacity: Float,
    callbacks: FloaterCallbacks,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    var manualDraft by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            color = cs.tertiaryContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    color = cs.tertiary,
                    shape = CircleShape,
                    modifier = Modifier.size(34.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (translationBubble.isTranslating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = cs.onTertiary,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.SportsEsports,
                                contentDescription = null,
                                tint = cs.onTertiary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.activeGameLabel ?: "Game Detected",
                        color = cs.onTertiaryContainer,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = translationBubble.statusHint,
                        color = cs.onTertiaryContainer.copy(alpha = 0.85f),
                        fontSize = 10.5.sp,
                        maxLines = 2,
                    )
                }
            }
        }

        if (!hasMicPermission) {
            Surface(
                color = cs.errorContainer,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Microphone Access Needed",
                        color = cs.onErrorContainer,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Allow RECORD_AUDIO so SpotUI can hear teammates speaking Spanish, Arabic, etc. and translate into English live.",
                        color = cs.onErrorContainer,
                        fontSize = 10.5.sp,
                    )
                    Button(
                        onClick = { callbacks.onHaptic(true); callbacks.onRequestMicPermission() },
                        colors = ButtonDefaults.buttonColors(containerColor = cs.error, contentColor = cs.onError),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("floater_grant_mic_button"),
                    ) {
                        Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Grant Mic Permission", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            val micBg by animateColorAsState(
                targetValue = if (isListening) cs.error else cs.primary,
                label = "micBtnBg",
            )
            val micFg by animateColorAsState(
                targetValue = if (isListening) cs.onError else cs.onPrimary,
                label = "micBtnFg",
            )
            Button(
                onClick = { callbacks.onHaptic(true); callbacks.onToggleListening() },
                colors = ButtonDefaults.buttonColors(containerColor = micBg, contentColor = micFg),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("floater_translate_mic_button"),
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isListening) "Stop Live Voice Translation" else "Start Live Voice Translation",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }

        ExpressivePanelCard(title = "Source Language (Auto-Detect → English)", accentColor = cs.primary) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GameTranslatorEngine.supportedLanguages.forEach { option ->
                    val selected = sourceLangCode == option.code
                    FilterChip(
                        selected = selected,
                        onClick = { callbacks.onHaptic(false); callbacks.onSelectTranslateSource(option.code) },
                        label = { Text("${option.shortLabel} · ${option.fullName}", fontSize = 11.sp) },
                        shape = RoundedCornerShape(16.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = cs.primary,
                            selectedLabelColor = cs.onPrimary,
                        ),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("floater_lang_${option.code}"),
                    )
                }
            }
        }

        ExpressivePanelCard(title = "Top-Left Bubble Size & Width", accentColor = cs.secondary) {
            ExpressiveToggleCard(
                title = "Show Top-Left Bubble",
                subtitle = "Floating live English caption over game",
                checked = bubbleEnabled,
                onCheckedChange = { callbacks.onHaptic(true); callbacks.onToggleBubbleVisible(it) },
                testTag = "floater_bubble_toggle",
            )
            ExpressiveSliderRow(
                label = "Bubble Width",
                valueText = "${bubbleWidthDp.roundToInt()} dp",
                value = ((bubbleWidthDp - 160f) / (420f - 160f)).coerceIn(0f, 1f),
                onValueChange = { callbacks.onInteraction(); callbacks.onBubbleWidthChange(160f + it * (420f - 160f)) },
            )
            ExpressiveSliderRow(
                label = "Bubble Text Size",
                valueText = "${bubbleTextSizeSp.roundToInt()} sp",
                value = ((bubbleTextSizeSp - 11f) / (24f - 11f)).coerceIn(0f, 1f),
                onValueChange = { callbacks.onInteraction(); callbacks.onBubbleTextSizeChange(11f + it * (24f - 11f)) },
            )
            ExpressiveSliderRow(
                label = "Bubble Opacity",
                valueText = "${(bubbleOpacity * 100f).roundToInt()}%",
                value = ((bubbleOpacity - 0.55f) / (0.98f - 0.55f)).coerceIn(0f, 1f),
                onValueChange = { callbacks.onInteraction(); callbacks.onBubbleOpacityChange(0.55f + it * (0.98f - 0.55f)) },
            )
            OutlinedButton(
                onClick = { callbacks.onHaptic(true); callbacks.onResetBubblePosition() },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("floater_bubble_snap_top_left"),
            ) {
                Icon(Icons.Rounded.NorthWest, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Snap Bubble to Top-Left", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
        }

        ExpressivePanelCard(title = "Quick Teammate Text → EN", accentColor = cs.tertiary) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedTextField(
                    value = manualDraft,
                    onValueChange = { callbacks.onInteraction(); manualDraft = it },
                    placeholder = { Text("Paste chat (Hola, يلا...)", fontSize = 11.sp) },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 12.sp),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = cs.primary,
                        unfocusedBorderColor = cs.outlineVariant,
                    ),
                    modifier = Modifier.weight(1f).testTag("floater_translate_input"),
                )
                IconButton(
                    onClick = {
                        if (manualDraft.isNotBlank()) {
                            callbacks.onHaptic(true)
                            callbacks.onTranslateManualText(manualDraft)
                            manualDraft = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(cs.primary)
                        .testTag("floater_translate_send_button"),
                ) {
                    Icon(Icons.Rounded.Send, contentDescription = "Translate to English", tint = cs.onPrimary, modifier = Modifier.size(18.dp))
                }
            }
        }

        if (history.isNotEmpty()) {
            ExpressivePanelCard(title = "Recent Translations", accentColor = cs.primary) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(
                        onClick = { callbacks.onHaptic(false); callbacks.onClearTranslationHistory() },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Clear", fontSize = 11.sp)
                    }
                }
                history.take(6).forEach { item ->
                    Surface(
                        color = cs.surfaceContainerHighest,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "[${item.detectedLangCode.uppercase(Locale.US)} → EN] ${item.translatedEnglish}",
                                color = cs.onSurface,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = item.originalText,
                                color = cs.onSurfaceVariant,
                                fontSize = 10.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EqResponseCurveCanvas(
    bands: List<Float>,
    enabled: Boolean,
    primary: Color,
    tertiary: Color,
    gridColor: Color,
) {
    val activeColor = if (enabled) primary else gridColor
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(74.dp)
            .clip(RoundedCornerShape(14.dp)),
    ) {
        val w = size.width
        val h = size.height
        val padX = 12f
        val padY = 12f
        val plotW = (w - padX * 2f).coerceAtLeast(1f)
        val plotH = (h - padY * 2f).coerceAtLeast(1f)
        val midY = padY + plotH * 0.5f

        // 0dB Center Dashed Guide
        drawLine(
            color = gridColor,
            start = Offset(padX, midY),
            end = Offset(w - padX, midY),
            strokeWidth = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
        )

        val points = bands.mapIndexed { index, raw ->
            val frac = index.toFloat() / (bands.size - 1).coerceAtLeast(1)
            val normY = if (enabled) (raw / 100f).coerceIn(0f, 1f) else 0.5f
            Offset(
                x = padX + frac * plotW,
                y = padY + (1f - normY) * plotH,
            )
        }

        if (points.size >= 2) {
            val curvePath = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 0 until points.lastIndex) {
                    val p0 = points[i]
                    val p1 = points[i + 1]
                    val cx = (p0.x + p1.x) * 0.5f
                    cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                }
            }
            val fillPath = Path().apply {
                addPath(curvePath)
                lineTo(points.last().x, h)
                lineTo(points.first().x, h)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(activeColor.copy(alpha = 0.34f), Color.Transparent),
                ),
            )
            drawPath(
                path = curvePath,
                brush = Brush.horizontalGradient(listOf(activeColor, if (enabled) tertiary else gridColor)),
                style = Stroke(width = 4.5f, cap = StrokeCap.Round),
            )
        }

        points.forEach { pt ->
            drawCircle(color = activeColor.copy(alpha = 0.32f), radius = 8f, center = pt)
            drawCircle(color = activeColor, radius = 4.5f, center = pt)
        }
    }
}

@Composable
private fun SpatialSoundstageRadarCanvas(
    enabled: Boolean,
    orbit8dEnabled: Boolean,
    orbitSpeedHz: Float,
    strengthFraction: Float,
    primary: Color,
    tertiary: Color,
    gridColor: Color,
) {
    val transition = rememberInfiniteTransition(label = "spatialOrbit")
    val durationMs = (1000f / orbitSpeedHz.coerceIn(0.10f, 1.50f)).roundToInt().coerceIn(650, 10_000)
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMs, easing = LinearEasing),
            repeatMode = AnimRepeatMode.Restart,
        ),
        label = "orbitPhase",
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp),
    ) {
        val center = Offset(size.width * 0.5f, size.height * 0.5f)
        val maxR = (size.minDimension * 0.44f).coerceAtLeast(16f)
        val stageColor = if (enabled) primary else gridColor

        // Concentric Soundstage Rings
        listOf(0.4f, 0.72f, 1.0f).forEach { factor ->
            drawCircle(
                color = gridColor,
                radius = maxR * factor,
                center = center,
                style = Stroke(width = 1.5f),
            )
        }

        // Expanding Depth Aura
        val spreadR = maxR * (0.28f + 0.70f * strengthFraction)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(stageColor.copy(alpha = if (enabled) 0.28f else 0.08f), Color.Transparent),
                center = center,
                radius = spreadR,
            ),
            radius = spreadR,
            center = center,
        )

        // Listener Center Anchor
        drawCircle(color = stageColor, radius = 6f, center = center)

        // 8D Orbiting Sound Source Orb
        val angle = if (enabled && orbit8dEnabled) phase else (-PI / 3.0).toFloat()
        val orbDist = spreadR * 0.85f
        val orbCenter = Offset(
            x = center.x + cos(angle) * orbDist,
            y = center.y + sin(angle) * orbDist,
        )
        drawLine(
            color = (if (enabled) tertiary else gridColor).copy(alpha = 0.5f),
            start = center,
            end = orbCenter,
            strokeWidth = 2f,
        )
        drawCircle(
            color = (if (enabled) tertiary else gridColor).copy(alpha = 0.30f),
            radius = 11f,
            center = orbCenter,
        )
        drawCircle(
            color = if (enabled) tertiary else gridColor,
            radius = 5.5f,
            center = orbCenter,
        )
    }
}

@Composable
private fun ExpressivePanelCard(
    title: String,
    accentColor: Color,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = cs.surfaceContainerHigh,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.38f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accentColor),
                )
                Text(
                    text = title,
                    color = cs.onSurface,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            content()
        }
    }
}

@Composable
private fun ExpressiveToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String? = null,
) {
    val cs = MaterialTheme.colorScheme
    val cardBg by animateColorAsState(
        targetValue = if (checked) cs.primaryContainer.copy(alpha = 0.52f) else cs.surfaceContainer,
        label = "toggleCardBg",
    )
    Surface(
        color = cardBg,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = cs.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = cs.onSurfaceVariant, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = cs.onPrimary,
                    checkedTrackColor = cs.primary,
                ),
                modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier,
            )
        }
    }
}

@Composable
private fun ExpressiveSliderRow(
    label: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = cs.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Text(valueText, color = cs.primary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        }
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = onValueChange,
            colors = SliderDefaults.colors(
                thumbColor = cs.primary,
                activeTrackColor = cs.primary,
                inactiveTrackColor = cs.surfaceContainerLowest,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun formatEqSliderDb(sliderVal: Float): String {
    val db = ((sliderVal - 50f) / 50f) * 12f
    return String.format(Locale.US, "%+.1fdB", db)
}
