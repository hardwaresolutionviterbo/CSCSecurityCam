package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.CscOrange
import com.example.ui.theme.CscSteel
import com.example.ui.theme.CscTitleWhite
import com.example.ui.theme.MontserratFontFamily

/** Promotional photography only; never presented as a live camera feed. */
@Composable
fun SecurityHeroCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val transition = rememberInfiniteTransition(label = "heroAmbient")
    val glow by transition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.26f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heroGlow"
    )
    val cardHeight = if (compact) 142.dp else 164.dp
    Box(
        modifier = modifier
            .height(cardHeight)
            .shadow(18.dp, RoundedCornerShape(26.dp), spotColor = CscOrange.copy(alpha = glow))
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF101B2E))
            .border(
                1.dp,
                Brush.linearGradient(listOf(Color(0xFF8291A8), CscOrange.copy(alpha = 0.75f), CscSteel)),
                RoundedCornerShape(26.dp)
            )
    ) {
        AsyncImage(
            model = "https://images.unsplash.com/photo-1600607687939-ce8a6c25118c?w=1200&auto=format&fit=crop&q=85",
            contentDescription = "Abitazione moderna protetta da un sistema di sicurezza",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Color(0xF0081224),
                    0.52f to Color(0xC5101D31),
                    1f to Color(0x3310182A)
                )
            )
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = if (compact) 12.dp else 16.dp)
                .size(if (compact) 78.dp else 94.dp)
                .shadow(12.dp, RoundedCornerShape(22.dp), spotColor = CscOrange.copy(alpha = 0.35f))
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFFF8A1F), CscOrange, Color(0xFFB94700))))
                .border(1.5.dp, Color.White.copy(alpha = 0.72f), RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.fillMaxSize().padding(7.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(Color(0xFF101B2E)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Videocam,
                    contentDescription = "Videocamera di sicurezza",
                    tint = Color.White,
                    modifier = Modifier.size(if (compact) 42.dp else 52.dp)
                )
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(0.64f)
                .padding(start = 18.dp, end = 4.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(25.dp).clip(CircleShape)
                        .background(CscOrange.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = CscOrange, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    "CSC SMART SECURITY  •  IMMAGINE PROMOZIONALE",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 6.5.sp,
                    letterSpacing = 0.65.sp,
                    fontFamily = MontserratFontFamily,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            Spacer(Modifier.height(9.dp))
            Text(
                title,
                color = CscTitleWhite,
                fontSize = if (compact) 14.sp else 17.sp,
                lineHeight = if (compact) 18.sp else 21.sp,
                fontFamily = MontserratFontFamily,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                color = Color(0xFFD0D9E8),
                fontSize = if (compact) 9.sp else 10.sp,
                lineHeight = 13.sp,
                fontFamily = MontserratFontFamily,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
 {
            Box(Modifier.size(5.dp).clip(CircleShape).background(Color(0xFF36D982)))
            Spacer(Modifier.size(6.dp))
            Text(
                "IMMAGINE PROMOZIONALE",
                color = Color.White,
                fontSize = 6.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.35.sp,
                maxLines = 1
            )
        }
    }
}
