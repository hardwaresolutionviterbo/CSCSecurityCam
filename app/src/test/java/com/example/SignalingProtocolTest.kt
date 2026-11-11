package com.example.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SignalingProtocolTest {

    @Test
    fun `join carries room and role`() {
        val parsed = SignalingProtocol.parse(
            SignalingProtocol.join("CSC-1234-5678", RemoteConfig.ROLE_CAMERA)
        )
        assertEquals(SignalingProtocol.JOIN, parsed?.type)
        assertEquals("CSC-1234-5678", parsed?.room)
        assertEquals("camera", parsed?.role)
    }

    @Test
    fun `sdp preserves newlines`() {
        val description = "v=0\r\no=- 1 1 IN IP4 127.0.0.1\r\ns=-\r\n"
        val parsed = SignalingProtocol.parse(SignalingProtocol.sdp("offer", description))
        assertEquals("offer", parsed?.sdpType)
        assertEquals(description, parsed?.sdp)
    }

    @Test
    fun `ice carries candidate and index`() {
        val candidate = "candidate:1 1 udp 2122260223 192.168.1.20 50000 typ host"
        val parsed = SignalingProtocol.parse(SignalingProtocol.ice(candidate, "0", 0))
        assertEquals(SignalingProtocol.ICE, parsed?.type)
        assertEquals(candidate, parsed?.candidate)
        assertEquals("0", parsed?.mid)
        assertEquals(0, parsed?.mlineIndex)
    }

    @Test
    fun `malformed payload is rejected`() {
        assertNull(SignalingProtocol.parse("not-json"))
        assertNull(SignalingProtocol.parse("{}"))
        assertNull(SignalingProtocol.parse(""))
        assertNull(SignalingProtocol.parse(null))
    }

    @Test
    fun `server errors expose their message`() {
        val parsed = SignalingProtocol.parse(
            """{"type":"error","message":"Stanza mancante"}"""
        )
        assertEquals(SignalingProtocol.ERROR, parsed?.type)
        assertEquals("Stanza mancante", parsed?.message)
    }
}
