package com.music.spotui.util

import org.junit.Assert.*
import org.junit.Test

class StartupSanityTest {

    @Test
    fun testDeepLinkParsingResilience() {
        assertNull(SpotifyDeepLink.parseUrlString(null))
        assertNull(SpotifyDeepLink.parseUrlString(""))
        assertNull(SpotifyDeepLink.parseUrlString("random invalid link"))
        assertNull(SpotifyDeepLink.parseUrlString("https://example.com"))
    }
}
