// File: app/src/main/java/com/music/spotui/data/models/DownloadModel.kt
package com.music.spotui.data.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Lifecycle state of an app-owned, offline track file. */
@Serializable
enum class DownloadState {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

/** Persisted progress and recoverability information for a download. */
@Serializable
@Entity(tableName = "downloads", indices = [Index(value = ["state"]), Index(value = ["updatedAtEpochMs"])])
data class DownloadModel(
    @PrimaryKey val trackId: String,
    val state: DownloadState = DownloadState.QUEUED,
    val progress: Float = 0F,
    val localUri: String? = null,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val errorMessage: String? = null,
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
)
