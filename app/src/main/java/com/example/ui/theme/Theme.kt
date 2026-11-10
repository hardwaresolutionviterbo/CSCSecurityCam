package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CscDarkColorScheme = darkColorScheme(
    primary = CscOrange,
    onPrimary = Color.White,
    primaryContainer = CscOrangeContainer,
    onPrimaryContainer = CscOrangeGlow,
    secondary = CscSteel,
    onSecondary = CscIceWhite,
    background = CscMidnightBlue,
    onBackground = CscIceWhite,
    surface = CscPanelBlue,
    onSurface = CscIceWhite,
    surfaceVariant = CscPanelBlueVariant,
    onSurfaceVariant = CscTextMuted,
    outline = CscSteel,
    error = CscLiveRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Premium Dark by design
    dynamicColor: Boolean = false, // Keep branded Dark Premium styling
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CscDarkColorScheme,
        typography = Typography,
        content = content
    )
}
