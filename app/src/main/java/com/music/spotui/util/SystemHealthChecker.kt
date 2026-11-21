package com.music.spotui.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.media3.common.Player
import com.metrolist.innertube.network.AuthSessionStore
import com.metrolist.spotify.SpotiFlac
import com.music.spotui.audio.AudioEffectController
import com.music.spotui.data.BatteryOptimizationHelper
import com.music.spotui.data.api.SpotifySession
import com.music.spotui.data.cache.CacheStorageManager
import com.music.spotui.data.preferences.ProviderRoutingPreferences
import com.music.spotui.data.preferences.getCustomLosslessMirrorUrl
import com.music.spotui.data.preferences.getDeezerArl
import com.music.spotui.data.preferences.getDnsPreset
import com.music.spotui.data.preferences.getProxyUrls
import com.music.spotui.data.preferences.isEqEnabled
import com.music.spotui.data.preferences.isEqSpatialEnabled
import com.music.spotui.deezer.DeezerSession
import com.music.spotui.di.SongPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

enum class CheckSeverity {
    OK,
    WARNING,
    FAIL,
}

data class HealthCheckResult(
    val id: String,
    val title: String,
    val category: String,
    val severity: CheckSeverity,
    val summary: String,
    val detail: String,
    val fixLabel: String? = null,
    val autoFix: (suspend (Context) -> String)? = null,
)

data class HealthScanReport(
    val timestampMs: Long,
    val durationMs: Long,
    val checks: List<HealthCheckResult>,
) {
    val okCount: Int get() = checks.count { it.severity == CheckSeverity.OK }
    val warnCount: Int get() = checks.count { it.severity == CheckSeverity.WARNING }
    val failCount: Int get() = checks.count { it.severity == CheckSeverity.FAIL }
}

/**
 * Active 20-point runtime health, playback, DSP, Bluetooth headset, fault monitor,
 * network, authentication, provider, and storage scanner with 1-tap remediation actions.
 */
object SystemHealthChecker {

    const val SCAN_COOLDOWN_MS = 10_000L

    private val pingClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    @Volatile
    private var lastScanCompletedAtMs: Long = 0L

    @Volatile
    private var cachedReport: HealthScanReport? = null

    fun remainingCooldownMs(nowMs: Long = System.currentTimeMillis()): Long {
        val elapsed = nowMs - lastScanCompletedAtMs
        return if (cachedReport != null && elapsed in 0 until SCAN_COOLDOWN_MS) {
            SCAN_COOLDOWN_MS - elapsed
        } else {
            0L
        }
    }

    internal fun resetCooldownForTest() {
        lastScanCompletedAtMs = 0L
        cachedReport = null
    }

