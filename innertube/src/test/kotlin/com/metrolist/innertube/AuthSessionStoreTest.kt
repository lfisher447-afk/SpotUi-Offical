package com.metrolist.innertube

import com.metrolist.innertube.models.ResponseContext
import com.metrolist.innertube.models.YouTubeClient
import com.metrolist.innertube.models.response.AccountMenuResponse
import com.metrolist.innertube.models.response.BrowseResponse
import com.metrolist.innertube.network.AuthSessionParsing
import com.metrolist.innertube.network.AuthSessionStore
import com.metrolist.innertube.network.DnsOverHttpsPreset
import com.metrolist.innertube.network.PlaybackAuthState
import com.metrolist.innertube.proxy.RotatingProxyClient
import com.metrolist.innertube.utils.hasYouTubeLoginCookie
import com.metrolist.innertube.utils.parseCookieString
import com.metrolist.innertube.utils.sapisidAuthorizationHeader
import com.metrolist.innertube.utils.youtubeLoginCookieValue
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Proxy

class AuthSessionStoreTest {

    @After
    fun tearDown() {
        AuthSessionStore.applyAuthState(PlaybackAuthState.EMPTY)
        AuthSessionStore.dnsPreset = DnsOverHttpsPreset.SYSTEM
        AuthSessionStore.applyProxyConfiguration(null)
        AuthSessionStore.ipRotationEnabled = false
        AuthSessionStore.streamBypassProxy = false
        RotatingProxyClient.reset()
    }

    @Test
    fun playbackAuthState_normalizesDataSyncId_andDelegatedSession() {
        val raw = PlaybackAuthState(
            cookie = "  SAPISID=abc; VISITOR_INFO1_LIVE=vis123  ",
            visitorData = "  null ",
            dataSyncId = "delegated_id%7C%7Cowner_id%20extra",
            poToken = "general_pt",
            poTokenGvs = "gvs_pt",
            poTokenPlayer = "player_pt",
            webClientPoTokenEnabled = true,
        ).normalized()

        assertNull(raw.visitorData)
        assertEquals("delegated_id||owner_id extra", raw.dataSyncId)
        assertEquals("delegated_id", raw.delegatedSessionId)
        assertTrue(raw.hasLoginCookie)
        assertTrue(raw.hasPlaybackLoginContext)
        assertEquals(raw.dataSyncId, raw.sessionId)

        val trailingEmpty = PlaybackAuthState(dataSyncId = "single_id||").normalized()
        assertEquals("single_id", trailingEmpty.dataSyncId)
        assertNull(trailingEmpty.delegatedSessionId)
    }

    @Test
    fun playbackAuthState_resolvesPoTokens_onlyForServiceIntegrityClients() {
        val state = PlaybackAuthState(
            poToken = "fallback_token",
            poTokenGvs = "gvs_token",
            poTokenPlayer = "player_token",
            webClientPoTokenEnabled = true,
        )

        assertEquals("player_token", state.resolvePlayerPoToken(YouTubeClient.WEB_REMIX))
        assertEquals("explicit_token", state.resolvePlayerPoToken(YouTubeClient.ANDROID_VR_NO_AUTH, "  explicit_token "))
        assertNull(state.resolvePlayerPoToken(YouTubeClient.ANDROID_VR_NO_AUTH))

        assertEquals("gvs_token", state.resolveGvsPoToken(YouTubeClient.WEB_REMIX))
        assertEquals("gvs_token", state.resolveGvsPoToken(null))
        assertNull(state.resolveGvsPoToken(YouTubeClient.ANDROID_VR_NO_AUTH))

        val disabled = state.copy(webClientPoTokenEnabled = false)
        assertNull(disabled.resolvePlayerPoToken(YouTubeClient.WEB_REMIX))
        assertNull(disabled.resolveGvsPoToken(YouTubeClient.WEB_REMIX))
    }

