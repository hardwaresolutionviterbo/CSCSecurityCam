package it.cscsecuritycam

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private val Navy = Color(0xFF101722)
private val Panel = Color(0xFF1A2635)
private val Orange = Color(0xFFFF8A32)
private val Muted = Color(0xFF9BA9B9)

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val permissions = buildList {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (permissions.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
        setContent { CSCApp() }
    }
}

@Composable
private fun CSCApp() {
    var selectedTab by remember { mutableIntStateOf(0) }
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Orange,
        background = Navy,
        surface = Panel,
        onPrimary = Navy,
        onBackground = Color.White,
        onSurface = Color.White
    )) {
        Scaffold(
            containerColor = Navy,
            bottomBar = {
                NavigationBar(containerColor = Panel, contentColor = Color.White) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Shield, contentDescription = "Dashboard") },
                        label = { Text("Home") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.CameraAlt, contentDescription = "Camera") },
                        label = { Text("Camera") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.Devices, contentDescription = "Monitor") },
                        label = { Text("Monitor") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Impostazioni") },
                        label = { Text("Setup") }
                    )
                }
            }
        ) { padding ->
            when (selectedTab) {
                0 -> Dashboard(Modifier.padding(padding))
                1 -> FeaturePage("Modalità Camera", "Questo dispositivo diventerà una videocamera.", "Anteprima CameraX in arrivo", Modifier.padding(padding))
                2 -> FeaturePage("Centro di controllo", "Collega e gestisci i tuoi dispositivi.", "Nessuna videocamera associata", Modifier.padding(padding))
                else -> FeaturePage("Impostazioni", "Privacy, rete e qualità video.", "Configurazione iniziale", Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun Dashboard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().background(Navy).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(46.dp).background(Orange, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Shield, contentDescription = null, tint = Navy, modifier = Modifier.size(27.dp)) }
            Column(Modifier.padding(start = 12.dp)) {
                Text("CSC SECURITYCAM", fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                Text("SMART HOME PROTECTION", color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("La tua sicurezza,\nsempre sotto controllo.", fontSize = 29.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
        Text("Scegli come vuoi utilizzare questo dispositivo.", color = Muted)
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Wifi, contentDescription = null, tint = Orange)
                    Text("  STATO DEL SISTEMA", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Text("Pronto per la configurazione", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text("Le funzioni di collegamento saranno attivate nei prossimi passaggi.", color = Muted, fontSize = 13.sp)
            }
        }
        ModeCard("01", "Usa come videocamera", "Trasforma questo smartphone in una camera.", Icons.Default.CameraAlt)
        ModeCard("02", "Centro di controllo", "Visualizza e gestisci le tue videocamere.", Icons.Default.Devices)
        Spacer(Modifier.weight(1f))
        Text("CSCSecurityCam • Versione 0.1.0", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun ModeCard(number: String, title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(Color(0xFF29394B), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Orange, modifier = Modifier.size(25.dp))
            }
            Column(Modifier.weight(1f).padding(start = 13.dp)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Muted, fontSize = 12.sp)
            }
            Text(number, color = Orange, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun FeaturePage(title: String, description: String, status: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(Navy).padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(title, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text(description, color = Muted)
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = Orange, modifier = Modifier.size(30.dp))
                Text(status, fontWeight = FontWeight.SemiBold)
                Text("Questa sezione verrà completata e verificata con le funzioni reali, senza mostrare controlli finti.", color = Muted, fontSize = 13.sp)
            }
        }
    }
}
