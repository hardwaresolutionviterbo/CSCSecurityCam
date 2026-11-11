package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.example.MainActivity
import com.example.R
import com.example.camera.CameraStreamAnalyzer
import com.example.engine.StreamingHub
import com.example.media.MediaStoreStorage
import com.example.media.StreamQuality
import com.example.motion.MotionDetector
import com.example.motion.MotionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Servizio in primo piano che possiede la sessione CameraX: la cattura continua con l'app in
 * background e lo schermo spento, perché non dipende più dal ciclo di vita della schermata Camera.
 *
 * La PreviewView della schermata si aggancia a questa stessa sessione tramite
 * [attachPreviewSurface], così non esistono due sessioni CameraX contemporanee sulla fotocamera.
 */
/** Esito della clip: uri reale, durata e dimensione lette dal file finalizzato. */
data class ClipResult(
    val uri: String,
    val durationSeconds: Int,
    val sizeBytes: Long,
    val mimeType: String,
    val success: Boolean
)

class CameraStreamingService : Service(), LifecycleOwner {

    private val serviceLifecycle = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = serviceLifecycle

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val qualityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var autoQualityJob: Job? = null
    private var autoFallbackActive = false
    private var appliedQuality = StreamQuality.AUTO
    private var pendingQualityRebind = false
    private val motionDetector = MotionDetector(sensitivity = 0.65f)

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var previewUseCase: Preview? = null
    private var analysisUseCase: ImageAnalysis? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null

    @Volatile
    private var useBackCamera = true
    private var torchEnabled = false
    private var zoomRatioValue = 1f

