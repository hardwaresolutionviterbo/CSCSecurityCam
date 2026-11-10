package com.example.viewmodel

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.wifi.WifiManager
import android.os.Build
import android.text.format.Formatter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioCaptureManager
import com.example.audio.AudioPlaybackManager
import com.example.audio.TwoWayAudioClient
import com.example.engine.ClientStreamStats
import com.example.engine.ConnectionStatus
import com.example.engine.LanStreamClient
import com.example.engine.LanStreamingServer
import com.example.engine.RemoteControlListener
import com.example.engine.ServerStats
import com.example.model.AppSettings
import com.example.model.CameraDevice
import com.example.model.DeviceType
import com.example.model.RecordingItem
import com.example.model.Screen
import com.example.motion.MotionDetector
import com.example.motion.MotionResult
import com.example.network.NetworkRecoveryManager
import com.example.notification.CscNotificationHelper
import com.example.power.BatteryOptimizationManager
import com.example.remote.RemoteAccessManager
import com.example.remote.RemoteTunnelStats
import com.example.service.CameraStreamingService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class MainUiState(
    val currentScreen: Screen = Screen.SPLASH,
    val previousScreen: Screen = Screen.HOME,
    val isStreamingActive: Boolean = false,
    val isBackCamera: Boolean = true,
    val isTorchOn: Boolean = false,
    val zoomRatio: Float = 1.0f,
    val localIpAddress: String = "192.168.0.9",
    val streamingPort: Int = 8765,
    val isStealthPowerSavingActive: Boolean = false,
    val batteryPercentage: Int = 85,
    val isCharging: Boolean = false,
    val batteryTemperatureCelsius: Float = 28.0f,
    val isWifiConnected: Boolean = true,
    val serverStats: ServerStats = ServerStats(),
    val clientStats: ClientStreamStats = ClientStreamStats(),
    val remoteTunnelStats: RemoteTunnelStats = RemoteTunnelStats(),
    val isAudioListening: Boolean = false,
    val isCameraSpeakerActive: Boolean = false,
    val audioAmplitudeIncoming: Float = 0f,
    val audioAmplitudeOutgoing: Float = 0f,
    val monitorAudioVolume: Float = 1.0f,
    val isRecording: Boolean = false,
    val recordingDurationSeconds: Int = 0,
    val isAutoRecordingFromMotion: Boolean = false,
    val nightVisionActive: Boolean = false,
    val motionDetectionActive: Boolean = true,
    val isMotionAlarmActive: Boolean = false,
    val activeMotionZone: RectF? = null,
    val lastMotionTimestamp: Long = 0L,
    val isTwoWayTalkActive: Boolean = false,
    val panOffset: Pair<Float, Float> = Pair(0f, 0f),
    val devices: List<CameraDevice> = listOf(
        CameraDevice(
            id = "cam_local",
            name = "Videocamera Principale",
            location = "Salotto",
            ipAddress = "192.168.0.9",
            port = 8765,
            isOnline = true,
            batteryPercent = 85,
            isLocalPhone = true,
            resolution = "1080p (Full HD)",
            type = DeviceType.SMARTPHONE
        ),
        CameraDevice(
            id = "cam_kitchen",
            name = "Camera Cucina",
            location = "Cucina",
            ipAddress = "192.168.0.12",
            port = 8765,
            isOnline = true,
            batteryPercent = 78,
            isLocalPhone = false,
            resolution = "1080p (Full HD)",
            type = DeviceType.SMARTPHONE
        ),
        CameraDevice(
            id = "cam_entrance",
            name = "Ingresso Esterno",
            location = "Ingresso",
            ipAddress = "192.168.0.25",
            port = 554,
            isOnline = true,
            batteryPercent = 100,
            isLocalPhone = false,
            resolution = "2K (QHD)",
            type = DeviceType.IP_ONVIF
        )
    ),
    val selectedDeviceId: String = "cam_local",
    val recordings: List<RecordingItem> = listOf(
        RecordingItem(
            id = "rec_01",
            title = "Movimento Rilevato - Salotto",
            timestamp = "Oggi, 14:12",
            durationSeconds = 45,
            isMotionEvent = true,
            cameraName = "Videocamera Principale"
        ),
        RecordingItem(
            id = "rec_02",
            title = "Istantanea Live",
            timestamp = "Oggi, 12:45",
            durationSeconds = 0,
            isSnapshot = true,
            cameraName = "Videocamera Principale"
        ),
        RecordingItem(
            id = "rec_03",
            title = "Registrazione Manuale",
            timestamp = "Ieri, 21:30",
            durationSeconds = 180,
            isMotionEvent = false,
            cameraName = "Ingresso Esterno"
        ),
        RecordingItem(
            id = "rec_04",
            title = "Movimento Notturno",
            timestamp = "Ieri, 03:15",
            durationSeconds = 62,
            isMotionEvent = true,
            cameraName = "Camera Cucina"
        )
    ),
    val settings: AppSettings = AppSettings(),
    val userProfile: com.example.model.UserProfile = com.example.model.UserProfile(),
    val toastMessage: String? = null
)