    @Test
    fun cookieUtils_parseTrimmedPairs_andBuildMultiSapisidHeader() {
        val cookie = " PREF=f4=40000; SAPISID=sap1; __Secure-1PAPISID=onep; __Secure-3PAPISID=threep "
        val parsed = parseCookieString(cookie)
        assertEquals("f4=40000", parsed["PREF"])
        assertEquals("sap1", parsed["SAPISID"])
        assertTrue(hasYouTubeLoginCookie(cookie))
        assertEquals("sap1", youtubeLoginCookieValue(cookie))

        val header = sapisidAuthorizationHeader(
            cookie = cookie,
            origin = YouTubeClient.ORIGIN_YOUTUBE_MUSIC,
            timestampSeconds = 1_700_000_000L,
        )
        assertNotNull(header)
        assertTrue(header!!.contains("SAPISIDHASH 1700000000_"))
        assertTrue(header.contains("SAPISID1PHASH 1700000000_"))
        assertTrue(header.contains("SAPISID3PHASH 1700000000_"))
    }

    @Test
    fun rotatingProxyClient_parsesAndRotatesAcrossPool() {
        RotatingProxyClient.setProxyUrls(
            "http://alice:secret@proxy1.example.com:8080, socks5://proxy2.example.com:1080",
        )
        assertEquals(2, RotatingProxyClient.activeCount())

        val first = RotatingProxyClient.current()!!
        assertEquals(Proxy.Type.HTTP, first.proxy.type())
        assertEquals("alice", first.username)
        assertEquals("secret", first.password)
        assertNotNull(first.basicAuthHeader)

        val second = RotatingProxyClient.rotate()!!
        assertEquals(Proxy.Type.SOCKS, second.proxy.type())
        assertNotEquals(first.rawUrl, second.rawUrl)

        val failover = RotatingProxyClient.markLastSelectedFailed()!!
        assertEquals(first.rawUrl, failover.rawUrl)
    }

    @Test
    fun authSessionStore_appendsGvsPoToken_andAppliesStreamBypass() {
        AuthSessionStore.applyAuthState(
            PlaybackAuthState(
                poTokenGvs = "myGvsToken",
                webClientPoTokenEnabled = true,
            ),
        )
        val signedUrl = AuthSessionStore.appendGvsPoToken(
            url = "https://rr1---sn-example.googlevideo.com/videoplayback?itag=251",
            client = YouTubeClient.WEB_REMIX,
        )
        assertTrue(signedUrl.contains("pot=myGvsToken"))

        val untouchedAndroid = AuthSessionStore.appendGvsPoToken(
            url = "https://rr1---sn-example.googlevideo.com/videoplayback?itag=251",
            client = YouTubeClient.ANDROID_VR_NO_AUTH,
        )
        assertFalse(untouchedAndroid.contains("pot="))

        AuthSessionStore.applyProxyConfiguration("http://proxy.example.com:8888")
        AuthSessionStore.streamBypassProxy = true
        val streamBuilder = AuthSessionStore.applyOkHttpSettings(OkHttpClient.Builder(), forStream = true)
        assertEquals(Proxy.NO_PROXY, streamBuilder.build().proxy)
    }

    @Test
    fun authSessionParsing_handlesMissingFieldsWithoutNullPointerException() {
        val emptyAccountMenu = AccountMenuResponse(actions = null)
        assertNull(AuthSessionParsing.parseAccountInfo(emptyAccountMenu))
        assertTrue(AuthSessionParsing.parseAccountChannels(emptyAccountMenu).isEmpty())

        val emptyBrowse = BrowseResponse(
            contents = null,
            continuationContents = null,
            onResponseReceivedActions = null,
            header = null,
            microformat = null,
            responseContext = ResponseContext(visitorData = "v", serviceTrackingParams = null),
            background = null,
        )
        val page = AuthSessionParsing.parseLibraryContinuation(emptyBrowse)
        assertTrue(page.items.isEmpty())
        assertNull(page.continuation)
    }
}
