package com.music.spotui.data.update

import android.Manifest
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
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
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import org.eclipse.Mrbean.client.MrbeanDownloadClient
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * In-app release APK downloader and native Android [PackageInstaller] session manager for SpotUI.
 *
 * Streams APKs inside the app using HTTP/2 pooled multi-range transfers ([MrbeanDownloadClient]),
 * validates ZIP/APK magic bytes (`PK`) and SHA-256 checksums, exports a backup copy to
 * `Downloads/SpotUI/`, and commits installs through Android's native [PackageInstaller] session API
 * with live session progress callbacks and [UpdateInstallReceiver] status handling.
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
            val signatureMismatch: Boolean = false,
            val downgradeBlocked: Boolean = false,
            val compatWarning: String = "",
        ) : DownloadState()

        data class Installing(
            val version: String,
            val fileName: String,
            val progressFraction: Float,
            val statusMessage: String,
            val sessionId: Int = -1,
        ) : DownloadState()

        data class Installed(
            val version: String,
            val fileName: String,
        ) : DownloadState()

        data class Failed(
            val version: String,
            val reason: String,
            val canRetry: Boolean = true,
            val fallbackCompleted: DownloadState.Completed? = null,
        ) : DownloadState()

        data object Cancelled : DownloadState()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val probeDispatcher = Dispatcher().apply {
        maxRequests = 16
        maxRequestsPerHost = 8
    }

    private val client = OkHttpClient.Builder()
        .dispatcher(probeDispatcher)
        .connectionPool(ConnectionPool(6, 3, TimeUnit.MINUTES))
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    private val _pendingConfirmIntent = MutableStateFlow<Intent?>(null)
    val pendingConfirmIntent: StateFlow<Intent?> = _pendingConfirmIntent.asStateFlow()

    @Volatile
    private var activeJob: Job? = null

    @Volatile
    private var activeInstallJob: Job? = null

    @Volatile
    private var lastCompleted: DownloadState.Completed? = null

    @Volatile
    private var activeSessionCallback: PackageInstaller.SessionCallback? = null

    @Volatile
    private var activeSessionId: Int = -1

    fun consumePendingConfirmIntent(): Intent? {
        val current = _pendingConfirmIntent.value ?: return null
        _pendingConfirmIntent.value = null
        return current
    }

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
                is DownloadState.Installing if current.version == info.version -> return
                is DownloadState.Installed if current.version == info.version -> return
                else -> Unit
            }
        }

        activeJob?.cancel()
        val resolvedName = info.apkFileName
            .ifBlank { "SpotUI-v${info.version}-release.apk" }
            .replace(Regex("""[^\w.\-]"""), "_")
            .let { if (it.endsWith(".apk", ignoreCase = true)) it else "$it.apk" }

        DevConsoleManager.logInfo(TAG, "Starting turbo HTTP/2 in-app APK download for v${info.version} ($resolvedName)")

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
                    subtitle = "Connecting via HTTP/2 multi-range pool…",
                    progress = 0,
                    indeterminate = true,
                )

                val directUrl = resolveDirectApkUrl(info)
                    ?: throw IllegalStateException("No downloadable .apk asset found on release v${info.version}.")

                val updatesDir = File(appCtx.cacheDir, "updates").apply { mkdirs() }
                updatesDir.listFiles()?.forEach { old ->
                    if (old.name != resolvedName) runCatching { old.delete() }
                }
                stagingFile = File(updatesDir, "$resolvedName.part")
                if (stagingFile.exists()) stagingFile.delete()

                val expectedTotal = info.apkSizeBytes
                val startMs = System.currentTimeMillis()
                var lastEmitMs = 0L

                val rangeResult = MrbeanDownloadClient.downloadRangedToFile(
                    context = appCtx,
                    url = directUrl,
                    destFile = stagingFile,
                ) { pct ->
                    val now = System.currentTimeMillis()
                    if (now - lastEmitMs >= 250L || pct >= 100) {
                        lastEmitMs = now
                        val currentBytes = stagingFile.length().coerceAtLeast(0L)
                        val fraction = (pct.coerceIn(0, 100)) / 100f
                        val elapsedSec = ((now - startMs).coerceAtLeast(250L)) / 1000.0
                        val avgSpeed = (currentBytes / elapsedSec).toLong().coerceAtLeast(0L)
                        val instantSpeed = MrbeanDownloadClient.throughputBytesPerSec.value.takeIf { it > 0L } ?: avgSpeed
                        val totalForDisplay = expectedTotal.takeIf { it > 0L }
                            ?: if (fraction > 0.01f) (currentBytes / fraction).toLong() else -1L
                        val etaSec = if (totalForDisplay > 0L && instantSpeed > 0L) {
                            ((totalForDisplay - currentBytes).coerceAtLeast(0L)) / instantSpeed
                        } else null

                        _state.value = DownloadState.Downloading(
                            version = info.version,
                            fileName = resolvedName,
                            downloadedBytes = currentBytes,
                            totalBytes = totalForDisplay,
                            progressFraction = fraction,
                            speedBytesPerSec = instantSpeed,
                            etaSeconds = etaSec,
                        )
                        postProgressNotification(
                            appCtx,
                            title = "Downloading SpotUI v${info.version}",
                            subtitle = formatNotificationProgress(currentBytes, totalForDisplay, instantSpeed),
                            progress = pct.coerceIn(0, 100),
                            indeterminate = false,
                        )
                    }
                }

                if (!rangeResult.success) {
                    throw IllegalStateException(rangeResult.error ?: "APK download failed")
                }

                val actualBytes = stagingFile.length()
                if (actualBytes < 1024L) {
                    throw IllegalStateException("Downloaded APK is unexpectedly small ($actualBytes bytes)")
                }

                _state.value = DownloadState.Verifying(version = info.version, fileName = resolvedName)

                // Verify APK ZIP magic header (`PK`) and compute full SHA-256 digest
                val computedSha256 = verifyApkAndComputeSha256(stagingFile)

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

                // Check signing key & version compatibility against the currently installed SpotUI
                val compat = verifyApkCompatibility(appCtx, verifiedApk)

                // Also export a backup copy into Downloads/SpotUI so the user can see/keep it in Files
                val saved = runCatching {
                    persistToDownloads(appCtx, verifiedApk, resolvedName)
                }.getOrElse {
                    "cache/updates/$resolvedName" to verifiedApk.toURI().toString()
                }

                DevConsoleManager.logInfo(
                    TAG,
                    "Completed v${info.version} (${actualBytes}B, sha256Verified=$shaVerified, sigMismatch=${compat.signatureMismatch}) -> ${verifiedApk.absolutePath}",
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
                    signatureMismatch = compat.signatureMismatch,
                    downgradeBlocked = compat.downgradeBlocked,
                    compatWarning = compat.reason,
                )
                lastCompleted = completed
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

    private suspend fun verifyApkAndComputeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(256 * 1024)
        var checkedMagic = false
        FileInputStream(file).use { input ->
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
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private data class ApkCompatCheck(
        val signatureMismatch: Boolean = false,
        val downgradeBlocked: Boolean = false,
        val reason: String = "",
    )

    @Suppress("DEPRECATION")
    private fun extractSigningDigests(packageInfo: android.content.pm.PackageInfo?): Set<String> {
        if (packageInfo == null) return emptySet()
        val sigBytes: List<ByteArray> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = packageInfo.signingInfo ?: return emptySet()
            val list = mutableListOf<ByteArray>()
            info.apkContentsSigners?.forEach { list += it.toByteArray() }
            info.signingCertificateHistory?.forEach { list += it.toByteArray() }
            list
        } else {
            packageInfo.signatures?.map { it.toByteArray() }.orEmpty()
        }
        if (sigBytes.isEmpty()) return emptySet()
        return sigBytes.mapTo(HashSet()) { raw ->
            MessageDigest.getInstance("SHA-256").digest(raw).joinToString("") { "%02x".format(it) }
        }
    }

    @Suppress("DEPRECATION")
    private fun verifyApkCompatibility(context: Context, apkFile: File): ApkCompatCheck {
        return runCatching {
            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                PackageManager.GET_SIGNATURES
            }
            val installedInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                pm.getPackageInfo(context.packageName, flags)
            }
            val archiveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
            } ?: return ApkCompatCheck()

            if (!archiveInfo.packageName.isNullOrBlank() && archiveInfo.packageName != context.packageName) {
                return ApkCompatCheck(
                    signatureMismatch = true,
                    reason = "Package ID differs (${archiveInfo.packageName} vs ${context.packageName}). Backup & uninstall the old build first, then install from Downloads/SpotUI.",
                )
            }

            val installedVerCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                installedInfo.longVersionCode
            } else {
                installedInfo.versionCode.toLong()
            }
            val archiveVerCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                archiveInfo.longVersionCode
            } else {
                archiveInfo.versionCode.toLong()
            }
            if (archiveVerCode in 1L until installedVerCode) {
                return ApkCompatCheck(
                    downgradeBlocked = true,
                    reason = "Downloaded APK versionCode ($archiveVerCode) is older than installed ($installedVerCode). Android blocks version downgrades without uninstalling first.",
                )
            }

            val installedCerts = extractSigningDigests(installedInfo)
            val archiveCerts = extractSigningDigests(archiveInfo)
            if (installedCerts.isNotEmpty() && archiveCerts.isNotEmpty() &&
                installedCerts.intersect(archiveCerts).isEmpty()
            ) {
                val curShort = installedCerts.first().take(10)
                val newShort = archiveCerts.first().take(10)
                return ApkCompatCheck(
                    signatureMismatch = true,
                    reason = "Signing key mismatch (installed $curShort… vs update $newShort…). Android blocks in-place updates across different keys (e.g., an older ephemeral CI key). Back up in Settings → Backup first, tap 'Uninstall Old App', then install the saved APK in Downloads/SpotUI — future builds using the same persistent keystore update in-place.",
                )
            }
            ApkCompatCheck()
        }.getOrDefault(ApkCompatCheck())
    }

    fun triggerUninstallCurrentApp(context: Context) {
        runCatching {
            val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${context.packageName}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure { err ->
            Toast.makeText(context, err.message ?: "Could not open uninstall prompt", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Installs the downloaded SpotUI APK via Android's native [PackageInstaller] session API,
     * streaming bytes into a staged install session with live OS verification progress and
     * [UpdateInstallReceiver] completion callbacks.
     */
    fun installApk(
        context: Context,
        completed: DownloadState.Completed? = (_state.value as? DownloadState.Completed) ?: lastCompleted,
        ignoreCompatCheck: Boolean = false,
    ): InstallLaunchResult {
        val appCtx = context.applicationContext
        if (completed == null) {
            return InstallLaunchResult.Failed("No downloaded update APK is ready")
        }
        lastCompleted = completed

        val currentState = _state.value
        if (currentState is DownloadState.Installing && currentState.version == completed.version) {
            return InstallLaunchResult.Launched
        }
        if (activeInstallJob?.isActive == true) {
            return InstallLaunchResult.Launched
        }

        if (!ignoreCompatCheck && (completed.signatureMismatch || completed.downgradeBlocked)) {
            return InstallLaunchResult.Failed(
                completed.compatWarning.ifBlank {
                    "Installed SpotUI conflicts with this APK (different signing key or higher versionCode). Uninstall old SpotUI first, then install from Downloads/SpotUI."
                },
            )
        }

        if (!canRequestPackageInstalls(appCtx)) {
            return InstallLaunchResult.PermissionRequired
        }

        val apkFile = File(completed.installFilePath)
        if (!apkFile.exists() || apkFile.length() < 1024L) {
            return InstallLaunchResult.Failed("Update APK file was cleared from cache — tap Download & Install again")
        }

        _pendingConfirmIntent.value = null
        _state.value = DownloadState.Installing(
            version = completed.version,
            fileName = completed.fileName,
            progressFraction = 0.02f,
            statusMessage = "Creating Android PackageInstaller session…",
        )

        activeInstallJob = scope.launch {
            var session: PackageInstaller.Session? = null
            var sessionId = -1
            try {
                val packageInstaller = appCtx.packageManager.packageInstaller
                unregisterSessionCallback(appCtx)

                // Clean up stale un-committed SpotUI installer sessions left over from past crashes
                runCatching {
                    packageInstaller.mySessions.forEach { info ->
                        if (info.sessionId != activeSessionId) {
                            runCatching { packageInstaller.abandonSession(info.sessionId) }
                        }
                    }
                }

                val totalBytes = apkFile.length()
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(appCtx.packageName)
                    setSize(totalBytes)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        // Explicitly request normal confirmation prompt so OEM installers (One UI / Pixel)
                        // never fail on update-ownership enforcement for self-updates.
                        setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        setPackageSource(PackageInstaller.PACKAGE_SOURCE_OTHER)
                    }
                }

                sessionId = packageInstaller.createSession(params)
                activeSessionId = sessionId

                val callback = object : PackageInstaller.SessionCallback() {
                    override fun onCreated(id: Int) = Unit
                    override fun onBadgingChanged(id: Int) = Unit
                    override fun onActiveChanged(id: Int, active: Boolean) = Unit
                    override fun onProgressChanged(id: Int, progress: Float) {
                        if (id != sessionId) return
                        val mapped = (0.50f + progress.coerceIn(0f, 1f) * 0.48f).coerceIn(0f, 0.98f)
                        val cur = _state.value
                        if (cur is DownloadState.Installing && cur.version == completed.version) {
                            _state.value = cur.copy(
                                progressFraction = mapped,
                                statusMessage = if (progress < 0.9f) {
                                    "Android PackageInstaller verifying & optimizing…"
                                } else {
                                    "Finalizing package commit…"
                                },
                                sessionId = sessionId,
                            )
                        }
                    }
                    override fun onFinished(id: Int, success: Boolean) {
                        if (id == sessionId) {
                            unregisterSessionCallback(appCtx)
                        }
                    }
                }
                activeSessionCallback = callback
                packageInstaller.registerSessionCallback(callback, Handler(Looper.getMainLooper()))

                session = packageInstaller.openSession(sessionId)

                val buffer = ByteArray(256 * 1024)
                var copied = 0L
                var lastEmit = 0L
                session.openWrite("spotui-v${completed.version}.apk", 0L, totalBytes).use { out ->
                    FileInputStream(apkFile).use { input ->
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            if (read == 0) continue
                            out.write(buffer, 0, read)
                            copied += read

                            val now = System.currentTimeMillis()
                            if (now - lastEmit >= 120L || copied >= totalBytes) {
                                lastEmit = now
                                val streamPct = (copied.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                _state.value = DownloadState.Installing(
                                    version = completed.version,
                                    fileName = completed.fileName,
                                    progressFraction = (streamPct * 0.50f).coerceIn(0.02f, 0.50f),
                                    statusMessage = "Streaming APK into PackageInstaller session (${(streamPct * 100).toInt()}%)…",
                                    sessionId = sessionId,
                                )
                            }
                        }
                    }
                    session.fsync(out)
                }

                val callbackIntent = Intent(appCtx, UpdateInstallReceiver::class.java).apply {
                    putExtra(UpdateInstallReceiver.EXTRA_UPDATE_VERSION, completed.version)
                    putExtra(UpdateInstallReceiver.EXTRA_UPDATE_FILENAME, completed.fileName)
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0

                val statusPendingIntent = PendingIntent.getBroadcast(
                    appCtx,
                    sessionId,
                    callbackIntent,
                    flags,
                )

                _state.value = DownloadState.Installing(
                    version = completed.version,
                    fileName = completed.fileName,
                    progressFraction = 0.65f,
                    statusMessage = "Committing Android PackageInstaller session…",
                    sessionId = sessionId,
                )

                DevConsoleManager.logInfo(TAG, "Committed PackageInstaller session #$sessionId for v${completed.version}")
                session.commit(statusPendingIntent.intentSender)
                session.close()
                session = null
            } catch (t: Throwable) {
                runCatching { session?.abandon() }
                runCatching { session?.close() }
                unregisterSessionCallback(appCtx)
                activeSessionId = -1
                val msg = t.message ?: "Could not start Android PackageInstaller session"
                DevConsoleManager.logError(TAG, "PackageInstaller session failed: $msg", t)
                _state.value = DownloadState.Failed(
                    version = completed.version,
                    reason = "PackageInstaller error: $msg",
                    canRetry = true,
                    fallbackCompleted = completed,
                )
            }
        }

        return InstallLaunchResult.Launched
    }

    internal fun onPackageInstallerPendingUserAction(
        context: Context,
        sessionId: Int,
        version: String,
        fileName: String,
        confirmIntent: Intent?,
    ) {
        val resolvedVersion = version.ifBlank { lastCompleted?.version.orEmpty() }
        val resolvedName = fileName.ifBlank { lastCompleted?.fileName.orEmpty() }
        _state.value = DownloadState.Installing(
            version = resolvedVersion,
            fileName = resolvedName,
            progressFraction = 0.85f,
            statusMessage = "Confirm update in Android Package Installer…",
            sessionId = sessionId,
        )
        if (confirmIntent != null) {
            // Hand off to foreground Compose UI first so Android 14-17 Background Activity Launch
            // restrictions never drop startActivity() called from a BroadcastReceiver context.
            _pendingConfirmIntent.value = confirmIntent
            Handler(Looper.getMainLooper()).postDelayed({
                val unconsumed = consumePendingConfirmIntent() ?: return@postDelayed
                runCatching {
                    unconsumed.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(unconsumed)
                    DevConsoleManager.logInfo(TAG, "Launched PackageInstaller confirmation prompt (fallback) for session #$sessionId")
                }.onFailure { err ->
                    DevConsoleManager.logError(TAG, "Failed launching PackageInstaller confirmation: ${err.message}", err)
                    val fallback = lastCompleted
                    _state.value = DownloadState.Failed(
                        version = resolvedVersion,
                        reason = err.message ?: "Unable to display Android PackageInstaller confirmation prompt",
                        canRetry = true,
                        fallbackCompleted = fallback,
                    )
                }
            }, 350L)
        }
    }

    internal fun onPackageInstallerSuccess(
        context: Context,
        sessionId: Int,
        version: String,
        fileName: String,
    ) {
        val appCtx = context.applicationContext
        _pendingConfirmIntent.value = null
        unregisterSessionCallback(appCtx)
        activeSessionId = -1
        val resolvedVersion = version.ifBlank { lastCompleted?.version.orEmpty() }
        val resolvedName = fileName.ifBlank { lastCompleted?.fileName.orEmpty() }
        DevConsoleManager.logInfo(TAG, "PackageInstaller succeeded for v$resolvedVersion (session #$sessionId)")
        runCatching {
            lastCompleted?.installFilePath?.let { File(it).delete() }
        }
        cancelProgressNotification(appCtx)
        _state.value = DownloadState.Installed(version = resolvedVersion, fileName = resolvedName)
    }

    internal fun onPackageInstallerFailure(
        context: Context,
        sessionId: Int,
        version: String,
        fileName: String,
        status: Int,
        rawMessage: String,
    ) {
        val appCtx = context.applicationContext
        _pendingConfirmIntent.value = null
        unregisterSessionCallback(appCtx)
        activeSessionId = -1
        runCatching {
            if (sessionId >= 0) appCtx.packageManager.packageInstaller.abandonSession(sessionId)
        }
        val resolvedVersion = version.ifBlank { lastCompleted?.version.orEmpty() }
        val humanReason = when (status) {
            PackageInstaller.STATUS_FAILURE_ABORTED -> "Installation cancelled by user."
            PackageInstaller.STATUS_FAILURE_BLOCKED -> "Blocked by device policy, Play Protect, or One UI Auto Blocker."
            PackageInstaller.STATUS_FAILURE_CONFLICT -> "Package conflict or signing key mismatch — tap 'Uninstall Old App', then install the APK saved in Downloads/SpotUI."
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "APK is incompatible with this Android version or CPU ABI."
            PackageInstaller.STATUS_FAILURE_INVALID -> "Downloaded APK package failed system validation."
            PackageInstaller.STATUS_FAILURE_STORAGE -> "Insufficient device storage to install update."
            else -> rawMessage.ifBlank { "PackageInstaller failed (status=$status)" }
        }
        DevConsoleManager.logError(TAG, "PackageInstaller failure (status=$status): $humanReason")
        val existing = lastCompleted
        val flaggedCompleted = if (status == PackageInstaller.STATUS_FAILURE_CONFLICT && existing != null) {
            existing.copy(signatureMismatch = true, compatWarning = humanReason)
        } else {
            existing
        }
        if (flaggedCompleted != null) lastCompleted = flaggedCompleted
        _state.value = DownloadState.Failed(
            version = resolvedVersion,
            reason = humanReason,
            canRetry = true,
            fallbackCompleted = flaggedCompleted,
        )
    }

    /**
     * Secondary fallback opening standard `ACTION_VIEW` FileProvider APK installer
     * when a device OEM restricts [PackageInstaller] session commits.
     */
    fun openFileProviderFallbackInstaller(
        context: Context,
        completed: DownloadState.Completed? = lastCompleted,
    ): InstallLaunchResult {
        val target = completed ?: return InstallLaunchResult.Failed("No downloaded APK available")
        if (!canRequestPackageInstalls(context)) return InstallLaunchResult.PermissionRequired
        val intent = buildInstallerIntent(context, target)
            ?: return InstallLaunchResult.Failed("Update APK file missing from cache")
        return runCatching {
            context.startActivity(intent)
            InstallLaunchResult.Launched
        }.getOrElse { err ->
            InstallLaunchResult.Failed(err.message ?: "Fallback installer launch failed")
        }
    }

    private fun unregisterSessionCallback(appCtx: Context) {
        val cb = activeSessionCallback ?: return
        activeSessionCallback = null
        runCatching {
            appCtx.packageManager.packageInstaller.unregisterSessionCallback(cb)
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
        activeInstallJob?.cancel()
        activeInstallJob = null
        context?.applicationContext?.let { appCtx ->
            unregisterSessionCallback(appCtx)
            cancelProgressNotification(appCtx)
        }
        _state.value = DownloadState.Cancelled
    }

    fun reset() {
        if (activeJob?.isActive == true || activeInstallJob?.isActive == true) return
        _state.value = DownloadState.Idle
    }

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
                        input.copyTo(out, bufferSize = 256 * 1024)
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
            @Suppress("DEPRECATION")
            val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val publicSpotUiDir = File(publicDownloads, "SpotUI")
            val usePublic = runCatching {
                (publicSpotUiDir.exists() || publicSpotUiDir.mkdirs()) && publicSpotUiDir.canWrite()
            }.getOrDefault(false)
            val targetDir = if (usePublic) {
                publicSpotUiDir
            } else {
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: File(context.filesDir, "downloads")
            }
            if (!targetDir.exists()) targetDir.mkdirs()
            val destFile = File(targetDir, fileName)
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output, bufferSize = 256 * 1024)
                    output.flush()
                }
            }
            val display = if (usePublic) "${Environment.DIRECTORY_DOWNLOADS}/SpotUI/$fileName" else destFile.absolutePath
            return display to destFile.toURI().toString()
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
                description = "In-app SpotUI release download progress & Android PackageInstaller sessions"
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
                "SHA-256 Verified · Tap to install via Android PackageInstaller"
            } else {
                "Tap to install via Android PackageInstaller"
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
