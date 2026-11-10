package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

class AudioPlaybackManager {
    private val tag = "AudioPlaybackManager"

    private var audioTrack: AudioTrack? = null
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _volume = MutableStateFlow(1.0f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _outputAmplitude = MutableStateFlow(0f)
    val outputAmplitude: StateFlow<Float> = _outputAmplitude.asStateFlow()

    fun initTrack(): Boolean {
        if (audioTrack != null) return true

        val bufferSize = AudioConfig.minTrackBufferSize
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setEncoding(AudioConfig.AUDIO_FORMAT)
                .setSampleRate(AudioConfig.SAMPLE_RATE)
                .setChannelMask(AudioConfig.CHANNEL_OUT_CONFIG)
                .build()

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                Log.e(tag, "AudioTrack init failed")
                audioTrack?.release()
                audioTrack = null
                return false
            }

            audioTrack?.play()
            _isPlaying.value = true
            applyVolume()
            Log.i(tag, "AudioTrack initialized and playing")
            return true
        } catch (e: Exception) {
            Log.e(tag, "Failed to create AudioTrack", e)
            stop()
            return false
        }
    }

    fun write(data: ByteArray, offset: Int = 0, length: Int = data.size) {
        if (_isMuted.value) return
        if (audioTrack == null) {
            if (!initTrack()) return
        }

        try {
            audioTrack?.write(data, offset, length)
            calculateAmplitude(data, offset, length)
        } catch (e: Exception) {
            Log.e(tag, "Error writing to AudioTrack", e)
        }
    }

    fun setVolume(vol: Float) {
        val clamped = vol.coerceIn(0f, 1f)
        _volume.value = clamped
        applyVolume()
    }

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
        applyVolume()
    }

    fun toggleMute() {
        setMuted(!_isMuted.value)
    }

    private fun applyVolume() {
        val effectiveVolume = if (_isMuted.value) 0f else _volume.value
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                audioTrack?.setVolume(effectiveVolume)
            } else {
                @Suppress("DEPRECATION")
                audioTrack?.setStereoVolume(effectiveVolume, effectiveVolume)
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to apply volume", e)
        }
    }

    private fun calculateAmplitude(chunk: ByteArray, offset: Int, length: Int) {
        if (length < 2) return
        var sum = 0.0
        val shortBuffer = ByteBuffer.wrap(chunk, offset, length).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val numSamples = shortBuffer.remaining()
        while (shortBuffer.hasRemaining()) {
            val sample = shortBuffer.get().toDouble()
            sum += sample * sample
        }
        val rms = if (numSamples > 0) sqrt(sum / numSamples) else 0.0
        val normalized = (rms / 32767.0).toFloat().coerceIn(0f, 1f)
        _outputAmplitude.value = normalized
    }

    fun stop() {
        _isPlaying.value = false
        _outputAmplitude.value = 0f
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(tag, "Error stopping AudioTrack", e)
        } finally {
            audioTrack = null
        }
        Log.i(tag, "AudioTrack stopped")
    }
}