    suspend fun runFullScan(
        context: Context,
        nowMs: Long = System.currentTimeMillis(),
        skipNetworkProbesForTest: Boolean = false,
    ): HealthScanReport = withContext(Dispatchers.IO) {
        val prev = cachedReport
        if (prev != null && (nowMs - lastScanCompletedAtMs) in 0 until SCAN_COOLDOWN_MS) {
            DevConsoleManager.logInfo(
                "HealthScanner",
                "Returning cached 20-point report (${remainingCooldownMs(nowMs) / 1000 + 1}s cooldown remaining)",
            )
            return@withContext prev
        }
        val appCtx = context.applicationContext
        val start = System.currentTimeMillis()
        val liveTelemetry = withContext(Dispatchers.Main) { SongPlayer.telemetrySnapshot(appCtx) }
        val results = coroutineScope {
            listOf(
                async { checkMediaSession(liveTelemetry) },
                async { checkExoPlayerPipeline(liveTelemetry) },
                async { checkSoftwareDsp(appCtx) },
                async { checkHardwareAudioFx() },
                async { checkBatteryAndWakeLock(appCtx) },
                async { checkPermissions(appCtx) },
                async { checkNetworkConnectivity(appCtx) },
                async { checkDnsAndProxy(appCtx) },
                async { checkSpotifySession(appCtx) },
                async { checkYouTubeInnerTubeSession() },
                async {
                    if (skipNetworkProbesForTest) {
                        offlineSkippedCheck("deezer_auth", "Deezer HiFi ARL & Direct Cdn", "Accounts")
                    } else {
                        kotlinx.coroutines.withTimeoutOrNull(4_500L) { checkDeezerAccount(appCtx) }
                            ?: timeoutCheck("deezer_auth", "Deezer HiFi ARL & Direct Cdn", "Accounts")
                    }
                },
                async {
                    if (skipNetworkProbesForTest) {
                        offlineSkippedCheck("custom_mirror", "Custom Lossless Mirror", "Lossless")
                    } else {
                        kotlinx.coroutines.withTimeoutOrNull(4_500L) { checkCustomMirror(appCtx) }
                            ?: timeoutCheck("custom_mirror", "Custom Lossless Mirror", "Lossless")
                    }
                },
                async {
                    if (skipNetworkProbesForTest) {
                        offlineSkippedCheck("lossless_mirrors", "Lossless Providers & Cooldowns", "Lossless")
                    } else {
                        kotlinx.coroutines.withTimeoutOrNull(4_500L) { checkLosslessMirrors(appCtx) }
                            ?: timeoutCheck("lossless_mirrors", "Lossless Providers & Cooldowns", "Lossless")
                    }
                },
                async {
                    if (skipNetworkProbesForTest) {
                        offlineSkippedCheck("lrclib", "LRCLIB Synced Lyrics Endpoint", "Services")
                    } else {
                        kotlinx.coroutines.withTimeoutOrNull(4_500L) { checkLrclibLyrics() }
                            ?: timeoutCheck("lrclib", "LRCLIB Synced Lyrics Endpoint", "Services")
                    }
                },
                async { checkOrphanedDownloads(appCtx) },
                async { checkMediaCache(appCtx) },
                async { checkCrashReports(appCtx) },
                async { checkQueueIntegrity(appCtx) },
                async { checkBluetoothSpecsAndConflicts(appCtx) },
                async { checkComponentFaults() },
            ).awaitAll()
        }
        val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
        DevConsoleManager.logInfo(
            "HealthScanner",
            "20-point scan completed in ${elapsed}ms: ${results.count { it.severity == CheckSeverity.OK }} OK, " +
                "${results.count { it.severity == CheckSeverity.WARNING }} WARN, " +
                "${results.count { it.severity == CheckSeverity.FAIL }} FAIL",
        )
        HealthScanReport(
            timestampMs = nowMs,
            durationMs = elapsed,
            checks = results,
        ).also {
            cachedReport = it
            lastScanCompletedAtMs = nowMs
        }
    }

    private fun timeoutCheck(id: String, title: String, category: String): HealthCheckResult =
        HealthCheckResult(
            id = id,
            title = title,
            category = category,
            severity = CheckSeverity.WARNING,
            summary = "Probe timed out (>4.5s)",
            detail = "Network endpoint did not respond within the scan budget.",
        )

    private fun offlineSkippedCheck(id: String, title: String, category: String): HealthCheckResult =
        HealthCheckResult(
            id = id,
            title = title,
            category = category,
            severity = CheckSeverity.OK,
            summary = "Skipped in offline test mode",
            detail = "Deterministic offline health verification.",
        )

    private fun checkMediaSession(telemetry: SongPlayer.PlayerTelemetry): HealthCheckResult {
        val activeService = com.music.spotui.ui.notification.ServiceSessionHolder.activeService()
        val hasForeground = activeService?.hasPromotedForeground == true
        val resumable = com.music.spotui.ui.notification.ServiceSessionHolder.isResumable()
        val statusSuffix = "FG=$hasForeground · Resumable=$resumable"
        return when {
            activeService != null && !hasForeground && telemetry.playbackState != Player.STATE_IDLE -> HealthCheckResult(
                id = "media_session",
                title = "Media3 Session & Audio Focus",
                category = "Playback",
                severity = CheckSeverity.WARNING,
                summary = "Session #${telemetry.sessionId} · Foreground promotion pending",
                detail = "Service bound without foreground notification ($statusSuffix).",
                fixLabel = "Promote Foreground",
                autoFix = { ctx ->
                    com.music.spotui.ui.notification.PlaybackService.ensureStarted(ctx)
                    com.music.spotui.ui.notification.PlaybackService.refreshNotification(ctx)
                    "Promoted PlaybackService to foreground and refreshed Media3 notification."
                },
            )
            telemetry.sessionId > 0 -> HealthCheckResult(
                id = "media_session",
                title = "Media3 Session & Audio Focus",
                category = "Playback",
                severity = CheckSeverity.OK,
                summary = "Active audio session #${telemetry.sessionId}",
                detail = "Media3 controller session bound and forwarding state cleanly ($statusSuffix).",
            )
            telemetry.playbackState == Player.STATE_IDLE -> HealthCheckResult(
                id = "media_session",
                title = "Media3 Session & Audio Focus",
                category = "Playback",
                severity = CheckSeverity.WARNING,
                summary = "Idle (no active track loaded)",
                detail = "Audio session allocates on first track start ($statusSuffix). Tap Refresh once playback starts.",
                fixLabel = "Sync Focus Policy",
                autoFix = { ctx ->
                    SongPlayer.refreshAudioFocusPolicy(ctx)
                    "Refreshed ExoPlayer audio focus attributes."
                },
            )
            else -> HealthCheckResult(
                id = "media_session",
                title = "Media3 Session & Audio Focus",
                category = "Playback",
                severity = CheckSeverity.FAIL,
                summary = "Session ID missing during playback",
                detail = "ExoPlayer reported state=${telemetry.playbackState} with sessionId=${telemetry.sessionId} ($statusSuffix).",
                fixLabel = "Rebind Effects & Focus",
                autoFix = { ctx ->
                    SongPlayer.refreshAudioFocusPolicy(ctx)
                    SongPlayer.refreshAudioEffects(ctx)
                    com.music.spotui.ui.notification.PlaybackService.refreshNotification(ctx)
                    "Re-applied audio focus and DSP session bindings."
                },
            )
        }
    }

