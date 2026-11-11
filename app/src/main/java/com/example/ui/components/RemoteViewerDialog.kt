package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.example.remote.RemoteConfigStore
import com.example.remote.WebRtcViewer
import com.example.ui.theme.CscGlassCard
import com.example.ui.theme.CscIceWhite
import com.example.ui.theme.CscLiveRed
import com.example.ui.theme.CscOrange
import com.example.ui.theme.CscPanelBlue
import com.example.ui.theme.CscSubtitleGray
import com.example.ui.theme.CscTextMuted
import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer

/**
 * Visione da fuori casa: entra nella stanza della telecamera via WebRTC e mostra il video.
 * Torcia e registrazione passano sul data channel, quindi funzionano anche senza LAN.
 */
@Composable
fun RemoteViewerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember { RemoteConfigStore(context) }
    var config by remember { mutableStateOf(store.load()) }
    var status by remember { mutableStateOf("Pronto: inserisci stanza e server") }
    var error by remember { mutableStateOf<String?>(null) }
    var connected by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var viewer by remember { mutableStateOf<WebRtcViewer?>(null) }

    val rendererEgl = remember { EglBase.create() }
    val renderer = remember {
        SurfaceViewRenderer(context).apply {
            init(rendererEgl.eglBaseContext, null)
            setEnableHardwareScaler(true)
            setMirror(false)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewer?.stop()
            viewer = null
            renderer.release()
            rendererEgl.release()
        }
    }

    fun connect() {
        val saved = store.save(config)
        config = saved
        if (!saved.hasSignaling) {
            error = "Server di signaling non valido: serve wss://"
            return
        }
        error = null
        val instance = WebRtcViewer(context, saved, renderer) { state ->
            status = state.detail ?: status
            error = state.error
            connected = state.connected
        }
        viewer = instance
        instance.start()
    }

    Dialog(onDismissRequest = {
        viewer?.stop()
        onDismiss()
    }) {
        Surface(
            color = CscPanelBlue,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("remote_viewer_dialog")
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Guarda da remoto",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = CscIceWhite
                )
                Text(
                    text = "Video cifrato DTLS/SRTP: il server di signaling vede solo SDP e candidati ICE.",
                    fontSize = 11.sp,
                    color = CscSubtitleGray
                )

                OutlinedTextField(
                    value = config.roomCode,
                    onValueChange = { config = config.copy(roomCode = it) },
                    label = { Text("Codice stanza della telecamera") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = config.signalingUrl,
                    onValueChange = { config = config.copy(signalingUrl = it) },
                    label = { Text("Server signaling (wss://...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = config.turnUrl,
                    onValueChange = { config = config.copy(turnUrl = it) },
                    label = { Text("TURN (opzionale, turn:host:3478)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = config.turnUsername,
                        onValueChange = { config = config.copy(turnUsername = it) },
                        label = { Text("Utente TURN") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = config.turnPassword,
                        onValueChange = { config = config.copy(turnPassword = it) },
                        label = { Text("Password TURN") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                AndroidView(
                    factory = { renderer },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .testTag("remote_video_view")
                )

                Text(text = status, fontSize = 12.sp, color = CscOrange)
                error?.let { Text(text = it, fontSize = 12.sp, color = CscLiveRed) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { connect() },
                        enabled = viewer == null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CscOrange,
                            disabledContainerColor = CscGlassCard
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("remote_connect_btn")
                    ) {
                        Text("Connetti", color = Color.White, fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            torchOn = !torchOn
                            viewer?.sendCommand("torch", if (torchOn) "1" else "0")
                        },
                        enabled = connected,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Torcia", color = CscIceWhite, fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            recording = !recording
                            viewer?.sendCommand("record", if (recording) "start" else "stop")
                        },
                        enabled = connected,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (recording) "Stop REC" else "REC", color = CscIceWhite, fontSize = 12.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        viewer?.stop()
                        viewer = null
                        connected = false
                        status = "Disconnesso"
                    }) {
                        Text("Disconnetti", color = CscTextMuted)
                    }
                    Spacer(modifier = Modifier.size(6.dp))
                    TextButton(onClick = {
                        viewer?.stop()
                        onDismiss()
                    }) {
                        Text("Chiudi", color = CscTextMuted)
                    }
                }
            }
        }
    }
}
