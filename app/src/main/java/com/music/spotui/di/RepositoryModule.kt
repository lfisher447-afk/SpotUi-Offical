// File: app/src/main/java/com/music/spotui/di/RepositoryModule.kt
package com.music.spotui.di

import com.music.spotui.data.repository.AlbumRepository
import com.music.spotui.data.repository.AlbumRepositoryImpl
import com.music.spotui.data.repository.ArtistRepository
import com.music.spotui.data.repository.ArtistRepositoryImpl
import com.music.spotui.data.repository.DownloadRepository
import com.music.spotui.data.repository.DownloadRepositoryImpl
import com.music.spotui.data.repository.LyricsRepository
import com.music.spotui.data.repository.LyricsRepositoryImpl
import com.music.spotui.data.repository.OfflineRepository
import com.music.spotui.data.repository.OfflineRepositoryImpl
import com.music.spotui.data.repository.PlaylistRepository
import com.music.spotui.data.repository.PlaylistRepositoryImpl
import com.music.spotui.data.repository.SearchRepository
import com.music.spotui.data.repository.SearchRepositoryImpl
import com.music.spotui.data.repository.TrackRepository
import com.music.spotui.data.repository.TrackRepositoryImpl
import com.music.spotui.data.repository.UserRepository
import com.music.spotui.data.repository.UserRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Binds clean repository interfaces to their singleton Room-backed implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    /** Binds album persistence. */
    @Binds abstract fun bindAlbumRepository(implementation: AlbumRepositoryImpl): AlbumRepository

    /** Binds artist persistence. */
    @Binds abstract fun bindArtistRepository(implementation: ArtistRepositoryImpl): ArtistRepository

    /** Binds download persistence. */
    @Binds abstract fun bindDownloadRepository(implementation: DownloadRepositoryImpl): DownloadRepository

    /** Binds lyric persistence. */
    @Binds abstract fun bindLyricsRepository(implementation: LyricsRepositoryImpl): LyricsRepository

    /** Binds offline-library projection. */
    @Binds abstract fun bindOfflineRepository(implementation: OfflineRepositoryImpl): OfflineRepository

    /** Binds playlist persistence. */
    @Binds abstract fun bindPlaylistRepository(implementation: PlaylistRepositoryImpl): PlaylistRepository

    /** Binds search persistence and provider fallback. */
    @Binds abstract fun bindSearchRepository(implementation: SearchRepositoryImpl): SearchRepository

    /** Binds track persistence. */
    @Binds abstract fun bindTrackRepository(implementation: TrackRepositoryImpl): TrackRepository

    /** Binds user persistence. */
    @Binds abstract fun bindUserRepository(implementation: UserRepositoryImpl): UserRepository
}
