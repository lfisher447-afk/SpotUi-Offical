// File: app/src/main/java/com/music/spotui/data/models/UserModel.kt
package com.music.spotui.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** The locally cached profile for the signed-in user. */
@Serializable
@Entity(tableName = "users")
data class UserModel(
    @PrimaryKey val id: String,
    val displayName: String,
    val email: String? = null,
    val avatarUrl: String? = null,
    val countryCode: String? = null,
    val product: String? = null,
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
)
