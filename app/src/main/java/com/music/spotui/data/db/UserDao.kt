// File: app/src/main/java/com/music/spotui/data/db/UserDao.kt
package com.music.spotui.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.music.spotui.data.models.UserModel
import kotlinx.coroutines.flow.Flow

/** Room access for cached user profiles. */
@Dao
interface UserDao {
    /** Streams the profile identified by [userId]. */
    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    fun observeById(userId: String): Flow<UserModel?>

    /** Returns the profile identified by [userId]. */
    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getById(userId: String): UserModel?

    /** Inserts or replaces [user]. */
    @Upsert
    suspend fun upsert(user: UserModel)

    /** Removes the profile identified by [userId]. */
    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteById(userId: String)
}
