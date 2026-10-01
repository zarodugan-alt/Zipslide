package com.example.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun ZipSlideAppTheme(
    themeSetting: String = "dark", // "dark", "light", "system"
    content: @Composable () -> Unit
) {
    val isDark = when (themeSetting.lowercase()) {
        "light" -> false
        "system" -> isSystemInDarkTheme()
        else -> true // Dark first by default
    }

    val zipSlideColors = if (isDark) DarkZipSlideColors else LightZipSlideColors
    val typography = ZipSlideTypography()

    val m3ColorScheme = if (isDark) {
        darkColorScheme(
            primary = zipSlideColors.accent,
            onPrimary = DesignTokens.Colors.Black,
            primaryContainer = zipSlideColors.accentSoft,
            onPrimaryContainer = zipSlideColors.accent,
            secondary = zipSlideColors.accent,
            onSecondary = DesignTokens.Colors.Black,
            background = zipSlideColors.bg,
            onBackground = zipSlideColors.textPrimary,
            surface = zipSlideColors.surface,
            onSurface = zipSlideColors.textPrimary,
            surfaceVariant = zipSlideColors.surfaceElev,
            onSurfaceVariant = zipSlideColors.textSecondary,
            outline = zipSlideColors.outline,
            error = zipSlideColors.error,
            onError = DesignTokens.Colors.Black
        )
    } else {
        lightColorScheme(
            primary = zipSlideColors.accent,
            onPrimary = DesignTokens.Colors.LightBg,
            primaryContainer = zipSlideColors.accentSoft,
            onPrimaryContainer = zipSlideColors.accent,
            secondary = zipSlideColors.accent,
            onSecondary = DesignTokens.Colors.LightBg,
            background = zipSlideColors.bg,
            onBackground = zipSlideColors.textPrimary,
            surface = zipSlideColors.surface,
            onSurface = zipSlideColors.textPrimary,
            surfaceVariant = zipSlideColors.surfaceElev,
            onSurfaceVariant = zipSlideColors.textSecondary,
            outline = zipSlideColors.outline,
            error = zipSlideColors.error,
            onError = DesignTokens.Colors.LightBg
        )
    }

    CompositionLocalProvider(
        LocalZipSlideColors provides zipSlideColors,
        LocalZipSlideTypography provides typography
    ) {
        MaterialTheme(
            colorScheme = m3ColorScheme,
            content = content
        )
    }
}
