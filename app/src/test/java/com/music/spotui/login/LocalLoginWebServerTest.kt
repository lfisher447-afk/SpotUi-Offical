package com.music.spotui.login

import com.music.spotui.ui.screens.SpotifyLogin.page.LocalLoginSubmission
import com.music.spotui.ui.screens.SpotifyLogin.page.LocalLoginWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLoginWebServerTest {

    @Test
    fun servesHtmlPortalAndEnforcesCsrfAcrossAllEndpoints() {
        val csrf = "nonce-12345"
        val received = mutableListOf<LocalLoginSubmission>()

        val indexResp = LocalLoginWebServer.routeRequest(
            method = "GET",
            path = "/",
            headers = emptyMap(),
            body = "",
            expectedCsrf = csrf,
        )
        assertEquals(200, indexResp.statusCode)
        assertTrue(indexResp.body.contains(csrf))

        val badCsrfResp = LocalLoginWebServer.routeRequest(
            method = "POST",
            path = "/api/spotify/cookie",
            headers = mapOf("x-spotui-csrf" to "wrong"),
            body = """{"payload":"sp_dc=abc"}""",
            expectedCsrf = csrf,
            onSubmission = { received.add(it) },
        )
        assertEquals(403, badCsrfResp.statusCode)
        assertTrue(received.isEmpty())

        val spotifyResp = LocalLoginWebServer.routeRequest(
            method = "POST",
            path = "/api/spotify/cookie",
            headers = mapOf("x-spotui-csrf" to csrf),
            body = """{"payload":"sp_dc=AQTestCookie123; sp_key=Key456"}""",
            expectedCsrf = csrf,
            onSubmission = { received.add(it) },
        )
        assertEquals(200, spotifyResp.statusCode)
        assertTrue(received.last() is LocalLoginSubmission.SpotifyCookie)

        val ytResp = LocalLoginWebServer.routeRequest(
            method = "POST",
            path = "/api/ytmusic/cookie",
            headers = mapOf("x-spotui-csrf" to csrf),
            body = """{"payload":"SAPISID=abc; SID=def; HSID=ghi"}""",
            expectedCsrf = csrf,
            onSubmission = { received.add(it) },
        )
        assertEquals(200, ytResp.statusCode)
        assertTrue(received.last() is LocalLoginSubmission.YouTubeCookie)

        val deezerResp = LocalLoginWebServer.routeRequest(
            method = "POST",
            path = "/api/deezer/arl",
            headers = mapOf("x-spotui-csrf" to csrf),
            body = """{"payload":"0123456789abcdef0123456789abcdef0123456789abcdef"}""",
            expectedCsrf = csrf,
            onSubmission = { received.add(it) },
        )
        assertEquals(200, deezerResp.statusCode)
        assertTrue(received.last() is LocalLoginSubmission.DeezerArl)
    }
}
