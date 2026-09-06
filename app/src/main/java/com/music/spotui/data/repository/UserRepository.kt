// File: app/src/main/java/com/music/spotui/data/repository/UserRepository.kt
package com.music.spotui.data.repository

import com.music.spotui.data.db.UserDao
import com.music.spotui.data.models.UserModel
import com.music.spotui.util.Result
import com.music.spotui.util.runSuspendCatching
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Source of truth for the locally cached signed-in profile. */
interface UserRepository {
    /** Streams the profile identified by [userId]. */
    fun observeUser(userId: String): Flow<UserModel?>

    /** Persists [user]. */
    suspend fun save(user: UserModel): Result<Unit>

    /** Removes [userId] from local storage. */
    suspend fun remove(userId: String): Result<Unit>
}

/** Room-backed implementation of [UserRepository]. */
@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userDao: UserDao,
) : UserRepository {
    override fun observeUser(userId: String): Flow<UserModel?> = userDao.observeById(userId)

    override suspend fun save(user: UserModel): Result<Unit> =
        runSuspendCatching("Unable to save user profile") { userDao.upsert(user) }

    override suspend fun remove(userId: String): Result<Unit> =
        runSuspendCatching("Unable to remove user profile") { userDao.deleteById(userId) }
}