    private fun checkExoPlayerPipeline(t: SongPlayer.PlayerTelemetry): HealthCheckResult {
        val stateName = when (t.playbackState) {
            Player.STATE_IDLE -> "IDLE"
            Player.STATE_BUFFERING -> "BUFFERING"
            Player.STATE_READY -> "READY"
            Player.STATE_ENDED -> "ENDED"
            else -> "UNKNOWN(${t.playbackState})"
        }
        return when {
            !t.lastError.isNullOrBlank() -> HealthCheckResult(
                id = "exo_pipeline",
                title = "ExoPlayer Playback Pipeline",
                category = "Playback",
                severity = CheckSeverity.FAIL,
                summary = "Last error: ${t.lastError.take(60)}",
                detail = "State=$stateName • Source=${t.activeSource} • Buffered=${t.bufferedPercentage}% • ${t.lastError}",
                fixLabel = "Clear Stream Caches",
                autoFix = { ctx ->
                    SongPlayer.restartCurrentTrackForQualityChange(ctx)
                    "Invalidated cached stream & restarted current track."
                },
            )
            t.isResolving -> HealthCheckResult(
                id = "exo_pipeline",
                title = "ExoPlayer Playback Pipeline",
                category = "Playback",
                severity = CheckSeverity.WARNING,
                summary = "Resolving stream ($stateName)",
                detail = "Asynchronous resolver active with wake & Wi-Fi lock held.",
            )
            else -> HealthCheckResult(
                id = "exo_pipeline",
                title = "ExoPlayer Playback Pipeline",
                category = "Playback",
                severity = CheckSeverity.OK,
                summary = "$stateName · ${t.activeSource} (${t.currentQuality.ifBlank { "Auto" }})",
                detail = "PlayWhenReady=${t.playWhenReady} · IsPlaying=${t.isPlaying} · Buffer=${t.bufferedPercentage}%",
            )
        }
    }

    private fun checkSoftwareDsp(context: Context): HealthCheckResult {
        val route = AudioEffectController.refreshOutputRoute(context)
        val caps = AudioEffectController.capabilityReport()
        val eqOn = isEqEnabled(context)
        val spOn = isEqSpatialEnabled(context)
        return if (caps.softwareDspAttached) {
            HealthCheckResult(
                id = "sw_dsp",
                title = "Software 7-Band DSP & Spatializer",
                category = "Audio FX",
                severity = CheckSeverity.OK,
                summary = "Active on ${route.route.label} (EQ=${if (eqOn) "ON" else "OFF"}, 3D=${if (spOn) "ON" else "OFF"})",
                detail = "SpotuiDspAudioProcessor & SpatialWideningAudioProcessor active on ${route.deviceName} (BT-Safe Bypass=${caps.bluetoothSafeBypassActive}).",
                fixLabel = "Resync DSP",
                autoFix = { ctx ->
                    SongPlayer.refreshAudioEffects(ctx)
                    "Synced all 7 EQ bands, preamp, limiter, and Bluetooth-safe 3D spatial profile."
                },
            )
        } else {
            HealthCheckResult(
                id = "sw_dsp",
                title = "Software 7-Band DSP & Spatializer",
                category = "Audio FX",
                severity = CheckSeverity.WARNING,
                summary = "Awaiting first ExoPlayer instance (${route.route.label})",
                detail = "DSP processors bind automatically when playback initializes or after crossfade.",
                fixLabel = "Sync Processors",
                autoFix = { ctx ->
                    AudioEffectController.syncSoftwareProcessors(ctx)
                    "Synced DSP preferences ready for next audio buffer."
                },
            )
        }
    }

