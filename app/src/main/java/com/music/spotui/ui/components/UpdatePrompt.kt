package com.music.spotui.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.music.spotui.BuildConfig
import com.music.spotui.R
import com.music.spotui.data.preferences.isAutoDownloadUpdatesEnabled
import com.music.spotui.data.update.AutoUpdaterLogger
import com.music.spotui.data.update.UpdateChecker
import com.music.spotui.data.update.UpdateDownloader
import kotlinx.coroutines.launch

private enum class UpdateModalTab(val label: String) {
    WHATS_NEW("What's New"),
    FULL_NOTES("Release Notes"),
    CONFLICT_LOG("Conflict Log"),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UpdatePrompt() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    val latestAvailable by UpdateChecker.latestAvailable.collectAsStateWithLifecycle()
    val downloadState by UpdateDownloader.state.collectAsStateWithLifecycle()
    val pendingConfirmIntent by UpdateDownloader.pendingConfirmIntent.collectAsStateWithLifecycle()
    val updaterLogs by AutoUpdaterLogger.entries.collectAsStateWithLifecycle()

    var dismissedFingerprint by remember { mutableStateOf<String?>(null) }
    var activeTab by remember { mutableStateOf(UpdateModalTab.WHATS_NEW) }
    var autoTriggeredVersion by remember { mutableStateOf<String?>(null) }
    var autoOpenedInstallerVersion by remember { mutableStateOf<String?>(null) }
    var installPermissionNeeded by remember { mutableStateOf(false) }
    var installerErrorMessage by remember { mutableStateOf<String?>(null) }

