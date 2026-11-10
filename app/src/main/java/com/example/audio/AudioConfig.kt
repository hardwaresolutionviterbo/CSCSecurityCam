package com.example.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack

object AudioConfig {
    const val SAMPLE_RATE = 16000 // 16 kHz sample rate
    const val CHANNEL_IN_CONFIG = AudioFormat.CHANNEL_IN_MONO
    const val CHANNEL_OUT_CONFIG = AudioFormat.CHANNEL_OUT_MONO
    const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    const val CHUNK_SIZE = 1280 // ~40ms per packet at 16kHz 16-bit mono

    val minRecordBufferSize: Int
        get() {
            val min = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN_CONFIG, AUDIO_FORMAT)
            return if (min > 0) maxOf(min, CHUNK_SIZE * 2) else CHUNK_SIZE * 4
        }

    val minTrackBufferSize: Int
        get() {
            val min = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT_CONFIG, AUDIO_FORMAT)
            return if (min > 0) maxOf(min, CHUNK_SIZE * 2) else CHUNK_SIZE * 4
        }
}
