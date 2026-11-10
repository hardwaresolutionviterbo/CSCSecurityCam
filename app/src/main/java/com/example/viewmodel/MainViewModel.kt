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
import com.example.engine.StreamingHub
import com.example.data.AppDatabase
import com.example.data.DeviceRepository
import com.example.pairing.CameraProbe
import com.example.pairing.PairingPayload
import com.example.pairing.ProbeResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    val localIpAddress: String = "",
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
    /** Solo telecamere accoppiate e verificate: all'avvio l'elenco è vuoto. */
    val devices: List<CameraDevice> = emptyList(),
    val selectedDeviceId: String? = null,
    /** false = /status non risponde, true = risponde, null = non ancora verificato. */
    val selectedDeviceReachable: Boolean? = null,
    val selectedDeviceProbeMessage: String? = null,
    val recordings: List<RecordingItem> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val userProfile: com.example.model.UserProfile = com.example.model.UserProfile(),
    val toastMessage: String? = null
)

class MainViewModel : ViewModel(), RemoteControlListener {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    // Server di processo: non muore quando l'Activity viene distrutta.
    val streamingServer = StreamingHub.server
    val streamClient = LanStreamClient(scope = viewModelScope)
    val motionDetector = MotionDetector(sensitivity = 0.65f)

    val clientLiveBitmap: StateFlow<Bitmap?> = streamClient.latestFrame

    private var batteryManager: BatteryOptimizationManager? = null
    private var networkRecoveryManager: NetworkRecoveryManager? = null
    private var deviceRepository: DeviceRepository? = null
    private var statusPollJob: Job? = null

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

        // I fotogrammi vanno dal servizio direttamente al server di processo: il ViewModel non è
        // più nel percorso del video, così chiudere l'app non interrompe lo streaming.
        CameraStreamingService.motionSink = { result -> handleMotionResult(context, result) }
        CameraStreamingService.stopSink = { syncStreamingStopped() }

        // I dispositivi esistono solo se sono stati accoppiati e salvati su Room.
        if (deviceRepository == null) {
            val repository = DeviceRepository(AppDatabase.getInstance(context).deviceDao())
            deviceRepository = repository
            viewModelScope.launch {
                repository.devices.collect { saved ->
                    _uiState.update { current ->
                        val selectedStillPresent = current.selectedDeviceId != null &&
                            saved.any { it.id == current.selectedDeviceId }
                        current.copy(
                            devices = saved,
                            selectedDeviceId = if (selectedStillPresent) current.selectedDeviceId else null
                        )
                    }
                }
            }
            startStatusPolling()
        }

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
            refreshSelectedStatus()
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
            // Il server LAN lo avvia il servizio in primo piano: vive più a lungo dell'Activity.
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
            syncStreamingStopped()
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

    /** Riallinea lo stato quando lo streaming viene fermato dall'esterno (es. notifica). */
    private fun syncStreamingStopped() {
        _uiState.update { it.copy(isStreamingActive = false) }
        cameraCaptureManager?.stop()
        cameraCaptureManager = null
        cameraPlaybackManager.stop()
    }

    fun toggleStealthPowerSaving() {
        _uiState.update { it.copy(isStealthPowerSavingActive = !it.isStealthPowerSavingActive) }
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
        _uiState.update {
            it.copy(
                selectedDeviceId = deviceId,
                selectedDeviceReachable = null,
                selectedDeviceProbeMessage = null
            )
        }
        val dev = getSelectedDevice() ?: return
        refreshSelectedStatus()
        if (_uiState.value.currentScreen == Screen.MONITOR) {
            streamClient.connect(dev.ipAddress, dev.port)
            if (_uiState.value.isAudioListening) {
                twoWayAudioClient?.startAmbientListening(dev.ipAddress, dev.port)
            }
        }
    }

    fun getLocalDevice(): CameraDevice {
        return _uiState.value.devices.firstOrNull { it.isLocalPhone }
            ?: CameraDevice(
                id = "local_phone",
                name = "Questo telefono",
                location = "Casa",
                ipAddress = _uiState.value.localIpAddress,
                port = _uiState.value.streamingPort,
                isLocalPhone = true
            )
    }

    /** Solo il dispositivo scelto: nessun ripiego sul primo della lista. */
    fun getSelectedDevice(): CameraDevice? {
        val selected = _uiState.value.selectedDeviceId ?: return null
        return _uiState.value.devices.firstOrNull { it.id == selected }
    }

