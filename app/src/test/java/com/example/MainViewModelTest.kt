package com.example

import com.example.model.DeviceType
import com.example.model.Screen
import com.example.viewmodel.MainViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainViewModelTest {

    @Test
    fun testNavigationFlow() {
        val viewModel = MainViewModel()
        assertEquals(Screen.SPLASH, viewModel.uiState.value.currentScreen)

        viewModel.navigateTo(Screen.HOME)
        assertEquals(Screen.HOME, viewModel.uiState.value.currentScreen)

        viewModel.navigateTo(Screen.CAMERA)
        assertEquals(Screen.CAMERA, viewModel.uiState.value.currentScreen)

        viewModel.navigateBack()
        assertEquals(Screen.HOME, viewModel.uiState.value.currentScreen)
    }

    @Test
    fun testPtzControlAndReset() {
        val viewModel = MainViewModel()
        assertEquals(1.0f, viewModel.uiState.value.zoomRatio)

        viewModel.panTilt(1f, 0f)
        assertTrue(viewModel.uiState.value.panOffset.first > 0f)

        viewModel.zoomIn()
        assertTrue(viewModel.uiState.value.zoomRatio > 1.0f)

        viewModel.resetPtz()
        assertEquals(Pair(0f, 0f), viewModel.uiState.value.panOffset)
        assertEquals(1.0f, viewModel.uiState.value.zoomRatio)
    }

    @Test
    fun testAddDeviceAndSelect() {
        val viewModel = MainViewModel()
        val initialCount = viewModel.uiState.value.devices.size

        viewModel.addDevice("Balcone", "192.168.0.88", "Esterno", DeviceType.IP_ONVIF)
        assertEquals(initialCount + 1, viewModel.uiState.value.devices.size)

        val added = viewModel.uiState.value.devices.last()
        viewModel.selectDevice(added.id)
        assertEquals(added.id, viewModel.getSelectedDevice()?.id)
    }

    @Test
    fun testSnapshotWithoutCameraFrameDoesNotInventEntry() {
        val viewModel = MainViewModel()

        // Senza un fotogramma reale non si salva nulla: nessuna voce finta nell'elenco.
        viewModel.takeSnapshot()
        assertTrue(viewModel.uiState.value.recordings.isEmpty())
        assertEquals(
            "Istantanea non disponibile: avvia prima la videocamera",
            viewModel.uiState.value.toastMessage
        )
    }

    @Test
    fun testRemoteControlCallbacks() {
        val viewModel = MainViewModel()
        assertFalse(viewModel.uiState.value.isTorchOn)

        viewModel.onTorchCommand(true)
        assertTrue(viewModel.uiState.value.isTorchOn)

        val initialLens = viewModel.uiState.value.isBackCamera
        viewModel.onSwitchLensCommand()
        assertEquals(!initialLens, viewModel.uiState.value.isBackCamera)

        viewModel.onZoomCommand(2.5f)
        assertEquals(2.5f, viewModel.uiState.value.zoomRatio, 0.01f)
    }

    @Test
    fun testStealthPowerSavingToggle() {
        val viewModel = MainViewModel()
        assertFalse(viewModel.uiState.value.isStealthPowerSavingActive)

        viewModel.toggleStealthPowerSaving()
        assertTrue(viewModel.uiState.value.isStealthPowerSavingActive)

        viewModel.toggleStealthPowerSaving()
        assertFalse(viewModel.uiState.value.isStealthPowerSavingActive)
    }

    @Test
    fun testSettingsMotionSensitivityUpdate() {
        val viewModel = MainViewModel()
        viewModel.updateSettings { it.copy(motionSensitivity = 0.85f) }
        assertEquals(0.85f, viewModel.uiState.value.settings.motionSensitivity, 0.001f)
    }
}

