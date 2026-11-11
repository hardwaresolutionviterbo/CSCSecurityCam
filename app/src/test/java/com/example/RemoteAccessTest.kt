package com.example

import com.example.remote.RemoteAccessManager
import com.example.remote.RemoteConnectionStatus
import com.example.remote.RemoteRouteType
import com.example.remote.RemoteTunnelStats
import com.example.remote.StunClient
import com.example.viewmodel.MainViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteAccessTest {

    @Test
    fun testStunConstants() {
        assertEquals("stun.l.google.com", StunClient.DEFAULT_STUN_SERVER)
        assertEquals(19302, StunClient.DEFAULT_STUN_PORT)
    }

    @Test
    fun testRemoteTunnelStatsDefaults() {
        val stats = RemoteTunnelStats()
        assertEquals(RemoteConnectionStatus.DISABLED, stats.status)
        assertEquals(RemoteRouteType.LOCAL_LAN, stats.activeRoute)
        assertTrue(stats.isEncrypted)
        assertTrue(stats.encryptionProtocol.contains("DTLS"))
        // Nessun codice o server TURN inventato: arrivano dalla configurazione reale.
        assertTrue(stats.pairingCode.isEmpty())
        assertEquals("Non configurato", stats.turnServer)
    }

    @Test
    fun testRemoteAccessSettingsToggle() {
        val viewModel = MainViewModel()
        assertFalse(viewModel.uiState.value.settings.remoteAccessWebRtc)

        viewModel.updateSettings { it.copy(remoteAccessWebRtc = true) }
        assertTrue(viewModel.uiState.value.settings.remoteAccessWebRtc)

        viewModel.updateSettings { it.copy(remoteAccessWebRtc = false) }
        assertFalse(viewModel.uiState.value.settings.remoteAccessWebRtc)
    }

    @Test
    fun testRemoteTunnelStatePresence() {
        val viewModel = MainViewModel()
        val stats = viewModel.uiState.value.remoteTunnelStats
        assertNotNull(stats)
        // Prima dell'inizializzazione il codice stanza resta vuoto finché non viene letto dal disco.
        assertEquals(RemoteConnectionStatus.DISABLED, stats.status)
        assertTrue(stats.pairingCode.isEmpty())
    }
}
