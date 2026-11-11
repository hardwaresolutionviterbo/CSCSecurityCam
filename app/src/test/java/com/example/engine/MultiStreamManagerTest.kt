package com.example.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiStreamManagerTest {

    private fun target(id: String, ip: String = "192.168.1.$id", port: Int = 8765) =
        GridTarget(id = id, ip = ip, port = port)

    @Test
    fun `opens only the new cameras and keeps the existing ones`() {
        val current = mapOf("a" to target("a"), "b" to target("b"))
        val plan = GridStreams.plan(current, listOf(target("a"), target("b"), target("c")))

        assertTrue(plan.close.isEmpty())
        assertEquals(listOf("c"), plan.open.map { it.id })
    }

    @Test
    fun `closes the tiles that are no longer visible`() {
        val current = mapOf("a" to target("a"), "b" to target("b"), "c" to target("c"))
        val plan = GridStreams.plan(current, listOf(target("a"), target("b")))

        assertEquals(listOf("c"), plan.close)
        assertTrue(plan.open.isEmpty())
    }

    @Test
    fun `a camera that changed address is reopened`() {
        val current = mapOf("a" to target("a", ip = "192.168.1.10"))
        val plan = GridStreams.plan(current, listOf(target("a", ip = "192.168.1.20")))

        assertTrue(plan.close.isEmpty())
        assertEquals(listOf("a"), plan.open.map { it.id })
    }

    @Test
    fun `never opens more than four streams`() {
        val targets = (1..6).map { target(it.toString()) }
        val plan = GridStreams.plan(emptyMap(), targets)

        assertEquals(GridStreams.MAX_STREAMS, plan.open.size)
    }

    @Test
    fun `duplicate ids are collapsed`() {
        val plan = GridStreams.plan(emptyMap(), listOf(target("a"), target("a")))

        assertEquals(1, plan.open.size)
    }

    @Test
    fun `scaled size keeps the aspect ratio under the maximum side`() {
        assertEquals(640 to 360, GridSizing.scaledSize(1920, 1080, 640))
        // In verticale il lato lungo è l'altezza: resta 640, la larghezza scende a 360.
        assertEquals(360 to 640, GridSizing.scaledSize(1080, 1920, 640))
        assertEquals(320 to 240, GridSizing.scaledSize(320, 240, 640))
    }

    @Test
    fun `sample size halves until the decoded frame fits`() {
        assertEquals(1, GridSizing.sampleSize(640, 480, 640))
        assertEquals(2, GridSizing.sampleSize(1920, 1080, 640))
        assertEquals(4, GridSizing.sampleSize(3840, 2160, 640))
    }
}
