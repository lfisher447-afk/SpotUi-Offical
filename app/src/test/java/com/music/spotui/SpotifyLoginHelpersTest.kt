package com.music.spotui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.music.spotui.ui.screens.LoginUaProfile
import com.music.spotui.ui.screens.looksLikeSpotifyCookiePayload
import com.music.spotui.ui.screens.parseSpotifyCookiesFromText
import com.music.spotui.ui.screens.redactSecrets
import com.music.spotui.ui.screens.resolveLoginUserAgent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = MyApplication::class)
class SpotifyLoginHelpersTest {

    @Test
    fun loginUaProfile_defaultsToCleanMobileFirst() {
        assertEquals(LoginUaProfile.CLEAN_MOBILE, LoginUaProfile.entries.first())
    }

    @Test
    fun resolveLoginUserAgent_stripsWebViewTokensInCleanMobileProfile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cleanUa = resolveLoginUserAgent(context, LoginUaProfile.CLEAN_MOBILE)
        assertTrue(cleanUa.isNotBlank())
        assertFalse(cleanUa.contains("; wv", ignoreCase = true))
        assertFalse(cleanUa.contains("Version/", ignoreCase = true))

        val desktopUa = resolveLoginUserAgent(context, LoginUaProfile.DESKTOP_CHROME)
        assertTrue(desktopUa.contains("Windows NT"))
        assertTrue(desktopUa.contains("Chrome/"))
    }

    @Test
    fun parseSpotifyCookiesFromText_extractsAllFormats() {
        val (rawDc, rawKey) = parseSpotifyCookiesFromText("AQD_RAW_TOKEN_VALUE")
        assertEquals("AQD_RAW_TOKEN_VALUE", rawDc)
        assertEquals("", rawKey)

        val (hdrDc, hdrKey) = parseSpotifyCookiesFromText("Cookie: foo=bar; sp_dc=AQD_HDR; sp_key=KEY_123")
        assertEquals("AQD_HDR", hdrDc)
        assertEquals("KEY_123", hdrKey)

        val jsonExport = """[{"name":"sp_key","value":"J_KEY"},{"name":"sp_dc","value":"AQD_JSON"}]"""
        val (jsonDc, jsonKey) = parseSpotifyCookiesFromText(jsonExport)
        assertEquals("AQD_JSON", jsonDc)
        assertEquals("J_KEY", jsonKey)

        val jsonObjectExport = """{"sp_dc":"AQD_OBJ","sp_key":"OBJ_KEY"}"""
        val (objDc, objKey) = parseSpotifyCookiesFromText(jsonObjectExport)
        assertEquals("AQD_OBJ", objDc)
        assertEquals("OBJ_KEY", objKey)

        val netscape = ".spotify.com\tTRUE\t/\tTRUE\t1900000000\tsp_dc\tAQD_NETSCAPE"
        val (netDc, _) = parseSpotifyCookiesFromText(netscape)
        assertEquals("AQD_NETSCAPE", netDc)

        val encoded = "sp_dc%3DAQD_ENC%3B%20sp_key%3DENC_KEY"
        val (encDc, encKey) = parseSpotifyCookiesFromText(encoded)
        assertEquals("AQD_ENC", encDc)
        assertEquals("ENC_KEY", encKey)
    }

    @Test
    fun redactSecrets_masksCookiesBearerAndRawTokens() {
        val rawToken = "AQ" + "A".repeat(60)
        val input = "sp_dc=secret_dc_123; sp_key=secret_key_456 Authorization: Bearer tok_xyz_789 $rawToken"
        val redacted = redactSecrets(input)
        assertFalse(redacted.contains("secret_dc_123"))
        assertFalse(redacted.contains("secret_key_456"))
        assertFalse(redacted.contains("tok_xyz_789"))
        assertFalse(redacted.contains(rawToken))
        assertTrue(redacted.contains("<redacted>"))
    }

    @Test
    fun looksLikeSpotifyCookiePayload_validatesClipboardCandidates() {
        assertTrue(looksLikeSpotifyCookiePayload("sp_dc=AQD_TEST_VALUE"))
        assertTrue(looksLikeSpotifyCookiePayload("AQ" + "B".repeat(55)))
        assertFalse(looksLikeSpotifyCookiePayload("hello world"))
        assertFalse(looksLikeSpotifyCookiePayload("   "))
    }
}
