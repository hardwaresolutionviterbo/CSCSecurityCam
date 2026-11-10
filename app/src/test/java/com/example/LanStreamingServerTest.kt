package com.example

import android.graphics.Bitmap
import com.example.engine.ConnectionStatus
import com.example.engine.LanStreamClient
import com.example.engine.LanStreamingServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL

/**
 * Verifica il percorso dei fotogrammi tra il telefono videocamera e il monitor senza due
 * dispositivi fisici: il server LAN gira su 127.0.0.1 e viene interrogato come farebbe un browser
 * (/status, /snapshot, /live.mjpg), poi LanStreamClient si collega allo stesso server.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LanStreamingServerTest {

    private lateinit var scope: CoroutineScope
    private lateinit var server: LanStreamingServer

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        server = LanStreamingServer(port = PORT, scope = scope)
        server.start()
        val deadline = System.currentTimeMillis() + 5_000
        while (!server.stats.value.isRunning && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }
        assertTrue("LanStreamingServer non si è avviato", server.stats.value.isRunning)
    }

    @After
    fun tearDown() {
        server.stop()
        scope.cancel()
    }

    @Test
    fun `status reports non zero fps and snapshot returns the broadcast frame`() {
        val jpeg = testJpegFrame()
        broadcastFor(jpeg, 1_600)

        val (statusCode, statusBody) = httpGet("/status")
        assertEquals(200, statusCode)
        val fps = Regex("\"fps\":(\\d+)").find(statusBody)?.groupValues?.get(1)?.toInt() ?: -1
        assertTrue("fps atteso > 0, risposta: $statusBody", fps > 0)

        val (snapshotCode, snapshotBody) = httpGetBytes("/snapshot")
        assertEquals("lo snapshot deve trovare un fotogramma", 200, snapshotCode)
        assertArrayEquals(jpeg, snapshotBody)
    }

    @Test
    fun `live mjpg streams multipart frames`() {
        val jpeg = testJpegFrame()
        // Come sull'app: i fotogrammi continuano ad arrivare mentre il monitor legge il flusso.
        val streaming = Thread {
            val end = System.currentTimeMillis() + 10_000
            while (System.currentTimeMillis() < end && !Thread.currentThread().isInterrupted) {
                server.broadcastFrame(jpeg)
                Thread.sleep(20)
            }
        }
        streaming.isDaemon = true
        streaming.start()

        val connection = (URL("http://127.0.0.1:$PORT/live.mjpg").openConnection() as HttpURLConnection).apply {
            connectTimeout = 3_000
            readTimeout = 5_000
            requestMethod = "GET"
        }
        try {
            connection.connect()
            assertEquals(200, connection.responseCode)
            val received = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            val input = connection.inputStream
            val deadline = System.currentTimeMillis() + 8_000
            var text = ""
            while (System.currentTimeMillis() < deadline &&
                !(text.contains("--boundarydonotcross") &&
                    text.contains("Content-Type: image/jpeg"))
            ) {
                val read = try {
                    input.read(buffer)
                } catch (timeout: java.net.SocketTimeoutException) {
                    -1
                }
                if (read <= 0) break
                received.write(buffer, 0, read)
                text = received.toString("ISO-8859-1")
            }
            // Il content type multipart viaggia negli header HTTP, che HttpURLConnection consuma.
            assertEquals(
                "multipart/x-mixed-replace",
                connection.contentType?.substringBefore(';')?.trim()
            )
            assertTrue("parte MJPEG assente: $text", text.contains("--boundarydonotcross"))
            assertTrue("content type del fotogramma assente: $text", text.contains("Content-Type: image/jpeg"))
        } finally {
            connection.disconnect()
            streaming.interrupt()
        }
    }

    @Test
    fun `monitor client connects to the server and receives frames`() {
        val client = LanStreamClient(scope)
        try {
            client.connect("127.0.0.1", PORT)
            val deadline = System.currentTimeMillis() + 8_000
            while (System.currentTimeMillis() < deadline && client.streamStats.value.status != ConnectionStatus.CONNECTED) {
                Thread.sleep(50)
            }
            assertEquals(
                "errore del monitor: ${client.streamStats.value.errorMessage}",
                ConnectionStatus.CONNECTED,
                client.streamStats.value.status
            )

            val jpeg = testJpegFrame()
            val frameDeadline = System.currentTimeMillis() + 8_000
            while (System.currentTimeMillis() < frameDeadline && client.latestFrame.value == null) {
                server.broadcastFrame(jpeg)
                Thread.sleep(60)
            }
            assertTrue("il monitor non ha ricevuto fotogrammi", client.latestFrame.value != null)
        } finally {
            client.disconnect()
        }
    }

    @Test
    fun `a monitor that stops reading does not block the camera pipeline`() {
        // Come un telefono con la scheda Monitor aperta ma che non consuma piu il flusso: il buffer
        // TCP si riempie. Il thread della fotocamera non deve restare bloccato dentro la scrittura.
        val stalled = openStalledLiveClient()
        try {
            val frame = ByteArray(256 * 1024) { 7 }
            var slowest = 0L
            repeat(40) {
                val start = System.currentTimeMillis()
                server.broadcastFrame(frame)
                slowest = maxOf(slowest, System.currentTimeMillis() - start)
            }
            assertTrue("broadcastFrame ha bloccato il produttore per $slowest ms", slowest < 500)
        } finally {
            stalled.close()
        }
    }

    @Test
    fun `a stalled monitor does not stop frames reaching another monitor`() {
        val stalled = openStalledLiveClient()
        val client = LanStreamClient(scope)
        try {
            client.connect("127.0.0.1", PORT)
            val deadline = System.currentTimeMillis() + 8_000
            while (System.currentTimeMillis() < deadline && client.streamStats.value.status != ConnectionStatus.CONNECTED) {
                Thread.sleep(50)
            }
            assertEquals(ConnectionStatus.CONNECTED, client.streamStats.value.status)

            val jpeg = testJpegFrame()
            val frameDeadline = System.currentTimeMillis() + 8_000
            while (System.currentTimeMillis() < frameDeadline && client.latestFrame.value == null) {
                server.broadcastFrame(jpeg)
                Thread.sleep(60)
            }
            assertTrue("il secondo monitor non ha ricevuto fotogrammi", client.latestFrame.value != null)
        } finally {
            client.disconnect()
            stalled.close()
        }
    }

    /** Apre /live.mjpg senza leggere la risposta: il suo buffer si riempie e non drena piu. */
    private fun openStalledLiveClient(): Socket {
        val socket = Socket("127.0.0.1", PORT)
        socket.receiveBufferSize = 1_024
        socket.getOutputStream().write("GET /live.mjpg HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n".toByteArray())
        socket.getOutputStream().flush()
        val deadline = System.currentTimeMillis() + 5_000
        while (server.stats.value.connectedClients < 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
        assertTrue("client bloccato non registrato sul server", server.stats.value.connectedClients >= 1)
        return socket
    }

    private fun broadcastFor(frame: ByteArray, durationMs: Long) {
        val deadline = System.currentTimeMillis() + durationMs
        while (System.currentTimeMillis() < deadline) {
            server.broadcastFrame(frame)
            Thread.sleep(20)
        }
    }

    private fun testJpegFrame(): ByteArray {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        return out.toByteArray()
    }

    private fun httpGet(path: String): Pair<Int, String> {
        val (code, body) = httpGetBytes(path)
        return code to body.toString(Charsets.UTF_8)
    }

    private fun httpGetBytes(path: String): Pair<Int, ByteArray> {
        val connection = (URL("http://127.0.0.1:$PORT$path").openConnection() as HttpURLConnection).apply {
            connectTimeout = 3_000
            readTimeout = 5_000
            requestMethod = "GET"
        }
        return try {
            connection.connect()
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            code to (stream?.readBytes() ?: ByteArray(0))
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val PORT = 8765
    }
}
