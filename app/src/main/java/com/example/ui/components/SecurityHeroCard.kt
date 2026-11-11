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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.CscOrange
import com.example.ui.theme.CscSteel
import com.example.ui.theme.CscTitleWhite
import com.example.ui.theme.MontserratFontFamily

/** Decorative photography only: this card is never represented as a live camera stream. */
@Composable
fun SecurityHeroCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val transition = rememberInfiniteTransition(label = "heroAmbient")
    val glow by transition.animateFloat(
        initialValue = 0.14f, targetValue = 0.30f,
        animationSpec = infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "heroGlow"
    )
    Box(
        modifier = modifier
            .height(if (compact) 148.dp else 184.dp)
            .shadow(18.dp, RoundedCornerShape(26.dp), spotColor = CscOrange.copy(alpha = glow))
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF111C30))
            .border(1.dp, Brush.linearGradient(listOf(Color(0xFF71839D), CscOrange.copy(alpha = 0.65f), CscSteel)), RoundedCornerShape(26.dp))
    ) {
        AsyncImage(
            model = "https://images.unsplash.com/photo-1600607687939-ce8a6c25118c?w=1200&auto=format&fit=crop&q=85",
            contentDescription = "Abitazione moderna illuminata al tramonto",
            modifier = Modifier.matchParentSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Color(0xE607101F), Color(0xAA17243A), Color(0x3310182A)))))
        AsyncImage(
            model = "https://images.unsplash.com/photo-1557597774-9d273605dfa9?w=480&auto=format&fit=crop&q=80",
            contentDescription = "Telecamera di sicurezza",
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = if (compact) 14.dp else 18.dp)
                .size(if (compact) 82.dp else 108.dp).clip(RoundedCornerShape(22.dp))
                .border(1.5.dp, CscOrange.copy(alpha = 0.9f), RoundedCornerShape(22.dp)),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
        Column(
            modifier = Modifier.align(Alignment.CenterStart).fillMaxWidth(0.70f)
                .padding(start = 18.dp, top = 14.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(27.dp).clip(CircleShape).background(CscOrange.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = CscOrange, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.size(8.dp))
                Text("CSC SMART SECURITY", color = Color.White.copy(alpha = 0.88f), fontSize = 8.sp, letterSpacing = 1.2.sp, fontFamily = MontserratFontFamily, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(9.dp))
            Text(title, color = CscTitleWhite, fontSize = if (compact) 15.sp else 18.sp, lineHeight = if (compact) 19.sp else 23.sp, fontFamily = MontserratFontFamily, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            Text(subtitle, color = Color(0xFFD0D9E8), fontSize = if (compact) 9.sp else 10.sp, lineHeight = 13.sp, fontFamily = MontserratFontFamily, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 18.dp, bottom = 10.dp)
                .clip(CircleShape).background(Color(0xCC0B1727)).border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                .padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF36D982)))
            Spacer(Modifier.size(6.dp))
            Text("IMMAGINE PROMOZIONALE", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
        }
    }
}