    companion object {
        private const val TAG = "CameraStreamingService"

        const val CHANNEL_ID = "csc_security_cam_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START_STREAMING"
        const val ACTION_STOP = "ACTION_STOP_STREAMING"

        @Volatile
        var isRunning: Boolean = false
            private set

        /** Il ViewModel registra qui il destinatario dei fotogrammi codificati. */
        @Volatile
        var frameSink: ((ByteArray) -> Unit)? = null

        /** Il ViewModel registra qui gli eventi del rilevamento movimento. */
        @Volatile
        var motionSink: ((MotionResult) -> Unit)? = null

        /** Il ViewModel registra qui la sincronizzazione quando lo stop arriva dalla notifica. */
        @Volatile
        var stopSink: (() -> Unit)? = null

        /** Ultimo fotogramma JPEG: è la base delle istantanee reali. */
        @Volatile
        var latestFrame: ByteArray? = null

        /** Il ViewModel riceve qui l'esito della clip quando il file è finalizzato. */
        @Volatile
        var clipSink: ((ClipResult) -> Unit)? = null

        /** Avvia una clip video in MediaStore; false se il servizio non è in esecuzione. */
        fun startClip(displayName: String): Boolean =
            instance?.startVideoClip(displayName) ?: false

        fun stopClip() {
            instance?.stopVideoClip()
        }

        @Volatile
        private var attachedSurface: Preview.SurfaceProvider? = null

        @Volatile
        private var instance: CameraStreamingService? = null

        /**
         * Aggancia la PreviewView della schermata alla sessione del servizio. Passare null la
         * stacca: la cattura continua comunque, senza superficie di anteprima.
         */
        fun attachPreviewSurface(provider: Preview.SurfaceProvider?) {
            attachedSurface = provider
            instance?.applyPreviewSurface(provider)
        }

        fun applyTorch(enabled: Boolean) {
            instance?.setTorch(enabled)
        }

        fun applyZoom(ratio: Float) {
            instance?.setZoom(ratio)
        }

        fun applyLens(useBack: Boolean) {
            instance?.setLens(useBack)
        }

        /** Qualità richiesta dal centro di controllo; vale anche alla sessione successiva. */
        @Volatile
        var streamQualityLabel: String = StreamQuality.AUTO.label
            private set

        fun applyQuality(label: String) {
            val quality = StreamQuality.fromLabel(label)
            streamQualityLabel = quality.label
            instance?.setStreamQuality(quality)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        serviceLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "CSCSecurityCam:StreamingWakeLock"
        )

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        wifiLock = wifiManager?.createWifiLock(
            WifiManager.WIFI_MODE_FULL_HIGH_PERF,
            "CSCSecurityCam:WifiLock"
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopStreaming()
                stopSink?.invoke()
                stopSelf()
            }
            else -> startStreaming()
        }
        return START_STICKY
    }

    private fun startStreaming() {
        if (isRunning) return
        isRunning = true

        // Il server LAN appartiene al servizio: resta attivo finché la videocamera è accesa.
        StreamingHub.server.start()
        startAutoQualityWatch()

        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire(24 * 60 * 60 * 1000L /* 24 hours */)
        }
        if (wifiLock?.isHeld == false) {
            wifiLock?.acquire()
        }

        val notification = buildNotification("Videocamera attiva e in streaming LAN • Background attivo")
        // Android 14+ richiede che i tipi dichiarati nel manifest corrispondano a quelli passati
        // qui: il servizio usa davvero fotocamera e microfono.
        val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, foregroundServiceType)
        } catch (e: Exception) {
            // Su Android 14+ avviare un servizio foreground con tipo camera da background non è
            // consentito: non deve però far crashare l'app.
            Log.e(TAG, "startForeground non riuscito", e)
        }

        serviceLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        serviceLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        bindCamera()
    }

    private fun bindCamera() {
        val providerFuture = try {
            ProcessCameraProvider.getInstance(this)
        } catch (e: Exception) {
            Log.e(TAG, "ProcessCameraProvider non disponibile", e)
            return
        }

        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                cameraProvider = provider
                provider.unbindAll()

                val preview = Preview.Builder().build()
                previewUseCase = preview
                applyPreviewSurface(attachedSurface)

                // La qualità scelta dal monitor diventa la risoluzione reale dell'analyzer:
                // niente full HD dichiarato e poi trasmesso a 640 pixel.
                val streamQuality = StreamQuality.fromLabel(streamQualityLabel)
                appliedQuality = streamQuality
                val dimensions = streamQuality.dimensions(autoFallbackActive)
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .setTargetResolution(Size(dimensions.first, dimensions.second))
                    .build()
                analysisUseCase = imageAnalysis

                // Registrazione video reale: CameraX usa MediaRecorder/MediaMuxer e scrive
                // direttamente l'MP4 in MediaStore.
                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.from(
                            Quality.FHD,
                            FallbackStrategy.lowerQualityOrHigherThan(Quality.HD)
                        )
                    )
                    .build()
                val recorderCapture = VideoCapture.withOutput(recorder)
                videoCapture = recorderCapture

                imageAnalysis.setAnalyzer(
                    analysisExecutor,
                    CameraStreamAnalyzer(
                        onYuvFrame = { yuv, width, height, rotation ->
                            // Stessi fotogrammi per LAN e WebRTC: nessun doppio accesso alla camera.
                            com.example.remote.RemotePublishHub.pushFrame(
                                yuv,
                                width,
                                height,
                                rotation
                            )
                        },
                        onFrameEncoded = { bytes ->
                            latestFrame = bytes
                            frameSink?.invoke(bytes)
                            // Il server è di processo: i fotogrammi continuano ad arrivare ai
                            // monitor anche con l'app chiusa o lo schermo spento.
                            StreamingHub.server.broadcastFrame(bytes)
                        },
                        motionDetector = motionDetector,
                        onMotionResult = { result ->
                            // L'evento va al server di processo: il centro di controllo lo vede
                            // anche quando l'app della telecamera è chiusa.
                            if (result.hasMotion) StreamingHub.server.publishMotion()
                            motionSink?.invoke(result)
                        }
                    )
                )

                val selector = if (useBackCamera) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                }

                camera = provider.bindToLifecycle(
                    this,
                    selector,
                    preview,
                    imageAnalysis,
                    recorderCapture
                )
                applyTorchState()
                applyZoomState()
                Log.i(TAG, "Sessione CameraX del servizio attiva")
            } catch (e: Exception) {
                Log.e(TAG, "Binding CameraX del servizio non riuscito", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun applyPreviewSurface(provider: Preview.SurfaceProvider?) {
        previewUseCase?.setSurfaceProvider(provider)
    }

    private fun setTorch(enabled: Boolean) {
        torchEnabled = enabled
        applyTorchState()
    }

    private fun applyTorchState() {
        try {
            if (useBackCamera) {
                camera?.cameraControl?.enableTorch(torchEnabled)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Torch non disponibile", e)
        }
    }

    private fun setZoom(ratio: Float) {
        zoomRatioValue = ratio
        applyZoomState()
    }

    private fun applyZoomState() {
        try {
            camera?.cameraControl?.setZoomRatio(zoomRatioValue.coerceIn(1f, 5f))
        } catch (e: Exception) {
            Log.w(TAG, "Zoom non disponibile", e)
        }
    }

    private fun setLens(useBack: Boolean) {
        if (useBack == useBackCamera) return
        useBackCamera = useBack
        torchEnabled = false
        // Cambiare obiettivo significa riaprire la sessione: la vecchia viene chiusa prima.
        rebindCamera()
    }

    private fun rebindCamera() {
        unbindOwnUseCases()
        bindCamera()
    }

    private fun setStreamQuality(quality: StreamQuality) {
        if (appliedQuality == quality && !pendingQualityRebind) return
        // Cambiare risoluzione durante una clip la interromperebbe: si rimanda alla fine.
        if (activeRecording != null) {
            pendingQualityRebind = true
            return
        }
        pendingQualityRebind = false
        autoFallbackActive = false
        rebindCamera()
    }

    /**
     * AUTO = miglior compromesso: parte da 720p e, se il monitor resta sotto gli 8 fps per 5
     * secondi, scende a 480p; torna a 720p quando il flusso supera i 20 fps.
     */
    private fun startAutoQualityWatch() {
        if (autoQualityJob?.isActive == true) return
        autoQualityJob = qualityScope.launch {
            var slowTicks = 0
            StreamingHub.server.stats.collect { stats ->
                val isAuto = StreamQuality.fromLabel(streamQualityLabel) == StreamQuality.AUTO
                if (!isAuto || !isRunning || activeRecording != null ||
                    stats.connectedClients == 0 || stats.currentFps <= 0
                ) {
                    slowTicks = 0
                    return@collect
                }

                when {
                    stats.currentFps < 8 -> {
                        slowTicks++
                        if (slowTicks >= 5 && !autoFallbackActive) {
                            autoFallbackActive = true
                            rebindCamera()
                        }
                    }
                    stats.currentFps >= 20 -> {
                        slowTicks = 0
                        if (autoFallbackActive) {
                            autoFallbackActive = false
                            rebindCamera()
                        }
                    }
                    else -> slowTicks = 0
                }
            }
        }
    }

    /**
     * Sgancia solo le use case del servizio: un unbindAll spegnerebbe anche la Preview che la
     * schermata riaggancia quando lo streaming viene fermato dall'interfaccia.
     */
    private fun unbindOwnUseCases() {
        val provider = cameraProvider
        stopVideoClip()
        val useCases = listOfNotNull(previewUseCase, analysisUseCase, videoCapture).toTypedArray()
        if (provider != null && useCases.isNotEmpty()) {
            try {
                provider.unbind(*useCases)
            } catch (e: Exception) {
                Log.w(TAG, "unbind delle use case del servizio non riuscito", e)
            }
        }
        camera = null
        previewUseCase = null
        analysisUseCase = null
        videoCapture = null
    }

    private fun startVideoClip(displayName: String): Boolean {
        val capture = videoCapture ?: return false
        if (activeRecording != null) return false

        val name = if (displayName.endsWith(".mp4", ignoreCase = true)) {
            displayName
        } else {
            "$displayName.mp4"
        }

        return try {
            val outputOptions = MediaStoreOutputOptions.Builder(
                contentResolver,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            )
                .setContentValues(MediaStoreStorage.videoContentValues(name))
                .build()

            activeRecording = capture.output
                .prepareRecording(this, outputOptions)
                .start(ContextCompat.getMainExecutor(this)) { event ->
                    if (event is VideoRecordEvent.Finalize) {
                        activeRecording = null
                        if (pendingQualityRebind) {
                            pendingQualityRebind = false
                            rebindCamera()
                        }
                        val uri = event.outputResults.outputUri
                        val success = !event.hasError() && uri != Uri.EMPTY
                        val seconds = (event.recordingStats.recordedDurationNanos / 1_000_000_000L)
                            .toInt()
                            .coerceAtLeast(0)
                        val size = if (success) MediaStoreStorage.querySizeBytes(this, uri) else 0L
                        clipSink?.invoke(
                            ClipResult(
                                uri = uri.toString(),
                                durationSeconds = seconds,
                                sizeBytes = size,
                                mimeType = MediaStoreStorage.VIDEO_MIME,
                                success = success
                            )
                        )
                    }
                }
            SystemClock.elapsedRealtime()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Avvio registrazione non riuscito", e)
            activeRecording = null
            false
        }
    }

    private fun stopVideoClip() {
        try {
            activeRecording?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Stop registrazione non riuscito", e)
        }
    }

    private fun stopStreaming() {
        // Anche se lo stato interno non era "running", il server condiviso va fermato qui.
        StreamingHub.server.stop()
        if (!isRunning) {
            releaseLocks()
            return
        }
        isRunning = false
        // Senza streaming non esiste un fotogramma corrente: evita istantanee vecchie.
        latestFrame = null

        serviceLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        serviceLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP)

        unbindOwnUseCases()

        releaseLocks()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun releaseLocks() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        if (wifiLock?.isHeld == true) {
            wifiLock?.release()
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, CameraStreamingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CSC SecurityCam")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Arresta", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "CSC SecurityCam Servizio di Streaming",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifica persistente per lo streaming della videocamera in background e a schermo spento"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopStreaming()
        serviceLifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        autoQualityJob?.cancel()
        qualityScope.cancel()
        analysisExecutor.shutdown()
        if (instance === this) instance = null
        attachedSurface = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
