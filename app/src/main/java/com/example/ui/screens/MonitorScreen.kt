package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MotionPhotosOn
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.engine.ClientStreamStats
import com.example.engine.ConnectionStatus
import com.example.media.StreamQuality
import com.example.model.CameraDevice
import com.example.model.DeviceType
import com.example.model.Screen
import com.example.ui.components.PairCameraDialog
import com.example.ui.components.CscBottomNav
import com.example.ui.components.PtzController
import com.example.ui.theme.CscBackground
import com.example.ui.theme.CscGlassBorder
import com.example.ui.theme.CscGlassCard
import com.example.ui.theme.CscIceWhite
import com.example.ui.theme.CscLiveRed
import com.example.ui.theme.CscMidnightBlue
import com.example.ui.theme.CscOnlineGreen
import com.example.ui.theme.CscOrange
import com.example.ui.theme.CscOrangeGlow
import com.example.ui.theme.CscPanelBlue
import com.example.ui.theme.CscSteel
import com.example.ui.theme.CscTextMuted
import com.example.viewmodel.MainUiState

@Composable
fun MonitorScreen(
    state: MainUiState,
    liveBitmap: Bitmap?,
    gridFrames: Map<String, Bitmap?>,
    gridStats: Map<String, ClientStreamStats>,
    onSelectLayout: (Int) -> Unit,
    onSelectDevice: (String) -> Unit,
    onReconnectTile: (String) -> Unit,
    onBack: () -> Unit,
    onNavigate: (Screen) -> Unit,
    onReconnect: () -> Unit,
    onToggleAudio: () -> Unit,
    onTakeSnapshot: () -> Unit,
    onToggleRecord: () -> Unit,
    onRotateView: () -> Unit,
    onSelectQuality: (String) -> Unit,
    onPanTilt: (dx: Float, dy: Float) -> Unit,
    onResetPtz: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onToggleNightVision: () -> Unit,
    onToggleMotionDetection: () -> Unit,
    onRemoteTorch: () -> Unit,
    onRemoteFlipLens: () -> Unit,
    onTwoWayTalk: (Boolean) -> Unit,
    onSavePairedDevice: (name: String, location: String, ip: String, port: Int, type: DeviceType) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    var showPairDialog by remember { mutableStateOf(false) }

    val pulseTransition = rememberInfiniteTransition(label = "pulse_live")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Solo il dispositivo selezionato: nessun ripiego sul primo della lista.
    val dev = state.devices.firstOrNull { it.id == state.selectedDeviceId }
    val isConnected = state.clientStats.status == ConnectionStatus.CONNECTED

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CscBackground,
        bottomBar = {
            CscBottomNav(
                currentScreen = Screen.MONITOR,
                onNavigate = onNavigate
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Torna indietro",
                        tint = CscIceWhite
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Centro di controllo",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = CscIceWhite
                )
            }

            // Subheader: Videocamera IP Status
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp, start = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val statusDotColor = when (state.clientStats.status) {
                    ConnectionStatus.CONNECTED -> CscOnlineGreen
                    ConnectionStatus.CONNECTING, ConnectionStatus.RECONNECTING -> CscOrange
                    ConnectionStatus.ERROR -> CscLiveRed
                    ConnectionStatus.DISCONNECTED -> CscTextMuted
                }

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusDotColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                val statusLabel = when (state.clientStats.status) {
                    ConnectionStatus.CONNECTED -> "Online"
                    ConnectionStatus.CONNECTING -> "Connessione..."
                    ConnectionStatus.RECONNECTING -> "Riconnessione..."
                    ConnectionStatus.ERROR -> "Offline"
                    ConnectionStatus.DISCONNECTED -> "In attesa"
                }
                Text(
                    text = dev?.let { "Videocamera: ${it.name} • ${it.ipAddress}:${it.port}" }
                        ?: "Nessuna telecamera accoppiata",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = CscIceWhite
                )
            }

            // Selettore riquadri: 1, 2 o 4 telecamere insieme.
            MonitorLayoutSelector(
                selected = state.monitorLayout,
                onSelect = onSelectLayout
            )

            if (state.monitorLayout > 1) {
                MultiCameraGrid(
                    devices = state.devices,
                    layout = state.monitorLayout,
                    frames = gridFrames,
                    stats = gridStats,
                    nightVision = state.nightVisionActive,
                    onOpenCamera = { id ->
                        onSelectDevice(id)
                        onSelectLayout(1)
                    },
                    onReconnectTile = onReconnectTile,
                    onPair = { showPairDialog = true }
                )
            }

            // Live Viewport Container (singola telecamera)
            if (state.monitorLayout == 1) Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9.5f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF0B111E))
                    .border(1.dp, CscSteel, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (liveBitmap != null) {
                    // Il video ricevuto ruota con la visuale; a 90° e 270° il riquadro viene
                    // ridimensionato per continuare a riempire il monitor.
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        val rotated = (state.viewRotationDegrees / 90) % 2 == 1
                        Box(
                            modifier = (if (rotated) {
                                Modifier.size(width = maxHeight, height = maxWidth)
                            } else {
                                Modifier.fillMaxSize()
                            })
                                .rotate(state.viewRotationDegrees.toFloat())
                                .offset {
                                    IntOffset(
                                        state.panOffset.first.toInt(),
                                        state.panOffset.second.toInt()
                                    )
                                }
                                .scale(state.zoomRatio),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = liveBitmap.asImageBitmap(),
                                contentDescription = "Live Security Stream Feed",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                colorFilter = if (state.nightVisionActive) {
                                    ColorFilter.lighting(Color(0xFF88FF88), Color(0xFF003300))
                                } else null
                            )
                        }
                    }
                } else {
                    // Fallback / Loading / Connection state view
                    when (state.clientStats.status) {
                        ConnectionStatus.CONNECTING, ConnectionStatus.RECONNECTING -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(color = CscOrange, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (state.clientStats.status == ConnectionStatus.CONNECTING) "Connessione al flusso LAN..." else "Riconnessione automatica...",
                                    color = CscIceWhite,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        ConnectionStatus.ERROR -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = CscLiveRed, modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (dev == null) "Nessuna telecamera selezionata" else "Videocamera non raggiungibile",
                                    color = CscIceWhite,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = when {
                                        dev == null -> "Accoppia una telecamera per iniziare a ricevere il video"
                                        state.selectedDeviceReachable == false ->
                                            "Nessuna risposta da ${dev.ipAddress}:${dev.port}: la telecamera è spenta, su un'altra Wi-Fi o l'app non è aperta"
                                        else ->
                                            "La telecamera risponde ma non invia video: lo streaming è spento su questo telefono"
                                    },
                                    color = CscTextMuted,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                                state.clientStats.errorMessage?.let { message ->
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = message,
                                        color = CscLiveRed,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                state.selectedDeviceProbeMessage?.let { message ->
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = message,
                                        color = CscTextMuted,
                                        fontSize = 10.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { if (dev == null) showPairDialog = true else onReconnect() },
                                        colors = ButtonDefaults.buttonColors(containerColor = CscPanelBlue),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, tint = CscOrange, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (dev == null) "Accoppia telecamera" else "Riconnetti",
                                            color = CscIceWhite,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                        else -> {
                            if (dev == null) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text(
                                        text = "Nessuna telecamera",
                                        color = CscIceWhite,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Accoppia una telecamera scansionando il suo QR o inserendo IP e porta.",
                                        color = CscTextMuted,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = { showPairDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = CscOrange),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.testTag("monitor_pair_camera_btn")
                                    ) {
                                        Text("Accoppia telecamera", color = Color.White, fontSize = 12.sp)
                                    }
                                }
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Videocam,
                                        contentDescription = null,
                                        tint = CscSubtitleGray,
                                        modifier = Modifier.size(34.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Anteprima non disponibile",
                                        color = CscIceWhite,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Avvia lo streaming sulla telecamera",
                                        color = CscTextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Night Vision Overlay
                if (state.nightVisionActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x2200FF55))
                    )
                }

                // Top-Left LIVE Badge
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isConnected) CscLiveRed else CscSteel)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .alpha(if (isConnected) pulseAlpha else 0.5f)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isConnected) {
                            if (state.settings.remoteAccessWebRtc) "LIVE • WebRTC" else "LIVE • LAN"
                        } else "OFFLINE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (isConnected && state.settings.remoteAccessWebRtc) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Crittografia DTLS/SRTP attiva",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                // Badge risoluzione/fps: solo a flusso attivo, per non coprire lo stato vuoto.
                if (isConnected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        val resText = if (state.clientStats.currentFps > 0) {
                            "${state.clientStats.resolution} ${state.clientStats.currentFps} fps"
                        } else {
                            "1920 × 1080"
                        }
                        Text(
                            text = resText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = CscIceWhite
                        )
                    }
                }

                if (state.viewRotationDegrees != 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Ruotata ${state.viewRotationDegrees}°",
                            fontSize = 11.sp,
                            color = CscIceWhite
                        )
                    }
                }

                // Active Recording Overlay Timer
                if (state.isRecording) {
                    val minutes = state.recordingDurationSeconds / 60
                    val seconds = state.recordingDurationSeconds % 60
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .alpha(pulseAlpha)
                                .clip(CircleShape)
                                .background(CscLiveRed)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = String.format("%02d:%02d", minutes, seconds),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CscLiveRed
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Toolbar (Speaker, Snapshot, REC, Fullscreen) - Frosted Glass
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(CscGlassCard)
                    .border(1.dp, CscGlassBorder, RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Speaker / Audio Listen
                IconButton(
                    onClick = onToggleAudio,
                    modifier = Modifier
                        .size(46.dp)
                        .testTag("action_audio_btn")
                ) {
                    Icon(
                        imageVector = if (state.isAudioListening) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeMute,
                        contentDescription = "Ascolto audio ambientale",
                        tint = if (state.isAudioListening) CscOrange else CscIceWhite,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Snapshot / Photo Capture
                IconButton(
                    onClick = onTakeSnapshot,
                    modifier = Modifier
                        .size(46.dp)
                        .testTag("action_snapshot_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = "Istantanea",
                        tint = CscIceWhite,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Glowing Neon REC Button
                Row(
                    modifier = Modifier
                        .shadow(
                            elevation = if (state.isRecording) 12.dp else 2.dp,
                            shape = RoundedCornerShape(20.dp),
                            spotColor = if (state.isRecording) CscLiveRed else Color.Transparent
                        )
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (state.isRecording) CscLiveRed else Color(0x99263345))
                        .border(
                            1.2.dp,
                            if (state.isRecording) CscLiveRed else CscGlassBorder,
                            RoundedCornerShape(20.dp)
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { onToggleRecord() })
                        }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .testTag("action_rec_btn"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FiberManualRecord,
                        contentDescription = "Registra video",
                        tint = if (state.isRecording) Color.White else CscLiveRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (state.isRecording) "STOP" else "REC",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Rotazione della visuale: 0°, 90°, 180°, 270°
                IconButton(
                    onClick = onRotateView,
                    modifier = Modifier
                        .size(46.dp)
                        .testTag("action_rotate_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.RotateRight,
                        contentDescription = "Ruota visuale",
                        tint = if (state.viewRotationDegrees != 0) CscOrange else CscIceWhite,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Qualità del flusso chiesta alla telecamera (AUTO = miglior compromesso)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CscGlassCard)
                    .border(1.dp, CscGlassBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = null,
                    tint = CscOrange,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Qualità",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = CscTextMuted
                )
                Spacer(modifier = Modifier.width(8.dp))

                val activeQuality = StreamQuality.fromLabel(state.settings.videoQuality)
                StreamQuality.options().forEach { quality ->
                    val selected = activeQuality == quality
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (selected) CscOrange else Color.Transparent)
                            .border(
                                1.dp,
                                if (selected) CscOrange else CscSteel,
                                RoundedCornerShape(9.dp)
                            )
                            .clickable { onSelectQuality(quality.label) }
                            .padding(vertical = 6.dp)
                            .testTag("quality_${quality.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = quality.shortLabel,
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) Color.White else CscIceWhite,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // PTZ D-pad Controller
            PtzController(
                onPanTilt = onPanTilt,
                onHome = onResetPtz,
                onZoomIn = onZoomIn,
                onZoomOut = onZoomOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Two-Way Audio / Push-to-Talk Status Banner
            if (state.isTwoWayTalkActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CscOrange.copy(alpha = 0.22f))
                        .border(1.dp, CscOrange, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = CscOrange,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "🎤 TRASMISSIONE VOCE IN CORSO • Parla ora",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CscOrange
                    )
                }
            } else if (state.isAudioListening) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CscPanelBlue)
                        .border(1.dp, CscSteel.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = CscOnlineGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "🔊 Ascolto ambientale attivo (16 kHz PCM)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = CscIceWhite
                    )
                }
            }

            // Riga finale: tre pulsanti arrotondati, come nel riferimento.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MonitorToolButton(
                    label = "Notte",
                    icon = Icons.Default.Nightlight,
                    active = state.nightVisionActive,
                    onClick = onToggleNightVision,
                    modifier = Modifier.weight(1f)
                )
                MonitorToolButton(
                    label = "Torcia",
                    icon = Icons.Default.FlashOn,
                    active = state.isTorchOn,
                    onClick = onRemoteTorch,
                    modifier = Modifier.weight(1f)
                )

                // Push-to-talk: si tiene premuto per parlare.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (state.isTwoWayTalkActive) CscOrange else CscPanelBlue)
                        .border(
                            1.dp,
                            if (state.isTwoWayTalkActive) CscOrangeGlow else CscSteel,
                            RoundedCornerShape(14.dp)
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    onTwoWayTalk(true)
                                    tryAwaitRelease()
                                    onTwoWayTalk(false)
                                }
                            )
                        }
                        .testTag("action_ptt_mic_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Parla",
                            tint = if (state.isTwoWayTalkActive) Color.White else CscIceWhite,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Parla",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (state.isTwoWayTalkActive) Color.White else CscIceWhite,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showPairDialog) {
        PairCameraDialog(
            isStreamingActive = state.isStreamingActive,
            onDismiss = { showPairDialog = false },
            onSave = { name, location, ip, port, type ->
                onSavePairedDevice(name, location, ip, port, type)
                showPairDialog = false
            }
        )
    }
}

