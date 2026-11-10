package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.DeviceType
import com.example.pairing.CameraProbe
import com.example.pairing.PairingPayload
import com.example.pairing.ProbeResult
import com.example.pairing.QrScannerView
import com.example.ui.theme.CscGlassCard
import com.example.ui.theme.CscGlassBorder
import com.example.ui.theme.CscIceWhite
import com.example.ui.theme.CscLiveRed
import com.example.ui.theme.CscOrange
import com.example.ui.theme.CscPanelBlue
import com.example.ui.theme.CscSteel
import com.example.ui.theme.CscSubtitleGray
import com.example.ui.theme.CscTextMuted
import kotlinx.coroutines.launch

private enum class PairMode { QR, MANUAL }

/**
 * Pannello unico per accoppiare una telecamera: scansione del QR mostrato dall'altro telefono
 * oppure inserimento manuale di nome, IP e porta. Il pulsante "Prova connessione" interroga
 * davvero http://ip:porta/status e il salvataggio è consentito solo se la telecamera risponde.
 */
@Composable
fun PairCameraDialog(
    isStreamingActive: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, location: String, ip: String, port: Int, type: DeviceType) -> Unit
) {
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(if (isStreamingActive) PairMode.MANUAL else PairMode.QR) }
    var name by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var ip by remember { mutableStateOf("") }
    var portText by remember { mutableStateOf(PairingPayload.DEFAULT_PORT.toString()) }
    var isIpCamera by remember { mutableStateOf(false) }
    var probing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ProbeResult?>(null) }
    var scanError by remember { mutableStateOf<String?>(null) }

    fun probe() {
        val cleanIp = ip.trim()
        val port = portText.trim().toIntOrNull()
        when {
            !PairingPayload.isValidIp(cleanIp) -> {
                result = ProbeResult.Unexpected("indirizzo IP non valido")
                return
            }
            port == null || !PairingPayload.isValidPort(port) -> {
                result = ProbeResult.Unexpected("porta non valida")
                return
            }
        }
        probing = true
        scope.launch {
            result = CameraProbe.probe(cleanIp, port!!)
            probing = false
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = CscPanelBlue,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pair_camera_dialog")
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Accoppia telecamera",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = CscIceWhite
                )
                Text(
                    text = "Scansiona il QR mostrato dal telefono videocamera, oppure inserisci indirizzo e porta.",
                    fontSize = 12.sp,
                    color = CscSubtitleGray
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeButton(
                        label = "Scansiona QR",
                        selected = mode == PairMode.QR,
                        modifier = Modifier.weight(1f)
                    ) {
                        mode = PairMode.QR
                    }
                    ModeButton(
                        label = "Inserisci a mano",
                        selected = mode == PairMode.MANUAL,
                        modifier = Modifier.weight(1f)
                    ) {
                        mode = PairMode.MANUAL
                    }
                }

                when (mode) {
                    PairMode.QR -> {
                        if (isStreamingActive) {
                            Text(
                                text = "La fotocamera è occupata dallo streaming: arrestalo, oppure inserisci l'indirizzo a mano.",
                                fontSize = 12.sp,
                                color = CscOrange
                            )
                        } else {
                            QrScannerView(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp),
                                onQrCode = { raw ->
                                    val info = PairingPayload.parse(raw)
                                    if (info == null) {
                                        scanError = "QR non riconosciuto: $raw"
                                    } else {
                                        scanError = null
                                        result = null
                                        ip = info.ip
                                        portText = info.port.toString()
                                        if (name.isBlank()) name = info.id ?: "Videocamera ${info.ip}"
                                        mode = PairMode.MANUAL
                                        probe()
                                    }
                                }
                            )
                            Text(
                                text = "Inquadra il QR di CSCSecurityCam",
                                fontSize = 11.sp,
                                color = CscTextMuted
                            )
                        }
                        scanError?.let { message ->
                            Text(text = message, fontSize = 11.sp, color = CscLiveRed)
                        }
                    }

                    PairMode.MANUAL -> {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Nome telecamera") },
                            singleLine = true,
                            colors = dialogFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = location,
                            onValueChange = { location = it },
                            label = { Text("Ambiente / Stanza") },
                            singleLine = true,
                            colors = dialogFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = ip,
                                onValueChange = {
                                    ip = it
                                    result = null
                                },
                                label = { Text("Indirizzo IP") },
                                placeholder = { Text("192.168.1.20") },
                                singleLine = true,
                                isError = ip.isNotBlank() && !PairingPayload.isValidIp(ip),
                                colors = dialogFieldColors(),
                                modifier = Modifier.weight(2f)
                            )
                            OutlinedTextField(
                                value = portText,
                                onValueChange = {
                                    portText = it.filter { ch -> ch.isDigit() }
                                    result = null
                                },
                                label = { Text("Porta") },
                                singleLine = true,
                                colors = dialogFieldColors(),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    isIpCamera = !isIpCamera
                                    if (isIpCamera && portText == PairingPayload.DEFAULT_PORT.toString()) {
                                        portText = "554"
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isIpCamera) CscOrange else Color.Transparent)
                                    .border(
                                        1.dp,
                                        if (isIpCamera) CscOrange else CscSteel,
                                        RoundedCornerShape(6.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isIpCamera) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = "Videocamera IP (RTSP / ONVIF)",
                                fontSize = 12.sp,
                                color = CscIceWhite
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { probe() },
                                enabled = !probing,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("probe_connection_btn")
                            ) {
                                if (probing) {
                                    CircularProgressIndicator(
                                        color = CscOrange,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.size(6.dp))
                                }
                                Text("Prova connessione", color = CscIceWhite, fontSize = 12.sp)
                            }
                        }

                        result?.let { probeResult ->
                            val reachable = probeResult is ProbeResult.Reachable
                            Text(
                                text = probeResult.describe(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (reachable) Color(0xFF4ADE80) else CscLiveRed
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Annulla", color = CscTextMuted)
                    }
                    Spacer(modifier = Modifier.size(6.dp))
                    Button(
                        onClick = {
                            val port = portText.trim().toIntOrNull() ?: return@Button
                            onSave(
                                name.ifBlank { "Videocamera" },
                                location.ifBlank { "Casa" },
                                ip.trim(),
                                port,
                                if (isIpCamera) DeviceType.IP_ONVIF else DeviceType.SMARTPHONE
                            )
                        },
                        // Si salva solo se il dispositivo ha risposto davvero a /status.
                        enabled = result is ProbeResult.Reachable && !probing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CscOrange,
                            disabledContainerColor = CscGlassCard
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("save_paired_device_btn")
                    ) {
                        Text("Salva", color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) CscOrange.copy(alpha = 0.22f) else CscGlassCard)
            .border(
                1.dp,
                if (selected) CscOrange else CscGlassBorder,
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) CscOrange else CscIceWhite
        )
    }
}

@Composable
private fun dialogFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = CscOrange,
    unfocusedBorderColor = CscSteel,
    focusedTextColor = CscIceWhite,
    unfocusedTextColor = CscIceWhite
)
