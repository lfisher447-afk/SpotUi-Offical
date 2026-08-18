package com.music.spotui.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import com.music.spotui.data.preferences.BackupPref
import com.music.spotui.data.preferences.LosslessTimeout
import com.music.spotui.data.preferences.getCellularLosslessTimeout
import com.music.spotui.data.preferences.getDownloadLosslessTimeout
import com.music.spotui.data.preferences.getWifiLosslessTimeout
import com.music.spotui.data.preferences.setCellularLosslessTimeout
import com.music.spotui.data.preferences.setDownloadLosslessTimeout
import com.music.spotui.data.preferences.setWifiLosslessTimeout
import com.music.spotui.util.BackupHelper
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.music.spotui.data.BatteryOptimizationHelper
import com.music.spotui.data.preferences.CROSSFADE_MAX_MS
import com.music.spotui.data.preferences.StreamQuality
import com.music.spotui.data.preferences.getCellularQuality
import com.music.spotui.data.preferences.getCrossfadeMs
import com.music.spotui.data.preferences.isGaplessPlaybackEnabled
import com.music.spotui.data.preferences.setCrossfadeMs
import com.music.spotui.data.preferences.setGaplessPlaybackEnabled
import com.music.spotui.data.preferences.getDownloadQuality
import com.music.spotui.data.preferences.isVideoFallbackEnabled
import com.music.spotui.data.preferences.isAutoPlayEnabled
import com.music.spotui.data.preferences.isPreloadEnabled
import com.music.spotui.data.preferences.isWebPlaybackEnabled
import com.music.spotui.data.preferences.getWifiQuality
import com.music.spotui.data.preferences.setCellularQuality
import com.music.spotui.data.preferences.setDownloadQuality
import com.music.spotui.data.preferences.setAutoPlayEnabled
import com.music.spotui.data.preferences.setPreloadEnabled
import com.music.spotui.data.preferences.setVideoFallbackEnabled
import com.music.spotui.data.preferences.setWebPlaybackEnabled
import com.music.spotui.data.preferences.setWifiQuality
import com.music.spotui.data.preferences.getUpdateRepoUrl
import com.music.spotui.data.preferences.setUpdateRepoUrl
import com.music.spotui.data.preferences.resetUpdateRepoUrl
import com.music.spotui.data.preferences.DEFAULT_UPDATE_REPO_URL
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.music.spotui.ui.components.DefaultAppPrompt
import com.music.spotui.util.DefaultLinkHelper
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.AppPalette
import com.music.spotui.data.cache.CacheStorageManager
import com.music.spotui.data.export.NovaAcExportManager
import com.music.spotui.di.SongPlayer
import com.music.spotui.util.AppDiagnostics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current

    var wifiQ by remember { mutableStateOf(getWifiQuality(context)) }
    var cellQ by remember { mutableStateOf(getCellularQuality(context)) }
    var dlQ by remember { mutableStateOf(getDownloadQuality(context)) }
    var wifiLosslessTimeout by remember { mutableStateOf(getWifiLosslessTimeout(context)) }
    var cellLosslessTimeout by remember { mutableStateOf(getCellularLosslessTimeout(context)) }
    var dlLosslessTimeout by remember { mutableStateOf(getDownloadLosslessTimeout(context)) }
    var crossfadeMs by remember { mutableStateOf(getCrossfadeMs(context).toFloat()) }
    var gaplessPlayback by remember { mutableStateOf(isGaplessPlaybackEnabled(context)) }
    var videoFallback by remember { mutableStateOf(isVideoFallbackEnabled(context)) }
    var autoPlay by remember { mutableStateOf(isAutoPlayEnabled(context)) }
    var preloadNextStream by remember { mutableStateOf(isPreloadEnabled(context)) }
    var spotifyWebPlayback by remember { mutableStateOf(isWebPlaybackEnabled(context)) }
    var youtubeMusicWeb by remember { mutableStateOf(com.music.spotui.data.preferences.isYoutubeMusicWebEnabled(context)) }
    var playerVideoPreview by remember { mutableStateOf(com.music.spotui.data.preferences.isPlayerVideoPreviewEnabled(context)) }
    var batteryOptExempt by remember { mutableStateOf(BatteryOptimizationHelper.isIgnoringBatteryOptimization(context)) }
    var updateRepoUrl by remember { mutableStateOf(getUpdateRepoUrl(context)) }
    var isDefaultLinkHandler by remember { mutableStateOf(DefaultLinkHelper.isAppDefaultLinkHandler(context)) }
    var showDefaultGuide by remember { mutableStateOf(false) }
    var showProviderStatusDialog by remember { mutableStateOf(false) }
    var providerStatuses by remember { mutableStateOf(emptyList<com.metrolist.spotify.SpotiFlac.ProviderStatus>()) }
    var isRefreshingStatuses by remember { mutableStateOf(false) }

    var backupDirUri by remember { mutableStateOf(BackupPref.getDirectoryUri(context)) }
    var folderName by remember(backupDirUri) { mutableStateOf(BackupHelper.getFolderDisplayName(context, backupDirUri)) }
    var isAutoBackup by remember { mutableStateOf(BackupPref.isAutoBackupEnabled(context)) }
    var isRestoring by remember { mutableStateOf(false) }
    var isBackingUp by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var eqPreset by remember { mutableStateOf(com.music.spotui.data.preferences.getEqPreset(context)) }
    var eqBass by remember { mutableStateOf(com.music.spotui.data.preferences.getEqBass(context)) }
    var eqVocal by remember { mutableStateOf(com.music.spotui.data.preferences.getEqVocal(context)) }
    var eqTreble by remember { mutableStateOf(com.music.spotui.data.preferences.getEqTreble(context)) }
    var eqLowMid by remember { mutableStateOf(com.music.spotui.data.preferences.getEqLowMid(context)) }
    var eqHighMid by remember { mutableStateOf(com.music.spotui.data.preferences.getEqHighMid(context)) }
    var eqSpatial by remember { mutableStateOf(com.music.spotui.data.preferences.isEqSpatialEnabled(context)) }
    var audioNormalizer by remember { mutableStateOf(com.music.spotui.data.preferences.isAudioNormalizerEnabled(context)) }
    var spatialProfile by remember { mutableStateOf(com.music.spotui.data.preferences.getSpatialProfile(context)) }
    var spatialStrength by remember { mutableStateOf(com.music.spotui.data.preferences.getSpatialStrength(context).toFloat()) }
    var normalizerGainMb by remember { mutableStateOf(com.music.spotui.data.preferences.getAudioNormalizerGainMb(context).toFloat()) }

    var cacheSizeStr by remember { mutableStateOf("Calculating…") }
    var cacheDetailStr by remember { mutableStateOf("") }
    var isOptimizingCache by remember { mutableStateOf(false) }
    var audioSectionExpanded by rememberSaveable { mutableStateOf(true) }
    var storageSectionExpanded by rememberSaveable { mutableStateOf(false) }
    var diagnosticsSectionExpanded by rememberSaveable { mutableStateOf(false) }
    var interfaceSectionExpanded by rememberSaveable { mutableStateOf(false) }
    var navigationMode by remember { mutableStateOf(com.music.spotui.data.preferences.getNavigationMode(context)) }
    var compactUi by remember { mutableStateOf(com.music.spotui.data.preferences.isCompactUiEnabled(context)) }
    var cornerRadiusDp by remember { mutableStateOf(com.music.spotui.data.preferences.getCornerRadiusDp(context)) }
    var novaAcImportStatus by remember { mutableStateOf<String?>(null) }
    var mrbeanEnabled by remember { mutableStateOf(org.eclipse.Mrbean.client.MrbeanNetworkSettings.isEnabled(context)) }
    var mrbeanCandidateLimit by remember { mutableStateOf(org.eclipse.Mrbean.client.MrbeanNetworkSettings.candidateLimit(context).toFloat()) }
    var mrbeanConnectSeconds by remember { mutableStateOf(org.eclipse.Mrbean.client.MrbeanNetworkSettings.connectTimeoutMs(context) / 1_000f) }
    var mrbeanReadSeconds by remember { mutableStateOf(org.eclipse.Mrbean.client.MrbeanNetworkSettings.readTimeoutMs(context) / 1_000f) }
    var mrbeanChunkMiB by remember { mutableStateOf(org.eclipse.Mrbean.client.MrbeanNetworkSettings.chunkMiB(context).toFloat()) }
    var mrbeanRetries by remember { mutableStateOf(org.eclipse.Mrbean.client.MrbeanNetworkSettings.rangeRetries(context).toFloat()) }
    var mrbeanSectionExpanded by rememberSaveable { mutableStateOf(false) }
    var novaAcMrbeanSectionExpanded by rememberSaveable { mutableStateOf(false) }
    var novaAcMrbeanSnapshot by remember { mutableStateOf(org.eclipse.Mrbean.client.MrbeanNovaAcController.snapshot(context)) }
    var novaAcMrbeanSummary by remember { mutableStateOf(org.eclipse.Mrbean.client.MrbeanNovaAcController.lastSummary(context)) }
    var novaAcRecoveries by remember { mutableStateOf(NovaAcExportManager.listRecoverableArchives(context)) }
    var pendingNovaAcRecoveryId by remember { mutableStateOf<String?>(null) }
    var novaAcRecoveryStatus by remember { mutableStateOf("") }
    var themeSectionExpanded by rememberSaveable { mutableStateOf(false) }
    var accessibilitySectionExpanded by rememberSaveable { mutableStateOf(false) }
    var activeThemePreset by remember { mutableStateOf(com.music.spotui.ui.theme.ThemePreferences.preset(context)) }
    var themeStatus by remember { mutableStateOf("") }
    var accessibilityHighContrast by remember { mutableStateOf(com.music.spotui.ui.theme.ThemePreferences.highContrast(context)) }
    var accessibilityTextScale by remember { mutableStateOf(com.music.spotui.ui.theme.ThemePreferences.textScale(context)) }
    var accessibilityReduceMotion by remember { mutableStateOf(com.music.spotui.ui.theme.ThemePreferences.reduceMotion(context)) }
    var accessibilityTouchTargets by remember { mutableStateOf(com.music.spotui.ui.theme.ThemePreferences.minimumTouchTarget(context)) }
    var accessibilityMonoAudio by remember { mutableStateOf(com.music.spotui.ui.theme.ThemePreferences.monoAudio(context)) }
    var accessibilityBalance by remember { mutableStateOf(com.music.spotui.ui.theme.ThemePreferences.audioBalance(context)) }
    var youtubeOAuthClientId by remember { mutableStateOf(com.music.spotui.data.youtube.YouTubeMusicSync.clientId(context)) }
    var youtubeSyncStatus by remember { mutableStateOf(com.music.spotui.data.youtube.YouTubeMusicSync.status(context)) }
    var youtubeSyncBusy by remember { mutableStateOf(false) }

    fun refreshCacheSummary() {
        val summary = CacheStorageManager.summarize(context)
        cacheSizeStr = summary.displaySize
        cacheDetailStr = summary.detail
    }

    fun refreshNovaAcRecoveries() {
        novaAcRecoveries = NovaAcExportManager.listRecoverableArchives(context)
        novaAcMrbeanSnapshot = org.eclipse.Mrbean.client.MrbeanNovaAcController.snapshot(context)
        novaAcMrbeanSummary = org.eclipse.Mrbean.client.MrbeanNovaAcController.lastSummary(context)
    }

    LaunchedEffect(Unit) { refreshCacheSummary() }
    LaunchedEffect(eqBass, eqLowMid, eqVocal, eqHighMid, eqTreble, eqSpatial, audioNormalizer, spatialProfile, spatialStrength, normalizerGainMb) {
        SongPlayer.refreshAudioEffects(context)
    }

    val dirPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            BackupPref.setDirectoryUri(context, uri.toString())
            backupDirUri = uri.toString()
            folderName = BackupHelper.getFolderDisplayName(context, uri.toString())
            scope.launch {
                val autoOk = BackupHelper.performAutoBackup(context)
                val msg = if (autoOk) "Backup folder set to $folderName (Auto-backup created)" else "Backup folder set to $folderName"
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    val restoreFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isRestoring = true
            scope.launch {
                val (success, message) = BackupHelper.restoreFromFileUri(context, uri)
                isRestoring = false
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                if (success) {
                    wifiQ = getWifiQuality(context)
                    cellQ = getCellularQuality(context)
                    dlQ = getDownloadQuality(context)
                    crossfadeMs = getCrossfadeMs(context).toFloat()
                    videoFallback = isVideoFallbackEnabled(context)
                    autoPlay = isAutoPlayEnabled(context)
                    updateRepoUrl = getUpdateRepoUrl(context)
                    backupDirUri = BackupPref.getDirectoryUri(context)
                    isAutoBackup = BackupPref.isAutoBackupEnabled(context)
                }
            }
        }
    }

    val novaAcImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            NovaAcExportManager.inspectHeader(context, uri)
                .onSuccess { header ->
                    novaAcImportStatus = header.compatibilityWarning
                        ?: "Compatible NovaAc v${header.formatVersion}: ${header.trackCount} tracks from ${header.collectionName}."
                }
                .onFailure { error ->
                    novaAcImportStatus = "Unable to inspect NovaAc file: ${error.message ?: "invalid file"}"
                }
        }
    }

    val novaAcRecoveryDeliveryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val recoveryId = pendingNovaAcRecoveryId
        pendingNovaAcRecoveryId = null
        if (uri != null && !recoveryId.isNullOrBlank()) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    NovaAcExportManager.retryRecoveryDelivery(context, uri, recoveryId)
                }
                novaAcRecoveryStatus = result.fold(
                    onSuccess = { delivered ->
                        "Recovery ${delivered.recoveryId.take(8)} delivered ${delivered.archiveBytes / (1024 * 1024)} MiB${if (delivered.deliveryVerified) " and verified" else "; provider readback unavailable"}."
                    },
                    onFailure = { error -> "Recovery delivery failed: ${error.message ?: "unknown error"}" },
                )
                refreshNovaAcRecoveries()
            }
        }
    }

    val themeJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            com.music.spotui.ui.theme.ThemePreferences.importJson(context, uri)
                .onSuccess { spec ->
                    activeThemePreset = com.music.spotui.ui.theme.ExpressiveThemePreset.CUSTOM_JSON
                    themeStatus = "Applied ${spec.name} from validated Material JSON."
                }
                .onFailure { themeStatus = "Theme import failed: ${it.message ?: "invalid JSON"}" }
        }
    }
    val themeImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            com.music.spotui.ui.theme.ThemePreferences.importImage(context, uri)
                .onSuccess { spec ->
                    activeThemePreset = com.music.spotui.ui.theme.ExpressiveThemePreset.IMAGE_DERIVED
                    themeStatus = "Applied ${spec.name}; colors were derived locally on this device."
                }
                .onFailure { themeStatus = "Image palette import failed: ${it.message ?: "unreadable image"}" }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isDefaultLinkHandler = DefaultLinkHelper.isAppDefaultLinkHandler(context)
                youtubeSyncStatus = com.music.spotui.data.youtube.YouTubeMusicSync.status(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val batteryOptLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        batteryOptExempt = BatteryOptimizationHelper.isIgnoringBatteryOptimization(context)
    }

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(26.dp)
                            .clickable { navController.popBackStack() }
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = AppBackground)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                // Clear the bottom nav + mini player so the last section
                // (account / log out) isn't hidden under the bar.
                .padding(bottom = 200.dp)
        ) {
            CollapsibleSettingsSection(
                title = "Audio FX & Equalizer",
                subtitle = "Device equalizer, spatial audio, and loudness controls",
                expanded = audioSectionExpanded,
                onExpandedChange = { audioSectionExpanded = it },
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1A1A20))
                    .padding(14.dp)
            ) {
                // Volume Normalizer Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Volume Normalization", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Balance audio levels for consistent volume", color = Color.Gray, fontSize = 12.sp)
                    }
                    Switch(
                        checked = audioNormalizer,
                        onCheckedChange = { checked ->
                            audioNormalizer = checked
                            com.music.spotui.data.preferences.setAudioNormalizerEnabled(context, checked)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = AppPalette)
                    )
                }
                if (audioNormalizer) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Normalizer intensity: ${normalizerGainMb.toInt()} mB", color = AppPalette, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = normalizerGainMb,
                        onValueChange = { normalizerGainMb = it },
                        onValueChangeFinished = {
                            com.music.spotui.data.preferences.setAudioNormalizerGainMb(context, normalizerGainMb.toInt())
                        },
                        valueRange = 0f..600f,
                        colors = SliderDefaults.colors(activeTrackColor = AppPalette, thumbColor = AppPalette),
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3D Spatial Audio Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("3D Spatial Audio Surround", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Immersive dimensional surround soundstage", color = Color.Gray, fontSize = 12.sp)
                    }
                    Switch(
                        checked = eqSpatial,
                        onCheckedChange = { checked ->
                            eqSpatial = checked
                            com.music.spotui.data.preferences.setEqSpatialEnabled(context, checked)
                            com.music.spotui.di.SongPlayer.refreshAudioEffects(context)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = AppPalette)
                    )
                }
                if (eqSpatial) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Spatial profile", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        com.music.spotui.data.preferences.SpatialProfile.entries.forEach { profile ->
                            val selected = spatialProfile == profile
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (selected) AppPalette else Color.White.copy(alpha = 0.08f))
                                    .clickable {
                                        spatialProfile = profile
                                        com.music.spotui.data.preferences.setSpatialProfile(context, profile)
                                        com.music.spotui.di.SongPlayer.refreshAudioEffects(context)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text(profile.label, color = if (selected) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Spatial depth", color = Color.White, fontSize = 13.sp)
                        Text("${spatialStrength.toInt() / 10}%", color = AppPalette, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = spatialStrength,
                        onValueChange = { spatialStrength = it },
                        onValueChangeFinished = {
                            com.music.spotui.data.preferences.setSpatialStrength(context, spatialStrength.toInt())
                            com.music.spotui.di.SongPlayer.refreshAudioEffects(context)
                        },
                        valueRange = 0f..1000f,
                        colors = SliderDefaults.colors(activeTrackColor = AppPalette, thumbColor = AppPalette),
                    )
                    Text(spatialProfile.detail, color = Color(0xFFB3B3B3), fontSize = 11.sp)
                    val spatialCapabilities = remember(eqSpatial, spatialStrength, spatialProfile) {
                        com.music.spotui.audio.AudioEffectController.capabilityReport()
                    }
                    Text(
                        when {
                            spatialCapabilities.sessionId <= 0 -> "Start a track to attach the live spatial processor."
                            spatialCapabilities.hardwareSpatialAvailable && spatialCapabilities.spatialStrengthSupported -> "Active: software stereo width + Android hardware spatial effect."
                            spatialCapabilities.hardwareSpatialAvailable -> "Active: software stereo width + Android virtualizer preset."
                            else -> "Active: software stereo-width processing (hardware virtualizer unavailable on this output)."
                        },
                        color = AppPalette,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text("Equalizer Presets", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                // Horizontal scrollable presets row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val presets = listOf("Flat", "Bass Boost", "Vocal Boost", "Electronic", "Classical", "Pop", "Rock")
                    presets.forEach { presetName ->
                        val isSelected = eqPreset == presetName
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) AppPalette else Color.White.copy(alpha = 0.08f))
                                .clickable {
                                    eqPreset = presetName
                                    com.music.spotui.data.preferences.setEqPreset(context, presetName)
                                    // Update sliders based on preset
                                    when (presetName) {
                                        "Flat" -> {
                                            eqBass = 50f; eqVocal = 50f; eqTreble = 50f
                                        }
                                        "Bass Boost" -> {
                                            eqBass = 90f; eqVocal = 45f; eqTreble = 40f
                                        }
                                        "Vocal Boost" -> {
                                            eqBass = 40f; eqVocal = 90f; eqTreble = 45f
                                        }
                                        "Electronic" -> {
                                            eqBass = 75f; eqVocal = 50f; eqTreble = 80f
                                        }
                                        "Classical" -> {
                                            eqBass = 45f; eqVocal = 75f; eqTreble = 65f
                                        }
                                        "Pop" -> {
                                            eqBass = 60f; eqVocal = 65f; eqTreble = 70f
                                        }
                                        "Rock" -> {
                                            eqBass = 80f; eqVocal = 55f; eqTreble = 75f
                                        }
                                    }
                                    eqLowMid = 50f; eqHighMid = 50f
                                    com.music.spotui.data.preferences.setEqBass(context, eqBass)
                                    com.music.spotui.data.preferences.setEqLowMid(context, eqLowMid)
                                    com.music.spotui.data.preferences.setEqVocal(context, eqVocal)
                                    com.music.spotui.data.preferences.setEqHighMid(context, eqHighMid)
                                    com.music.spotui.data.preferences.setEqTreble(context, eqTreble)
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = presetName,
                                color = if (isSelected) Color.Black else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Slider: Bass
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Bass Boost", color = Color.White, fontSize = 13.sp)
                        Text("${eqBass.toInt()}%", color = AppPalette, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = eqBass,
                        onValueChange = {
                            eqBass = it
                            eqPreset = "Custom"
                        },
                        onValueChangeFinished = {
                            com.music.spotui.data.preferences.setEqBass(context, eqBass)
                            com.music.spotui.data.preferences.setEqPreset(context, "Custom")
                        },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(
                            activeTrackColor = AppPalette,
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                            thumbColor = AppPalette
                        )
                    )
                }

                // Slider: Vocal
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Vocal Enhancer", color = Color.White, fontSize = 13.sp)
                        Text("${eqVocal.toInt()}%", color = AppPalette, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = eqVocal,
                        onValueChange = {
                            eqVocal = it
                            eqPreset = "Custom"
                        },
                        onValueChangeFinished = {
                            com.music.spotui.data.preferences.setEqVocal(context, eqVocal)
                            com.music.spotui.data.preferences.setEqPreset(context, "Custom")
                        },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(
                            activeTrackColor = AppPalette,
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                            thumbColor = AppPalette
                        )
                    )
                }

                // Slider: Low mids
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Low-mid body", color = Color.White, fontSize = 13.sp)
                        Text("${eqLowMid.toInt()}%", color = AppPalette, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(value = eqLowMid, onValueChange = { eqLowMid = it; eqPreset = "Custom" }, onValueChangeFinished = {
                        com.music.spotui.data.preferences.setEqLowMid(context, eqLowMid)
                        com.music.spotui.data.preferences.setEqPreset(context, "Custom")
                    }, valueRange = 0f..100f, colors = SliderDefaults.colors(activeTrackColor = AppPalette, inactiveTrackColor = Color.White.copy(alpha = 0.2f), thumbColor = AppPalette))
                }

                // Slider: High mids
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Presence", color = Color.White, fontSize = 13.sp)
                        Text("${eqHighMid.toInt()}%", color = AppPalette, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(value = eqHighMid, onValueChange = { eqHighMid = it; eqPreset = "Custom" }, onValueChangeFinished = {
                        com.music.spotui.data.preferences.setEqHighMid(context, eqHighMid)
                        com.music.spotui.data.preferences.setEqPreset(context, "Custom")
                    }, valueRange = 0f..100f, colors = SliderDefaults.colors(activeTrackColor = AppPalette, inactiveTrackColor = Color.White.copy(alpha = 0.2f), thumbColor = AppPalette))
                }

                // Slider: Treble
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Treble Detail", color = Color.White, fontSize = 13.sp)
                        Text("${eqTreble.toInt()}%", color = AppPalette, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = eqTreble,
                        onValueChange = {
                            eqTreble = it
                            eqPreset = "Custom"
                        },
                        onValueChangeFinished = {
                            com.music.spotui.data.preferences.setEqTreble(context, eqTreble)
                            com.music.spotui.data.preferences.setEqPreset(context, "Custom")
                        },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(
                            activeTrackColor = AppPalette,
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                            thumbColor = AppPalette
                        )
                    )
                }
            }
            }

            Spacer(Modifier.height(16.dp))

            CollapsibleSettingsSection(
                title = "Eclipse MrBean Network Engine",
                subtitle = "Resolver and ranged-download transport tuning",
                expanded = mrbeanSectionExpanded,
                onExpandedChange = { mrbeanSectionExpanded = it },
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF1A1A20)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Enable MrBean transport tuning", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("Applies settings to stream-candidate fallback and byte-range downloads.", color = Color.Gray, fontSize = 11.sp)
                        }
                        Switch(
                            checked = mrbeanEnabled,
                            onCheckedChange = { enabled ->
                                mrbeanEnabled = enabled
                                org.eclipse.Mrbean.client.MrbeanNetworkSettings.setEnabled(context, enabled)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = AppPalette),
                        )
                    }
                    if (mrbeanEnabled) {
                        Text("Candidate fallback: ${mrbeanCandidateLimit.toInt()} ranked sources", color = Color.White, fontSize = 13.sp)
                        Slider(
                            value = mrbeanCandidateLimit,
                            onValueChange = { mrbeanCandidateLimit = it },
                            onValueChangeFinished = {
                                mrbeanCandidateLimit = mrbeanCandidateLimit.toInt().coerceIn(3, 8).toFloat()
                                org.eclipse.Mrbean.client.MrbeanNetworkSettings.setCandidateLimit(context, mrbeanCandidateLimit.toInt())
                            },
                            valueRange = 3f..8f,
                            steps = 4,
                            colors = SliderDefaults.colors(activeTrackColor = AppPalette, thumbColor = AppPalette),
                        )
                        Text("Connection timeout: ${mrbeanConnectSeconds.toInt()} seconds", color = Color.White, fontSize = 13.sp)
                        Slider(
                            value = mrbeanConnectSeconds,
                            onValueChange = { mrbeanConnectSeconds = it },
                            onValueChangeFinished = {
                                mrbeanConnectSeconds = (mrbeanConnectSeconds / 5f).toInt().coerceIn(1, 9).times(5).toFloat()
                                org.eclipse.Mrbean.client.MrbeanNetworkSettings.setConnectTimeoutMs(context, (mrbeanConnectSeconds * 1_000).toInt())
                            },
                            valueRange = 5f..45f,
                            steps = 7,
                            colors = SliderDefaults.colors(activeTrackColor = AppPalette, thumbColor = AppPalette),
                        )
                        Text("Read timeout: ${mrbeanReadSeconds.toInt()} seconds", color = Color.White, fontSize = 13.sp)
                        Slider(
                            value = mrbeanReadSeconds,
                            onValueChange = { mrbeanReadSeconds = it },
                            onValueChangeFinished = {
                                mrbeanReadSeconds = (mrbeanReadSeconds / 5f).toInt().coerceIn(2, 18).times(5).toFloat()
                                org.eclipse.Mrbean.client.MrbeanNetworkSettings.setReadTimeoutMs(context, (mrbeanReadSeconds * 1_000).toInt())
                            },
                            valueRange = 10f..90f,
                            steps = 15,
                            colors = SliderDefaults.colors(activeTrackColor = AppPalette, thumbColor = AppPalette),
                        )
                        Text("Ranged-download chunk: ${mrbeanChunkMiB.toInt()} MiB", color = Color.White, fontSize = 13.sp)
                        Slider(
                            value = mrbeanChunkMiB,
                            onValueChange = { mrbeanChunkMiB = it },
                            onValueChangeFinished = {
                                mrbeanChunkMiB = mrbeanChunkMiB.toInt().coerceIn(1, 16).toFloat()
                                org.eclipse.Mrbean.client.MrbeanNetworkSettings.setChunkMiB(context, mrbeanChunkMiB.toInt())
                            },
                            valueRange = 1f..16f,
                            steps = 14,
                            colors = SliderDefaults.colors(activeTrackColor = AppPalette, thumbColor = AppPalette),
                        )
                        Text("Retries per byte range: ${mrbeanRetries.toInt()}", color = Color.White, fontSize = 13.sp)
                        Slider(
                            value = mrbeanRetries,
                            onValueChange = { mrbeanRetries = it },
                            onValueChangeFinished = {
                                mrbeanRetries = mrbeanRetries.toInt().coerceIn(1, 5).toFloat()
                                org.eclipse.Mrbean.client.MrbeanNetworkSettings.setRangeRetries(context, mrbeanRetries.toInt())
                            },
                            valueRange = 1f..5f,
                            steps = 3,
                            colors = SliderDefaults.colors(activeTrackColor = AppPalette, thumbColor = AppPalette),
                        )
                    }
                    Text(
                        "Protocol note: the Android TLS stack negotiates HTTP/2 when supported. HTTP/3 is not bundled in this build, so it is never advertised as active.",
                        color = Color(0xFF9FA9BC), fontSize = 11.sp,
                    )
                    TextButton(onClick = {
                        org.eclipse.Mrbean.client.MrbeanNetworkSettings.reset(context)
                        val reset = org.eclipse.Mrbean.client.MrbeanNetworkSettings.snapshot(context)
                        mrbeanEnabled = reset.enabled; mrbeanCandidateLimit = reset.candidateLimit.toFloat()
                        mrbeanConnectSeconds = reset.connectTimeoutMs / 1_000f; mrbeanReadSeconds = reset.readTimeoutMs / 1_000f
                        mrbeanChunkMiB = reset.chunkMiB.toFloat(); mrbeanRetries = reset.rangeRetries.toFloat()
                    }) { Text("Restore recommended network defaults", color = AppPalette) }
                }
            }

            Spacer(Modifier.height(16.dp))

            CollapsibleSettingsSection(
                title = "MrBean NovaAc Archive Controller",
                subtitle = "Adaptive framed archive I/O, recovery, verification, and live diagnostics",
                expanded = novaAcMrbeanSectionExpanded,
                onExpandedChange = { novaAcMrbeanSectionExpanded = it },
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF151D22)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Dedicated NovaAc controller", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("Controls only staged archive work; it does not alter music playback networking.", color = Color(0xFF9FA9BC), fontSize = 11.sp)
                        }
                        Switch(
                            checked = novaAcMrbeanSnapshot.enabled,
                            onCheckedChange = { enabled ->
                                org.eclipse.Mrbean.client.MrbeanNovaAcController.setEnabled(context, enabled)
                                novaAcMrbeanSnapshot = org.eclipse.Mrbean.client.MrbeanNovaAcController.snapshot(context)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = AppPalette),
                        )
                    }
                    if (novaAcMrbeanSnapshot.enabled) {
                        Text("Archive profile", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            org.eclipse.Mrbean.client.MrbeanNovaAcController.Profile.entries.forEach { profile ->
                                TextButton(
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        org.eclipse.Mrbean.client.MrbeanNovaAcController.setProfile(context, profile)
                                        novaAcMrbeanSnapshot = org.eclipse.Mrbean.client.MrbeanNovaAcController.snapshot(context)
                                    },
                                ) {
                                    Text(
                                        profile.label,
                                        color = if (novaAcMrbeanSnapshot.profile == profile) AppPalette else Color(0xFFBBC4D4),
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                        Text(
                            "${novaAcMrbeanSnapshot.profile.label}: ${novaAcMrbeanSnapshot.archiveBufferBytes / 1024} KiB payload I/O · ${novaAcMrbeanSnapshot.deliveryBufferBytes / 1024} KiB delivery I/O · 64 KiB authenticated encryption frames",
                            color = Color(0xFFB7C4CE), fontSize = 11.sp,
                        )
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Retain verified recovery copies", color = Color.White, fontSize = 13.sp)
                                Text("Keep the private staged archive after a verified delivery so it can be inspected or delivered again.", color = Color(0xFF9FA9BC), fontSize = 11.sp)
                            }
                            Switch(
                                checked = novaAcMrbeanSnapshot.keepRecoveryCopies,
                                onCheckedChange = { keep ->
                                    org.eclipse.Mrbean.client.MrbeanNovaAcController.setKeepRecoveryCopies(context, keep)
                                    novaAcMrbeanSnapshot = org.eclipse.Mrbean.client.MrbeanNovaAcController.snapshot(context)
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = AppPalette),
                            )
                        }
                    }
                    Text("Last NovaAc MrBean record", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(novaAcMrbeanSummary, color = Color(0xFFB7C4CE), fontSize = 11.sp)
                    Text("Retained recovery archives: ${novaAcRecoveries.size}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    if (novaAcRecoveries.isEmpty()) {
                        Text("No verified staged archives are currently retained. Enable recovery retention before export to preserve a redelivery copy after success.", color = Color(0xFF9FA9BC), fontSize = 11.sp)
                    } else {
                        novaAcRecoveries.take(3).forEach { recovery ->
                            Column(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF10161C)).padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(recovery.collectionName.ifBlank { "Unnamed NovaAc archive" }, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text("${recovery.archiveBytes / (1024 * 1024)} MiB · ID ${recovery.id.take(8)}${if (recovery.lastFailure.isNotBlank()) " · ${recovery.lastFailure}" else ""}", color = Color(0xFF9FA9BC), fontSize = 10.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(onClick = {
                                        pendingNovaAcRecoveryId = recovery.id
                                        novaAcRecoveryDeliveryLauncher.launch("Spotui_NovaAc_recovery_${recovery.id.take(8)}.novaac")
                                    }) { Text("Deliver again", color = AppPalette, fontSize = 11.sp) }
                                    TextButton(onClick = {
                                        val removed = NovaAcExportManager.purgeRecoveryArchive(context, recovery.id)
                                        novaAcRecoveryStatus = if (removed) "Removed retained recovery ${recovery.id.take(8)}." else "Unable to remove recovery ${recovery.id.take(8)}."
                                        refreshNovaAcRecoveries()
                                    }) { Text("Purge", color = Color(0xFFFFB4AB), fontSize = 11.sp) }
                                }
                            }
                        }
                    }
                    if (novaAcRecoveryStatus.isNotBlank()) Text(novaAcRecoveryStatus, color = Color(0xFFB7C4CE), fontSize = 11.sp)
                    TextButton(onClick = {
                        refreshNovaAcRecoveries()
                    }) { Text("Refresh archive diagnostics", color = AppPalette) }
                }
            }

            Spacer(Modifier.height(16.dp))

            CollapsibleSettingsSection(
                title = "Storage & Cache",
                subtitle = "Manage app-owned temporary media and artwork files",
                expanded = storageSectionExpanded,
                onExpandedChange = { storageSectionExpanded = it },
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1A1A20))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Local App Cache", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(cacheSizeStr, color = Color.Gray, fontSize = 12.sp)
                        if (cacheDetailStr.isNotBlank()) {
                            Text(cacheDetailStr, color = Color(0xFF8A8A8A), fontSize = 11.sp)
                        }
                    }
                    if (isOptimizingCache) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = AppPalette,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(AppPalette)
                                .clickable {
                                    scope.launch {
                                        isOptimizingCache = true
                                        CacheStorageManager.clearTransientCache(context)
                                            .onSuccess {
                                                refreshCacheSummary()
                                                android.widget.Toast.makeText(context, "Transient media and artwork cache cleared", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                            .onFailure {
                                                AppDiagnostics.error("Settings", "Cache cleanup failed", it)
                                                android.widget.Toast.makeText(context, "Cache cleanup failed: ${it.message}", android.widget.Toast.LENGTH_LONG).show()
                                            }
                                        isOptimizingCache = false
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "Optimize",
                                color = Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            if (novaAcImportStatus != null) {
                Text(
                    novaAcImportStatus!!,
                    color = if (novaAcImportStatus!!.startsWith("Compatible")) Color(0xFF81C784) else Color(0xFFFFB74D),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { novaAcImportLauncher.launch(arrayOf("application/octet-stream", "application/*")) }
                    .background(Color(0xFF25252C))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Inspect NovaAc cache file", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Checks the cache-format and app-version header before use", color = Color(0xFFB3B3B3), fontSize = 11.sp)
                }
                Icon(Icons.Default.FolderOpen, contentDescription = "Inspect NovaAc cache file", tint = AppPalette)
            }
            }

            Spacer(Modifier.height(16.dp))

            CollapsibleSettingsSection(
                title = "Diagnostics",
                subtitle = "Launch, warning, error, and playback diagnostic records",
                expanded = diagnosticsSectionExpanded,
                onExpandedChange = { diagnosticsSectionExpanded = it },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { navController.navigate(com.music.spotui.ui.navigation.Routes.DeveloperConsole.route) }
                        .background(Color(0xFF1A1A20))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Open diagnostic console", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("Persistent app-private logs are active for this launch", color = Color(0xFFB3B3B3), fontSize = 12.sp)
                    }
                    Icon(Icons.Default.OpenInNew, contentDescription = "Open diagnostic console", tint = AppPalette)
                }
            }

            Spacer(Modifier.height(16.dp))

            CollapsibleSettingsSection(
                title = "Interface & Navigation",
                subtitle = "Choose navigation placement, UI density, and surface shape",
                expanded = interfaceSectionExpanded,
                onExpandedChange = { interfaceSectionExpanded = it },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(cornerRadiusDp.dp))
                        .background(Color(0xFF1A1A20))
                        .padding(14.dp),
                ) {
                    Text("Navigation layout", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        com.music.spotui.data.preferences.NavigationMode.entries.forEach { mode ->
                            val selected = navigationMode == mode
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (selected) AppPalette else Color.White.copy(alpha = 0.08f))
                                    .clickable {
                                        navigationMode = mode
                                        com.music.spotui.data.preferences.setNavigationMode(context, mode)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text(mode.label, color = if (selected) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(navigationMode.detail, color = Color(0xFFB3B3B3), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Compact UI", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("Smaller navigation controls and tighter spacing", color = Color.Gray, fontSize = 12.sp)
                        }
                        Switch(
                            checked = compactUi,
                            onCheckedChange = {
                                compactUi = it
                                com.music.spotui.data.preferences.setCompactUiEnabled(context, it)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = AppPalette),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Corner radius", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("${cornerRadiusDp.toInt()} dp", color = AppPalette, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = cornerRadiusDp,
                        onValueChange = {
                            cornerRadiusDp = it
                            com.music.spotui.data.preferences.setCornerRadiusDp(context, it)
                        },
                        valueRange = 0f..48f,
                        steps = 47,
                        colors = SliderDefaults.colors(activeTrackColor = AppPalette, thumbColor = AppPalette),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(cornerRadiusDp.dp))
                            .background(Color(0xFF25252C))
                            .padding(horizontal = 12.dp, vertical = if (compactUi) 8.dp else 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Live shape preview", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(if (compactUi) "Compact" else "Comfortable", color = AppPalette, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            CollapsibleSettingsSection(
                title = "Theme Studio",
                subtitle = "Material 3 expressive presets, JSON themes, and image-derived palettes",
                expanded = themeSectionExpanded,
                onExpandedChange = { themeSectionExpanded = it },
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(cornerRadiusDp.dp)).background(Color(0xFF1A1A20)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Material 3 expressive presets", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Every preset updates the app-wide Material color scheme and existing Spotui palette surfaces immediately.", color = Color(0xFFB3B3B3), fontSize = 11.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        com.music.spotui.ui.theme.ExpressiveThemePreset.entries.forEach { preset ->
                            val selected = activeThemePreset == preset
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(20.dp))
                                    .background(if (selected) AppPalette else Color.White.copy(alpha = 0.08f))
                                    .clickable {
                                        when (preset) {
                                            com.music.spotui.ui.theme.ExpressiveThemePreset.CUSTOM_JSON -> themeJsonLauncher.launch(arrayOf("application/json", "text/json", "text/plain"))
                                            com.music.spotui.ui.theme.ExpressiveThemePreset.IMAGE_DERIVED -> themeImageLauncher.launch(arrayOf("image/*"))
                                            else -> {
                                                activeThemePreset = preset
                                                com.music.spotui.ui.theme.ThemePreferences.setPreset(context, preset)
                                                themeStatus = "Applied ${preset.label}."
                                            }
                                        }
                                    }.padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text(preset.label, color = if (selected) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    val activeSpec = com.music.spotui.ui.theme.ThemePreferences.currentSpec(context)
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(activeSpec.primaryArgb).copy(alpha = 0.22f)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(activeSpec.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(activeSpec.sourceDescription, color = Color(0xFFD2D6E0), fontSize = 11.sp)
                        }
                        Box(Modifier.size(28.dp).clip(RoundedCornerShape(14.dp)).background(Color(activeSpec.primaryArgb)))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { themeJsonLauncher.launch(arrayOf("application/json", "text/json", "text/plain")) }) { Text("Import JSON", color = AppPalette) }
                        TextButton(onClick = { themeImageLauncher.launch(arrayOf("image/*")) }) { Text("Use image palette", color = AppPalette) }
                        TextButton(onClick = {
                            com.music.spotui.ui.theme.ThemePreferences.resetCustomTheme(context)
                            activeThemePreset = com.music.spotui.ui.theme.ExpressiveThemePreset.SPOTUI_BLUE
                            themeStatus = "Restored Spotui Blue."
                        }) { Text("Reset", color = AppPalette) }
                    }
                    Text("JSON schema: name, primary, optional secondary, tertiary, background, surface, onPrimary, onBackground, and dark. Colors must be #RRGGBB or #AARRGGBB. Image palettes are calculated locally and never uploaded.", color = Color(0xFF9FA9BC), fontSize = 10.sp)
                    if (themeStatus.isNotBlank()) Text(themeStatus, color = AppPalette, fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            CollapsibleSettingsSection(
                title = "Accessibility",
                subtitle = "Visual readability, motion comfort, touch targets, and audio access",
                expanded = accessibilitySectionExpanded,
                onExpandedChange = { accessibilitySectionExpanded = it },
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(cornerRadiusDp.dp)).background(Color(0xFF1A1A20)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AccessibilitySwitchRow("High contrast", "Strengthens Material text, surface, and outline contrast.", accessibilityHighContrast) {
                        accessibilityHighContrast = it
                        com.music.spotui.ui.theme.ThemePreferences.setHighContrast(context, it)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Text scale", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("${(accessibilityTextScale * 100).toInt()}%", color = AppPalette, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = accessibilityTextScale,
                        onValueChange = { accessibilityTextScale = it },
                        onValueChangeFinished = { com.music.spotui.ui.theme.ThemePreferences.setTextScale(context, accessibilityTextScale) },
                        valueRange = 0.85f..1.45f,
                        steps = 11,
                        colors = SliderDefaults.colors(activeTrackColor = AppPalette, thumbColor = AppPalette),
                    )
                    AccessibilitySwitchRow("Reduce motion", "Uses gentler expand/collapse behavior for animated settings sections.", accessibilityReduceMotion) {
                        accessibilityReduceMotion = it
                        com.music.spotui.ui.theme.ThemePreferences.setReduceMotion(context, it)
                    }
                    AccessibilitySwitchRow("Larger touch targets", "Expands interactive settings rows for easier tapping.", accessibilityTouchTargets) {
                        accessibilityTouchTargets = it
                        com.music.spotui.ui.theme.ThemePreferences.setMinimumTouchTarget(context, it)
                    }
                    AccessibilitySwitchRow("Mono audio mix", "Mixes stereo channels to mono in the app’s PCM accessibility processor.", accessibilityMonoAudio) {
                        accessibilityMonoAudio = it
                        com.music.spotui.ui.theme.ThemePreferences.setMonoAudio(context, it)
                        SongPlayer.refreshAudioEffects(context)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Stereo balance", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(if (accessibilityBalance == 0f) "Centered" else if (accessibilityBalance < 0f) "Left ${(-accessibilityBalance * 100).toInt()}%" else "Right ${(accessibilityBalance * 100).toInt()}%", color = AppPalette, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = accessibilityBalance,
                        onValueChange = { accessibilityBalance = it },
                        onValueChangeFinished = {
                            com.music.spotui.ui.theme.ThemePreferences.setAudioBalance(context, accessibilityBalance)
                            SongPlayer.refreshAudioEffects(context)
                        },
                        valueRange = -1f..1f,
                        steps = 19,
                        colors = SliderDefaults.colors(activeTrackColor = AppPalette, thumbColor = AppPalette),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionTitle("Background playback")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        batteryOptLauncher.launch(BatteryOptimizationHelper.buildAppSettingsIntent(context))
                    }
                    .background(Color(0xFF1A1A20))
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Battery optimization", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (batteryOptExempt) "Exempt — app won't be killed" else "Not exempt — tap to change",
                        color = if (batteryOptExempt) Color(0xFF81C784) else Color(0xFFB3B3B3),
                        fontSize = 12.sp,
                    )
                }
                if (batteryOptExempt) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Enabled",
                        tint = AppPalette,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            BatteryOptimizationHelper.getManufacturerTips()?.let { (name, tip) ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Tip for $name",
                    color = Color(0xFFB3B3B3),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = tip,
                    color = Color(0xFF808080),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1A1A20))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle("Link handling")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        if (isDefaultLinkHandler) {
                            DefaultLinkHelper.openSpotuiDefaultSettings(context)
                        } else {
                            showDefaultGuide = true
                        }
                    }
                    .background(Color(0xFF1A1A20))
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Open Spotify links by default", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (isDefaultLinkHandler) "Spotui handles Spotify URLs by default" else "Not default — tap to open setup guide",
                        color = if (isDefaultLinkHandler) Color(0xFF81C784) else Color(0xFFB3B3B3),
                        fontSize = 12.sp,
                    )
                }
                if (isDefaultLinkHandler) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Enabled",
                        tint = AppPalette,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = "Open Settings",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            SectionTitle("Streaming engine")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1A1A20))
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Secure HTTPS audio resolver", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (spotifyWebPlayback) "Standby fallback when Spotify Web cannot start" else "Active: resolves direct HTTPS streams with quality fallback",
                        color = Color(0xFFB3B3B3),
                        fontSize = 12.sp,
                    )
                }
                Icon(Icons.Default.Check, contentDescription = "HTTPS resolver active", tint = AppPalette, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(8.dp))
            SettingsSwitchRow(
                title = "Spotify Web playback engine",
                subtitle = "Use your logged-in Spotify Web player when available; secure HTTPS resolver remains the fallback",
                checked = spotifyWebPlayback,
            ) {
                spotifyWebPlayback = it
                setWebPlaybackEnabled(context, it)
            }
            SettingsSwitchRow(
                title = "YouTube Music web companion",
                subtitle = "Optional: sign in on the official YouTube Music site and browse/play your playlists in the in-app web companion",
                checked = youtubeMusicWeb,
            ) {
                youtubeMusicWeb = it
                com.music.spotui.data.preferences.setYoutubeMusicWebEnabled(context, it)
            }
            if (youtubeMusicWeb) {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF1A1A20))
                        .clickable { context.startActivity(Intent(context, com.music.spotui.ui.screens.YouTubeMusicWebActivity::class.java)) }
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Open YouTube Music playlists", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("Sign in on the official site, then browse and play your YouTube Music library.", color = Color(0xFFB3B3B3), fontSize = 12.sp)
                    }
                    Icon(Icons.Default.OpenInNew, contentDescription = "Open YouTube Music", tint = AppPalette, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF1A1A20)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("YouTube playlist sync", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("Optional official Google OAuth import. It syncs playlist metadata and video references into the collapsible YouTube Music library section; it does not download protected media.", color = Color(0xFFB3B3B3), fontSize = 11.sp)
                androidx.compose.material3.OutlinedTextField(
                    value = youtubeOAuthClientId,
                    onValueChange = { youtubeOAuthClientId = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Google OAuth Android client ID") },
                    placeholder = { Text("…apps.googleusercontent.com") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        com.music.spotui.data.youtube.YouTubeMusicSync.setClientId(context, youtubeOAuthClientId)
                        youtubeSyncStatus = com.music.spotui.data.youtube.YouTubeMusicSync.status(context)
                    }) { Text("Save client ID", color = AppPalette) }
                    TextButton(
                        enabled = youtubeOAuthClientId.trim().isNotBlank() && !youtubeSyncBusy,
                        onClick = {
                            // Match the direct sign-in experience: save the currently entered
                            // client ID, then immediately launch the user-visible Google consent flow.
                            youtubeSyncBusy = true
                            com.music.spotui.data.youtube.YouTubeMusicSync.setClientId(context, youtubeOAuthClientId.trim())
                            youtubeSyncStatus = com.music.spotui.data.youtube.YouTubeMusicSync.status(context)
                            com.music.spotui.data.youtube.YouTubeMusicSync.authorizationUrl(context)
                                .onSuccess { url ->
                                    youtubeSyncBusy = false
                                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                                }
                                .onFailure { error ->
                                    youtubeSyncBusy = false
                                    youtubeSyncStatus = com.music.spotui.data.youtube.YouTubeMusicSync.status(context)
                                    themeStatus = error.message ?: "Unable to begin Google authorization."
                                }
                        },
                    ) { Text(if (youtubeSyncBusy) "Opening Google…" else if (youtubeSyncStatus.connected) "Reconnect Google" else "Connect Google", color = AppPalette) }
                }
                Text(youtubeSyncStatus.detail, color = if (youtubeSyncStatus.connected) Color(0xFF81C784) else Color(0xFFB3B3B3), fontSize = 11.sp)
                if (!youtubeSyncStatus.configured) {
                    Text("Create an Android OAuth client in Google Cloud, add com.music.spotui:/oauth2redirect as its redirect URI, paste the client ID above, then tap Connect Google.", color = Color(0xFF9DA7B7), fontSize = 10.sp)
                }
                if (youtubeSyncStatus.connected) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            enabled = !youtubeSyncBusy,
                            onClick = {
                                youtubeSyncBusy = true
                                scope.launch {
                                    com.music.spotui.data.youtube.YouTubeMusicSync.syncNow(context)
                                    youtubeSyncStatus = com.music.spotui.data.youtube.YouTubeMusicSync.status(context)
                                    youtubeSyncBusy = false
                                }
                            },
                        ) { Text(if (youtubeSyncBusy) "Syncing…" else "Sync now", color = AppPalette) }
                        TextButton(onClick = {
                            com.music.spotui.data.youtube.YouTubeMusicSync.revoke(context)
                            youtubeSyncStatus = com.music.spotui.data.youtube.YouTubeMusicSync.status(context)
                        }) { Text("Revoke", color = Color(0xFFFF8B94)) }
                    }
                    SettingsSwitchRow(
                        title = "Automatic YouTube playlist sync",
                        subtitle = "Runs every 15 minutes when connected and a network is available. Last sync: ${youtubeSyncStatus.lastSync}",
                        checked = youtubeSyncStatus.autoSync,
                    ) {
                        com.music.spotui.data.youtube.YouTubeMusicSync.setAutoSync(context, it)
                        youtubeSyncStatus = com.music.spotui.data.youtube.YouTubeMusicSync.status(context)
                    }
                }
            }

            SettingsSwitchRow(
                title = "Player video preview",
                subtitle = "Show opt-in YouTube video preview controls in the player; audio playback remains separate",
                checked = playerVideoPreview,
            ) {
                playerVideoPreview = it
                com.music.spotui.data.preferences.setPlayerVideoPreviewEnabled(context, it)
            }
            SettingsSwitchRow(
                title = "Preload next HTTPS stream",
                subtitle = "Warm the next track in the app cache for faster transitions",
                checked = preloadNextStream,
            ) {
                preloadNextStream = it
                setPreloadEnabled(context, it)
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle("Audio quality")
            QualityPicker(
                title = "Streaming over Wi-Fi",
                selected = wifiQ,
                showFlacWarning = wifiQ == StreamQuality.LOSSLESS,
                losslessTimeout = wifiLosslessTimeout,
                onSelectTimeout = { wifiLosslessTimeout = it; setWifiLosslessTimeout(context, it) },
            ) { wifiQ = it; setWifiQuality(context, it) }

            QualityPicker(
                title = "Streaming over cellular",
                selected = cellQ,
                showFlacWarning = cellQ == StreamQuality.LOSSLESS,
                losslessTimeout = cellLosslessTimeout,
                onSelectTimeout = { cellLosslessTimeout = it; setCellularLosslessTimeout(context, it) },
            ) { cellQ = it; setCellularQuality(context, it) }

            QualityPicker(
                title = "Download quality",
                selected = dlQ,
                losslessTimeout = dlLosslessTimeout,
                onSelectTimeout = { dlLosslessTimeout = it; setDownloadLosslessTimeout(context, it) },
            ) { dlQ = it; setDownloadQuality(context, it) }

            var losslessStatusSummary by remember { mutableStateOf("Checking lossless mirrors…") }
            var losslessProviderOrder by remember { mutableStateOf(com.music.spotui.data.preferences.ProviderRoutingPreferences.losslessOrder(context)) }
            var strictFullLengthLossless by remember { mutableStateOf(com.music.spotui.data.preferences.ProviderRoutingPreferences.preferFullLength(context)) }
            LaunchedEffect(Unit) {
                providerStatuses = com.metrolist.spotify.SpotiFlac.getProviderStatuses()
                val upCount = providerStatuses.count { it.isUp && !it.isCooldown }
                losslessStatusSummary = "$upCount/5 online • Tap to inspect providers"
            }

            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        scope.launch {
                            isRefreshingStatuses = true
                            providerStatuses = com.metrolist.spotify.SpotiFlac.getProviderStatuses()
                            isRefreshingStatuses = false
                            showProviderStatusDialog = true
                        }
                    }
                    .background(Color(0xFF1E1E24))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Lossless Provider Status", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(losslessStatusSummary, color = Color(0xFFB3B3B3), fontSize = 12.sp)
                }
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Inspect Status",
                    tint = AppPalette,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.height(10.dp))
            Text("Lossless route priority", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("The resolver launches eligible providers in this sequence; unavailable or cooldown sources are skipped.", color = Color(0xFFB3B3B3), fontSize = 11.sp)
            com.music.spotui.data.preferences.LosslessProviderRoute.entries.forEachIndexed { index, route ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${index + 1}", color = AppPalette, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(route.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(route.detail, color = Color(0xFF9FA9BC), fontSize = 10.sp)
                    }
                    TextButton(
                        enabled = index > 0,
                        onClick = {
                            val reordered = losslessProviderOrder.toMutableList().also { list ->
                                val current = list.indexOf(route.id)
                                if (current > 0) java.util.Collections.swap(list, current, current - 1)
                            }
                            losslessProviderOrder = reordered
                            com.music.spotui.data.preferences.ProviderRoutingPreferences.setLosslessOrder(context, reordered)
                        },
                    ) { Text("Up", color = if (index > 0) AppPalette else Color.Gray, fontSize = 11.sp) }
                    TextButton(
                        enabled = index < losslessProviderOrder.lastIndex,
                        onClick = {
                            val reordered = losslessProviderOrder.toMutableList().also { list ->
                                val current = list.indexOf(route.id)
                                if (current >= 0 && current < list.lastIndex) java.util.Collections.swap(list, current, current + 1)
                            }
                            losslessProviderOrder = reordered
                            com.music.spotui.data.preferences.ProviderRoutingPreferences.setLosslessOrder(context, reordered)
                        },
                    ) { Text("Down", color = if (index < losslessProviderOrder.lastIndex) AppPalette else Color.Gray, fontSize = 11.sp) }
                }
            }
            SettingsSwitchRow(
                title = "Reject preview-length lossless sources",
                subtitle = "Skips manifest results shorter than 45 seconds before fallback.",
                checked = strictFullLengthLossless,
            ) {
                strictFullLengthLossless = it
                com.music.spotui.data.preferences.ProviderRoutingPreferences.setPreferFullLength(context, it)
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle("Matching")
            SettingsSwitchRow(
                title = "Allow video fallback",
                subtitle = "Use regular YouTube videos only after Music song results fail",
                checked = videoFallback,
            ) {
                videoFallback = it
                setVideoFallbackEnabled(context, it)
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle("Playback")
            SettingsSwitchRow(
                title = "Auto-play on startup",
                subtitle = "Resume playing the last track when the app opens",
                checked = autoPlay,
            ) {
                autoPlay = it
                setAutoPlayEnabled(context, it)
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle("Crossfade")
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Crossfade", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    if (crossfadeMs <= 0f) "Off" else "${(crossfadeMs / 1000f).let { String.format("%.0f", it) }}s",
                    color = if (crossfadeMs <= 0f) Color(0xFFB3B3B3) else AppPalette,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                "Blend the end of a song into the start of the next",
                color = Color(0xFFB3B3B3),
                fontSize = 13.sp,
            )
            Slider(
                value = crossfadeMs,
                onValueChange = { crossfadeMs = it },
                onValueChangeFinished = { setCrossfadeMs(context, crossfadeMs.toInt()) },
                valueRange = 0f..CROSSFADE_MAX_MS.toFloat(),
                steps = (CROSSFADE_MAX_MS / 1000) - 1, // 1-second stops
                colors = SliderDefaults.colors(
                    thumbColor = AppPalette,
                    activeTrackColor = AppPalette,
                    inactiveTrackColor = Color(0xFF333333),
                ),
            )
            SettingsSwitchRow(
                title = "Gapless playback",
                subtitle = if (crossfadeMs > 0f) "Crossfade currently takes priority; this is used whenever Crossfade is Off" else "Prepares the next queue stream and uses a short equal-power handoff to avoid a silence gap",
                checked = gaplessPlayback,
            ) {
                gaplessPlayback = it
                setGaplessPlaybackEnabled(context, it)
            }
            Spacer(Modifier.height(12.dp))
            SectionTitle("Updates")
            Text(
                "Update source repository",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "GitHub repo URL used to check for new versions",
                color = Color(0xFFB3B3B3),
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = updateRepoUrl,
                onValueChange = {
                    updateRepoUrl = it
                    setUpdateRepoUrl(context, it)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = AppPalette,
                    unfocusedBorderColor = Color(0xFF333333),
                    cursorColor = AppPalette,
                    focusedPlaceholderColor = Color(0xFF666666),
                    unfocusedPlaceholderColor = Color(0xFF666666),
                ),
                placeholder = { Text("https://github.com/Owner/Repo") },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Reset to default",
                        tint = if (updateRepoUrl != DEFAULT_UPDATE_REPO_URL) AppPalette else Color(0xFF444444),
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .clickable {
                                updateRepoUrl = DEFAULT_UPDATE_REPO_URL
                                resetUpdateRepoUrl(context)
                            }
                    )
                },
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { navController.navigate(com.music.spotui.ui.navigation.Routes.UpdateRoadmap.route) }
                    .background(Color(0xFF1A1A20))
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Roadmap & Update Master Plan", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text("View active v1.4.5 sprints, future milestone to-dos (v1.4.6–v1.5.0), and pipeline topology", color = Color(0xFFB3B3B3), fontSize = 12.sp)
                }
                Icon(
                    imageVector = Icons.Filled.OpenInNew,
                    contentDescription = "View",
                    tint = AppPalette,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            SectionTitle("Backup & Restore")

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = !isBackingUp) {
                            if (backupDirUri.isNullOrBlank()) {
                                dirPickerLauncher.launch(null)
                            } else {
                                isBackingUp = true
                                scope.launch {
                                    val (success, message) = BackupHelper.performManualBackup(context)
                                    isBackingUp = false
                                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                        .background(Color(0xFF1A1A20))
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Back Up Now", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            when {
                                isBackingUp -> "Creating backup in background…"
                                backupDirUri.isNullOrBlank() -> "Tap to choose folder & back up"
                                else -> "Folder: $folderName"
                            },
                            color = if (backupDirUri.isNullOrBlank()) Color(0xFFFFB74D) else Color(0xFFB3B3B3),
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                    }
                    if (isBackingUp) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = AppPalette,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Save,
                            contentDescription = "Back Up Now",
                            tint = AppPalette,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                if (!backupDirUri.isNullOrBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1A1A20))
                            .clickable(enabled = !isBackingUp) { dirPickerLauncher.launch(null) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Folder,
                            contentDescription = "Change Backup Folder",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = !isRestoring) {
                        restoreFileLauncher.launch(arrayOf("application/json", "*/*"))
                    }
                    .background(Color(0xFF1A1A20))
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Restore from File", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (isRestoring) "Restoring backup in background…" else "Import playlists and settings from a Spotui backup file",
                        color = Color(0xFFB3B3B3),
                        fontSize = 12.sp,
                    )
                }
                if (isRestoring) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = AppPalette,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = "Restore",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            SettingsSwitchRow(
                title = "Automatic backup",
                subtitle = if (backupDirUri.isNullOrBlank()) "Automatically backs up settings and playlists when app opens" else "Auto-backup saved to $folderName",
                checked = isAutoBackup,
            ) { enabled ->
                if (enabled && backupDirUri.isNullOrBlank()) {
                    dirPickerLauncher.launch(null)
                } else {
                    isAutoBackup = enabled
                    BackupPref.setAutoBackupEnabled(context, enabled)
                    if (enabled) {
                        scope.launch { BackupHelper.performAutoBackup(context) }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle("Developer Options & Diagnostics")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E1E24))
                    .clickable {
                        navController.navigate(com.music.spotui.ui.navigation.Routes.DeveloperConsole.route)
                    }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Developer Console & Telemetry",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Logcat, playback watchdog, stream sanitizer & collision monitor",
                        color = Color(0xFFB3B3B3),
                        fontSize = 12.sp
                    )
                }
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = "Open Console",
                    tint = AppPalette,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle("Account")
            Text(
                text = "Log out",
                color = Color(0xFFE57373),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        com.music.spotui.data.api.SpotifySession.setSpDc(context, "")
                        com.music.spotui.data.api.Api.HomeCache.clear()
                        navController.navigate(com.music.spotui.ui.navigation.Routes.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                    .padding(vertical = 14.dp)
            )
            Spacer(Modifier.height(40.dp))
        }

        if (showDefaultGuide) {
            DefaultAppPrompt(
                forceShow = true,
                onDismiss = {
                    showDefaultGuide = false
                    isDefaultLinkHandler = DefaultLinkHelper.isAppDefaultLinkHandler(context)
                }
            )
        }

        if (showProviderStatusDialog) {
            AlertDialog(
                onDismissRequest = { showProviderStatusDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Lossless Provider Status", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (isRefreshingStatuses) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = AppPalette)
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refresh",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable {
                                        scope.launch {
                                            isRefreshingStatuses = true
                                            com.metrolist.spotify.SpotiFlac.clearStatusCache()
                                            providerStatuses = com.metrolist.spotify.SpotiFlac.getProviderStatuses()
                                            isRefreshingStatuses = false
                                        }
                                    }
                            )
                        }
                    }
                },
                text = {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            "Real-time availability of lossless audio mirrors (Tidal, Qobuz, Amazon, Deezer, Monochrome). Playback automatically resolves from the fastest available online provider.",
                            color = Color(0xFFB3B3B3),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        providerStatuses.forEach { status ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1A1A20))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(status.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text(status.detail, color = Color(0xFF999999), fontSize = 11.sp)
                                }
                                Spacer(Modifier.width(6.dp))
                                val (statusText, statusBg, statusFg) = when {
                                    status.isCooldown -> Triple("Cooldown (${status.cooldownRemainingSec}s)", Color(0x33FFB74D), Color(0xFFFFB74D))
                                    status.isUp -> Triple("Online", Color(0x3381C784), Color(0xFF81C784))
                                    else -> Triple("Offline", Color(0x33E57373), Color(0xFFE57373))
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(statusBg)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(statusText, color = statusFg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showProviderStatusDialog = false }) {
                        Text("Close", color = AppPalette)
                    }
                },
                containerColor = Color(0xFF141418),
                titleContentColor = Color.White,
                textContentColor = Color.White,
            )
        }
    }
}

