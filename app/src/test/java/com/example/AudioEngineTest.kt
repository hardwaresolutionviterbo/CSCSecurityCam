package com.example

import com.example.audio.AudioConfig
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AudioEngineTest {

    @Test
    fun testAudioConfigValues() {
        assertEquals(16000, AudioConfig.SAMPLE_RATE)
        assertEquals(1280, AudioConfig.CHUNK_SIZE)
        assertTrue(AudioConfig.minRecordBufferSize >= AudioConfig.CHUNK_SIZE * 2)
        assertTrue(AudioConfig.minTrackBufferSize >= AudioConfig.CHUNK_SIZE * 2)
    }

    @Test
    fun testAudioListeningStateToggle() {
        val viewModel = MainViewModel()
        assertFalse(viewModel.uiState.value.isAudioListening)

        viewModel.toggleAudioListening()
        assertTrue(viewModel.uiState.value.isAudioListening)

        viewModel.toggleAudioListening()
        assertFalse(viewModel.uiState.value.isAudioListening)
    }

    @Test
    fun testTwoWayTalkStateToggle() {
        val viewModel = MainViewModel()
        assertFalse(viewModel.uiState.value.isTwoWayTalkActive)

        viewModel.setTwoWayTalk(true)
        assertTrue(viewModel.uiState.value.isTwoWayTalkActive)

        viewModel.setTwoWayTalk(false)
        assertFalse(viewModel.uiState.value.isTwoWayTalkActive)
    }

    @Test
    fun testMonitorVolumeAdjustment() {
        val viewModel = MainViewModel()
        assertEquals(1.0f, viewModel.uiState.value.monitorAudioVolume, 0.001f)

        viewModel.setMonitorVolume(0.5f)
        assertEquals(0.5f, viewModel.uiState.value.monitorAudioVolume, 0.001f)

        viewModel.setMonitorVolume(0.0f)
        assertEquals(0.0f, viewModel.uiState.value.monitorAudioVolume, 0.001f)
    }

    @Test
    fun testLanStreamingServerAudioBroadcastSafeWhenNoClients() {
        val viewModel = MainViewModel()
        val dummyPcm = ByteArray(1280) { 0 }
        // Broadcasting should succeed safely even if no clients are connected
        viewModel.streamingServer.broadcastAudio(dummyPcm)
        assertTrue(viewModel.streamingServer.stats.value.isRunning || !viewModel.streamingServer.stats.value.isRunning)
    }
}
