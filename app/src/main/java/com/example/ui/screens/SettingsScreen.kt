package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.VideoCameraBack
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Screen
import com.example.remote.RemoteConfigStore
import com.example.ui.components.CscBottomNav
import com.example.ui.components.RemoteViewerDialog
import com.example.ui.theme.CscBackground
import com.example.ui.theme.CscGlassBorder
import com.example.ui.theme.CscGlassCard
import com.example.ui.theme.CscIceWhite
import com.example.ui.theme.CscMidnightBlue
import com.example.ui.theme.CscOrange
import com.example.ui.theme.CscPanelBlue
import com.example.ui.theme.CscSteel
import com.example.ui.theme.CscSubtitleGray
import com.example.ui.theme.CscTextMuted
import com.example.ui.theme.CscRowDivider
import com.example.ui.theme.CscTitleWhite
import com.example.ui.theme.MontserratFontFamily
import com.example.viewmodel.MainUiState
import kotlin.math.abs

@Composable
fun SettingsScreen(
    state: MainUiState,
    onBack: () -> Unit,
    onNavigate: (Screen) -> Unit,
    onUpdateVideoQuality: (String) -> Unit,
    onUpdateAudioMode: (String) -> Unit,
    onToggleMotionDetection: (Boolean) -> Unit,
    onToggleAutoRecording: (Boolean) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onToggleRemoteAccess: (Boolean) -> Unit,
    onUpdateMotionSensitivity: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    var showQualityDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showSensitivityDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showRemoteDialog by remember { mutableStateOf(false) }
    var showRemoteViewer by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CscBackground,
        bottomBar = {
            CscBottomNav(
                currentScreen = Screen.SETTINGS,
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
                    text = "Impostazioni",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = CscIceWhite
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Elenco raggruppato: righe separate da 1dp bianco all'8%, come nel riferimento.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(CscGlassCard)
                    .border(1.dp, CscGlassBorder, RoundedCornerShape(20.dp))
            ) {
                // 1. Qualità video
                SettingNavigationCard(
                    title = "Qualità video",
                    subtitle = state.settings.videoQuality,
                    icon = Icons.Default.Videocam,
                    testTag = "setting_quality",
                    onClick = { showQualityDialog = true }
                )

                SettingsDivider()

                // 2. Audio
                SettingNavigationCard(
                    title = "Audio",
                    subtitle = state.settings.audioMode,
                    icon = Icons.Default.VolumeUp,
                    testTag = "setting_audio",
                    onClick = { showAudioDialog = true }
                )

                SettingsDivider()

                // 3. Rilevamento movimento (Switch + tuning)
                val sensitivityLabel = when {
                    state.settings.motionSensitivity > 0.75f -> "Alta"
                    state.settings.motionSensitivity < 0.45f -> "Bassa"
                    else -> "Media"
                }
                SettingSwitchCard(
                    title = "Rilevamento movimento",
                    subtitle = if (state.settings.motionDetectionEnabled) "Attivato • Sensibilità $sensitivityLabel" else "Disattivato",
                    icon = Icons.Default.DirectionsRun,
                    isChecked = state.settings.motionDetectionEnabled,
                    testTag = "setting_motion_switch",
                    onCheckedChange = onToggleMotionDetection,
                    onClickBody = { showSensitivityDialog = true }
                )

                SettingsDivider()

                // 4. Registrazione automatica (Switch)
                SettingSwitchCard(
                    title = "Registrazione automatica",
                    subtitle = "Solo su movimento",
                    icon = Icons.Default.VideoCameraBack,
                    isChecked = state.settings.autoRecordingOnlyMotion,
                    testTag = "setting_auto_rec_switch",
                    onCheckedChange = onToggleAutoRecording
                )

                SettingsDivider()

                // 5. Notifiche (Switch)
                SettingSwitchCard(
                    title = "Notifiche",
                    subtitle = "Su questo dispositivo",
                    icon = Icons.Default.Notifications,
                    isChecked = state.settings.notificationsEnabled,
                    testTag = "setting_notif_switch",
                    onCheckedChange = onToggleNotifications
                )

                SettingsDivider()

                // 6. Accesso remoto (Switch)
                SettingSwitchCard(
                    title = "Accesso remoto",
                    subtitle = if (state.settings.remoteAccessWebRtc) "Internet / WebRTC • Attivo" else "Internet / WebRTC • Disattivato",
                    icon = Icons.Default.Cloud,
                    isChecked = state.settings.remoteAccessWebRtc,
                    testTag = "setting_remote_switch",
                    onCheckedChange = onToggleRemoteAccess,
                    onClickBody = { showRemoteDialog = true }
                )

                SettingsDivider()

                // 7. Informazioni app
                SettingNavigationCard(
                    title = "Informazioni app",
                    subtitle = "v1.0.0",
                    icon = Icons.Default.Info,
                    testTag = "setting_info",
                    onClick = { showInfoDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Quality Selection Dialog
    if (showQualityDialog) {
        val qualities = com.example.media.StreamQuality.options().map { it.label }
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            containerColor = CscPanelBlue,
            title = {
                Text(
                    text = "Seleziona Qualità Video",
                    color = CscIceWhite,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    qualities.forEach { quality ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUpdateVideoQuality(quality)
                                    showQualityDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.settings.videoQuality == quality,
                                onClick = {
                                    onUpdateVideoQuality(quality)
                                    showQualityDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = CscOrange)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = quality, color = CscIceWhite, fontSize = 15.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) {
                    Text("Chiudi", color = CscOrange)
                }
            }
        )
    }

    // Audio Mode Selection Dialog
    if (showAudioDialog) {
        val modes = listOf("Bidirezionale", "Solo ascolto ambientale", "Audio disattivato")
        AlertDialog(
            onDismissRequest = { showAudioDialog = false },
            containerColor = CscPanelBlue,
            title = {
                Text(
                    text = "Seleziona Modalità Audio",
                    color = CscIceWhite,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    modes.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUpdateAudioMode(mode)
                                    showAudioDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.settings.audioMode == mode,
                                onClick = {
                                    onUpdateAudioMode(mode)
                                    showAudioDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = CscOrange)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = mode, color = CscIceWhite, fontSize = 15.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAudioDialog = false }) {
                    Text("Chiudi", color = CscOrange)
                }
            }
        )
    }

    // Sensitivity Selection Dialog
    if (showSensitivityDialog) {
        val sensitivityOptions = listOf(
            Triple("Bassa (35%)", 0.35f, "Riduce i falsi allarmi, rileva solo movimenti evidenti"),
            Triple("Media (65%)", 0.65f, "Bilanciata per ambienti interni ed esterni"),
            Triple("Alta (85%)", 0.85f, "Rileva anche movimenti minimi")
        )
        AlertDialog(
            onDismissRequest = { showSensitivityDialog = false },
            containerColor = CscPanelBlue,
            title = {
                Text(
                    text = "Sensibilità Rilevamento",
                    color = CscIceWhite,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sensitivityOptions.forEach { (label, value, desc) ->
                        val isSelected = abs(state.settings.motionSensitivity - value) < 0.15f
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUpdateMotionSensitivity(value)
                                    showSensitivityDialog = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    onUpdateMotionSensitivity(value)
                                    showSensitivityDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = CscOrange)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(text = label, color = CscIceWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(text = desc, color = CscTextMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSensitivityDialog = false }) {
                    Text("Chiudi", color = CscOrange)
                }
            }
        )
    }

    // App Info Dialog
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            containerColor = CscPanelBlue,
            title = {
                Text(
                    text = "CSC SecurityCam",
                    color = CscOrange,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Versione: 1.0.0 (Build 2026)", color = CscIceWhite, fontSize = 14.sp)
                    Text("Piattaforma: Videosorveglianza Smart", color = CscTextMuted, fontSize = 13.sp)
                    Text("Architettura: CameraX, H.264, Local LAN & RTSP/ONVIF", color = CscTextMuted, fontSize = 13.sp)
                    Text("Design: Dark Premium Smart Surveillance", color = CscTextMuted, fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("OK", color = CscOrange)
                }
            }
        )
    }

    // WebRTC Remote Access Configuration Dialog
    if (showRemoteDialog) {
        val stats = state.remoteTunnelStats
        val settingsContext = LocalContext.current
        val remoteStore = remember { RemoteConfigStore(settingsContext) }
        var remoteConfig by remember { mutableStateOf(remoteStore.load()) }
        AlertDialog(
            onDismissRequest = { showRemoteDialog = false },
            containerColor = CscPanelBlue,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Cloud, contentDescription = null, tint = CscOrange, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Accesso Remoto WebRTC",
                        color = CscOrange,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Consente di visualizzare la videocamera da fuori casa tramite rete dati 4G/5G o un'altra connessione Wi-Fi.",
                        color = CscTextMuted,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = remoteConfig.signalingUrl,
                        onValueChange = { remoteConfig = remoteConfig.copy(signalingUrl = it) },
                        label = { Text("Server di signaling (wss://...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = remoteConfig.roomCode,
                        onValueChange = { remoteConfig = remoteConfig.copy(roomCode = it) },
                        label = { Text("Codice stanza") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = remoteConfig.turnUrl,
                        onValueChange = { remoteConfig = remoteConfig.copy(turnUrl = it) },
                        label = { Text("TURN (turn:host:3478)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = remoteConfig.turnUsername,
                            onValueChange = { remoteConfig = remoteConfig.copy(turnUsername = it) },
                            label = { Text("Utente TURN") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = remoteConfig.turnPassword,
                            onValueChange = { remoteConfig = remoteConfig.copy(turnPassword = it) },
                            label = { Text("Password TURN") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    RemoteInfoRow(label = "Stato Servizio", value = if (state.settings.remoteAccessWebRtc) "Attivo (Online)" else "Disattivato")
                    RemoteInfoRow(label = "Attraversamento NAT", value = stats.natType)
                    RemoteInfoRow(label = "IP Pubblico Rilevato", value = stats.publicIp ?: "In attesa STUN...")
                    RemoteInfoRow(label = "Porta Esterna", value = if (stats.publicPort > 0) stats.publicPort.toString() else "8765")
                    RemoteInfoRow(label = "Codice Associazione P2P", value = stats.pairingCode)
                    RemoteInfoRow(label = "Crittografia Flusso", value = stats.encryptionProtocol)
                    RemoteInfoRow(label = "Server STUN", value = stats.stunServer)
                    RemoteInfoRow(label = "Server Relay TURN", value = stats.turnServer)
                }
            },
            confirmButton = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        remoteConfig = remoteStore.save(remoteConfig)
                        showRemoteViewer = true
                    }) {
                        Text("Guarda", color = CscOrange)
                    }
                    TextButton(onClick = {
                        remoteConfig = remoteStore.save(remoteConfig)
                    }) {
                        Text("Salva", color = CscOrange)
                    }
                    TextButton(onClick = { showRemoteDialog = false }) {
                        Text("Chiudi", color = CscOrange)
                    }
                }
            }
        )
    }

    if (showRemoteViewer) {
        RemoteViewerDialog(onDismiss = { showRemoteViewer = false })
    }
}

@Composable
private fun RemoteInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = CscTextMuted, fontSize = 12.sp)
        Text(text = value, color = CscIceWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SettingNavigationCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF263345).copy(alpha = 0.7f))
                    .border(1.dp, CscGlassBorder, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = CscOrange,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontFamily = MontserratFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = CscTitleWhite,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    fontFamily = MontserratFontFamily,
                    fontWeight = FontWeight.Normal,
                    color = CscSubtitleGray
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = CscSteel,
            modifier = Modifier.size(15.dp)
        )
    }
}

@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 68.dp)
            .height(1.dp)
            .background(CscRowDivider)
    )
}

@Composable
fun SettingSwitchCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isChecked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit,
    onClickBody: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .then(if (onClickBody != null) Modifier.clickable { onClickBody() } else Modifier)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF263345).copy(alpha = 0.7f))
                    .border(1.dp, CscGlassBorder, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = CscOrange,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontFamily = MontserratFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = CscTitleWhite,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    fontFamily = MontserratFontFamily,
                    fontWeight = FontWeight.Normal,
                    color = CscSubtitleGray
                )
            }
        }

        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = CscOrange,
                uncheckedThumbColor = CscSubtitleGray,
                uncheckedTrackColor = Color(0xFF1E2838)
            )
        )
    }
}
