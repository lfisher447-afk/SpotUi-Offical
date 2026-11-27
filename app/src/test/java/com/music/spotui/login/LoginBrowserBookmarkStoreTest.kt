package com.music.spotui.login

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.music.spotui.ui.screens.SpotifyLogin.helper.LoginBrowserBookmarkStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LoginBrowserBookmarkStoreTest {
    @Test
    fun bookmarksCrudPinFilterAndJsonRoundtripWork() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val defaults = LoginBrowserBookmarkStore.resetDefaultBookmarks(context)
        assertTrue(defaults.size >= 6)
        assertTrue(LoginBrowserBookmarkStore.isBookmarked(defaults, "https://open.spotify.com"))

        val added = LoginBrowserBookmarkStore.addOrUpdateBookmark(
            context = context,
            title = "SpotUI Docs",
            url = "github.com/lfisher447-afk/SpotUi-Offical",
            folder = "Docs",
            pinned = false,
        )
        val created = added.first { it.title == "SpotUI Docs" }
        assertEquals("https://github.com/lfisher447-afk/SpotUi-Offical", created.url)
        assertFalse(created.pinned)

        val toggled = LoginBrowserBookmarkStore.togglePin(context, created.id)
        assertTrue(toggled.first { it.id == created.id }.pinned)

        val filtered = LoginBrowserBookmarkStore.filterBookmarks(toggled, "SpotUI", "Docs")
        assertEquals(1, filtered.size)

        val json = LoginBrowserBookmarkStore.exportBookmarksJson(toggled)
        LoginBrowserBookmarkStore.resetDefaultBookmarks(context)
        val imported = LoginBrowserBookmarkStore.importBookmarksJson(context, json, merge = true).getOrThrow()
        assertTrue(imported.any { it.title == "SpotUI Docs" })

        val afterRemove = LoginBrowserBookmarkStore.removeBookmark(context, created.id)
        assertFalse(afterRemove.any { it.id == created.id })
    }

    @Test
    fun urlNormalizationAndHistoryWork() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("http://127.0.0.1:8765/", LoginBrowserBookmarkStore.normalizeBrowserInput("127.0.0.1:8765/"))
        assertEquals("https://open.spotify.com", LoginBrowserBookmarkStore.normalizeBrowserInput("open.spotify.com"))
        assertTrue(
            LoginBrowserBookmarkStore.normalizeBrowserInput("spotify cookie login")
                .startsWith("https://duckduckgo.com/?q="),
        )

        LoginBrowserBookmarkStore.clearHistory(context)
        val h1 = LoginBrowserBookmarkStore.recordHistory(context, "https://accounts.spotify.com/en/login", "Spotify Login")
        assertEquals(1, h1.size)
        val h2 = LoginBrowserBookmarkStore.recordHistory(context, "https://open.spotify.com/", "Spotify Web")
        assertEquals(2, h2.size)
        assertEquals("https://open.spotify.com/", h2.first().url)
        assertTrue(LoginBrowserBookmarkStore.clearHistory(context).isEmpty())
    }
}
