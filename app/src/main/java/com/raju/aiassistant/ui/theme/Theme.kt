package com.raju.aiassistant.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val RajuColors = darkColorScheme(
    primary = Emerald,
    onPrimary = VoidBlack,
    primaryContainer = EmeraldMist,
    onPrimaryContainer = EmeraldBright,
    secondary = HoloCyan,
    onSecondary = VoidBlack,
    secondaryContainer = GlassPanelHigh,
    onSecondaryContainer = EmeraldBright,
    tertiary = HoloViolet,
    background = VoidBlack,
    onBackground = TextPrimary,
    surface = GlassPanel,
    onSurface = TextPrimary,
    surfaceVariant = GlassPanelHigh,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = GlassPanel,
    surfaceContainerHigh = GlassPanelHigh,
    surfaceContainerHighest = GlassPanelHigh,
    surfaceContainerLow = GlassPanel,
    error = ErrorRed,
    onError = VoidBlack,
    outline = Emerald.copy(alpha = 0.45f),
    outlineVariant = Emerald.copy(alpha = 0.18f),
)

/** Softly rounded fields, menus and dialogs (cards keep their HUD cut corners). */
private val RajuShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val RajuTypography = Typography(
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, letterSpacing = 1.5.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = 0.8.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp, letterSpacing = 0.4.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
    /** Hints under settings: 13 sp (Material's 12 sp default was hard to read on the dark panels). */
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 1.sp),
    /** Small technical HUD labels (status codes, telemetry). Monospace, but never below 11 sp. */
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.2.sp),
)

/** The app is always dark (sci-fi HUD look), independent of the system theme. */
@Composable
fun RajuTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RajuColors, typography = RajuTypography, shapes = RajuShapes, content = content)
}