    private fun checkHardwareAudioFx(): HealthCheckResult {
        val caps = AudioEffectController.capabilityReport()
        val route = AudioEffectController.routeState.value
        return when {
            caps.sessionId <= 0 -> HealthCheckResult(
                id = "hw_audiofx",
                title = "Hardware AudioFX HAL",
                category = "Audio FX",
                severity = CheckSeverity.WARNING,
                summary = "No active audio session yet",
                detail = "Play any track to probe device AudioFX handles.",
            )
            route.isBluetooth || caps.bluetoothSafeBypassActive -> HealthCheckResult(
                id = "hw_audiofx",
                title = "Hardware AudioFX HAL",
                category = "Audio FX",
                severity = CheckSeverity.OK,
                summary = "BT/SW Safe Mode · ${route.deviceName}",
                detail = "HW Virtualizer & PresetReverb are intentionally bypassed so Bluetooth A2DP/BLE audio never mutes. In-process 32-bit PCM EQ + 3D handles all FX.",
            )
            caps.equalizerAvailable || caps.hardwareSpatialAvailable || caps.bassBoostAvailable -> HealthCheckResult(
                id = "hw_audiofx",
                title = "Hardware AudioFX HAL",
                category = "Audio FX",
                severity = CheckSeverity.OK,
                summary = "HW EQ=${caps.equalizerBands} bands · Spatial=${caps.hardwareSpatialAvailable} · Bass=${caps.bassBoostAvailable}",
                detail = "Loudness=${caps.loudnessAvailable}, Room=${caps.roomAvailable}, SW fallback guaranteed active.",
            )
            else -> HealthCheckResult(
                id = "hw_audiofx",
                title = "Hardware AudioFX HAL",
                category = "Audio FX",
                severity = CheckSeverity.WARNING,
                summary = "HW effects blocked on current output; SW DSP active",
                detail = "Device OEM HAL refused AudioEffect handles. Spotui software Biquad EQ + 3D handles processing instead.",
            )
        }
    }

    private fun checkBatteryAndWakeLock(context: Context): HealthCheckResult {
        val exempt = BatteryOptimizationHelper.isIgnoringBatteryOptimization(context)
        val hasForeground = com.music.spotui.ui.notification.ServiceSessionHolder.activeService()?.hasPromotedForeground == true
        return if (exempt) {
            HealthCheckResult(
                id = "battery_opt",
                title = "Background Battery & WakeLock",
                category = "System",
                severity = CheckSeverity.OK,
                summary = if (hasForeground) {
                    "Unrestricted · WIU foreground active"
                } else {
                    "Unrestricted (Exempt from Doze)"
                },
                detail = "OS will not throttle Media3 service or next-track resolution in background (FG=$hasForeground).",
            )
        } else {
            HealthCheckResult(
                id = "battery_opt",
                title = "Background Battery & WakeLock",
                category = "System",
                severity = CheckSeverity.WARNING,
                summary = "Battery optimized — may pause background advances",
                detail = BatteryOptimizationHelper.getManufacturerTips()?.second
                    ?: "Disable battery optimization for Spotui so background transitions stay uninterrupted.",
                fixLabel = "Open Settings",
                autoFix = { ctx ->
                    val intent = BatteryOptimizationHelper.buildRequestOptimizationIntent(ctx).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { ctx.startActivity(intent) }
                        .onFailure {
                            val fallback = BatteryOptimizationHelper.buildAppSettingsIntent(ctx).apply {
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            runCatching { ctx.startActivity(fallback) }
                        }
                    "Opened system battery settings."
                },
            )
        }
    }

    private fun checkPermissions(context: Context): HealthCheckResult {
        val missing = PermissionHelper.missingRuntimePermissions(context)
        return if (missing.isEmpty()) {
            HealthCheckResult(
                id = "permissions",
                title = "Runtime Media & Notification Permissions",
                category = "System",
                severity = CheckSeverity.OK,
                summary = "All required permissions granted",
                detail = "Notifications, Bluetooth routing, and audio read permissions are ready.",
            )
        } else {
            HealthCheckResult(
                id = "permissions",
                title = "Runtime Media & Notification Permissions",
                category = "System",
                severity = CheckSeverity.WARNING,
                summary = "Missing ${missing.size} permission(s)",
                detail = "Missing: ${missing.joinToString { it.substringAfterLast('.') }}",
                fixLabel = "Open App Permissions",
                autoFix = { ctx ->
                    PermissionHelper.openAppPermissionSettings(ctx)
                    "Opened app permissions screen."
                },
            )
        }
    }

    private fun checkNetworkConnectivity(context: Context): HealthCheckResult {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val net = cm?.activeNetwork
        val caps = net?.let { cm.getNetworkCapabilities(it) }
        val connected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        return if (connected) {
            val transport = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                else -> "Active"
            }
            HealthCheckResult(
                id = "network",
                title = "Network Interface",
                category = "Network",
                severity = CheckSeverity.OK,
                summary = "$transport connected",
                detail = "Internet capability verified on active network transport.",
            )
        } else {
            HealthCheckResult(
                id = "network",
                title = "Network Interface",
                category = "Network",
                severity = CheckSeverity.FAIL,
                summary = "Offline / No active internet capability",
                detail = "Check device Wi-Fi, cellular data, or VPN connection.",
            )
        }
    }

