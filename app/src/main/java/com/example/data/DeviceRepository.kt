package com.example.data

import com.example.model.CameraDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Unica fonte dei dispositivi accoppiati: quello che è qui è stato verificato e salvato. */
class DeviceRepository(private val dao: DeviceDao) {

    val devices: Flow<List<CameraDevice>> = dao.observeDevices().map { entities ->
        entities.map { it.toCameraDevice() }
    }

    suspend fun all(): List<CameraDevice> = dao.getAll().map { it.toCameraDevice() }

    suspend fun save(device: CameraDevice) {
        dao.upsert(device.toEntity())
    }

    suspend fun delete(id: String) {
        dao.deleteById(id)
    }
}
