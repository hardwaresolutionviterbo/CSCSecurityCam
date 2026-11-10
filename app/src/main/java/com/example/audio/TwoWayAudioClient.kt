package com.example.audio

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

class TwoWayAudioClient(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val tag = "TwoWayAudioClient"

    val playbackManager = AudioPlaybackManager()
    private var captureManager: AudioCaptureManager? = null

    private var ambientJob: Job? = null
    private var talkJob: Job? = null
    private var talkOutputStream: OutputStream? = null
    private var talkConnection: HttpURLConnection? = null

    private val _isAmbientListening = MutableStateFlow(false)
    val isAmbientListening: StateFlow<Boolean> = _isAmbientListening.asStateFlow()

    private val _isPushToTalkActive = MutableStateFlow(false)
    val isPushToTalkActive: StateFlow<Boolean> = _isPushToTalkActive.asStateFlow()

    private val _incomingAmplitude = MutableStateFlow(0f)
    val incomingAmplitude: StateFlow<Float> = playbackManager.outputAmplitude

    private val _outgoingAmplitude = MutableStateFlow(0f)
    val outgoingAmplitude: StateFlow<Float> = _outgoingAmplitude.asStateFlow()

    fun startAmbientListening(cameraIp: String, port: Int) {
        if (_isAmbientListening.value) return
        _isAmbientListening.value = true
        playbackManager.initTrack()

        ambientJob = scope.launch(Dispatchers.IO) {
            val urlString = "http://$cameraIp:$port/audio.pcm"
            while (isActive && _isAmbientListening.value) {
                var conn: HttpURLConnection? = null
                var inputStream: InputStream? = null
                try {
                    val url = URL(urlString)
                    conn = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 4000
                        readTimeout = 10000
                        requestMethod = "GET"
                        setRequestProperty("Connection", "close")
                    }

                    if (conn.responseCode == 200) {
                        inputStream = conn.inputStream
                        val buffer = ByteArray(AudioConfig.CHUNK_SIZE)
                        while (isActive && _isAmbientListening.value) {
                            val bytesRead = inputStream.read(buffer)
                            if (bytesRead <= 0) break
                            playbackManager.write(buffer, 0, bytesRead)
                        }
                    }
                } catch (e: Exception) {
                    if (isActive && _isAmbientListening.value) {
                        Log.d(tag, "Ambient audio reconnecting: ${e.message}")
                        kotlinx.coroutines.delay(1500)
                    }
                } finally {
                    try { inputStream?.close() } catch (ignored: Exception) {}
                    try { conn?.disconnect() } catch (ignored: Exception) {}
                }
            }
        }
    }

    fun stopAmbientListening() {
        _isAmbientListening.value = false
        ambientJob?.cancel()
        ambientJob = null
        playbackManager.stop()
    }

    fun setMuteAmbient(muted: Boolean) {
        playbackManager.setMuted(muted)
    }

    fun setVolume(vol: Float) {
        playbackManager.setVolume(vol)
    }

    fun startPushToTalk(cameraIp: String, port: Int): Boolean {
        if (_isPushToTalkActive.value) return true
        _isPushToTalkActive.value = true

        // Squelch: duck ambient listening while speaking to prevent feedback echo
        playbackManager.setVolume(0.1f)

        talkJob = scope.launch(Dispatchers.IO) {
            try {
                val url = URL("http://$cameraIp:$port/talk")
                talkConnection = (url.openConnection() as HttpURLConnection).apply {
                    doOutput = true
                    requestMethod = "POST"
                    setChunkedStreamingMode(AudioConfig.CHUNK_SIZE)
                    setRequestProperty("Content-Type", "audio/L16;rate=16000;channels=1")
                    setRequestProperty("Connection", "close")
                    connectTimeout = 3000
                    readTimeout = 0 // continuous streaming
                }

                talkOutputStream = talkConnection?.outputStream

                captureManager = AudioCaptureManager(
                    context = context,
                    scope = scope,
                    onChunkCaptured = { pcmChunk ->
                        try {
                            talkOutputStream?.write(pcmChunk)
                            talkOutputStream?.flush()
                        } catch (e: Exception) {
                            Log.e(tag, "Error streaming voice chunk", e)
                        }
                    }
                )

                val started = captureManager?.start() ?: false
                if (!started) {
                    Log.e(tag, "Failed to start AudioRecord for Push-to-Talk")
                    stopPushToTalk()
                    return@launch
                }

                // Track outgoing amplitude
                launch {
                    captureManager?.audioAmplitude?.collect { amp ->
                        _outgoingAmplitude.value = amp
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to initiate Push-to-Talk", e)
                stopPushToTalk()
            }
        }
        return true
    }

    fun stopPushToTalk() {
        if (!_isPushToTalkActive.value) return
        _isPushToTalkActive.value = false
        _outgoingAmplitude.value = 0f

        // Restore ambient listening volume
        playbackManager.setVolume(1.0f)

        captureManager?.stop()
        captureManager = null

        talkJob?.cancel()
        talkJob = null

        scope.launch(Dispatchers.IO) {
            try {
                talkOutputStream?.flush()
                talkOutputStream?.close()
            } catch (ignored: Exception) {}
            try {
                talkConnection?.disconnect()
            } catch (ignored: Exception) {}
            talkOutputStream = null
            talkConnection = null
        }
    }

    fun release() {
        stopAmbientListening()
        stopPushToTalk()
    }
}
