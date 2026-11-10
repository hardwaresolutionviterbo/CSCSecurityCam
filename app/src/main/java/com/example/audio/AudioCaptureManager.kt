package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

class AudioCaptureManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onChunkCaptured: (ByteArray) -> Unit
) {
    private val tag = "AudioCaptureManager"

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _audioAmplitude = MutableStateFlow(0f)
    val audioAmplitude: StateFlow<Float> = _audioAmplitude.asStateFlow()

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (_isRecording.value) return true

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            Log.w(tag, "RECORD_AUDIO permission not granted")
            return false
        }

        val bufferSize = AudioConfig.minRecordBufferSize
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                AudioConfig.SAMPLE_RATE,
                AudioConfig.CHANNEL_IN_CONFIG,
                AudioConfig.AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(tag, "AudioRecord initialization failed")
                audioRecord?.release()
                audioRecord = null
                return false
            }

            audioRecord?.startRecording()
            _isRecording.value = true

            captureJob = scope.launch(Dispatchers.IO) {
                val readBuffer = ByteArray(AudioConfig.CHUNK_SIZE)
                while (isActive && _isRecording.value) {
                    val record = audioRecord ?: break
                    val bytesRead = record.read(readBuffer, 0, readBuffer.size)
                    if (bytesRead > 0) {
                        val chunk = readBuffer.copyOf(bytesRead)
                        calculateAmplitude(chunk)
                        onChunkCaptured(chunk)
                    }
                }
            }

            Log.i(tag, "Audio capture started at ${AudioConfig.SAMPLE_RATE}Hz")
            return true
        } catch (e: Exception) {
            Log.e(tag, "Failed to start AudioRecord", e)
            stop()
            return false
        }
    }

    private fun calculateAmplitude(chunk: ByteArray) {
        if (chunk.size < 2) return
        var sum = 0.0
        val shortBuffer = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val numSamples = shortBuffer.remaining()
        while (shortBuffer.hasRemaining()) {
            val sample = shortBuffer.get().toDouble()
            sum += sample * sample
        }
        val rms = if (numSamples > 0) sqrt(sum / numSamples) else 0.0
        // Normalize 0.0 to 1.0 (assuming max 32767 for 16-bit PCM)
        val normalized = (rms / 32767.0).toFloat().coerceIn(0f, 1f)
        _audioAmplitude.value = normalized
    }

    fun stop() {
        _isRecording.value = false
        _audioAmplitude.value = 0f
        captureJob?.cancel()
        captureJob = null
        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(tag, "Error stopping AudioRecord", e)
        } finally {
            audioRecord = null
        }
        Log.i(tag, "Audio capture stopped")
    }
}
