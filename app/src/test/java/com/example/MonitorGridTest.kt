package com.example

import com.example.engine.GridStreams
import com.example.viewmodel.MainViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

class MonitorGridTest {

    @Test
    fun `the control center starts with a single camera`() {
        val viewModel = MainViewModel()
        assertEquals(1, viewModel.uiState.value.monitorLayout)
    }

    @Test
    fun `the layout selector accepts one two or four tiles`() {
        val viewModel = MainViewModel()

        viewModel.setMonitorLayout(4)
        assertEquals(4, viewModel.uiState.value.monitorLayout)

        viewModel.setMonitorLayout(2)
        assertEquals(2, viewModel.uiState.value.monitorLayout)

        viewModel.setMonitorLayout(1)
        assertEquals(1, viewModel.uiState.value.monitorLayout)
    }

    @Test
    fun `an unsupported layout falls back to a single camera`() {
        val viewModel = MainViewModel()
        viewModel.setMonitorLayout(3)
        assertEquals(1, viewModel.uiState.value.monitorLayout)
    }

    @Test
    fun `the grid never exceeds four concurrent streams`() {
        assertEquals(4, GridStreams.MAX_STREAMS)
        assertEquals(640, GridStreams.GRID_MAX_SIDE)
        assertEquals(12, GridStreams.GRID_FPS)
    }
}