    private fun checkDnsAndProxy(context: Context): HealthCheckResult {
        val preset = getDnsPreset(context)
        val proxyRaw = getProxyUrls(context)
        val activeProxies = AuthSessionStore.activeProxyCount()
        return if (proxyRaw.isNotBlank() && activeProxies == 0) {
            HealthCheckResult(
                id = "dns_proxy",
                title = "DNS-over-HTTPS & Proxy Pool",
                category = "Network",
                severity = CheckSeverity.WARNING,
                summary = "Proxy URL configured but 0 valid endpoints parsed",
                detail = "DoH=${preset.displayName}. Check proxy syntax (http://host:port or socks5://host:port).",
                fixLabel = "Clear Invalid Proxies",
                autoFix = { ctx ->
                    com.music.spotui.data.preferences.setProxyUrls(ctx, "")
                    "Cleared malformed proxy string and restored direct routing."
                },
            )
        } else {
            HealthCheckResult(
                id = "dns_proxy",
                title = "DNS-over-HTTPS & Proxy Pool",
                category = "Network",
                severity = CheckSeverity.OK,
                summary = "DoH: ${preset.displayName} · $activeProxies proxy(s)",
                detail = "IP rotation=${AuthSessionStore.ipRotationEnabled}, bypassMedia=${AuthSessionStore.streamBypassProxy}",
            )
        }
    }

    private fun checkSpotifySession(context: Context): HealthCheckResult {
        val spDc = SpotifySession.spDc(context)
        return if (spDc.isNotBlank()) {
            HealthCheckResult(
                id = "spotify_auth",
                title = "Spotify Metadata Session (sp_dc)",
                category = "Accounts",
                severity = CheckSeverity.OK,
                summary = "Authenticated (${spDc.take(8)}…)",
                detail = "Full Spotify web metadata & canvas/lyrics token minting enabled.",
            )
        } else {
            HealthCheckResult(
                id = "spotify_auth",
                title = "Spotify Metadata Session (sp_dc)",
                category = "Accounts",
                severity = CheckSeverity.WARNING,
                summary = "No sp_dc cookie (anonymous fallback only)",
                detail = "Sign in to Spotify in Settings to unlock full personalized library & synced canvas/lyrics.",
            )
        }
    }

    private fun checkYouTubeInnerTubeSession(): HealthCheckResult {
        val state = AuthSessionStore.authStateFlow.value
        val hasVisitor = !AuthSessionStore.visitorData.isNullOrBlank()
        val hasPoToken = !state.poToken.isNullOrBlank() || !state.poTokenPlayer.isNullOrBlank()
        return when {
            state.hasPlaybackLoginContext -> HealthCheckResult(
                id = "yt_session",
                title = "YouTube InnerTube Engine",
                category = "Accounts",
                severity = CheckSeverity.OK,
                summary = "Authenticated playback + PoToken=${if (hasPoToken) "Ready" else "Auto"}",
                detail = "Fingerprint=${state.fingerprint.take(12)}",
            )
            hasVisitor || hasPoToken -> HealthCheckResult(
                id = "yt_session",
                title = "YouTube InnerTube Engine",
                category = "Accounts",
                severity = CheckSeverity.OK,
                summary = "Visitor session active (PoToken=${if (hasPoToken) "Cached" else "On-Demand"})",
                detail = "Ready for song fallback & explicit stream resolution.",
            )
            else -> HealthCheckResult(
                id = "yt_session",
                title = "YouTube InnerTube Engine",
                category = "Accounts",
                severity = CheckSeverity.WARNING,
                summary = "Cold InnerTube session (no cached PoToken/visitorData)",
                detail = "Will bootstrap automatically on first YouTube fallback request.",
            )
        }
    }

