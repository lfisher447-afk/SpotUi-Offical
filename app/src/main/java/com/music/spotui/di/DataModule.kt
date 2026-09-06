// File: app/src/main/java/com/music/spotui/di/DataModule.kt
package com.music.spotui.di

import com.music.spotui.data.db.AlbumDao
import com.music.spotui.data.db.ArtistDao
import com.music.spotui.data.db.DownloadDao
import com.music.spotui.data.db.LyricsDao
import com.music.spotui.data.db.PlaylistDao
import com.music.spotui.data.db.SearchHistoryDao
import com.music.spotui.data.db.SpotUIDatabase
import com.music.spotui.data.db.TrackDao
import com.music.spotui.data.db.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Supplies singleton Room data-access objects to repository implementations. */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    /** Provides album access. */
    @Provides @Singleton fun provideAlbumDao(database: SpotUIDatabase): AlbumDao = database.albumDao()

    /** Provides artist access. */
    @Provides @Singleton fun provideArtistDao(database: SpotUIDatabase): ArtistDao = database.artistDao()

    /** Provides download-state access. */
    @Provides @Singleton fun provideDownloadDao(database: SpotUIDatabase): DownloadDao = database.downloadDao()

    /** Provides lyric-cache access. */
    @Provides @Singleton fun provideLyricsDao(database: SpotUIDatabase): LyricsDao = database.lyricsDao()

    /** Provides playlist access. */
    @Provides @Singleton fun providePlaylistDao(database: SpotUIDatabase): PlaylistDao = database.playlistDao()

    /** Provides search-history access. */
    @Provides @Singleton fun provideSearchHistoryDao(database: SpotUIDatabase): SearchHistoryDao = database.searchHistoryDao()

    /** Provides track-catalog access. */
    @Provides @Singleton fun provideTrackDao(database: SpotUIDatabase): TrackDao = database.trackDao()

    /** Provides user-profile access. */
    @Provides @Singleton fun provideUserDao(database: SpotUIDatabase): UserDao = database.userDao()
}
