package com.music.spotui.util

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import com.metrolist.innertube.network.AuthSessionStore
import com.metrolist.innertube.network.DnsOverHttpsPreset
import com.music.spotui.MyApplication
import com.music.spotui.data.preferences.getDnsPreset
import com.music.spotui.data.preferences.getProxyUrls
import com.music.spotui.data.preferences.isIpRotationEnabled
import com.music.spotui.data.preferences.isStreamBypassProxyEnabled
import com.music.spotui.data.preferences.isWebClientPoTokenEnabled
import com.music.spotui.data.preferences.getPlaybackPitchPref
import com.music.spotui.data.preferences.getPlaybackSpeedPref
import com.music.spotui.data.preferences.isSkipSilenceEnabled
import com.music.spotui.data.preferences.setDnsPreset
import com.music.spotui.data.preferences.setIpRotationEnabled
import com.music.spotui.data.preferences.setPlaybackPitchPref
import com.music.spotui.data.preferences.setPlaybackSpeedPref
import com.music.spotui.data.preferences.setProxyUrls
import com.music.spotui.data.preferences.setSkipSilenceEnabled
import com.music.spotui.data.preferences.setStreamBypassProxyEnabled
import com.music.spotui.data.preferences.setWebClientPoTokenEnabled
import com.music.spotui.data.update.UpdateChecker
import com.music.spotui.ui.theme.ExpressiveThemePreset
import com.music.spotui.ui.theme.ThemePreferences
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = MyApplication::class)
class IntentAndPermissionTest {