    private fun checkDeezerAccount(context: Context): HealthCheckResult {
        val arl = getDeezerArl(context)
        if (arl.isBlank()) {
            return HealthCheckResult(
                id = "deezer_auth",
                title = "Deezer HiFi ARL & Direct Cdn",
                category = "Accounts",
                severity = CheckSeverity.WARNING,
                summary = "Not linked (community mirror only)",
                detail = "Sign in via Lossless Priority settings to unlock direct Deezer FLAC/320 decryption.",
            )
        }
        DeezerSession.setArl(arl)
        val session = runCatching { DeezerSession.authorize(forceRefresh = true) }.getOrNull()
        return if (session != null && session.authorized && session.userId > 0L && DeezerSession.hasArl()) {
            val tierBadge = when (session.entitledQuality) {
                DeezerSession.QUALITY_FLAC -> "HiFi FLAC"
                DeezerSession.QUALITY_MP3_320 -> "Premium 320"
                else -> "Free 128"
            }
            HealthCheckResult(
                id = "deezer_auth",
                title = "Deezer HiFi ARL & Direct Cdn",
                category = "Accounts",
                severity = CheckSeverity.OK,
                summary = "${session.userName.ifBlank { "Linked" }} ($tierBadge)",
                detail = "${session.detail} • Direct Blowfish-CBC chunk decryption ready.",
            )
        } else {
            HealthCheckResult(
                id = "deezer_auth",
                title = "Deezer HiFi ARL & Direct Cdn",
                category = "Accounts",
                severity = CheckSeverity.FAIL,
                summary = "Saved ARL rejected or expired",
                detail = "Re-authenticate Deezer in Settings > Lossless Route Priority to restore direct FLAC.",
                fixLabel = "Clear Stale ARL",
                autoFix = { ctx ->
                    com.music.spotui.data.preferences.setDeezerArl(ctx, "")
                    "Cleared expired Deezer ARL cookie."
                },
            )
        }
    }

    private fun checkCustomMirror(context: Context): HealthCheckResult {
        val customUrl = getCustomLosslessMirrorUrl(context)
        if (customUrl.isBlank()) {
            return HealthCheckResult(
                id = "custom_mirror",
                title = "Custom Lossless Mirror",
                category = "Lossless",
                severity = CheckSeverity.OK,
                summary = "Disabled (using built-in mirrors)",
                detail = "Configure a self-hosted HiFi/SpotiFlac mirror URL in Lossless Settings if desired.",
            )
        }
        val pingMs = pingUrl(customUrl)
        return if (pingMs >= 0L) {
            HealthCheckResult(
                id = "custom_mirror",
                title = "Custom Lossless Mirror",
                category = "Lossless",
                severity = CheckSeverity.OK,
                summary = "Reachable (${pingMs}ms)",
                detail = customUrl,
            )
        } else {
            HealthCheckResult(
                id = "custom_mirror",
                title = "Custom Lossless Mirror",
                category = "Lossless",
                severity = CheckSeverity.FAIL,
                summary = "Unreachable",
                detail = "Failed HTTP probe to $customUrl",
                fixLabel = "Disable Custom Mirror",
                autoFix = { ctx ->
                    com.music.spotui.data.preferences.setCustomLosslessMirrorUrl(ctx, "")
                    "Cleared unreachable custom mirror URL."
                },
            )
        }
    }

    private suspend fun checkLosslessMirrors(context: Context): HealthCheckResult {
        val statuses = runCatching { SpotiFlac.getProviderStatuses() }.getOrElse { emptyList() }
        if (statuses.isEmpty()) {
            return HealthCheckResult(
                id = "lossless_mirrors",
                title = "Lossless Providers & Cooldowns",
                category = "Lossless",
                severity = CheckSeverity.WARNING,
                summary = "Could not probe mirrors",
                detail = "Check internet connectivity and tap Scan again.",
            )
        }
        val up = statuses.count { it.isUp && !it.isCooldown }
        val cooldowns = statuses.count { it.isCooldown }
        val enabledCount = ProviderRoutingPreferences.enabledProviders(context).size
        return when {
            cooldowns > 0 -> HealthCheckResult(
                id = "lossless_mirrors",
                title = "Lossless Providers & Cooldowns",
                category = "Lossless",
                severity = CheckSeverity.WARNING,
                summary = "$up/${statuses.size} online • $cooldowns cooling down ($enabledCount enabled)",
                detail = statuses.joinToString(" | ") { "${it.name}: ${if (it.isCooldown) "cooldown" else if (it.isUp) "UP" else "DOWN"}" },
                fixLabel = "Clear Cooldowns",
                autoFix = {
                    SpotiFlac.clearCooldowns()
                    SpotiFlac.clearStatusCache()
                    "Reset all HTTP 503/429 provider cooldown timers."
                },
            )
            up == 0 -> HealthCheckResult(
                id = "lossless_mirrors",
                title = "Lossless Providers & Cooldowns",
                category = "Lossless",
                severity = CheckSeverity.FAIL,
                summary = "0/${statuses.size} mirrors reachable",
                detail = "All public lossless mirrors failed health check; playback will fall back to Deezer/YouTube.",
                fixLabel = "Refresh Mirrors",
                autoFix = {
                    SpotiFlac.clearCooldowns()
                    SpotiFlac.clearStatusCache()
                    "Cleared provider status cache and cooldowns."
                },
            )
            else -> HealthCheckResult(
                id = "lossless_mirrors",
                title = "Lossless Providers & Cooldowns",
                category = "Lossless",
                severity = CheckSeverity.OK,
                summary = "$up/${statuses.size} online • $enabledCount active routes",
                detail = statuses.joinToString(" · ") { "${it.name}:${if (it.isUp) "OK" else "ERR"}" },
            )
        }
    }

