package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.CameraDevice
import com.example.model.DeviceType

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val location: String,
    val ipAddress: String,
    val port: Int,
    val type: String,
    val isLocalPhone: Boolean = false,
    val resolution: String = "1080p (Full HD)",
    /** Account proprietario: le telecamere non si mescolano tra utenti diversi. */
    val ownerId: String = ""
)

fun DeviceEntity.toCameraDevice(): CameraDevice = CameraDevice(
    id = id,
    name = name,
    location = location,
    ipAddress = ipAddress,
    port = port,
    isOnline = false,
    batteryPercent = 0,
    isLocalPhone = isLocalPhone,
    resolution = resolution,
    type = runCatching { DeviceType.valueOf(type) }.getOrDefault(DeviceType.SMARTPHONE),
    ownerId = ownerId
)

fun CameraDevice.toEntity(): DeviceEntity = DeviceEntity(
    id = id,
    name = name,
    location = location,
    ipAddress = ipAddress,
    port = port,
    type = type.name,
    isLocalPhone = isLocalPhone,
    resolution = resolution,
    ownerId = ownerId
)
