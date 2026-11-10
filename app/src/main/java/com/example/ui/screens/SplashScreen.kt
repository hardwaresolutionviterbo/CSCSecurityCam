package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ripple
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.CscBrightBlue
import com.example.ui.theme.CscDeepBackground
import com.example.ui.theme.CscGlassBorder
import com.example.ui.theme.CscNeonGlow
import com.example.ui.theme.CscNightBlue
import com.example.ui.theme.CscOrange
import com.example.ui.theme.CscOrangeGlow
import com.example.ui.theme.CscPanel
import com.example.ui.theme.CscSecondaryText
import com.example.ui.theme.CscWhite
import com.example.ui.theme.MontserratFontFamily

@Composable
fun SplashScreen(
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit,
    onGuestClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_halo")
    val haloPulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloPulse"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CscDeepBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Decorative glowing cyber background arcs
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Top-left orange ambient arc
            val topArcPath = Path().apply {
                moveTo(0f, height * 0.16f)
                cubicTo(
                    width * 0.3f, height * 0.12f,
                    width * 0.7f, height * 0.18f,
                    width, height * 0.08f
                )
            }
            drawPath(
                path = topArcPath,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        CscOrange.copy(alpha = 0.5f),
                        CscOrangeGlow.copy(alpha = 0.8f),
                        Color.Transparent
                    )
                ),
                style = Stroke(width = 2.5f, cap = StrokeCap.Round)
            )

            // Middle arc above buttons
            val midArcPath = Path().apply {
                moveTo(-width * 0.1f, height * 0.44f)
                cubicTo(
                    width * 0.35f, height * 0.41f,
                    width * 0.65f, height * 0.41f,
                    width * 1.1f, height * 0.44f
                )
            }
            drawPath(
                path = midArcPath,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        CscOrange.copy(alpha = 0.65f),
                        CscOrangeGlow,
                        CscOrange.copy(alpha = 0.65f),
                        Color.Transparent
                    )
                ),
                style = Stroke(width = 2f, cap = StrokeCap.Round)
            )

            // Bottom horizon glow curve
            val bottomCurve = Path().apply {
                moveTo(-width * 0.2f, height * 0.94f)
                cubicTo(
                    width * 0.3f, height * 0.88f,
                    width * 0.7f, height * 0.88f,
                    width * 1.2f, height * 0.94f
                )
            }
            drawPath(
                path = bottomCurve,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        CscOrange.copy(alpha = 0.5f),
                        CscOrangeGlow.copy(alpha = 0.85f),
                        CscOrange.copy(alpha = 0.5f),
                        Color.Transparent
                    )
                ),
                style = Stroke(width = 3f)
            )
        }

        // Scrollable content column ensuring compatibility across all screen heights
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // 1. Central Camera Logo Badge with Glowing Orange Aura
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .scale(haloPulse),
                contentAlignment = Alignment.Center
            ) {
                // Neon halo ring
                Box(
                    modifier = Modifier
                        .size(146.dp)
                        .clip(RoundedCornerShape(36.dp))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    CscOrange.copy(alpha = 0.45f),
                                    CscOrangeGlow.copy(alpha = 0.18f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Main Logo Box
                Box(
                    modifier = Modifier
                        .size(126.dp)
                        .shadow(
                            elevation = 20.dp,
                            shape = RoundedCornerShape(32.dp),
                            spotColor = CscOrange,
                            ambientColor = Color.Black
                        )
                        .clip(RoundedCornerShape(32.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF1E283E),
                                    CscNightBlue
                                )
                            )
                        )
                        .border(
                            width = 2.dp,
                            brush = Brush.verticalGradient(
                                listOf(
                                    CscOrangeGlow,
                                    CscOrange
                                )
                            ),
                            shape = RoundedCornerShape(32.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_csc_logo),
                        contentDescription = "CSC SecurityCam Logo",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(30.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 2. Title & Payoff
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "CSC ",
                    fontSize = 32.sp,
                    fontFamily = MontserratFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Security",
                    fontSize = 32.sp,
                    fontFamily = MontserratFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = CscOrange
                )
                Text(
                    text = "Cam",
                    fontSize = 32.sp,
                    fontFamily = MontserratFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Payoff: VIDEOSORVEGLIANZA SMART
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(1.dp)
                        .background(CscSecondaryText.copy(alpha = 0.6f))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "VIDEOSORVEGLIANZA SMART",
                    fontSize = 11.sp,
                    fontFamily = MontserratFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = CscWhite,
                    letterSpacing = 2.5.sp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(1.dp)
                        .background(CscSecondaryText.copy(alpha = 0.6f))
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 3. Five Feature Highlights Row (Exact style from mockup)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                FeatureHighlightItem(
                    iconType = FeatureIconType.LIVE,
                    label = "Monitoraggio\nLive"
                )
                FeatureHighlightItem(
                    iconType = FeatureIconType.MOTION,
                    label = "Rilevamento\nMovimento"
                )
                FeatureHighlightItem(
                    iconType = FeatureIconType.REC,
                    label = "Registrazione\nVideo"
                )
                FeatureHighlightItem(
                    iconType = FeatureIconType.NOTIFICATIONS,
                    label = "Notifiche\nSmart"
                )
                FeatureHighlightItem(
                    iconType = FeatureIconType.REMOTE,
                    label = "Accesso\nRemoto"
                )
            }

            Spacer(modifier = Modifier.height(30.dp))

            // 4. Three Main Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // PULSANTE 1 – ACCEDI (Pieno Arancione con bagliore)
                MainActionButton(
                    text = "Accedi",
                    icon = Icons.Default.Person,
                    backgroundBrush = Brush.horizontalGradient(
                        listOf(
                            Color(0xFFFF7A1A),
                            CscOrange,
                            Color(0xFFE65500)
                        )
                    ),
                    borderColor = Color.Transparent,
                    hasGlow = true,
                    testTag = "btn_auth_login",
                    onClick = onLoginClick
                )

                // PULSANTE 2 – REGISTRATI (Blu notte con bordo arancione luminoso)
                MainActionButton(
                    text = "Registrati",
                    icon = Icons.Default.PersonAdd,
                    backgroundBrush = Brush.horizontalGradient(
                        listOf(
                            Color(0xFF131D31),
                            CscNightBlue
                        )
                    ),
                    borderColor = CscOrange,
                    hasGlow = false,
                    testTag = "btn_auth_register",
                    onClick = onRegisterClick
                )

                // PULSANTE 3 – CONTINUA COME OSPITE (Blu acciaio con bordo blu luminoso)
                MainActionButton(
                    text = "Continua come ospite",
                    icon = Icons.Default.Group,
                    backgroundBrush = Brush.horizontalGradient(
                        listOf(
                            Color(0xFF1E2D47),
                            Color(0xFF152238)
                        )
                    ),
                    borderColor = CscBrightBlue.copy(alpha = 0.85f),
                    hasGlow = false,
                    testTag = "btn_auth_guest",
                    onClick = onGuestClick
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            // 5. Social Login Divider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(CscPanel.copy(alpha = 0.8f))
                )
                Text(
                    text = "oppure accedi con",
                    fontSize = 12.sp,
                    fontFamily = MontserratFontFamily,
                    color = CscSecondaryText,
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(CscPanel.copy(alpha = 0.8f))
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Social Buttons Row (Google & Facebook)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SocialLoginButton(
                    provider = SocialProvider.GOOGLE,
                    label = "Google",
                    modifier = Modifier.weight(1f),
                    onClick = onLoginClick
                )
                SocialLoginButton(
                    provider = SocialProvider.FACEBOOK,
                    label = "Facebook",
                    modifier = Modifier.weight(1f),
                    onClick = onLoginClick
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 6. Security Shield Padlock with Horizon Line
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                CscOrange.copy(alpha = 0.35f),
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF2B1F17),
                                    Color(0xFF140E0A)
                                )
                            )
                        )
                        .border(1.5.dp, CscOrange, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Sicurezza Protetta",
                        tint = CscOrange,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

// ----------------------------------------------------------------------------
// Feature Highlights Components
// ----------------------------------------------------------------------------

enum class FeatureIconType {
    LIVE, MOTION, REC, NOTIFICATIONS, REMOTE
}

@Composable
private fun FeatureHighlightItem(
    iconType: FeatureIconType,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(62.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0F1A2E).copy(alpha = 0.85f))
                .border(1.dp, Color(0x3338BDF8), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            when (iconType) {
                FeatureIconType.LIVE -> {
                    // LIVE badge container with camera
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .border(1.dp, CscOrange, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "LIVE",
                                fontSize = 8.sp,
                                fontFamily = MontserratFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = CscOrange
                            )
                        }
                    }
                }
                FeatureIconType.MOTION -> {
                    Icon(
                        imageVector = Icons.Default.DirectionsRun,
                        contentDescription = null,
                        tint = CscOrange,
                        modifier = Modifier.size(26.dp)
                    )
                }
                FeatureIconType.REC -> {
                    // REC badge container
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .border(1.dp, CscOrange, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444))
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "REC",
                                fontSize = 8.sp,
                                fontFamily = MontserratFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = CscOrange
                            )
                        }
                    }
                }
                FeatureIconType.NOTIFICATIONS -> {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = CscOrange,
                        modifier = Modifier.size(24.dp)
                    )
                }
                FeatureIconType.REMOTE -> {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        tint = CscOrange,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = label,
            fontSize = 10.sp,
            fontFamily = MontserratFontFamily,
            fontWeight = FontWeight.Medium,
            color = CscWhite,
            textAlign = TextAlign.Center,
            lineHeight = 13.sp
        )
    }
}

