package com.music.spotui

import android.content.Context
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.playback.LogicalQueueDisplay
import com.music.spotui.playback.MediaCommand
import com.music.spotui.playback.MediaCommandDispatcher
import com.music.spotui.playback.PlaybackSessionBus
import com.music.spotui.playback.QueueWindow
import com.music.spotui.playback.ResumePlanBuilder
import com.music.spotui.ui.notification.PlaybackService
import com.music.spotui.widget.NowPlayingWidgetState
import com.music.spotui.widget.NowPlayingWidgetStateStore
import com.music.spotui.widget.WidgetControls
import com.music.spotui.widget.WidgetKeyRelay
import com.music.spotui.widget.WidgetMediaIntents
import com.music.spotui.widget.WidgetTransport
import com.music.spotui.widget.WidgetUpdateDebouncer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = MyApplication::class)
class WidgetAndPlaybackCoordinationTest {

    @After
    fun tearDown() {
        MediaCommandDispatcher.stop()
        PlaybackSessionBus.onServiceStopping()
    }

    @Test
    fun widgetControls_mapsServiceAliveAndDeadStatesAccurately() {
        assertNull(WidgetControls.of(null, serviceRunning = true))
        assertNull(WidgetControls.of(NowPlayingWidgetState(title = ""), serviceRunning = true))

        val playing = NowPlayingWidgetState(songId = 10, title = "Track", artist = "Artist", isPlaying = true)

        // Playing + service running -> non-foreground Pause & Next via WidgetKeyRelay
        val liveButtons = WidgetControls.of(playing, serviceRunning = true)!!
        assertTrue(liveButtons.showsPause)
        assertEquals(KeyEvent.KEYCODE_MEDIA_PAUSE, liveButtons.playPause.keyCode)
        assertFalse(liveButtons.playPause.foreground)
        assertEquals(KeyEvent.KEYCODE_MEDIA_NEXT, liveButtons.next.keyCode)
        assertFalse(liveButtons.next.foreground)

        // Stored "playing" after process death -> believe serviceRunning == false, force foreground Play / SkipFirst
        val deadButtons = WidgetControls.of(playing, serviceRunning = false)!!
        assertFalse(deadButtons.showsPause)
        assertEquals(KeyEvent.KEYCODE_MEDIA_PLAY, deadButtons.playPause.keyCode)
        assertTrue(deadButtons.playPause.foreground)
        assertFalse(deadButtons.playPause.skipFirst)
        assertEquals(KeyEvent.KEYCODE_MEDIA_PLAY, deadButtons.next.keyCode)
        assertTrue(deadButtons.next.foreground)
        assertTrue(deadButtons.next.skipFirst)
    }

    @Test
    fun widgetMediaIntents_routeForegroundToService_andRelayWhenServiceRunning() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val fgPlay = WidgetMediaIntents.mediaButton(
            context,
            WidgetTransport(KeyEvent.KEYCODE_MEDIA_PLAY, foreground = true, skipFirst = false),
        )
        assertEquals(PlaybackService::class.java.name, fgPlay.component?.className)
        assertFalse(WidgetMediaIntents.isSkipFirst(fgPlay))

        val fgNext = WidgetMediaIntents.mediaButton(
            context,
            WidgetTransport(KeyEvent.KEYCODE_MEDIA_PLAY, foreground = true, skipFirst = true),
        )
        assertEquals(PlaybackService::class.java.name, fgNext.component?.className)
        assertTrue(WidgetMediaIntents.isSkipFirst(fgNext))

