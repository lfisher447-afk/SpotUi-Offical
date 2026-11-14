package com.music.spotui.engine

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.music.spotui.data.preferences.isAutoResumeOnBluetooth
import com.music.spotui.data.preferences.isPauseOnUnplugEnabled
import com.music.spotui.di.SongPlayer
import com.music.spotui.util.AppDiagnostics

/**
 * Listens for audio becoming noisy (headphone unplugs or Bluetooth disconnects)
 * and Bluetooth A2DP reconnects to safely pause or resume playback per user prefs.
 */
class SmartUnplugHandler(private val context: Context) : BroadcastReceiver() {

    @Volatile private var pausedByUnplug = false

    fun register() {
        runCatching {
            val filter = IntentFilter().apply {
                addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.registerReceiver(
                    context,
                    this,
                    filter,
                    ContextCompat.RECEIVER_EXPORTED,
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
        } catch (_: Exception) {
            // Already unregistered
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val ctx = context ?: this.context
        when (intent?.action) {
            AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                if (isPauseOnUnplugEnabled(ctx) && SongPlayer.isPlaying()) {
                    pausedByUnplug = true
                    SongPlayer.pause()
                }
            }
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
                if (state == BluetoothProfile.STATE_CONNECTED && pausedByUnplug && isAutoResumeOnBluetooth(ctx)) {
                    pausedByUnplug = false
                    SongPlayer.play()
                }
            }
        }
    }
}
