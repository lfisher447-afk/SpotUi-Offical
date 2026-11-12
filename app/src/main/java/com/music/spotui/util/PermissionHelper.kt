// File: app/src/main/java/com/music/spotui/util/PermissionHelper.kt
package com.music.spotui.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/** Determines the minimal runtime permissions and system states for SpotUI features. */
object PermissionHelper {
    /** Returns whether [permission] has been granted to this application. */
    fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Returns media-library permissions relevant to the current Android version. */
    fun mediaReadPermissions(): List<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(Manifest.permission.READ_MEDIA_AUDIO)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        else -> emptyList()
    }

    /** Returns the notification permission only on Android versions that require it. */
    fun notificationPermissions(): List<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        emptyList()
    }

    /** Returns Bluetooth connect permission on Android 12+ (API 31+) for BT device routing. */
    fun bluetoothConnectPermissions(): List<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        emptyList()
    }

    fun hasNotificationPermission(context: Context): Boolean {
        val perms = notificationPermissions()
        return perms.isEmpty() || perms.all { isGranted(context, it) }
    }

    fun hasMediaReadPermission(context: Context): Boolean {
        val perms = mediaReadPermissions()
        return perms.isEmpty() || perms.all { isGranted(context, it) }
    }

    fun hasBluetoothConnectPermission(context: Context): Boolean {
        val perms = bluetoothConnectPermissions()
        return perms.isEmpty() || perms.all { isGranted(context, it) }
    }

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return runCatching { pm.isIgnoringBatteryOptimizations(context.packageName) }.getOrDefault(false)
    }

    fun openAppPermissionSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun requestIgnoreBatteryOptimizationIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** Returns all recommended runtime permissions not yet granted on this device. */
    fun missingRuntimePermissions(context: Context): List<String> =
        (notificationPermissions() + mediaReadPermissions() + bluetoothConnectPermissions())
            .distinct()
            .filterNot { isGranted(context, it) }
}
