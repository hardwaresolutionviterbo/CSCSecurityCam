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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CameraDevice
import com.example.model.DeviceType
import com.example.model.Screen
import com.example.ui.components.CscBottomNav
import com.example.ui.components.PairCameraDialog
import com.example.ui.theme.CscBackground
import com.example.ui.theme.CscGlassBorder
import com.example.ui.theme.CscGlassCard
import com.example.ui.theme.CscIceWhite
import com.example.ui.theme.CscOnlineGreen
import com.example.ui.theme.CscOrange
import com.example.ui.theme.CscPanelBlue
import com.example.ui.theme.CscSubtitleGray
import com.example.ui.theme.CscTextMuted
import com.example.ui.theme.CscTitleWhite
import com.example.ui.theme.MontserratFontFamily
import com.example.viewmodel.MainUiState

@Composable
fun DevicesScreen(
    state: MainUiState,
    onBack: () -> Unit,
    onNavigate: (Screen) -> Unit,
    onSelectDevice: (String) -> Unit,
    onSaveDevice: (name: String, ip: String, location: String, type: DeviceType, port: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    var showPairDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CscBackground,
        bottomBar = {
            CscBottomNav(
                currentScreen = Screen.HOME,
                onNavigate = onNavigate
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showPairDialog = true },
                containerColor = CscOrange,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.testTag("add_device_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Accoppia telecamera")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
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
                    text = "Dispositivi",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = CscIceWhite
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "VIDEOCAMERE COLLEGATE (${state.devices.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CscTextMuted,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.devices.isEmpty()) {
                    item(key = "empty_devices") {
                        EmptyDevicesCard(onPair = { showPairDialog = true })
                    }
                }
                items(state.devices, key = { it.id }) { device ->
                    DeviceCard(
                        device = device,
                        isSelected = state.selectedDeviceId == device.id,
                        onClick = {
                            onSelectDevice(device.id)
                            onNavigate(Screen.MONITOR)
                        }
                    )
                }
            }
        }
    }

    if (showPairDialog) {
        PairCameraDialog(
            isStreamingActive = state.isStreamingActive,
            onDismiss = { showPairDialog = false },
            onSave = { name, location, ip, port, type ->
                onSaveDevice(name, ip, location, type, port)
                showPairDialog = false
            }
        )
    }
}

@Composable
private fun EmptyDevicesCard(onPair: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CscPanelBlue)
            .border(1.dp, CscGlassBorder, RoundedCornerShape(18.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Videocam,
            contentDescription = null,
            tint = CscOrange,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Nessuna telecamera accoppiata",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = CscTitleWhite,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Scansiona il QR mostrato dal secondo telefono, oppure inserisci indirizzo e porta. La telecamera viene salvata solo se risponde.",
            fontSize = 11.sp,
            color = CscSubtitleGray,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onPair,
            colors = ButtonDefaults.buttonColors(containerColor = CscOrange),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.testTag("empty_pair_camera_btn")
        ) {
            Text("Accoppia telecamera", color = Color.White, fontSize = 12.sp)
        }
    }
}

@Composable
fun DeviceCard(
    device: CameraDevice,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (isSelected) Color(0x33FF6A00) else CscGlassCard)
            .border(
                1.5.dp,
                if (isSelected) CscOrange else CscGlassBorder,
                RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected) CscOrange.copy(alpha = 0.25f) else Color(0xFF263345).copy(alpha = 0.7f))
                    .border(
                        1.dp,
                        if (isSelected) CscOrange else Color(0x33FFFFFF),
                        RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (device.type == DeviceType.SMARTPHONE) Icons.Default.Smartphone else Icons.Default.Videocam,
                    contentDescription = null,
                    tint = if (isSelected) CscOrange else CscIceWhite,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = device.name,
                        fontSize = 15.sp,
                        fontFamily = MontserratFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = CscTitleWhite
                    )
                    if (device.isLocalPhone) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(CscOrange.copy(alpha = 0.25f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "QUESTO DISPOSITIVO",
                                fontSize = 9.sp,
                                fontFamily = MontserratFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = CscOrange
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${device.location} • ${device.ipAddress}:${device.port}",
                    fontSize = 12.sp,
                    fontFamily = MontserratFontFamily,
                    color = CscSubtitleGray
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (device.isOnline) CscOnlineGreen else CscTextMuted)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (device.isOnline) "Online" else "Offline",
                        fontSize = 11.sp,
                        fontFamily = MontserratFontFamily,
                        color = if (device.isOnline) CscOnlineGreen else CscSubtitleGray
                    )

                    if (device.type == DeviceType.SMARTPHONE) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Icon(
                            imageVector = Icons.Default.BatteryStd,
                            contentDescription = null,
                            tint = CscSubtitleGray,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "${device.batteryPercent}%",
                            fontSize = 11.sp,
                            fontFamily = MontserratFontFamily,
                            color = CscSubtitleGray
                        )
                    }
                }
            }
        }
    }
}
