package com.music.spotui.data.update

import android.Manifest
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.music.spotui.BuildConfig
import com.music.spotui.R
import com.music.spotui.util.DevConsoleManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * In-app release APK downloader and Android Package Installer launcher for SpotUI.
 *
 * Streams APKs directly inside the app via OkHttp (zero external browser hop), computes inline
 * SHA-256 digests, validates ZIP/APK magic bytes (`PK`), verifies against the GitHub release
 * SHA-256 digest when present, keeps a verified copy in `cache/updates/` for FileProvider-backed
 * Android Package Installer updates, and also exports a copy into `Downloads/SpotUI/`.
 */
object UpdateDownloader {

    private const val TAG = "UpdateDownloader"
    private const val CHANNEL_ID = "spotui_app_updates"
    private const val NOTIF_ID = 9402

    sealed class InstallLaunchResult {
        data object Launched : InstallLaunchResult()
        data object PermissionRequired : InstallLaunchResult()
        data class Failed(val reason: String) : InstallLaunchResult()
    }

    sealed class DownloadState {
        data object Idle : DownloadState()

        data class Downloading(
            val version: String,
            val fileName: String,
            val downloadedBytes: Long,
            val totalBytes: Long,
            val progressFraction: Float,
            val speedBytesPerSec: Long,
            val etaSeconds: Long?,
        ) : DownloadState()

        data class Verifying(
            val version: String,
            val fileName: String,
        ) : DownloadState()

        data class Completed(
            val version: String,
            val fileName: String,
            val savedDisplayPath: String,
            val savedUriString: String,
            val installFilePath: String,
            val sizeBytes: Long,
            val computedSha256: String,
            val sha256Verified: Boolean?,
        ) : DownloadState()

        data class Failed(
            val version: String,
            val reason: String,
            val canRetry: Boolean = true,
        ) : DownloadState()

        data object Cancelled : DownloadState()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    @Volatile
    private var activeJob: Job? = null

    fun canRequestPackageInstalls(context: Context): Boolean =
        runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)

