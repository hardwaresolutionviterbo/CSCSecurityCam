package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.CscOrange

/**
 * Marchio CSC SecurityCam: badge arrotondato scuro con anello arancione e alone.
 * L'alone compare solo dove il marchio è elemento primario (splash), non nelle barre.
 */
@Composable
fun CscBrandMark(
    size: Dp = 64.dp,
    showGlow: Boolean = true,
    modifier: Modifier = Modifier
) {
    val outerSize = if (showGlow) size * 1.3f else size
    val shape = RoundedCornerShape(size * 0.26f)

    Box(
        modifier = modifier.size(outerSize),
        contentAlignment = Alignment.Center
    ) {
        if (showGlow) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                CscOrange.copy(alpha = 0.38f),
                                CscOrange.copy(alpha = 0.14f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        Box(
            modifier = Modifier
                .size(size)
                .then(
                    if (showGlow) {
                        Modifier.shadow(
                            elevation = 18.dp,
                            shape = shape,
                            spotColor = CscOrange,
                            ambientColor = CscOrange.copy(alpha = 0.4f)
                        )
                    } else {
                        Modifier
                    }
                )
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF1C2A44), Color(0xFF0F172A))
                    )
                )
                .border(width = 2.dp, color = CscOrange, shape = shape)
                .padding(size * 0.035f),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_csc_logo),
                contentDescription = "CSC SecurityCam",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