@Composable
private fun CollapsibleSettingsSection(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    val accessibilityContext = LocalContext.current
    val reduceMotion = com.music.spotui.ui.theme.ThemePreferences.reduceMotion(accessibilityContext)
    val expandedPadding = if (com.music.spotui.ui.theme.ThemePreferences.minimumTouchTarget(accessibilityContext)) 20.dp else 14.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF15151A)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpandedChange(!expanded) }
                .padding(horizontal = 14.dp, vertical = expandedPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Color(0xFFB3B3B3), fontSize = 12.sp)
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                tint = AppPalette,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = if (reduceMotion) androidx.compose.animation.EnterTransition.None else expandVertically(),
            exit = if (reduceMotion) androidx.compose.animation.ExitTransition.None else shrinkVertically(),
        ) {
            Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = AppPalette,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun AccessibilitySwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) = SettingsSwitchRow(title, subtitle, checked, onCheckedChange)

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val targetContext = LocalContext.current
    val verticalPadding = if (com.music.spotui.ui.theme.ThemePreferences.minimumTouchTarget(targetContext)) 12.dp else 8.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color(0xFFB3B3B3), fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AppPalette,
                uncheckedThumbColor = Color(0xFFB3B3B3),
                uncheckedTrackColor = Color(0xFF333333),
            ),
        )
    }
}

