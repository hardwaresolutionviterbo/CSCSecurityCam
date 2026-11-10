package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.camera.CameraXPreview
import com.example.ui.components.QrCodeView
import com.example.ui.theme.CscBackground
import com.example.ui.theme.CscGlassBorder
import com.example.ui.theme.CscGlassCard
import com.example.ui.theme.CscLiveRed
import com.example.ui.theme.CscNeonGlow
import com.example.ui.theme.CscOnlineGreen
import com.example.ui.theme.CscOrange
import com.example.ui.theme.CscOrangeGlow
import com.example.ui.theme.CscSubtitleGray
import com.example.ui.theme.CscTitleWhite
import com.example.viewmodel.MainUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CameraScreen(
    state: MainUiState,
    onBack: () -> Unit,
    onToggleStreaming: () -> Unit,
    onToggleLens: () -> Unit,
    onToggleTorch: () -> Unit,
    onToggleStealth: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler {
        if (state.isStealthPowerSavingActive) {
            onToggleStealth()
        } else {
            onBack()
        }
    }
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "live_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CscBackground)
    ) {
        // OLED Stealth / Screen Saver Mode Overlay
        if (state.isStealthPowerSavingActive) {
            val timeString = remember { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { onToggleStealth() }
                        )
                    }
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = timeString,
                        fontSize = 54.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .alpha(pulseAlpha)
                                .clip(CircleShape)
                                .background(CscOnlineGreen)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CSC SecurityCam • Streaming Attivo",
                            fontSize = 12.sp,
                            color = Color(0xFF334155)
                        )
                    }
                    Spacer(modifier = Modifier.height(30.dp))
                    Text(
                        text = "MODALITÀ RISPARMIO AMOLED ATTIVA\nTocca due volte per riattivare lo schermo",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp,
                        color = Color(0xFF1E293B),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Standard Cyber-Security Camera Screen UI
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CscBackground)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                // Top Bar with Frosted Glass Action Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(CscGlassCard)
                                .border(1.dp, CscGlassBorder, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Torna indietro",
                                tint = CscTitleWhite,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Videocamera",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = CscTitleWhite
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Battery Status Badge
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(CscGlassCard)
                                .border(1.dp, CscGlassBorder, RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (state.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
                                contentDescription = null,
                                tint = if (state.isCharging) CscOrange else if (state.batteryPercentage <= 20) CscLiveRed else CscOnlineGreen,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${state.batteryPercentage}%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CscTitleWhite
                            )
                        }

                        // Stealth Power Saving Button
                        IconButton(
                            onClick = onToggleStealth,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(CscGlassCard)
                                .border(1.dp, CscGlassBorder, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bedtime,
                                contentDescription = "Modalità Risparmio Schermo",
                                tint = CscTitleWhite,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Torch Button
                        IconButton(
                            onClick = onToggleTorch,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (state.isTorchOn) CscOrange.copy(alpha = 0.25f) else CscGlassCard)
                                .border(
                                    1.dp,
                                    if (state.isTorchOn) CscOrange else CscGlassBorder,
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = if (state.isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "Torcia",
                                tint = if (state.isTorchOn) CscOrange else CscTitleWhite,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Lens Switch Button
                        IconButton(
                            onClick = onToggleLens,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(CscGlassCard)
                                .border(1.dp, CscGlassBorder, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cameraswitch,
                                contentDescription = "Cambia fotocamera",
                                tint = CscTitleWhite,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Camera Feed Container with Rounded Corners & Cyber Neon Badges
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 10f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.5.dp, CscGlassBorder, RoundedCornerShape(22.dp))
                ) {
                    if (hasCameraPermission) {
                        CameraXPreview(
                            isBackCamera = state.isBackCamera,
                            isTorchOn = state.isTorchOn,
                            zoomRatio = state.zoomRatio,
                            isStreaming = state.isStreamingActive,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Autorizzazione fotocamera richiesta",
                                color = CscSubtitleGray,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Top-Center Motion Alert Banner
                    if (state.isMotionAlarmActive) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 10.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CscOrange)
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚠️ MOVIMENTO RILEVATO",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // Top-Left LIVE Badge with pulsing red neon dot
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(CscLiveRed)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .alpha(pulseAlpha)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "LIVE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Loudspeaker Audio Indicator
                    if (state.isCameraSpeakerActive) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 12.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CscOrange)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🔊 VOCE DALL'OPERATORE (Altoparlante)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // Bottom-Right Resolution / FPS Cyber Badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        val fpsVal = if (state.serverStats.currentFps > 0) state.serverStats.currentFps else 30
                        Text(
                            text = "1080p $fpsVal fps",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = CscTitleWhite
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Subtitle: SCANSIONA CON IL SECONDO TELEFONO (Montserrat uppercase with widened tracking)
                Text(
                    text = "SCANSIONA CON IL SECONDO TELEFONO",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = CscTitleWhite,
                    letterSpacing = 2.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Stylized QR Code Box with Cyber Orange Corner Brackets
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(200.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(CscGlassCard)
                        .border(2.dp, CscOrange, RoundedCornerShape(20.dp))
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val qrData = if (state.settings.remoteAccessWebRtc) {
                        "csc://cam?ip=${state.localIpAddress}&port=${state.streamingPort}&remote=1&p2p=${state.remoteTunnelStats.pairingCode}&nat=${state.remoteTunnelStats.natType}"
                    } else {
                        "csc://cam?ip=${state.localIpAddress}&port=${state.streamingPort}&id=cam_local"
                    }
                    QrCodeView(
                        data = qrData,
                        modifier = Modifier.fillMaxSize(),
                        backgroundColor = Color.White,
                        codeColor = Color(0xFF0A0F1C),
                        cornerColor = CscOrange
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Real-time Streaming Indicator with Green Pulse
                Row(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (state.isStreamingActive) CscOnlineGreen else CscSubtitleGray)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (state.isStreamingActive) {
                            val clientCount = state.serverStats.connectedClients
                            val clientText = if (clientCount == 1) "1 client" else "$clientCount client"
                            "Streaming LAN attivo • Porta ${state.streamingPort} • $clientText"
                        } else {
                            "Streaming in pausa • ${state.localIpAddress}"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (state.isStreamingActive) CscTitleWhite else CscSubtitleGray
                    )
                }

                if (state.settings.remoteAccessWebRtc) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = null,
                            tint = CscOrange,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "WebRTC P2P attivo • ${state.remoteTunnelStats.pairingCode}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = CscOrange
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Primary Start/Stop Streaming Button - Pill Shaped (border-radius: 50px) with Neon Glow
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .shadow(
                            elevation = 14.dp,
                            shape = RoundedCornerShape(50.dp),
                            spotColor = if (state.isStreamingActive) CscLiveRed else CscOrange,
                            ambientColor = if (state.isStreamingActive) Color(0x66EF4444) else CscNeonGlow
                        )
                ) {
                    Button(
                        onClick = onToggleStreaming,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("camera_toggle_stream_btn"),
                        shape = RoundedCornerShape(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.isStreamingActive) CscLiveRed else CscOrange,
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Icon(
                            imageVector = if (state.isStreamingActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (state.isStreamingActive) "Arresta videocamera" else "Avvia videocamera",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