class MainViewModel : ViewModel(), RemoteControlListener {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val streamingServer = LanStreamingServer(port = 8765, scope = viewModelScope)
    val streamClient = LanStreamClient(scope = viewModelScope)
    val motionDetector = MotionDetector(sensitivity = 0.65f)

    val clientLiveBitmap: StateFlow<Bitmap?> = streamClient.latestFrame

    private var batteryManager: BatteryOptimizationManager? = null
    private var networkRecoveryManager: NetworkRecoveryManager? = null

    val cameraPlaybackManager = AudioPlaybackManager()
    private var cameraCaptureManager: AudioCaptureManager? = null
    var twoWayAudioClient: TwoWayAudioClient? = null
    var remoteAccessManager: RemoteAccessManager? = null
    private var cameraSpeakerResetJob: Job? = null

    private var recordingTimerJob: Job? = null
    private var motionResetJob: Job? = null
    private var autoStopMotionRecordingJob: Job? = null

    init {
        streamingServer.remoteControlListener = this

        streamingServer.onIncomingVoiceChunk = { chunk ->
            cameraPlaybackManager.write(chunk)
            _uiState.update { it.copy(isCameraSpeakerActive = true) }
            cameraSpeakerResetJob?.cancel()
            cameraSpeakerResetJob = viewModelScope.launch {
                delay(800)
                _uiState.update { it.copy(isCameraSpeakerActive = false) }
            }
        }

        viewModelScope.launch {
            streamingServer.stats.collect { stats ->
                _uiState.update { it.copy(serverStats = stats) }
            }
        }

        viewModelScope.launch {
            streamClient.streamStats.collect { stats ->
                _uiState.update { it.copy(clientStats = stats) }
            }
        }
    }

