package com.example.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

data class ClientStreamStats(
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val currentFps: Int = 0,
    val resolution: String = "1920 × 1080",
    val latencyMs: Long = 0L,
    val errorMessage: String? = null
)

class LanStreamClient(
    private val scope: CoroutineScope
) {
    private val tag = "LanStreamClient"

    private var streamJob: Job? = null
    private val _streamStats = MutableStateFlow(ClientStreamStats())
    val streamStats: StateFlow<ClientStreamStats> = _streamStats.asStateFlow()

    private val _latestFrame = MutableStateFlow<Bitmap?>(null)
    val latestFrame: StateFlow<Bitmap?> = _latestFrame.asStateFlow()

    private var targetIp: String = ""
    private var targetPort: Int = 8765

    private var frameCounter = 0
    private var lastFpsCalcTime = System.currentTimeMillis()

    fun connect(ip: String, port: Int = 8765) {
        disconnect()
        targetIp = ip
        targetPort = port

        streamJob = scope.launch(Dispatchers.IO) {
            var retryDelay = 1000L
            while (isActive) {
                _streamStats.value = _streamStats.value.copy(
                    status = if (retryDelay > 1000L) ConnectionStatus.RECONNECTING else ConnectionStatus.CONNECTING,
                    errorMessage = null
                )

                try {
                    val url = URL("http://$targetIp:$targetPort/live.mjpg")
                    val connection = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 4000
                        readTimeout = 8000
                        requestMethod = "GET"
                        doInput = true
                    }

                    connection.connect()
                    if (connection.responseCode == 200) {
                        _streamStats.value = _streamStats.value.copy(
                            status = ConnectionStatus.CONNECTED,
                            errorMessage = null
                        )
                        retryDelay = 1000L // Reset backoff on success

                        val inputStream = BufferedInputStream(connection.inputStream)
                        readMjpegStream(inputStream)
                    } else {
                        throw Exception("HTTP ${connection.responseCode} ${connection.responseMessage}")
                    }
                } catch (e: Exception) {
                    if (!isActive) break
                    Log.w(tag, "Connection failed to $targetIp:$targetPort - ${e.message}")
                    _streamStats.value = _streamStats.value.copy(
                        status = ConnectionStatus.ERROR,
                        errorMessage = e.message ?: "Connessione fallita",
                        currentFps = 0
                    )
                    delay(retryDelay)
                    retryDelay = (retryDelay * 1.5).toLong().coerceAtMost(8000L)
                }
            }
        }
    }

    private suspend fun readMjpegStream(input: InputStream) = withContext(Dispatchers.IO) {
        val boundary = "--boundarydonotcross"
        val buffer = ByteArray(4096)
        var frameBuffer = ByteArrayOutputStream(64 * 1024)

        var inHeaders = false
        var contentLength = -1

        // Start FPS reporting coroutine
        val fpsJob = scope.launch {
            while (isActive) {
                delay(1000)
                val now = System.currentTimeMillis()
                val elapsed = (now - lastFpsCalcTime).coerceAtLeast(1)
                val fps = ((frameCounter * 1000L) / elapsed).toInt()
                frameCounter = 0
                lastFpsCalcTime = now
                _streamStats.value = _streamStats.value.copy(currentFps = fps)
            }
        }

        try {
            while (isActive) {
                val headerLine = readLine(input) ?: break

                if (headerLine.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = headerLine.substringAfter(":").trim().toIntOrNull() ?: -1
                } else if (headerLine.isEmpty()) {
                    // Empty line means headers ended, next bytes are JPEG body
                    if (contentLength > 0) {
                        val jpegBytes = ByteArray(contentLength)
                        var bytesRead = 0
                        while (bytesRead < contentLength) {
                            val r = input.read(jpegBytes, bytesRead, contentLength - bytesRead)
                            if (r == -1) break
                            bytesRead += r
                        }

                        if (bytesRead == contentLength) {
                            val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                            if (bitmap != null) {
                                _latestFrame.value = bitmap
                                frameCounter++
                                _streamStats.value = _streamStats.value.copy(
                                    resolution = "${bitmap.width} × ${bitmap.height}"
                                )
                            }
                        }
                        contentLength = -1
                    }
                }
            }
        } finally {
            fpsJob.cancel()
        }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        var prev = -1
        while (true) {
            val c = input.read()
            if (c == -1) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code && prev == '\r'.code) {
                return sb.substring(0, sb.length - 1)
            }
            sb.append(c.toChar())
            prev = c
        }
    }

    fun sendRemoteCommand(action: String, value: String = "") {
        scope.launch(Dispatchers.IO) {
            try {
                // Le etichette di qualità contengono spazi e parentesi: il server le decodifica.
                val encodedValue = java.net.URLEncoder.encode(value, "UTF-8")
                val urlStr = "http://$targetIp:$targetPort/control?action=$action&val=$encodedValue"
                val connection = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3000
                    readTimeout = 3000
                    requestMethod = "GET"
                }
                connection.responseCode
                connection.disconnect()
            } catch (e: Exception) {
                Log.e(tag, "Failed to send remote command $action to $targetIp:$targetPort", e)
            }
        }
    }

    fun disconnect() {
        streamJob?.cancel()
        streamJob = null
        _latestFrame.value = null
        _streamStats.value = ClientStreamStats(status = ConnectionStatus.DISCONNECTED)
    }
}
