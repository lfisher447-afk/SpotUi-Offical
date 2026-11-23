package com.music.spotui.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.music.spotui.data.preferences.isAutoDownloadUpdatesEnabled
import com.music.spotui.data.preferences.setAutoDownloadUpdatesEnabled
import com.music.spotui.data.preferences.setUpdateRepoUrl
import com.music.spotui.data.preferences.resetUpdateRepoUrl
import com.music.spotui.data.preferences.DEFAULT_UPDATE_REPO_URL
import com.music.spotui.data.update.UpdateDownloader
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.network.AuthSessionStore
import com.metrolist.innertube.network.DnsOverHttpsPreset
import com.music.spotui.BuildConfig
import com.music.spotui.data.preferences.getDnsPreset
import com.music.spotui.data.preferences.getPlaybackPitchPref
import com.music.spotui.data.preferences.getPlaybackSpeedPref
import com.music.spotui.data.preferences.getProxyUrls
import com.music.spotui.data.preferences.isIpRotationEnabled
import com.music.spotui.data.preferences.isSkipSilenceEnabled
import com.music.spotui.data.preferences.isStreamBypassProxyEnabled
import com.music.spotui.data.preferences.isWebClientPoTokenEnabled
import com.music.spotui.data.preferences.setDnsPreset
import com.music.spotui.data.preferences.setIpRotationEnabled
import com.music.spotui.data.preferences.setPlaybackPitchPref
import com.music.spotui.data.preferences.setPlaybackSpeedPref
import com.music.spotui.data.preferences.setProxyUrls
import com.music.spotui.data.preferences.setSkipSilenceEnabled
import com.music.spotui.data.preferences.setStreamBypassProxyEnabled
import com.music.spotui.data.preferences.setWebClientPoTokenEnabled
import com.music.spotui.data.preferences.setYoutubeCookie
import com.music.spotui.data.preferences.isAdvancedSettingsEnabled
import com.music.spotui.data.preferences.setAdvancedSettingsEnabled
import com.music.spotui.data.update.UpdateChecker
import com.music.spotui.ui.components.DefaultAppPrompt
import com.music.spotui.util.DefaultLinkHelper
import com.music.spotui.util.PermissionHelper
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.AppPalette
import com.music.spotui.audio.UiSoundEngine
import com.music.spotui.data.cache.CacheStorageManager
import com.music.spotui.data.export.NovaAcExportManager
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.components.PatchNotesDialog
import com.music.spotui.ui.screens.settings.AudioDspSection
import com.music.spotui.ui.screens.settings.LosslessProviderSection
import com.music.spotui.ui.screens.settings.ModuleLibsSettingsSection
import com.music.spotui.ui.screens.settings.PlaybackAndToolsSection
import com.music.spotui.ui.screens.settings.UiSoundAndStyleSection
import com.music.spotui.accessibility.AccessibilityHelper
import com.music.spotui.data.preferences.FloaterHapticStrength
import com.music.spotui.data.preferences.floaterSettingsUpdates
import com.music.spotui.data.preferences.getFloaterAutoHideMs
import com.music.spotui.data.preferences.getFloaterHapticStrength
import com.music.spotui.data.preferences.isFloaterEqAnd3dEnabled
import com.music.spotui.data.preferences.isFloaterHapticsEnabled
import com.music.spotui.data.preferences.isFloaterStartExpanded
import com.music.spotui.data.preferences.resetFloaterGeometry
import com.music.spotui.data.preferences.setFloaterAutoHideMs
import com.music.spotui.data.preferences.setFloaterEqAnd3dEnabled
import com.music.spotui.data.preferences.setFloaterHapticStrength
import com.music.spotui.data.preferences.setFloaterHapticsEnabled
import com.music.spotui.data.preferences.setFloaterStartExpanded
import com.music.spotui.util.AppDiagnostics

