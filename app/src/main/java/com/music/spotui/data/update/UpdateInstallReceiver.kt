package com.music.spotui.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat

/**
 * Receives commit status callbacks from Android's [PackageInstaller] session API
 * when installing downloaded SpotUI APK updates in-place, forwarding full status,
 * error details, and any conflicting package names into [UpdateDownloader] & [AutoUpdaterLogger].
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        val otherPackage = intent.getStringExtra(PackageInstaller.EXTRA_OTHER_PACKAGE_NAME).orEmpty()
        val version = intent.getStringExtra(EXTRA_UPDATE_VERSION).orEmpty()
        val fileName = intent.getStringExtra(EXTRA_UPDATE_FILENAME).orEmpty()
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent = IntentCompat.getParcelableExtra(
                    intent,
                    Intent.EXTRA_INTENT,
                    Intent::class.java,
                )
                UpdateDownloader.onPackageInstallerPendingUserAction(
                    context = context,
                    sessionId = sessionId,
                    version = version,
                    fileName = fileName,
                    confirmIntent = confirmIntent,
                )
            }

            PackageInstaller.STATUS_SUCCESS -> {
                UpdateDownloader.onPackageInstallerSuccess(
                    context = context,
                    sessionId = sessionId,
                    version = version,
                    fileName = fileName,
                )
            }

            else -> {
                UpdateDownloader.onPackageInstallerFailure(
                    context = context,
                    sessionId = sessionId,
                    version = version,
                    fileName = fileName,
                    status = status,
                    rawMessage = message,
                    otherPackageName = otherPackage,
                )
            }
        }
    }

    companion object {
        const val EXTRA_UPDATE_VERSION = "spotui_update_version"
        const val EXTRA_UPDATE_FILENAME = "spotui_update_filename"
    }
}
