package org.eclipse.Mrbean.client

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Concrete Media3 transport owner for MrBean settings.
 *
 * When enabled, playback and cache preloads use this OkHttp-backed DataSource. It
 * applies the user-selected connection/read limits and stable identity headers at
 * the Media3 boundary. It intentionally relies on OkHttp and Android TLS for
 * protocol negotiation; the app does not claim HTTP/3 unless a real QUIC stack is
 * installed.
 */
object MrbeanMediaDataSource {
    private const val USER_AGENT = "Spotui-MrBean/2.0 (Android Media3)"

    fun upstreamFactory(context: Context): DataSource.Factory {
        val snapshot = MrbeanNetworkSettings.snapshot(context)
        if (!snapshot.enabled) {
            return DefaultHttpDataSource.Factory()
                .setUserAgent(USER_AGENT)
                .setAllowCrossProtocolRedirects(true)
        }
        val stableHeaders = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", USER_AGENT)
                .header("Accept", "*/*")
                .header("Accept-Encoding", "identity")
                .header("Cache-Control", "no-transform")
                .build()
            chain.proceed(request)
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(snapshot.connectTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(snapshot.readTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .writeTimeout(snapshot.connectTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .addNetworkInterceptor(stableHeaders)
            .build()
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