    @After
    fun tearDown() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setDnsPreset(context, DnsOverHttpsPreset.SYSTEM)
        setProxyUrls(context, "")
        setIpRotationEnabled(context, false)
        setStreamBypassProxyEnabled(context, false)
        setWebClientPoTokenEnabled(context, true)
        setPlaybackSpeedPref(context, 1.0f)
        setPlaybackPitchPref(context, 1.0f)
        setSkipSilenceEnabled(context, false)
        ThemePreferences.setPreset(context, ExpressiveThemePreset.SPOTUI_BLUE)
    }

    @Test
    fun deepLinkHandler_parsesPlayFromSearch_Send_LocalAudio_andMusicLinks() {
        assertNull(DeepLinkHandler.parseIntent(null))

        val searchIntent = Intent("android.media.action.MEDIA_PLAY_FROM_SEARCH").apply {
            putExtra(SearchManager.QUERY, "Midnight City")
            putExtra(MediaStore.EXTRA_MEDIA_ARTIST, "M83")
        }
        val parsedSearch = DeepLinkHandler.parseIntent(searchIntent)
        assertTrue(parsedSearch is DeepLinkHandler.IncomingAction.PlayFromSearch)
        assertEquals("Midnight City", (parsedSearch as DeepLinkHandler.IncomingAction.PlayFromSearch).query)
        assertEquals("M83", parsedSearch.artist)

        val sharedSpotifyIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Listen to this track https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT?si=123")
        }
        val parsedSharedLink = DeepLinkHandler.parseIntent(sharedSpotifyIntent)
        assertTrue(parsedSharedLink is DeepLinkHandler.IncomingAction.DeepLinkUri)
        assertTrue((parsedSharedLink as DeepLinkHandler.IncomingAction.DeepLinkUri).uri.toString().contains("4cOdK2wGLETKBW3PvgPWqT"))

        val sharedQueryIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Daft Punk Instant Crush")
        }
        val parsedSharedQuery = DeepLinkHandler.parseIntent(sharedQueryIntent)
        assertTrue(parsedSharedQuery is DeepLinkHandler.IncomingAction.SharedText)
        assertEquals("Daft Punk Instant Crush", (parsedSharedQuery as DeepLinkHandler.IncomingAction.SharedText).text)

        val localAudioIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("content://media/external/audio/media/42"), "audio/flac")
        }
        val parsedAudio = DeepLinkHandler.parseIntent(localAudioIntent)
        assertTrue(parsedAudio is DeepLinkHandler.IncomingAction.LocalAudioUri)
        assertEquals("audio/flac", (parsedAudio as DeepLinkHandler.IncomingAction.LocalAudioUri).mimeType)

        val youtubeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/watch?v=dQw4w9WgXcQ"))
        val parsedYt = DeepLinkHandler.parseIntent(youtubeIntent)
        assertTrue(parsedYt is DeepLinkHandler.IncomingAction.DeepLinkUri)
    }

    @Test
    fun permissionHelper_exposesCorrectSdkPermissions_andIntents() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(PermissionHelper.notificationPermissions().isNotEmpty())
        assertTrue(PermissionHelper.mediaReadPermissions().isNotEmpty())
        assertTrue(PermissionHelper.bluetoothConnectPermissions().isNotEmpty())

        val batteryIntent = PermissionHelper.requestIgnoreBatteryOptimizationIntent(context)
        assertNotNull(batteryIntent.data)
        assertTrue(batteryIntent.data.toString().contains(context.packageName))
    }

    @Test
    fun settingsPref_networkAndAuthSettings_syncWithAuthSessionStore() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setDnsPreset(context, DnsOverHttpsPreset.CLOUDFLARE)
        assertEquals(DnsOverHttpsPreset.CLOUDFLARE, getDnsPreset(context))
        assertEquals(DnsOverHttpsPreset.CLOUDFLARE, AuthSessionStore.dnsPreset)

        setProxyUrls(context, "http://127.0.0.1:8080, socks5://127.0.0.1:1080")
        assertEquals(2, AuthSessionStore.activeProxyCount())
        assertTrue(getProxyUrls(context).contains("8080"))

        setIpRotationEnabled(context, true)
        assertTrue(isIpRotationEnabled(context))
        assertTrue(AuthSessionStore.ipRotationEnabled)

        setStreamBypassProxyEnabled(context, true)
        assertTrue(isStreamBypassProxyEnabled(context))
        assertTrue(AuthSessionStore.streamBypassProxy)

        setWebClientPoTokenEnabled(context, false)
        assertFalse(isWebClientPoTokenEnabled(context))
        assertFalse(AuthSessionStore.webClientPoTokenEnabled)
    }

    @Test
    fun thanksgivingTheme_andPlaybackTuningPreferences_persistAndClampCleanly() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val spec = ThemePreferences.presetSpec(ExpressiveThemePreset.THANKSGIVING)
        assertEquals("Harvest Gold", spec.name)
        assertEquals(0xFFE87A24.toInt(), spec.primaryArgb)
        assertEquals(0xFFFFB74D.toInt(), spec.secondaryArgb)
        ThemePreferences.setPreset(context, ExpressiveThemePreset.THANKSGIVING)
        assertEquals(ExpressiveThemePreset.THANKSGIVING, ThemePreferences.currentSpec(context).preset)

        setPlaybackSpeedPref(context, 1.35f)
        assertEquals(1.35f, getPlaybackSpeedPref(context), 0.001f)
        setPlaybackSpeedPref(context, 9.0f)
        assertEquals(2.0f, getPlaybackSpeedPref(context), 0.001f)

        setPlaybackPitchPref(context, 0.9f)
        assertEquals(0.9f, getPlaybackPitchPref(context), 0.001f)
        setPlaybackPitchPref(context, 0.1f)
        assertEquals(0.75f, getPlaybackPitchPref(context), 0.001f)

        setSkipSilenceEnabled(context, true)
        assertTrue(isSkipSilenceEnabled(context))
    }

    @Test
    fun missingRuntimePermissions_andUpdateCheckerUrl_matchExpectedContracts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val missing = PermissionHelper.missingRuntimePermissions(context)
        assertTrue(missing.size == missing.distinct().size)
        assertEquals(
            "https://api.github.com/repos/lfisher447-afk/SpotUi-Offical/releases/latest",
            UpdateChecker.buildLatestReleaseApiUrl("https://github.com/lfisher447-afk/SpotUi-Offical.git/"),
        )
    }
}
