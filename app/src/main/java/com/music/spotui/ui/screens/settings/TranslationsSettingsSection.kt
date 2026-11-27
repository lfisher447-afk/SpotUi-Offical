package com.music.spotui.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.spotui.accessibility.GameTranslatorEngine
import com.music.spotui.translator.GameVoiceTranslatorEngine
import com.music.spotui.translator.MultiEngineTranslationRouter
import com.music.spotui.translator.TranslationEngineBackend
import com.music.spotui.translator.TranslationEnginePreferences
import com.music.spotui.translator.TranslatorModelManager
import com.music.spotui.translator.VoiceCaptureSource
import com.music.spotui.ui.theme.AppPalette
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TranslationsSettingsSection(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        TranslationEnginePreferences.hydrateIfNeeded(context)
        GameTranslatorEngine.hydratePreferences(context)
    }

    val config by TranslationEnginePreferences.state.collectAsStateWithLifecycle()
    val captionState by GameVoiceTranslatorEngine.captionState.collectAsStateWithLifecycle()
    val engineStats by MultiEngineTranslationRouter.engineStats.collectAsStateWithLifecycle()

    val sourceLang by GameTranslatorEngine.sourceLangFlow.collectAsStateWithLifecycle()
    val targetLang by GameTranslatorEngine.targetLangFlow.collectAsStateWithLifecycle()
    val downloadedPacks by GameTranslatorEngine.downloadedModelsFlow.collectAsStateWithLifecycle()
    val busyPack by GameTranslatorEngine.modelDownloadBusyFlow.collectAsStateWithLifecycle()
    val alwaysUnlocked by GameTranslatorEngine.alwaysUnlockTranslatorFlow.collectAsStateWithLifecycle()
    val continuousListen by GameTranslatorEngine.continuousListenFlow.collectAsStateWithLifecycle()
    val bubbleEnabled by GameTranslatorEngine.bubbleEnabledFlow.collectAsStateWithLifecycle()
    val bubbleWidthDp by GameTranslatorEngine.bubbleWidthDpFlow.collectAsStateWithLifecycle()
    val bubbleTextSp by GameTranslatorEngine.bubbleTextSizeSpFlow.collectAsStateWithLifecycle()
    val bubbleOpacity by GameTranslatorEngine.bubbleOpacityFlow.collectAsStateWithLifecycle()
    val legacyBubble by GameTranslatorEngine.bubbleStateFlow.collectAsStateWithLifecycle()

    var hasAudioPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val audioPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasAudioPerm = granted
        GameTranslatorEngine.refreshMicPermission(context)
        if (granted) {
            Toast.makeText(context, "Audio capture API permission granted", Toast.LENGTH_SHORT).show()
        }
    }

    var voskStorage by remember { mutableStateOf(TranslatorModelManager.inspectVoskStorage(context)) }
    var voskDownloading by remember { mutableStateOf(false) }
    var voskProgress by remember { mutableIntStateOf(0) }
    var voskStatusMsg by remember { mutableStateOf("") }
    var customVoskUrl by remember(config.customVoskUrl) { mutableStateOf(config.customVoskUrl) }
    var lingvaMirror by remember(config.lingvaMirrorUrl) { mutableStateOf(config.lingvaMirrorUrl) }
    var libreUrl by remember(config.libreTranslateUrl) { mutableStateOf(config.libreTranslateUrl) }
    var libreKey by remember(config.libreApiKey) { mutableStateOf(config.libreApiKey) }
    var testPhrase by remember { mutableStateOf("Necesito munición, enemigo a la izquierda") }
    var testOutput by remember { mutableStateOf("") }
    var testBusy by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 1. Live Internal Game Audio Capture Card (Zero Mic Locking)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1A202C),
            border = BorderStroke(
                1.dp,
                if (captionState.captureActive) Color(0xFF1DB954).copy(alpha = 0.7f) else AppPalette.copy(alpha = 0.35f),
            ),
            modifier = Modifier.fillMaxWidth().testTag("translations_capture_card"),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (captionState.captureActive) Color(0xFF1DB954).copy(alpha = 0.2f) else AppPalette.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SportsEsports,
                            contentDescription = "Internal game audio translator",
                            tint = if (captionState.captureActive) Color(0xFF66EE99) else AppPalette,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Internal Game Audio Translator",
                            color = Color.White,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text = captionState.statusMessage,
                            color = Color(0xFFB7C0D1),
                            fontSize = 11.5.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Surface(
                        color = if (captionState.captureActive) Color(0xFF143822) else Color(0xFF282D3B),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            text = if (captionState.captureActive) "CAPTURING" else "IDLE",
                            color = if (captionState.captureActive) Color(0xFF66EE99) else Color(0xFFB7C0D1),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }

                if (captionState.captureActive) {
                    LinearProgressIndicator(
                        progress = { captionState.rmsLevel.coerceIn(0.05f, 1f) },
                        color = Color(0xFF1DB954),
                        trackColor = Color(0xFF10131A),
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    )
                }

                if (!hasAudioPerm) {
                    Button(
                        onClick = { audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        colors = ButtonDefaults.buttonColors(containerColor = AppPalette, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("translations_grant_perm_btn"),
                    ) {
                        Icon(Icons.Rounded.Mic, contentDescription = "Grant audio permission", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Grant Audio API Permission (Required for Game Capture)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            if (captionState.captureActive) {
                                GameVoiceTranslatorEngine.stopCapture(context)
                            } else {
                                if (!hasAudioPerm) {
                                    audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    GameVoiceTranslatorEngine.requestInternalCapture(context)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (captionState.captureActive) Color(0xFFE53935) else Color(0xFF1DB954),
                            contentColor = if (captionState.captureActive) Color.White else Color.Black,
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("translations_toggle_capture_btn"),
                    ) {
                        Icon(
                            imageVector = if (captionState.captureActive) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                            contentDescription = if (captionState.captureActive) "Stop internal capture" else "Start internal capture",
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (captionState.captureActive) "Stop Game Capture" else "Capture Team Game Audio",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }

                Text("Capture Source Routing", color = Color(0xFFD0D6E0), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    VoiceCaptureSource.entries.forEach { src ->
                        val selected = config.captureSource == src
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) AppPalette.copy(alpha = 0.16f) else Color(0xFF141822),
                            border = BorderStroke(1.dp, if (selected) AppPalette else Color(0xFF2B3142)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable { TranslationEnginePreferences.setCaptureSource(context, src) }
                                .testTag("capture_source_${src.id}"),
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(
                                    text = src.label,
                                    color = if (selected) AppPalette else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = src.subtitle,
                                    color = Color(0xFF9EA8BC),
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Offline Vosk Speech Model Downloader
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1A1A22),
            border = BorderStroke(1.dp, Color(0xFF2D3140)),
            modifier = Modifier.fillMaxWidth().testTag("translations_vosk_card"),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.GraphicEq, contentDescription = "Vosk offline ASR", tint = AppPalette, modifier = Modifier.size(20.dp))
                        Column {
                            Text("Offline Vosk Speech Engine", color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (voskStorage.isReady) {
                                    "Installed: ${voskStorage.activeModelKey.ifBlank { "custom" }} (${String.format(Locale.US, "%.1f", voskStorage.sizeMb)} MB)"
                                } else {
                                    "Not installed yet · Download a lightweight voice model (~35–80 MB)"
                                },
                                color = if (voskStorage.isReady) Color(0xFF81C784) else Color(0xFFFFB74D),
                                fontSize = 11.sp,
                            )
                        }
                    }
                    if (voskStorage.isReady) {
                        IconButton(
                            onClick = {
                                TranslatorModelManager.deleteVoskModel(context)
                                voskStorage = TranslatorModelManager.inspectVoskStorage(context)
                                voskStatusMsg = "Vosk model removed from app storage."
                            },
                            modifier = Modifier.size(48.dp).testTag("vosk_delete_btn"),
                        ) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete Vosk model", tint = Color(0xFFEF9A9A))
                        }
                    }
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TranslatorModelManager.curatedVoskModels.forEach { preset ->
                        val selected = config.voskModelKey == preset.key
                        val installed = voskStorage.isReady && voskStorage.activeModelKey == preset.key
                        FilterChip(
                            selected = selected,
                            enabled = !voskDownloading,
                            onClick = {
                                TranslationEnginePreferences.setVoskModelKey(context, preset.key)
                            },
                            label = {
                                Text(
                                    text = if (installed) "${preset.title} ✓" else preset.title,
                                    fontSize = 11.sp,
                                )
                            },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("vosk_preset_${preset.key}"),
                        )
                    }
                }

                OutlinedTextField(
                    value = customVoskUrl,
                    onValueChange = {
                        customVoskUrl = it
                        TranslationEnginePreferences.setCustomVoskUrl(context, it)
                    },
                    singleLine = true,
                    label = { Text("Custom Vosk .zip URL (Optional override)", fontSize = 11.sp) },
                    placeholder = { Text("https://alphacephei.com/vosk/models/...", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth().testTag("vosk_custom_url_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppPalette,
                        unfocusedBorderColor = Color(0xFF34343F),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                    ),
                )

                val activePreset = TranslatorModelManager.presetForKeyOrLanguage(config.voskModelKey)
                val resolvedUrl = customVoskUrl.trim().ifBlank { activePreset.downloadUrl }
                val resolvedKey = if (customVoskUrl.trim().isNotBlank()) "custom" else activePreset.key

                if (voskDownloading) {
                    LinearProgressIndicator(
                        progress = { (voskProgress / 100f).coerceIn(0.02f, 1f) },
                        color = AppPalette,
                        trackColor = Color(0xFF10131A),
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    )
                    Text("Downloading & extracting Vosk model… $voskProgress%", color = Color(0xFFD0D6E0), fontSize = 11.sp)
                }

                Button(
                    enabled = !voskDownloading,
                    onClick = {
                        voskDownloading = true
                        voskProgress = 1
                        voskStatusMsg = "Connecting to Vosk repository…"
                        scope.launch {
                            val res = TranslatorModelManager.downloadAndExtractVoskModel(
                                context = context,
                                modelUrl = resolvedUrl,
                                modelKey = resolvedKey,
                                onProgress = { pct -> voskProgress = pct },
                            )
                            voskDownloading = false
                            voskStorage = TranslatorModelManager.inspectVoskStorage(context)
                            voskStatusMsg = res.fold(
                                onSuccess = { "Vosk model installed (${String.format(Locale.US, "%.1f", voskStorage.sizeMb)} MB)" },
                                onFailure = { err -> "Download failed: ${err.message ?: "unknown"}" },
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppPalette, contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("vosk_download_btn"),
                ) {
                    Icon(Icons.Rounded.CloudDownload, contentDescription = "Download Vosk model", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (voskStorage.isReady) "Re-Download / Switch Vosk Model" else "Download Selected Vosk Model",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                    )
                }
                if (voskStatusMsg.isNotBlank()) {
                    Text(voskStatusMsg, color = Color(0xFFB7C0D1), fontSize = 11.sp)
                }
            }
        }

        // 3. 7-Backend Translation Library Selector & Mirror Configuration
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1A1A22),
            border = BorderStroke(1.dp, Color(0xFF2D3140)),
            modifier = Modifier.fillMaxWidth().testTag("translations_backends_card"),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = "Translation engines", tint = AppPalette, modifier = Modifier.size(20.dp))
                    Column {
                        Text("Translation Engine Libraries (7 Backends)", color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Choose Smart Cascade or pin a specific offline/online translation library",
                            color = Color(0xFF9EA8BC),
                            fontSize = 11.sp,
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TranslationEngineBackend.entries.forEach { backend ->
                        val selected = config.engineBackend == backend
                        val stat = engineStats[backend]
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) AppPalette.copy(alpha = 0.16f) else Color(0xFF13151C),
                            border = BorderStroke(1.dp, if (selected) AppPalette else Color(0xFF2A2D38)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable { TranslationEnginePreferences.setEngineBackend(context, backend) }
                                .testTag("translation_backend_${backend.id}"),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = backend.label,
                                            color = if (selected) AppPalette else Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Surface(
                                            color = if (backend.requiresNetwork) Color(0xFF243048) else Color(0xFF1B3A26),
                                            shape = RoundedCornerShape(6.dp),
                                        ) {
                                            Text(
                                                text = backend.badge,
                                                color = if (backend.requiresNetwork) Color(0xFF8AB4F8) else Color(0xFF81C784),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                            )
                                        }
                                    }
                                    Text(backend.description, color = Color(0xFF9EA8BC), fontSize = 11.sp)
                                    if (stat != null && (stat.successCount > 0 || stat.failureCount > 0)) {
                                        Text(
                                            text = "Stats: ${stat.successCount} ok · ${stat.failureCount} fail · ${stat.lastLatencyMs}ms",
                                            color = Color(0xFF81C784),
                                            fontSize = 10.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = lingvaMirror,
                    onValueChange = {
                        lingvaMirror = it
                        TranslationEnginePreferences.setLingvaMirrorUrl(context, it)
                    },
                    singleLine = true,
                    label = { Text("Lingva Mirror Base URL", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth().testTag("lingva_url_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppPalette,
                        unfocusedBorderColor = Color(0xFF34343F),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                    ),
                )

                OutlinedTextField(
                    value = libreUrl,
                    onValueChange = {
                        libreUrl = it
                        TranslationEnginePreferences.setLibreTranslateConfig(context, it, libreKey)
                    },
                    singleLine = true,
                    label = { Text("LibreTranslate Endpoint URL", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth().testTag("libre_url_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppPalette,
                        unfocusedBorderColor = Color(0xFF34343F),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                    ),
                )

                OutlinedTextField(
                    value = libreKey,
                    onValueChange = {
                        libreKey = it
                        TranslationEnginePreferences.setLibreTranslateConfig(context, libreUrl, it)
                    },
                    singleLine = true,
                    label = { Text("LibreTranslate API Key (Optional)", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth().testTag("libre_key_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppPalette,
                        unfocusedBorderColor = Color(0xFF34343F),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                    ),
                )
            }
        }

        // 4. Language Auto-Detection, ML Kit Offline Packs & Live Overlay Behavior
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1A1A22),
            border = BorderStroke(1.dp, Color(0xFF2D3140)),
            modifier = Modifier.fillMaxWidth().testTag("translations_language_card"),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Translate, contentDescription = "Language settings", tint = AppPalette, modifier = Modifier.size(20.dp))
                        Text(
                            text = "Languages (${sourceLang.uppercase()} → ${targetLang.uppercase()})",
                            color = Color.White,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    TextButton(
                        enabled = sourceLang != "auto",
                        onClick = { GameTranslatorEngine.swapLanguages(context) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("translations_swap_lang_btn"),
                    ) {
                        Icon(Icons.Rounded.SwapHoriz, contentDescription = "Swap source and target languages", tint = AppPalette, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Swap", color = AppPalette, fontWeight = FontWeight.Bold)
                    }
                }

                Text("Teammate Speech Language (Auto-Detect recommended)", color = Color(0xFFB7C0D1), fontSize = 11.5.sp)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    GameTranslatorEngine.supportedLanguages.forEach { lang ->
                        FilterChip(
                            selected = sourceLang == lang.code,
                            onClick = { GameTranslatorEngine.setSourceLanguage(context, lang.code) },
                            label = { Text("${lang.shortLabel} · ${lang.fullName}", fontSize = 11.sp) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }

                Text("Target Caption Language", color = Color(0xFFB7C0D1), fontSize = 11.5.sp)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    GameTranslatorEngine.supportedTargetLanguages.forEach { lang ->
                        FilterChip(
                            selected = targetLang == lang.code,
                            onClick = { GameTranslatorEngine.setTargetLanguage(context, lang.code) },
                            label = { Text("${lang.shortLabel} · ${lang.fullName}", fontSize = 11.sp) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }

                Text("Offline ML Kit Language Packs", color = Color(0xFFD0D6E0), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    GameTranslatorEngine.supportedTargetLanguages.forEach { lang ->
                        val downloaded = lang.code in downloadedPacks
                        val isBusy = busyPack == lang.code
                        FilterChip(
                            selected = downloaded,
                            enabled = !isBusy,
                            onClick = {
                                GameTranslatorEngine.downloadLanguagePack(context, lang.code)
                                scope.launch {
                                    TranslatorModelManager.ensureTargetLanguageModel(lang.code)
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (downloaded) Icons.Rounded.CloudDone else Icons.Rounded.CloudDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                            },
                            label = { Text(if (isBusy) "${lang.shortLabel}…" else "${lang.shortLabel}${if (downloaded) " ✓" else ""}", fontSize = 10.5.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF1E3A24),
                                selectedLabelColor = Color(0xFF81C784),
                            ),
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }

                // Toggles & Precision Sliders
                TranslatorSwitchRow(
                    title = "Click-Through Game Pass-Through Lock",
                    subtitle = "Keeps live captions non-touchable (FLAG_NOT_TOUCHABLE) so gameplay taps never block",
                    checked = config.clickThroughLocked,
                    tag = "translations_click_through_switch",
                ) { TranslationEnginePreferences.setClickThroughLocked(context, it) }

                TranslatorSwitchRow(
                    title = "Show Dual-Line (Original + Translation)",
                    subtitle = "Displays teammate's transcribed phrase above the translated callout",
                    checked = config.showDualLineCaption,
                    tag = "translations_dual_line_switch",
                ) { TranslationEnginePreferences.setShowDualLineCaption(context, it) }

                TranslatorSwitchRow(
                    title = "Tactical Gaming Callout Lexicon",
                    subtitle = "Instant <1ms FPS/MOBA slang lookup (revive, flank, push, ammo) across ES/AR/RU/FR/PT/DE/JA",
                    checked = config.gamingLexiconEnabled,
                    tag = "translations_gaming_lexicon_switch",
                ) { TranslationEnginePreferences.setGamingLexiconEnabled(context, it) }

                TranslatorSwitchRow(
                    title = "Exclude SpotUI Music Audio UID",
                    subtitle = "Prevents music playback from feeding into teammate voice transcription",
                    checked = config.excludeOwnMusicUid,
                    tag = "translations_exclude_uid_switch",
                ) { TranslationEnginePreferences.setExcludeOwnMusicUid(context, it) }

                TranslatorSwitchRow(
                    title = "Internal Audio Auto Gain Control (AGC)",
                    subtitle = "Normalizes quiet teammate voices in internal game capture before Vosk ASR",
                    checked = config.internalAgcEnabled,
                    tag = "translations_agc_switch",
                ) { TranslationEnginePreferences.setInternalAgcEnabled(context, it) }

                TranslatorSwitchRow(
                    title = "Profanity Masking Filter",
                    subtitle = "Masks strong profanity tokens cleanly inside live overlay captions",
                    checked = config.profanityMaskEnabled,
                    tag = "translations_profanity_switch",
                ) { TranslationEnginePreferences.setProfanityMaskEnabled(context, it) }

                TranslatorSwitchRow(
                    title = "Keep Floater Translate Tab Unlocked",
                    subtitle = "Show the Translate tab even when no game is detected (for Discord / Twitch)",
                    checked = alwaysUnlocked,
                    tag = "translations_always_unlock_switch",
                ) { GameTranslatorEngine.setAlwaysUnlockTranslator(context, it) }

                TranslatorSwitchRow(
                    title = "Continuous Hands-Free Speech Fallback",
                    subtitle = "Automatically restarts SpeechRecognizer when using microphone fallback mode",
                    checked = continuousListen,
                    tag = "translations_continuous_switch",
                ) { GameTranslatorEngine.setContinuousListen(context, it) }

                TranslatorSwitchRow(
                    title = "Floating Live Caption Pill",
                    subtitle = "Render non-intrusive live captions above games via Accessibility overlay",
                    checked = bubbleEnabled,
                    tag = "translations_bubble_enabled_switch",
                ) { GameTranslatorEngine.setBubbleEnabled(context, it) }

                // Sliders
                Text(
                    "Silence Auto-Hide Timeout: ${String.format(Locale.US, "%.1f", config.autoHideSilenceMs / 1000f)}s",
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Slider(
                    value = config.autoHideSilenceMs.toFloat(),
                    onValueChange = { TranslationEnginePreferences.setAutoHideSilenceMs(context, it.roundToInt().toLong()) },
                    valueRange = 1_500f..12_000f,
                    modifier = Modifier.testTag("translations_autohide_slider"),
                    colors = SliderDefaults.colors(activeTrackColor = AppPalette, thumbColor = AppPalette),
                )

                Text(
                    "Auto Language Detect Confidence: ${(config.autoDetectThreshold * 100).roundToInt()}%",
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Slider(
                    value = config.autoDetectThreshold,
                    onValueChange = { TranslationEnginePreferences.setAutoDetectThreshold(context, it) },
                    valueRange = 0.10f..0.85f,
                    modifier = Modifier.testTag("translations_detect_threshold_slider"),
                    colors = SliderDefaults.colors(activeTrackColor = AppPalette, thumbColor = AppPalette),
                )

                Text(
                    "Teammate Voice Music Ducking: ${config.teammateVoiceDuckingPct}%",
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Slider(
                    value = config.teammateVoiceDuckingPct.toFloat(),
                    onValueChange = { TranslationEnginePreferences.setTeammateVoiceDuckingPct(context, it.roundToInt()) },
                    valueRange = 0f..75f,
                    modifier = Modifier.testTag("translations_ducking_slider"),
                    colors = SliderDefaults.colors(activeTrackColor = AppPalette, thumbColor = AppPalette),
                )

                Text(
                    "Caption Width ${bubbleWidthDp.roundToInt()}dp · Text ${bubbleTextSp.roundToInt()}sp · Opacity ${(bubbleOpacity * 100).roundToInt()}%",
                    color = Color(0xFFB7C0D1),
                    fontSize = 11.5.sp,
                )
                Slider(
                    value = bubbleWidthDp,
                    onValueChange = { GameTranslatorEngine.setBubbleWidthDp(context, it) },
                    valueRange = 160f..420f,
                    colors = SliderDefaults.colors(activeTrackColor = AppPalette, thumbColor = AppPalette),
                )
                Slider(
                    value = bubbleTextSp,
                    onValueChange = { GameTranslatorEngine.setBubbleTextSizeSp(context, it) },
                    valueRange = 11f..24f,
                    colors = SliderDefaults.colors(activeTrackColor = AppPalette, thumbColor = AppPalette),
                )
                Slider(
                    value = bubbleOpacity,
                    onValueChange = { GameTranslatorEngine.setBubbleOpacity(context, it) },
                    valueRange = 0.55f..0.98f,
                    colors = SliderDefaults.colors(activeTrackColor = AppPalette, thumbColor = AppPalette),
                )

                // Live Router Test Bench
                Text("Live Multi-Engine Translation Test", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = testPhrase,
                        onValueChange = { testPhrase = it },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("translations_test_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppPalette,
                            unfocusedBorderColor = Color(0xFF34343F),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                        ),
                    )
                    Button(
                        enabled = !testBusy && testPhrase.isNotBlank(),
                        onClick = {
                            testBusy = true
                            GameTranslatorEngine.translateTextNow(testPhrase, recordHistory = true)
                            scope.launch {
                                val res = MultiEngineTranslationRouter.translate(
                                    context = context,
                                    rawText = testPhrase,
                                    sourceHint = sourceLang,
                                    targetLang = targetLang,
                                )
                                testOutput = "[${res.detectedSourceLang.uppercase()} → ${res.targetLang.uppercase()} via ${res.engineUsed.badge} · ${res.latencyMs}ms] ${res.translatedText}"
                                testBusy = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AppPalette, contentColor = Color.Black),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("translations_test_btn"),
                    ) {
                        Text(if (testBusy) "…" else "Translate", fontWeight = FontWeight.Bold)
                    }
                }

                val shownResult = testOutput.ifBlank {
                    if (legacyBubble.translatedEnglish.isNotBlank()) {
                        "[${legacyBubble.detectedLangCode.uppercase()} → ${legacyBubble.targetLangCode.uppercase()}] ${legacyBubble.translatedEnglish}"
                    } else {
                        ""
                    }
                }
                if (shownResult.isNotBlank()) {
                    Surface(
                        color = Color(0xFF15251A),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = shownResult,
                            color = Color(0xFF81C784),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TranslatorSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color(0xFF9EA8BC), fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag),
            colors = SwitchDefaults.colors(checkedThumbColor = AppPalette),
        )
    }
}
