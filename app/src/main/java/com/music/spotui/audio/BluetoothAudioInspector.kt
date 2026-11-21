package com.music.spotui.audio

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothCodecConfig
import android.bluetooth.BluetoothCodecStatus
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.music.spotui.data.preferences.getBassBoostStrength
import com.music.spotui.data.preferences.getEqPreampDb
import com.music.spotui.data.preferences.isAudioOffloadEnabled
import com.music.spotui.data.preferences.isEqEnabled
import com.music.spotui.data.preferences.isEqSpatialEnabled
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.theme.ThemePreferences
import com.music.spotui.util.CheckSeverity
import com.music.spotui.util.DevConsoleManager
import com.music.spotui.util.PermissionHelper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Inspects active Bluetooth/wired headset hardware capabilities (codec, sample rate, bit depth,
 * channel layout, supported sink formats) and actively detects SpotUI settings/DSP conditions that
 * could cause muffled, mono, stuttered, or silent audio on Bluetooth output devices.
 */
object BluetoothAudioInspector {

    data class BluetoothConflict(
        val id: String,
        val title: String,
        val severity: CheckSeverity,
        val causedBySpotui: Boolean,
        val explanation: String,
        val fixLabel: String? = null,
        val applyFix: ((Context) -> String)? = null,
    )

    data class BluetoothSpecsReport(
        val timestampMs: Long = System.currentTimeMillis(),
        val isBluetoothConnected: Boolean = false,
        val hasBluetoothPermission: Boolean = true,
        val activeOutputType: String = "Device Speaker",
        val deviceName: String = "Device Speaker",
        val transportLabel: String = "Internal Speaker",
        val codecName: String = "PCM (Internal)",
        val sampleRateKhz: String = "48.0 kHz",
        val bitsPerSample: String = "32-bit float",
        val channelMode: String = "Stereo",
        val supportedRates: String = "44.1 / 48 kHz",
        val supportedEncodings: String = "PCM 16/Float",
        val selectableCodecs: List<String> = emptyList(),
        val bluetoothSafeBypassActive: Boolean = false,
        val softwareDspActive: Boolean = false,
        val offloadEnabled: Boolean = false,
        val appVolumePercent: Int = 100,
        val conflicts: List<BluetoothConflict> = emptyList(),
    ) {
        val issueCount: Int get() = conflicts.count { it.severity != CheckSeverity.OK }
        val appCausedIssueCount: Int get() = conflicts.count { it.causedBySpotui && it.severity != CheckSeverity.OK }
    }

    suspend fun inspect(context: Context): BluetoothSpecsReport {
        val appCtx = context.applicationContext
        val am = appCtx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val route = AudioEffectController.refreshOutputRoute(appCtx)
        val caps = AudioEffectController.capabilityReport()
        val devices = runCatching { am?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.toList().orEmpty() }.getOrElse { emptyList() }

        val btAudioDevice = devices.firstOrNull { d ->
            d.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                d.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                d.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                d.type == AudioDeviceInfo.TYPE_BLE_BROADCAST ||
                d.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                d.type == AudioDeviceInfo.TYPE_HEARING_AID
        }

        val primaryDevice = btAudioDevice
            ?: devices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
            }
            ?: devices.firstOrNull()

