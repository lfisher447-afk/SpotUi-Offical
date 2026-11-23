package com.music.spotui.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/**
 * Helper utilities for checking and configuring Spotui's [MediaAccessibilityService]
 * on Samsung One UI 9.0 / Android 17 devices.
 */
object AccessibilityHelper {

    const val ONE_UI_RESTRICTED_SETTINGS_TIP: String =
        "One UI 9.0 / Android 17 Setup:\n" +
            "1. Unlock Allow restricted settings in App Info > ⋮ (and ensure Advanced Protection isn't blocking third-party accessibility tools on Android 17).\n" +
            "2. Enable Spotui Floating Mini-Player under Installed apps, turn on the shortcut, and set Accessibility button > Location to Floating (or disable Block navigation buttons in One UI 9.0 Game Booster) so the trigger stays accessible inside full-screen landscape games."

    fun isServiceEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        val enabledServices = runCatching {
            am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        }.getOrNull() ?: return false

        val targetPackage = context.packageName
        val targetClass = MediaAccessibilityService::class.java.name
        return enabledServices.any { info ->
            val si = info.resolveInfo?.serviceInfo ?: return@any false
            si.packageName == targetPackage && si.name == targetClass
        }
    }

    fun openAccessibilitySettings(context: Context): Boolean = runCatching {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    fun openAppInfoSettings(context: Context): Boolean = runCatching {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
