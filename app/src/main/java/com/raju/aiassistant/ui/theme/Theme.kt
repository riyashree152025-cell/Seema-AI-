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

/**
 * Large, comfortable type (the old 11-14 sp hints were too small to read at a glance). Every screen's
 * text follows these sizes unless it sets its own.
 */
private val RajuTypography = Typography(
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = 0.6.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.3.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = 0.2.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.5.sp, lineHeight = 24.sp),
    /** Hints under settings: 15 sp (13 sp was still hard to read on the dark panels). */
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp, letterSpacing = 0.4.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.5.sp, letterSpacing = 0.3.sp),
    /** Small technical labels (status codes, telemetry). Monospace, never below 13 sp. */
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.8.sp),
)

/** The app is always dark (sci-fi HUD look), independent of the system theme. */
@Composable
fun RajuTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RajuColors, typography = RajuTypography, shapes = RajuShapes, content = content)
}