    // Background update check every time the user opens or returns to SpotUI (with 15s cooldown in UpdateChecker)
    DisposableEffect(lifecycleOwner, context) {
        AutoUpdaterLogger.initialize(context)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                scope.launch {
                    val found = UpdateChecker.checkOnAppOpen(
                        context = context,
                        force = false,
                        trigger = "app-open",
                    )
                    if (found != null && found.fingerprint != dismissedFingerprint) {
                        // Latest update available in UpdateChecker.latestAvailable; modal displays automatically
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(pendingConfirmIntent) {
        val confirmIntent = UpdateDownloader.consumePendingConfirmIntent() ?: return@LaunchedEffect
        AutoUpdaterLogger.log(
            context,
            stage = "PackageInstaller",
            severity = AutoUpdaterLogger.Severity.INFO,
            message = "Presenting Android PackageInstaller confirmation dialog in foreground UI",
        )
        runCatching {
            context.startActivity(confirmIntent)
        }.onFailure { err ->
            val msg = err.message ?: "Unable to open Android PackageInstaller dialog"
            installerErrorMessage = msg
            AutoUpdaterLogger.log(
                context,
                stage = "PackageInstaller",
                severity = AutoUpdaterLogger.Severity.ERROR,
                message = "Foreground PackageInstaller launch failed: $msg",
            )
        }
    }

    val unknownSourcesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val granted = UpdateDownloader.canRequestPackageInstalls(context)
        AutoUpdaterLogger.log(
            context,
            stage = "Permission",
            severity = if (granted) AutoUpdaterLogger.Severity.SUCCESS else AutoUpdaterLogger.Severity.WARNING,
            message = if (granted) {
                "Install Unknown Apps permission granted — resuming installer"
            } else {
                "Install Unknown Apps permission still disabled"
            },
        )
        if (granted) {
            installPermissionNeeded = false
            val completed = (downloadState as? UpdateDownloader.DownloadState.Completed)
                ?: (downloadState as? UpdateDownloader.DownloadState.Failed)?.fallbackCompleted
                ?: UpdateDownloader.peekLatestCompleted()
            if (completed != null) {
                when (val res = UpdateDownloader.installApk(context, completed)) {
                    is UpdateDownloader.InstallLaunchResult.Launched -> installerErrorMessage = null
                    is UpdateDownloader.InstallLaunchResult.PermissionRequired -> installPermissionNeeded = true
                    is UpdateDownloader.InstallLaunchResult.Failed -> installerErrorMessage = res.reason
                }
            }
        }
    }

    fun triggerPackageInstaller(
        completed: UpdateDownloader.DownloadState.Completed,
        ignoreCompatCheck: Boolean = false,
    ) {
        when (val res = UpdateDownloader.installApk(context, completed, ignoreCompatCheck = ignoreCompatCheck)) {
            is UpdateDownloader.InstallLaunchResult.Launched -> {
                installPermissionNeeded = false
                installerErrorMessage = null
            }
            is UpdateDownloader.InstallLaunchResult.PermissionRequired -> {
                installPermissionNeeded = true
                installerErrorMessage = null
            }
            is UpdateDownloader.InstallLaunchResult.Failed -> {
                installerErrorMessage = res.reason
            }
        }
    }

    fun launchSystemFallbackInstaller(completed: UpdateDownloader.DownloadState.Completed) {
        when (val res = UpdateDownloader.openFileProviderFallbackInstaller(context, completed)) {
            is UpdateDownloader.InstallLaunchResult.Launched -> {
                installPermissionNeeded = false
                installerErrorMessage = null
            }
            is UpdateDownloader.InstallLaunchResult.PermissionRequired -> {
                installPermissionNeeded = true
                unknownSourcesLauncher.launch(
                    UpdateDownloader.buildUnknownAppSourcesIntent(context, newTask = false),
                )
            }
            is UpdateDownloader.InstallLaunchResult.Failed -> {
                installerErrorMessage = res.reason
            }
        }
    }

    val info = latestAvailable?.takeIf { it.fingerprint != dismissedFingerprint } ?: return

    LaunchedEffect(info.version) {
        val canAutoDownload = isAutoDownloadUpdatesEnabled(context) &&
            (info.downloadUrl.endsWith(".apk", ignoreCase = true) ||
                info.downloadUrl.contains("/releases/", ignoreCase = true))
        val signingEval = UpdateDownloader.evaluateRemoteSigningCompatibility(context, info)
        if (canAutoDownload &&
            autoTriggeredVersion != info.version &&
            signingEval.status != UpdateDownloader.RemoteSigningCompatibility.MISMATCH
        ) {
            autoTriggeredVersion = info.version
            UpdateDownloader.startDownload(context, info)
        }
    }

    LaunchedEffect(downloadState) {
        val st = downloadState
        if (st is UpdateDownloader.DownloadState.Completed &&
            st.version == info.version &&
            !st.signatureMismatch &&
            !st.downgradeBlocked &&
            autoOpenedInstallerVersion != info.version
        ) {
            autoOpenedInstallerVersion = info.version
            triggerPackageInstaller(st)
        }
    }

    val activeDownloadForVersion = when (val st = downloadState) {
        is UpdateDownloader.DownloadState.Downloading -> st.version == info.version
        is UpdateDownloader.DownloadState.Verifying -> st.version == info.version
        is UpdateDownloader.DownloadState.Completed -> st.version == info.version
        is UpdateDownloader.DownloadState.Installing -> st.version == info.version
        is UpdateDownloader.DownloadState.Installed -> st.version == info.version
        is UpdateDownloader.DownloadState.Failed -> st.version == info.version
        else -> false
    }

    val signingEval = remember(info.fingerprint, info.remoteSigningKeyId, info.remoteSigningSource) {
        UpdateDownloader.evaluateRemoteSigningCompatibility(context, info)
    }
    val conflictEventsCount = remember(updaterLogs) {
        updaterLogs.count {
            it.severity == AutoUpdaterLogger.Severity.CONFLICT ||
                it.severity == AutoUpdaterLogger.Severity.ERROR ||
                it.severity == AutoUpdaterLogger.Severity.WARNING
        }
    }

    Dialog(
        onDismissRequest = { dismissedFingerprint = info.fingerprint },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .widthIn(max = 580.dp)
                .testTag("update_prompt_dialog"),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF13151C),
            tonalElevation = 8.dp,
            border = BorderStroke(1.dp, Color(0xFF2B3142)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1A2334).copy(alpha = 0.72f),
                                Color(0xFF13151C),
                                Color(0xFF111319),
                            ),
                        ),
                    )
                    .padding(18.dp),
            ) {
                // Expressive Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF1DB954), Color(0xFF3E7BFA)),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SystemUpdate,
                            contentDescription = "Update Available",
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (info.releaseTitle.isNotBlank() && info.releaseTitle != "v${info.version}") {
                                info.releaseTitle
                            } else {
                                "SpotUI Update Ready"
                            },
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            StatusPill(
                                text = "v${BuildConfig.VERSION_NAME} → v${info.version}",
                                containerColor = Color(0xFF1DB954).copy(alpha = 0.18f),
                                contentColor = Color(0xFF56E38B),
                            )
                            StatusPill(
                                text = if (info.isPrerelease) "Pre-Release" else "Stable Release",
                                containerColor = if (info.isPrerelease) {
                                    Color(0xFFFFB74D).copy(alpha = 0.18f)
                                } else {
                                    Color(0xFF618DFF).copy(alpha = 0.18f)
                                },
                                contentColor = if (info.isPrerelease) Color(0xFFFFCA7A) else Color(0xFF9BB8FF),
                            )
                        }
                    }

                    IconButton(
                        onClick = { dismissedFingerprint = info.fingerprint },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("update_prompt_close_icon"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close update modal",
                            tint = Color(0xFFB5BCCE),
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Expressive Navigation Chips (What's New / Full Notes / Conflict Log)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    UpdateModalTab.entries.forEach { tab ->
                        val selected = activeTab == tab
                        val labelText = if (tab == UpdateModalTab.CONFLICT_LOG && conflictEventsCount > 0) {
                            "${tab.label} ($conflictEventsCount)"
                        } else {
                            tab.label
                        }
                        FilterChip(
                            selected = selected,
                            onClick = { activeTab = tab },
                            label = {
                                Text(
                                    text = labelText,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = when (tab) {
                                        UpdateModalTab.WHATS_NEW -> Icons.Rounded.AutoAwesome
                                        UpdateModalTab.FULL_NOTES -> Icons.Rounded.NewReleases
                                        UpdateModalTab.CONFLICT_LOG -> Icons.Rounded.Terminal
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF1DB954).copy(alpha = 0.22f),
                                selectedLabelColor = Color(0xFF69EB99),
                                selectedLeadingIconColor = Color(0xFF69EB99),
                                containerColor = Color(0xFF1B1E28),
                                labelColor = Color(0xFFB9C1D1),
                                iconColor = Color(0xFF8C96AA),
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = Color(0xFF2D3344),
                                selectedBorderColor = Color(0xFF1DB954).copy(alpha = 0.55f),
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("update_tab_${tab.name.lowercase()}"),
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (activeDownloadForVersion) {
                        InAppUpdateStatusCard(
                            state = downloadState,
                            installPermissionNeeded = installPermissionNeeded,
                            installerErrorMessage = installerErrorMessage,
                        )
                    }

                    when (activeTab) {
                        UpdateModalTab.WHATS_NEW -> {
                            SigningKeyCompatibilityCard(eval = signingEval)

                            if (info.apkFileName.isNotBlank() || info.expectedSha256.isNotBlank()) {
                                PackageMetadataCard(context = context, info = info)
                            }

                            val sections = info.changelogSections
                            if (sections.hasStructuredItems) {
                                if (sections.features.isNotEmpty()) {
                                    ChangelogCategoryCard(
                                        title = "New Features",
                                        icon = Icons.Rounded.NewReleases,
                                        accent = Color(0xFF56E38B),
                                        items = sections.features,
                                    )
                                }
                                if (sections.changes.isNotEmpty()) {
                                    ChangelogCategoryCard(
                                        title = "Improvements & Changes",
                                        icon = Icons.Rounded.AutoAwesome,
                                        accent = Color(0xFF86B4FF),
                                        items = sections.changes,
                                    )
                                }
                                if (sections.fixes.isNotEmpty()) {
                                    ChangelogCategoryCard(
                                        title = "Fixes & Stability",
                                        icon = Icons.Rounded.Build,
                                        accent = Color(0xFFFFC56D),
                                        items = sections.fixes,
                                    )
                                }
                                if (sections.otherNotes.isNotEmpty()) {
                                    ChangelogCategoryCard(
                                        title = "Additional Notes",
                                        icon = Icons.Rounded.VerifiedUser,
                                        accent = Color(0xFFC5B4FF),
                                        items = sections.otherNotes,
                                    )
                                }
                            } else if (info.releaseBody.isNotBlank()) {
                                RenderMarkdown(info.releaseBody)
                            } else {
                                Text(
                                    text = "A new release (v${info.version}) is available on GitHub.",
                                    color = Color(0xFFB3B3B3),
                                    fontSize = 13.sp,
                                )
                            }
                        }

                        UpdateModalTab.FULL_NOTES -> {
                            if (info.releaseBody.isNotBlank()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(18.dp),
                                    color = Color(0xFF181B24),
                                    border = BorderStroke(1.dp, Color(0xFF282D3B)),
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        RenderMarkdown(info.releaseBody)
                                    }
                                }
                            } else {
                                Text(
                                    text = "No detailed release notes provided for v${info.version}.",
                                    color = Color(0xFFB3B3B3),
                                    fontSize = 13.sp,
                                )
                            }
                        }

                        UpdateModalTab.CONFLICT_LOG -> {
                            UpdaterConflictLogPanel(
                                context = context,
                                entries = updaterLogs,
                                latestInfo = info,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFF262B38))
                Spacer(Modifier.height(12.dp))

                // Expressive Action Footer
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        when (val st = downloadState) {
                            is UpdateDownloader.DownloadState.Completed if st.version == info.version -> {
                                if (st.signatureMismatch || st.downgradeBlocked) {
                                    OutlinedButton(
                                        onClick = { UpdateDownloader.openDownloadsFolder(context) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("update_open_downloads_button"),
                                        shape = RoundedCornerShape(16.dp),
                                    ) {
                                        Text("Downloads", color = Color(0xFF9ED4FF), fontWeight = FontWeight.SemiBold)
                                    }
                                    Button(
                                        onClick = {
                                            com.music.spotui.audio.UiSoundEngine.playTapChime(context)
                                            UpdateDownloader.triggerUninstallCurrentApp(context)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFFFB74D),
                                            contentColor = Color.Black,
                                        ),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .weight(1.35f)
                                            .heightIn(min = 48.dp)
                                            .testTag("update_uninstall_old_button"),
                                    ) {
                                        Text("Uninstall Old App", fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            com.music.spotui.audio.UiSoundEngine.playTapChime(context)
                                            if (!UpdateDownloader.canRequestPackageInstalls(context)) {
                                                unknownSourcesLauncher.launch(
                                                    UpdateDownloader.buildUnknownAppSourcesIntent(context, newTask = false),
                                                )
                                            } else {
                                                triggerPackageInstaller(st)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF1DB954),
                                            contentColor = Color.Black,
                                        ),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .testTag("update_install_apk_button"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.InstallMobile,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = if (installPermissionNeeded) "Allow & Install In-Place" else "Install Update In-Place",
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }

                            is UpdateDownloader.DownloadState.Downloading if st.version == info.version -> {
                                OutlinedButton(
                                    onClick = {
                                        com.music.spotui.audio.UiSoundEngine.playTapChime(context)
                                        UpdateDownloader.cancelDownload(context)
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("update_cancel_download_button"),
                                ) {
                                    Text("Cancel Download", color = Color(0xFFFF7A59), fontWeight = FontWeight.Bold)
                                }
                            }

                            is UpdateDownloader.DownloadState.Verifying if st.version == info.version -> {
                                Button(
                                    onClick = {},
                                    enabled = false,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp),
                                ) {
                                    Text("Verifying SHA-256 & Key…", color = Color(0xFF9ED4FF))
                                }
                            }

                            is UpdateDownloader.DownloadState.Installing if st.version == info.version -> {
                                val readyFallback = UpdateDownloader.peekLatestCompleted()
                                if (readyFallback != null && !readyFallback.signatureMismatch && !readyFallback.downgradeBlocked) {
                                    OutlinedButton(
                                        onClick = { launchSystemFallbackInstaller(readyFallback) },
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp),
                                    ) {
                                        Text("System Installer", color = Color(0xFF9ED4FF), fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                Button(
                                    onClick = {},
                                    enabled = false,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp),
                                ) {
                                    Text("Installing…", color = Color(0xFF56E38B), fontWeight = FontWeight.Bold)
                                }
                            }

                            is UpdateDownloader.DownloadState.Installed if st.version == info.version -> {
                                Button(
                                    onClick = { dismissedFingerprint = info.fingerprint },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF1DB954),
                                        contentColor = Color.Black,
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp),
                                ) {
                                    Text("Done", fontWeight = FontWeight.Bold)
                                }
                            }

                            else -> {
                                val failedSt = downloadState as? UpdateDownloader.DownloadState.Failed
                                val fallback = failedSt?.fallbackCompleted
                                if (fallback != null && (fallback.signatureMismatch || fallback.downgradeBlocked)) {
                                    OutlinedButton(
                                        onClick = { UpdateDownloader.openDownloadsFolder(context) },
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp),
                                    ) {
                                        Text("Downloads", color = Color(0xFF9ED4FF), fontWeight = FontWeight.SemiBold)
                                    }
                                    Button(
                                        onClick = {
                                            com.music.spotui.audio.UiSoundEngine.playTapChime(context)
                                            UpdateDownloader.triggerUninstallCurrentApp(context)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFFFB74D),
                                            contentColor = Color.Black,
                                        ),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .weight(1.35f)
                                            .heightIn(min = 48.dp)
                                            .testTag("update_uninstall_old_button"),
                                    ) {
                                        Text("Uninstall Old App", fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    if (fallback != null) {
                                        OutlinedButton(
                                            onClick = { launchSystemFallbackInstaller(fallback) },
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp),
                                        ) {
                                            Text("System Fallback", color = Color(0xFF9ED4FF), fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            com.music.spotui.audio.UiSoundEngine.playTapChime(context)
                                            autoOpenedInstallerVersion = null
                                            if (fallback != null) {
                                                if (!UpdateDownloader.canRequestPackageInstalls(context)) {
                                                    unknownSourcesLauncher.launch(
                                                        UpdateDownloader.buildUnknownAppSourcesIntent(context, newTask = false),
                                                    )
                                                } else {
                                                    triggerPackageInstaller(fallback, ignoreCompatCheck = false)
                                                }
                                            } else {
                                                UpdateDownloader.startDownload(context, info, force = true)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF1DB954),
                                            contentColor = Color.Black,
                                        ),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .weight(1.35f)
                                            .heightIn(min = 48.dp)
                                            .testTag("update_download_in_app_button"),
                                    ) {
                                        Text(
                                            text = when {
                                                fallback != null && installPermissionNeeded -> "Allow & Install"
                                                fallback != null -> "Retry Installer"
                                                failedSt != null -> "Retry Download"
                                                else -> "Download & Install"
                                            },
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {
                                UpdateChecker.skipRelease(context, info)
                                dismissedFingerprint = info.fingerprint
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("update_skip_release_button"),
                        ) {
                            Text("Skip This Version", color = Color(0xFF9BA3B5), fontSize = 12.sp)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (info.releasePageUrl.isNotBlank()) {
                                TextButton(
                                    onClick = {
                                        runCatching { uriHandler.openUri(info.releasePageUrl) }
                                    },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("update_open_release_page_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                        contentDescription = null,
                                        tint = Color(0xFF9ED4FF),
                                        modifier = Modifier.size(15.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("GitHub", color = Color(0xFF9ED4FF), fontSize = 12.sp)
                                }
                            }
                            TextButton(
                                onClick = { dismissedFingerprint = info.fingerprint },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("update_dismiss_button"),
                            ) {
                                Text(
                                    text = if (downloadState is UpdateDownloader.DownloadState.Downloading) "Hide" else "Later",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(containerColor)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            color = contentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SigningKeyCompatibilityCard(eval: UpdateDownloader.RemoteSigningEvaluation) {
    val (bg, border, accent, icon, title) = when (eval.status) {
        UpdateDownloader.RemoteSigningCompatibility.COMPATIBLE -> FiveTuple(
            Color(0xFF14241B),
            Color(0xFF1DB954).copy(alpha = 0.45f),
            Color(0xFF56E38B),
            Icons.Rounded.VerifiedUser,
            "Signing Key Verified — In-Place Update Ready",
        )
        UpdateDownloader.RemoteSigningCompatibility.MISMATCH -> FiveTuple(
            Color(0xFF2B1C16),
            Color(0xFFFF8A65).copy(alpha = 0.55f),
            Color(0xFFFFAB91),
            Icons.Rounded.ErrorOutline,
            "Signing Key Mismatch Detected Pre-Flight",
        )
        UpdateDownloader.RemoteSigningCompatibility.EPHEMERAL_RISK -> FiveTuple(
            Color(0xFF282012),
            Color(0xFFFFC107).copy(alpha = 0.48f),
            Color(0xFFFFD54F),
            Icons.Rounded.WarningAmber,
            "Ephemeral CI Keystore Notice",
        )
        UpdateDownloader.RemoteSigningCompatibility.UNKNOWN -> FiveTuple(
            Color(0xFF191D29),
            Color(0xFF618DFF).copy(alpha = 0.35f),
            Color(0xFF9BB8FF),
            Icons.Rounded.Key,
            "Pre-Install Signing Certificate Check",
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = bg,
        border = BorderStroke(1.dp, border),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                Text(title, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            if (eval.installedShortId.isNotBlank() || eval.remoteShortId.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (eval.installedShortId.isNotBlank()) {
                        Text(
                            text = "Installed: ${eval.installedShortId}…",
                            color = Color(0xFFCFD6E4),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        )
                    }
                    if (eval.remoteShortId.isNotBlank()) {
                        Text(
                            text = "Release: ${eval.remoteShortId}…",
                            color = Color(0xFFCFD6E4),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
            Text(
                text = eval.summary,
                color = Color(0xFFD9DFEC),
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        }
    }
}

private data class FiveTuple<A, B, C, D, E>(
    val a: A,
    val b: B,
    val c: C,
    val d: D,
    val e: E,
)

@Composable
private fun PackageMetadataCard(
    context: Context,
    info: UpdateChecker.UpdateInfo,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF1A1D27),
        border = BorderStroke(1.dp, Color(0xFF2B3142)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val sizeLabel = if (info.apkSizeBytes > 0L) {
                " · %.1f MB".format(info.apkSizeBytes / (1024.0 * 1024.0))
            } else ""
            if (info.apkFileName.isNotBlank()) {
                Text(
                    text = "${info.apkFileName}$sizeLabel",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (info.expectedSha256.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("SpotUI SHA256", info.expectedSha256))
                            Toast.makeText(context, "SHA-256 digest copied", Toast.LENGTH_SHORT).show()
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = Color(0xFF9ED4FF),
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "SHA-256: ${info.expectedSha256.take(24)}… (tap to copy)",
                        color = Color(0xFF9ED4FF),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChangelogCategoryCard(
    title: String,
    icon: ImageVector,
    accent: Color,
    items: List<String>,
) {
    var expanded by remember { mutableStateOf(true) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF181B24),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 36.dp)
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
                }
                Text(
                    text = "$title (${items.size})",
                    color = accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (expanded) "Hide" else "Show",
                    color = Color(0xFF8C96AA),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items.forEach { bullet ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(accent),
                            )
                            Text(
                                text = bullet,
                                color = Color(0xFFE3E7F0),
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UpdaterConflictLogPanel(
    context: Context,
    entries: List<AutoUpdaterLogger.UpdaterLogEntry>,
    latestInfo: UpdateChecker.UpdateInfo?,
) {
    val installedCert = remember { UpdateDownloader.getInstalledSigningCertInfo(context) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("updater_conflict_log_panel"),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF171A23),
        border = BorderStroke(1.dp, Color(0xFF2B3142)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-Updater Conflict & Install Log",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Installed Signing ID: ${installedCert?.shortId ?: "unknown"}…",
                        color = Color(0xFF9EA7BA),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = {
                            val report = AutoUpdaterLogger.exportReport(
                                context = context,
                                installedCert = installedCert,
                                latestInfo = latestInfo,
                            )
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("SpotUI AutoUpdater Log", report))
                            Toast.makeText(context, "Updater diagnostic log copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("copy_updater_log_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Copy updater log",
                            tint = Color(0xFF9ED4FF),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(
                        onClick = { AutoUpdaterLogger.clear(context) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("clear_updater_log_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Clear updater log",
                            tint = Color(0xFFFF8A65),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            if (entries.isEmpty()) {
                Text(
                    text = "No update events or conflicts recorded yet.",
                    color = Color(0xFF8C96AA),
                    fontSize = 12.sp,
                )
            } else {
                entries.take(30).forEach { entry ->
                    val badgeColor = when (entry.severity) {
                        AutoUpdaterLogger.Severity.CONFLICT -> Color(0xFFFF8A65)
                        AutoUpdaterLogger.Severity.ERROR -> Color(0xFFFF5252)
                        AutoUpdaterLogger.Severity.WARNING -> Color(0xFFFFCA28)
                        AutoUpdaterLogger.Severity.SUCCESS -> Color(0xFF56E38B)
                        AutoUpdaterLogger.Severity.INFO -> Color(0xFF86B4FF)
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF11131A),
                        border = BorderStroke(
                            1.dp,
                            if (entry.severity == AutoUpdaterLogger.Severity.CONFLICT ||
                                entry.severity == AutoUpdaterLogger.Severity.ERROR
                            ) {
                                badgeColor.copy(alpha = 0.45f)
                            } else {
                                Color(0xFF232836)
                            },
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(9.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${entry.severity.label} · ${entry.stage}",
                                    color = badgeColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Text(
                                    text = entry.formattedTime,
                                    color = Color(0xFF7B8498),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                            Text(
                                text = entry.message,
                                color = Color(0xFFE6EAF3),
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                            )
                            if (entry.details.isNotBlank()) {
                                Text(
                                    text = entry.details,
                                    color = Color(0xFF9EA7BA),
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InAppUpdateStatusCard(
    state: UpdateDownloader.DownloadState,
    installPermissionNeeded: Boolean,
    installerErrorMessage: String?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF191D26)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when (state) {
                is UpdateDownloader.DownloadState.Downloading -> {
                    val pctText = if (state.progressFraction >= 0f) {
                        "${(state.progressFraction * 100).toInt()}%"
                    } else "Streaming…"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Downloading update inside app…", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(pctText, color = Color(0xFF1DB954), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    if (state.progressFraction >= 0f) {
                        LinearProgressIndicator(
                            progress = { state.progressFraction },
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFF1DB954),
                            trackColor = Color(0xFF2B303D),
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFF1DB954),
                            trackColor = Color(0xFF2B303D),
                        )
                    }
                    val totalStr = if (state.totalBytes > 0L) " / ${UpdateDownloader.formatBytes(state.totalBytes)}" else ""
                    val eta = UpdateDownloader.formatEta(state.etaSeconds)
                    val etaPart = if (eta.isNotBlank()) " · $eta" else ""
                    Text(
                        text = "${UpdateDownloader.formatBytes(state.downloadedBytes)}$totalStr · ${UpdateDownloader.formatSpeed(state.speedBytesPerSec)}$etaPart",
                        color = Color(0xFFB3B8C4),
                        fontSize = 11.sp,
                    )
                }
                is UpdateDownloader.DownloadState.Verifying -> {
                    Text("Verifying APK integrity & SHA-256…", color = Color(0xFF9ED4FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFF9ED4FF),
                        trackColor = Color(0xFF2B303D),
                    )
                }
                is UpdateDownloader.DownloadState.Completed -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (state.signatureMismatch || state.downgradeBlocked) Color(0xFFFFB74D) else Color(0xFF1DB954),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = when {
                                state.signatureMismatch || state.downgradeBlocked -> "Action Needed: Signature / Version Conflict"
                                state.sha256Verified == true -> "Ready to Install (SHA-256 Verified)"
                                else -> "Ready to Install (APK Verified)"
                            },
                            color = if (state.signatureMismatch || state.downgradeBlocked) Color(0xFFFFB74D) else Color(0xFF1DB954),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = if (state.compatWarning.isNotBlank()) {
                            "${state.compatWarning} Backup saved at ${state.savedDisplayPath}."
                        } else {
                            "Android updates in-place over your current SpotUI install while keeping your library & settings. Backup copy saved in ${state.savedDisplayPath}."
                        },
                        color = if (state.signatureMismatch || state.downgradeBlocked) Color(0xFFFFE0B2) else Color(0xFFD0D5E0),
                        fontSize = 11.sp,
                    )
                    if (installPermissionNeeded) {
                        Text(
                            text = "Tap 'Allow & Install' and enable 'Allow from this source' once — Android Package Installer will open immediately on return.",
                            color = Color(0xFFFFD54F),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (!installerErrorMessage.isNullOrBlank()) {
                        Text(
                            text = installerErrorMessage,
                            color = Color(0xFFFF7A59),
                            fontSize = 11.sp,
                        )
                    }
                    Text(
                        text = "SHA-256: ${state.computedSha256.take(24)}…",
                        color = Color(0xFF9ED4FF),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                    )
                }
                is UpdateDownloader.DownloadState.Installing -> {
                    val pct = (state.progressFraction.coerceIn(0f, 1f) * 100).toInt()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Android PackageInstaller Session", color = Color(0xFF1DB954), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("$pct%", color = Color(0xFF1DB954), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(
                        progress = { state.progressFraction.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFF1DB954),
                        trackColor = Color(0xFF2B303D),
                    )
                    Text(state.statusMessage, color = Color(0xFFD0D5E0), fontSize = 11.sp)
                }
                is UpdateDownloader.DownloadState.Installed -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF1DB954),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "SpotUI v${state.version} installed via Android PackageInstaller",
                            color = Color(0xFF1DB954),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                is UpdateDownloader.DownloadState.Failed -> {
                    Text("Update step failed: ${state.reason}", color = Color(0xFFFF7A59), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    if (installPermissionNeeded) {
                        Text(
                            text = "Tap 'Allow & Install' and enable 'Allow from this source' once — Android Package Installer will open immediately on return.",
                            color = Color(0xFFFFD54F),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (!installerErrorMessage.isNullOrBlank()) {
                        Text(
                            text = installerErrorMessage,
                            color = Color(0xFFFF7A59),
                            fontSize = 11.sp,
                        )
                    }
                }
                else -> Unit
            }
        }
    }
}

private val HEADER_RE = Regex("""^(#{1,6})\s+(.*)""")

private data class ImageItem(val url: String, val widthPercent: Float?)

private fun extractImages(text: String): List<ImageItem> {
    val list = mutableListOf<ImageItem>()
    val imgTags = Regex("""<img\s+[^>]+>""", RegexOption.IGNORE_CASE).findAll(text)
    for (match in imgTags) {
        val tagContent = match.value
        val srcMatch = Regex("""src=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(tagContent)
        val widthMatch = Regex("""width=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(tagContent)
        if (srcMatch != null) {
            val url = srcMatch.groupValues[1]
            val widthStr = widthMatch?.groupValues?.get(1)
            val widthPercent = widthStr?.removeSuffix("%")?.toFloatOrNull()?.let { it / 100f }
            list.add(ImageItem(url, widthPercent))
        }
    }
    val mdImgTags = Regex("""!\[([^\]]*)\]\(([^)]+)\)""").findAll(text)
    for (match in mdImgTags) {
        val url = match.groupValues[2]
        list.add(ImageItem(url, null))
    }
    return list
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun RenderImagesRow(images: List<ImageItem>, onImageClick: (String) -> Unit) {
    val aspectRatio = when (images.size) {
        1 -> 1.77f
        2 -> 1f
        else -> 0.6f
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        images.forEach { image ->
            val weight = image.widthPercent ?: (1f / images.size)
            Box(
                modifier = Modifier
                    .weight(weight)
                    .aspectRatio(aspectRatio)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onImageClick(image.url) },
            ) {
                GlideImage(
                    model = image.url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    failure = placeholder(R.drawable.placeholder),
                    loading = placeholder(R.drawable.placeholder),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun ZoomableImageDialog(url: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        var scale by remember { mutableStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val state = rememberTransformableState { zoomChange, offsetChange, _ ->
            scale = (scale * zoomChange).coerceIn(1f, 5f)
            if (scale > 1f) {
                offset += offsetChange
            } else {
                offset = Offset.Zero
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onDismiss() })
                },
        ) {
            GlideImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    )
                    .transformable(state = state),
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .minimumInteractiveComponentSize(),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun RenderMarkdown(markdown: String) {
    val uriHandler = LocalUriHandler.current
    val linkColor = Color(0xFF1DB954)
    val headingColor = Color.White
    val bodyColor = Color(0xFFB3B3B3)

    var enlargedImageUrl by remember { mutableStateOf<String?>(null) }

    val lines = markdown.lines()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        val currentImages = mutableListOf<ImageItem>()
        var j = i
        var hasImages = false
        while (j < lines.size) {
            val nextLine = lines[j].trim()
            val nextImages = extractImages(nextLine)
            val isImageContainerTag = nextLine.startsWith("<p", ignoreCase = true) ||
                nextLine.startsWith("</p", ignoreCase = true) ||
                nextLine.startsWith("<div", ignoreCase = true) ||
                nextLine.startsWith("</div", ignoreCase = true)

            if (nextImages.isNotEmpty()) {
                currentImages.addAll(nextImages)
                hasImages = true
                j++
            } else if (isImageContainerTag || nextLine.isEmpty()) {
                j++
            } else {
                break
            }
        }

        if (hasImages && currentImages.isNotEmpty()) {
            RenderImagesRow(currentImages, onImageClick = { enlargedImageUrl = it })
            i = j
            continue
        }

        when {
            trimmed.isEmpty() -> {
                Spacer(modifier = Modifier.height(6.dp))
            }
            trimmed.startsWith("<p", ignoreCase = true) ||
                trimmed.startsWith("</p", ignoreCase = true) ||
                trimmed.startsWith("<div", ignoreCase = true) ||
                trimmed.startsWith("</div", ignoreCase = true) -> {
                // Ignore container tags
            }
            trimmed == "---" || trimmed == "***" || trimmed == "___" ||
                (trimmed.length >= 3 && trimmed.all { it == '_' || it == '-' || it == '*' }) -> {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFF2A2A2A)),
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            HEADER_RE.matches(trimmed) -> {
                val match = HEADER_RE.find(trimmed)
                if (match != null && match.groupValues.size >= 3) {
                    val level = match.groupValues[1].length
                    val (size, weight) = when (level) {
                        1 -> 18.sp to FontWeight.ExtraBold
                        2 -> 16.sp to FontWeight.Bold
                        3 -> 15.sp to FontWeight.SemiBold
                        4 -> 14.sp to FontWeight.SemiBold
                        5 -> 13.sp to FontWeight.SemiBold
                        else -> 13.sp to FontWeight.Bold
                    }
                    Text(
                        text = match.groupValues[2],
                        color = headingColor,
                        fontSize = size,
                        fontWeight = weight,
                        modifier = Modifier.padding(top = if (i > 0) 10.dp else 0.dp),
                    )
                } else {
                    MarkdownInline(
                        text = trimmed,
                        prefix = "",
                        linkColor = linkColor,
                        bodyColor = bodyColor,
                        uriHandler = uriHandler,
                    )
                }
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                val item = trimmed.removePrefix("- ").removePrefix("* ")
                MarkdownInline(
                    text = item,
                    prefix = "\u2022 ",
                    linkColor = linkColor,
                    bodyColor = bodyColor,
                    uriHandler = uriHandler,
                )
            }
            Regex("""^\d+\.\s""").containsMatchIn(trimmed) -> {
                val numMatch = Regex("""^(\d+\.\s)(.*)""").find(trimmed)
                if (numMatch != null && numMatch.groupValues.size >= 3) {
                    MarkdownInline(
                        text = numMatch.groupValues[2],
                        prefix = numMatch.groupValues[1],
                        linkColor = linkColor,
                        bodyColor = bodyColor,
                        uriHandler = uriHandler,
                    )
                } else {
                    MarkdownInline(
                        text = trimmed,
                        prefix = "",
                        linkColor = linkColor,
                        bodyColor = bodyColor,
                        uriHandler = uriHandler,
                    )
                }
            }
            else -> {
                MarkdownInline(
                    text = trimmed,
                    prefix = "",
                    linkColor = linkColor,
                    bodyColor = bodyColor,
                    uriHandler = uriHandler,
                )
            }
        }
        i++
    }

    enlargedImageUrl?.let { url ->
        ZoomableImageDialog(url = url, onDismiss = { enlargedImageUrl = null })
    }
}

@Composable
private fun MarkdownInline(
    text: String,
    prefix: String,
    linkColor: Color,
    bodyColor: Color,
    uriHandler: UriHandler,
) {
    val patterns = listOf(
        Triple(Regex("""\*\*(.+?)\*\*"""), "bold", null as String?),
        Triple(Regex("""(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)"""), "italic", null as String?),
        Triple(Regex("""__(.+?)__"""), "underline", null as String?),
        Triple(Regex("""_(.+?)_"""), "underline", null as String?),
        Triple(Regex("""~~(.+?)~~"""), "strikethrough", null as String?),
        Triple(Regex("""`([^`]+)`"""), "code", null as String?),
        Triple(Regex("""\[([^\]]+)\]\(([^)]+)\)"""), "link", null as String?),
        Triple(Regex("""<a\s+[^>]*href=["']([^"']+)["'][^>]*>(.*?)</a>""", RegexOption.IGNORE_CASE), "html_link", null as String?),
        Triple(Regex("""(?<!\()(https?://[^\s<)]+)"""), "raw_link", null as String?),
    )

    val annotated = buildAnnotatedString {
        if (prefix.isNotEmpty()) {
            pushStyle(SpanStyle(color = bodyColor))
            append(prefix)
            pop()
        }

        data class Segment(val start: Int, val end: Int, val type: String, val content: String, val url: String? = null)

        val segments = mutableListOf<Segment>()
        for ((regex, type, _) in patterns) {
            for (m in regex.findAll(text)) {
                segments.add(
                    when (type) {
                        "link" -> Segment(m.range.first, m.range.last + 1, type, m.groupValues[1], m.groupValues[2])
                        "html_link" -> Segment(m.range.first, m.range.last + 1, "link", m.groupValues[2], m.groupValues[1])
                        "raw_link" -> Segment(m.range.first, m.range.last + 1, "link", m.groupValues[1], m.groupValues[1])
                        else -> Segment(m.range.first, m.range.last + 1, type, m.groupValues[1])
                    },
                )
            }
        }

        val sorted = segments.sortedBy { it.start }.distinctBy { it.start }

        var cursor = 0
        for (seg in sorted) {
            if (seg.start < cursor) continue
            if (seg.start > cursor) {
                pushStyle(SpanStyle(color = bodyColor))
                append(text.substring(cursor, seg.start))
                pop()
            }
            when (seg.type) {
                "bold" -> {
                    pushStyle(SpanStyle(color = bodyColor, fontWeight = FontWeight.Bold))
                    append(seg.content)
                    pop()
                }
                "italic" -> {
                    pushStyle(SpanStyle(color = bodyColor, fontStyle = FontStyle.Italic))
                    append(seg.content)
                    pop()
                }
                "underline" -> {
                    pushStyle(SpanStyle(color = bodyColor, textDecoration = TextDecoration.Underline))
                    append(seg.content)
                    pop()
                }
                "strikethrough" -> {
                    pushStyle(SpanStyle(color = bodyColor, textDecoration = TextDecoration.LineThrough))
                    append(seg.content)
                    pop()
                }
                "code" -> {
                    pushStyle(
                        SpanStyle(
                            color = Color(0xFFE0E0E0),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                        ),
                    )
                    append(seg.content)
                    pop()
                }
                "link" -> {
                    val url = seg.url.orEmpty()
                    if (url.isNotBlank()) {
                        pushLink(
                            LinkAnnotation.Url(
                                url = url,
                                styles = TextLinkStyles(
                                    style = SpanStyle(
                                        color = linkColor,
                                        textDecoration = TextDecoration.Underline,
                                    ),
                                ),
                                linkInteractionListener = {
                                    runCatching { uriHandler.openUri(url) }
                                },
                            ),
                        )
                        append(seg.content)
                        pop()
                    } else {
                        pushStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                        append(seg.content)
                        pop()
                    }
                }
            }
            cursor = seg.end
        }

        if (cursor < text.length) {
            pushStyle(SpanStyle(color = bodyColor))
            append(text.substring(cursor))
            pop()
        }
    }

    Text(
        text = annotated,
        style = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
        modifier = Modifier.padding(vertical = 2.dp),
    )
}
