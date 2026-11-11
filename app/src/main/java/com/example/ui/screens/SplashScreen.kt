package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.graphics.graphicsLayer
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
        initialValue = 0.97f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )
    val glowAlpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = modifier.fillMaxSize().background(CscBackground)
    ) {
        coil.compose.AsyncImage(
            model = "https://images.unsplash.com/photo-1600607687939-ce8a6c25118c?w=1600&auto=format&fit=crop&q=90",
            contentDescription = "Casa moderna protetta al tramonto",
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color(0xB8081428),
                    0.34f to Color(0x77101B30),
                    0.66f to Color(0x9910182A),
                    1f to Color(0xFF0B1425)
                )
            )
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val glowCenter = Offset(size.width * 0.88f, size.height * 0.16f)
            val radius = size.width * 0.72f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(CscOrange.copy(alpha = 0.25f * glowAlpha), Color.Transparent),
                    center = glowCenter,
                    radius = radius
                ),
                center = glowCenter,
                radius = radius
            )
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, CscOrange.copy(alpha = 0.72f * glowAlpha), Color.Transparent)
                ),
                start = Offset(0f, size.height * 0.83f),
                end = Offset(size.width, size.height * 0.83f),
                strokeWidth = 1.5f
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 26.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier.scale(glowScale)
                    .shadow(24.dp, RoundedCornerShape(26.dp), spotColor = CscOrange.copy(alpha = glowAlpha))
            ) {
                CscBrandMark(size = 76.dp)
            }
            Spacer(Modifier.height(13.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("CSC", fontSize = 27.sp, fontFamily = MontserratFontFamily, fontWeight = FontWeight.Bold, color = CscTitleWhite, maxLines = 1)
                Text("SecurityCam", fontSize = 29.sp, fontFamily = MontserratFontFamily, fontWeight = FontWeight.Bold, color = CscOrange, maxLines = 1)
            }
            Spacer(Modifier.height(7.dp))
            Text(
                "VIDEOSORVEGLIANZA SMART",
                fontSize = 10.sp,
                fontFamily = MontserratFontFamily,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFD4DDEA),
                letterSpacing = 2.3.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 22.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(8.dp).clip(CircleShape).background(CscOrange)
                        .shadow(8.dp, CircleShape, spotColor = CscOrange)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "PROTEZIONE INTELLIGENTE, OVUNQUE",
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    textAlign = TextAlign.Center
                )
            }
            SplashPrimaryButton(onClick = onStart)
            Spacer(Modifier.height(12.dp))
            SplashSecondaryButton(onClick = onOpenSettings)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SplashPrimaryButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.965f else 1f, label = "primaryPress")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(50.dp),
                spotColor = CscOrange,
                ambientColor = CscOrange.copy(alpha = 0.5f)
            )
            .clip(RoundedCornerShape(50.dp))
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale; translationY = if (pressed) 2.dp.toPx() else 0f }
            .background(
                Brush.horizontalGradient(
                    colors = listOf(Color(0xFFFF8A1F), CscOrange, Color(0xFFE65700))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(50.dp))
            .clickable(
                interactionSource = interaction,
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
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.975f else 1f, label = "secondaryPress")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(50.dp),
                spotColor = CscOrange.copy(alpha = 0.55f),
                ambientColor = CscOrange.copy(alpha = 0.22f)
            )
            .clip(RoundedCornerShape(50.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF24334B), CscPanelBlue, Color(0xFF111C30))
                )
            )
            .border(
                width = 1.5.dp,
                brush = Brush.verticalGradient(listOf(Color(0xFFFFA34D), CscOrange, Color(0xFFB94B00))),
                shape = RoundedCornerShape(50.dp)
            )
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .clickable(
                interactionSource = interaction,
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
