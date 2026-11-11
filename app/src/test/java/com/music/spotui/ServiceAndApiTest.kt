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
        controller.destroy()
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
