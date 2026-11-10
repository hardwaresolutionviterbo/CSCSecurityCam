package com.example

import com.example.pairing.CameraProbe
import com.example.pairing.ProbeResult
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

class CameraProbeTest {

    private fun serverWithBody(body: String, status: Int = 200): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/status") { exchange ->
            val payload = body.toByteArray()
            exchange.sendResponseHeaders(status, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        server.start()
        return server
    }

    @Test
    fun `reachable when status answers with camera json`() = runBlocking {
        val server = serverWithBody(
            """{"status":"streaming","fps":24,"clients":2,"motionAt":1700000000000,"quality":"720p (HD)"}"""
        )
        try {
            val result = CameraProbe.probe("127.0.0.1", server.address.port, timeoutMs = 2_000)
            assertTrue("atteso Reachable, ottenuto $result", result is ProbeResult.Reachable)
            result as ProbeResult.Reachable
            assertEquals(24, result.fps)
            assertEquals(2, result.clients)
            assertEquals(1_700_000_000_000L, result.motionAt)
            assertEquals("720p (HD)", result.quality)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `unexpected when something else answers`() = runBlocking {
        val server = serverWithBody("hello, non sono una telecamera")
        try {
            val result = CameraProbe.probe("127.0.0.1", server.address.port, timeoutMs = 2_000)
            assertTrue("atteso Unexpected, ottenuto $result", result is ProbeResult.Unexpected)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `refused when the port is closed`() = runBlocking {
        val socket = ServerSocket(0)
        val closedPort = socket.localPort
        socket.close()

        val result = CameraProbe.probe("127.0.0.1", closedPort, timeoutMs = 800)
        assertTrue("atteso Refused, ottenuto $result", result is ProbeResult.Refused)
    }

    @Test
    fun `timeout when the server never answers`() = runBlocking {
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort
        thread(isDaemon = true) {
            try {
                serverSocket.accept().use { Thread.sleep(1_500) }
            } catch (e: Exception) {
                // Il test chiude il socket alla fine.
            }
        }

        try {
            val result = CameraProbe.probe("127.0.0.1", port, timeoutMs = 300)
            assertTrue("atteso Timeout, ottenuto $result", result is ProbeResult.Timeout)
        } finally {
            serverSocket.close()
        }
    }
}
