package com.example.viewmodel

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.wifi.WifiManager
import android.os.Build
import android.os.StatFs
import android.text.format.Formatter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioCaptureManager
import com.example.audio.AudioPlaybackManager
import com.example.audio.TwoWayAudioClient
import com.example.engine.ClientStreamStats
import com.example.engine.ConnectionStatus
import com.example.engine.GridTarget
import com.example.engine.LanStreamClient
import com.example.engine.LanStreamingServer
import com.example.engine.MultiStreamManager
import com.example.engine.RemoteControlListener
import com.example.engine.ServerStats
import com.example.engine.StreamingHub
import com.example.data.AppDatabase
import com.example.data.DeviceRepository
import com.example.data.RecordingRepository
import com.example.data.SettingsRepository
import com.example.media.MediaFormat
import com.example.media.MediaStoreStorage
import com.example.media.StreamQuality
import com.example.service.ClipResult
import com.example.pairing.CameraProbe
import com.example.pairing.LocalAddress
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
    /** Rotazione della visuale nel centro di controllo: 0, 90, 180 o 270 gradi. */
    val viewRotationDegrees: Int = 0,
    val storageTotalBytes: Long = 0L,
    val storageFreeBytes: Long = 0L,
    val recordingsBytes: Long = 0L,
    val localIpAddress: String = "",
    val streamingPort: Int = 8765,
    val isStealthPowerSavingActive: Boolean = false,
    val batteryPercentage: Int = 85,
    val isCharging: Boolean = false,
    val batteryTemperatureCelsius: Float = 28.0f,
    val isWifiConnected: Boolean = true,
    val serverStats: ServerStats = ServerStats(),
    val clientStats: ClientStreamStats = ClientStreamStats(),
    /** Riquadri del centro di controllo: 1, 2 o 4 telecamere insieme. */
    val monitorLayout: Int = 1,
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

    /** Un client per riquadro: LanStreamClient apre una sola connessione alla volta. */
    val multiStreamManager = MultiStreamManager(viewModelScope)
    val gridFrames: StateFlow<Map<String, Bitmap?>> = multiStreamManager.frames
    val gridStats: StateFlow<Map<String, ClientStreamStats>> = multiStreamManager.stats

    val clientLiveBitmap: StateFlow<Bitmap?> = streamClient.latestFrame

    private var batteryManager: BatteryOptimizationManager? = null
    private var networkRecoveryManager: NetworkRecoveryManager? = null
    private var deviceRepository: DeviceRepository? = null
    private var recordingRepository: RecordingRepository? = null
    private var settingsRepository: SettingsRepository? = null
    private var appContext: Context? = null
    private var statusPollJob: Job? = null
    private var clipCameraName: String = "Videocamera"
    private var clipIsMotionEvent: Boolean = false
    private var lastNotifiedMotionAt: Long = 0L

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
        CameraStreamingService.clipSink = { clip -> handleClipFinalized(clip) }
        appContext = context.applicationContext

        // Comandi che arrivano fuori casa sul data channel WebRTC: stessa logica del LAN.
        com.example.remote.RemotePublishHub.controlSink = { command, value ->
            when (command) {
                "torch" -> onTorchCommand(value == "1" || value == "true")
                "record" -> onRecordCommand(value == "start")
                "quality" -> setStreamQuality(value)
                "lens" -> onSwitchLensCommand()
            }
        }

        // Il server risponde a /status anche prima di avviare lo streaming: senza questo il QR
        // della telecamera non era interrogabile e l'accoppiamento falliva sempre.
        StreamingHub.server.start()

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
                    if (_uiState.value.currentScreen == Screen.MONITOR) {
                        // Aggiungere o rimuovere una telecamera cambia i riquadri della griglia.
                        syncMonitorStreams()
                    }
                }
            }
            startStatusPolling()
        }

        // Registrazioni e impostazioni vivono su Room: sopravvivono alla chiusura dell'app.
        if (recordingRepository == null) {
            val database = AppDatabase.getInstance(context)

            val recordings = RecordingRepository(database.recordingDao())
            recordingRepository = recordings
            viewModelScope.launch {
                recordings.recordings.collect { saved ->
                    _uiState.update { it.copy(recordings = saved) }
                    // Salvataggi ed eliminazioni cambiano lo spazio occupato: si rilegge.
                    refreshStorageStats()
                }
            }

            val persistedSettings = SettingsRepository(database.settingsDao())
            settingsRepository = persistedSettings
            viewModelScope.launch {
                val saved = persistedSettings.get()
                // Le qualità salvate da versioni precedenti (2K/4K) non esistono più.
                val normalized = saved.copy(
                    videoQuality = StreamQuality.fromLabel(saved.videoQuality).label
                )
                motionDetector.sensitivity = normalized.motionSensitivity
                _uiState.update { it.copy(settings = normalized) }
                if (normalized != saved) {
                    persistedSettings.save(normalized)
                }
                if (normalized.remoteAccessWebRtc) {
                    remoteAccessManager?.startRemoteAccess()
                }
            }
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

    /** Comando ricevuto dalla telecamera: il flash va acceso davvero, non solo nello stato. */
    override fun onTorchCommand(enabled: Boolean) {
        _uiState.update { it.copy(isTorchOn = enabled && it.isBackCamera) }
        CameraStreamingService.applyTorch(enabled)
    }

    override fun onSwitchLensCommand() {
        val useBack = !_uiState.value.isBackCamera
        _uiState.update { it.copy(isBackCamera = useBack, isTorchOn = false) }
        CameraStreamingService.applyLens(useBack)
        CameraStreamingService.applyTorch(false)
    }

    override fun onZoomCommand(ratio: Float) {
        setZoom(ratio)
        CameraStreamingService.applyZoom(ratio)
    }

    override fun onTriggerSnapshotCommand() {
        takeSnapshot()
    }

    override fun onRecordCommand(start: Boolean) {
        if (start) {
            startRealRecording(isMotionTriggered = false)
        } else {
            stopMotionRecording(isMotionTriggered = false)
        }
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

            // L'evento è già pubblicato dal servizio: sono i telefoni del centro di controllo a
            // notificarlo, non il telefono che sta riprendendo.

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
        // Anche la clip da movimento è un file reale: senza videocamera attiva non si inventa nulla.
        if (!startRealRecording(isMotionTriggered = true)) return

        if (_uiState.value.settings.notificationsEnabled) {
            CscNotificationHelper.sendRecordingNotification(context, cameraName, "Rilevamento movimento automatico")
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
        // È il servizio a chiudere il file: la riga in Room nasce quando arriva ClipResult.
        CameraStreamingService.stopClip()
        _uiState.update {
            it.copy(
                isRecording = false,
                isAutoRecordingFromMotion = false,
                recordingDurationSeconds = 0
            )
        }
    }

    /** Avvia una clip reale su MediaStore; false se la videocamera non è attiva. */
    private fun startRealRecording(isMotionTriggered: Boolean): Boolean {
        if (!_uiState.value.isStreamingActive) {
            _uiState.update { it.copy(toastMessage = "Avvia la videocamera prima di registrare") }
            return false
        }

        val started = CameraStreamingService.startClip(
            MediaFormat.clipFileName(System.currentTimeMillis())
        )
        if (!started) {
            _uiState.update { it.copy(toastMessage = "Registrazione non disponibile: riavvia la videocamera") }
            return false
        }

        clipCameraName = getSelectedDevice()?.name ?: getLocalDevice().name
        clipIsMotionEvent = isMotionTriggered
        _uiState.update {
            it.copy(
                isRecording = true,
                isAutoRecordingFromMotion = isMotionTriggered,
                recordingDurationSeconds = 0,
                toastMessage = if (isMotionTriggered) "Movimento rilevato! Registrazione avviata" else null
            )
        }

        startRecordingTimer()
        return true
    }

    /** Il file è stato finalizzato: si salva la riga con durata e dimensione reali. */
    private fun handleClipFinalized(clip: ClipResult) {
        recordingTimerJob?.cancel()
        autoStopMotionRecordingJob?.cancel()

        if (!clip.success) {
            _uiState.update {
                it.copy(
                    isRecording = false,
                    isAutoRecordingFromMotion = false,
                    recordingDurationSeconds = 0,
                    toastMessage = "Registrazione non riuscita"
                )
            }
            return
        }

        val now = System.currentTimeMillis()
        val item = RecordingItem(
            id = UUID.randomUUID().toString(),
            title = if (clipIsMotionEvent) {
                "Movimento ${MediaFormat.formatDuration(clip.durationSeconds)}"
            } else {
                "Clip ${MediaFormat.formatDuration(clip.durationSeconds)}"
            },
            timestamp = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(now)),
            durationSeconds = clip.durationSeconds,
            isMotionEvent = clipIsMotionEvent,
            isSnapshot = false,
            cameraName = clipCameraName,
            uri = clip.uri,
            fileSizeBytes = clip.sizeBytes,
            createdAtMillis = now,
            mimeType = clip.mimeType
        )
        viewModelScope.launch { recordingRepository?.save(item) }
        _uiState.update {
            it.copy(
                isRecording = false,
                isAutoRecordingFromMotion = false,
                recordingDurationSeconds = 0,
                toastMessage = "Clip salvata (${MediaFormat.formatSize(clip.sizeBytes)})"
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

        if (screen == Screen.CAMERA) {
            // L'IP può essere cambiato: il QR deve mostrare quello attuale.
            appContext?.let { initLocalIp(it) }
        }

        if (screen == Screen.PLAYBACK) {
            refreshStorageStats()
        }

        if (screen == Screen.MONITOR) {
            refreshSelectedStatus()
            syncMonitorStreams()
        } else if (_uiState.value.previousScreen == Screen.MONITOR && screen != Screen.MONITOR) {
            multiStreamManager.clear()
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
            multiStreamManager.clear()
            streamClient.disconnect()
            twoWayAudioClient?.stopAmbientListening()
            twoWayAudioClient?.stopPushToTalk()
            _uiState.update { it.copy(isAudioListening = false, isTwoWayTalkActive = false) }
        }
    }

    fun initLocalIp(context: Context) {
        try {
            CscNotificationHelper.createChannels(context)
            // WifiManager deprecato restituiva 0 e il QR usciva con ip vuoto.
            val ipStr = LocalAddress.resolve(context)
            if (!ipStr.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        localIpAddress = ipStr,
                        devices = it.devices.map { d ->
                            if (d.isLocalPhone) d.copy(ipAddress = ipStr) else d
                        }
                    )
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
            // La qualità scelta dal centro di controllo vale già dalla prima sessione.
            val quality = _uiState.value.settings.videoQuality
            CameraStreamingService.applyQuality(quality)
            streamingServer.publishQuality(quality)

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
        // Resta in ascolto per /status: l'accoppiamento e il monitor funzionano anche a
        // streaming fermo, senza trasmettere alcun fotogramma.
        StreamingHub.server.start()
    }

    fun toggleStealthPowerSaving() {
        _uiState.update { it.copy(isStealthPowerSavingActive = !it.isStealthPowerSavingActive) }
    }

    fun toggleLens() {
        _uiState.update { it.copy(isBackCamera = !it.isBackCamera, isTorchOn = false) }
    }

    fun toggleTorch() {
        if (!_uiState.value.isBackCamera) {
            _uiState.update {
                it.copy(toastMessage = "Il flash è disponibile solo con la fotocamera posteriore")
            }
            return
        }
        val enabled = !_uiState.value.isTorchOn
        _uiState.update { it.copy(isTorchOn = enabled) }
        CameraStreamingService.applyTorch(enabled)
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
        val state = _uiState.value
        if (!state.isRecording) {
            if (state.isStreamingActive) {
                startRealRecording(isMotionTriggered = false)
            } else if (getSelectedDevice() != null) {
                if (state.clientStats.status != ConnectionStatus.CONNECTED) {
                    _uiState.update {
                        it.copy(toastMessage = "Telecamera non collegata: riconnetti prima di registrare")
                    }
                    return
                }
                // Il monitor non ha la fotocamera: la clip la registra la telecamera.
                streamClient.sendRemoteCommand("record", "start")
                _uiState.update {
                    it.copy(
                        isRecording = true,
                        isAutoRecordingFromMotion = false,
                        recordingDurationSeconds = 0,
                        toastMessage = "Registrazione avviata sulla telecamera"
                    )
                }
                startRecordingTimer()
            } else {
                _uiState.update {
                    it.copy(toastMessage = "Avvia la videocamera prima di registrare")
                }
            }
        } else if (state.isStreamingActive) {
            stopMotionRecording(isMotionTriggered = state.isAutoRecordingFromMotion)
        } else {
            streamClient.sendRemoteCommand("record", "stop")
            recordingTimerJob?.cancel()
            _uiState.update {
                it.copy(
                    isRecording = false,
                    isAutoRecordingFromMotion = false,
                    recordingDurationSeconds = 0,
                    toastMessage = "Registrazione fermata: la clip è salvata sulla telecamera"
                )
            }
        }
    }

    private fun startRecordingTimer() {
        recordingTimerJob?.cancel()
        recordingTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _uiState.update { it.copy(recordingDurationSeconds = it.recordingDurationSeconds + 1) }
            }
        }
    }

    fun takeSnapshot() {
        val context = appContext ?: return
        val cameraJpeg = CameraStreamingService.latestFrame
        val received = clientLiveBitmap.value
        if (cameraJpeg == null && received == null) {
            _uiState.update {
                it.copy(toastMessage = "Istantanea non disponibile: avvia la videocamera o apri il monitor")
            }
            return
        }

        // Si salva il JPEG del servizio o, sul telefono del monitor, il fotogramma ricevuto.
        viewModelScope.launch {
            val jpeg = cameraJpeg ?: withContext(Dispatchers.Default) {
                java.io.ByteArrayOutputStream().use { out ->
                    received?.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    out.toByteArray()
                }
            }
            if (jpeg.isEmpty()) {
                _uiState.update { it.copy(toastMessage = "Istantanea non disponibile") }
                return@launch
            }
            val now = System.currentTimeMillis()
            val saved = MediaStoreStorage.saveSnapshot(
                context,
                jpeg,
                MediaFormat.snapshotFileName(now)
            )
            if (saved == null) {
                _uiState.update { it.copy(toastMessage = "Salvataggio dell'istantanea non riuscito") }
                return@launch
            }

            val nowStr = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(now))
            val snapshot = RecordingItem(
                id = UUID.randomUUID().toString(),
                title = "Istantanea $nowStr",
                timestamp = nowStr,
                durationSeconds = 0,
                isSnapshot = true,
                cameraName = getSelectedDevice()?.name ?: getLocalDevice().name,
                uri = saved.uri.toString(),
                fileSizeBytes = saved.sizeBytes,
                createdAtMillis = now,
                mimeType = saved.mimeType
            )
            recordingRepository?.save(snapshot)
            _uiState.update {
                it.copy(
                    toastMessage = "Istantanea salvata (${MediaFormat.formatSize(saved.sizeBytes)})"
                )
            }
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

    /** Ruota la visuale del monitor di 90° a ogni tocco. */
    fun rotateView() {
        _uiState.update {
            it.copy(viewRotationDegrees = (it.viewRotationDegrees + 90) % 360)
        }
    }

    /** Il centro di controllo cambia la qualità: si salva e si chiede alla telecamera. */
    fun setStreamQuality(label: String) {
        val quality = StreamQuality.fromLabel(label)
        updateSettings { it.copy(videoQuality = quality.label) }
    }

    override fun onQualityCommand(quality: String) {
        val parsed = StreamQuality.fromLabel(quality)
        CameraStreamingService.applyQuality(parsed.label)
        streamingServer.publishQuality(parsed.label)
    }

    /** Spazio reale del telefono: niente più "64 GB" scritto a mano. */
    fun refreshStorageStats() {
        val context = appContext ?: return
        viewModelScope.launch {
            val usage = withContext(Dispatchers.IO) {
                val dir = context.getExternalFilesDir(null) ?: context.filesDir
                val stat = StatFs(dir.absolutePath)
                (stat.blockCountLong * stat.blockSizeLong) to
                    (stat.availableBlocksLong * stat.blockSizeLong)
            }
            val recordingsBytes = recordingRepository?.totalBytes() ?: 0L
            _uiState.update {
                it.copy(
                    storageTotalBytes = usage.first,
                    storageFreeBytes = usage.second,
                    recordingsBytes = recordingsBytes
                )
            }
        }
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
            if (_uiState.value.monitorLayout <= 1) {
                streamClient.connect(dev.ipAddress, dev.port)
                if (_uiState.value.isAudioListening) {
                    twoWayAudioClient?.startAmbientListening(dev.ipAddress, dev.port)
                }
            } else {
                // La griglia resta sui suoi flussi: la selezione serve al doppio tocco.
                multiStreamManager.clear()
            }
        }
    }

    /** Cambia il numero di riquadri: la griglia collega solo quelli visibili. */
    fun setMonitorLayout(count: Int) {
        val safe = if (count == 1 || count == 2 || count == 4) count else 1
        _uiState.update { it.copy(monitorLayout = safe) }
        if (_uiState.value.currentScreen == Screen.MONITOR) syncMonitorStreams()
    }

    fun reconnectGridStream(deviceId: String) {
        multiStreamManager.reconnect(deviceId)
    }

    /**
     * Modalità singola: un solo flusso, con audio e comandi legati alla telecamera scelta.
     * Modalità griglia: fino a quattro flussi da 12 fps e 640 px, uno per riquadro visibile.
     */
    private fun syncMonitorStreams() {
        val state = _uiState.value
        if (state.currentScreen != Screen.MONITOR) {
            multiStreamManager.clear()
            return
        }

        if (state.monitorLayout <= 1) {
            multiStreamManager.clear()
            val device = getSelectedDevice()
            if (device != null) {
                streamClient.connect(device.ipAddress, device.port)
                if (state.isAudioListening) {
                    twoWayAudioClient?.startAmbientListening(device.ipAddress, device.port)
                }
            } else {
                streamClient.disconnect()
            }
            return
        }

        // Una telecamera non deve essere aperta due volte: in griglia niente flusso singolo.
        streamClient.disconnect()
        val targets = state.devices
            .take(state.monitorLayout)
            .map { GridTarget(id = it.id, ip = it.ipAddress, port = it.port) }
        multiStreamManager.setStreams(targets)
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
                // Sul centro di controllo si interroga più spesso: l'allarme deve arrivare subito.
                delay(if (_uiState.value.currentScreen == Screen.MONITOR) 2_000 else 5_000)
            }
        }
    }

    fun refreshSelectedStatus() {
        viewModelScope.launch { pollDeviceStatuses() }
    }

    /**
     * Gli avvisi di movimento arrivano qui, sul telefono che guarda: la telecamera li pubblica
     * soltanto su /status.
     */
    private fun notifyMotionIfNeeded(device: CameraDevice, motionAt: Long) {
        if (motionAt <= 0L || motionAt <= lastNotifiedMotionAt) return
        lastNotifiedMotionAt = motionAt

        // Un allarme già vecchio quando si apre il monitor non deve suonare.
        if (System.currentTimeMillis() - motionAt > 20_000L) return
        if (!_uiState.value.settings.notificationsEnabled) return
        val context = appContext ?: return

        CscNotificationHelper.sendMotionAlert(context, device.name, device.location)
        _uiState.update {
            it.copy(isMotionAlarmActive = true, lastMotionTimestamp = motionAt)
        }
        motionResetJob?.cancel()
        motionResetJob = viewModelScope.launch {
            delay(3000)
            _uiState.update { it.copy(isMotionAlarmActive = false) }
        }
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

        val selectedDevice = _uiState.value.selectedDeviceId?.let { id ->
            _uiState.value.devices.firstOrNull { it.id == id }
        }
        val selectedProbe = selectedDevice?.let { results[it.id] }
        if (selectedDevice != null && selectedProbe is ProbeResult.Reachable) {
            notifyMotionIfNeeded(selectedDevice, selectedProbe.motionAt)
        }
    }

    fun deleteRecording(id: String) {
        val context = appContext
        viewModelScope.launch {
            val stored = recordingRepository?.find(id)
                ?: _uiState.value.recordings.firstOrNull { it.id == id }
            val removed = if (context != null && stored != null) {
                MediaStoreStorage.delete(context, stored.uri.ifEmpty { stored.filePath })
            } else {
                false
            }
            recordingRepository?.delete(id)
            _uiState.update {
                it.copy(
                    recordings = it.recordings.filter { rec -> rec.id != id },
                    toastMessage = if (removed) {
                        "File eliminato dal dispositivo"
                    } else {
                        "Voce eliminata dall'elenco"
                    }
                )
            }
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        var toPersist: AppSettings? = null
        _uiState.update {
            val updated = transform(it.settings)
            toPersist = updated
            motionDetector.sensitivity = updated.motionSensitivity
            if (updated.remoteAccessWebRtc != it.settings.remoteAccessWebRtc) {
                if (updated.remoteAccessWebRtc) {
                    remoteAccessManager?.startRemoteAccess()
                } else {
                    remoteAccessManager?.stopRemoteAccess()
                }
            }
            if (updated.videoQuality != it.settings.videoQuality) {
                // La nuova qualità va applicata alla telecamera, non solo salvata.
                streamClient.sendRemoteCommand("quality", updated.videoQuality)
            }
            it.copy(settings = updated)
        }
        toPersist?.let { updated ->
            viewModelScope.launch { settingsRepository?.save(updated) }
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
        CameraStreamingService.clipSink = null
        CameraStreamingService.motionSink = null
    }
}
