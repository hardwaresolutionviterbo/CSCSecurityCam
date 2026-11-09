package it.cscsecuritycam

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.integration.android.IntentIntegrator
import it.cscsecuritycam.camera.YuvFrameConverter
import it.cscsecuritycam.network.MjpegStreamServer
import it.cscsecuritycam.network.NetworkUtils
import it.cscsecuritycam.surveillance.MotionDetector
import it.cscsecuritycam.surveillance.MotionNotifier
import java.io.IOException
import java.util.concurrent.Executors
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import android.webkit.WebView
import android.webkit.WebViewClient

private val Navy = Color(0xFF101722)
private val Panel = Color(0xFF1A2635)
private val Orange = Color(0xFFFF8A32)
private val Muted = Color(0xFF9BA9B9)

class MainActivity : ComponentActivity() {
    private val streamServer by lazy { MjpegStreamServer() }
    private val scanResult = mutableStateOf<String?>(null)
    private val hasCameraPermission = mutableStateOf(false)
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> hasCameraPermission.value = granted }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val qrScanner = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            scanResult.value = result.data?.getStringExtra("SCAN_RESULT")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasCameraPermission.value = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (!hasCameraPermission.value) cameraPermission.launch(Manifest.permission.CAMERA)
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            CSCApp(
                server = streamServer,
                cameraGranted = hasCameraPermission.value,
                scannedPayload = scanResult.value,
                onConsumeScan = { scanResult.value = null },
                onLaunchScanner = {
                    val intent = IntentIntegrator(this)
                        .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                        .setPrompt("Inquadra il QR code di CSCSecurityCam")
                        .setBeepEnabled(false)
                        .setOrientationLocked(false)
                        .createScanIntent()
                    qrScanner.launch(intent)
                },
                onRequestCamera = { cameraPermission.launch(Manifest.permission.CAMERA) }
            )
        }
    }

    override fun onDestroy() {
        if (streamServer.isAlive) streamServer.stop()
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }
}

private data class StreamConnection(val host: String, val port: Int, val token: String) {
    val url: String get() = "http://$host:$port/stream.mjpg?token=$token"

    companion object {
        fun parse(payload: String): StreamConnection? {
            return try {
                val uri = Uri.parse(payload.trim())
                val host = uri.getQueryParameter("host") ?: uri.host ?: return null
                val port = uri.getQueryParameter("port")?.toIntOrNull() ?: uri.port.takeIf { it > 0 } ?: MjpegStreamServer.PORT
                val token = uri.getQueryParameter("token") ?: return null
                if (host.isBlank() || port !in 1..65535 || token.length < 12) null
                else StreamConnection(host, port, token)
            } catch (_: Exception) {
                null
            }
        }
    }
}

