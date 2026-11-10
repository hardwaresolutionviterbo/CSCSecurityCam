package com.example

import com.example.media.StreamQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StreamQualityTest {

    @Test
    fun `labels round trip through fromLabel`() {
        assertEquals(StreamQuality.AUTO, StreamQuality.fromLabel("Auto"))
        assertEquals(StreamQuality.FULL_HD, StreamQuality.fromLabel("1080p (Full HD)"))
        assertEquals(StreamQuality.HD, StreamQuality.fromLabel("720p (HD)"))
        assertEquals(StreamQuality.SD, StreamQuality.fromLabel("480p (SD)"))
    }

    @Test
    fun `unknown or legacy labels fall back to auto`() {
        assertEquals(StreamQuality.AUTO, StreamQuality.fromLabel("2K (QHD)"))
        assertEquals(StreamQuality.AUTO, StreamQuality.fromLabel(null))
        assertEquals(StreamQuality.AUTO, StreamQuality.fromLabel(""))
    }

    @Test
    fun `auto is the 720p compromise and drops to 480p when the link is slow`() {
        assertEquals(1280 to 720, StreamQuality.AUTO.dimensions(autoFallback = false))
        assertEquals(640 to 480, StreamQuality.AUTO.dimensions(autoFallback = true))

        // Le qualità scelte a mano non vengono mai ridotte dal fallback.
        assertEquals(1920 to 1080, StreamQuality.FULL_HD.dimensions(autoFallback = true))
        assertEquals(1280 to 720, StreamQuality.HD.dimensions(autoFallback = true))
    }

    @Test
    fun `options are ordered from auto to the lightest stream`() {
        val labels = StreamQuality.options().map { it.label }
        assertEquals(listOf("Auto", "1080p (Full HD)", "720p (HD)", "480p (SD)"), labels)
        assertNotEquals(StreamQuality.AUTO.targetWidth, StreamQuality.FULL_HD.targetWidth)
    }
}