// ----------------------------------------------------------------------------
// Action Pill Button
// ----------------------------------------------------------------------------

@Composable
private fun MainActionButton(
    text: String,
    icon: ImageVector,
    backgroundBrush: Brush,
    borderColor: Color,
    hasGlow: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .then(
                if (hasGlow) {
                    Modifier.shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(50.dp),
                        spotColor = CscOrange,
                        ambientColor = CscNeonGlow
                    )
                } else Modifier
            )
            .clip(RoundedCornerShape(50.dp))
            .background(backgroundBrush)
            .then(
                if (borderColor != Color.Transparent) {
                    Modifier.border(1.5.dp, borderColor, RoundedCornerShape(50.dp))
                } else Modifier
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = Color.White.copy(alpha = 0.2f)),
                onClick = onClick
            )
            .padding(horizontal = 22.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Text(
                    text = text,
                    fontSize = 16.sp,
                    fontFamily = MontserratFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ----------------------------------------------------------------------------
// Social Login Button
// ----------------------------------------------------------------------------

enum class SocialProvider {
    GOOGLE, FACEBOOK
}

@Composable
private fun SocialLoginButton(
    provider: SocialProvider,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(50.dp))
            .background(Color(0xFF131D31))
            .border(1.dp, CscGlassBorder, RoundedCornerShape(50.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = Color.White.copy(alpha = 0.2f)),
                onClick = onClick
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (provider == SocialProvider.GOOGLE) {
            // Google 'G' stylized logo
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "G",
                    fontSize = 13.sp,
                    fontFamily = MontserratFontFamily,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF4285F4)
                )
            }
        } else {
            // Facebook 'f' logo
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1877F2)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "f",
                    fontSize = 14.sp,
                    fontFamily = MontserratFontFamily,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = label,
            fontSize = 14.sp,
            fontFamily = MontserratFontFamily,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}