        val relayPause = WidgetMediaIntents.mediaButton(
            context,
            WidgetTransport(KeyEvent.KEYCODE_MEDIA_PAUSE, foreground = false),
        )
        assertEquals(WidgetKeyRelay::class.java.name, relayPause.component?.className)
    }

    @Test
    fun widgetUpdateDebouncer_coalescesBursts_respectsMaxWait_andDropsDuplicate() = runTest {
        val published = mutableListOf<NowPlayingWidgetState>()
        var virtualClock = 0L
        val debouncer = WidgetUpdateDebouncer(
            scope = backgroundScope,
            clock = { virtualClock },
            publish = { published.add(it) },
        )

        val s1 = NowPlayingWidgetState(songId = 1, title = "A", isPlaying = true)
        val s2 = NowPlayingWidgetState(songId = 1, title = "A", artworkUrl = "https://img/a.jpg", isPlaying = true)
        debouncer.submit(s1)
        virtualClock += 120L
        advanceTimeBy(120L)
        debouncer.submit(s2)
        runCurrent()
        assertEquals(0, published.size)

        // Advance past quietMs (350ms) -> publishes only settled s2
        virtualClock += 350L
        advanceTimeBy(350L)
        runCurrent()
        assertEquals(listOf(s2), published)

        // Flap back to something temporarily then back to s2 before quietMs -> no extra publish
        debouncer.submit(s2.copy(isPlaying = false))
        virtualClock += 100L
        advanceTimeBy(100L)
        debouncer.submit(s2)
        virtualClock += 400L
        advanceTimeBy(400L)
        runCurrent()
        assertEquals(1, published.size)

        // After reset(), exact same s2 state publishes again (post idle stop)
        debouncer.reset()
        debouncer.submit(s2)
        virtualClock += 350L
        advanceTimeBy(350L)
        runCurrent()
        assertEquals(2, published.size)
    }

    @Test
    fun resumePlan_queueWindow_andLogicalQueueDisplay_enforceContracts() {
        val queue = (1..2000).map { i ->
            SongsModel(
                id = i,
                title = "Track $i",
                album = "Album",
                singer = "Artist",
                coverUri = "",
                url = "spotify:track:id_$i",
                spotifyTrackId = "id_$i",
            )
        }

        // Empty queue returns null from buildFromPersistedQueue
        assertNull(ResumePlanBuilder.buildFromPersistedQueue(emptyList()))

        // Resume last track at index 1200 -> preserves index & position
        val plan = ResumePlanBuilder.buildFromPersistedQueue(
            savedQueue = queue,
            lastSong = queue[1200],
            lastPositionMs = 42_000L,
            skipFirst = false,
        )!!
        assertEquals(1200, plan.startIndex)
        assertEquals(42_000L, plan.positionMs)

        // skipFirst = true advances +1 and resets position to 0
        val skipPlan = ResumePlanBuilder.buildFromPersistedQueue(
            savedQueue = queue,
            lastSong = queue[1999],
            lastPositionMs = 42_000L,
            skipFirst = true,
        )!!
        assertEquals(0, skipPlan.startIndex)
        assertEquals(0L, skipPlan.positionMs)

        // QueueWindow keeps Media3 IPC bounded at 500 items centered around active track
        val slice = QueueWindow.slice(queue, currentIndex = 1200)
        assertEquals(QueueWindow.MAX_PLAYER_ITEMS, slice.items.size)
        assertEquals(queue[1200].id, slice.items[slice.activeIndexInWindow].id)
        assertEquals(2000, slice.totalLogicalSize)

        // LogicalQueueDisplay prefers logical queue when current track is present, else timeline fallback
        val subsetTimeline = listOf(queue[0])
        assertEquals(queue, LogicalQueueDisplay.compute(queue, currentSongId = 1200, timelineFallback = subsetTimeline))
        val externalTrack = SongsModel(id = 99999, title = "External", album = "A", singer = "S", coverUri = "", url = "ext")
        assertEquals(
            listOf(externalTrack),
            LogicalQueueDisplay.compute(queue, currentSongId = 99999, timelineFallback = listOf(externalTrack)),
        )
    }

    @Test
    fun mediaCommandDispatcher_debouncesHardwareBurstToFinalIntent() = runTest {
        val dispatched = mutableListOf<MediaCommand>()
        MediaCommandDispatcher.start(
            scope = backgroundScope,
            windowMs = 100L,
            sink = { dispatched.add(it) },
        )

        MediaCommandDispatcher.dispatchMediaCommand(MediaCommand.Play)
        advanceTimeBy(30L)
        MediaCommandDispatcher.dispatchMediaCommand(MediaCommand.Pause)
        advanceTimeBy(30L)
        MediaCommandDispatcher.dispatchMediaCommand(MediaCommand.Next)
        runCurrent()
        assertTrue(dispatched.isEmpty())

        advanceTimeBy(110L)
        runCurrent()
        assertEquals(listOf(MediaCommand.Next), dispatched)
        MediaCommandDispatcher.stop()
    }

    @Test
    fun sessionBus_andWidgetStateStore_trackLifecycleAndPersistence() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        PlaybackSessionBus.onServiceCreated()
        assertTrue(PlaybackSessionBus.sessionAlive.value)
        PlaybackSessionBus.onServiceStopping()
        assertFalse(PlaybackSessionBus.sessionAlive.value)

        val state = NowPlayingWidgetState(
            songId = 77,
            title = "Starboy",
            artist = "The Weeknd",
            album = "Starboy",
            artworkUrl = "https://example.com/art.jpg",
            isPlaying = true,
            isLiked = true,
            updatedAtMs = 1000L,
        )
        NowPlayingWidgetStateStore.saveState(context, state)
        assertEquals(state, NowPlayingWidgetStateStore.getState(context))

        val stopped = NowPlayingWidgetStateStore.markStopped(context)
        assertNotNull(stopped)
        assertFalse(stopped!!.isPlaying)
        assertEquals("Starboy", stopped.title)
        assertTrue(stopped.isLiked)
    }
}