    private fun checkLrclibLyrics(): HealthCheckResult {
        val ms = pingUrl("https://lrclib.net/api/get?artist_name=test&track_name=test")
        return if (ms >= 0L) {
            HealthCheckResult(
                id = "lrclib",
                title = "LRCLIB Synced Lyrics Endpoint",
                category = "Services",
                severity = CheckSeverity.OK,
                summary = "Online (${ms}ms)",
                detail = "Millisecond LRC fallback server responding normally.",
            )
        } else {
            HealthCheckResult(
                id = "lrclib",
                title = "LRCLIB Synced Lyrics Endpoint",
                category = "Services",
                severity = CheckSeverity.WARNING,
                summary = "LRCLIB unreachable or timed out",
                detail = "Spotify synced lyrics still work if sp_dc is set; LRC fallback temporarily unavailable.",
            )
        }
    }

    private fun checkOrphanedDownloads(context: Context): HealthCheckResult {
        val dir = File(context.filesDir, "downloads")
        val orphans = dir.listFiles { _, name ->
            name.endsWith(".part") || name.endsWith(".tmp") || name.endsWith(".flacpart")
        }?.toList().orEmpty()
        return if (orphans.isEmpty()) {
            HealthCheckResult(
                id = "orphan_downloads",
                title = "Offline Download Integrity",
                category = "Storage",
                severity = CheckSeverity.OK,
                summary = "Clean (no partial temp files)",
                detail = "Downloads directory verified free of abandoned transfer artifacts.",
            )
        } else {
            val totalKb = orphans.sumOf { it.length() } / 1024L
            HealthCheckResult(
                id = "orphan_downloads",
                title = "Offline Download Integrity",
                category = "Storage",
                severity = CheckSeverity.WARNING,
                summary = "${orphans.size} stale temp file(s) (${totalKb} KB)",
                detail = orphans.joinToString(", ") { it.name },
                fixLabel = "Purge Temp Files",
                autoFix = { ctx ->
                    var count = 0
                    File(ctx.filesDir, "downloads").listFiles { _, name ->
                        name.endsWith(".part") || name.endsWith(".tmp") || name.endsWith(".flacpart")
                    }?.forEach { if (it.delete()) count++ }
                    "Removed $count incomplete download artifact(s)."
                },
            )
        }
    }

    private fun checkMediaCache(context: Context): HealthCheckResult {
        val summary = CacheStorageManager.summarize(context)
        val highWaterBytes = 750L * 1024L * 1024L
        return if (summary.totalBytes > highWaterBytes) {
            HealthCheckResult(
                id = "media_cache",
                title = "Media & Artwork Disk Cache",
                category = "Storage",
                severity = CheckSeverity.WARNING,
                summary = "${summary.displaySize} (exceeds 750 MB)",
                detail = summary.detail,
                fixLabel = "Trim Excess",
                autoFix = { ctx ->
                    val freed = CacheStorageManager.autoEvictExcessCache(ctx, 400L * 1024L * 1024L)
                    "Evicted ${freed / (1024 * 1024)} MB of oldest media cache."
                },
            )
        } else {
            HealthCheckResult(
                id = "media_cache",
                title = "Media & Artwork Disk Cache",
                category = "Storage",
                severity = CheckSeverity.OK,
                summary = summary.displaySize,
                detail = summary.detail,
            )
        }
    }

    private fun checkCrashReports(context: Context): HealthCheckResult {
        val hasCrash = CrashHandler.hasCrashReport(context)
        val count = CrashHandler.getAllCrashReportFiles(context).size
        return if (!hasCrash && count == 0) {
            HealthCheckResult(
                id = "crashes",
                title = "Crash Dump Repository",
                category = "Stability",
                severity = CheckSeverity.OK,
                summary = "Zero unread crashes",
                detail = "No uncaught native/JVM crash logs pending inspection.",
            )
        } else {
            HealthCheckResult(
                id = "crashes",
                title = "Crash Dump Repository",
                category = "Stability",
                severity = CheckSeverity.WARNING,
                summary = "$count crash report(s) stored",
                detail = "Inspect crash dump in Telemetry tab or clear archived logs.",
                fixLabel = "Clear Crash Logs",
                autoFix = { ctx ->
                    CrashHandler.clearAllCrashReports(ctx)
                    "Cleared all stored crash reports."
                },
            )
        }
    }

