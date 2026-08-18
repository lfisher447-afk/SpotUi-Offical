package com.music.spotui.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import com.music.spotui.di.SongPlayer

/**
 * Version 1.5.1 - SmartUnplugHandler
 * Listens for Bluetooth disconnects or headphone unplugs to safely pause audio without popping or lagging.
 */
class SmartUnplugHandler(private val context: Context) : BroadcastReceiver() {
    
    fun register() {
        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        context.registerReceiver(this, filter)
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
            AudioManager.ACTION_AUDIO_BECOMING_NOISY,
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                // Pause player safely
                SongPlayer.pause()
            }
        }
    }
}