/** Selettore del numero di riquadri visibili. */
@Composable
private fun MonitorLayoutSelector(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CscGlassCard)
            .border(1.dp, CscGlassBorder, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(1, 2, 4).forEach { count ->
            val active = selected == count
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (active) CscOrange else Color.Transparent)
                    .clickable { onSelect(count) }
                    .padding(vertical = 8.dp)
                    .testTag("monitor_layout_$count"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (count == 1) "1" else "$count",
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = if (active) Color.White else CscIceWhite
                )
            }
        }
    }
}

/** Griglia 2×2 (o riga singola a due riquadri) con nome e stato sotto ogni telecamera. */
@Composable
private fun MultiCameraGrid(
    devices: List<CameraDevice>,
    layout: Int,
    frames: Map<String, Bitmap?>,
    stats: Map<String, ClientStreamStats>,
    nightVision: Boolean,
    onOpenCamera: (String) -> Unit,
    onReconnectTile: (String) -> Unit,
    onPair: () -> Unit
) {
    // Una riga per 2 telecamere, due righe per 4: la disposizione resta 2×2.
    val rows = (0 until layout).chunked(2)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { rowIndexes ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowIndexes.forEach { index ->
                    val device = devices.getOrNull(index)
                    MonitorTile(
                        device = device,
                        bitmap = device?.let { frames[it.id] },
                        stats = device?.let { stats[it.id] },
                        nightVision = nightVision,
                        onOpen = { device?.let { onOpenCamera(it.id) } },
                        onReconnect = { device?.let { onReconnectTile(it.id) } },
                        onPair = onPair,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MonitorTile(
    device: CameraDevice?,
    bitmap: Bitmap?,
    stats: ClientStreamStats?,
    nightVision: Boolean,
    onOpen: () -> Unit,
    onReconnect: () -> Unit,
    onPair: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status = stats?.status ?: ConnectionStatus.DISCONNECTED
    val connected = status == ConnectionStatus.CONNECTED

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0B111E))
                .border(1.dp, CscSteel, RoundedCornerShape(16.dp))
                .pointerInput(device?.id) {
                    if (device != null) {
                        detectTapGestures(onDoubleTap = { onOpen() })
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            when {
                device == null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Slot libero", color = CscTextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Accoppia",
                        color = CscOrange,
                        fontSize = 11.sp,
                        modifier = Modifier.clickable(onClick = onPair)
                    )
                }

                bitmap != null -> Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = device.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    colorFilter = if (nightVision) {
                        ColorFilter.lighting(Color(0xFF88FF88), Color(0xFF003300))
                    } else null
                )

                status == ConnectionStatus.ERROR -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = CscLiveRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Text("Offline", color = CscIceWhite, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(CscPanelBlue)
                            .clickable(onClick = onReconnect)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = CscOrange,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Riconnetti", color = CscIceWhite, fontSize = 10.sp)
                    }
                }

                status == ConnectionStatus.CONNECTING || status == ConnectionStatus.RECONNECTING -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = CscOrange, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Connessione...", color = CscTextMuted, fontSize = 10.sp)
                }

                else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = CscSubtitleGray,
                        modifier = Modifier.size(23.dp)
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Text("Nessun flusso video", color = CscTextMuted, fontSize = 10.sp)
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (connected) CscLiveRed else CscSteel)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (connected) "LIVE" else "OFF",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = device?.name ?: "Nessuna telecamera",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (device == null) CscTextMuted else CscIceWhite,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (device != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                when (status) {
                                    ConnectionStatus.CONNECTED -> CscOnlineGreen
                                    ConnectionStatus.CONNECTING,
                                    ConnectionStatus.RECONNECTING -> CscOrange
                                    ConnectionStatus.ERROR -> CscLiveRed
                                    ConnectionStatus.DISCONNECTED -> CscTextMuted
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = monitorStatusLabel(status),
                        fontSize = 10.sp,
                        color = CscTextMuted
                    )
                }
            }
        }
    }
}

private fun monitorStatusLabel(status: ConnectionStatus): String = when (status) {
    ConnectionStatus.CONNECTED -> "Online"
    ConnectionStatus.CONNECTING -> "Connessione..."
    ConnectionStatus.RECONNECTING -> "Riconnessione..."
    ConnectionStatus.ERROR -> "Offline"
    ConnectionStatus.DISCONNECTED -> "In attesa"
}

/** Pulsante arrotondato della riga finale del Centro di controllo. */
@Composable
private fun MonitorToolButton(
    label: String,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) CscOrange.copy(alpha = 0.18f) else CscPanelBlue)
            .border(
                1.dp,
                if (active) CscOrange else CscSteel,
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) CscOrange else CscIceWhite,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (active) CscOrange else CscIceWhite,
            maxLines = 1
        )
    }
}
