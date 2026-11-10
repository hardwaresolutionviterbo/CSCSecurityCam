package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.CscBrandMark
import com.example.ui.theme.CscBackground
import com.example.ui.theme.CscOrange
import com.example.ui.theme.CscPanelBlue
import com.example.ui.theme.CscSteel
import com.example.ui.theme.CscSubtitleGray
import com.example.ui.theme.CscTitleWhite
import com.example.ui.theme.MontserratFontFamily

/**
 * Schermata 1 del riferimento: marchio con alone arancione, wordmark, tagline e i due pulsanti
 * "Avvia" (pieno) e "Configurazione" (solo bordo). Nessun elenco di funzioni: quelle stanno
 * nella dashboard.
 */
@Composable
fun SplashScreen(
    onStart: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "splash_glow")
    val glowScale by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )
    val glowAlpha by transition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CscBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Alone ambientale arancione e linea d'orizzonte, come nel riferimento.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerTop = Offset(size.width * 0.5f, size.height * 0.26f)
            val topRadius = size.width * 0.78f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        CscOrange.copy(alpha = 0.26f * glowAlpha),
                        CscOrange.copy(alpha = 0.08f * glowAlpha),
                        Color.Transparent
                    ),
                    center = centerTop,
                    radius = topRadius
                ),
                radius = topRadius,
                center = centerTop
            )

            val sideCenter = Offset(size.width * 1.05f, size.height * 0.66f)
            val sideRadius = size.width * 0.62f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        CscOrange.copy(alpha = 0.14f * glowAlpha),
                        Color.Transparent
                    ),
                    center = sideCenter,
                    radius = sideRadius
                ),
                radius = sideRadius,
                center = sideCenter
            )

            val horizonY = size.height * 0.88f
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        CscOrange.copy(alpha = 0.65f),
                        CscOrange.copy(alpha = 0.9f),
                        CscOrange.copy(alpha = 0.65f),
                        Color.Transparent
                    )
                ),
                start = Offset(0f, horizonY),
                end = Offset(size.width, horizonY),
                strokeWidth = 2.5f,
                cap = StrokeCap.Round
            )

            // Griglia prospettica appena percettibile sotto l'orizzonte.
            for (i in 1..5) {
                val y = horizonY + (size.height - horizonY) * (i / 6f)
                drawLine(
                    color = CscSteel.copy(alpha = 0.18f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Box(modifier = Modifier.scale(glowScale)) {
                CscBrandMark(size = 136.dp)
            }

            Spacer(modifier = Modifier.height(26.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "CSC",
                    fontSize = 32.sp,
                    fontFamily = MontserratFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = CscTitleWhite,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = "SecurityCam",
                    fontSize = 32.sp,
                    fontFamily = MontserratFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = CscOrange,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "VIDEOSORVEGLIANZA SMART",
                fontSize = 11.sp,
                fontFamily = MontserratFontFamily,
                fontWeight = FontWeight.SemiBold,
                color = CscSubtitleGray,
                letterSpacing = 3.sp,
                textAlign = TextAlign.Center,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(56.dp))

            SplashPrimaryButton(onClick = onStart)
            Spacer(modifier = Modifier.height(14.dp))
            SplashSecondaryButton(onClick = onOpenSettings)

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SplashPrimaryButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(50.dp),
                spotColor = CscOrange,
                ambientColor = CscOrange.copy(alpha = 0.5f)
            )
            .clip(RoundedCornerShape(50.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(Color(0xFFFF8A1F), CscOrange, Color(0xFFE65700))
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = Color.White.copy(alpha = 0.25f))
            ) { onClick() }
            .testTag("btn_start_app"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Avvia",
                fontSize = 17.sp,
                fontFamily = MontserratFontFamily,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SplashSecondaryButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(50.dp))
            .background(CscPanelBlue.copy(alpha = 0.55f))
            .border(1.5.dp, CscOrange, RoundedCornerShape(50.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = CscOrange.copy(alpha = 0.25f))
            ) { onClick() }
            .testTag("btn_open_settings"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = CscOrange,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Configurazione",
                fontSize = 16.sp,
                fontFamily = MontserratFontFamily,
                fontWeight = FontWeight.SemiBold,
                color = CscTitleWhite,
                maxLines = 1
            )
        }
    }
}
