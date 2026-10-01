package com.example.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ZipSlide Design Tokens
 * Strictly defines all colors, typography, spacing, radii, and motion.
 * No hardcoded hex values anywhere else in the application.
 */
object DesignTokens {

    object Colors {
        // Dark theme (Default)
        val DarkBg = Color(0xFF0B0B0F)
        val DarkSurface = Color(0xFF14141A)
        val DarkSurfaceElev = Color(0xFF1C1C24)
        val DarkSurfaceHigh = Color(0xFF26262F)
        val DarkOutline = Color(0xFF2E2E38)
        val DarkTextPrimary = Color(0xFFF5F5F7)
        val DarkTextSecondary = Color(0xFFA0A0AB)
        val DarkTextTertiary = Color(0xFF6B6B76)
        val DarkAccent = Color(0xFFE8B458)
        val DarkAccentSoft = Color(0xFF3A2E18)
        val DarkSuccess = Color(0xFF4ADE80)
        val DarkWarning = Color(0xFFFBBF24)
        val DarkError = Color(0xFFF87171)

        // Light theme
        val LightBg = Color(0xFFFAFAFB)
        val LightSurface = Color(0xFFFFFFFF)
        val LightSurfaceElev = Color(0xFFF2F2F5)
        val LightSurfaceHigh = Color(0xFFE9E9EE)
        val LightOutline = Color(0xFFE4E4EA)
        val LightTextPrimary = Color(0xFF101014)
        val LightTextSecondary = Color(0xFF5C5C68)
        val LightTextTertiary = Color(0xFF8A8A96)
        val LightAccent = Color(0xFFB8862B)
        val LightAccentSoft = Color(0xFFFBF0D8)
        val LightSuccess = Color(0xFF22C55E)
        val LightWarning = Color(0xFFEAB308)
        val LightError = Color(0xFFEF4444)

        // Black for pure dark slideshow & scrims
        val Black = Color(0xFF000000)
        val Transparent = Color(0x00000000)
    }

    object Spacing {
        val s4: Dp = 4.dp
        val s8: Dp = 8.dp
        val s12: Dp = 12.dp
        val s16: Dp = 16.dp
        val s20: Dp = 20.dp
        val s24: Dp = 24.dp
        val s32: Dp = 32.dp
        val s48: Dp = 48.dp
        val s64: Dp = 64.dp

        val screenGutter: Dp = 20.dp
        val sectionGap: Dp = 32.dp
        val cardGap: Dp = 12.dp
        val cardInnerPadding: Dp = 12.dp
    }

    object Radii {
        val sm: Dp = 10.dp
        val md: Dp = 16.dp
        val lg: Dp = 22.dp
        val xl: Dp = 28.dp
        val full: Dp = 999.dp
    }

    object Motion {
        const val FastDurationMs = 140
        const val StandardDurationMs = 220
        const val ExpressiveDurationMs = 380

        val standardEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
        val fastEasing: Easing = CubicBezierEasing(0.0f, 0f, 0.2f, 1f)

        const val SpringStiffness = 380f
        const val SpringDamping = 32f
    }
}

data class ZipSlideColorScheme(
    val bg: Color,
    val surface: Color,
    val surfaceElev: Color,
    val surfaceHigh: Color,
    val outline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val accentSoft: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val isDark: Boolean
)

val DarkZipSlideColors = ZipSlideColorScheme(
    bg = DesignTokens.Colors.DarkBg,
    surface = DesignTokens.Colors.DarkSurface,
    surfaceElev = DesignTokens.Colors.DarkSurfaceElev,
    surfaceHigh = DesignTokens.Colors.DarkSurfaceHigh,
    outline = DesignTokens.Colors.DarkOutline,
    textPrimary = DesignTokens.Colors.DarkTextPrimary,
    textSecondary = DesignTokens.Colors.DarkTextSecondary,
    textTertiary = DesignTokens.Colors.DarkTextTertiary,
    accent = DesignTokens.Colors.DarkAccent,
    accentSoft = DesignTokens.Colors.DarkAccentSoft,
    success = DesignTokens.Colors.DarkSuccess,
    warning = DesignTokens.Colors.DarkWarning,
    error = DesignTokens.Colors.DarkError,
    isDark = true
)

val LightZipSlideColors = ZipSlideColorScheme(
    bg = DesignTokens.Colors.LightBg,
    surface = DesignTokens.Colors.LightSurface,
    surfaceElev = DesignTokens.Colors.LightSurfaceElev,
    surfaceHigh = DesignTokens.Colors.LightSurfaceHigh,
    outline = DesignTokens.Colors.LightOutline,
    textPrimary = DesignTokens.Colors.LightTextPrimary,
    textSecondary = DesignTokens.Colors.LightTextSecondary,
    textTertiary = DesignTokens.Colors.LightTextTertiary,
    accent = DesignTokens.Colors.LightAccent,
    accentSoft = DesignTokens.Colors.LightAccentSoft,
    success = DesignTokens.Colors.LightSuccess,
    warning = DesignTokens.Colors.LightWarning,
    error = DesignTokens.Colors.LightError,
    isDark = false
)

data class ZipSlideTypography(
    val display: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp
    ),
    val titleL: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp
    ),
    val titleM: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp
    ),
    val body: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    val bodyM: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    val label: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    ),
    val caption: TextStyle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    )
)

val LocalZipSlideColors = staticCompositionLocalOf { DarkZipSlideColors }
val LocalZipSlideTypography = staticCompositionLocalOf { ZipSlideTypography() }

object ZipSlideTheme {
    val colors: ZipSlideColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalZipSlideColors.current

    val typography: ZipSlideTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalZipSlideTypography.current

    val spacing: DesignTokens.Spacing
        get() = DesignTokens.Spacing

    val radii: DesignTokens.Radii
        get() = DesignTokens.Radii

    val motion: DesignTokens.Motion
        get() = DesignTokens.Motion
}
