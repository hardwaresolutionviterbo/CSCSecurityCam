package it.cscsecuritycam.network

import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger

class MjpegStreamServer : NanoHTTPD(PORT) {
    @Volatile private var latestFrame: ByteArray? = null
    private var frameVersion: Long = 0
    private val frameLock = Object()
    @Volatile private var accessToken: String = newToken()
    private val connectedMonitors = AtomicInteger(0)

    fun hasConnectedMonitor(): Boolean = connectedMonitors.get() > 0

    fun token(): String = accessToken

    fun rotateToken(): String {
        accessToken = newToken()
        return accessToken
    }

    fun publishFrame(jpeg: ByteArray) {
        latestFrame = jpeg
        synchronized(frameLock) {
            frameVersion += 1
            frameLock.notifyAll()
        }
    }

    override fun serve(session: IHTTPSession): Response {
        return when (session.uri) {
            "/", "/health" -> newFixedLengthResponse(
                Response.Status.OK,
                "text/plain; charset=utf-8",
                "CSCSecurityCam LAN stream ready"
            )
            "/stream.mjpg" -> {
                val supplied = session.parameters["token"]?.firstOrNull()
                if (supplied == null || supplied != accessToken) {
                    newFixedLengthResponse(Response.Status.UNAUTHORIZED, "text/plain", "Invalid stream token")
                } else {
                    newChunkedResponse(
                        Response.Status.OK,
                        "multipart/x-mixed-replace; boundary=frame",
                        MjpegInputStream()
                    ).apply {
                        addHeader("Cache-Control", "no-store, no-cache, must-revalidate")
                        addHeader("Pragma", "no-cache")
                        addHeader("Connection", "close")
                    }
                }
            }
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
        }
    }

    private inner class MjpegInputStream : InputStream() {
        private val counted = java.util.concurrent.atomic.AtomicBoolean(true).also { connectedMonitors.incrementAndGet() }
        private var seenVersion = -1L
        private var current = ByteArray(0)
        private var offset = 0
        @Volatile private var closed = false

        override fun read(): Int {
            val one = ByteArray(1)
            val count = read(one, 0, 1)
            return if (count < 0) -1 else one[0].toInt() and 0xff
        }

        override fun read(buffer: ByteArray, off: Int, len: Int): Int {
            if (closed) return -1
            if (len == 0) return 0
            while (offset >= current.size) {
                val next = awaitNextFrame()
                if (next == null) {
                    if (closed) return -1
                    continue
                }
                current = next
                offset = 0
            }
            val count = minOf(len, current.size - offset)
            System.arraycopy(current, offset, buffer, off, count)
            offset += count
            return count
        }

        private fun awaitNextFrame(): ByteArray? {
            synchronized(frameLock) {
                while (!closed && frameVersion <= seenVersion) {
                    try { frameLock.wait(1500) } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return null
                    }
                    if (frameVersion <= seenVersion) return null
                }
                if (closed) return null
                val frame = latestFrame ?: return null
                seenVersion = frameVersion
                val header = (
                    "--frame\r\n" +
                    "Content-Type: image/jpeg\r\n" +
                    "Content-Length: ${frame.size}\r\n\r\n"
                ).toByteArray(StandardCharsets.US_ASCII)
                val tail = "\r\n".toByteArray(StandardCharsets.US_ASCII)
                return ByteArray(header.size + frame.size + tail.size).also {
                    System.arraycopy(header, 0, it, 0, header.size)
                    System.arraycopy(frame, 0, it, header.size, frame.size)
                    System.arraycopy(tail, 0, it, header.size + frame.size, tail.size)
                }
            }
        }

        override fun close() {
            closed = true
            if (counted.compareAndSet(true, false)) connectedMonitors.decrementAndGet()
            synchronized(frameLock) { frameLock.notifyAll() }
        }
    }

    companion object {
        const val PORT = 8765

        private fun newToken(): String {
            val bytes = ByteArray(18)
            SecureRandom().nextBytes(bytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }
}