        val hasBtPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(appCtx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else true

        val a2dpCodec = if (btAudioDevice != null && hasBtPerm) {
            withTimeoutOrNull(900L) { queryA2dpCodecInfo(appCtx) }
        } else null

        val transport = when (primaryDevice?.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP Stereo"
            AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE Audio (LC3)"
            AudioDeviceInfo.TYPE_BLE_SPEAKER -> "Bluetooth LE Speaker"
            AudioDeviceInfo.TYPE_BLE_BROADCAST -> "Bluetooth Auracast"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth HFP/SCO Call Mode"
            AudioDeviceInfo.TYPE_HEARING_AID -> "Bluetooth Hearing Aid (ASHA)"
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Audio / DAC"
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired 3.5mm Headset"
            else -> "Internal Device Speaker"
        }

        val ratesText = primaryDevice?.sampleRates
            ?.filter { it > 0 }
            ?.distinct()
            ?.sorted()
            ?.joinToString(" / ") { rate ->
                if (rate % 1000 == 0) "${rate / 1000} kHz" else "%.1f kHz".format(rate / 1000.0)
            }
            ?.ifBlank { "44.1 / 48 kHz" }
            ?: "44.1 / 48 kHz"

        val encText = primaryDevice?.encodings
            ?.map { encodingLabel(it) }
            ?.distinct()
            ?.joinToString(" · ")
            ?.ifBlank { "PCM 16-bit" }
            ?: "PCM 16-bit"

        val codecLabel = when {
            primaryDevice?.type == AudioDeviceInfo.TYPE_BLE_HEADSET -> "LC3 (LE Audio)"
            primaryDevice?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "CVSD/mSBC (SCO Call)"
            a2dpCodec != null -> a2dpCodec.codecLabel
            btAudioDevice != null -> "A2DP (grant BT perm for exact codec)"
            else -> encText.substringBefore(" ·")
        }

        val sampleRateDisplay = a2dpCodec?.sampleRateLabel
            ?: primaryDevice?.sampleRates?.maxOrNull()?.let { r ->
                if (r % 1000 == 0) "${r / 1000} kHz" else "%.1f kHz".format(r / 1000.0)
            }
            ?: "48 kHz"

        val bitsDisplay = a2dpCodec?.bitsLabel ?: when {
            primaryDevice?.encodings?.contains(AudioFormat.ENCODING_PCM_FLOAT) == true -> "32-bit float (DSP)"
            primaryDevice?.encodings?.contains(AudioFormat.ENCODING_PCM_24BIT_PACKED) == true ||
                primaryDevice?.encodings?.contains(AudioFormat.ENCODING_PCM_32BIT) == true -> "24/32-bit"
            else -> "16-bit / 32-bit DSP"
        }

        val channelsDisplay = a2dpCodec?.channelLabel ?: when {
            ThemePreferences.monoAudio(appCtx) -> "Mono (Downmixed by SpotUI)"
            primaryDevice?.channelCounts?.maxOrNull() == 1 -> "Mono Sink"
            else -> "Stereo (2ch)"
        }

        val devName = a2dpCodec?.deviceName?.takeIf { it.isNotBlank() }
            ?: primaryDevice?.productName?.toString()?.trim()?.takeIf { it.isNotBlank() }
            ?: route.deviceName

        val offloadOn = isAudioOffloadEnabled(appCtx)
        val eqOn = isEqEnabled(appCtx)
        val spatialOn = isEqSpatialEnabled(appCtx)
        val preampDb = getEqPreampDb(appCtx)
        val bassStrength = getBassBoostStrength(appCtx)
        val appVol = SongPlayer.getAppVolumePercent(appCtx)
        val monoOn = ThemePreferences.monoAudio(appCtx)
        val balance = ThemePreferences.audioBalance(appCtx)

        val conflicts = mutableListOf<BluetoothConflict>()

        if (btAudioDevice != null && !hasBtPerm) {
            conflicts += BluetoothConflict(
                id = "bt_perm_missing",
                title = "Bluetooth Connect permission missing",
                severity = CheckSeverity.WARNING,
                causedBySpotui = true,
                explanation = "SpotUI cannot read A2DP codec (LDAC/aptX/AAC/SBC), bit depth, or selectable headset profiles until Nearby Devices permission is granted.",
                fixLabel = "Open App Permissions",
                applyFix = { ctx ->
                    PermissionHelper.openAppPermissionSettings(ctx)
                    "Opened app permissions screen."
                },
            )
        }

        if (btAudioDevice?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
            conflicts += BluetoothConflict(
                id = "bt_sco_active",
                title = "Headset stuck in low-bandwidth HFP/SCO call mode",
                severity = CheckSeverity.WARNING,
                causedBySpotui = false,
                explanation = "Android is routing media over hands-free SCO (8–16 kHz mono). Another app or active call may hold Communication focus.",
                fixLabel = "Reset Audio Focus",
                applyFix = { ctx ->
                    SongPlayer.refreshAudioFocusPolicy(ctx)
                    SongPlayer.refreshAudioEffects(ctx)
                    "Requested standard MEDIA A2DP audio focus."
                },
            )
        }

        if (offloadOn && (spatialOn || eqOn || bassStrength > 0 || appVol < 100)) {
            conflicts += BluetoothConflict(
                id = "offload_blocks_dsp",
                title = "Audio Offload is bypassing 3D Spatial & EQ on headset",
                severity = CheckSeverity.FAIL,
                causedBySpotui = true,
                explanation = "Experimental Audio Offload sends compressed frames straight to the DSP chip, skipping SpotUI's 3D soundstage and 7-band EQ.",
                fixLabel = "Disable Offload",
                applyFix = { ctx ->
                    com.music.spotui.data.preferences.setAudioOffloadEnabled(ctx, false)
                    SongPlayer.restartCurrentTrackForQualityChange(ctx)
                    "Disabled Audio Offload so 3D Spatial & EQ process every Bluetooth frame."
                },
            )
        }

        if (btAudioDevice != null && !caps.bluetoothSafeBypassActive && caps.sessionId > 0) {
            conflicts += BluetoothConflict(
                id = "hw_fx_bt_risk",
                title = "Hardware AudioFX may mute Bluetooth sink",
                severity = CheckSeverity.FAIL,
                causedBySpotui = true,
                explanation = "Bluetooth headset active without BT-Safe HW bypass. Samsung/Qualcomm A2DP HALs can output silence if hardware Virtualizer attaches.",
                fixLabel = "Enable BT-Safe DSP",
                applyFix = { ctx ->
                    SongPlayer.refreshAudioEffects(ctx)
                    "Applied BT-Safe DSP bypass and synced 32-bit PCM Spatializer."
                },
            )
        }

        if (appVol == 0) {
            conflicts += BluetoothConflict(
                id = "app_volume_muted",
                title = "SpotUI in-app volume slider is set to 0%",
                severity = CheckSeverity.FAIL,
                causedBySpotui = true,
                explanation = "Device media volume may look high while SpotUI's independent 0–100% mixer gain is muted, causing apparent Bluetooth silence.",
                fixLabel = "Restore 100% Gain",
                applyFix = { ctx ->
                    SongPlayer.setAppVolumePercent(ctx, 100)
                    "Restored SpotUI internal volume slider to 100%."
                },
            )
        }

        if (preampDb >= 4.5f && bassStrength >= 700) {
            conflicts += BluetoothConflict(
                id = "bt_headroom_clip",
                title = "Preamp + Bass Boost headroom may distort or trigger BT limiter",
                severity = CheckSeverity.WARNING,
                causedBySpotui = true,
                explanation = "Preamp +${"%.1f".format(preampDb)} dB plus heavy Bass Boost ($bassStrength/1000) pushes SBC/AAC encoders into harsh dynamic pumping.",
                fixLabel = "Normalize Headroom",
                applyFix = { ctx ->
                    com.music.spotui.data.preferences.setEqPreampDb(ctx, 0f)
                    com.music.spotui.data.preferences.setBassBoostStrength(ctx, 400)
                    SongPlayer.refreshAudioEffects(ctx)
                    "Set Preamp to 0 dB and Bass Boost to 40% for clean Bluetooth headroom."
                },
            )
        }

        if (btAudioDevice != null && spatialOn && monoOn) {
            conflicts += BluetoothConflict(
                id = "mono_kills_3d",
                title = "Mono Mix collapses 3D Spatial soundstage",
                severity = CheckSeverity.WARNING,
                causedBySpotui = true,
                explanation = "Mono downmix merges Left/Right channels after widening, canceling stereo spatial cues on your headset.",
                fixLabel = "Restore Stereo",
                applyFix = { ctx ->
                    ThemePreferences.setMonoAudio(ctx, false)
                    SongPlayer.refreshAudioEffects(ctx)
                    "Disabled Mono Mix to restore full stereo 3D soundstage."
                },
            )
        }

        if (kotlin.math.abs(balance) > 0.65f) {
            conflicts += BluetoothConflict(
                id = "extreme_balance_skew",
                title = "Extreme L/R stereo balance (${(balance * 100).toInt()}%)",
                severity = CheckSeverity.WARNING,
                causedBySpotui = true,
                explanation = "One earbud will sound near-silent because SpotUI L/R balance is heavily skewed.",
                fixLabel = "Center Balance",
                applyFix = { ctx ->
                    ThemePreferences.setAudioBalance(ctx, 0f)
                    SongPlayer.refreshAudioEffects(ctx)
                    "Centered L/R audio balance to 0%."
                },
            )
        }

        val report = BluetoothSpecsReport(
            isBluetoothConnected = btAudioDevice != null,
            hasBluetoothPermission = hasBtPerm,
            activeOutputType = route.route.label,
            deviceName = devName,
            transportLabel = transport,
            codecName = codecLabel,
            sampleRateKhz = sampleRateDisplay,
            bitsPerSample = bitsDisplay,
            channelMode = channelsDisplay,
            supportedRates = ratesText,
            supportedEncodings = encText,
            selectableCodecs = a2dpCodec?.selectableCodecs.orEmpty(),
            bluetoothSafeBypassActive = caps.bluetoothSafeBypassActive,
            softwareDspActive = caps.softwareDspAttached,
            offloadEnabled = offloadOn,
            appVolumePercent = appVol,
            conflicts = conflicts,
        )

        val summary = if (report.isBluetoothConnected) {
            "${report.deviceName} · ${report.codecName} · ${report.sampleRateKhz} · ${report.issueCount} issue(s)"
        } else {
            "${report.activeOutputType} · ${report.sampleRateKhz}"
        }
        DevConsoleManager.updateDiagnostics { copy(bluetoothSummary = summary) }
        DevConsoleManager.logBluetoothDiagnostic("BT/Audio route probe: $summary")
        return report
    }