@Composable
private fun CSCApp(
    server: MjpegStreamServer,
    cameraGranted: Boolean,
    scannedPayload: String?,
    onConsumeScan: () -> Unit,
    onLaunchScanner: () -> Unit,
    onRequestCamera: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Orange, background = Navy, surface = Panel,
        onPrimary = Navy, onBackground = Color.White, onSurface = Color.White
    )) {
        Scaffold(
            containerColor = Navy,
            bottomBar = {
                NavigationBar(containerColor = Panel, contentColor = Color.White) {
                    NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Shield, contentDescription = "Home") }, label = { androidx.compose.material3.Text("Home") })
                    NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.CameraAlt, contentDescription = "Camera") }, label = { androidx.compose.material3.Text("Camera") })
                    NavigationBarItem(selected = selectedTab == 2, onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.Devices, contentDescription = "Monitor") }, label = { androidx.compose.material3.Text("Monitor") })
                    NavigationBarItem(selected = selectedTab == 3, onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") }, label = { androidx.compose.material3.Text("Setup") })
                }
            }
        ) { padding ->
            when (selectedTab) {
                0 -> Dashboard(Modifier.padding(padding), onCamera = { selectedTab = 1 }, onMonitor = { selectedTab = 2 })
                1 -> CameraScreen(server, cameraGranted, Modifier.padding(padding), onRequestCamera)
                2 -> MonitorScreen(scannedPayload, onConsumeScan, onLaunchScanner, Modifier.padding(padding))
                else -> SettingsScreen(Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun Dashboard(modifier: Modifier = Modifier, onCamera: () -> Unit, onMonitor: () -> Unit) {
    Column(modifier.fillMaxSize().background(Navy).padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).background(Orange, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = Navy, modifier = Modifier.size(27.dp))
            }
            Column(Modifier.padding(start = 12.dp)) {
                androidx.compose.material3.Text("CSC SECURITYCAM", fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                androidx.compose.material3.Text("SMART HOME PROTECTION", color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        androidx.compose.material3.Text("La tua sicurezza,\nsempre sotto controllo.", fontSize = 29.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
        androidx.compose.material3.Text("Scegli il ruolo di questo smartphone.", color = Muted)
        ActionCard("01", "Usa come videocamera", "Trasmetti il video a un secondo telefono sulla stessa rete Wi-Fi.", Icons.Default.CameraAlt, onCamera)
        ActionCard("02", "Centro di controllo", "Scansiona il QR code della videocamera e guarda il flusso live.", Icons.Default.Devices, onMonitor)
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Wifi, tint = Orange, contentDescription = null)
                Column(Modifier.padding(start = 12.dp)) {
                    androidx.compose.material3.Text("Connessione locale", fontWeight = FontWeight.SemiBold)
                    androidx.compose.material3.Text("Prima versione: streaming video su Wi-Fi condiviso.", color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ActionCard(number: String, title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(Color(0xFF29394B), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Orange, modifier = Modifier.size(25.dp))
            }
            Column(Modifier.weight(1f).padding(start = 13.dp)) {
                androidx.compose.material3.Text(title, fontWeight = FontWeight.Bold)
                androidx.compose.material3.Text(subtitle, color = Muted, fontSize = 12.sp)
            }
            androidx.compose.material3.Text(number, color = Orange, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun CameraScreen(server: MjpegStreamServer, granted: Boolean, modifier: Modifier = Modifier, onRequestCamera: () -> Unit) {
    val context = LocalContext.current
    var running by remember { mutableStateOf(false) }
    var connectionPayload by remember { mutableStateOf<String?>(null) }
    var monitorConnected by remember { mutableStateOf(false) }
    var motionEnabled by remember { mutableStateOf(true) }
    var motionDetected by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(running) {
        while (running) {
            monitorConnected = server.hasConnectedMonitor()
            delay(700)
        }
        monitorConnected = false
    }

    Column(modifier.fillMaxSize().background(Navy).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        androidx.compose.material3.Text("Modalità videocamera", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        androidx.compose.material3.Text("Questo telefono acquisisce il video e lo condivide sulla rete Wi-Fi locale.", color = Muted, fontSize = 13.sp)
        if (running && granted) {
            CameraPreview(server, motionEnabled, onMotion = { motionDetected = true; MotionNotifier.show(context) }, Modifier.fillMaxWidth().weight(1f))
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (monitorConnected) {
                        androidx.compose.material3.Text("MONITOR COLLEGATO", color = Color(0xFF62D69A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        androidx.compose.material3.Text("Il secondo telefono sta ricevendo il video in diretta.", color = Muted, fontSize = 12.sp)
                    } else {
                        androidx.compose.material3.Text("SCANSIONA CON IL SECONDO TELEFONO", color = Orange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        val qr = remember(connectionPayload) { connectionPayload?.let { makeQrBitmap(it) } }
                        if (qr != null) Image(qr.asImageBitmap(), contentDescription = "QR di collegamento", modifier = Modifier.size(170.dp).background(Color.White, RoundedCornerShape(8.dp)).padding(8.dp))
                        androidx.compose.material3.Text("Inquadra il QR con il monitor. Il codice scomparirà al collegamento.", color = Muted, fontSize = 11.sp)
                    }
                }
            }
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Orange, modifier = Modifier.size(32.dp))
                    androidx.compose.material3.Text(if (granted) "Videocamera pronta" else "Permesso videocamera necessario", fontWeight = FontWeight.Bold)
                    androidx.compose.material3.Text("Avvia la trasmissione per creare un QR code temporaneo e protetto.", color = Muted, fontSize = 13.sp)
                    if (error != null) androidx.compose.material3.Text(error!!, color = Color(0xFFFF7777), fontSize = 12.sp)
                }
            }
        }
        Button(
            onClick = {
                if (!granted) {
                    onRequestCamera()
                } else if (running) {
                    running = false
                    connectionPayload = null
                    server.stop()
                } else {
                    val ip = NetworkUtils.localIpv4Address()
                    if (ip == null) {
                        error = "Nessuna rete Wi-Fi disponibile. Collega il telefono alla stessa rete del monitor."
                    } else {
                        try {
                            server.rotateToken()
                            if (!server.isAlive) server.start()
                            connectionPayload = "cscam://connect?host=$ip&port=${MjpegStreamServer.PORT}&token=${server.token()}"
                            running = true
                            error = null
                        } catch (e: IOException) {
                            error = "Impossibile avviare il server locale: ${e.localizedMessage ?: "errore"}"
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = if (running) Color(0xFFB84343) else Orange, contentColor = Color.White)
        ) {
            androidx.compose.material3.Text(if (running) "Arresta videocamera" else "Avvia videocamera", fontWeight = FontWeight.Bold)
        }
        if (running) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    androidx.compose.material3.Text("Rilevamento movimento", color = Color.White, fontWeight = FontWeight.SemiBold)
                    androidx.compose.material3.Text(if (motionDetected) "Movimento rilevato" else "Analisi della scena attiva", color = if (motionDetected) Orange else Muted, fontSize = 11.sp)
                }
                androidx.compose.material3.Switch(checked = motionEnabled, onCheckedChange = { motionEnabled = it })
            }
            androidx.compose.material3.Text("Streaming LAN attivo • porta " + MjpegStreamServer.PORT, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun CameraPreview(server: MjpegStreamServer, motionEnabled: Boolean, onMotion: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    AndroidView(factory = { previewView }, modifier = modifier.background(Color.Black, RoundedCornerShape(18.dp)))

    DisposableEffect(server, lifecycleOwner, previewView, motionEnabled, onMotion) {
        val motionDetector = MotionDetector()
        val executor = Executors.newSingleThreadExecutor()
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(android.util.Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { image ->
                    try {
                        YuvFrameConverter.toJpeg(image)?.let(server::publishFrame)
                        if (motionEnabled && motionDetector.detect(image)) onMotion()
                    } catch (_: Exception) {
                    } finally {
                        image.close()
                    }
                }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (_: Exception) {
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            executor.shutdown()
            try {
                if (providerFuture.isDone) providerFuture.get().unbindAll()
            } catch (_: Exception) {
            }
        }
    }
}

@Composable
private fun MonitorScreen(
    scannedPayload: String?,
    onConsumeScan: () -> Unit,
    onLaunchScanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    var input by remember { mutableStateOf("") }
    var connection by remember { mutableStateOf<StreamConnection?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(scannedPayload) {
        if (!scannedPayload.isNullOrBlank()) {
            val parsed = StreamConnection.parse(scannedPayload)
            if (parsed != null) {
                connection = parsed
                error = null
            } else {
                error = "Il QR code non contiene un collegamento CSCSecurityCam valido."
            }
            onConsumeScan()
        }
    }

    Column(modifier.fillMaxSize().background(Navy).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        androidx.compose.material3.Text("Centro di controllo", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        androidx.compose.material3.Text("Collega il telefono videocamera e guarda il flusso live.", color = Muted, fontSize = 13.sp)
        if (connection == null) {
            Button(onClick = onLaunchScanner, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Orange)) {
                androidx.compose.material3.Text("Scansiona QR code")
            }
            androidx.compose.material3.Text("Oppure inserisci il codice di connessione:", color = Muted, fontSize = 12.sp)
            OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.fillMaxWidth(), label = { androidx.compose.material3.Text("Codice cscam://...") }, singleLine = true)
            Button(onClick = {
                val parsed = StreamConnection.parse(input)
                if (parsed != null) {
                    connection = parsed
                    error = null
                } else error = "Codice non valido. Scansiona il QR mostrato dalla videocamera."
            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Panel)) {
                androidx.compose.material3.Text("Collega")
            }
            if (error != null) androidx.compose.material3.Text(error!!, color = Color(0xFFFF7777), fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            androidx.compose.material3.Text("Nota: questa prima connessione funziona quando entrambi i telefoni sono sulla stessa rete Wi-Fi.", color = Muted, fontSize = 11.sp)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Text("Videocamera: ${connection!!.host}", color = Orange, fontWeight = FontWeight.SemiBold)
                Button(onClick = { connection = null }, colors = ButtonDefaults.buttonColors(containerColor = Panel)) {
                    androidx.compose.material3.Text("Disconnetti")
                }
            }
            MjpegViewer(connection!!.url, Modifier.fillMaxWidth().weight(1f))
            androidx.compose.material3.Text("Video in diretta sulla rete locale • MJPEG", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun MjpegViewer(url: String, modifier: Modifier = Modifier) {
    var frame by remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var streamError by remember(url) { mutableStateOf<String?>(null) }

    LaunchedEffect(url) {
        withContext(Dispatchers.IO) {
            var connection: java.net.HttpURLConnection? = null
            try {
                connection = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 20000
                    useCaches = false
                    setRequestProperty("Cache-Control", "no-cache")
                    setRequestProperty("Accept", "multipart/x-mixed-replace")
                }
                connection.connect()
                val code = connection.responseCode
                if (code != 200) throw IOException("Server video: HTTP $code")
                val input = java.io.DataInputStream(java.io.BufferedInputStream(connection.inputStream))
                while (isActive) {
                    var line = readMjpegLine(input)
                    while (line.isEmpty() && isActive) line = readMjpegLine(input)
                    if (!line.startsWith("--")) continue

                    var contentLength = -1
                    while (true) {
                        val header = readMjpegLine(input)
                        if (header.isEmpty()) break
                        if (header.startsWith("Content-Length:", ignoreCase = true)) {
                            contentLength = header.substringAfter(":").trim().toIntOrNull() ?: -1
                        }
                    }
                    if (contentLength <= 0 || contentLength > 8_000_000) continue

                    val jpeg = ByteArray(contentLength)
                    input.readFully(jpeg)
                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
                    if (bitmap != null) {
                        withContext(Dispatchers.Main) {
                            frame = bitmap
                            streamError = null
                        }
                    }
                }
            } catch (e: Exception) {
                if (isActive) withContext(Dispatchers.Main) {
                    streamError = e.localizedMessage ?: "Impossibile ricevere il video"
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    Box(modifier.background(Color.Black, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
        val currentFrame = frame
        if (currentFrame != null) {
            Image(
                bitmap = currentFrame.asImageBitmap(),
                contentDescription = "Video live della videocamera",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            androidx.compose.material3.Text(
                streamError ?: "Connessione video in corso…",
                color = Color.White,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

private fun readMjpegLine(input: java.io.DataInputStream): String {
    val bytes = java.io.ByteArrayOutputStream()
    while (true) {
        val value = input.read()
        if (value == -1) throw IOException("Connessione video interrotta")
        if (value == 10) break
        if (value != 13) bytes.write(value)
        if (bytes.size() > 4096) throw IOException("Intestazione MJPEG non valida")
    }
    return bytes.toString(Charsets.US_ASCII.name())
}

@Composable
private fun SettingsScreen(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(Navy).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        androidx.compose.material3.Text("Impostazioni", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Text("Connessione attuale", fontWeight = FontWeight.Bold)
                androidx.compose.material3.Text("La versione 0.2 supporta lo streaming video locale su Wi-Fi. Il collegamento remoto e l'audio bidirezionale sono in sviluppo.", color = Muted, fontSize = 13.sp)
            }
        }
    }
}

private fun makeQrBitmap(value: String): android.graphics.Bitmap? {
    return try {
        val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 420, 420)
        val pixels = IntArray(420 * 420)
        for (y in 0 until 420) for (x in 0 until 420) {
            pixels[y * 420 + x] = if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
        android.graphics.Bitmap.createBitmap(pixels, 420, 420, android.graphics.Bitmap.Config.RGB_565)
    } catch (_: Exception) {
        null
    }
}