    private fun checkQueueIntegrity(context: Context): HealthCheckResult {
        val total = SongPlayer.queueSize()
        if (total == 0) {
            return HealthCheckResult(
                id = "queue_integrity",
                title = "Active Queue Integrity",
                category = "Playback",
                severity = CheckSeverity.OK,
                summary = "Queue empty",
                detail = "Play a playlist, album, or search result to inspect live queue hygiene.",
            )
        }
        return HealthCheckResult(
            id = "queue_integrity",
            title = "Active Queue Integrity",
            category = "Playback",
            severity = CheckSeverity.OK,
            summary = "$total track(s) in queue",
            detail = "Metadata registries & play-query sanitation active.",
            fixLabel = "Deduplicate Queue",
            autoFix = {
                val removed = SongPlayer.deduplicateQueue()
                "Removed $removed duplicate track(s) from active queue."
            },
        )
    }

    private suspend fun checkBluetoothSpecsAndConflicts(context: Context): HealthCheckResult {
        val bt = com.music.spotui.audio.BluetoothAudioInspector.inspect(context)
        val firstFail = bt.conflicts.firstOrNull { it.severity == CheckSeverity.FAIL }
            ?: bt.conflicts.firstOrNull { it.severity == CheckSeverity.WARNING }
        return when {
            firstFail != null -> HealthCheckResult(
                id = "bluetooth_specs",
                title = "Bluetooth & Output Specs",
                category = "Audio FX",
                severity = firstFail.severity,
                summary = "${bt.deviceName} (${bt.codecName}) — ${firstFail.title}",
                detail = "${bt.transportLabel} · ${bt.sampleRateKhz} · ${bt.bitsPerSample} · ${firstFail.explanation}",
                fixLabel = firstFail.fixLabel,
                autoFix = firstFail.applyFix?.let { fn ->
                    { ctx -> fn(ctx) }
                },
            )
            bt.isBluetoothConnected -> HealthCheckResult(
                id = "bluetooth_specs",
                title = "Bluetooth & Output Specs",
                category = "Audio FX",
                severity = CheckSeverity.OK,
                summary = "${bt.deviceName} · ${bt.codecName} · ${bt.sampleRateKhz}",
                detail = "${bt.bitsPerSample} · ${bt.channelMode} · BT-Safe=${bt.bluetoothSafeBypassActive} · Supported: ${bt.supportedRates}",
            )
            else -> HealthCheckResult(
                id = "bluetooth_specs",
                title = "Bluetooth & Output Specs",
                category = "Audio FX",
                severity = CheckSeverity.OK,
                summary = "${bt.activeOutputType} · ${bt.sampleRateKhz}",
                detail = "${bt.transportLabel} · ${bt.bitsPerSample} · Zero BT conflicts detected.",
            )
        }
    }

    private fun checkComponentFaults(): HealthCheckResult {
        val unresolved = DevConsoleManager.faults.value.filter { !it.resolved }
        return if (unresolved.isEmpty()) {
            HealthCheckResult(
                id = "component_faults",
                title = "Component & Button Fault Monitor",
                category = "Stability",
                severity = CheckSeverity.OK,
                summary = "Zero unresolved UI/action faults",
                detail = "All monitored buttons, playback controls, playlists, and sheets responded normally.",
            )
        } else {
            val latest = unresolved.first()
            HealthCheckResult(
                id = "component_faults",
                title = "Component & Button Fault Monitor",
                category = "Stability",
                severity = CheckSeverity.WARNING,
                summary = "${unresolved.size} fault(s): ${latest.component}.${latest.action}",
                detail = latest.reason + if (latest.autoFixHint.isNotBlank()) " Hint: ${latest.autoFixHint}" else "",
                fixLabel = "Clear Faults",
                autoFix = {
                    DevConsoleManager.clearFaults()
                    "Cleared recorded component faults."
                },
            )
        }
    }

    private fun pingUrl(url: String): Long {
        if (url.isBlank()) return -1L
        val start = System.currentTimeMillis()
        return runCatching {
            val req = Request.Builder().url(url).get().build()
            pingClient.newCall(req).execute().use { resp ->
                if (resp.code in 200..499) System.currentTimeMillis() - start else -1L
            }
        }.getOrDefault(-1L)
    }
}
