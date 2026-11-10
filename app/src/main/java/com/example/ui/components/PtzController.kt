package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CscBackground
import com.example.ui.theme.CscGlassBorder
import com.example.ui.theme.CscGlassCard
import com.example.ui.theme.CscOrange
import com.example.ui.theme.CscOrangeGlow
import com.example.ui.theme.CscTitleWhite

@Composable
fun PtzController(
    onPanTilt: (dx: Float, dy: Float) -> Unit,
    onHome: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // PTZ Circular Cyber-Security Controller Disc
        Box(
            modifier = Modifier
                .size(190.dp)
                .shadow(16.dp, CircleShape, spotColor = Color(0x99FF6A00))
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF24324B), // Cyber core
                            CscGlassCard,
                            CscBackground
                        )
                    )
                )
                .border(1.5.dp, CscGlassBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Up Arrow
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .size(46.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = CscOrangeGlow)
                    ) { onPanTilt(0f, -1f) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Pan Up",
                    tint = CscOrange,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Down Arrow
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .size(46.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = CscOrangeGlow)
                    ) { onPanTilt(0f, 1f) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Pan Down",
                    tint = CscOrange,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Left Arrow
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 10.dp)
                    .size(46.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = CscOrangeGlow)
                    ) { onPanTilt(-1f, 0f) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowLeft,
                    contentDescription = "Pan Left",
                    tint = CscOrange,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Right Arrow
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp)
                    .size(46.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = CscOrangeGlow)
                    ) { onPanTilt(1f, 0f) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = "Pan Right",
                    tint = CscOrange,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Center Home Button with Neon Orange Glow
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .shadow(8.dp, CircleShape, spotColor = CscOrange)
                    .clip(CircleShape)
                    .background(Color(0xFF0F172A))
                    .border(1.5.dp, CscOrange.copy(alpha = 0.8f), CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = CscOrange)
                    ) { onHome() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "PTZ Home Preset",
                    tint = CscTitleWhite,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(20.dp))

        // Side Zoom Column (+ and -) with Glassmorphism
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(26.dp))
                .background(CscGlassCard)
                .border(1.dp, CscGlassBorder, RoundedCornerShape(26.dp))
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(
                onClick = onZoomIn,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Zoom In",
                    tint = CscTitleWhite,
                    modifier = Modifier.size(26.dp)
                )
            }

            Box(
                modifier = Modifier
                    .width(28.dp)
                    .height(1.dp)
                    .background(CscGlassBorder)
            )

            IconButton(
                onClick = onZoomOut,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Zoom Out",
                    tint = CscTitleWhite,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}
