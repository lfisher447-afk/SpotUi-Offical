package com.music.spotui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.metrolist.music.utils.cipher.CipherDeobfuscator
import com.music.spotui.data.api.Api
import com.music.spotui.data.api.SpotifySession
import com.music.spotui.data.db.AppDatabase
import com.music.spotui.data.db.SpotUIDatabase
import com.music.spotui.engine.TrashBinManager
import com.music.spotui.ui.notification.PlaybackService
import com.music.spotui.util.DefaultLinkHelper
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = MyApplication::class)
class ServiceAndApiTest {

    @Test
    fun playbackService_createsWithoutCrashing() {
        val controller = Robolectric.buildService(PlaybackService::class.java)
        val service = controller.create().get()
        assertNotNull(service)
        assertTrue(service.sessions.isNotEmpty())
        val nm = service.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            assertNotNull(nm.getNotificationChannel(PlaybackService.CHANNEL_ID))
        }
        controller.destroy()
    }

    @Test
    fun playbackService_handlesEnsureForegroundAndRefreshActionsWithoutCrashing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val ensureIntent = android.content.Intent(context, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_ENSURE_FOREGROUND
        }
        val controller = Robolectric.buildService(PlaybackService::class.java, ensureIntent)
        val service = controller.create().startCommand(0, 1).get()
        assertNotNull(service)
        assertTrue(service.sessions.isNotEmpty())

        val refreshIntent = android.content.Intent(context, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_REFRESH_NOTIFICATION
        }
        service.onStartCommand(refreshIntent, 0, 2)
        controller.destroy()
    }

    @Test
    fun playbackService_sessionPlayerProvidesPlaceholderTimelineAndCommandsDuringResolution() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val controller = Robolectric.buildService(PlaybackService::class.java)
        val service = controller.create().get()
        val session = service.sessions.first()
        val sessionPlayer = session.player

        // Verify required transport and metadata commands are always exposed for One UI / System UI.
        val cmds = sessionPlayer.availableCommands
        assertTrue(cmds.contains(androidx.media3.common.Player.COMMAND_PLAY_PAUSE))
        assertTrue(cmds.contains(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT))
        assertTrue(cmds.contains(androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS))
        assertTrue(cmds.contains(androidx.media3.common.Player.COMMAND_GET_CURRENT_MEDIA_ITEM))
        assertTrue(cmds.contains(androidx.media3.common.Player.COMMAND_GET_TIMELINE))

        val seenEvents = mutableSetOf<Int>()
        var isPlayingCallbacks = 0
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isPlayingCallbacks++
            }

            override fun onEvents(
                player: androidx.media3.common.Player,
                events: androidx.media3.common.Player.Events,
            ) {
                for (i in 0 until events.size()) {
                    seenEvents.add(events.get(i))
                }
            }
        }
        sessionPlayer.addListener(listener)

        // Simulate track resolution before ExoPlayer has a stream URL loaded.
        com.music.spotui.di.SongPlayer.setNowPlayingMeta(
            title = "Blinding Lights",
            artist = "The Weeknd",
            coverUri = "",
            album = "After Hours",
            durationMs = 200_000L,
        )
        com.music.spotui.di.SongPlayer.setResolvingStateForTest(true)
        PlaybackService.notifyPlaybackStateChanged(context)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertFalse(sessionPlayer.currentTimeline.isEmpty)
        assertEquals(1, sessionPlayer.mediaItemCount)
        assertNotNull(sessionPlayer.currentMediaItem)
        assertEquals("Blinding Lights", sessionPlayer.mediaMetadata.title?.toString())
        assertEquals(androidx.media3.common.Player.STATE_BUFFERING, sessionPlayer.playbackState)
        assertTrue(sessionPlayer.playWhenReady)
        assertFalse(sessionPlayer.isPlaying)
        assertTrue(seenEvents.contains(androidx.media3.common.Player.EVENT_TIMELINE_CHANGED))
        assertTrue(seenEvents.contains(androidx.media3.common.Player.EVENT_MEDIA_ITEM_TRANSITION))
        assertTrue(seenEvents.contains(androidx.media3.common.Player.EVENT_MEDIA_METADATA_CHANGED))
        assertTrue(seenEvents.contains(androidx.media3.common.Player.EVENT_PLAYBACK_STATE_CHANGED))
        assertTrue(seenEvents.contains(androidx.media3.common.Player.EVENT_PLAY_WHEN_READY_CHANGED))

        // Clearing resolution returns idle state safely when no stream is loaded and queue is empty.
        com.music.spotui.di.SongPlayer.setResolvingStateForTest(false)
        PlaybackService.notifyPlaybackStateChanged(context)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertEquals(androidx.media3.common.Player.STATE_IDLE, sessionPlayer.playbackState)
        assertFalse(sessionPlayer.isPlaying)
        assertTrue(isPlayingCallbacks >= 0)

        // Verify onBind delegates MediaSessionService first and handles local bind action.
        val mediaSessionBindIntent = android.content.Intent(
            androidx.media3.session.MediaSessionService.SERVICE_INTERFACE
        )
        assertNotNull(service.onBind(mediaSessionBindIntent))
        val localBindIntent = android.content.Intent(PlaybackService.ACTION_LOCAL_BIND)
        assertTrue(service.onBind(localBindIntent) is PlaybackService.LocalBinder)

        sessionPlayer.removeListener(listener)
        controller.destroy()
    }

    @Test
    fun webMediaPlayer_refreshOffMainThread_postsSafelyToMainLooper() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val songState = com.music.spotui.di.CurrentSongState(context)
        val webPlayer = com.music.spotui.ui.notification.WebMediaPlayer(
            android.os.Looper.getMainLooper(),
            songState,
        ) {}
        var thrown: Throwable? = null
        val t = Thread {
            try {
                webPlayer.refresh()
            } catch (e: Throwable) {
                thrown = e
            }
        }
        t.start()
        t.join(2000L)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals(null, thrown)
        webPlayer.release()
    }

    @Test
    fun api_unauthenticatedCalls_emitGracefulErrorsWithoutThrowing() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val api = Api(context)
        val home = api.getHomeFeed().toList()
        val albums = api.getAlbums().toList()
        val artists = api.getArtists().toList()
        val songs = api.getSongs().toList()
        assertTrue(home.isNotEmpty())
        assertTrue(albums.isNotEmpty())
        assertTrue(artists.isNotEmpty())
        assertTrue(songs.isNotEmpty())
    }

    @Test
    fun startupManagers_andDatabases_initializeWithoutCrashing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        CipherDeobfuscator.initialize(context)
        assertTrue(CipherDeobfuscator.isInitialized)

        TrashBinManager.initialize(context)
        assertFalse(TrashBinManager.shouldSkip("nonexistent_track", "nonexistent_artist"))

        assertNotNull(AppDatabase.getInstance(context))
        assertNotNull(SpotUIDatabase.getInstance(context))

        assertFalse(DefaultLinkHelper.isAppDefaultLinkHandler(context))
        assertFalse(DefaultLinkHelper.shouldShowPrompt(context))
    }

    @Test
    fun spotifySession_setAndClear_worksSafely() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SpotifySession.setSpDc(context, "test_cookie_value")
        assertEquals("test_cookie_value", SpotifySession.spDc(context))
        SpotifySession.setSpDc(context, "")
        assertTrue(SpotifySession.spDc(context).isBlank())
    }

    @Test
    fun updateChecker_usesOfficialGithubRepoAndMigratesLegacyDefault() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(
            "https://github.com/lfisher447-afk/SpotUi-Offical",
            com.music.spotui.data.preferences.DEFAULT_UPDATE_REPO_URL
        )
        assertEquals(
            com.music.spotui.data.preferences.DEFAULT_UPDATE_REPO_URL,
            com.music.spotui.data.preferences.getUpdateRepoUrl(context)
        )
        com.music.spotui.data.preferences.setUpdateRepoUrl(context, "https://github.com/H4zh4n/Spotui")
        assertEquals(
            com.music.spotui.data.preferences.DEFAULT_UPDATE_REPO_URL,
            com.music.spotui.data.preferences.getUpdateRepoUrl(context)
        )
        assertEquals(
            "https://api.github.com/repos/lfisher447-afk/SpotUi-Offical/releases/latest",
            com.music.spotui.data.update.UpdateChecker.buildLatestReleaseApiUrl(
                "https://github.com/lfisher447-afk/SpotUi-Offical/"
            )
        )
    }
}
