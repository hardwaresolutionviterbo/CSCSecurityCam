package com.example.ui.screens

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
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
import com.example.ui.theme.CscOrange
import com.example.ui.theme.CscPanelBlue
import com.example.ui.theme.CscPanelBlueVariant
import com.example.ui.theme.CscSteel
import com.example.ui.theme.CscSubtitleGray
import com.example.ui.theme.CscTitleWhite
import com.example.ui.theme.MontserratFontFamily

/**
 * Schermata 2 del riferimento: intestazione con marchio e ingranaggio, griglia 2x2 delle funzioni.
 */
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
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
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
                    CscBrandMark(size = 42.dp, showGlow = false)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "CSC",
                                fontSize = 19.sp,
                                fontFamily = MontserratFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = CscTitleWhite,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = "SecurityCam",
                                fontSize = 19.sp,
                                fontFamily = MontserratFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = CscOrange,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        Text(
                            text = "VIDEOSORVEGLIANZA SMART",
                            fontSize = 7.sp,
                            fontFamily = MontserratFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = CscSubtitleGray,
                            letterSpacing = 1.6.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                IconButton(
                    onClick = { onNavigate(Screen.SETTINGS) },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(CscPanelBlue)
                        .border(1.dp, CscSteel, CircleShape)
                        .testTag("home_settings_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Impostazioni",
                        tint = CscTitleWhite,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            com.example.ui.components.SecurityHeroCard(
                title = "Protezione intelligente",
                subtitle = "Tutti i tuoi ambienti, in un unico centro di controllo",
                modifier = Modifier.fillMaxWidth(),
                compact = true
            )

            Spacer(modifier = Modifier.height(22.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 2.dp, bottom = 12.dp)
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
                    fontFamily = MontserratFontFamily,
                    color = CscSubtitleGray,
                    letterSpacing = 2.5.sp,
                    maxLines = 1
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                DashboardCard(
                    title = "Videocamera",
                    subtitle = "Usa questo telefono come camera",
                    icon = Icons.Default.Videocam,
                    isSelected = true,
                    testTag = "card_camera",
                    onClick = { onNavigate(Screen.CAMERA) },
                    modifier = Modifier.weight(1f)
                )
                DashboardCard(
                    title = "Centro di Controllo",
                    subtitle = "Guarda il live delle videocamere",
                    icon = Icons.Default.Tv,
                    isSelected = false,
                    testTag = "card_monitor",
                    onClick = { onNavigate(Screen.MONITOR) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                DashboardCard(
                    title = "Dispositivi",
                    subtitle = "Gestisci le tue camere",
                    icon = Icons.Default.GridView,
                    isSelected = false,
                    testTag = "card_devices",
                    onClick = { onNavigate(Screen.DEVICES) },
                    modifier = Modifier.weight(1f)
                )
                DashboardCard(
                    title = "Registrazioni",
                    subtitle = "Video salvati e clip eventi",
                    icon = Icons.Default.PlayCircle,
                    isSelected = false,
                    testTag = "card_playback",
                    onClick = { onNavigate(Screen.PLAYBACK) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun DashboardCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.965f else 1f, animationSpec = spring(dampingRatio = 0.58f, stiffness = 520f), label = "dashboardCardPress")
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale; translationY = if (pressed) 3.dp.toPx() else 0f }
            .aspectRatio(0.88f)
            .shadow(
                elevation = if (isSelected) 15.dp else 0.dp,
                shape = RoundedCornerShape(18.dp),
                spotColor = CscOrange,
                ambientColor = CscOrange.copy(alpha = 0.45f)
            )
            .clip(RoundedCornerShape(18.dp))
            .background(if (isSelected) Color(0xFF241A12) else CscPanelBlue)
            .border(
                width = if (isSelected) 1.6.dp else 1.dp,
                color = if (isSelected) CscOrange else CscSteel,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = CscOrange.copy(alpha = 0.22f))
            ) { onClick() }
            .padding(14.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isSelected) {
                            Brush.verticalGradient(listOf(Color(0xFFFF8A1F), CscOrange))
                        } else {
                            Brush.verticalGradient(listOf(CscPanelBlueVariant, Color(0xFF131D30)))
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) Color.Transparent else CscSteel,
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (isSelected) Color(0xFF0F172A) else CscOrange,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = title,
                fontSize = 15.sp,
                fontFamily = MontserratFontFamily,
                fontWeight = FontWeight.Bold,
                color = CscTitleWhite,
                textAlign = TextAlign.Center,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(5.dp))

            Text(
                text = subtitle,
                fontSize = 11.sp,
                fontFamily = MontserratFontFamily,
                fontWeight = FontWeight.Normal,
                color = CscSubtitleGray,
                textAlign = TextAlign.Center,
                lineHeight = 15.sp,
                maxLines = 3
            )
        }
    }
}
