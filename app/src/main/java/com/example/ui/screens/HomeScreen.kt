package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PermPhoneMsg
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoCameraBack
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Screen
import com.example.ui.components.CscBottomNav
import com.example.ui.components.CscBrandMark
import com.example.ui.theme.CscBackground
import com.example.ui.theme.CscGlassBorder
import com.example.ui.theme.CscGlassCard
import com.example.ui.theme.CscNeonGlow
import com.example.ui.theme.CscNeonGlowSubtle
import com.example.ui.theme.CscOrange
import com.example.ui.theme.CscOrangeGlow
import com.example.ui.theme.CscPanelBlue
import com.example.ui.theme.CscPanelBlueVariant
import com.example.ui.theme.CscSubtitleGray
import com.example.ui.theme.CscTitleWhite

@Composable
fun HomeScreen(
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CscBackground,
        bottomBar = {
            CscBottomNav(
                currentScreen = Screen.HOME,
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
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Top App Bar: Brand Logo & Glass Settings Icon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    CscBrandMark(size = 44.dp, showGlow = false)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Wordmark ufficiale: "CSC" bianco, "SecurityCam" arancione.
                            Text(
                                text = "CSC",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = CscTitleWhite,
                                letterSpacing = 0.5.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = "SecurityCam",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = CscOrange,
                                letterSpacing = 0.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        Text(
                            text = "VIDEOSORVEGLIANZA SMART",
                            fontSize = 7.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = CscSubtitleGray,
                            letterSpacing = 1.6.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // Frosted Glass Settings Button
                IconButton(
                    onClick = { onNavigate(Screen.SETTINGS) },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(CscGlassCard)
                        .border(1.dp, CscGlassBorder, CircleShape)
                        .testTag("home_settings_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Impostazioni",
                        tint = CscTitleWhite,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Official Reference Feature Overview Row (from Graphic Board)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(CscPanelBlue)
                    .border(1.dp, CscGlassBorder, RoundedCornerShape(18.dp))
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FeaturePill(icon = Icons.Default.PermPhoneMsg, label = "Camera")
                FeaturePill(icon = Icons.Default.Sensors, label = "Live")
                FeaturePill(icon = Icons.Default.VideoCameraBack, label = "REC")
                FeaturePill(icon = Icons.Default.DirectionsRun, label = "Motion")
                FeaturePill(icon = Icons.Default.Notifications, label = "Avvisi")
                FeaturePill(icon = Icons.Default.Cloud, label = "Remoto")
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Etichetta di sezione con barretta arancione.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .height(2.dp)
                        .background(CscOrange)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "MODALITÀ OPERATIVA",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = CscSubtitleGray,
                    letterSpacing = 2.5.sp,
                    maxLines = 1
                )
            }

            // 2x2 Grid of Mode Cards
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Row 1: Videocamera (Selected / Active Glow) & Centro di Controllo (Glass)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CyberDashboardCard(
                        title = "Videocamera",
                        subtitle = "Usa questo telefono come camera",
                        icon = Icons.Default.Videocam,
                        isSelected = true,
                        testTag = "card_camera",
                        onClick = { onNavigate(Screen.CAMERA) },
                        modifier = Modifier.weight(1f)
                    )

                    CyberDashboardCard(
                        title = "Centro di Controllo",
                        subtitle = "Guarda il live delle videocamere",
                        icon = Icons.Default.Tv,
                        isSelected = false,
                        testTag = "card_monitor",
                        onClick = { onNavigate(Screen.MONITOR) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 2: Dispositivi (Glass) & Registrazioni (Glass)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CyberDashboardCard(
                        title = "Dispositivi",
                        subtitle = "Gestisci le tue camere",
                        icon = Icons.Default.GridView,
                        isSelected = false,
                        testTag = "card_devices",
                        onClick = { onNavigate(Screen.DEVICES) },
                        modifier = Modifier.weight(1f)
                    )

                    CyberDashboardCard(
                        title = "Registrazioni",
                        subtitle = "Video salvati e clip eventi",
                        icon = Icons.Default.PlayCircle,
                        isSelected = false,
                        testTag = "card_playback",
                        onClick = { onNavigate(Screen.PLAYBACK) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FeaturePill(
    icon: ImageVector,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CscPanelBlueVariant)
                .border(1.dp, CscGlassBorder, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = CscOrange,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            color = CscSubtitleGray,
            maxLines = 1
        )
    }
}

@Composable
fun CyberDashboardCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(0.92f)
            .shadow(
                elevation = if (isSelected) 18.dp else 6.dp,
                shape = RoundedCornerShape(22.dp),
                spotColor = if (isSelected) CscOrange else Color.Black,
                ambientColor = if (isSelected) CscNeonGlow else Color.Transparent
            )
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    colors = if (isSelected) {
                        listOf(
                            Color(0xFF2C2219), // Alone caldo sulla sola scheda selezionata
                            CscPanelBlue
                        )
                    } else {
                        listOf(
                            CscPanelBlue,
                            CscPanelBlueVariant
                        )
                    }
                )
            )
            .border(
                width = if (isSelected) 1.8.dp else 1.dp,
                brush = if (isSelected) {
                    Brush.verticalGradient(
                        listOf(
                            CscOrange,
                            CscOrangeGlow.copy(alpha = 0.8f)
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(
                            Color(0x33FFFFFF), // 1px light reflection top
                            CscGlassBorder     // 1px rgba(255,255,255,0.1) bottom
                        )
                    )
                },
                shape = RoundedCornerShape(22.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = CscOrange.copy(alpha = 0.2f)),
                onClick = onClick
            )
            .padding(16.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon in box with Neon Glow when selected
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (isSelected) {
                            Color(0x33FF6A00) // Neon orange glow background
                        } else {
                            CscPanelBlueVariant // Contenitore icona
                        }
                    )
                    .then(
                        if (isSelected) {
                            Modifier.border(
                                width = 1.dp,
                                color = CscOrange.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(18.dp)
                            )
                        } else {
                            Modifier.border(
                                width = 1.dp,
                                color = CscGlassBorder,
                                shape = RoundedCornerShape(18.dp)
                            )
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Radial glow behind icon if selected
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        CscOrange.copy(alpha = 0.5f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }

                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (isSelected) CscOrange else CscTitleWhite,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Card Title (Pure White, Bold Montserrat)
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = CscTitleWhite,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Card Subtitle (Light Gray #A0AABF)
            Text(
                text = subtitle,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                color = CscSubtitleGray,
                textAlign = TextAlign.Center,
                lineHeight = 15.sp
            )
        }
    }
}
