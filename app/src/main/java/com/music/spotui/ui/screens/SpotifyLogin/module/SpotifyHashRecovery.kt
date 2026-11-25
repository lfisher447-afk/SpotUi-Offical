package com.music.spotui.ui.screens.SpotifyLogin.module

import android.content.Context
import com.metrolist.spotify.Spotify
import com.metrolist.spotify.SpotifyHashDiscovery
import com.metrolist.spotify.SpotifyHashProvider
import com.metrolist.spotify.SpotifyHashRefresher
import com.music.spotui.util.DevConsoleManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

private const val HASH_STORE_FILE = "spotify_learned_hashes.json"
private const val HASH_FETCH_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36"

private val hashRecoveryInstalled = AtomicBoolean(false)

/**
 * Self-heals Spotify's GraphQL persisted-query hashes. When every known hash for an operation is
 * rejected, the live hash is read from the open.spotify.com web-player bundles, applied immediately
 * and remembered on disk (and re-applied on the next launch). Safe to call repeatedly.
 */
internal fun installSpotifyHashRecovery(context: Context) {
    if (!hashRecoveryInstalled.compareAndSet(false, true)) return
    val app = context.applicationContext
    val refresher = SpotifyHashRefresher(
        discovery = SpotifyHashDiscovery(fetch = ::fetchText),
        store = File(app.filesDir, HASH_STORE_FILE),
    )

    launchLoginTask { runCatching { refresher.applyLearnedOnce() } }

    Spotify.onHashExpired = { operation ->
        launchLoginTask {
            val rejected = SpotifyHashProvider.getHash(operation)
            val live = runCatching { refresher.liveHash(operation, setOf(rejected)) }.getOrNull()
            if (live == null) {
                DevConsoleManager.logWarning("SpotifyHash", "No newer hash found for $operation")
            } else {
                refresher.learn(operation, live)
                SpotifyHashProvider.updateHashes(
                    mapOf(operation to SpotifyHashProvider.RemoteHashEntry(hash = live, previousHash = rejected)),
                    SpotifyHashProvider.HashSource.REMOTE,
                )
                DevConsoleManager.logAuth("Recovered GQL hash for $operation from the web player")
            }
        }
    }
}

private suspend fun fetchText(url: String): String = withContext(Dispatchers.IO) {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", HASH_FETCH_UA)
        connection.setRequestProperty("Accept-Language", "en")
        if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode} for $url")
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}
