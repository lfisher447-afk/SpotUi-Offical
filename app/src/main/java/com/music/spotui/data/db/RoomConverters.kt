// File: app/src/main/java/com/music/spotui/data/db/RoomConverters.kt
package com.music.spotui.data.db

import androidx.room.TypeConverter
import com.music.spotui.data.models.DownloadState
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Converts the immutable model values Room cannot store natively. */
object RoomConverters {
    private val json = Json { ignoreUnknownKeys = true }

    /** Serializes a list of strings as JSON for a Room column. */
    @TypeConverter
    fun fromStringList(value: List<String>): String = json.encodeToString(value)

    /** Restores a JSON string list from a Room column. */
    @TypeConverter
    fun toStringList(value: String): List<String> = json.decodeFromString(value)

    /** Serializes a download state for a Room column. */
    @TypeConverter
    fun fromDownloadState(value: DownloadState): String = value.name

    /** Restores a download state, using a safe queued default for old records. */
    @TypeConverter
    fun toDownloadState(value: String): DownloadState =
        DownloadState.entries.firstOrNull { it.name == value } ?: DownloadState.QUEUED
}