    fun buildUnknownAppSourcesIntent(context: Context, newTask: Boolean = true): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).apply {
            if (newTask) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun startDownload(
        context: Context,
        info: UpdateChecker.UpdateInfo,
        force: Boolean = false,
    ) {
        val appCtx = context.applicationContext
        val current = _state.value
        if (!force) {
            when (current) {
                is DownloadState.Downloading if current.version == info.version -> return
                is DownloadState.Verifying if current.version == info.version -> return
                is DownloadState.Completed if current.version == info.version -> return
                else -> Unit
            }
        }

        activeJob?.cancel()
        val resolvedName = info.apkFileName
            .ifBlank { "SpotUI-v${info.version}-release.apk" }
            .replace(Regex("""[^\w.\-]"""), "_")
            .let { if (it.endsWith(".apk", ignoreCase = true)) it else "$it.apk" }

        DevConsoleManager.logInfo(TAG, "Starting in-app APK download for v${info.version} ($resolvedName)")

        activeJob = scope.launch {
            var stagingFile: File? = null
            try {
                _state.value = DownloadState.Downloading(
                    version = info.version,
                    fileName = resolvedName,
                    downloadedBytes = 0L,
                    totalBytes = info.apkSizeBytes,
                    progressFraction = if (info.apkSizeBytes > 0L) 0f else -1f,
                    speedBytesPerSec = 0L,
                    etaSeconds = null,
                )
                postProgressNotification(
                    appCtx,
                    title = "Downloading SpotUI v${info.version}",
                    subtitle = "Preparing in-app download…",
                    progress = 0,
                    indeterminate = true,
                )

                val directUrl = resolveDirectApkUrl(info)
                    ?: throw IllegalStateException("No downloadable .apk asset found on release v${info.version}.")

                val updatesDir = File(appCtx.cacheDir, "updates").apply { mkdirs() }
                // Clean old cached update APKs so cache stays compact
                updatesDir.listFiles()?.forEach { old ->
                    if (old.name != resolvedName) runCatching { old.delete() }
                }
                stagingFile = File(updatesDir, "$resolvedName.part")
                if (stagingFile.exists()) stagingFile.delete()

                val request = Request.Builder()
                    .url(directUrl)
                    .header("User-Agent", "SpotUI/${BuildConfig.VERSION_NAME}")
                    .header("Accept", "application/vnd.android.package-archive, application/octet-stream, */*")
                    .build()

                var computedSha256 = ""
                var actualBytes = 0L

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Download server returned HTTP ${response.code}")
                    }
                    val body = response.body ?: throw IllegalStateException("Empty download response body")
                    val contentType = body.contentType()?.toString().orEmpty()
                    if (contentType.contains("text/html", ignoreCase = true)) {
                        throw IllegalStateException("Release URL returned an HTML page instead of an APK file")
                    }

                    val expectedTotal = body.contentLength().takeIf { it > 0L } ?: info.apkSizeBytes
                    val digest = MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(64 * 1024)
                    val startMs = System.currentTimeMillis()
                    var lastEmitMs = 0L
                    var checkedMagic = false

                    body.byteStream().use { input ->
                        FileOutputStream(stagingFile).use { output ->
                            while (true) {
                                coroutineContext.ensureActive()
                                val read = input.read(buffer)
                                if (read == -1) break
                                if (read == 0) continue

                                if (!checkedMagic) {
                                    checkedMagic = true
                                    if (read < 2 || buffer[0] != 0x50.toByte() || buffer[1] != 0x4B.toByte()) {
                                        throw IllegalStateException("Downloaded payload is not a valid APK/ZIP archive")
                                    }
                                }

                                output.write(buffer, 0, read)
                                digest.update(buffer, 0, read)
                                actualBytes += read

                                val now = System.currentTimeMillis()
                                if (now - lastEmitMs >= 300L) {
                                    lastEmitMs = now
                                    val elapsedSec = ((now - startMs).coerceAtLeast(250L)) / 1000.0
                                    val speed = (actualBytes / elapsedSec).toLong().coerceAtLeast(0L)
                                    val fraction = if (expectedTotal > 0L) {
                                        (actualBytes.toFloat() / expectedTotal.toFloat()).coerceIn(0f, 1f)
                                    } else {
                                        -1f
                                    }
                                    val etaSec = if (expectedTotal > 0L && speed > 0L) {
                                        ((expectedTotal - actualBytes).coerceAtLeast(0L)) / speed
                                    } else {
                                        null
                                    }
                                    _state.value = DownloadState.Downloading(
                                        version = info.version,
                                        fileName = resolvedName,
                                        downloadedBytes = actualBytes,
                                        totalBytes = expectedTotal,
                                        progressFraction = fraction,
                                        speedBytesPerSec = speed,
                                        etaSeconds = etaSec,
                                    )
                                    val pct = if (fraction >= 0f) (fraction * 100).toInt().coerceIn(0, 100) else 0
                                    postProgressNotification(
                                        appCtx,
                                        title = "Downloading SpotUI v${info.version}",
                                        subtitle = formatNotificationProgress(actualBytes, expectedTotal, speed),
                                        progress = pct,
                                        indeterminate = fraction < 0f,
                                    )
                                }
                            }
                            output.flush()
                        }
                    }
                    computedSha256 = digest.digest().joinToString("") { "%02x".format(it) }
                }

                if (actualBytes < 1024L) {
                    throw IllegalStateException("Downloaded APK is unexpectedly small ($actualBytes bytes)")
                }

                _state.value = DownloadState.Verifying(version = info.version, fileName = resolvedName)

                val expectedSha = info.expectedSha256.trim()
                val shaVerified: Boolean? = if (expectedSha.isNotBlank()) {
                    val matched = computedSha256.equals(expectedSha, ignoreCase = true)
                    if (!matched) {
                        throw IllegalStateException(
                            "SHA-256 verification failed (expected ${expectedSha.take(12)}…, got ${computedSha256.take(12)}…)"
                        )
                    }
                    true
                } else {
                    null
                }

                val verifiedApk = File(updatesDir, resolvedName)
                if (verifiedApk.exists()) verifiedApk.delete()
                if (!stagingFile.renameTo(verifiedApk)) {
                    stagingFile.copyTo(verifiedApk, overwrite = true)
                    stagingFile.delete()
                }

                // Also export a copy into Downloads/SpotUI so the user can see/keep it in Files
                val saved = runCatching {
                    persistToDownloads(appCtx, verifiedApk, resolvedName)
                }.getOrElse {
                    "cache/updates/$resolvedName" to verifiedApk.toURI().toString()
                }

                DevConsoleManager.logInfo(
                    TAG,
                    "Completed v${info.version} (${actualBytes}B, sha256Verified=$shaVerified) -> ${verifiedApk.absolutePath}",
                )

                val completed = DownloadState.Completed(
                    version = info.version,
                    fileName = resolvedName,
                    savedDisplayPath = saved.first,
                    savedUriString = saved.second,
                    installFilePath = verifiedApk.absolutePath,
                    sizeBytes = actualBytes,
                    computedSha256 = computedSha256,
                    sha256Verified = shaVerified,
                )
                _state.value = completed

                postCompletedNotification(
                    appCtx,
                    completed = completed,
                )
            } catch (_: CancellationException) {
                stagingFile?.delete()
                cancelProgressNotification(appCtx)
                _state.value = DownloadState.Cancelled
                DevConsoleManager.logInfo(TAG, "Cancelled in-app update download for v${info.version}")
            } catch (t: Throwable) {
                stagingFile?.delete()
                cancelProgressNotification(appCtx)
                val msg = t.message ?: "Download failed"
                DevConsoleManager.logError(TAG, "In-app download failed for v${info.version}: $msg", t)
                _state.value = DownloadState.Failed(version = info.version, reason = msg)
            }
        }
    }

    /**
     * Opens Android's native Package Installer UI for an in-place upgrade over the installed
     * SpotUI version (matching package name & signing key). If "Install unknown apps" is not yet
     * allowed for SpotUI, returns [InstallLaunchResult.PermissionRequired].
     */
    fun installApk(
        context: Context,
        completed: DownloadState.Completed? = _state.value as? DownloadState.Completed,
    ): InstallLaunchResult {
        if (completed == null) {
            return InstallLaunchResult.Failed("No downloaded update APK is ready")
        }
        if (!canRequestPackageInstalls(context)) {
            return InstallLaunchResult.PermissionRequired
        }
        val intent = buildInstallerIntent(context, completed)
            ?: return InstallLaunchResult.Failed("Update APK file was cleared from cache — tap Download In-App again")

        return runCatching {
            context.startActivity(intent)
            DevConsoleManager.logInfo(TAG, "Opened Android Package Installer for v${completed.version} (${completed.fileName})")
            InstallLaunchResult.Launched
        }.getOrElse { err ->
            DevConsoleManager.logError(TAG, "Failed launching Package Installer: ${err.message}", err)
            InstallLaunchResult.Failed(err.message ?: "Could not open Android Package Installer")
        }
    }

    fun buildInstallerIntent(
        context: Context,
        completed: DownloadState.Completed,
    ): Intent? {
        val cachedApk = File(completed.installFilePath)
        val uri: Uri = when {
            cachedApk.exists() -> {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cachedApk)
            }
            completed.savedUriString.startsWith("content://") -> {
                Uri.parse(completed.savedUriString)
            }
            else -> return null
        }

        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_RETURN_RESULT, false)
        }
    }

    fun cancelDownload(context: Context? = null) {
        activeJob?.cancel()
        activeJob = null
        context?.applicationContext?.let(::cancelProgressNotification)
        _state.value = DownloadState.Cancelled
    }

    fun reset() {
        if (activeJob?.isActive == true) return
        _state.value = DownloadState.Idle
    }

    /**
     * Opens Android's system Downloads view so the user can also inspect the exported APK copy.
     */
    fun openDownloadsFolder(context: Context) {
        val downloadsIntent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val opened = runCatching {
            context.startActivity(downloadsIntent)
        }.isSuccess
        if (!opened) {
            Toast.makeText(
                context,
                "Saved to Downloads/SpotUI — open your Files or Downloads app to view the APK.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun resolveDirectApkUrl(info: UpdateChecker.UpdateInfo): String? {
        val raw = info.downloadUrl.trim()
        if (raw.isBlank()) return null
        if (raw.endsWith(".apk", ignoreCase = true) || raw.contains("/releases/download/", ignoreCase = true)) {
            return raw
        }
        // Fallback when UpdateInfo came from Atom feed (release HTML URL): probe GitHub expanded_assets page
        val repoBase = raw.substringBefore("/releases/").takeIf { it.startsWith("http") } ?: return null
        val expandedUrl = "$repoBase/releases/expanded_assets/v${info.version}"
        return runCatching {
            val req = Request.Builder()
                .url(expandedUrl)
                .header("User-Agent", "SpotUI/${BuildConfig.VERSION_NAME}")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val html = resp.body?.string().orEmpty()
                val hrefMatches = Regex("""href=["']([^"']*?/releases/download/[^"']+\.apk)["']""", RegexOption.IGNORE_CASE)
                    .findAll(html)
                    .map { it.groupValues[1] }
                    .toList()
                val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull()?.lowercase().orEmpty()
                val bestRel = hrefMatches.maxByOrNull { path ->
                    val lower = path.lowercase()
                    var s = 0
                    if (primaryAbi.isNotBlank() && lower.contains(primaryAbi)) s += 100
                    if (lower.contains("universal")) s += 40
                    if (lower.contains("release")) s += 30
                    if (lower.contains("debug")) s -= 15
                    s
                } ?: return@use null
                if (bestRel.startsWith("http")) bestRel else "https://github.com$bestRel"
            }
        }.getOrNull()
    }

    private fun persistToDownloads(
        context: Context,
        sourceFile: File,
        fileName: String,
    ): Pair<String, String> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val relPath = "${Environment.DIRECTORY_DOWNLOADS}/SpotUI"
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                put(MediaStore.Downloads.RELATIVE_PATH, relPath)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create destination entry in Downloads/SpotUI")
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(out, bufferSize = 64 * 1024)
                    }
                    out.flush()
                } ?: throw IllegalStateException("Failed to open Downloads output stream")

                val doneValues = ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
                resolver.update(uri, doneValues, null, null)
                return "$relPath/$fileName" to uri.toString()
            } catch (t: Throwable) {
                runCatching { resolver.delete(uri, null, null) }
                throw t
            }
        } else {
            val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(context.filesDir, "downloads")
            if (!targetDir.exists()) targetDir.mkdirs()
            val destFile = File(targetDir, fileName)
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                    output.flush()
                }
            }
            return destFile.absolutePath to destFile.toURI().toString()
        }
    }

    private fun canPostNotifications(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel(context: Context): NotificationManager? {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return null
        val existing = nm.getNotificationChannel(CHANNEL_ID)
        if (existing == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Updates",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "In-app SpotUI release download progress & Package Installer handoff"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
        return nm
    }

    private fun postProgressNotification(
        context: Context,
        title: String,
        subtitle: String,
        progress: Int,
        indeterminate: Boolean,
    ) {
        if (!canPostNotifications(context)) return
        runCatching {
            val nm = ensureChannel(context) ?: return
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(subtitle)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setProgress(100, progress, indeterminate)
            nm.notify(NOTIF_ID, builder.build())
        }
    }

    private fun postCompletedNotification(
        context: Context,
        completed: DownloadState.Completed,
    ) {
        if (!canPostNotifications(context)) return
        runCatching {
            val nm = ensureChannel(context) ?: return
            val targetIntent = when {
                !canRequestPackageInstalls(context) -> buildUnknownAppSourcesIntent(context)
                else -> buildInstallerIntent(context, completed)
                    ?: Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pi = PendingIntent.getActivity(
                context,
                NOTIF_ID,
                targetIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val status = if (completed.sha256Verified == true) {
                "SHA-256 Verified · Tap to install via Android Package Installer"
            } else {
                "Tap to install via Android Package Installer"
            }
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("SpotUI v${completed.version} ready to install")
                .setContentText(status)
                .setStyle(NotificationCompat.BigTextStyle().bigText("$status (${completed.fileName})"))
                .setAutoCancel(true)
                .setOngoing(false)
                .setContentIntent(pi)
            nm.notify(NOTIF_ID, builder.build())
        }
    }

    private fun cancelProgressNotification(context: Context) {
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIF_ID)
        }
    }

    private fun formatNotificationProgress(downloaded: Long, total: Long, speed: Long): String {
        val dlMb = downloaded / (1024.0 * 1024.0)
        val speedText = if (speed >= 1024 * 1024) {
            "%.1f MB/s".format(speed / (1024.0 * 1024.0))
        } else {
            "${(speed / 1024L).coerceAtLeast(0L)} KB/s"
        }
        return if (total > 0L) {
            val totalMb = total / (1024.0 * 1024.0)
            "%.1f / %.1f MB · %s".format(dlMb, totalMb, speedText)
        } else {
            "%.1f MB · %s".format(dlMb, speedText)
        }
    }

    internal fun formatBytes(bytes: Long): String {
        if (bytes < 0L) return "Unknown"
        return when {
            bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            bytes >= 1024L -> "${bytes / 1024L} KB"
            else -> "$bytes B"
        }
    }

    internal fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1024L * 1024L -> "%.1f MB/s".format(bytesPerSec / (1024.0 * 1024.0))
            bytesPerSec >= 1024L -> "${bytesPerSec / 1024L} KB/s"
            else -> "$bytesPerSec B/s"
        }
    }

    internal fun formatEta(seconds: Long?): String {
        if (seconds == null || seconds < 0L) return ""
        val min = seconds / 60L
        val sec = seconds % 60L
        return if (min > 0L) "${min}m ${sec}s left" else "${sec}s left"
    }
}
