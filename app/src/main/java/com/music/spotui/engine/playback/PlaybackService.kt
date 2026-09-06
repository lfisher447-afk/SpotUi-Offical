// File: app/src/main/java/com/music/spotui/engine/playback/PlaybackService.kt
package com.music.spotui.engine.playback

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.music.spotui.util.Logger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** MediaSession service for the clean playback engine; register it when migrating the legacy service. */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    @Inject lateinit var songPlayer: SongPlayer

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSession.Builder(this, songPlayer.media3Player).build()
        Logger.info("PlaybackService", "Clean playback service created")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
