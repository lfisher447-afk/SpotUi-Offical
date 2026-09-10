// File: app/src/main/java/com/music/spotui/data/db/SpotUIDatabase.kt
package com.music.spotui.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.music.spotui.data.dao.TrashBinDao
import com.music.spotui.data.entity.TrashBinEntity
import com.music.spotui.data.models.AlbumModel
import com.music.spotui.data.models.ArtistModel
import com.music.spotui.data.models.DownloadModel
import com.music.spotui.data.models.LyricsModel
import com.music.spotui.data.models.PlaylistModel
import com.music.spotui.data.models.SearchHistoryModel
import com.music.spotui.data.models.TrackModel
import com.music.spotui.data.models.UserModel
import com.music.spotui.util.Constants

/** The single Room database that owns SpotUI's new persisted application state. */
@Database(
    entities = [
        AlbumModel::class,
        ArtistModel::class,
        DownloadModel::class,
        LyricsModel::class,
        PlaylistModel::class,
        SearchHistoryModel::class,
        TrackModel::class,
        TrashBinEntity::class,
        UserModel::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(RoomConverters::class)
abstract class SpotUIDatabase : RoomDatabase() {
    /** Returns album metadata access. */
    abstract fun albumDao(): AlbumDao

    /** Returns artist profile access. */
    abstract fun artistDao(): ArtistDao

    /** Returns download-state access. */
    abstract fun downloadDao(): DownloadDao

    /** Returns lyrics-cache access. */
    abstract fun lyricsDao(): LyricsDao

    /** Returns playlist access. */
    abstract fun playlistDao(): PlaylistDao

    /** Returns search-history access. */
    abstract fun searchHistoryDao(): SearchHistoryDao

    /** Returns track-catalog access. */
    abstract fun trackDao(): TrackDao

    /** Returns blacklist access retained for compatibility with the existing player. */
    abstract fun trashBinDao(): TrashBinDao

    /** Returns user-profile access. */
    abstract fun userDao(): UserDao

    companion object {
        @Volatile
        private var instance: SpotUIDatabase? = null

        fun getInstance(context: Context): SpotUIDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SpotUIDatabase::class.java,
                    Constants.DATABASE_NAME
                )
                .fallbackToDestructiveMigration(true)
                .fallbackToDestructiveMigrationOnDowngrade(true)
                .build().also { instance = it }
            }
        }
    }
}
