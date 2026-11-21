package com.music.spotui

import com.music.spotui.ui.screens.WebUaMode
import com.music.spotui.ui.screens.buildCredentialAutofillJs
import com.music.spotui.ui.screens.buildDomErrorProbeJs
import com.music.spotui.ui.screens.isTrustedAuthHost
import com.music.spotui.ui.screens.maskEmailForLog
import com.music.spotui.ui.screens.parseSpotifyCookiesFromText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyLoginHelpersTest {

    @Test
    fun webUaMode_defaultsToSystemWebViewFirst() {
        assertEquals(WebUaMode.SYSTEM_WEBVIEW, WebUaMode.entries.first())
    }

    @Test
    fun buildCredentialAutofillJs_emailCode_neverMatchesSocialButtonsOrLooseContinueSubstring() {
        val js = buildCredentialAutofillJs(
            emailOrUsername = "listener@example.com",
            password = null,
            preferEmailCode = true,
        )

        // Must NOT fall back to substring indexOf('continue') which matches "Continue with Google"
        assertFalse(js.contains("indexOf('continue')"))
        // Must explicitly exclude social SSO providers & target Spotify login submit button
        assertTrue(js.contains("isExcludedBtn"))
        assertTrue(js.contains("combined.indexOf('google')"))
        assertTrue(js.contains("""button[data-testid="login-button"]"""))
        // Must clear previous run timer so re-invoking Email Code works on same page context
        assertTrue(js.contains("__spotuiAutofillTimer"))
        assertTrue(js.contains("without password"))
    }

    @Test
    fun buildDomErrorProbeJs_filtersInvisibleAndTinyRecaptchaIframes() {
        val js = buildDomErrorProbeJs()
        assertTrue(js.contains("rect.width > 120 && rect.height > 120"))
        assertTrue(js.contains("style.display === 'none'"))
        assertTrue(js.contains("style.visibility === 'hidden'"))
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

        val netscape = ".spotify.com\tTRUE\t/\tTRUE\t1900000000\tsp_dc\tAQD_NETSCAPE"
        val (netDc, _) = parseSpotifyCookiesFromText(netscape)
        assertEquals("AQD_NETSCAPE", netDc)
    }

    @Test
    fun isTrustedAuthHost_allowsExactOrSubdomainsOnly() {
        assertTrue(isTrustedAuthHost("accounts.spotify.com"))
        assertTrue(isTrustedAuthHost("spotify.com"))
        assertTrue(isTrustedAuthHost("accounts.google.com"))
        assertTrue(isTrustedAuthHost("www.recaptcha.net"))
        assertFalse(isTrustedAuthHost("evilspotify.com"))
        assertFalse(isTrustedAuthHost("notgoogle.com"))
        assertFalse(isTrustedAuthHost(""))
        assertFalse(isTrustedAuthHost(null))
    }

    @Test
    fun maskEmailForLog_redactsLocalPart() {
        assertEquals("us***@example.com", maskEmailForLog("user@example.com"))
        assertEquals("<empty>", maskEmailForLog("   "))
    }
}
