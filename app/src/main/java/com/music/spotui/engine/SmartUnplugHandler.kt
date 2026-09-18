package com.music.spotui.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.music.spotui.di.SongPlayer
import com.music.spotui.util.AppDiagnostics

/**
 * Version 1.5.1 - SmartUnplugHandler
 * Listens for audio becoming noisy (headphone unplugs or Bluetooth disconnects)
 * to safely pause audio without popping or lagging.
 */
class SmartUnplugHandler(private val context: Context) : BroadcastReceiver() {

    fun register() {
        runCatching {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.registerReceiver(
                    context,
                    this,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(this, filter)
            }
        }.onFailure {
            AppDiagnostics.warning("SmartUnplugHandler", "Failed to register unplug receiver safely", it)
        }
    }

    fun unregister() {
        try {
            context.unregisterReceiver(this)
        } catch (e: Exception) {
            // Already unregistered
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                // Pause player safely
                SongPlayer.pause()
            }
        }
    }
}
