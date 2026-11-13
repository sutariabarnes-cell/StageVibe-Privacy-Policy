package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NeonViolet,
    onPrimary = Color.White,
    primaryContainer = StageSurfaceElevated,
    onPrimaryContainer = NeonVioletLight,
    secondary = ElectricCyan,
    onSecondary = Color(0xFF00201A),
    secondaryContainer = Color(0xFF003831),
    onSecondaryContainer = ElectricCyan,
    tertiary = GoldCrown,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF3E2D00),
    onTertiaryContainer = GoldCrownLight,
    background = StageBackground,
    onBackground = TextPrimary,
    surface = StageSurface,
    onSurface = TextPrimary,
    surfaceVariant = StageSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = StageSurfaceHighlight,
    outlineVariant = DividerColor
)

private val LightColorScheme = DarkColorScheme // VersePulse is an immersive stage experience, standardizing on rich dark stage aesthetic

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep consistent stage branding
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
