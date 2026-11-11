package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.example.model.Screen
import com.example.ui.components.CscBottomNav
import com.example.ui.theme.CscBackground
import com.example.ui.theme.CscGlassBorder
import com.example.ui.theme.CscOnlineGreen
import com.example.ui.theme.CscOrange
import com.example.ui.theme.CscPanelBlue
import com.example.ui.theme.CscPanelBlueVariant
import com.example.ui.theme.CscSubtitleGray
import com.example.ui.theme.CscTitleWhite
import com.example.viewmodel.MainUiState

@Composable
fun HomeScreen(
    state: MainUiState,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val onlineCount = state.devices.count { it.isOnline }
    val displayName = state.userProfile.fullName.trim().ifBlank { "Operatore" }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CscBackground,
        bottomBar = {
            CscBottomNav(currentScreen = Screen.HOME, onNavigate = onNavigate)
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFF334A68), Color(0xFF101B2E))))
                        .border(1.5.dp, CscOrange, CircleShape)
                        .clickable { onNavigate(Screen.PROFILE) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, contentDescription = "Profilo", tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Ciao, $displayName", color = CscTitleWhite, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(CscOnlineGreen))
                        Spacer(Modifier.width(6.dp))
                        Text("Sistema pronto", color = CscSubtitleGray, fontSize = 11.sp)
                    }
                }
                IconButton(
                    onClick = { onNavigate(Screen.SETTINGS) },
                    modifier = Modifier.size(42.dp).clip(CircleShape)
                        .background(CscPanelBlue).border(1.dp, CscGlassBorder, CircleShape)
                ) {
                    Icon(Icons.Default.NotificationsNone, contentDescription = "Notifiche e impostazioni", tint = CscTitleWhite)
                }
            }

            HomeStatusCard(
                onlineCount = onlineCount,
                totalCount = state.devices.size,
                onClick = { onNavigate(Screen.MONITOR) }
            )

            Text(
                "CENTRO OPERATIVO",
                color = CscSubtitleGray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(start = 3.dp, top = 1.dp)
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DashboardCard(
                    title = "Videocamera",
                    subtitle = "Usa questo telefono come telecamera",
                    icon = Icons.Default.Videocam,
                    gradient = listOf(Color(0xFFFF8A1F), Color(0xFFB94700), Color(0xFF321A17)),
                    testTag = "card_camera",
                    onClick = { onNavigate(Screen.CAMERA) },
                    modifier = Modifier.weight(1f)
                )
                DashboardCard(
                    title = "Centro di controllo",
                    subtitle = "Guarda il live delle telecamere",
                    icon = Icons.Default.Tv,
                    gradient = listOf(Color(0xFF22B8FF), Color(0xFF075B9D), Color(0xFF10233F)),
                    testTag = "card_monitor",
                    onClick = { onNavigate(Screen.MONITOR) },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DashboardCard(
                    title = "Dispositivi",
                    subtitle = "Gestisci le tue telecamere",
                    icon = Icons.Default.GridView,
                    gradient = listOf(Color(0xFFB45BFF), Color(0xFF54239A), Color(0xFF211B42)),
                    testTag = "card_devices",
                    onClick = { onNavigate(Screen.DEVICES) },
                    modifier = Modifier.weight(1f)
                )
                DashboardCard(
                    title = "Registrazioni",
                    subtitle = "Video salvati e clip eventi",
                    icon = Icons.Default.PlayCircle,
                    gradient = listOf(Color(0xFFFF5278), Color(0xFF9B2148), Color(0xFF35182F)),
                    testTag = "card_playback",
                    onClick = { onNavigate(Screen.PLAYBACK) },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HomeStatusCard(
    onlineCount: Int,
    totalCount: Int,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth().height(126.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF10213A))
            .border(1.dp, CscGlassBorder, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = "https://images.unsplash.com/photo-1600607687939-ce8a6c25118c?w=1200&auto=format&fit=crop&q=85",
            contentDescription = "Abitazione moderna protetta",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Color(0xF0081426),
                    0.62f to Color(0xC5101D31),
                    1f to Color(0x3310182A)
                )
            )
        )
        Column(
            Modifier.align(Alignment.CenterStart).fillMaxWidth(0.78f).padding(start = 18.dp, end = 8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = CscOnlineGreen, modifier = Modifier.size(27.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (onlineCount > 0) "Sistema attivo" else "Sistema pronto",
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                when {
                    totalCount == 0 -> "Aggiungi la tua prima telecamera per iniziare"
                    onlineCount == totalCount -> "Tutte le $totalCount telecamere sono online"
                    else -> "$onlineCount telecamere online su $totalCount"
                },
                color = Color(0xFFE1E8F2), fontSize = 11.sp, maxLines = 2
            )
        }
        Box(
            Modifier.align(Alignment.CenterEnd).padding(end = 16.dp).size(38.dp)
                .clip(CircleShape).background(Color(0x5523C978)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.size(23.dp))
        }
    }
}

@Composable
fun DashboardCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    gradient: List<Color>,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        if (pressed) 0.965f else 1f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = 520f),
        label = "dashboardCardPress"
    )
    Box(
        modifier = modifier.graphicsLayer {
            scaleX = pressScale
            scaleY = pressScale
            translationY = if (pressed) 3.dp.toPx() else 0f
        }
            .aspectRatio(0.90f)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(gradient))
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(20.dp))
            .clickable(interactionSource = interaction, indication = ripple(color = Color.White.copy(alpha = 0.2f))) { onClick() }
            .padding(13.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(15.dp))
                    .background(Color.White.copy(alpha = 0.16f))
                    .border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = Color.White, modifier = Modifier.size(27.dp))
            }
            Spacer(Modifier.height(11.dp))
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
            Spacer(Modifier.height(5.dp))
            Text(subtitle, color = Color.White.copy(alpha = 0.86f), fontSize = 10.sp, lineHeight = 13.sp, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}
