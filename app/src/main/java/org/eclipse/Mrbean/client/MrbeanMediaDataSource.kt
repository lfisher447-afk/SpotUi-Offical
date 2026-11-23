package org.eclipse.Mrbean.client

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.metrolist.innertube.network.AuthSessionStore
import com.metrolist.innertube.network.DnsOverHttpsPreset

/**
 * Concrete Media3 transport owner for MrBean settings.
 *
 * Reuses the cached, HTTP/2-multiplexed OkHttpClient and connection pool from
 * [MrbeanDownloadClient] so playback and cache preloads never allocate fresh sockets
 * or TLS handshakes per track.
 */
object MrbeanMediaDataSource {
    private const val USER_AGENT = "Spotui-MrBean/2.0 (Android Media3)"

    fun upstreamFactory(context: Context): DataSource.Factory {
        val snapshot = MrbeanNetworkSettings.snapshot(context)
        val hasCustomNetwork =
            AuthSessionStore.dnsPreset != DnsOverHttpsPreset.SYSTEM ||
                AuthSessionStore.okHttpProxy() != null
        if (!snapshot.enabled && !hasCustomNetwork) {
            return DefaultHttpDataSource.Factory()
                .setUserAgent(USER_AGENT)
                .setAllowCrossProtocolRedirects(true)
        }
        val client = MrbeanDownloadClient.playbackOkHttpClient(context)
        return OkHttpDataSource.Factory(client)
            .setUserAgent(USER_AGENT)
            .setDefaultRequestProperties(
                mapOf(
                    "Accept" to "*/*",
                    "Accept-Encoding" to "identity",
                    "Cache-Control" to "no-transform",
                ),
            )
    }
}
