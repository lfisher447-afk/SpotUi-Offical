package com.music.spotui.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.spotui.data.preferences.isAutoDownloadUpdatesEnabled
import com.music.spotui.data.update.UpdateChecker
import com.music.spotui.data.update.UpdateDownloader
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import kotlin.OptIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Refresh

@Composable
fun UpdatePrompt() {
    val context = LocalContext.current
    var update by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var autoTriggeredVersion by remember { mutableStateOf<String?>(null) }
    var autoOpenedInstallerVersion by remember { mutableStateOf<String?>(null) }
    var installPermissionNeeded by remember { mutableStateOf(false) }
    var installerErrorMessage by remember { mutableStateOf<String?>(null) }
    val downloadState by UpdateDownloader.state.collectAsStateWithLifecycle()
    val pendingConfirmIntent by UpdateDownloader.pendingConfirmIntent.collectAsStateWithLifecycle()

    LaunchedEffect(pendingConfirmIntent) {
        val confirmIntent = UpdateDownloader.consumePendingConfirmIntent() ?: return@LaunchedEffect
        runCatching {
            context.startActivity(confirmIntent)
        }.onFailure { err ->
            installerErrorMessage = err.message ?: "Unable to open Android PackageInstaller dialog"
        }
    }

    val unknownSourcesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (UpdateDownloader.canRequestPackageInstalls(context)) {
            installPermissionNeeded = false
            val completed = downloadState as? UpdateDownloader.DownloadState.Completed
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

    LaunchedEffect(Unit) {
        update = UpdateChecker.check(context)
    }

    val info = update ?: return

    LaunchedEffect(info.version) {
        val canAutoDownload = isAutoDownloadUpdatesEnabled(context) &&
            (info.downloadUrl.endsWith(".apk", ignoreCase = true) ||
                info.downloadUrl.contains("/releases/", ignoreCase = true))
        if (canAutoDownload && autoTriggeredVersion != info.version) {
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

    AlertDialog(
        onDismissRequest = { update = null },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        containerColor = Color(0xFF141419),
        titleContentColor = Color.White,
        title = {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = Color(0xFF618DFF)
                )
                Text("Update available — ${info.version}")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Installed: v${com.music.spotui.BuildConfig.VERSION_NAME}  →  Latest: v${info.version}",
                    color = Color(0xFF1DB954),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (info.apkFileName.isNotBlank() || info.expectedSha256.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1D1F28)),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
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
                                Text(
                                    text = "SHA-256: ${info.expectedSha256.take(24)}…",
                                    color = Color(0xFF9ED4FF),
                                    fontFamily = FontFamily.Monospace,
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

                if (activeDownloadForVersion) {
                    Spacer(modifier = Modifier.height(8.dp))
                    InAppUpdateStatusCard(
                        state = downloadState,
                        installPermissionNeeded = installPermissionNeeded,
                        installerErrorMessage = installerErrorMessage,
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                if (info.releaseBody.isNotBlank()) {
                    RenderMarkdown(info.releaseBody)
                } else {
                    Text(
                        text = "A new release (v${info.version}) is available on GitHub.",
                        color = Color(0xFFB3B3B3),
                        fontSize = 13.sp,
                    )
                }
            }
        },
        confirmButton = {
            when (val st = downloadState) {
                is UpdateDownloader.DownloadState.Completed if st.version == info.version -> {
                    if (st.signatureMismatch || st.downgradeBlocked) {
                        TextButton(
                            onClick = { UpdateDownloader.openDownloadsFolder(context) },
                            modifier = Modifier.heightIn(min = 48.dp),
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
                            modifier = Modifier
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
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("update_install_apk_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Default.InstallMobile,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (installPermissionNeeded) "Allow & Install" else "Install Update",
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                is UpdateDownloader.DownloadState.Downloading if st.version == info.version -> {
                    TextButton(
                        onClick = {
                            com.music.spotui.audio.UiSoundEngine.playTapChime(context)
                            UpdateDownloader.cancelDownload(context)
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("update_cancel_download_button"),
                    ) {
                        Text("Cancel Download", color = Color(0xFFFF7A59), fontWeight = FontWeight.Bold)
                    }
                }
                is UpdateDownloader.DownloadState.Verifying if st.version == info.version -> {
                    TextButton(onClick = {}, enabled = false) {
                        Text("Verifying…", color = Color(0xFF9ED4FF))
                    }
                }
                is UpdateDownloader.DownloadState.Installing if st.version == info.version -> {
                    TextButton(onClick = {}, enabled = false) {
                        Text("Installing…", color = Color(0xFF1DB954), fontWeight = FontWeight.Bold)
                    }
                }
                is UpdateDownloader.DownloadState.Installed if st.version == info.version -> {
                    Button(
                        onClick = { update = null },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1DB954),
                            contentColor = Color.Black,
                        ),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold)
                    }
                }
                else -> {
                    val failedSt = downloadState as? UpdateDownloader.DownloadState.Failed
                    val fallback = failedSt?.fallbackCompleted
                    if (fallback != null && (fallback.signatureMismatch || fallback.downgradeBlocked)) {
                        TextButton(
                            onClick = { UpdateDownloader.openDownloadsFolder(context) },
                            modifier = Modifier.heightIn(min = 48.dp),
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
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text("Uninstall Old App", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        if (fallback != null) {
                            TextButton(
                                onClick = {
                                    UpdateDownloader.openFileProviderFallbackInstaller(context, fallback)
                                },
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) {
                                Text("System Fallback", color = Color(0xFF9ED4FF), fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Button(
                            onClick = {
                                com.music.spotui.audio.UiSoundEngine.playTapChime(context)
                                autoOpenedInstallerVersion = null
                                if (fallback != null) {
                                    triggerPackageInstaller(fallback, ignoreCompatCheck = true)
                                } else {
                                    UpdateDownloader.startDownload(context, info, force = true)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1DB954),
                                contentColor = Color.Black,
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("update_download_in_app_button"),
                        ) {
                            Text(
                                text = when {
                                    fallback != null -> "Retry Installer"
                                    failedSt != null -> "Retry In-App"
                                    else -> "Download & Install"
                                },
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                UpdateChecker.skipRelease(context, info)
                update = null
            }) {
                Text("Don't show again", color = Color(0xFFB3B3B3))
            }
            TextButton(onClick = { update = null }) {
                Text(
                    if (downloadState is UpdateDownloader.DownloadState.Downloading) "Hide" else "Dismiss",
                    color = Color.White,
                )
            }
        },
    )
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
        shape = RoundedCornerShape(10.dp),
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
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
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
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
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
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun UpdateBullet(title: String, desc: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.Top) {
        Text("•", color = Color(0xFF618DFF), modifier = Modifier.width(12.dp), fontWeight = FontWeight.Bold)
        Column {
            Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(desc, color = Color.Gray, fontSize = 11.sp, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun FutureMilestoneTile(version: String, title: String, items: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C24)),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF618DFF).copy(alpha = 0.2f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(version, color = Color(0xFF618DFF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            items.forEach { item ->
                Text("◦ $item", color = Color.LightGray, fontSize = 11.sp, lineHeight = 14.sp)
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
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        images.forEach { image ->
            val weight = image.widthPercent ?: (1f / images.size)
            Box(
                modifier = Modifier
                    .weight(weight)
                    .aspectRatio(aspectRatio)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onImageClick(image.url) }
            ) {
                GlideImage(
                    model = image.url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    failure = placeholder(com.music.spotui.R.drawable.placeholder),
                    loading = placeholder(com.music.spotui.R.drawable.placeholder),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun ZoomableImageDialog(url: String, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        var scale by remember { mutableStateOf(1f) }
        var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
        val state = rememberTransformableState { zoomChange, offsetChange, _ ->
            scale = (scale * zoomChange).coerceIn(1f, 5f)
            if (scale > 1f) {
                offset += offsetChange
            } else {
                offset = androidx.compose.ui.geometry.Offset.Zero
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onDismiss() })
                }
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
                        translationY = offset.y
                    )
                    .transformable(state = state)
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun RenderMarkdown(markdown: String) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val linkColor = Color(0xFF1DB954)
    val headingColor = Color.White
    val bodyColor = Color(0xFFB3B3B3)

    var enlargedImageUrl by remember { mutableStateOf<String?>(null) }

    val lines = markdown.lines()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        // Look ahead for image block
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
            trimmed == "---" || trimmed == "***" || trimmed == "___"
                || (trimmed.length >= 3 && trimmed.all { it == '_' || it == '-' || it == '*' }) -> {
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
    uriHandler: androidx.compose.ui.platform.UriHandler,
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
                    }
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
                    pushStyle(SpanStyle(
                        color = Color(0xFFE0E0E0),
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 13.sp,
                    ))
                    append(seg.content)
                    pop()
                }
                "link" -> {
                    pushStringAnnotation(tag = "URL", annotation = seg.url ?: "")
                    pushStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                    append(seg.content)
                    pop()
                    pop()
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

    ClickableText(
        text = annotated,
        style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
        modifier = Modifier.padding(vertical = 2.dp),
        onClick = { offset ->
            annotated.getStringAnnotations("URL", offset, offset)
                .firstOrNull()?.let { uriHandler.openUri(it.item) }
        },
    )
}