@Composable
private fun QualityPicker(
    title: String,
    selected: StreamQuality,
    showFlacWarning: Boolean = false,
    losslessTimeout: LosslessTimeout? = null,
    onSelectTimeout: ((LosslessTimeout) -> Unit)? = null,
    onSelect: (StreamQuality) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        StreamQuality.values().forEach { q ->
            val isSel = q == selected
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelect(q) }
                        .background(if (isSel) Color(0xFF1A1A20) else Color.Transparent)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(q.label, color = Color.White, fontSize = 15.sp)
                        Text(q.detail, color = Color(0xFFB3B3B3), fontSize = 12.sp)
                    }
                    if (isSel) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Selected",
                            tint = AppPalette,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (isSel && q == StreamQuality.LOSSLESS) {
                    if (showFlacWarning) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Lossless streams are resolved per-session and aren't cached to disk, which may add a few seconds of loading time when playing music.",
                            color = Color(0xFFFFB74D),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x33FFB74D))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                    if (losslessTimeout != null && onSelectTimeout != null) {
                        Spacer(Modifier.height(8.dp))
                        Text("Lossless resolution wait time", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LosslessTimeout.values().forEach { t ->
                                val selectedT = t == losslessTimeout
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onSelectTimeout(t) }
                                        .background(if (selectedT) AppPalette.copy(alpha = 0.2f) else Color(0xFF1E1E24))
                                        .border(1.dp, if (selectedT) AppPalette else Color.Transparent, RoundedCornerShape(8.dp))
                                        .padding(vertical = 8.dp, horizontal = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(t.label, color = if (selectedT) Color.White else Color(0xFFB3B3B3), fontSize = 13.sp, fontWeight = if (selectedT) FontWeight.Bold else FontWeight.Normal)
                                        Text(t.detail, color = Color(0xFF999999), fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}
