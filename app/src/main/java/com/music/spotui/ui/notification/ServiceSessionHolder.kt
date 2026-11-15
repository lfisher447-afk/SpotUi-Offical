package com.music.spotui.ui.notification

import androidx.annotation.OptIn
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaLibraryService
import java.lang.ref.WeakReference

/**
 * Central holder for the active [PlaybackService], [MediaLibraryService.MediaLibrarySession],
 * and latest [MediaMetadata] across service/player transitions.
 */
@OptIn(UnstableApi::class)
object ServiceSessionHolder {

    @Volatile
    private var serviceRef: WeakReference<PlaybackService>? = null

    @Volatile
    var session: MediaLibraryService.MediaLibrarySession? = null
        private set

    @Volatile
    var currentMetadata: MediaMetadata? = null
        private set

    fun attach(
        service: PlaybackService,
        mediaLibrarySession: MediaLibraryService.MediaLibrarySession,
    ) {
        serviceRef = WeakReference(service)
        session = mediaLibrarySession
    }

    fun updateMetadata(metadata: MediaMetadata?) {
        currentMetadata = metadata
    }

    fun activeService(): PlaybackService? = serviceRef?.get()

    fun detach(service: PlaybackService) {
        if (serviceRef?.get() === service || serviceRef?.get() == null) {
            serviceRef = null
            session = null
            currentMetadata = null
        }
    }

    /**
     * Returns true when [player] holds a resumable item (non-empty timeline and not IDLE/ENDED),
     * matching Samsung One UI 9 / Media3 requirements before exposing active resume controls.
     */
    fun isResumable(player: Player?): Boolean {
        if (player == null) return false
        return player.mediaItemCount > 0 &&
            player.currentMediaItem != null &&
            player.playbackState != Player.STATE_IDLE &&
            player.playbackState != Player.STATE_ENDED
    }
}