    fun initContinuousOperationManagers(context: Context) {
        initLocalIp(context)

        if (batteryManager == null) {
            batteryManager = BatteryOptimizationManager(
                context = context.applicationContext,
                onLowBatteryAlert = { pct ->
                    if (_uiState.value.settings.notificationsEnabled) {
                        CscNotificationHelper.sendBatteryAlert(context, pct)
                    }
                }
            ).apply {
                start()
                viewModelScope.launch {
                    batteryInfo.collect { info ->
                        _uiState.update {
                            it.copy(
                                batteryPercentage = info.percentage,
                                isCharging = info.isCharging,
                                batteryTemperatureCelsius = info.temperatureCelsius,
                                devices = it.devices.map { d ->
                                    if (d.isLocalPhone) d.copy(batteryPercent = info.percentage) else d
                                }
                            )
                        }
                    }
                }
            }
        }

        if (networkRecoveryManager == null) {
            networkRecoveryManager = NetworkRecoveryManager(
                context = context.applicationContext,
                onNetworkRestored = { newIp ->
                    _uiState.update {
                        it.copy(
                            localIpAddress = newIp,
                            isWifiConnected = true,
                            toastMessage = "Rete Wi-Fi attiva ($newIp)"
                        )
                    }
                },
                onNetworkLost = {
                    _uiState.update {
                        it.copy(
                            isWifiConnected = false,
                            toastMessage = "Avviso: Wi-Fi scollegato"
                        )
                    }
                    if (_uiState.value.settings.notificationsEnabled) {
                        CscNotificationHelper.sendNetworkAlert(
                            context,
                            "La connessione Wi-Fi della videocamera è interrotta.",
                            isRestored = false
                        )
                    }
                }
            ).apply {
                start()
            }
        }

        if (twoWayAudioClient == null) {
            twoWayAudioClient = TwoWayAudioClient(context.applicationContext, viewModelScope).apply {
                viewModelScope.launch {
                    incomingAmplitude.collect { amp ->
                        _uiState.update { it.copy(audioAmplitudeIncoming = amp) }
                    }
                }
                viewModelScope.launch {
                    outgoingAmplitude.collect { amp ->
                        _uiState.update { it.copy(audioAmplitudeOutgoing = amp) }
                    }
                }
            }
        }

        if (remoteAccessManager == null) {
            remoteAccessManager = RemoteAccessManager(context.applicationContext, viewModelScope).apply {
                viewModelScope.launch {
                    tunnelStats.collect { stats ->
                        _uiState.update { it.copy(remoteTunnelStats = stats) }
                    }
                }
                if (_uiState.value.settings.remoteAccessWebRtc) {
                    startRemoteAccess()
                }
            }
        }
    }

    override fun onTorchCommand(enabled: Boolean) {
        _uiState.update { it.copy(isTorchOn = enabled) }
    }

    override fun onSwitchLensCommand() {
        toggleLens()
    }

    override fun onZoomCommand(ratio: Float) {
        setZoom(ratio)
    }

    override fun onTriggerSnapshotCommand() {
        takeSnapshot()
    }

    fun handleMotionResult(context: Context, result: MotionResult) {
        if (!_uiState.value.settings.motionDetectionEnabled) return

        if (result.hasMotion) {
            val localDev = getLocalDevice()
            _uiState.update {
                it.copy(
                    isMotionAlarmActive = true,
                    activeMotionZone = result.activeZone,
                    lastMotionTimestamp = result.timestamp
                )
            }

            // Send push notification if enabled
            if (_uiState.value.settings.notificationsEnabled) {
                CscNotificationHelper.sendMotionAlert(
                    context,
                    localDev.name,
                    localDev.location
                )
            }

            // Auto-start recording clip if configured
            if (_uiState.value.settings.autoRecordingOnlyMotion && !_uiState.value.isRecording) {
                startAutoMotionRecording(context, localDev.name)
            }

            // Reset visual alarm indicator after 3 seconds
            motionResetJob?.cancel()
            motionResetJob = viewModelScope.launch {
                delay(3000)
                _uiState.update { it.copy(isMotionAlarmActive = false, activeMotionZone = null) }
            }
        }
    }

