package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.AppSettings

/** Impostazioni persistite: una sola riga, id = 1. */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val videoQuality: String,
    val audioMode: String,
    val motionDetectionEnabled: Boolean,
    val motionSensitivity: Float,
    val autoRecordingOnlyMotion: Boolean,
    val notificationsEnabled: Boolean,
    val remoteAccessWebRtc: Boolean,
    val nightVisionHardware: Boolean,
    val port: Int
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

fun SettingsEntity.toAppSettings(): AppSettings = AppSettings(
    videoQuality = videoQuality,
    audioMode = audioMode,
    motionDetectionEnabled = motionDetectionEnabled,
    motionSensitivity = motionSensitivity,
    autoRecordingOnlyMotion = autoRecordingOnlyMotion,
    notificationsEnabled = notificationsEnabled,
    remoteAccessWebRtc = remoteAccessWebRtc,
    nightVisionHardware = nightVisionHardware,
    port = port
)

fun AppSettings.toEntity(): SettingsEntity = SettingsEntity(
    videoQuality = videoQuality,
    audioMode = audioMode,
    motionDetectionEnabled = motionDetectionEnabled,
    motionSensitivity = motionSensitivity,
    autoRecordingOnlyMotion = autoRecordingOnlyMotion,
    notificationsEnabled = notificationsEnabled,
    remoteAccessWebRtc = remoteAccessWebRtc,
    nightVisionHardware = nightVisionHardware,
    port = port
)