private enum class SettingsSideTab(
    val shortLabel: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val icon: ImageVector,
) {
    STYLE("Style", "Expressive Style & UI", "Themes, SongPlay shapes, tactile chimes & AI", "M3E", Icons.Rounded.Palette),
    AUDIO("Audio", "3D Studio & Quality", "7-Band EQ, 3D Spatial Orbit, Lossless & Crossfade", "3D", Icons.Rounded.GraphicEq),
    FLOATER("Floater", "Accessibility Floater", "In-app volume, corner resize, haptics & EQ/3D hook", "A11Y", Icons.Rounded.PictureInPictureAlt),
    MODULES("Modules", "16 Library Modules", "Toggle & verify Spotify, InnerTube, Lyrics & Shazam", "16", Icons.Rounded.Extension),
    TURBO("Turbo", "MrBean & Storage", "8x HTTP/2 slices, connection pool & NovaAc cache", "8x", Icons.Rounded.Bolt),
    NETWORK("Network", "DNS, Auth & Sync", "DoH resolver, PoToken, proxy pool & YTM sync", "DoH", Icons.Rounded.Public),
    SYSTEM("System", "System, Updates & Account", "Permissions, battery guard, v2.5.3 updater & backup", "OS", Icons.Rounded.Tune),
    LOGS("Logs", "Logger & Telemetry", "2000-entry 8-channel developer diagnostics console", "LOG", Icons.Rounded.Terminal),
    ALL("All", "All SpotUI Settings", "Full scrollable configuration dashboard", "ALL", Icons.Rounded.Apps),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current

    var activeSideTab by rememberSaveable { mutableStateOf(SettingsSideTab.STYLE) }
    val rightPaneScrollState = rememberScrollState()
    val railScrollState = rememberScrollState()
    fun showTab(vararg tabs: SettingsSideTab): Boolean =
        activeSideTab == SettingsSideTab.ALL || activeSideTab in tabs

    var advancedSettingsEnabled by remember { mutableStateOf(isAdvancedSettingsEnabled(context)) }
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
    var accessibilityMiniPlayerEnabled by remember { mutableStateOf(AccessibilityHelper.isServiceEnabled(context)) }
    var floaterEq3dHook by remember { mutableStateOf(isFloaterEqAnd3dEnabled(context)) }
    var floaterStartExpanded by remember { mutableStateOf(isFloaterStartExpanded(context)) }
    var floaterHaptics by remember { mutableStateOf(isFloaterHapticsEnabled(context)) }
    var floaterHapticStrength by remember { mutableStateOf(getFloaterHapticStrength(context)) }
    var floaterAutoHideMs by remember { mutableStateOf(getFloaterAutoHideMs(context)) }
    val floaterPrefRevision by floaterSettingsUpdates.collectAsStateWithLifecycle()
    LaunchedEffect(floaterPrefRevision) {
        floaterEq3dHook = isFloaterEqAnd3dEnabled(context)
        floaterStartExpanded = isFloaterStartExpanded(context)
        floaterHaptics = isFloaterHapticsEnabled(context)
        floaterHapticStrength = getFloaterHapticStrength(context)
        floaterAutoHideMs = getFloaterAutoHideMs(context)
    }
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
    var mrbeanConcurrentDownloads by remember { mutableStateOf(org.eclipse.Mrbean.client.MrbeanNetworkSettings.maxConcurrentDownloads(context).toFloat()) }
    var mrbeanParallelChunks by remember { mutableStateOf(org.eclipse.Mrbean.client.MrbeanNetworkSettings.parallelChunksPerFile(context).toFloat()) }
    var mrbeanIoBufferKb by remember { mutableStateOf(org.eclipse.Mrbean.client.MrbeanNetworkSettings.ioBufferKb(context)) }
    var mrbeanHttp2 by remember { mutableStateOf(org.eclipse.Mrbean.client.MrbeanNetworkSettings.http2Multiplexing(context)) }
    var mrbeanPrefetchAsync by remember { mutableStateOf(org.eclipse.Mrbean.client.MrbeanNetworkSettings.prefetchMetadataInParallel(context)) }
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
    var geminiFreeMode by remember { mutableStateOf(com.music.spotui.data.preferences.isGeminiFreeMode(context)) }
    var geminiApiKey by remember { mutableStateOf(com.music.spotui.data.preferences.getGeminiApiKey(context)) }
    var geminiModel by remember { mutableStateOf(com.music.spotui.data.preferences.getGeminiModel(context)) }
    var aiSectionExpanded by rememberSaveable { mutableStateOf(false) }
    var explicitAllowed by remember { mutableStateOf(com.music.spotui.data.preferences.isExplicitContentAllowed(context)) }
    var preferExplicit by remember { mutableStateOf(com.music.spotui.data.preferences.isPreferExplicitVersions(context)) }
    var explicitBadgeVisible by remember { mutableStateOf(com.music.spotui.data.preferences.isExplicitBadgeVisible(context)) }
    var oneUiHeader by remember { mutableStateOf(com.music.spotui.data.preferences.isOneUiHeaderEnabled(context)) }
    var oneUiSquircle by remember { mutableStateOf(com.music.spotui.data.preferences.isOneUiSquircleIcons(context)) }
    var explicitSectionExpanded by rememberSaveable { mutableStateOf(true) }
    var oneUiSectionExpanded by rememberSaveable { mutableStateOf(true) }
    var networkSessionSectionExpanded by rememberSaveable { mutableStateOf(false) }
    var permissionsSectionExpanded by rememberSaveable { mutableStateOf(false) }

    val authState by AuthSessionStore.authStateFlow.collectAsStateWithLifecycle()
    var dnsPreset by remember { mutableStateOf(getDnsPreset(context)) }
    var webClientPoToken by remember { mutableStateOf(isWebClientPoTokenEnabled(context)) }
    var proxyUrlsInput by remember { mutableStateOf(getProxyUrls(context)) }
    var ipRotationEnabled by remember { mutableStateOf(isIpRotationEnabled(context)) }
    var streamBypassProxy by remember { mutableStateOf(isStreamBypassProxyEnabled(context)) }
    var activeProxyCount by remember { mutableStateOf(AuthSessionStore.activeProxyCount()) }
    var proxyStatusNote by remember { mutableStateOf("") }

    var hasNotifPerm by remember { mutableStateOf(PermissionHelper.hasNotificationPermission(context)) }
    var hasMediaPerm by remember { mutableStateOf(PermissionHelper.hasMediaReadPermission(context)) }
    var hasBluetoothPerm by remember { mutableStateOf(PermissionHelper.hasBluetoothConnectPermission(context)) }
    var exitMiniNotifEnabled by remember { mutableStateOf(com.music.spotui.data.preferences.isBackgroundExitNotificationEnabled(context)) }
    var playbackSpeed by remember { mutableStateOf(getPlaybackSpeedPref(context)) }
    var playbackPitch by remember { mutableStateOf(getPlaybackPitchPref(context)) }
    var skipSilence by remember { mutableStateOf(isSkipSilenceEnabled(context)) }
    var ytSessionVerifyBusy by remember { mutableStateOf(false) }
    var ytSessionSummary by remember { mutableStateOf("") }
    var manualUpdateBusy by remember { mutableStateOf(false) }
    var manualUpdateStatus by remember { mutableStateOf("") }
    var manualUpdateInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var autoDownloadUpdates by remember { mutableStateOf(isAutoDownloadUpdatesEnabled(context)) }
    val updateDownloadState by UpdateDownloader.state.collectAsStateWithLifecycle()
    val pendingConfirmIntent by UpdateDownloader.pendingConfirmIntent.collectAsStateWithLifecycle()
    var settingsAutoOpenedVersion by remember { mutableStateOf<String?>(null) }
    var settingsInstallPermNeeded by remember { mutableStateOf(false) }
    var settingsInstallError by remember { mutableStateOf<String?>(null) }
    var showPatchNotesModal by remember { mutableStateOf(false) }

    LaunchedEffect(pendingConfirmIntent) {
        val confirmIntent = UpdateDownloader.consumePendingConfirmIntent() ?: return@LaunchedEffect
        runCatching {
            context.startActivity(confirmIntent)
        }.onFailure { err ->
            settingsInstallError = err.message ?: "Unable to open Android PackageInstaller dialog"
        }
    }

    val settingsUnknownSourcesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (UpdateDownloader.canRequestPackageInstalls(context)) {
            settingsInstallPermNeeded = false
            val completed = updateDownloadState as? UpdateDownloader.DownloadState.Completed
            if (completed != null) {
                when (val res = UpdateDownloader.installApk(context, completed)) {
                    is UpdateDownloader.InstallLaunchResult.Launched -> settingsInstallError = null
                    is UpdateDownloader.InstallLaunchResult.PermissionRequired -> settingsInstallPermNeeded = true
                    is UpdateDownloader.InstallLaunchResult.Failed -> settingsInstallError = res.reason
                }
            }
        }
    }

    LaunchedEffect(updateDownloadState) {
        val st = updateDownloadState
        if (st is UpdateDownloader.DownloadState.Completed &&
            manualUpdateInfo?.version == st.version &&
            !st.signatureMismatch &&
            !st.downgradeBlocked &&
            settingsAutoOpenedVersion != st.version
        ) {
            settingsAutoOpenedVersion = st.version
            when (val res = UpdateDownloader.installApk(context, st)) {
                is UpdateDownloader.InstallLaunchResult.Launched -> {
                    settingsInstallPermNeeded = false
                    settingsInstallError = null
                }
                is UpdateDownloader.InstallLaunchResult.PermissionRequired -> {
                    settingsInstallPermNeeded = true
                    settingsInstallError = null
                }
                is UpdateDownloader.InstallLaunchResult.Failed -> {
                    settingsInstallError = res.reason
                }
            }
        }
    }
    var uiAndStyleExpanded by rememberSaveable { mutableStateOf(true) }
    var moduleLibsExpanded by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(activeSideTab) {
        rightPaneScrollState.scrollTo(0)
        when (activeSideTab) {
            SettingsSideTab.STYLE -> {
                uiAndStyleExpanded = true
                interfaceSectionExpanded = true
                themeSectionExpanded = true
            }
            SettingsSideTab.AUDIO -> audioSectionExpanded = true
            SettingsSideTab.FLOATER -> {
                oneUiSectionExpanded = true
                accessibilitySectionExpanded = true
            }
            SettingsSideTab.MODULES -> moduleLibsExpanded = true
            SettingsSideTab.TURBO -> {
                mrbeanSectionExpanded = true
                novaAcMrbeanSectionExpanded = true
                storageSectionExpanded = true
            }
            SettingsSideTab.NETWORK -> {
                networkSessionSectionExpanded = true
                explicitSectionExpanded = true
            }
            SettingsSideTab.SYSTEM -> permissionsSectionExpanded = true
            SettingsSideTab.LOGS -> diagnosticsSectionExpanded = true
            SettingsSideTab.ALL -> Unit
        }
    }

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
                    playbackSpeed = getPlaybackSpeedPref(context)
                    playbackPitch = getPlaybackPitchPref(context)
                    skipSilence = isSkipSilenceEnabled(context)
                    SongPlayer.refreshPlaybackTuning(context)
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

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasNotifPerm = PermissionHelper.hasNotificationPermission(context)
    }
    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasMediaPerm = PermissionHelper.hasMediaReadPermission(context)
    }
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasBluetoothPerm = PermissionHelper.hasBluetoothConnectPermission(context)
    }
    val batchPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasNotifPerm = PermissionHelper.hasNotificationPermission(context)
        hasMediaPerm = PermissionHelper.hasMediaReadPermission(context)
        hasBluetoothPerm = PermissionHelper.hasBluetoothConnectPermission(context)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isDefaultLinkHandler = DefaultLinkHelper.isAppDefaultLinkHandler(context)
                youtubeSyncStatus = com.music.spotui.data.youtube.YouTubeMusicSync.status(context)
                batteryOptExempt = BatteryOptimizationHelper.isIgnoringBatteryOptimization(context)
                accessibilityMiniPlayerEnabled = AccessibilityHelper.isServiceEnabled(context)
                hasNotifPerm = PermissionHelper.hasNotificationPermission(context)
                hasMediaPerm = PermissionHelper.hasMediaReadPermission(context)
                hasBluetoothPerm = PermissionHelper.hasBluetoothConnectPermission(context)
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
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("SpotUI Studio Settings", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                        Text("v2.5.3 · ${activeSideTab.title}", color = AppPalette, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.testTag("settings_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = AppBackground)
            )
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
            // Material 3 Expressive Vertical Side-Tab Rail Switcher
            Column(
                modifier = Modifier
                    .width(78.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF0C0F16))
                    .verticalScroll(railScrollState)
                    .padding(horizontal = 6.dp, vertical = 10.dp)
                    .padding(bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SettingsSideTab.entries.forEach { tab ->
                    val selected = activeSideTab == tab
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (selected) {
                                    Brush.verticalGradient(
                                        listOf(
                                            AppPalette.copy(alpha = 0.28f),
                                            AppPalette.copy(alpha = 0.12f),
                                        )
                                    )
                                } else {
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF141824), Color(0xFF11141E))
                                    )
                                }
                            )
                            .border(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) AppPalette.copy(alpha = 0.78f) else Color(0xFF202636),
                                shape = RoundedCornerShape(18.dp),
                            )
                            .clickable {
                                UiSoundEngine.playTapChime(context)
                                activeSideTab = tab
                            }
                            .testTag("settings_side_tab_${tab.name.lowercase()}")
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) AppPalette else Color(0xFF1C2232))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = tab.badge,
                                color = if (selected) Color.Black else Color(0xFF8C97AD),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title,
                            tint = if (selected) AppPalette else Color(0xFFB5BECE),
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = tab.shortLabel,
                            color = if (selected) Color.White else Color(0xFF9EA8BA),
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            VerticalDivider(
                modifier = Modifier.fillMaxHeight(),
                thickness = 1.dp,
                color = Color(0xFF1E2433),
            )

            // Material 3 Expressive Side-Tab Detail Content Pane
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rightPaneScrollState)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    // Clear bottom navigation + mini player
                    .padding(bottom = 200.dp),
            ) {
                // Expressive Category Hero Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    AppPalette.copy(alpha = 0.22f),
                                    Color(0xFF181C29),
                                    Color(0xFF11141E),
                                )
                            )
                        )
                        .border(1.dp, AppPalette.copy(alpha = 0.42f), RoundedCornerShape(24.dp))
                        .padding(16.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(AppPalette.copy(alpha = 0.20f))
                                .border(1.dp, AppPalette.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = activeSideTab.icon,
                                contentDescription = null,
                                tint = AppPalette,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeSideTab.title,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            Text(
                                text = activeSideTab.subtitle,
                                color = Color(0xFFB7C0D1),
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))

                if (oneUiHeader && showTab(SettingsSideTab.STYLE)) {
                // One UI signature expanded header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 2.dp),
                ) {
                    Text(
                        text = "Spotui • Android 16 & M3 Expressive",
                        fontSize = 13.sp,
                        color = AppPalette,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF1E2620),
                            border = BorderStroke(1.dp, Color(0xFF2E4C33)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1DB954))
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Android 16 Ready",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (batteryOptExempt) Color(0xFF1E2620) else Color(0xFF26201E),
                            border = BorderStroke(1.dp, if (batteryOptExempt) Color(0xFF2E4C33) else Color(0xFF4C332E)),
                            modifier = Modifier.clickable {
                                batteryOptLauncher.launch(BatteryOptimizationHelper.buildRequestOptimizationIntent(context))
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (batteryOptExempt) Color(0xFF1DB954) else Color(0xFFFFA500))
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (batteryOptExempt) "Battery: Unrestricted" else "Battery: Action Needed",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF1C2230),
                            border = BorderStroke(1.dp, Color(0xFF2B3A54)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Predictive Back: Active",
                                    color = Color(0xFF8AB4F8),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF282033),
                            border = BorderStroke(1.dp, Color(0xFF462D63)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (explicitAllowed) "Explicit Audio: On" else "Explicit Audio: Filtered",
                                    color = Color(0xFFD4BBFF),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (activeSideTab == SettingsSideTab.ALL) {
            // Master Advanced Settings Toggle Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (advancedSettingsEnabled) Color(0xFF1A2520) else Color(0xFF1A1A20))
                    .border(
                        width = 1.dp,
                        color = if (advancedSettingsEnabled) AppPalette.copy(alpha = 0.6f) else Color(0xFF2D2E38),
                        shape = RoundedCornerShape(18.dp),
                    )
                    .clickable {
                        val next = !advancedSettingsEnabled
                        advancedSettingsEnabled = next
                        setAdvancedSettingsEnabled(context, next)
                    }
                    .testTag("advanced_settings_toggle")
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Advanced Settings",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (advancedSettingsEnabled) AppPalette else Color(0xFF2D2E38))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = if (advancedSettingsEnabled) "ON" else "OFF",
                                color = if (advancedSettingsEnabled) Color.Black else Color(0xFFB3B3B3),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (advancedSettingsEnabled) {
                            "Showing SpotUI v2.5.3 Style, UI Sounds, All 16 Library Modules, 3D DSP, Lossless, Network/DoH & NovaAc"
                        } else {
                            "Tap to unlock SongPlay UI Toggle, Tactile Sounds, 16 Library Modules, 7-Band EQ & 3D Studio"
                        },
                        color = Color(0xFFB3B3B3),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
                Switch(
                    checked = advancedSettingsEnabled,
                    onCheckedChange = { enabled ->
                        advancedSettingsEnabled = enabled
                        setAdvancedSettingsEnabled(context, enabled)
                        UiSoundEngine.playToggleSound(context, enabled)
                    },
                    modifier = Modifier.testTag("advanced_settings_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AppPalette,
                        uncheckedThumbColor = Color(0xFFB3B3B3),
                        uncheckedTrackColor = Color(0xFF333333),
                    ),
                )
            }

            Spacer(Modifier.height(14.dp))
            }

            AnimatedVisibility(
                visible = advancedSettingsEnabled || activeSideTab != SettingsSideTab.ALL,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
            if (showTab(SettingsSideTab.STYLE)) {
            SectionTitle("Experience & Style")
            CollapsibleSettingsSection(
                title = "SongPlay UI, Live Clock & Tactile Sounds",
                subtitle = "Switch New Expressive / Legacy Classic UI, tab/toggle/slider audio, and patch notes",
                expanded = uiAndStyleExpanded,
                onExpandedChange = { uiAndStyleExpanded = it },
            ) {
                UiSoundAndStyleSection(
                    onShowPatchNotes = { showPatchNotesModal = true },
                )
            }

            Spacer(Modifier.height(16.dp))
            }

            if (showTab(SettingsSideTab.MODULES)) {
            SectionTitle("SpotUI 16 Library Submodules")
            CollapsibleSettingsSection(
                title = "Library Submodules (16 Modules)",
                subtitle = "Configure Spotify, InnerTube, LRCLIB, BetterLyrics, KuGou, SimpMusic, Paxsenix, Last.fm, ShazamKit, Fastlane, MrBean & Studio DSP",
                expanded = moduleLibsExpanded,
                onExpandedChange = { moduleLibsExpanded = it },
            ) {
                ModuleLibsSettingsSection()
            }

            Spacer(Modifier.height(16.dp))
            }
            if (showTab(SettingsSideTab.STYLE, SettingsSideTab.FLOATER)) {
            SectionTitle("System, One UI & Accessibility Floater")
            CollapsibleSettingsSection(
                title = "One UI & Android 16 System Integration",
                subtitle = "Predictive back gestures, squircle geometry, edge-to-edge, and battery guard",
                expanded = oneUiSectionExpanded,
                onExpandedChange = { oneUiSectionExpanded = it },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1A1A20))
                        .padding(14.dp)
                ) {
                    // One UI Large Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("One UI large title header", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("Display Samsung One UI style expansive hero title with live status pills", color = Color.Gray, fontSize = 12.sp)
                        }
                        Switch(
                            checked = oneUiHeader,
                            onCheckedChange = { checked ->
                                oneUiHeader = checked
                                com.music.spotui.data.preferences.setOneUiHeaderEnabled(context, checked)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = AppPalette)
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // One UI Squircle Icons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("One UI squircle geometry", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("Apply Samsung continuous curvature squircle rounding to album art and controls", color = Color.Gray, fontSize = 12.sp)
                        }
                        Switch(
                            checked = oneUiSquircle,
                            onCheckedChange = { checked ->
                                oneUiSquircle = checked
                                com.music.spotui.data.preferences.setOneUiSquircleIcons(context, checked)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = AppPalette)
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // Predictive Back & System Info
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF22242E),
                        border = BorderStroke(1.dp, Color(0xFF33384A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = AppPalette,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "System Gesture & Inset Support",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "• Android 14 / 15 / 16 Predictive Back: Fully configured in manifest with smooth swipe-to-home preview animations.\n• Edge-to-Edge System Bars: Dynamic WindowInsets with immersive status bar blending.\n• Samsung One UI 6 / 7 / 8: Native color palette synchronization and notification media seekbar.",
                                color = Color(0xFFB3B3B3),
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Battery Optimization Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF22242E))
                            .clickable {
                                batteryOptLauncher.launch(BatteryOptimizationHelper.buildRequestOptimizationIntent(context))
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Unrestricted Background Battery", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (batteryOptExempt) Color(0xFF1E3A24) else Color(0xFF3D2A1C))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        if (batteryOptExempt) "Active" else "Action Needed",
                                        color = if (batteryOptExempt) Color(0xFF81C784) else Color(0xFFFFB74D),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                if (batteryOptExempt) "Battery optimization exemption is active. Background audio will never be interrupted." else "Tap to grant unrestricted background activity so Android does not suspend audio.",
                                color = Color(0xFFB3B3B3),
                                fontSize = 11.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Configure Battery",
                            tint = AppPalette,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // One UI 9.0 Game Mini-Player & Studio Floater (Accessibility Button Overlay)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF22242E),
                        border = BorderStroke(
                            1.dp,
                            if (accessibilityMiniPlayerEnabled) Color(0xFF2E6B3E) else Color(0xFF33384A),
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    "One UI Studio Mini-Player Floater",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (accessibilityMiniPlayerEnabled) Color(0xFF1E3A24) else Color(0xFF3D2A1C),
                                        )
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        if (accessibilityMiniPlayerEnabled) "Active" else "Setup Needed",
                                        color = if (accessibilityMiniPlayerEnabled) Color(0xFF81C784) else Color(0xFFFFB74D),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                AccessibilityHelper.ONE_UI_RESTRICTED_SETTINGS_TIP,
                                color = Color(0xFFB3B3B3),
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(
                                    onClick = { AccessibilityHelper.openAccessibilitySettings(context) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AppPalette,
                                        contentColor = Color.Black,
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("oneui_accessibility_settings_button"),
                                ) {
                                    Text(
                                        if (accessibilityMiniPlayerEnabled) "Manage Shortcut" else "Enable Service",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                OutlinedButton(
                                    onClick = { AccessibilityHelper.openAppInfoSettings(context) },
                                    border = BorderStroke(1.dp, Color(0xFF4A5068)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("oneui_restricted_settings_button"),
                                ) {
                                    Text(
                                        "Allow Restricted",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            // Floater Dedicated Settings & Studio FX Hook
                            SettingsSwitchRow(
                                title = "Floater EQ & 3D Studio Controls",
                                subtitle = "Adjust 7-band EQ presets, Bass/Vocal/Treble, 3D Spatial profile & 8D audio live from the floater (controls SpotUI In-App Volume)",
                                checked = floaterEq3dHook,
                            ) { enabled ->
                                floaterEq3dHook = enabled
                                setFloaterEqAnd3dEnabled(context, enabled)
                            }
                            if (floaterEq3dHook) {
                                SettingsSwitchRow(
                                    title = "Open Floater Expanded to Studio FX",
                                    subtitle = "Automatically expand the EQ & 3D drawer when opening the floating overlay",
                                    checked = floaterStartExpanded,
                                ) { expanded ->
                                    floaterStartExpanded = expanded
                                    setFloaterStartExpanded(context, expanded)
                                }
                            }
                            SettingsSwitchRow(
                                title = "Tactile Vibration Feedback",
                                subtitle = "Criss-cross haptic ticks for corner resizing, transport taps, sliders, and EQ/3D switches",
                                checked = floaterHaptics,
                            ) { enabled ->
                                floaterHaptics = enabled
                                setFloaterHapticsEnabled(context, enabled)
                            }

                            if (floaterHaptics) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Vibration Intensity",
                                    color = Color(0xFFD0D6E0),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    FloaterHapticStrength.entries.forEach { strength ->
                                        val selected = floaterHapticStrength == strength
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (selected) AppPalette else Color(0xFF1A1B23))
                                                .clickable {
                                                    floaterHapticStrength = strength
                                                    setFloaterHapticStrength(context, strength)
                                                }
                                                .testTag("floater_haptic_${strength.name.lowercase()}")
                                                .padding(horizontal = 8.dp, vertical = 10.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                strength.label,
                                                color = if (selected) Color.Black else Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Auto-Dismiss Inactivity Timer",
                                color = Color(0xFFD0D6E0),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                listOf(
                                    5_000L to "5s",
                                    12_000L to "12s",
                                    25_000L to "25s",
                                    0L to "Stay Open",
                                ).forEach { (ms, label) ->
                                    val selected = floaterAutoHideMs == ms
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (selected) AppPalette else Color(0xFF1A1B23))
                                            .clickable {
                                                floaterAutoHideMs = ms
                                                setFloaterAutoHideMs(context, ms)
                                            }
                                            .testTag("floater_autohide_${ms}")
                                            .padding(horizontal = 6.dp, vertical = 10.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            label,
                                            color = if (selected) Color.Black else Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                TextButton(
                                    onClick = {
                                        resetFloaterGeometry(context)
                                        android.widget.Toast.makeText(
                                            context,
                                            "Floater size & corner position reset to default",
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("floater_reset_geometry_button"),
                                ) {
                                    Text("Reset Size & Pos", color = Color(0xFFFFB74D), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                TextButton(
                                    onClick = {
                                        audioSectionExpanded = true
                                        activeSideTab = SettingsSideTab.AUDIO
                                        android.widget.Toast.makeText(
                                            context,
                                            "Switched to Studio EQ & 3D tab",
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("floater_open_dsp_hook_button"),
                                ) {
                                    Text("Tune EQ & 3D", color = AppPalette, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            }

            if (showTab(SettingsSideTab.NETWORK)) {
            CollapsibleSettingsSection(
                title = "Explicit Content & Playback Protection",
                subtitle = "Manage explicit music, 'E' badges, and YouTube age-verification bypass",
                expanded = explicitSectionExpanded,
                onExpandedChange = { explicitSectionExpanded = it },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1A1A20))
                        .padding(14.dp)
                ) {
                    // Allow Explicit Content
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Allow explicit content", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (explicitAllowed) Color(0xFFE53935) else Color.Gray)
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text("E", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text("Play songs and albums with explicit language or mature themes", color = Color.Gray, fontSize = 12.sp)
                        }
                        Switch(
                            checked = explicitAllowed,
                            onCheckedChange = { checked ->
                                explicitAllowed = checked
                                com.music.spotui.data.preferences.setExplicitContentAllowed(context, checked)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = AppPalette)
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // Prefer Explicit Versions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Prefer original explicit versions", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("Prioritize uncut original master recordings over clean/radio edits when matching tracks", color = Color.Gray, fontSize = 12.sp)
                        }
                        Switch(
                            checked = preferExplicit,
                            enabled = explicitAllowed,
                            onCheckedChange = { checked ->
                                preferExplicit = checked
                                com.music.spotui.data.preferences.setPreferExplicitVersions(context, checked)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = AppPalette)
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // Show Explicit Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Show 'E' explicit badges", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("Display the explicit badge indicator next to titles in search results and track lists", color = Color.Gray, fontSize = 12.sp)
                        }
                        Switch(
                            checked = explicitBadgeVisible,
                            onCheckedChange = { checked ->
                                explicitBadgeVisible = checked
                                com.music.spotui.data.preferences.setExplicitBadgeVisible(context, checked)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = AppPalette)
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // InnerTube Bypass Status Pill
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF22242E),
                        border = BorderStroke(1.dp, Color(0xFF33384A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF64B5F6),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Multi-Client Explicit Stream Engine", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("VisionOS & Embedded VR bypass activated. Explicit and age-gated songs resolve automatically without requiring a YouTube account sign-in.", color = Color(0xFFB3B3B3), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            }

            if (showTab(SettingsSideTab.AUDIO)) {
            CollapsibleSettingsSection(
                title = "Studio Audio FX, 7-Band EQ & 3D Spatial",
                subtitle = "Parametric Biquad EQ, Preamp, Limiter, Normalizer, and Binaural Soundstage",
                expanded = audioSectionExpanded,
                onExpandedChange = { audioSectionExpanded = it },
            ) {
                AudioDspSection()
            }

            Spacer(Modifier.height(16.dp))
            }

            if (showTab(SettingsSideTab.NETWORK)) {
            CollapsibleSettingsSection(
                title = "Network, DNS & InnerTube Session",
                subtitle = "DnsOverHttps, proxy rotation, PoToken, and playback auth telemetry",
                expanded = networkSessionSectionExpanded,
                onExpandedChange = { networkSessionSectionExpanded = it },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1A1A20))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val sessionLabel = when {
                        authState.hasPlaybackLoginContext -> "Authenticated Playback Context"
                        authState.hasLoginCookie -> "Cookie Active (Sync Pending)"
                        else -> "Anonymous Visitor Session"
                    }
                    val poTokenLabel = if (!authState.poToken.isNullOrBlank() || !authState.poTokenPlayer.isNullOrBlank()) {
                        "PoToken ready"
                    } else {
                        "No cached PoToken"
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF12161D))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(sessionLabel, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Fingerprint ${authState.fingerprint.take(12)} · $poTokenLabel · Proxies active: $activeProxyCount",
                            color = Color(0xFFB3B8C4),
                            fontSize = 11.sp,
                        )
                        if (ytSessionSummary.isNotBlank()) {
                            Text(ytSessionSummary, color = AppPalette, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TextButton(
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("yt_session_signin_button"),
                                onClick = {
                                    context.startActivity(Intent(context, YouTubeMusicWebActivity::class.java))
                                },
                            ) {
                                Text(
                                    if (authState.hasLoginCookie) "Refresh YT Login" else "Sign in to YT Music",
                                    color = AppPalette,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            TextButton(
                                enabled = !ytSessionVerifyBusy,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("yt_session_verify_button"),
                                onClick = {
                                    ytSessionVerifyBusy = true
                                    scope.launch {
                                        val infoResult = YouTube.accountInfo()
                                        val channelsResult = YouTube.accountChannels()
                                        ytSessionVerifyBusy = false
                                        ytSessionSummary = infoResult.fold(
                                            onSuccess = { info ->
                                                val channelsCount = channelsResult.getOrNull()?.size ?: 0
                                                val handle = info.channelHandle?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                                                "Verified: ${info.name}$handle · $channelsCount channel(s)"
                                            },
                                            onFailure = { err ->
                                                val visitorOk = !AuthSessionStore.visitorData.isNullOrBlank()
                                                if (visitorOk) {
                                                    "Visitor context ready (sign in for personal YT channels)"
                                                } else {
                                                    "Session check failed: ${err.message ?: "offline"}"
                                                }
                                            },
                                        )
                                    }
                                },
                            ) {
                                Text(
                                    if (ytSessionVerifyBusy) "Checking…" else "Verify Channels",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            if (authState.hasLoginCookie) {
                                TextButton(
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("yt_session_signout_button"),
                                    onClick = {
                                        setYoutubeCookie(context, "")
                                        ytSessionSummary = "YouTube cookie cleared; using anonymous visitor context."
                                    },
                                ) {
                                    Text("Sign Out YT", color = Color(0xFFFF8B94), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    SettingsSwitchRow(
                        title = "WebClient Service Integrity PoToken",
                        subtitle = "Attach PoToken checks to WEB / WEB_REMIX / TVHTML5 stream resolution.",
                        checked = webClientPoToken,
                        onCheckedChange = { enabled ->
                            webClientPoToken = enabled
                            setWebClientPoTokenEnabled(context, enabled)
                        },
                    )

                    Text("DNS-over-HTTPS (DoH) resolver", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DnsOverHttpsPreset.entries.forEach { preset ->
                            val selected = dnsPreset == preset
                            Box(
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (selected) AppPalette else Color.White.copy(alpha = 0.08f))
                                    .clickable {
                                        dnsPreset = preset
                                        setDnsPreset(context, preset)
                                    }
                                    .testTag("doh_preset_${preset.name.lowercase()}")
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    preset.displayName,
                                    color = if (selected) Color.Black else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    Text("Proxy Pool (comma or newline separated)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = proxyUrlsInput,
                        onValueChange = { proxyUrlsInput = it },
                        placeholder = { Text("http://user:pass@host:8080, socks5://host:1080", color = Color.Gray, fontSize = 12.sp) },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("proxy_urls_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppPalette,
                            unfocusedBorderColor = Color(0xFF34343F),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                        ),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("apply_proxy_button"),
                            onClick = {
                                setProxyUrls(context, proxyUrlsInput)
                                activeProxyCount = AuthSessionStore.activeProxyCount()
                                proxyStatusNote = if (activeProxyCount > 0) {
                                    "Applied $activeProxyCount active proxy endpoint(s)."
                                } else {
                                    "Cleared proxy pool; using direct connection."
                                }
                            },
                        ) {
                            Text("Apply Proxies", color = AppPalette, fontWeight = FontWeight.Bold)
                        }
                        if (activeProxyCount > 1) {
                            TextButton(
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("rotate_ip_button"),
                                onClick = {
                                    val rotated = AuthSessionStore.rotateIp()
                                    proxyStatusNote = if (rotated) "Rotated to next proxy endpoint." else "No alternate proxy available."
                                },
                            ) {
                                Text("Rotate IP Now", color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    if (proxyStatusNote.isNotBlank()) {
                        Text(proxyStatusNote, color = Color(0xFF9FA9BC), fontSize = 11.sp)
                    }

                    SettingsSwitchRow(
                        title = "Automatic IP rotation",
                        subtitle = "Rotates across configured proxies on failure or HTTP 429 responses.",
                        checked = ipRotationEnabled,
                        onCheckedChange = { enabled ->
                            ipRotationEnabled = enabled
                            setIpRotationEnabled(context, enabled)
                        },
                    )
                    SettingsSwitchRow(
                        title = "Bypass proxy for media streams",
                        subtitle = "Keep API metadata behind proxy while streaming CDN audio directly.",
                        checked = streamBypassProxy,
                        onCheckedChange = { bypass ->
                            streamBypassProxy = bypass
                            setStreamBypassProxyEnabled(context, bypass)
                        },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            }

            if (showTab(SettingsSideTab.SYSTEM)) {
            CollapsibleSettingsSection(
                title = "App Permissions & System Access",
                subtitle = "Runtime permissions for notifications, local audio, Bluetooth, and battery",
                expanded = permissionsSectionExpanded,
                onExpandedChange = { permissionsSectionExpanded = it },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1A1A20))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val notifPerms = PermissionHelper.notificationPermissions()
                    PermissionStatusRow(
                        title = "Playback & Download Notifications",
                        subtitle = if (hasNotifPerm) "Granted — media controls and updates active" else "Needed on Android 13+ for player controls",
                        granted = hasNotifPerm,
                        testTag = "permission_notifications_row",
                        onAction = {
                            if (!hasNotifPerm && notifPerms.isNotEmpty()) {
                                notifPermissionLauncher.launch(notifPerms.toTypedArray())
                            } else {
                                PermissionHelper.openAppPermissionSettings(context)
                            }
                        },
                    )
                    val mediaPerms = PermissionHelper.mediaReadPermissions()
                    PermissionStatusRow(
                        title = "Local Audio Library Access",
                        subtitle = if (hasMediaPerm) "Granted — local audio files and downloads readable" else "Required to browse and play on-device audio tracks",
                        granted = hasMediaPerm,
                        testTag = "permission_media_row",
                        onAction = {
                            if (!hasMediaPerm && mediaPerms.isNotEmpty()) {
                                mediaPermissionLauncher.launch(mediaPerms.toTypedArray())
                            } else {
                                PermissionHelper.openAppPermissionSettings(context)
                            }
                        },
                    )
                    val btPerms = PermissionHelper.bluetoothConnectPermissions()
                    PermissionStatusRow(
                        title = "Bluetooth Audio Device Routing",
                        subtitle = if (hasBluetoothPerm) "Granted — headset & speaker output routing active" else "Needed on Android 12+ to display and route BT devices",
                        granted = hasBluetoothPerm,
                        testTag = "permission_bluetooth_row",
                        onAction = {
                            if (!hasBluetoothPerm && btPerms.isNotEmpty()) {
                                bluetoothPermissionLauncher.launch(btPerms.toTypedArray())
                            } else {
                                PermissionHelper.openAppPermissionSettings(context)
                            }
                        },
                    )
                    PermissionStatusRow(
                        title = "Unrestricted Background Battery",
                        subtitle = if (batteryOptExempt) "Exempt — uninterrupted background playback" else "Tap to request exemption from battery optimization",
                        granted = batteryOptExempt,
                        testTag = "permission_battery_row",
                        onAction = {
                            if (!batteryOptExempt) {
                                batteryOptLauncher.launch(PermissionHelper.requestIgnoreBatteryOptimizationIntent(context))
                            } else {
                                PermissionHelper.openAppPermissionSettings(context)
                            }
                        },
                    )
                    val missingPerms = remember(hasNotifPerm, hasMediaPerm, hasBluetoothPerm) {
                        PermissionHelper.missingRuntimePermissions(context)
                    }
                    if (missingPerms.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AppPalette.copy(alpha = 0.16f))
                                .border(1.dp, AppPalette.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .clickable { batchPermissionLauncher.launch(missingPerms.toTypedArray()) }
                                .testTag("grant_all_permissions_button")
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Grant All Missing Runtime Permissions (${missingPerms.size})",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "Requests notifications, audio library, and Bluetooth connect in one prompt",
                                    color = Color(0xFFD0D6E0),
                                    fontSize = 11.sp,
                                )
                            }
                            Text("Allow All", color = AppPalette, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text(
                            "All Play-compliant runtime audio & notification permissions granted.",
                            color = Color(0xFF81C784),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    TextButton(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("open_system_app_settings_button"),
                        onClick = { PermissionHelper.openAppPermissionSettings(context) },
                    ) {
                        Text("Open Android App Permission Settings", color = AppPalette, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            }

            if (showTab(SettingsSideTab.TURBO)) {
            CollapsibleSettingsSection(
                title = "Eclipse MrBean Network Engine",
                subtitle = "Turbo 1–10 concurrent downloads, parallel HTTP/2 range slices & pooling",
                expanded = mrbeanSectionExpanded,
                onExpandedChange = { mrbeanSectionExpanded = it },
            ) {
                val mrbeanSpeedBps by com.music.spotui.di.SongPlayer.downloadThroughputBytesPerSec().collectAsStateWithLifecycle()
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF1A1A20)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF232530))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("MrBean Turbo Pipeline", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "${mrbeanConcurrentDownloads.toInt()} songs × ${mrbeanParallelChunks.toInt()} slices · ${mrbeanIoBufferKb} KB buf · ${if (mrbeanHttp2) "HTTP/2 Pool" else "HTTP/1.1"}",
                                color = Color(0xFFB3BAC8),
                                fontSize = 11.sp,
                            )
                        }
                        Text(
                            text = if (mrbeanSpeedBps > 0L) org.eclipse.Mrbean.client.MrbeanDownloadClient.formatSpeed(mrbeanSpeedBps) else "Ready",
                            color = AppPalette,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Enable MrBean transport tuning", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("Multi-slice HTTP/2 download pool, TTL DNS cache, and fast resolver fallback.", color = Color.Gray, fontSize = 11.sp)
                        }
                        Switch(
                            checked = mrbeanEnabled,
                            onCheckedChange = { enabled ->
                                mrbeanEnabled = enabled
                                UiSoundEngine.playToggleSound(context, enabled)
                                org.eclipse.Mrbean.client.MrbeanNetworkSettings.setEnabled(context, enabled)
                            },
                            modifier = Modifier.testTag("mrbean_enable_switch"),
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = AppPalette),
                        )
                    }

                    // Always configurable 1-10 simultaneous song downloads (live hot-scalable)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Simultaneous song downloads", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("${mrbeanConcurrentDownloads.toInt()} / 10 tracks", color = AppPalette, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = mrbeanConcurrentDownloads,
                        onValueChange = {
                            val clamped = kotlin.math.round(it).toInt().coerceIn(1, 10)
                            mrbeanConcurrentDownloads = clamped.toFloat()
                            org.eclipse.Mrbean.client.MrbeanNetworkSettings.setMaxConcurrentDownloads(context, clamped)
                        },
                        valueRange = 1f..10f,
                        steps = 8,
                        modifier = Modifier.testTag("mrbean_concurrent_downloads_slider"),
                        colors = SliderDefaults.colors(activeTrackColor = AppPalette, thumbColor = AppPalette),
                    )

                    if (mrbeanEnabled) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Parallel HTTP Range connections per song", color = Color.White, fontSize = 13.sp)
                            Text("${mrbeanParallelChunks.toInt()}x parallel", color = AppPalette, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = mrbeanParallelChunks,
                            onValueChange = {
                                val clamped = kotlin.math.round(it).toInt().coerceIn(1, 6)
                                mrbeanParallelChunks = clamped.toFloat()
                                org.eclipse.Mrbean.client.MrbeanNetworkSettings.setParallelChunksPerFile(context, clamped)
                            },
                            valueRange = 1f..6f,
                            steps = 4,
                            modifier = Modifier.testTag("mrbean_parallel_chunks_slider"),
                            colors = SliderDefaults.colors(activeTrackColor = AppPalette, thumbColor = AppPalette),
                        )

                        Text("I/O Stream Buffer Size", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(64, 128, 256, 512).forEach { kb ->
                                val selected = mrbeanIoBufferKb == kb
                                Box(
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(if (selected) AppPalette else Color.White.copy(alpha = 0.08f))
                                        .clickable {
                                            mrbeanIoBufferKb = kb
                                            UiSoundEngine.playTapChime(context)
                                            org.eclipse.Mrbean.client.MrbeanNetworkSettings.setIoBufferKb(context, kb)
                                        }
                                        .testTag("mrbean_io_buffer_${kb}kb")
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("$kb KB", color = if (selected) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("HTTP/2 Multiplexing + Keep-Alive Pool", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Shares warm TLS sockets across range workers and resolvers.", color = Color.Gray, fontSize = 11.sp)
                            }
                            Switch(
                                checked = mrbeanHttp2,
                                onCheckedChange = {
                                    mrbeanHttp2 = it
                                    UiSoundEngine.playToggleSound(context, it)
                                    org.eclipse.Mrbean.client.MrbeanNetworkSettings.setHttp2Multiplexing(context, it)
                                },
                                modifier = Modifier.testTag("mrbean_http2_switch"),
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = AppPalette),
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Async Cover & Lyrics Prefetch", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Decouples artwork & lyrics caching so audio workers finish immediately.", color = Color.Gray, fontSize = 11.sp)
                            }
                            Switch(
                                checked = mrbeanPrefetchAsync,
                                onCheckedChange = {
                                    mrbeanPrefetchAsync = it
                                    UiSoundEngine.playToggleSound(context, it)
                                    org.eclipse.Mrbean.client.MrbeanNetworkSettings.setPrefetchMetadataInParallel(context, it)
                                },
                                modifier = Modifier.testTag("mrbean_prefetch_async_switch"),
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = AppPalette),
                            )
                        }

                        Text("Candidate fallback: ${mrbeanCandidateLimit.toInt()} ranked sources", color = Color.White, fontSize = 13.sp)
                        Slider(
                            value = mrbeanCandidateLimit,
                            onValueChange = { mrbeanCandidateLimit = it },
                            onValueChangeFinished = {
                                mrbeanCandidateLimit = kotlin.math.round(mrbeanCandidateLimit).toInt().coerceIn(3, 10).toFloat()
                                org.eclipse.Mrbean.client.MrbeanNetworkSettings.setCandidateLimit(context, mrbeanCandidateLimit.toInt())
                            },
                            valueRange = 3f..10f,
                            steps = 6,
                            modifier = Modifier.testTag("mrbean_candidate_limit_slider"),
                            colors = SliderDefaults.colors(activeTrackColor = AppPalette, thumbColor = AppPalette),
                        )
                        Text("Connection timeout: ${mrbeanConnectSeconds.toInt()} seconds", color = Color.White, fontSize = 13.sp)
                        Slider(
                            value = mrbeanConnectSeconds,
                            onValueChange = { mrbeanConnectSeconds = it },
                            onValueChangeFinished = {
                                val snapped = kotlin.math.round(mrbeanConnectSeconds / 3f).toInt().coerceIn(1, 15).times(3)
                                mrbeanConnectSeconds = snapped.toFloat()
                                org.eclipse.Mrbean.client.MrbeanNetworkSettings.setConnectTimeoutMs(context, snapped * 1_000)
                            },
                            valueRange = 3f..45f,
                            steps = 13,
                            modifier = Modifier.testTag("mrbean_connect_timeout_slider"),
                            colors = SliderDefaults.colors(activeTrackColor = AppPalette, thumbColor = AppPalette),
                        )
                        Text("Read timeout: ${mrbeanReadSeconds.toInt()} seconds", color = Color.White, fontSize = 13.sp)
                        Slider(
                            value = mrbeanReadSeconds,
                            onValueChange = { mrbeanReadSeconds = it },
                            onValueChangeFinished = {
                                val snapped = kotlin.math.round(mrbeanReadSeconds / 5f).toInt().coerceIn(1, 18).times(5)
                                mrbeanReadSeconds = snapped.toFloat()
                                org.eclipse.Mrbean.client.MrbeanNetworkSettings.setReadTimeoutMs(context, snapped * 1_000)
                            },
                            valueRange = 5f..90f,
                            steps = 16,
                            modifier = Modifier.testTag("mrbean_read_timeout_slider"),
                            colors = SliderDefaults.colors(activeTrackColor = AppPalette, thumbColor = AppPalette),
                        )
                        Text("Ranged-download slice size: ${mrbeanChunkMiB.toInt()} MiB", color = Color.White, fontSize = 13.sp)
                        Slider(
                            value = mrbeanChunkMiB,
                            onValueChange = { mrbeanChunkMiB = it },
                            onValueChangeFinished = {
                                mrbeanChunkMiB = kotlin.math.round(mrbeanChunkMiB).toInt().coerceIn(1, 16).toFloat()
                                org.eclipse.Mrbean.client.MrbeanNetworkSettings.setChunkMiB(context, mrbeanChunkMiB.toInt())
                            },
                            valueRange = 1f..16f,
                            steps = 14,
                            modifier = Modifier.testTag("mrbean_chunk_size_slider"),
                            colors = SliderDefaults.colors(activeTrackColor = AppPalette, thumbColor = AppPalette),
                        )
                        Text("Retries per byte range: ${mrbeanRetries.toInt()}", color = Color.White, fontSize = 13.sp)
                        Slider(
                            value = mrbeanRetries,
                            onValueChange = { mrbeanRetries = it },
                            onValueChangeFinished = {
                                mrbeanRetries = kotlin.math.round(mrbeanRetries).toInt().coerceIn(1, 6).toFloat()
                                org.eclipse.Mrbean.client.MrbeanNetworkSettings.setRangeRetries(context, mrbeanRetries.toInt())
                            },
                            valueRange = 1f..6f,
                            steps = 4,
                            modifier = Modifier.testTag("mrbean_range_retries_slider"),
                            colors = SliderDefaults.colors(activeTrackColor = AppPalette, thumbColor = AppPalette),
                        )
                    }
                    Text(
                        "Protocol note: HTTP/2 multiplexing + connection pooling runs on Android's TLS stack across MrBean, SpotiFlac, InnerTube, and Deezer.",
                        color = Color(0xFF9FA9BC), fontSize = 11.sp,
                    )
                    TextButton(
                        onClick = {
                            org.eclipse.Mrbean.client.MrbeanNetworkSettings.reset(context)
                            val reset = org.eclipse.Mrbean.client.MrbeanNetworkSettings.snapshot(context)
                            mrbeanEnabled = reset.enabled
                            mrbeanConcurrentDownloads = reset.maxConcurrentDownloads.toFloat()
                            mrbeanParallelChunks = reset.parallelChunksPerFile.toFloat()
                            mrbeanIoBufferKb = reset.ioBufferKb
                            mrbeanHttp2 = reset.http2Multiplexing
                            mrbeanPrefetchAsync = reset.prefetchMetadataInParallel
                            mrbeanCandidateLimit = reset.candidateLimit.toFloat()
                            mrbeanConnectSeconds = reset.connectTimeoutMs / 1_000f
                            mrbeanReadSeconds = reset.readTimeoutMs / 1_000f
                            mrbeanChunkMiB = reset.chunkMiB.toFloat()
                            mrbeanRetries = reset.rangeRetries.toFloat()
                        },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("mrbean_reset_defaults_button"),
                    ) { Text("Restore recommended turbo defaults", color = AppPalette) }
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
                            "${novaAcMrbeanSnapshot.profile.label}: ${novaAcMrbeanSnapshot.archiveBufferBytes / 1024} KiB payload I/O · ${novaAcMrbeanSnapshot.deliveryBufferBytes / 1024} KiB delivery I/O · ${novaAcMrbeanSnapshot.framePlaintextBytes / 1024} KiB AEAD frames${if (novaAcMrbeanSnapshot.zeroDeflateAudio) " · Zero-Deflate Multi-MB/s" else ""}",
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
            novaAcImportStatus?.let { status ->
                Text(
                    status,
                    color = if (status.startsWith("Compatible")) Color(0xFF81C784) else Color(0xFFFFB74D),
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
            }

            if (showTab(SettingsSideTab.LOGS)) {
            CollapsibleSettingsSection(
                title = "Diagnostics & Telemetry Hub",
                subtitle = "Launch, warning, error, Floater, DSP, Network & Auth records (2000-entry buffer)",
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
                        Text("Open Full Developer Logger Console", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("Inspect 8 categorized channels, export session JSON & run live probes", color = Color(0xFFB3B3B3), fontSize = 12.sp)
                    }
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open diagnostic console", tint = AppPalette)
                }
            }

            Spacer(Modifier.height(16.dp))
            }

            if (showTab(SettingsSideTab.STYLE)) {
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
            }

            if (showTab(SettingsSideTab.FLOATER)) {
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
            }

            if (showTab(SettingsSideTab.SYSTEM)) {
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

            }

            if (showTab(SettingsSideTab.NETWORK)) {
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
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
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
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open YouTube Music", tint = AppPalette, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF1A1A20)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("YouTube Music Native Playlist & Album Sync", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("Syncs all your YouTube Music playlists, Liked Music & saved albums directly into SpotUI's Library via signed-in session cookies or official Google OAuth—no need to browse the website to play.", color = Color(0xFFB3B3B3), fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        enabled = !youtubeSyncBusy,
                        onClick = {
                            if (!youtubeSyncStatus.sessionReady && !youtubeSyncStatus.connected) {
                                context.startActivity(Intent(context, com.music.spotui.ui.screens.YouTubeMusicWebActivity::class.java))
                            } else {
                                youtubeSyncBusy = true
                                scope.launch {
                                    if (youtubeSyncStatus.sessionReady) {
                                        com.music.spotui.data.youtube.YouTubeMusicSync.syncFromSignedInSession(context)
                                    } else {
                                        com.music.spotui.data.youtube.YouTubeMusicSync.syncNow(context)
                                    }
                                    youtubeSyncStatus = com.music.spotui.data.youtube.YouTubeMusicSync.status(context)
                                    youtubeSyncBusy = false
                                }
                            }
                        },
                    ) {
                        Text(
                            when {
                                youtubeSyncBusy -> "Syncing…"
                                youtubeSyncStatus.sessionReady || youtubeSyncStatus.connected -> "Sync All YT Playlists Now"
                                else -> "Sign in to YT Music"
                            },
                            color = AppPalette,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
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
            }
                }
            }

            if (showTab(SettingsSideTab.AUDIO)) {
            Spacer(Modifier.height(12.dp))
            SectionTitle("Audio quality")
            QualityPicker(
                title = "Streaming over Wi-Fi",
                selected = wifiQ,
                showFlacWarning = wifiQ == StreamQuality.LOSSLESS,
                losslessTimeout = wifiLosslessTimeout,
                onSelectTimeout = { wifiLosslessTimeout = it; setWifiLosslessTimeout(context, it) },
            ) { selected ->
                val changed = wifiQ != selected
                wifiQ = selected
                setWifiQuality(context, selected)
                if (changed && SongPlayer.restartCurrentTrackForQualityChange(context)) {
                    android.widget.Toast.makeText(
                        context,
                        "Restarting current track in ${selected.label}…",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            }

            QualityPicker(
                title = "Streaming over cellular",
                selected = cellQ,
                showFlacWarning = cellQ == StreamQuality.LOSSLESS,
                losslessTimeout = cellLosslessTimeout,
                onSelectTimeout = { cellLosslessTimeout = it; setCellularLosslessTimeout(context, it) },
            ) { selected ->
                val changed = cellQ != selected
                cellQ = selected
                setCellularQuality(context, selected)
                if (changed && SongPlayer.restartCurrentTrackForQualityChange(context)) {
                    android.widget.Toast.makeText(
                        context,
                        "Restarting current track in ${selected.label}…",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            }

            QualityPicker(
                title = "Download quality",
                selected = dlQ,
                losslessTimeout = dlLosslessTimeout,
                onSelectTimeout = { dlLosslessTimeout = it; setDownloadLosslessTimeout(context, it) },
            ) { dlQ = it; setDownloadQuality(context, it) }
            }

            AnimatedVisibility(
                visible = advancedSettingsEnabled || activeSideTab != SettingsSideTab.ALL,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
            if (showTab(SettingsSideTab.AUDIO)) {
            Spacer(Modifier.height(8.dp))
            LosslessProviderSection(
                onOpenProviderStatusDialog = {
                    scope.launch {
                        isRefreshingStatuses = true
                        providerStatuses = com.metrolist.spotify.SpotiFlac.getProviderStatuses()
                        isRefreshingStatuses = false
                        showProviderStatusDialog = true
                    }
                },
            )

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
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1A1A20))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Playback Speed", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("${String.format("%.2f", playbackSpeed)}x", color = AppPalette, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = playbackSpeed,
                    onValueChange = { playbackSpeed = (it * 20f).toInt() / 20f },
                    onValueChangeFinished = {
                        setPlaybackSpeedPref(context, playbackSpeed)
                        SongPlayer.refreshPlaybackTuning(context)
                    },
                    valueRange = 0.5f..2.0f,
                    modifier = Modifier.testTag("playback_speed_slider"),
                    colors = SliderDefaults.colors(activeTrackColor = AppPalette, thumbColor = AppPalette),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Playback Pitch", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("${String.format("%.2f", playbackPitch)}x", color = AppPalette, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = playbackPitch,
                    onValueChange = { playbackPitch = (it * 20f).toInt() / 20f },
                    onValueChangeFinished = {
                        setPlaybackPitchPref(context, playbackPitch)
                        SongPlayer.refreshPlaybackTuning(context)
                    },
                    valueRange = 0.75f..1.25f,
                    modifier = Modifier.testTag("playback_pitch_slider"),
                    colors = SliderDefaults.colors(activeTrackColor = AppPalette, thumbColor = AppPalette),
                )
                SettingsSwitchRow(
                    title = "Skip silence",
                    subtitle = "Automatically compresses extended silent passages during playback",
                    checked = skipSilence,
                ) { enabled ->
                    skipSilence = enabled
                    setSkipSilenceEnabled(context, enabled)
                    SongPlayer.refreshPlaybackTuning(context)
                }
                TextButton(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("reset_playback_tuning_button"),
                    onClick = {
                        playbackSpeed = 1.0f
                        playbackPitch = 1.0f
                        skipSilence = false
                        setPlaybackSpeedPref(context, 1.0f)
                        setPlaybackPitchPref(context, 1.0f)
                        setSkipSilenceEnabled(context, false)
                        SongPlayer.refreshPlaybackTuning(context)
                    },
                ) {
                    Text("Reset Speed & Pitch to 1.00x", color = AppPalette, fontWeight = FontWeight.Bold)
                }
            }
            }

            if (showTab(SettingsSideTab.AUDIO, SettingsSideTab.LOGS)) {
            Spacer(Modifier.height(10.dp))
            PlaybackAndToolsSection(
                onOpenDeveloperConsole = {
                    navController.navigate(com.music.spotui.ui.navigation.Routes.DeveloperConsole.route)
                },
            )
            }
                }
            }

            if (showTab(SettingsSideTab.SYSTEM)) {
            Spacer(Modifier.height(12.dp))
            SectionTitle("Background Notifications")
            SettingsSwitchRow(
                title = "Exit Play/Pause Mini-Notification",
                subtitle = "When you exit SpotUI, show a lightweight notification with Play/Pause, Next, and Previous buttons without opening the app",
                checked = exitMiniNotifEnabled,
            ) { enabled ->
                exitMiniNotifEnabled = enabled
                com.music.spotui.data.preferences.setBackgroundExitNotificationEnabled(context, enabled)
                if (!enabled) {
                    com.music.spotui.engine.MediaNotificationAdapter.dismissExitMiniNotification(context)
                }
            }
            }

            if (showTab(SettingsSideTab.AUDIO)) {
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
            }
            if (showTab(SettingsSideTab.SYSTEM)) {
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
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Installed: v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                color = Color(0xFFB3B3B3),
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(6.dp))
            SettingsSwitchRow(
                title = "Auto-download updates in-app",
                subtitle = "Streams GitHub Release APKs inside SpotUI, verifies SHA-256, and opens Android Package Installer automatically",
                checked = autoDownloadUpdates,
            ) {
                autoDownloadUpdates = it
                setAutoDownloadUpdatesEnabled(context, it)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppPalette)
                        .clickable(enabled = !manualUpdateBusy) {
                            manualUpdateBusy = true
                            manualUpdateStatus = "Checking GitHub Releases…"
                            scope.launch {
                                when (val result = UpdateChecker.checkManual(context)) {
                                    is UpdateChecker.ManualCheckResult.UpdateAvailable -> {
                                        manualUpdateInfo = result.info
                                        if (autoDownloadUpdates) {
                                            manualUpdateStatus = "Found v${result.info.version} — downloading in-app…"
                                            UpdateDownloader.startDownload(context, result.info)
                                        } else {
                                            manualUpdateStatus = "New version v${result.info.version} available on GitHub!"
                                        }
                                    }
                                    is UpdateChecker.ManualCheckResult.UpToDate -> {
                                        manualUpdateInfo = null
                                        manualUpdateStatus = "You are up to date (v${result.installedVersion})."
                                    }
                                    is UpdateChecker.ManualCheckResult.Failed -> {
                                        manualUpdateInfo = null
                                        manualUpdateStatus = "Check failed: ${result.reason}"
                                    }
                                }
                                manualUpdateBusy = false
                            }
                        }
                        .testTag("check_updates_now_button")
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (manualUpdateBusy) "Checking…" else "Check for Updates",
                        color = Color.Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF232530))
                        .clickable {
                            val cleanRepo = updateRepoUrl.trim().trimEnd('/').removeSuffix(".git")
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$cleanRepo/releases")))
                            }
                        }
                        .testTag("open_github_releases_button")
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("GitHub Releases", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (manualUpdateStatus.isNotBlank() || updateDownloadState !is UpdateDownloader.DownloadState.Idle) {
                Spacer(Modifier.height(6.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1A1A20))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (manualUpdateStatus.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                manualUpdateStatus,
                                color = if (manualUpdateInfo != null) Color(0xFF81C784) else Color(0xFFD0D6E0),
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f),
                            )
                            manualUpdateInfo?.let { info ->
                                val isDownloadingThis = updateDownloadState is UpdateDownloader.DownloadState.Downloading &&
                                    (updateDownloadState as UpdateDownloader.DownloadState.Downloading).version == info.version
                                TextButton(
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    onClick = {
                                        UiSoundEngine.playTapChime(context)
                                        if (isDownloadingThis) {
                                            UpdateDownloader.cancelDownload(context)
                                        } else {
                                            UpdateDownloader.startDownload(context, info, force = true)
                                        }
                                    },
                                ) {
                                    Text(
                                        if (isDownloadingThis) "Cancel" else "Download In-App",
                                        color = if (isDownloadingThis) Color(0xFFFF7A59) else AppPalette,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                    when (val dlState = updateDownloadState) {
                        is UpdateDownloader.DownloadState.Downloading -> {
                            val pct = if (dlState.progressFraction >= 0f) "${(dlState.progressFraction * 100).toInt()}%" else "…"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("In-App Download: ${dlState.fileName}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text(pct, color = AppPalette, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            if (dlState.progressFraction >= 0f) {
                                LinearProgressIndicator(
                                    progress = { dlState.progressFraction },
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                                    color = AppPalette,
                                    trackColor = Color(0xFF2B2D36),
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                                    color = AppPalette,
                                    trackColor = Color(0xFF2B2D36),
                                )
                            }
                            val totalStr = if (dlState.totalBytes > 0L) " / ${UpdateDownloader.formatBytes(dlState.totalBytes)}" else ""
                            val eta = UpdateDownloader.formatEta(dlState.etaSeconds)
                            Text(
                                text = "${UpdateDownloader.formatBytes(dlState.downloadedBytes)}$totalStr · ${UpdateDownloader.formatSpeed(dlState.speedBytesPerSec)}${if (eta.isNotBlank()) " · $eta" else ""}",
                                color = Color(0xFF9EA5B5),
                                fontSize = 11.sp,
                            )
                        }
                        is UpdateDownloader.DownloadState.Verifying -> {
                            Text("Verifying ${dlState.fileName} SHA-256 & APK header…", color = Color(0xFF9ED4FF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                                color = Color(0xFF9ED4FF),
                                trackColor = Color(0xFF2B2D36),
                            )
                        }
                        is UpdateDownloader.DownloadState.Completed -> {
                            Text(
                                text = when {
                                    dlState.signatureMismatch || dlState.downgradeBlocked -> "⚠ v${dlState.version} downloaded (Signature/Version conflict)"
                                    dlState.sha256Verified == true -> "✓ v${dlState.version} ready to install (SHA-256 verified)"
                                    else -> "✓ v${dlState.version} ready to install (APK verified)"
                                },
                                color = if (dlState.signatureMismatch || dlState.downgradeBlocked) Color(0xFFFFB74D) else Color(0xFF81C784),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = if (dlState.compatWarning.isNotBlank()) {
                                    "${dlState.compatWarning} Saved copy: ${dlState.savedDisplayPath} (${UpdateDownloader.formatBytes(dlState.sizeBytes)})."
                                } else {
                                    "Installs in-place over your current SpotUI install while keeping your library. Backup copy: ${dlState.savedDisplayPath} (${UpdateDownloader.formatBytes(dlState.sizeBytes)})."
                                },
                                color = if (dlState.signatureMismatch || dlState.downgradeBlocked) Color(0xFFFFE0B2) else Color(0xFFD0D6E0),
                                fontSize = 11.sp,
                            )
                            if (settingsInstallPermNeeded) {
                                Text(
                                    text = "Enable 'Allow from this source' once — Android Package Installer will open automatically right after.",
                                    color = Color(0xFFFFD54F),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            if (!settingsInstallError.isNullOrBlank()) {
                                Text(
                                    text = settingsInstallError!!,
                                    color = Color(0xFFFF7A59),
                                    fontSize = 11.sp,
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (dlState.signatureMismatch || dlState.downgradeBlocked) {
                                    TextButton(
                                        modifier = Modifier.heightIn(min = 48.dp),
                                        onClick = {
                                            UiSoundEngine.playTapChime(context)
                                            UpdateDownloader.triggerUninstallCurrentApp(context)
                                        },
                                    ) {
                                        Text("Uninstall Old App", color = Color(0xFFFFB74D), fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    TextButton(
                                        modifier = Modifier.heightIn(min = 48.dp),
                                        onClick = {
                                            UiSoundEngine.playTapChime(context)
                                            if (!UpdateDownloader.canRequestPackageInstalls(context)) {
                                                settingsUnknownSourcesLauncher.launch(
                                                    UpdateDownloader.buildUnknownAppSourcesIntent(context, newTask = false),
                                                )
                                            } else {
                                                when (val res = UpdateDownloader.installApk(context, dlState)) {
                                                    is UpdateDownloader.InstallLaunchResult.Launched -> {
                                                        settingsInstallPermNeeded = false
                                                        settingsInstallError = null
                                                    }
                                                    is UpdateDownloader.InstallLaunchResult.PermissionRequired -> {
                                                        settingsInstallPermNeeded = true
                                                    }
                                                    is UpdateDownloader.InstallLaunchResult.Failed -> {
                                                        settingsInstallError = res.reason
                                                    }
                                                }
                                            }
                                        },
                                    ) {
                                        Text(
                                            if (settingsInstallPermNeeded) "Allow & Install" else "Install Update",
                                            color = AppPalette,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                                TextButton(
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    onClick = {
                                        UiSoundEngine.playTapChime(context)
                                        UpdateDownloader.openDownloadsFolder(context)
                                    },
                                ) {
                                    Text("Downloads", color = Color(0xFFD0D6E0), fontSize = 11.sp)
                                }
                                Text(
                                    text = "SHA: ${dlState.computedSha256.take(12)}…",
                                    color = Color(0xFF9ED4FF),
                                    fontSize = 10.sp,
                                    modifier = Modifier.clickable {
                                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        cm.setPrimaryClip(android.content.ClipData.newPlainText("SpotUI SHA256", dlState.computedSha256))
                                        android.widget.Toast.makeText(context, "SHA-256 digest copied", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                )
                            }
                        }
                        is UpdateDownloader.DownloadState.Installing -> {
                            val pct = (dlState.progressFraction.coerceIn(0f, 1f) * 100).toInt()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("Android PackageInstaller Session", color = Color(0xFF81C784), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("$pct%", color = Color(0xFF81C784), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            LinearProgressIndicator(
                                progress = { dlState.progressFraction.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                                color = Color(0xFF81C784),
                                trackColor = Color(0xFF2B2D36),
                            )
                            Text(dlState.statusMessage, color = Color(0xFFD0D6E0), fontSize = 11.sp)
                        }
                        is UpdateDownloader.DownloadState.Installed -> {
                            Text(
                                text = "✓ SpotUI v${dlState.version} installed via Android PackageInstaller",
                                color = Color(0xFF81C784),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        is UpdateDownloader.DownloadState.Failed -> {
                            Text("Update failed: ${dlState.reason}", color = Color(0xFFFF7A59), fontSize = 11.sp)
                            val fallback = dlState.fallbackCompleted
                            if (fallback != null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (fallback.signatureMismatch || fallback.downgradeBlocked) {
                                        TextButton(
                                            modifier = Modifier.heightIn(min = 48.dp),
                                            onClick = {
                                                UiSoundEngine.playTapChime(context)
                                                UpdateDownloader.triggerUninstallCurrentApp(context)
                                            },
                                        ) {
                                            Text("Uninstall Old App", color = Color(0xFFFFB74D), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        TextButton(
                                            modifier = Modifier.heightIn(min = 48.dp),
                                            onClick = { UpdateDownloader.openDownloadsFolder(context) },
                                        ) {
                                            Text("Open Downloads", color = Color(0xFF9ED4FF), fontSize = 12.sp)
                                        }
                                    } else {
                                        TextButton(
                                            modifier = Modifier.heightIn(min = 48.dp),
                                            onClick = {
                                                UiSoundEngine.playTapChime(context)
                                                if (!UpdateDownloader.canRequestPackageInstalls(context)) {
                                                    settingsUnknownSourcesLauncher.launch(
                                                        UpdateDownloader.buildUnknownAppSourcesIntent(context, newTask = false),
                                                    )
                                                } else {
                                                    when (val res = UpdateDownloader.installApk(context, fallback, ignoreCompatCheck = true)) {
                                                        is UpdateDownloader.InstallLaunchResult.Launched -> {
                                                            settingsInstallPermNeeded = false
                                                            settingsInstallError = null
                                                        }
                                                        is UpdateDownloader.InstallLaunchResult.PermissionRequired -> {
                                                            settingsInstallPermNeeded = true
                                                        }
                                                        is UpdateDownloader.InstallLaunchResult.Failed -> {
                                                            settingsInstallError = res.reason
                                                        }
                                                    }
                                                }
                                            },
                                        ) {
                                            Text("Retry PackageInstaller", color = AppPalette, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        TextButton(
                                            modifier = Modifier.heightIn(min = 48.dp),
                                            onClick = { UpdateDownloader.openFileProviderFallbackInstaller(context, fallback) },
                                        ) {
                                            Text("System Fallback", color = Color(0xFF9ED4FF), fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                        is UpdateDownloader.DownloadState.Cancelled -> {
                            Text("In-app update download cancelled.", color = Color(0xFFB3B3B3), fontSize = 11.sp)
                        }
                        UpdateDownloader.DownloadState.Idle -> Unit
                    }
                    manualUpdateInfo?.let { info ->
                        if (info.apkFileName.isNotBlank() || info.expectedSha256.isNotBlank()) {
                            val mbText = if (info.apkSizeBytes > 0L) " · %.1f MB".format(info.apkSizeBytes / (1024.0 * 1024.0)) else ""
                            if (info.apkFileName.isNotBlank()) {
                                Text(
                                    text = "Release asset: ${info.apkFileName}$mbText",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            if (info.expectedSha256.isNotBlank()) {
                                Text(
                                    text = "SHA-256 (tap to copy): ${info.expectedSha256.take(24)}…",
                                    color = Color(0xFF9ED4FF),
                                    fontSize = 11.sp,
                                    modifier = Modifier.clickable {
                                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        cm.setPrimaryClip(android.content.ClipData.newPlainText("SpotUI SHA256", info.expectedSha256))
                                        android.widget.Toast.makeText(context, "SHA-256 digest copied", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        UiSoundEngine.playTapChime(context)
                        navController.navigate(com.music.spotui.ui.navigation.Routes.UpdateRoadmap.route)
                    }
                    .background(Color(0xFF1A1A20))
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Roadmap & Update Master Plan", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text("View complete release history and version changelogs (including v2.5.3)", color = Color(0xFFB3B3B3), fontSize = 12.sp)
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "View",
                    tint = AppPalette,
                    modifier = Modifier.size(20.dp)
                )
            }
            }
            AnimatedVisibility(
                visible = advancedSettingsEnabled || activeSideTab != SettingsSideTab.ALL,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
            if (showTab(SettingsSideTab.SYSTEM)) {
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

            }

            if (showTab(SettingsSideTab.STYLE)) {
            Spacer(Modifier.height(12.dp))
            SectionTitle("AI & Music Insights")
            SettingsSwitchRow(
                title = "Key-Free AI Mode",
                subtitle = "Use built-in music intelligence and tour insights without needing an API key",
                checked = geminiFreeMode,
                onCheckedChange = { enabled ->
                    geminiFreeMode = enabled
                    com.music.spotui.data.preferences.setGeminiFreeMode(context, enabled)
                }
            )

            AnimatedVisibility(
                visible = !geminiFreeMode,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E1E24))
                        .padding(14.dp)
                ) {
                    Text(
                        text = "Custom Gemini API Key",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Enables live Google Search Grounding for up-to-the-minute tour dates and releases.",
                        color = Color(0xFFB3B3B3),
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = geminiApiKey,
                        onValueChange = {
                            geminiApiKey = it
                            com.music.spotui.data.preferences.setGeminiApiKey(context, it)
                        },
                        placeholder = { Text("Paste Gemini API Key", color = Color.Gray, fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = AppPalette,
                            unfocusedBorderColor = Color(0xFF444444),
                            cursorColor = AppPalette
                        )
                    )
                }
            }

            }

            if (showTab(SettingsSideTab.LOGS)) {
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
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Open Console",
                    tint = AppPalette,
                    modifier = Modifier.size(20.dp)
                )
            }
            }
                }
            }

            if (showTab(SettingsSideTab.SYSTEM)) {
            Spacer(Modifier.height(12.dp))
            SectionTitle("Account")
            val isSpotifyLoggedIn = remember(com.music.spotui.data.api.SpotifySession.spDc(context)) {
                com.music.spotui.data.api.SpotifySession.spDc(context).isNotBlank()
            }
            Text(
                text = if (isSpotifyLoggedIn) "Log out" else "Log in to Spotify",
                color = if (isSpotifyLoggedIn) Color(0xFFE57373) else Color(0xFF1ED760),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        if (isSpotifyLoggedIn) {
                            com.music.spotui.data.api.SpotifySession.setSpDc(context, "")
                            com.music.spotui.data.api.Api.HomeCache.clear()
                        }
                        navController.navigate(com.music.spotui.ui.navigation.Routes.Login.route)
                    }
                    .padding(vertical = 14.dp)
            )
            }
            Spacer(Modifier.height(40.dp))
            }
        }

        if (showPatchNotesModal) {
            PatchNotesDialog(
                onDismiss = { showPatchNotesModal = false },
                onOpenDiagnostics = {
                    showPatchNotesModal = false
                    navController.navigate(com.music.spotui.ui.navigation.Routes.DeveloperConsole.route)
                },
            )
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
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF191D29), Color(0xFF131620))
                )
            )
            .border(
                width = 1.dp,
                color = if (expanded) AppPalette.copy(alpha = 0.55f) else Color(0xFF272D3E),
                shape = RoundedCornerShape(22.dp),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    UiSoundEngine.playTapChime(accessibilityContext)
                    onExpandedChange(!expanded)
                }
                .padding(horizontal = 14.dp, vertical = expandedPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Text(subtitle, color = Color(0xFFB3BAC8), fontSize = 11.sp, lineHeight = 15.sp)
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (expanded) AppPalette.copy(alpha = 0.18f) else Color(0xFF212636)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                    tint = AppPalette,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = if (reduceMotion) androidx.compose.animation.EnterTransition.None else expandVertically(),
            exit = if (reduceMotion) androidx.compose.animation.ExitTransition.None else shrinkVertically(),
        ) {
            Column(modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Row(
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(AppPalette),
        )
        Text(
            text = text,
            color = AppPalette,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.4.sp,
        )
    }
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
            onCheckedChange = {
                UiSoundEngine.playToggleSound(targetContext, it)
                onCheckedChange(it)
            },
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
private fun PermissionStatusRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    testTag: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF12161D))
            .clickable { onAction() }
            .testTag(testTag)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color(0xFFB3B8C4), fontSize = 11.sp)
        }
        Text(
            text = if (granted) "Granted" else "Allow",
            color = if (granted) Color(0xFF81C784) else AppPalette,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
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
