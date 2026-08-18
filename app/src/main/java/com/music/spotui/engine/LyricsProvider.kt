package com.music.spotui.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * Version 1.5.1 - LyricsProvider
 * Open-source LRCLIB client fetching time-synced lyrics using track title, artist name, and duration matching.
 */
object LyricsProvider {
    suspend fun fetchLyrics(title: String, artist: String, durationMs: Long): String? = withContext(Dispatchers.IO) {
        try {
            val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
            val encodedArtist = java.net.URLEncoder.encode(artist, "UTF-8")
            val durationSec = durationMs / 1000
            val url = URL("https://lrclib.net/api/get?track_name=\$encodedTitle&artist_name=\$encodedArtist&duration=\$durationSec")
            
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "SpotUI/1.5.1")
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                val json = org.json.JSONObject(response)
                return@withContext json.optString("syncedLyrics", json.optString("plainLyrics", null))
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
