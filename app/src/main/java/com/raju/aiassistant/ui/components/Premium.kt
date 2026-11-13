package com.raju.aiassistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
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

    /** Neon rim colours of the six crystal tiles on Home (each tile glows in its own colour). */
    val NeonBrain = Color(0xFF2FE6FF)
    val NeonVoice = Color(0xFF3FB8FF)
    val NeonMemory = Color(0xFFB98CFF)
    val NeonAutomation = Color(0xFFFFC65A)
    val NeonApps = Color(0xFF3DF0B2)
    val NeonSettings = Color(0xFFB4CCF5)

    val CardShape = RoundedCornerShape(22.dp)
}

/**
 * Crystal glass with a neon rim, for Home: a dark see-through body that lightens towards the edge in
 * the tile's own colour, a fine bright line along the top (the light on the glass), a glowing rim and a
 * soft bloom around it. [lit] 0..1 (pressed) makes the rim and the bloom brighter.
 */
fun Modifier.crystalSurface(
    shape: Shape = Premium.CardShape,
    accent: Color = Premium.Aqua,
    lit: Float = 0f,
): Modifier = this
    .drawBehind {
        // Bloom: three soft rings just outside the rim (no blur needed, works on every Android).
        val outline = shape.createOutline(size, layoutDirection, this)
        val boost = 1f + 1.6f * lit
        drawOutline(outline, accent.copy(alpha = (0.05f * boost).coerceAtMost(1f)), style = Stroke(9.dp.toPx()))
        drawOutline(outline, accent.copy(alpha = (0.09f * boost).coerceAtMost(1f)), style = Stroke(5.5.dp.toPx()))
        drawOutline(outline, accent.copy(alpha = (0.16f * boost).coerceAtMost(1f)), style = Stroke(3.dp.toPx()))
    }
    .clip(shape)
    .drawWithContent {
        val reach = maxOf(size.width, size.height) * 0.78f
        if (reach > 1f) {
            // The glass body: near-black navy in the middle, the tile's colour coming up at the edge.
            drawRect(
                Brush.radialGradient(
                    0f to Color(0xD903060D), 0.55f to Color(0xD9081226), 1f to accent.copy(alpha = 0.20f + 0.16f * lit),
                    center = center, radius = reach,
                ),
            )
            // Light falling on the upper half of the glass.
            drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.11f + 0.07f * lit), 0.45f to Color.Transparent))
        }
        drawContent()
        // The specular line: a hair of white light along the top edge, fading at both ends.
        val y = 1.6.dp.toPx()
        val x0 = size.width * 0.16f
        val x1 = size.width * 0.84f
        if (x1 - x0 > 1f) {
            drawLine(
                Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.80f), Color.Transparent), startX = x0, endX = x1),
                Offset(x0, y), Offset(x1, y), strokeWidth = 1.2.dp.toPx(), cap = StrokeCap.Round,
            )
        }
    }
    .border(
        1.3.dp,
        Brush.linearGradient(
            listOf(
                accent.copy(alpha = (0.90f + 0.10f * lit).coerceAtMost(1f)),
                accent.copy(alpha = 0.30f + 0.40f * lit),
                Color.White.copy(alpha = 0.30f + 0.30f * lit),
                accent.copy(alpha = (0.80f + 0.20f * lit).coerceAtMost(1f)),
            ),
        ),
        shape,
    )

/**
 * A glowing glass orb for a tile's icon: a halo, a sphere lit from the top left, a small white glint
 * and a bright rim. Draw behind a circular box that holds the icon.
 */
fun Modifier.glowingOrb(accent: Color, lit: Float = 0f): Modifier = this.drawBehind {
    val r = size.minDimension / 2f
    if (!(r > 1f)) return@drawBehind
    val c = center
    // Halo (bloom) around the orb.
    drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.42f + 0.30f * lit), Color.Transparent), c, r * 1.75f), r * 1.75f, c)
    // The sphere: lit from the top left, falling off to deep navy at the lower right.
    val light = Offset(c.x - r * 0.32f, c.y - r * 0.38f)
    drawCircle(
        Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.55f), 0.28f to accent.copy(alpha = 0.60f), 0.70f to accent.copy(alpha = 0.22f), 1f to Color(0xE6040A18),
            center = light, radius = r * 1.45f,
        ),
        r, c,
    )
    // The glint and the rim.
    drawOval(
        Color.White.copy(alpha = 0.55f),
        topLeft = Offset(c.x - r * 0.52f, c.y - r * 0.66f), size = androidx.compose.ui.geometry.Size(r * 0.50f, r * 0.24f),
    )
    drawCircle(accent.copy(alpha = (0.85f + 0.15f * lit).coerceAtMost(1f)), r, c, style = Stroke(1.2.dp.toPx()))
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


/**
 * Home's soft glass (the user's choice, like the "आँख | Liquid Glass" switch): a faint frosted fill with
 * no neon rim, a hairline edge and a little light on the top half. [lit] (0..1, pressed) brightens it.
 */
fun Modifier.softGlass(shape: Shape = RoundedCornerShape(18.dp), lit: Float = 0f): Modifier = this
    .clip(shape)
    .background(Color.White.copy(alpha = 0.06f + 0.06f * lit))
    .drawBehind {
        drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.05f + 0.04f * lit), 0.5f to Color.Transparent))
    }
    .border(0.6.dp, Color.White.copy(alpha = 0.07f + 0.10f * lit), shape)