    private fun startAutoMotionRecording(context: Context, cameraName: String) {
        _uiState.update {
            it.copy(
                isRecording = true,
                isAutoRecordingFromMotion = true,
                recordingDurationSeconds = 0,
                toastMessage = "Movimento rilevato! Registrazione automatica avviata"
            )
        }

        if (_uiState.value.settings.notificationsEnabled) {
            CscNotificationHelper.sendRecordingNotification(context, cameraName, "Rilevamento movimento automatico")
        }

        recordingTimerJob?.cancel()
        recordingTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _uiState.update { it.copy(recordingDurationSeconds = it.recordingDurationSeconds + 1) }
            }
        }

        // Auto-stop after 15 seconds buffer
        autoStopMotionRecordingJob?.cancel()
        autoStopMotionRecordingJob = viewModelScope.launch {
            delay(15000)
            if (_uiState.value.isRecording && _uiState.value.isAutoRecordingFromMotion) {
                stopMotionRecording(isMotionTriggered = true)
            }
        }
    }

    private fun stopMotionRecording(isMotionTriggered: Boolean) {
        recordingTimerJob?.cancel()
        autoStopMotionRecordingJob?.cancel()
        val duration = _uiState.value.recordingDurationSeconds
        val nowStr = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date())
        val newRec = RecordingItem(
            id = UUID.randomUUID().toString(),
            title = if (isMotionTriggered) "Movimento Rilevato (${duration}s)" else "Registrazione Live (${duration}s)",
            timestamp = nowStr,
            durationSeconds = duration,
            isMotionEvent = isMotionTriggered,
            cameraName = getSelectedDevice()?.name ?: "Videocamera Principale"
        )
        _uiState.update {
            it.copy(
                isRecording = false,
                isAutoRecordingFromMotion = false,
                recordingDurationSeconds = 0,
                recordings = listOf(newRec) + it.recordings,
                toastMessage = "Clip salvata in Registrazioni!"
            )
        }
    }

    fun navigateTo(screen: Screen) {
        _uiState.update {
            it.copy(
                previousScreen = it.currentScreen,
                currentScreen = screen
            )
        }

        if (screen == Screen.MONITOR) {
            val dev = getSelectedDevice()
            if (dev != null) {
                streamClient.connect(dev.ipAddress, dev.port)
                if (_uiState.value.isAudioListening) {
                    twoWayAudioClient?.startAmbientListening(dev.ipAddress, dev.port)
                }
            }
        } else if (_uiState.value.previousScreen == Screen.MONITOR && screen != Screen.MONITOR) {
            streamClient.disconnect()
            twoWayAudioClient?.stopAmbientListening()
            twoWayAudioClient?.stopPushToTalk()
            _uiState.update { it.copy(isAudioListening = false, isTwoWayTalkActive = false) }
        }
    }

    fun continueAsGuest() {
        _uiState.update {
            it.copy(
                userProfile = com.example.model.UserProfile(
                    fullName = "Ospite",
                    email = "ospite@csc.local",
                    isGuest = true,
                    isLoggedIn = false
                ),
                currentScreen = Screen.HOME,
                toastMessage = "Accesso come Ospite effettuato"
            )
        }
    }

    fun loginUser(email: String, rememberMe: Boolean) {
        val displayName = email.substringBefore("@").replace(".", " ").replaceFirstChar { it.uppercase() }
        _uiState.update {
            it.copy(
                userProfile = com.example.model.UserProfile(
                    fullName = displayName,
                    email = email,
                    isGuest = false,
                    isLoggedIn = true
                ),
                currentScreen = Screen.HOME,
                toastMessage = "Bentornato, $displayName!"
            )
        }
    }

    fun registerUser(firstName: String, lastName: String, email: String) {
        val fullName = "$firstName $lastName".trim().ifBlank { "Nuovo Utente" }
        _uiState.update {
            it.copy(
                userProfile = com.example.model.UserProfile(
                    fullName = fullName,
                    email = email,
                    isGuest = false,
                    isLoggedIn = true
                ),
                currentScreen = Screen.HOME,
                toastMessage = "Account creato con successo! Benvenuto in CSC SecurityCam."
            )
        }
    }

    fun navigateBack() {
        val prev = _uiState.value.currentScreen
        _uiState.update {
            val target = when (it.currentScreen) {
                Screen.LOGIN, Screen.REGISTER -> Screen.SPLASH
                Screen.CAMERA, Screen.MONITOR, Screen.DEVICES, Screen.PLAYBACK, Screen.SETTINGS -> Screen.HOME
                Screen.HOME -> Screen.SPLASH
                Screen.SPLASH -> Screen.SPLASH
            }
            it.copy(currentScreen = target)
        }

        if (prev == Screen.MONITOR) {
            streamClient.disconnect()
            twoWayAudioClient?.stopAmbientListening()
            twoWayAudioClient?.stopPushToTalk()
            _uiState.update { it.copy(isAudioListening = false, isTwoWayTalkActive = false) }
        }
    }

    fun initLocalIp(context: Context) {
        try {
            CscNotificationHelper.createChannels(context)
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                @Suppress("DEPRECATION")
                val ipStr = Formatter.formatIpAddress(ipInt)
                if (ipStr.isNotEmpty() && ipStr != "0.0.0.0") {
                    _uiState.update {
                        it.copy(
                            localIpAddress = ipStr,
                            devices = it.devices.map { d ->
                                if (d.isLocalPhone) d.copy(ipAddress = ipStr) else d
                            }
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun toggleCameraStreaming(context: Context) {
        val newState = !_uiState.value.isStreamingActive
        _uiState.update { it.copy(isStreamingActive = newState) }

        if (newState) {
            streamingServer.start()
            cameraCaptureManager?.stop()
            cameraCaptureManager = AudioCaptureManager(
                context = context.applicationContext,
                scope = viewModelScope,
                onChunkCaptured = { pcmChunk ->
                    streamingServer.broadcastAudio(pcmChunk)
                }
            ).apply {
                start()
            }
        } else {
            streamingServer.stop()
            cameraCaptureManager?.stop()
            cameraCaptureManager = null
            cameraPlaybackManager.stop()
        }

        val intent = Intent(context, CameraStreamingService::class.java).apply {
            action = if (newState) CameraStreamingService.ACTION_START else CameraStreamingService.ACTION_STOP
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (newState) context.startForegroundService(intent) else context.startService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun toggleStealthPowerSaving() {
        _uiState.update { it.copy(isStealthPowerSavingActive = !it.isStealthPowerSavingActive) }
    }

    fun broadcastEncodedFrame(jpegBytes: ByteArray) {
        if (_uiState.value.isStreamingActive) {
            streamingServer.broadcastFrame(jpegBytes)
        }
    }

    fun toggleLens() {
        _uiState.update { it.copy(isBackCamera = !it.isBackCamera, isTorchOn = false) }
    }

    fun toggleTorch() {
        if (_uiState.value.isBackCamera) {
            _uiState.update { it.copy(isTorchOn = !it.isTorchOn) }
        }
    }

    fun setZoom(zoom: Float) {
        _uiState.update { it.copy(zoomRatio = zoom.coerceIn(1.0f, 5.0f)) }
    }

    fun zoomIn() {
        val newZoom = (_uiState.value.zoomRatio + 0.5f).coerceIn(1.0f, 5.0f)
        setZoom(newZoom)
        streamClient.sendRemoteCommand("zoom", newZoom.toString())
    }

    fun zoomOut() {
        val newZoom = (_uiState.value.zoomRatio - 0.5f).coerceIn(1.0f, 5.0f)
        setZoom(newZoom)
        streamClient.sendRemoteCommand("zoom", newZoom.toString())
    }

    fun sendRemoteTorch() {
        val newTorch = !_uiState.value.isTorchOn
        _uiState.update { it.copy(isTorchOn = newTorch) }
        streamClient.sendRemoteCommand("torch", if (newTorch) "1" else "0")
    }

    fun sendRemoteFlipLens() {
        toggleLens()
        streamClient.sendRemoteCommand("lens")
    }

    fun toggleAudioListening() {
        val newState = !_uiState.value.isAudioListening
        _uiState.update { it.copy(isAudioListening = newState) }
        val dev = getSelectedDevice()
        if (newState && dev != null) {
            twoWayAudioClient?.startAmbientListening(dev.ipAddress, dev.port)
        } else {
            twoWayAudioClient?.stopAmbientListening()
        }
    }

    fun setMonitorVolume(vol: Float) {
        _uiState.update { it.copy(monitorAudioVolume = vol) }
        twoWayAudioClient?.setVolume(vol)
    }

    fun toggleRecording() {
        if (!_uiState.value.isRecording) {
            _uiState.update { it.copy(isRecording = true, isAutoRecordingFromMotion = false, recordingDurationSeconds = 0) }
            recordingTimerJob?.cancel()
            recordingTimerJob = viewModelScope.launch {
                while (true) {
                    delay(1000)
                    _uiState.update { it.copy(recordingDurationSeconds = it.recordingDurationSeconds + 1) }
                }
            }
        } else {
            stopMotionRecording(isMotionTriggered = _uiState.value.isAutoRecordingFromMotion)
        }
    }

    fun takeSnapshot() {
        val nowStr = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date())
        val snapshot = RecordingItem(
            id = UUID.randomUUID().toString(),
            title = "Istantanea Centro Controllo",
            timestamp = nowStr,
            durationSeconds = 0,
            isSnapshot = true,
            cameraName = getSelectedDevice()?.name ?: "Camera"
        )
        _uiState.update {
            it.copy(
                recordings = listOf(snapshot) + it.recordings,
                toastMessage = "Istantanea catturata e salvata!"
            )
        }
        streamClient.sendRemoteCommand("snapshot")
    }

    fun toggleNightVision() {
        _uiState.update { it.copy(nightVisionActive = !it.nightVisionActive) }
    }

    fun toggleMotionDetection() {
        val newState = !_uiState.value.motionDetectionActive
        _uiState.update {
            it.copy(
                motionDetectionActive = newState,
                settings = it.settings.copy(motionDetectionEnabled = newState)
            )
        }
    }

    fun setTwoWayTalk(active: Boolean) {
        _uiState.update { it.copy(isTwoWayTalkActive = active) }
        val dev = getSelectedDevice()
        if (active && dev != null) {
            twoWayAudioClient?.startPushToTalk(dev.ipAddress, dev.port)
        } else {
            twoWayAudioClient?.stopPushToTalk()
        }
    }

    fun panTilt(dx: Float, dy: Float) {
        _uiState.update {
            val cur = it.panOffset
            it.copy(panOffset = Pair((cur.first + dx * 20f).coerceIn(-100f, 100f), (cur.second + dy * 20f).coerceIn(-100f, 100f)))
        }
    }

    fun resetPtz() {
        _uiState.update { it.copy(panOffset = Pair(0f, 0f), zoomRatio = 1.0f) }
        streamClient.sendRemoteCommand("zoom", "1.0")
    }

    fun selectDevice(deviceId: String) {
        _uiState.update { it.copy(selectedDeviceId = deviceId) }
        val dev = getSelectedDevice()
        if (dev != null && _uiState.value.currentScreen == Screen.MONITOR) {
            streamClient.connect(dev.ipAddress, dev.port)
        }
    }

    fun getLocalDevice(): CameraDevice {
        return _uiState.value.devices.firstOrNull { it.isLocalPhone }
            ?: CameraDevice("cam_local", "Videocamera Principale", "Salotto", _uiState.value.localIpAddress, 8765)
    }

    fun getSelectedDevice(): CameraDevice? {
        return _uiState.value.devices.firstOrNull { it.id == _uiState.value.selectedDeviceId }
            ?: _uiState.value.devices.firstOrNull()
    }

    fun addDevice(name: String, ip: String, location: String, type: DeviceType) {
        val newDevice = CameraDevice(
            id = UUID.randomUUID().toString(),
            name = name,
            location = location,
            ipAddress = ip,
            port = if (type == DeviceType.SMARTPHONE) 8765 else 554,
            type = type
        )
        _uiState.update { it.copy(devices = it.devices + newDevice, toastMessage = "Dispositivo $name aggiunto!") }
    }

    fun deleteRecording(id: String) {
        _uiState.update { it.copy(recordings = it.recordings.filter { rec -> rec.id != id }) }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        _uiState.update {
            val updated = transform(it.settings)
            motionDetector.sensitivity = updated.motionSensitivity
            if (updated.remoteAccessWebRtc != it.settings.remoteAccessWebRtc) {
                if (updated.remoteAccessWebRtc) {
                    remoteAccessManager?.startRemoteAccess()
                } else {
                    remoteAccessManager?.stopRemoteAccess()
                }
            }
            it.copy(settings = updated)
        }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        streamingServer.stop()
        streamClient.disconnect()
        cameraCaptureManager?.stop()
        cameraPlaybackManager.stop()
        twoWayAudioClient?.release()
        remoteAccessManager?.stopRemoteAccess()
        batteryManager?.stop()
        networkRecoveryManager?.stop()
        recordingTimerJob?.cancel()
        motionResetJob?.cancel()
        autoStopMotionRecordingJob?.cancel()
        cameraSpeakerResetJob?.cancel()
    }
}
