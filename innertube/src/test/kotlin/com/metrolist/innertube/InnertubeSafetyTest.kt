package com.metrolist.innertube

import com.metrolist.innertube.models.YouTubeLocale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class InnertubeSafetyTest {

    @Test
    fun newPipeExtractor_init_isIdempotentAndDoesNotThrow() {
        NewPipeExtractor.init()
        NewPipeExtractor.init()
    }

    @Test
    fun youtubeLocale_defaultsAndMutation_workCleanly() {
        YouTube.locale = YouTubeLocale(gl = "US", hl = "en")
        assertEquals("US", YouTube.locale.gl)
        assertEquals("en", YouTube.locale.hl)
        YouTube.cookie = null
        assertEquals(null, YouTube.cookie)
    }
}
