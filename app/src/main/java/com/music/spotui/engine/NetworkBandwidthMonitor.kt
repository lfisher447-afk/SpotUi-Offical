package com.music.spotui.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Version 1.5.1 - NetworkBandwidthMonitor
 * Monitors signal strength and auto-adjusts audio caching buffer sizes to prevent stuttering.
 */
class NetworkBandwidthMonitor(private val context: Context) {
    fun isHighBandwidthAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || 
               caps.linkDownstreamBandwidthKbps > 5000 // > 5 Mbps
    }
}
