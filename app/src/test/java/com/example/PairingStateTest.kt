package com.example

import com.example.model.DeviceType
import com.example.viewmodel.MainViewModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingStateTest {

    @Test
    fun `starts empty without invented devices`() {
        val viewModel = MainViewModel()
        assertTrue(viewModel.uiState.value.devices.isEmpty())
        assertNull(viewModel.uiState.value.selectedDeviceId)
        assertNull(viewModel.getSelectedDevice())
        assertTrue(viewModel.uiState.value.recordings.isEmpty())
    }

    @Test
    fun `rejects an invalid ip`() {
        val viewModel = MainViewModel()
        assertFalse(viewModel.addDevice("Balcone", "999.1.1.1", "Esterno", DeviceType.SMARTPHONE, 8765))
        assertTrue(viewModel.uiState.value.devices.isEmpty())
    }

    @Test
    fun `rejects an invalid port`() {
        val viewModel = MainViewModel()
        assertFalse(viewModel.addDevice("Balcone", "192.168.1.20", "Esterno", DeviceType.SMARTPHONE, 70_000))
        assertTrue(viewModel.uiState.value.devices.isEmpty())
    }

    @Test
    fun `keeps the chosen port and selects the new device`() {
        val viewModel = MainViewModel()
        val added = viewModel.addDevice("Balcone", "192.168.1.20", "Esterno", DeviceType.SMARTPHONE, 9100)

        assertTrue(added)
        val device = viewModel.getSelectedDevice()
        assertTrue(device != null)
        assertTrue(device!!.port == 9100)
        assertTrue(device.ipAddress == "192.168.1.20")
    }

    @Test
    fun `selected device is never the first one by fallback`() {
        val viewModel = MainViewModel()
        viewModel.addDevice("Balcone", "192.168.1.20", "Esterno", DeviceType.SMARTPHONE, 8765)
        val first = viewModel.uiState.value.devices.first()

        // Selezionare un id inesistente non deve far ripiegare sul primo dispositivo.
        viewModel.selectDevice("id-inesistente")
        assertNull(viewModel.getSelectedDevice())
        assertTrue(viewModel.uiState.value.devices.any { it.id == first.id })
    }

    @Test
    fun `rejects a malformed pairing link without saving`() {
        val viewModel = MainViewModel()
        viewModel.handlePairingLink("https://example.com/non-un-qr")
        assertTrue(viewModel.uiState.value.devices.isEmpty())
    }
}
