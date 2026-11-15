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
    fun playbackService_sessionBindsExoPlayerAndDelegatesOnBind() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val controller = Robolectric.buildService(PlaybackService::class.java)
        val service = controller.create().get()
        val session = service.sessions.first()
        assertNotNull(session)
        assertNotNull(session.player)
        assertEquals(service, com.music.spotui.ui.notification.ServiceSessionHolder.activeService())
        assertEquals(session, com.music.spotui.ui.notification.ServiceSessionHolder.session)

        // Verify onBind delegates MediaSessionService first and handles local bind action.
        val mediaSessionBindIntent = android.content.Intent(
            androidx.media3.session.MediaSessionService.SERVICE_INTERFACE
        )
        assertNotNull(service.onBind(mediaSessionBindIntent))
        val localBindIntent = android.content.Intent(PlaybackService.ACTION_LOCAL_BIND)
        assertTrue(service.onBind(localBindIntent) is PlaybackService.LocalBinder)

        controller.destroy()
        assertEquals(null, com.music.spotui.ui.notification.ServiceSessionHolder.activeService())
    }

    @Test
    fun playbackService_immediateForegroundPromotionAndDismissHandling() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val ensureIntent = android.content.Intent(context, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_ENSURE_FOREGROUND
        }
        val controller = Robolectric.buildService(PlaybackService::class.java, ensureIntent)
        val service = controller.create().startCommand(0, 1).get()

        assertTrue(service.hasPromotedForeground)
        val shadowService = org.robolectric.Shadows.shadowOf(service)
        assertEquals(PlaybackService.NOTIFICATION_ID, shadowService.lastForegroundNotificationId)
        assertNotNull(shadowService.lastForegroundNotification)

        val dismissIntent = android.content.Intent(context, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_MEDIA_NOTIFICATION_DISMISSED
        }
        service.onStartCommand(dismissIntent, 0, 2)
        assertFalse(service.hasPromotedForeground)
        controller.destroy()
    }

    @Test
    fun mediaNotificationProvider_attachesDismissPendingIntent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val provider = com.music.spotui.ui.notification.SpotuiMediaNotificationProvider(context)
        val raw = android.app.Notification.Builder(context, PlaybackService.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_spotui_notification)
            .setContentTitle("Test Track")
            .build()
        val mediaNotification = androidx.media3.session.MediaNotification(PlaybackService.NOTIFICATION_ID, raw)
        val decorated = provider.decorate(mediaNotification)
        assertNotNull(decorated.notification.deleteIntent)
        val shadowPending = org.robolectric.Shadows.shadowOf(decorated.notification.deleteIntent)
        assertEquals(
            PlaybackService.ACTION_MEDIA_NOTIFICATION_DISMISSED,
            shadowPending.savedIntent.action,
        )
        assertEquals(
            PlaybackService::class.java.name,
            shadowPending.savedIntent.component?.className,
        )
    }

    @Test
    fun songPlayer_queuesImmediateMediaItemWithMetadataOnFrameZero() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prevYt = com.music.spotui.di.SongPlayer.youtubeEnabled
        val prevLossless = com.music.spotui.di.SongPlayer.losslessStreaming
        val prevWeb = com.music.spotui.di.SongPlayer.webPlayerEnabled
        try {
            com.music.spotui.di.SongPlayer.youtubeEnabled = false
            com.music.spotui.di.SongPlayer.losslessStreaming = false
            com.music.spotui.di.SongPlayer.webPlayerEnabled = false

            val songState = com.music.spotui.di.CurrentSongState(context)
            com.music.spotui.di.SongPlayer.bindState(songState)
            com.music.spotui.di.SongPlayer.setNowPlayingMeta(
                title = "Test Song",
                artist = "Test Artist",
                coverUri = "",
                album = "Test Album",
                durationMs = 180_000L,
            )
            com.music.spotui.di.SongPlayer.playSong("spotify:track:test123|Test Song Test Artist", context, "song/42")
            org.robolectric.shadows.ShadowLooper.idleMainLooper()

            val p = com.music.spotui.di.SongPlayer.exoPlayer
            assertNotNull(p)
            assertTrue(p!!.mediaItemCount > 0)
            assertFalse(p.currentTimeline.isEmpty)
            assertEquals("Test Song", p.currentMediaItem?.mediaMetadata?.title?.toString())
            assertEquals("Test Artist", p.currentMediaItem?.mediaMetadata?.artist?.toString())
            assertTrue(p.playWhenReady)
            assertTrue(com.music.spotui.ui.notification.ServiceSessionHolder.isResumable(p))
        } finally {
            com.music.spotui.di.SongPlayer.release()
            com.music.spotui.di.SongPlayer.youtubeEnabled = prevYt
            com.music.spotui.di.SongPlayer.losslessStreaming = prevLossless
            com.music.spotui.di.SongPlayer.webPlayerEnabled = prevWeb
        }
    }

    @Test
    fun songPlayer_streamMimeType_isolatesTrackSourcesAcrossTransitions() {
        val ytUrl = "https://rr3---sn-ab5l6nzl.googlevideo.com/videoplayback?id=123"
        // Even if global/stale state still says Lossless FLAC from track N, YouTube track N+1 must NOT get AUDIO_FLAC.
        assertEquals(
            null,
            com.music.spotui.di.SongPlayer.streamMimeType(
                ytUrl,
                source = "Lossless • Tidal",
                quality = "FLAC 16-bit",
            ),
        )
        assertEquals(
            androidx.media3.common.MimeTypes.AUDIO_FLAC,
            com.music.spotui.di.SongPlayer.streamMimeType(
                "https://cdn.example.com/stream?token=abc",
                source = "Lossless • Qobuz",
                quality = "FLAC 24-bit",
            ),
        )
        assertEquals(
            androidx.media3.common.MimeTypes.APPLICATION_MPD,
            com.music.spotui.di.SongPlayer.streamMimeType(
                "https://manifest.tidal.com/manifests/track123",
                source = "Lossless • Tidal",
                quality = "FLAC 16-bit",
            ),
        )
        assertEquals(
            androidx.media3.common.MimeTypes.AUDIO_FLAC,
            com.music.spotui.di.SongPlayer.streamMimeType(
                "deezer://track/1?quality=9",
                source = "Lossless • Deezer",
                quality = "FLAC 16-bit",
            ),
        )
        assertEquals(
            androidx.media3.common.MimeTypes.AUDIO_MPEG,
            com.music.spotui.di.SongPlayer.streamMimeType(
                "deezer://track/1?quality=3",
                source = "Deezer Direct",
                quality = "MP3 320 kbps",
            ),
        )
    }

    @Test
    fun widgetKeyRelay_mapsMediaKeyCodesToCommands() {
        val relay = com.music.spotui.widget.WidgetKeyRelay()
        assertEquals(
            com.music.spotui.playback.MediaCommand.Next,
            relay.mapKeyCodeToCommand(android.view.KeyEvent.KEYCODE_MEDIA_NEXT),
        )
        assertEquals(
            com.music.spotui.playback.MediaCommand.Previous,
            relay.mapKeyCodeToCommand(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS),
        )
        assertEquals(
            com.music.spotui.playback.MediaCommand.PlayPause,
            relay.mapKeyCodeToCommand(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE),
        )
        assertEquals(
            com.music.spotui.playback.MediaCommand.Play,
            relay.mapKeyCodeToCommand(android.view.KeyEvent.KEYCODE_MEDIA_PLAY),
        )
        assertEquals(
            com.music.spotui.playback.MediaCommand.Pause,
            relay.mapKeyCodeToCommand(android.view.KeyEvent.KEYCODE_MEDIA_PAUSE),
        )
    }

    @Test
    fun systemHealthChecker_enforcesTenSecondCooldown() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.music.spotui.util.SystemHealthChecker.resetCooldownForTest()
        val t0 = 1_000_000L
        val first = com.music.spotui.util.SystemHealthChecker.runFullScan(
            context,
            nowMs = t0,
            skipNetworkProbesForTest = true,
        )
        assertEquals(18, first.checks.size)
        assertTrue(com.music.spotui.util.SystemHealthChecker.remainingCooldownMs(t0 + 2_000L) > 0L)
        val cached = com.music.spotui.util.SystemHealthChecker.runFullScan(
            context,
            nowMs = t0 + 2_000L,
            skipNetworkProbesForTest = true,
        )
        assertEquals(first.timestampMs, cached.timestampMs)
        assertEquals(0L, com.music.spotui.util.SystemHealthChecker.remainingCooldownMs(t0 + 11_000L))
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

    @Test
    fun songPlayer_queuesFrameZeroLookaheadAndSyncsRepeatOne() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prevYt = com.music.spotui.di.SongPlayer.youtubeEnabled
        val prevLossless = com.music.spotui.di.SongPlayer.losslessStreaming
        val prevWeb = com.music.spotui.di.SongPlayer.webPlayerEnabled
        val prevCrossfade = com.music.spotui.data.preferences.getCrossfadeMs(context)
        try {
            com.music.spotui.di.SongPlayer.youtubeEnabled = false
            com.music.spotui.di.SongPlayer.losslessStreaming = false
            com.music.spotui.di.SongPlayer.webPlayerEnabled = false
            com.music.spotui.data.preferences.setCrossfadeMs(context, 0)

            val track1 = com.music.spotui.data.entity.SongsModel(
                id = 101,
                title = "Track One",
                album = "Album A",
                singer = "Artist One",
                coverUri = "",
                url = "spotify:track:t1|Track One Artist One",
                durationMs = 200_000,
            )
            val track2 = com.music.spotui.data.entity.SongsModel(
                id = 102,
                title = "Track Two",
                album = "Album A",
                singer = "Artist Two",
                coverUri = "",
                url = "spotify:track:t2|Track Two Artist Two",
                durationMs = 210_000,
            )
            val songState = com.music.spotui.di.CurrentSongState(context)
            songState.updateQueue(listOf(track1, track2))
            songState.updateRepeatState(com.music.spotui.di.RepeatMode.ONE)
            songState.updateSongState(
                track1.coverUri, track1.title, track1.singer, true,
                track1.id, 0, track1.album,
            )
            com.music.spotui.di.SongPlayer.bindState(songState)
            com.music.spotui.di.SongPlayer.playSong(track1.url, context, "song/${track1.id}")
            org.robolectric.shadows.ShadowLooper.idleMainLooper()

            val p = com.music.spotui.di.SongPlayer.exoPlayer!!
            assertEquals(2, p.mediaItemCount)
            assertEquals("song/101", p.getMediaItemAt(0).mediaId)
            assertEquals("song/102", p.getMediaItemAt(1).mediaId)
            assertTrue(p.hasNextMediaItem())
            assertTrue(p.isCommandAvailable(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT))
            assertEquals(androidx.media3.common.Player.REPEAT_MODE_ONE, p.repeatMode)
        } finally {
            com.music.spotui.di.SongPlayer.release()
            com.music.spotui.data.preferences.setCrossfadeMs(context, prevCrossfade)
            com.music.spotui.di.SongPlayer.youtubeEnabled = prevYt
            com.music.spotui.di.SongPlayer.losslessStreaming = prevLossless
            com.music.spotui.di.SongPlayer.webPlayerEnabled = prevWeb
        }
    }

    @Test
    fun artworkHelper_buildTrackMetadata_omitsBlankAlbumAndNonPositiveDuration() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val metaMinimal = com.music.spotui.util.ArtworkHelper.buildTrackMetadata(
            context = context,
            title = "Midnight",
            artist = "M83",
            album = "   ",
            coverUri = "",
            durationMs = 0L,
            songId = "song/99",
            streamUrl = "/nonexistent/local/audio.flac",
        )
        assertEquals("Midnight", metaMinimal.title?.toString())
        assertEquals("M83", metaMinimal.artist?.toString())
        assertEquals(null, metaMinimal.albumTitle)
        assertEquals(null, metaMinimal.durationMs)
        assertEquals(true, metaMinimal.isPlayable)

        val metaFull = com.music.spotui.util.ArtworkHelper.buildTrackMetadata(
            context = context,
            title = "City",
            artist = "M83",
            album = "Hurry Up",
            coverUri = "https://example.com/cover.jpg",
            durationMs = 243_000L,
            songId = "song/100",
        )
        assertEquals("Hurry Up", metaFull.albumTitle?.toString())
        assertEquals(243_000L, metaFull.durationMs)
        assertEquals("https://example.com/cover.jpg", metaFull.artworkUri?.toString())
    }

    @Test
    fun notificationProvider_augmentsNextPrevWhenMultiTrackQueueActive() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prevYt = com.music.spotui.di.SongPlayer.youtubeEnabled
        val prevLossless = com.music.spotui.di.SongPlayer.losslessStreaming
        val prevWeb = com.music.spotui.di.SongPlayer.webPlayerEnabled
        try {
            com.music.spotui.di.SongPlayer.youtubeEnabled = false
            com.music.spotui.di.SongPlayer.losslessStreaming = false
            com.music.spotui.di.SongPlayer.webPlayerEnabled = false

            val track1 = com.music.spotui.data.entity.SongsModel(1, "A", "Alb", "Art", "", "u1")
            val track2 = com.music.spotui.data.entity.SongsModel(2, "B", "Alb", "Art", "", "u2")
            val songState = com.music.spotui.di.CurrentSongState(context)
            songState.updateQueue(listOf(track1, track2))
            com.music.spotui.di.SongPlayer.bindState(songState)

            val controller = Robolectric.buildService(PlaybackService::class.java)
            val service = controller.create().get()
            val session = service.sessions.first()
            val provider = com.music.spotui.ui.notification.SpotuiMediaNotificationProvider(context)
            val baseCommands = androidx.media3.common.Player.Commands.Builder()
                .add(androidx.media3.common.Player.COMMAND_PLAY_PAUSE)
                .build()
            val buttons = provider.resolveMediaButtons(
                session,
                baseCommands,
                com.google.common.collect.ImmutableList.of(),
                true,
            )
            val playerCommands = buttons.map { it.playerCommand }.toSet()
            assertTrue(playerCommands.contains(androidx.media3.common.Player.COMMAND_PLAY_PAUSE))
            assertTrue(playerCommands.contains(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT))
            assertTrue(playerCommands.contains(androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS))
            controller.destroy()
        } finally {
            com.music.spotui.di.SongPlayer.release()
            com.music.spotui.di.SongPlayer.youtubeEnabled = prevYt
            com.music.spotui.di.SongPlayer.losslessStreaming = prevLossless
            com.music.spotui.di.SongPlayer.webPlayerEnabled = prevWeb
        }
    }
}
