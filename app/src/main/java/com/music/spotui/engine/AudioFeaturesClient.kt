package com.music.spotui.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AudioFeatures(val bpm: Int, val key: String, val valence: Float, val energy: Float, val loudness: Float)

/**
 * Version 1.5.1 - AudioFeaturesClient
 * Direct API fetcher parsing track acoustic attributes from Spotify Audio Features endpoint.
 */
object AudioFeaturesClient {
    private val keyMap = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    suspend fun getFeatures(spotifyTrackId: String, accessToken: String): AudioFeatures? = withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL("https://api.spotify.com/v1/audio-features/\$spotifyTrackId")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer \$accessToken")
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                val json = org.json.JSONObject(response)
                
                val tempo = json.optDouble("tempo", 120.0).toInt()
                val keyInt = json.optInt("key", -1)
                val keyStr = if (keyInt in keyMap.indices) keyMap[keyInt] else "Unknown"
                val mode = json.optInt("mode", 1)
                val keyFinal = if (mode == 1) "\$keyStr Major" else "\$keyStr Minor"
                
                return@withContext AudioFeatures(
                    bpm = tempo,
                    key = keyFinal,
                    valence = json.optDouble("valence", 0.5).toFloat(),
                    energy = json.optDouble("energy", 0.5).toFloat(),
                    loudness = json.optDouble("loudness", -5.0).toFloat()
                )
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
