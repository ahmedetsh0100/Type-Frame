package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.data.AppThemeMode
import com.example.data.ThemeSettings

@Immutable
data class StudioPalette(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceHighest: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val accentSoft: Color,
    val mint: Color,
    val mintSoft: Color,
    val danger: Color
)

val LocalStudioPalette = staticCompositionLocalOf {
    StudioPalette(
        isDark = true,
        background = StudioObsidian,
        surface = StudioSurface,
        surfaceElevated = StudioSurfaceElevated,
        surfaceHighest = StudioSurfaceHighest,
        border = StudioBorder,
        textPrimary = StudioTextPrimary,
        textSecondary = StudioTextSecondary,
        textMuted = StudioTextMuted,
        accent = StudioCoral,
        accentSoft = StudioCoralSoft,
        mint = StudioMint,
        mintSoft = StudioMintSoft,
        danger = StudioDanger
    )
}

@Composable
fun TypeframeTheme(
    settings: ThemeSettings = ThemeSettings(),
    content: @Composable () -> Unit
) {
    val isDark = when (settings.mode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val accent = Color(settings.accentColorArgb)
    val accentSoft = accent.copy(alpha = if (isDark) 0.22f else 0.14f)

    val palette = if (isDark) {
        StudioPalette(
            isDark = true,
            background = StudioObsidian,
            surface = StudioSurface,
            surfaceElevated = StudioSurfaceElevated,
            surfaceHighest = StudioSurfaceHighest,
            border = StudioBorder,
            textPrimary = StudioTextPrimary,
            textSecondary = StudioTextSecondary,
            textMuted = StudioTextMuted,
            accent = accent,
            accentSoft = accentSoft,
            mint = StudioMint,
            mintSoft = StudioMintSoft,
            danger = StudioDanger
        )
    } else {
        StudioPalette(
            isDark = false,
            background = StudioLightBg,
            surface = StudioLightSurface,
            surfaceElevated = StudioLightElevated,
            surfaceHighest = StudioLightHighest,
            border = StudioLightBorder,
            textPrimary = StudioLightTextPrimary,
            textSecondary = StudioLightTextSecondary,
            textMuted = StudioLightTextMuted,
            accent = accent,
            accentSoft = accentSoft,
            mint = Color(0xFF0E9F6E),
            mintSoft = Color(0xFFE3F9F0),
            danger = Color(0xFFD92D4B)
        )
    }

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = accentSoft,
            onPrimaryContainer = accent,
            secondary = palette.mint,
            onSecondary = StudioObsidian,
            secondaryContainer = palette.mintSoft,
            onSecondaryContainer = palette.mint,
            tertiary = StudioAmber,
            onTertiary = StudioObsidian,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceElevated,
            onSurfaceVariant = palette.textSecondary,
            surfaceContainerLow = palette.surface,
            surfaceContainer = palette.surfaceElevated,
            surfaceContainerHigh = palette.surfaceHighest,
            outline = palette.border,
            error = palette.danger,
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = accentSoft,
            onPrimaryContainer = accent,
            secondary = palette.mint,
            onSecondary = Color.White,
            secondaryContainer = palette.mintSoft,
            onSecondaryContainer = palette.mint,
            tertiary = StudioAmber,
            onTertiary = Color.White,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceElevated,
            onSurfaceVariant = palette.textSecondary,
            surfaceContainerLow = palette.surface,
            surfaceContainer = palette.surfaceElevated,
            surfaceContainerHigh = palette.surfaceHighest,
            outline = palette.border,
            error = palette.danger,
            onError = Color.White
        )
    }

    CompositionLocalProvider(LocalStudioPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TypeframeTypography,
            content = content
        )
    }
}
