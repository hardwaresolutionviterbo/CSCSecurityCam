package com.example.engine

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

data class ServerStats(
    val isRunning: Boolean = false,
    val port: Int = 8765,
    val connectedClients: Int = 0,
    val currentFps: Int = 0,
    val totalFramesSent: Long = 0L,
    val lastError: String? = null
)

interface RemoteControlListener {
    fun onTorchCommand(enabled: Boolean)
    fun onSwitchLensCommand()
    fun onZoomCommand(ratio: Float)
    fun onTriggerSnapshotCommand()

    /** Il centro di controllo chiede una qualità di flusso; solo la telecamera la applica. */
    fun onQualityCommand(quality: String) {}

    /** Il centro di controllo chiede di avviare o fermare una clip sulla telecamera. */
    fun onRecordCommand(start: Boolean) {}
}

class LanStreamingServer(
    private val port: Int = 8765,
    private val scope: CoroutineScope
) {
    private val tag = "LanStreamingServer"

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val clientStreams = ConcurrentHashMap<String, ClientSink>()
    private val audioClientStreams = ConcurrentHashMap<String, OutputStream>()

    private val _stats = MutableStateFlow(ServerStats(port = port))
    val stats: StateFlow<ServerStats> = _stats.asStateFlow()

    var remoteControlListener: RemoteControlListener? = null
    var onIncomingVoiceChunk: ((ByteArray) -> Unit)? = null

    private val frameCount = AtomicInteger(0)
    private val totalFrames = AtomicLong(0L)
    private var lastFpsTimestamp = System.currentTimeMillis()

    @Volatile
    private var latestFrame: ByteArray? = null

    /** Timestamp dell'ultimo movimento rilevato: il centro di controllo lo legge da /status. */
    @Volatile
    private var lastMotionAt: Long = 0L

    @Volatile
    private var qualityLabel: String = "Auto"

    fun start() {
        if (serverJob?.isActive == true) return

        serverJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port).apply {
                    reuseAddress = true
                }
                _stats.value = _stats.value.copy(isRunning = true, lastError = null)
                Log.i(tag, "LanStreamingServer started on port $port")

                // Start FPS calculation coroutine
                launch {
                    while (isActive) {
                        kotlinx.coroutines.delay(1000)
                        val now = System.currentTimeMillis()
                        val elapsed = (now - lastFpsTimestamp).coerceAtLeast(1)
                        val count = frameCount.getAndSet(0)
                        val fps = ((count * 1000L) / elapsed).toInt()
                        lastFpsTimestamp = now
                        _stats.value = _stats.value.copy(
                            currentFps = fps,
                            connectedClients = clientStreams.size,
                            totalFramesSent = totalFrames.get()
                        )
                    }
                }

                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    launch(Dispatchers.IO) {
                        handleClient(socket)
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(tag, "Error in streaming server", e)
                    _stats.value = _stats.value.copy(isRunning = false, lastError = e.message)
                }
            }
        }
    }

    private fun handleClient(socket: Socket) {
        val clientId = "${socket.inetAddress.hostAddress}:${socket.port}"
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val out = socket.getOutputStream()
            val requestLine = reader.readLine() ?: return

            Log.d(tag, "Client request: $requestLine from $clientId")
            val parts = requestLine.split(" ")
            val path = if (parts.size >= 2) parts[1] else "/"

            when {
                path.startsWith("/live.mjpg") || path.startsWith("/stream") -> {
                    // Send HTTP Multipart MJPEG header
                    val header = (
                        "HTTP/1.1 200 OK\r\n" +
                        "Connection: close\r\n" +
                        "Server: CSCSecurityCam-Server/1.0\r\n" +
                        "Cache-Control: no-store, no-cache, must-revalidate, pre-check=0, post-check=0, max-age=0\r\n" +
                        "Pragma: no-cache\r\n" +
                        "Content-Type: multipart/x-mixed-replace; boundary=--boundarydonotcross\r\n\r\n"
                    )
                    out.write(header.toByteArray(Charsets.UTF_8))
                    out.flush()

                    // Register client for live frame broadcast. Frames are written by the client's
                    // own coroutine, so a monitor that cannot keep up never blocks this thread.
                    // ?fps=N limita i frame inviati a questo client (la griglia chiede 12 fps).
                    val liveQuery = if (path.contains("?")) path.substringAfter("?") else ""
                    val requestedFps = liveQuery.split("&")
                        .firstOrNull { it.startsWith("fps=") }
                        ?.substringAfter("fps=")
                        ?.toIntOrNull()
                        ?: 0
                    val sink = ClientSink(out, fpsLimit = requestedFps)
                    clientStreams[clientId] = sink
                    _stats.value = _stats.value.copy(connectedClients = clientStreams.size)

                    // If we have a cached frame, send it immediately
                    latestFrame?.let { enqueueFrame(clientId, sink, it) }

                    // Keep connection open until socket disconnects
                    while (!socket.isClosed && socket.isConnected) {
                        val check = socket.getInputStream().read()
                        if (check == -1) break
                    }
                }

                path.startsWith("/control") -> {
                    // Parse query parameters
                    val query = if (path.contains("?")) path.substringAfter("?") else ""
                    handleControlQuery(query)

                    val jsonResp = "{\"status\":\"ok\",\"timestamp\":${System.currentTimeMillis()}}"
                    val resp = (
                        "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: application/json; charset=utf-8\r\n" +
                        "Content-Length: ${jsonResp.toByteArray().size}\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Connection: close\r\n\r\n$jsonResp"
                    )
                    out.write(resp.toByteArray(Charsets.UTF_8))
                    out.flush()
                    socket.close()
                }

                path.startsWith("/status") -> {
                    val safeQuality = qualityLabel.replace("\"", "")
                    val jsonResp = "{\"server\":\"CSCSecurityCam\",\"status\":\"online\",\"fps\":${_stats.value.currentFps},\"clients\":${clientStreams.size},\"motionAt\":$lastMotionAt,\"quality\":\"$safeQuality\"}"
                    val resp = (
                        "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: application/json; charset=utf-8\r\n" +
                        "Content-Length: ${jsonResp.toByteArray().size}\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Connection: close\r\n\r\n$jsonResp"
                    )
                    out.write(resp.toByteArray(Charsets.UTF_8))
                    out.flush()
                    socket.close()
                }

                path.startsWith("/snapshot") -> {
                    val frame = latestFrame
                    if (frame != null) {
                        val resp = (
                            "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: image/jpeg\r\n" +
                            "Content-Length: ${frame.size}\r\n" +
                            "Connection: close\r\n\r\n"
                        )
                        out.write(resp.toByteArray(Charsets.UTF_8))
                        out.write(frame)
                        out.flush()
                    } else {
                        val resp = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n"
                        out.write(resp.toByteArray(Charsets.UTF_8))
                        out.flush()
                    }
                    socket.close()
                }

                path.startsWith("/audio.pcm") -> {
                    val header = (
                        "HTTP/1.1 200 OK\r\n" +
                        "Connection: close\r\n" +
                        "Server: CSCSecurityCam-Audio/1.0\r\n" +
                        "Cache-Control: no-store, no-cache, must-revalidate\r\n" +
                        "Content-Type: audio/L16;rate=16000;channels=1\r\n\r\n"
                    )
                    out.write(header.toByteArray(Charsets.UTF_8))
                    out.flush()

                    audioClientStreams[clientId] = out
                    while (!socket.isClosed && socket.isConnected) {
                        val check = socket.getInputStream().read()
                        if (check == -1) break
                    }
                }

                path.startsWith("/talk") -> {
                    // Receive Push-to-Talk audio stream from monitor
                    var contentLength = 0
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        if (line!!.isEmpty()) break
                        if (line!!.lowercase().startsWith("content-length:")) {
                            contentLength = line!!.substringAfter(":").trim().toIntOrNull() ?: 0
                        }
                    }

                    val rawInput = socket.getInputStream()
                    val talkBuffer = ByteArray(1280)
                    var bytesReceived = 0

                    if (contentLength > 0) {
                        while (bytesReceived < contentLength) {
                            val toRead = minOf(talkBuffer.size, contentLength - bytesReceived)
                            val read = rawInput.read(talkBuffer, 0, toRead)
                            if (read <= 0) break
                            val chunk = talkBuffer.copyOf(read)
                            onIncomingVoiceChunk?.invoke(chunk)
                            bytesReceived += read
                        }
                    } else {
                        while (!socket.isClosed) {
                            val read = rawInput.read(talkBuffer)
                            if (read <= 0) break
                            val chunk = talkBuffer.copyOf(read)
                            onIncomingVoiceChunk?.invoke(chunk)
                        }
                    }

                    val resp = "HTTP/1.1 200 OK\r\nConnection: close\r\nContent-Length: 0\r\n\r\n"
                    out.write(resp.toByteArray(Charsets.UTF_8))
                    out.flush()
                    socket.close()
                }

                else -> {
                    val html = "<html><head><title>CSC SecurityCam</title></head><body style='background:#0F172A;color:#E2E8F0;font-family:sans-serif;'><h2>CSC SecurityCam Stream Server</h2><p>Streaming LAN attivo</p><p><a style='color:#FF6A00;' href='/live.mjpg'>Apri flusso /live.mjpg</a></p></body></html>"
                    val resp = (
                        "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/html; charset=utf-8\r\n" +
                        "Content-Length: ${html.toByteArray().size}\r\n" +
                        "Connection: close\r\n\r\n$html"
                    )
                    out.write(resp.toByteArray(Charsets.UTF_8))
                    out.flush()
                    socket.close()
                }
            }
        } catch (e: Exception) {
            // Client closed connection or read timed out
        } finally {
            clientStreams.remove(clientId)
            audioClientStreams.remove(clientId)
            _stats.value = _stats.value.copy(connectedClients = clientStreams.size)
            try { socket.close() } catch (ignored: Exception) {}
        }
    }

    private fun handleControlQuery(query: String) {
        val params = query.split("&").mapNotNull {
            val kv = it.split("=")
            if (kv.size == 2) kv[0] to decode(kv[1]) else null
        }.toMap()

        val action = params["action"] ?: params["cmd"]
        when (action) {
            "torch" -> {
                val enabled = params["val"] == "1" || params["val"] == "true" || params["val"] == "on"
                remoteControlListener?.onTorchCommand(enabled)
            }
            "lens" -> {
                remoteControlListener?.onSwitchLensCommand()
            }
            "zoom" -> {
                val ratio = params["val"]?.toFloatOrNull() ?: 1.0f
                remoteControlListener?.onZoomCommand(ratio)
            }
            "snapshot" -> {
                remoteControlListener?.onTriggerSnapshotCommand()
            }
            "quality" -> {
                remoteControlListener?.onQualityCommand(params["val"] ?: "Auto")
            }
            "record" -> {
                val value = params["val"]?.lowercase()
                remoteControlListener?.onRecordCommand(value != "0" && value != "stop")
            }
        }
    }

    private fun decode(value: String): String = try {
        java.net.URLDecoder.decode(value, "UTF-8")
    } catch (e: Exception) {
        value
    }

    /** La telecamera pubblica qui un movimento: il monitor lo trova nel prossimo /status. */
    fun publishMotion() {
        lastMotionAt = System.currentTimeMillis()
    }

    fun publishQuality(label: String) {
        qualityLabel = label
    }

    fun broadcastFrame(jpegBytes: ByteArray) {
        latestFrame = jpegBytes
        frameCount.incrementAndGet()
        totalFrames.incrementAndGet()

        // Never write to a client socket from here: this runs on the camera analyzer thread. A
        // monitor that stops reading would fill its TCP buffer and block that thread, freezing the
        // video and making the client hit its read timeout (Online -> Offline -> Riconnessione).
        val now = System.currentTimeMillis()
        for ((clientId, sink) in clientStreams) {
            if (!sink.shouldSend(now)) continue
            enqueueFrame(clientId, sink, jpegBytes)
        }
    }

    private fun enqueueFrame(clientId: String, sink: ClientSink, frame: ByteArray) {
        if (sink.offer(frame)) {
            scope.launch(Dispatchers.IO) { drainFrames(clientId, sink) }
        }
    }

    private fun drainFrames(clientId: String, sink: ClientSink) {
        try {
            while (true) {
                val frame = sink.takePending() ?: break
                sendFrameToStream(sink.out, frame)
            }
        } catch (e: Exception) {
            removeClient(clientId)
        } finally {
            if (sink.finishDrain()) {
                scope.launch(Dispatchers.IO) { drainFrames(clientId, sink) }
            }
        }
    }

    private fun removeClient(clientId: String) {
        clientStreams.remove(clientId)
        _stats.value = _stats.value.copy(connectedClients = clientStreams.size)
    }

    /**
     * Write buffer of a single monitor: only the newest frame is kept. If the monitor is slower
     * than the camera its older frames are dropped instead of queueing up or blocking the sender.
     */
    private class ClientSink(val out: OutputStream, fpsLimit: Int = 0) {
        private val pending = AtomicReference<ByteArray?>(null)
        private val draining = AtomicBoolean(false)

        // 0 = nessun limite: il flusso singolo resta a piena frequenza.
        private val minIntervalMs = if (fpsLimit > 0) {
            1000L / fpsLimit.coerceIn(1, 60)
        } else {
            0L
        }
        private var lastSentAt = 0L

        fun shouldSend(now: Long): Boolean {
            if (minIntervalMs <= 0L) return true
            if (now - lastSentAt < minIntervalMs) return false
            lastSentAt = now
            return true
        }

        /** Stores [frame] as the latest one to send; returns true if the caller must start a drain. */
        fun offer(frame: ByteArray): Boolean {
            pending.set(frame)
            return draining.compareAndSet(false, true)
        }

        fun takePending(): ByteArray? = pending.getAndSet(null)

        /** Finishes a drain; returns true when frames arrived meanwhile and need a new drain. */
        fun finishDrain(): Boolean {
            draining.set(false)
            return pending.get() != null && draining.compareAndSet(false, true)
        }
    }

    private fun sendFrameToStream(out: OutputStream, jpegBytes: ByteArray) {
        val frameHeader = (
            "--boundarydonotcross\r\n" +
            "Content-Type: image/jpeg\r\n" +
            "Content-Length: ${jpegBytes.size}\r\n\r\n"
        )
        out.write(frameHeader.toByteArray(Charsets.UTF_8))
        out.write(jpegBytes)
        out.write("\r\n".toByteArray(Charsets.UTF_8))
        out.flush()
    }

    fun broadcastAudio(pcmBytes: ByteArray) {
        if (audioClientStreams.isEmpty()) return
        val deadClients = mutableListOf<String>()
        for ((clientId, stream) in audioClientStreams) {
            try {
                stream.write(pcmBytes)
                stream.flush()
            } catch (e: Exception) {
                deadClients.add(clientId)
            }
        }
        if (deadClients.isNotEmpty()) {
            deadClients.forEach { audioClientStreams.remove(it) }
        }
    }

    fun stop() {
        try {
            serverSocket?.close()
        } catch (ignored: Exception) {}
        serverJob?.cancel()
        clientStreams.clear()
        audioClientStreams.clear()
        _stats.value = _stats.value.copy(isRunning = false, connectedClients = 0, currentFps = 0)
        Log.i(tag, "LanStreamingServer stopped")
    }
}