    /** Valida IP e porta, salva su Room e seleziona il nuovo dispositivo. */
    fun addDevice(name: String, ip: String, location: String, type: DeviceType, port: Int? = null): Boolean {
        val cleanIp = ip.trim()
        if (!PairingPayload.isValidIp(cleanIp)) {
            _uiState.update { it.copy(toastMessage = "Indirizzo IP non valido: $cleanIp") }
            return false
        }
        val effectivePort = port ?: if (type == DeviceType.SMARTPHONE) PairingPayload.DEFAULT_PORT else 554
        if (!PairingPayload.isValidPort(effectivePort)) {
            _uiState.update { it.copy(toastMessage = "Porta non valida: $effectivePort") }
            return false
        }

        val newDevice = CameraDevice(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "Videocamera" },
            location = location.trim().ifBlank { "Casa" },
            ipAddress = cleanIp,
            port = effectivePort,
            type = type
        )
        _uiState.update {
            it.copy(
                devices = it.devices + newDevice,
                selectedDeviceId = newDevice.id,
                toastMessage = "Dispositivo ${newDevice.name} aggiunto!"
            )
        }
        viewModelScope.launch { deviceRepository?.save(newDevice) }
        refreshSelectedStatus()
        return true
    }

    fun deleteDevice(deviceId: String) {
        val device = _uiState.value.devices.firstOrNull { it.id == deviceId } ?: return
        _uiState.update {
            it.copy(
                devices = it.devices.filterNot { d -> d.id == deviceId },
                selectedDeviceId = if (it.selectedDeviceId == deviceId) null else it.selectedDeviceId,
                toastMessage = "Dispositivo ${device.name} rimosso"
            )
        }
        viewModelScope.launch { deviceRepository?.delete(deviceId) }
    }

    /** "Riconnetti" usa il dispositivo selezionato, mai il primo della lista. */
    fun reconnectSelected() {
        val device = getSelectedDevice()
        if (device == null) {
            _uiState.update { it.copy(toastMessage = "Nessuna telecamera selezionata") }
            return
        }
        streamClient.disconnect()
        streamClient.connect(device.ipAddress, device.port)
        if (_uiState.value.isAudioListening) {
            twoWayAudioClient?.startAmbientListening(device.ipAddress, device.port)
        }
        refreshSelectedStatus()
    }

    /**
     * Link csc://cam?ip=..&port=.. (o "ip:porta"): si prova la connessione e si salva solo se la
     * telecamera risponde davvero a /status.
     */
    fun handlePairingLink(rawLink: String) {
        val info = PairingPayload.parse(rawLink)
        if (info == null) {
            _uiState.update { it.copy(toastMessage = "QR non riconosciuto: serve csc://cam?ip=..&port=..") }
            return
        }
        val alreadySaved = _uiState.value.devices.firstOrNull {
            it.ipAddress == info.ip && it.port == info.port
        }
        viewModelScope.launch {
            val result = CameraProbe.probe(info.ip, info.port)
            if (result !is ProbeResult.Reachable) {
                _uiState.update {
                    it.copy(toastMessage = "Telecamera non raggiungibile (${info.ip}:${info.port}): ${result.describe()}")
                }
                return@launch
            }

            if (alreadySaved != null) {
                selectDevice(alreadySaved.id)
                _uiState.update {
                    it.copy(currentScreen = Screen.MONITOR, toastMessage = "Telecamera ${alreadySaved.name} collegata")
                }
            } else {
                addDevice(
                    name = info.id?.takeIf { it.isNotBlank() } ?: "Videocamera ${info.ip}",
                    ip = info.ip,
                    location = "Casa",
                    type = DeviceType.SMARTPHONE,
                    port = info.port
                )
                _uiState.update { it.copy(currentScreen = Screen.MONITOR) }
            }
            streamClient.connect(info.ip, info.port)
        }
    }

    private fun startStatusPolling() {
        if (statusPollJob != null) return
        statusPollJob = viewModelScope.launch {
            while (true) {
                pollDeviceStatuses()
                delay(5_000)
            }
        }
    }

    fun refreshSelectedStatus() {
        viewModelScope.launch { pollDeviceStatuses() }
    }

    /** Online/Offline dall'esito reale di GET /status, non da un'etichetta scritta a mano. */
    private suspend fun pollDeviceStatuses() {
        val devices = _uiState.value.devices
        if (devices.isEmpty()) {
            _uiState.update { it.copy(selectedDeviceReachable = null, selectedDeviceProbeMessage = null) }
            return
        }

        val results = coroutineScope {
            devices.map { device ->
                async { device.id to CameraProbe.probe(device.ipAddress, device.port, timeoutMs = 1_200) }
            }.awaitAll()
        }.toMap()

        _uiState.update { current ->
            val selectedResult = current.selectedDeviceId?.let { results[it] }
            current.copy(
                devices = current.devices.map { device ->
                    device.copy(isOnline = results[device.id] is ProbeResult.Reachable)
                },
                selectedDeviceReachable = selectedResult?.let { it is ProbeResult.Reachable },
                selectedDeviceProbeMessage = selectedResult?.describe()
            )
        }
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
        // Il server LAN NON viene fermato qui: appartiene al servizio in primo piano. Fermarlo
        // all'uscita dall'app era la causa della disconnessione con app chiusa o schermo spento.
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
        statusPollJob?.cancel()
    }
}
