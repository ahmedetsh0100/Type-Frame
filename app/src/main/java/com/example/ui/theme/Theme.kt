package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TypeframeDarkColorScheme = darkColorScheme(
    primary = StudioCoral,
    onPrimary = Color.White,
    primaryContainer = StudioCoralSoft,
    onPrimaryContainer = StudioCoral,
    secondary = StudioMint,
    onSecondary = StudioObsidian,
    secondaryContainer = StudioMintSoft,
    onSecondaryContainer = StudioMint,
    tertiary = StudioAmber,
    onTertiary = StudioObsidian,
    background = StudioObsidian,
    onBackground = StudioTextPrimary,
    surface = StudioSurface,
    onSurface = StudioTextPrimary,
    surfaceVariant = StudioSurfaceElevated,
    onSurfaceVariant = StudioTextSecondary,
    surfaceContainerLow = StudioSurface,
    surfaceContainer = StudioSurfaceElevated,
    surfaceContainerHigh = StudioSurfaceHighest,
    outline = StudioBorder,
    error = StudioDanger,
    onError = Color.White
)

@Composable
fun TypeframeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TypeframeDarkColorScheme,
        typography = TypeframeTypography,
        content = content
    )
}