    private data class A2dpCodecSnapshot(
        val deviceName: String,
        val codecLabel: String,
        val sampleRateLabel: String,
        val bitsLabel: String,
        val channelLabel: String,
        val selectableCodecs: List<String>,
    )

    @Suppress("DEPRECATION")
    private suspend fun queryA2dpCodecInfo(context: Context): A2dpCodecSnapshot? =
        suspendCancellableCoroutine { cont ->
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter: BluetoothAdapter? = manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
            if (adapter == null || !adapter.isEnabled) {
                if (cont.isActive) cont.resume(null)
                return@suspendCancellableCoroutine
            }
            val started = runCatching {
                adapter.getProfileProxy(
                    context,
                    object : BluetoothProfile.ServiceListener {
                        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                            val result = runCatching {
                                val a2dp = proxy as? android.bluetooth.BluetoothA2dp
                                val activeDev = a2dp?.connectedDevices?.firstOrNull()
                                val status: BluetoothCodecStatus? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && a2dp != null) {
                                    runCatching {
                                        val m = a2dp.javaClass.getMethod("getCodecStatus", android.bluetooth.BluetoothDevice::class.java)
                                        m.invoke(a2dp, activeDev) as? BluetoothCodecStatus
                                    }.getOrNull()
                                } else null
                                val cfg: BluetoothCodecConfig? = status?.codecConfig
                                val devName = runCatching { activeDev?.name }.getOrNull().orEmpty()
                                val selectable = runCatching {
                                    status?.codecsSelectableCapabilities?.map { codecTypeName(it.codecType) }?.distinct().orEmpty()
                                }.getOrDefault(emptyList())
                                if (cfg != null) {
                                    A2dpCodecSnapshot(
                                        deviceName = devName,
                                        codecLabel = codecTypeName(cfg.codecType),
                                        sampleRateLabel = a2dpSampleRateName(cfg.sampleRate),
                                        bitsLabel = a2dpBitsName(cfg.bitsPerSample),
                                        channelLabel = a2dpChannelName(cfg.channelMode),
                                        selectableCodecs = selectable,
                                    )
                                } else if (devName.isNotBlank()) {
                                    A2dpCodecSnapshot(
                                        deviceName = devName,
                                        codecLabel = "A2DP Standard",
                                        sampleRateLabel = "44.1 / 48 kHz",
                                        bitsLabel = "16-bit",
                                        channelLabel = "Stereo",
                                        selectableCodecs = selectable,
                                    )
                                } else null
                            }.getOrNull()
                            if (proxy != null) runCatching { adapter.closeProfileProxy(profile, proxy) }
                            if (cont.isActive) cont.resume(result)
                        }

                        override fun onServiceDisconnected(profile: Int) {
                            if (cont.isActive) cont.resume(null)
                        }
                    },
                    BluetoothProfile.A2DP,
                )
            }.getOrDefault(false)
            if (!started && cont.isActive) cont.resume(null)
        }

    private fun codecTypeName(type: Int): String = when (type) {
        0 -> "SBC"
        1 -> "AAC"
        2 -> "Qualcomm aptX"
        3 -> "Qualcomm aptX HD"
        4 -> "Sony LDAC"
        5 -> "LC3 (LE Audio)"
        6 -> "Opus"
        else -> "Codec #$type"
    }

    private fun a2dpSampleRateName(rate: Int): String = when (rate) {
        BluetoothCodecConfig.SAMPLE_RATE_44100 -> "44.1 kHz"
        BluetoothCodecConfig.SAMPLE_RATE_48000 -> "48.0 kHz"
        BluetoothCodecConfig.SAMPLE_RATE_88200 -> "88.2 kHz"
        BluetoothCodecConfig.SAMPLE_RATE_96000 -> "96.0 kHz"
        BluetoothCodecConfig.SAMPLE_RATE_176400 -> "176.4 kHz"
        BluetoothCodecConfig.SAMPLE_RATE_192000 -> "192.0 kHz"
        else -> "Auto (48 kHz)"
    }

    private fun a2dpBitsName(bits: Int): String = when (bits) {
        BluetoothCodecConfig.BITS_PER_SAMPLE_16 -> "16-bit"
        BluetoothCodecConfig.BITS_PER_SAMPLE_24 -> "24-bit"
        BluetoothCodecConfig.BITS_PER_SAMPLE_32 -> "32-bit"
        else -> "16/24-bit"
    }

    private fun a2dpChannelName(mode: Int): String = when (mode) {
        BluetoothCodecConfig.CHANNEL_MODE_MONO -> "Mono"
        BluetoothCodecConfig.CHANNEL_MODE_STEREO -> "Stereo (A2DP)"
        else -> "Stereo"
    }

    private fun encodingLabel(enc: Int): String = when (enc) {
        AudioFormat.ENCODING_PCM_16BIT -> "PCM16"
        AudioFormat.ENCODING_PCM_FLOAT -> "PCM-Float"
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> "PCM24"
        AudioFormat.ENCODING_PCM_32BIT -> "PCM32"
        AudioFormat.ENCODING_AAC_LC -> "AAC"
        AudioFormat.ENCODING_MP3 -> "MP3"
        AudioFormat.ENCODING_OPUS -> "Opus"
        else -> "Enc($enc)"
    }
}
