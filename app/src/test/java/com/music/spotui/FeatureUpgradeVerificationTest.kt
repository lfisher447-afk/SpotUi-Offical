package com.music.spotui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.metrolist.spotify.SpotiFlac
import com.music.spotui.data.preferences.ProviderRoutingPreferences
import com.music.spotui.data.preferences.getCustomLosslessMirrorUrl
import com.music.spotui.data.preferences.getDeezerArl
import com.music.spotui.data.preferences.getUpdateRepoUrl
import com.music.spotui.data.preferences.isStableUpdatesOnly
import com.music.spotui.data.preferences.setCustomLosslessMirrorUrl
import com.music.spotui.data.preferences.setDeezerArl
import com.music.spotui.data.preferences.setStableUpdatesOnly
import com.music.spotui.data.update.UpdateChecker
import com.music.spotui.data.youtube.YouTubeMusicSync
import com.music.spotui.ui.screens.DeezerLoginActivity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = MyApplication::class)
class FeatureUpgradeVerificationTest {

    @After
    fun tearDown() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ProviderRoutingPreferences.setLosslessOrder(context, listOf("tidal", "qobuz", "amazon", "deezer"))
        listOf("tidal", "qobuz", "amazon", "deezer").forEach {
            ProviderRoutingPreferences.setProviderEnabled(context, it, true)
        }
        ProviderRoutingPreferences.setSoloProvider(context, false)
        setDeezerArl(context, "")
        setCustomLosslessMirrorUrl(context, "")
        setStableUpdatesOnly(context, true)
        YouTubeMusicSync.setRedirectMode(context, YouTubeMusicSync.OAuthRedirectMode.ANDROID_SCHEME)
    }

    @Test
    fun losslessProviderSwitching_andDeezerArlSync_workCorrectly() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Switch primary provider to Deezer
        val orderAfterSwitch = ProviderRoutingPreferences.setPrimaryProvider(context, "deezer")
        assertEquals("deezer", orderAfterSwitch.first())
        assertTrue("deezer" in ProviderRoutingPreferences.enabledProviders(context))

        // Solo mode toggle
        ProviderRoutingPreferences.setSoloProvider(context, true)
        assertTrue(ProviderRoutingPreferences.soloProvider(context))

        // Disable other providers cleanly, leaving at least 1 enabled
        ProviderRoutingPreferences.setProviderEnabled(context, "tidal", false)
        ProviderRoutingPreferences.setProviderEnabled(context, "qobuz", false)
        ProviderRoutingPreferences.setProviderEnabled(context, "amazon", false)
        val remaining = ProviderRoutingPreferences.setProviderEnabled(context, "deezer", false)
        assertEquals(setOf("deezer"), remaining)

        // Deezer ARL & custom mirror propagation
        val fakeArl = "a".repeat(64)
        setDeezerArl(context, fakeArl)
        assertEquals(fakeArl, getDeezerArl(context))
        assertEquals(fakeArl, SpotiFlac.deezerArl)

        setCustomLosslessMirrorUrl(context, "https://lossless-mirror.example/")
        assertEquals("https://lossless-mirror.example", getCustomLosslessMirrorUrl(context))
        assertEquals("https://lossless-mirror.example", SpotiFlac.customMirrorBaseUrl)

        val extracted = DeezerLoginActivity.extractArlFromCookieHeader("sid=123; arl=$fakeArl; dz_lang=en")
        assertEquals(fakeArl, extracted)
        assertNull(DeezerLoginActivity.extractArlFromCookieHeader("sid=123; dz_lang=en"))
    }

    @Test
    fun youtubeMusicSync_loopbackOAuth_andUrlParsing_workWithoutCustomSchemeWebError() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val loopbackUrl = YouTubeMusicSync.buildAuthorizationUri(
            clientId = "123456.apps.googleusercontent.com",
            mode = YouTubeMusicSync.OAuthRedirectMode.LOOPBACK_HTTP,
            codeChallenge = "challenge123",
        )
        assertTrue(loopbackUrl.contains("redirect_uri=http%3A%2F%2F127.0.0.1%3A8765%2Foauth2redirect"))

        val androidUrl = YouTubeMusicSync.buildAuthorizationUri(
            clientId = "123456.apps.googleusercontent.com",
            mode = YouTubeMusicSync.OAuthRedirectMode.ANDROID_SCHEME,
            codeChallenge = "challenge123",
        )
        assertTrue(androidUrl.contains("redirect_uri=com.music.spotui%3A%2Foauth2redirect"))

        YouTubeMusicSync.setRedirectMode(context, YouTubeMusicSync.OAuthRedirectMode.LOOPBACK_HTTP)
        assertEquals(YouTubeMusicSync.OAuthRedirectMode.LOOPBACK_HTTP, YouTubeMusicSync.status(context).redirectMode)

        assertEquals(
            "PL1234567890ABCDEF",
            YouTubeMusicSync.extractPlaylistIdOrRaw("https://music.youtube.com/playlist?list=PL1234567890ABCDEF"),
        )
        assertEquals(
            "PL9999999",
            YouTubeMusicSync.extractPlaylistIdOrRaw("VLPL9999999"),
        )
        assertNull(YouTubeMusicSync.extractPlaylistIdOrRaw("not a valid url"))

        val parsedQuery = YouTubeMusicSync.parseQueryParams("code=abc%2F123&scope=youtube.readonly")
        assertEquals("abc/123", parsedQuery["code"])
    }

    @Test
    fun updateChecker_prefersStableApks_parsesAtomFeed_andDetectsStableUpgradeFromDebug() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("https://github.com/lfisher447-afk/SpotUi-Offical", getUpdateRepoUrl(context))
        assertTrue(isStableUpdatesOnly(context))

        val assets = JSONArray()
            .put(JSONObject().put("name", "app-debug.apk").put("browser_download_url", "https://example.com/app-debug.apk"))
            .put(JSONObject().put("name", "SpotUI-v2.1.8-release.apk").put("browser_download_url", "https://example.com/SpotUI-v2.1.8-release.apk"))
        assertEquals("https://example.com/SpotUI-v2.1.8-release.apk", UpdateChecker.selectBestApkUrl(assets))

        val atom = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry>
                <title>SpotUI v2.2.0 Stable</title>
                <link rel="alternate" href="https://github.com/lfisher447-afk/SpotUi-Offical/releases/tag/v2.2.0"/>
                <updated>2026-11-12T00:00:00Z</updated>
                <content type="html">&lt;p&gt;Maintained by Andrew&lt;/p&gt;</content>
              </entry>
            </feed>
        """.trimIndent()
        val parsed = UpdateChecker.parseReleasesAtom(atom, "https://github.com/lfisher447-afk/SpotUi-Offical")
        assertEquals("2.2.0", parsed?.version)
        assertEquals("Maintained by Andrew", parsed?.releaseBody)
        assertFalse(parsed?.isPrerelease ?: true)

        assertTrue(UpdateChecker.isNewer("2.1.8", "2.1.7"))
        assertTrue(UpdateChecker.isNewer("2.1.7", "2.1.7-debug"))
        assertFalse(UpdateChecker.isNewer("2.1.7", "2.1.7"))
    }
}
