package com.music.spotui.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class AudioFeatures(val bpm: Int, val key: String, val valence: Float, val energy: Float, val loudness: Float)

/**
 * Version 2.5.2 - AudioFeaturesClient
 * Pooled HTTP/2 API fetcher parsing track acoustic attributes from Spotify Audio Features endpoint.
 */
object AudioFeaturesClient {
    private val keyMap = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    private val client = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(6, 3, TimeUnit.MINUTES))
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun getFeatures(spotifyTrackId: String, accessToken: String): AudioFeatures? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://api.spotify.com/v1/audio-features/$spotifyTrackId")
                .header("Authorization", "Bearer $accessToken")
                .header("Accept", "application/json")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val response = resp.body?.string().orEmpty()
                if (response.isBlank()) return@withContext null
                val json = org.json.JSONObject(response)

                val tempo = json.optDouble("tempo", 120.0).toInt()
                val keyInt = json.optInt("key", -1)
                val keyStr = if (keyInt in keyMap.indices) keyMap[keyInt] else "Unknown"
                val mode = json.optInt("mode", 1)
                val keyFinal = if (mode == 1) "$keyStr Major" else "$keyStr Minor"

                AudioFeatures(
                    bpm = tempo,
                    key = keyFinal,
                    valence = json.optDouble("valence", 0.5).toFloat(),
                    energy = json.optDouble("energy", 0.5).toFloat(),
                    loudness = json.optDouble("loudness", -5.0).toFloat()
                )
            }
        } catch (_: Exception) {
            null
        }
    }
}
