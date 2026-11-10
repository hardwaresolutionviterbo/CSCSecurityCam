package com.example

import com.example.pairing.PairingPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingPayloadTest {

    @Test
    fun `parses full csc link`() {
        val info = PairingPayload.parse("csc://cam?ip=192.168.1.20&port=8765&id=cam_192.168.1.20")
        assertEquals("192.168.1.20", info?.ip)
        assertEquals(8765, info?.port)
        assertEquals("cam_192.168.1.20", info?.id)
    }

    @Test
    fun `defaults missing port and id`() {
        val info = PairingPayload.parse("csc://cam?ip=10.0.0.7")
        assertEquals("10.0.0.7", info?.ip)
        assertEquals(PairingPayload.DEFAULT_PORT, info?.port)
        assertNull(info?.id)
    }

    @Test
    fun `accepts ip and port pasted by hand`() {
        val info = PairingPayload.parse(" 192.168.1.44:9000 ")
        assertEquals("192.168.1.44", info?.ip)
        assertEquals(9000, info?.port)
    }

    @Test
    fun `accepts bare ip with default port`() {
        val info = PairingPayload.parse("192.168.1.44")
        assertEquals("192.168.1.44", info?.ip)
        assertEquals(8765, info?.port)
    }

    @Test
    fun `rejects invalid payloads`() {
        assertNull(PairingPayload.parse(""))
        assertNull(PairingPayload.parse("https://example.com"))
        assertNull(PairingPayload.parse("csc://cam?ip=999.1.1.1"))
        assertNull(PairingPayload.parse("csc://cam?ip=192.168.1.2&port=70000"))
        assertNull(PairingPayload.parse("csc://other?ip=192.168.1.2"))
        assertNull(PairingPayload.parse("not an address"))
    }

    @Test
    fun `validates ipv4 octets`() {
        assertTrue(PairingPayload.isValidIp("192.168.0.1"))
        assertTrue(PairingPayload.isValidIp("10.0.0.255"))
        assertFalse(PairingPayload.isValidIp("192.168.0.256"))
        assertFalse(PairingPayload.isValidIp("192.168.0"))
        assertFalse(PairingPayload.isValidIp("camera.local"))
    }

    @Test
    fun `build produces a scannable link that parses back`() {
        val link = PairingPayload.build("192.168.1.30", 8765, "cam_192.168.1.30")
        assertTrue(link.startsWith("csc://cam?ip=192.168.1.30&port=8765"))
        val parsed = PairingPayload.parse(link)
        assertEquals("192.168.1.30", parsed?.ip)
        assertEquals(8765, parsed?.port)
        assertEquals("cam_192.168.1.30", parsed?.id)
    }
}
