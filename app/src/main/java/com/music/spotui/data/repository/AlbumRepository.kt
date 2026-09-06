// File: app/src/main/java/com/music/spotui/data/repository/AlbumRepository.kt
package com.music.spotui.data.repository

import com.music.spotui.data.db.AlbumDao
import com.music.spotui.data.models.AlbumModel
import com.music.spotui.util.Result
import com.music.spotui.util.runSuspendCatching
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Source of truth for locally cached album metadata. */
interface AlbumRepository {
    /** Streams cached albums. */
    fun observeAlbums(): Flow<List<AlbumModel>>

    /** Streams one album by its stable [albumId]. */
    fun observeAlbum(albumId: String): Flow<AlbumModel?>

    /** Persists [album]. */
    suspend fun save(album: AlbumModel): Result<Unit>

    /** Persists [albums]. */
    suspend fun saveAll(albums: List<AlbumModel>): Result<Unit>
}

/** Room-backed implementation of [AlbumRepository]. */
@Singleton
class AlbumRepositoryImpl @Inject constructor(
    private val albumDao: AlbumDao,
) : AlbumRepository {
    override fun observeAlbums(): Flow<List<AlbumModel>> = albumDao.observeAll()

    override fun observeAlbum(albumId: String): Flow<AlbumModel?> = albumDao.observeById(albumId)

    override suspend fun save(album: AlbumModel): Result<Unit> =
        runSuspendCatching("Unable to save album") { albumDao.upsert(album) }

    override suspend fun saveAll(albums: List<AlbumModel>): Result<Unit> =
        runSuspendCatching("Unable to save albums") { albumDao.upsertAll(albums) }
}
