package com.raju.aiassistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * The premium look shared by the home tiles and every settings page: deep ink glass, a hairline edge that
 * runs from champagne gold to the panel's own accent, a faint light from the top, and calm (not neon)
 * colours. The Living Eye keeps its own colours.
 */
object Premium {
    val Gold = Color(0xFFE6C98F)
    val GoldSoft = Color(0xFFCFB27A)
    val Aqua = Color(0xFF7FE3F0)
    val Ink = Color(0xF20C1526)
    val InkDeep = Color(0xF2060B15)
    val TextHigh = Color(0xFFF3F6FB)
    val TextLow = Color(0xFFBCC7D8)

    /** Calm accent colours for the six home tiles (the neon versions felt cheap). */
    val Brain = Color(0xFF7FE3F0)
    val Voice = Color(0xFF8DB6FF)
    val Memory = Color(0xFFC6AEFF)
    val Automation = Color(0xFFE6C98F)
    val Apps = Color(0xFF86E3BA)
    val Settings = Color(0xFFC9D4E3)

    val CardShape = RoundedCornerShape(22.dp)
}

/** Ink glass with a gold-to-accent hairline. [lit] 0..1 brightens the edge (pressed or selected). */
fun Modifier.premiumSurface(
    shape: Shape = Premium.CardShape,
    accent: Color = Premium.Aqua,
    lit: Float = 0f,
): Modifier = this
    .clip(shape)
    .background(Brush.verticalGradient(listOf(Premium.Ink, Premium.InkDeep)))
    .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.06f + 0.04f * lit), 0.35f to Color.Transparent))
    .border(
        1.dp,
        Brush.linearGradient(
            listOf(
                Premium.Gold.copy(alpha = 0.50f + 0.35f * lit),
                Color.White.copy(alpha = 0.07f),
                accent.copy(alpha = 0.32f + 0.40f * lit),
            ),
        ),
        shape,
    )
