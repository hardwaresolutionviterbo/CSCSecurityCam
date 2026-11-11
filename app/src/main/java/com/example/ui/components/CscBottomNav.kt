package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
import com.example.ui.theme.CscGlassBorder
import com.example.ui.theme.CscNeonGlowSubtle
import com.example.ui.theme.CscOrange
import com.example.ui.theme.CscPanelBlue
import com.example.ui.theme.CscPanelBlueVariant
import com.example.ui.theme.CscSubtitleGray

data class NavItem(
    val screen: Screen,
    val title: String,
    val icon: ImageVector,
    val testTag: String
)

@Composable
fun CscBottomNav(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        NavItem(Screen.HOME, "Home", Icons.Default.Home, "nav_home"),
        NavItem(Screen.CAMERA, "Camera", Icons.Default.Videocam, "nav_camera"),
        NavItem(Screen.MONITOR, "Monitor", Icons.Default.Tv, "nav_monitor"),
        NavItem(Screen.PLAYBACK, "Playback", Icons.Default.PlayCircle, "nav_playback"),
        NavItem(Screen.SETTINGS, "Setup", Icons.Default.Settings, "nav_setup")
    )

    // Floating Glassmorphism Pill Container
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(32.dp),
                    spotColor = Color(0xAA000000),
                    ambientColor = Color(0x66FF6A00)
                )
                .clip(RoundedCornerShape(32.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            CscPanelBlue,
                            CscPanelBlueVariant
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0x33FFFFFF), // 1px light reflection top
                            CscGlassBorder     // 1px rgba(255,255,255,0.1) bottom
                        )
                    ),
                    shape = RoundedCornerShape(32.dp)
                )
                .padding(horizontal = 8.dp, vertical = 8.dp)            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val isSelected = currentScreen == item.screen
                    val itemInteraction = remember(item.screen) { MutableInteractionSource() }
                    val isPressed by itemInteraction.collectIsPressedAsState()
                    val pressScale by animateFloatAsState(
                        targetValue = if (isPressed) 0.88f else 1f,
                        animationSpec = spring(dampingRatio = 0.52f, stiffness = 650f),
                        label = "navPress"
                    )

                    val iconTint by animateColorAsState(
                        targetValue = if (isSelected) CscOrange else CscSubtitleGray,
                        animationSpec = tween(250),
                        label = "iconTint"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .testTag(item.testTag)
                            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(
                                interactionSource = itemInteraction,
                                indication = ripple(bounded = true, color = CscOrange.copy(alpha = 0.25f))
                            ) { onNavigate(item.screen) }
                            .padding(horizontal = 2.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Icon Box with Neon Glow when selected
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .then(
                                    if (isSelected) {
                                        Modifier
                                            .clip(CircleShape)
                                            .background(
                                                Brush.radialGradient(
                                                    colors = listOf(
                                                        CscNeonGlowSubtle,
                                                        Color.Transparent
                                                    )
                                                )
                                            )
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title,
                                tint = iconTint,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Text(
                            text = item.title,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = iconTint,
                            maxLines = 1,
                            softWrap = false,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        // Glowing orange dot beneath active item
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) CscOrange else Color.Transparent)
                        )
                    }
                }
            }
        }
    }
}
