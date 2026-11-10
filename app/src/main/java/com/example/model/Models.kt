package com.example.model

enum class Screen {
    SPLASH,
    LOGIN,
    REGISTER,
    HOME,
    CAMERA,
    MONITOR,
    DEVICES,
    PLAYBACK,
    SETTINGS
}

data class UserProfile(
    val email: String = "",
    val fullName: String = "Ospite",
    val isGuest: Boolean = true,
    val isLoggedIn: Boolean = false
)

data class CameraDevice(
    val id: String,
    val name: String,
    val location: String,
    val ipAddress: String,
    val port: Int = 8765,
    val isOnline: Boolean = true,
    val batteryPercent: Int = 85,
    val isLocalPhone: Boolean = false,
    val resolution: String = "1080p (Full HD)",
    val type: DeviceType = DeviceType.SMARTPHONE
)

enum class DeviceType {
    SMARTPHONE,
    IP_ONVIF,
    IP_RTSP
}

data class RecordingItem(
    val id: String,
    val title: String,
    val timestamp: String,
    val durationSeconds: Int,
    val isMotionEvent: Boolean = false,
    val isSnapshot: Boolean = false,
    val filePath: String = "",
    val cameraName: String = "Camera Principale",
    /** Uri MediaStore (o file://) del media salvato davvero su disco. */
    val uri: String = "",
    val fileSizeBytes: Long = 0L,
    val createdAtMillis: Long = 0L,
    val mimeType: String = ""
) {
    val hasFile: Boolean get() = uri.isNotEmpty()
}

data class AppSettings(
    val videoQuality: String = "Auto",
    val audioMode: String = "Bidirezionale",
    val motionDetectionEnabled: Boolean = true,
    val motionSensitivity: Float = 0.75f,
    val autoRecordingOnlyMotion: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val remoteAccessWebRtc: Boolean = false,
    val nightVisionHardware: Boolean = false,
    val port: Int = 8765
)
