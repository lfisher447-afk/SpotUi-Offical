package com.music.spotui.translator

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.music.spotui.ui.notification.PlaybackService
import com.music.spotui.util.DevConsoleManager
import com.music.spotui.util.DevConsoleManager.LogLevel

/**
 * Transparent permission trampoline used from the Accessibility Floater or Settings screen
 * to request Android's [MediaProjection] consent + `RECORD_AUDIO` permission and immediately
 * promote [PlaybackService] to `mediaPlayback|mediaProjection` foreground type before starting capture.
 */
class MediaProjectionPermissionActivity : ComponentActivity() {

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            PlaybackService.promoteForMediaProjection(applicationContext) {
                GameVoiceTranslatorEngine.startWithMediaProjection(
                    context = applicationContext,
                    resultCode = result.resultCode,
                    resultData = data,
                )
            }
        } else {
            DevConsoleManager.log(LogLevel.INFO, TAG, "User cancelled internal audio projection prompt")
        }
        finish()
    }

    private val audioPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            launchMediaProjectionPrompt()
        } else {
            DevConsoleManager.log(LogLevel.WARNING, TAG, "RECORD_AUDIO permission denied for AudioPlaybackCapture")
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            finish()
            return
        }

        val hasAudioPerm = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        if (hasAudioPerm) {
            launchMediaProjectionPrompt()
        } else {
            audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun launchMediaProjectionPrompt() {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (manager == null) {
            finish()
            return
        }
        PlaybackService.promoteForMediaProjection(applicationContext) {}
        runCatching {
            projectionLauncher.launch(manager.createScreenCaptureIntent())
        }.onFailure { err ->
            DevConsoleManager.log(LogLevel.ERROR, TAG, "Failed to open screen capture consent: ${err.message}", err)
            finish()
        }
    }

    companion object {
        private const val TAG = "MediaProjPermActivity"

        fun launch(context: Context) {
            val intent = Intent(context.applicationContext, MediaProjectionPermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            runCatching { context.applicationContext.startActivity(intent) }
        }
    }
}
