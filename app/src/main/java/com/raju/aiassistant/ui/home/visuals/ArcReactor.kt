package com.raju.aiassistant.ui.home.visuals

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * SEEMA AI Official 3D Metallic Arc Reactor
 * 1:1 match to Reference Image Screen 1:
 * - 4 Diagonal X-Chassis Support Struts
 * - Outer Titanium Mechanical Bezel Ring with recessed Cyan Glow Slits
 * - Curved Dual Gold Armor Flanges on Left and Right Quadrants
 * - Glowing Purple & Electric Cyan Optical Channel
 * - Inner Segmented Metallic Chrono Bezel with Precision Gauge Markings
 * - Central Circular Aperture with Intense Electric Cyan Oscillating Soundwave
 */
@Composable
fun ArcReactor(
    style: ReactorStyle,
    particleCount: Int,
    richEffects: Boolean,
    time: () -> Float,
    speechLevel: () -> Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val t = time()
        val r = size.minDimension / 2f
        val c = center

        val level = speechLevel().coerceIn(0f, 1f)
        val beat = if (style.speechReactive) level else heartbeat(t, style.bpm)
        val energy = 0.6f + 0.4f * beat
        val spin = t * 24f // Smooth mechanical rotation

        // 1. Soft radial ambient glow behind reactor
        drawCircle(
            brush = Brush.radialGradient(
                listOf(
                    Color(0x3300E5FF),
                    Color(0x187C4DFF),
                    Color(0x060044AA),
                    Color.Transparent
                ),
                center = c,
                radius = r * 1.15f
            ),
            radius = r * 1.15f,
            center = c
        )

        // 2. Four Diagonal Corner Support Struts (X-Pattern)
        drawCornerStruts(c, r, spin * 0.05f)

        // 3. Whole reactor micro-pulse on audio/energy
        scale(1f + 0.035f * beat, pivot = c) {
            // 4. Outer Titanium Mechanical Collar
            drawOuterCollar(c, r)

            // 5. Dual Curved Gold Armor Flanges on Left & Right
            drawGoldFlanges(c, r)

            // 6. Segmented Outer Cog Ring with Cyan Slits (Counter-rotating)
            rotate(-spin * 0.4f, c) {
                drawOuterCogs(c, r)
            }

            // 7. Glowing Violet & Cyan Optical Channel
            drawOpticalChannel(c, r, t)

            // 8. Inner Stepped Cog & Chrono Gauge Markings
            rotate(spin * 0.7f, c) {
                drawInnerBezel(c, r)
            }

            // 9. Central Aperture (Void + Halo + Soundwave)
            drawCentralCore(c, r, t, energy, level)
        }
    }
}

private fun DrawScope.drawCornerStruts(c: Offset, r: Float, wobble: Float) {
    val angles = floatArrayOf(45f, 135f, 225f, 315f)
    for (deg in angles) {
        val rad = Math.toRadians((deg + wobble).toDouble())
        val cosA = cos(rad).toFloat()
        val sinA = sin(rad).toFloat()

        val p1 = Offset(c.x + r * 0.78f * cosA, c.y + r * 0.78f * sinA)
        val p2 = Offset(c.x + r * 1.25f * cosA, c.y + r * 1.25f * sinA)

        // Heavy dark chassis channel
        drawLine(
            color = Color(0xFF0F1B2B),
            start = p1,
            end = p2,
            strokeWidth = r * 0.075f,
            cap = StrokeCap.Round
        )
        // Metallic rail highlight
        drawLine(
            brush = Brush.linearGradient(
                listOf(Color(0xFF37474F), Color(0xFF78909C), Color(0xFF263238))
            ),
            start = p1,
            end = p2,
            strokeWidth = r * 0.045f,
            cap = StrokeCap.Round
        )
        // Electric cyan inner energy slit
        drawLine(
            color = Color(0xFF00E5FF).copy(alpha = 0.85f),
            start = p1,
            end = p2,
            strokeWidth = r * 0.012f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawOuterCollar(c: Offset, r: Float) {
    // Outer brushed metal ring
    drawCircle(
        brush = Brush.sweepGradient(
            listOf(
                Color(0xFF37474F), Color(0xFF78909C), Color(0xFF263238),
                Color(0xFF607D8B), Color(0xFF263238), Color(0xFF78909C), Color(0xFF37474F)
            ),
            center = c
        ),
        radius = r * 0.96f,
        center = c,
        style = Stroke(width = r * 0.045f)
    )

    // Deep recessed shadow ring
    drawCircle(
        color = Color(0xFF080E17),
        radius = r * 0.93f,
        center = c,
        style = Stroke(width = r * 0.016f)
    )
}

private fun DrawScope.drawGoldFlanges(c: Offset, r: Float) {
    val goldDark = Color(0xFF996515)
    val goldMid = Color(0xFFFFB300)
    val goldLight = Color(0xFFFFE082)

    val goldBrush = Brush.sweepGradient(
        listOf(goldDark, goldMid, goldLight, goldMid, goldDark),
        center = c
    )

    val flangeRadius = r * 0.895f
    val flangeThickness = r * 0.045f

    // Left curved armor plate (115 deg to 245 deg)
    drawArc(
        brush = goldBrush,
        startAngle = 115f,
        sweepAngle = 130f,
        useCenter = false,
        topLeft = Offset(c.x - flangeRadius, c.y - flangeRadius),
        size = Size(flangeRadius * 2, flangeRadius * 2),
        style = Stroke(width = flangeThickness, cap = StrokeCap.Round)
    )

    // Right curved armor plate (295 deg to 65 deg = sweep 130 deg)
    drawArc(
        brush = goldBrush,
        startAngle = 295f,
        sweepAngle = 130f,
        useCenter = false,
        topLeft = Offset(c.x - flangeRadius, c.y - flangeRadius),
        size = Size(flangeRadius * 2, flangeRadius * 2),
        style = Stroke(width = flangeThickness, cap = StrokeCap.Round)
    )

    // Golden cap studs / rivets at ends
    val rivetAngles = floatArrayOf(115f, 245f, 295f, 65f)
    for (deg in rivetAngles) {
        val rad = Math.toRadians(deg.toDouble())
        val rx = c.x + flangeRadius * cos(rad).toFloat()
        val ry = c.y + flangeRadius * sin(rad).toFloat()
        drawCircle(
            color = Color(0xFFFFD700),
            radius = r * 0.022f,
            center = Offset(rx, ry)
        )
        drawCircle(
            color = Color(0xFF5D4037),
            radius = r * 0.011f,
            center = Offset(rx, ry)
        )
    }
}

private fun DrawScope.drawOuterCogs(c: Offset, r: Float) {
    val cogR = r * 0.81f
    val segments = 16
    val sweepPerSeg = 360f / segments
    val gap = 6f
    val arcLen = sweepPerSeg - gap

    for (i in 0 until segments) {
        val startA = i * sweepPerSeg + gap / 2f
        drawArc(
            color = Color(0xFF1E2C3D),
            startAngle = startA,
            sweepAngle = arcLen,
            useCenter = false,
            topLeft = Offset(c.x - cogR, c.y - cogR),
            size = Size(cogR * 2, cogR * 2),
            style = Stroke(width = r * 0.048f)
        )

        // Radial glowing cyan slit between segments
        val slitAngle = Math.toRadians((i * sweepPerSeg).toDouble())
        val s1 = Offset(c.x + (cogR - r * 0.028f) * cos(slitAngle).toFloat(), c.y + (cogR - r * 0.028f) * sin(slitAngle).toFloat())
        val s2 = Offset(c.x + (cogR + r * 0.028f) * cos(slitAngle).toFloat(), c.y + (cogR + r * 0.028f) * sin(slitAngle).toFloat())
        drawLine(
            color = Color(0xFF00E5FF),
            start = s1,
            end = s2,
            strokeWidth = r * 0.012f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawOpticalChannel(c: Offset, r: Float, t: Float) {
    // Deep neon violet conduit
    drawCircle(
        color = Color(0xFF7C4DFF).copy(alpha = 0.85f),
        radius = r * 0.69f,
        center = c,
        style = Stroke(width = r * 0.022f)
    )

    // Vibrant Electric Blue Halo Ring
    drawCircle(
        brush = Brush.sweepGradient(
            listOf(Color(0xFF00E5FF), Color(0xFF0077FF), Color(0xFF00E5FF)),
            center = c
        ),
        radius = r * 0.62f,
        center = c,
        style = Stroke(width = r * 0.020f)
    )
}

private fun DrawScope.drawInnerBezel(c: Offset, r: Float) {
    val bezelR = r * 0.53f
    drawCircle(
        color = Color(0xFF162333),
        radius = bezelR,
        center = c,
        style = Stroke(width = r * 0.026f)
    )

    // 48 Precision gauge tick marks
    val count = 48
    for (i in 0 until count) {
        val a = Math.toRadians((i * (360.0 / count)))
        val isMajor = i % 4 == 0
        val tlen = if (isMajor) r * 0.024f else r * 0.012f
        val color = if (isMajor) Color(0xFF00E5FF) else Color(0xFF00E5FF).copy(alpha = 0.45f)
        val width = if (isMajor) r * 0.007f else r * 0.004f

        val x1 = c.x + (bezelR - tlen) * cos(a).toFloat()
        val y1 = c.y + (bezelR - tlen) * sin(a).toFloat()
        val x2 = c.x + (bezelR + tlen) * cos(a).toFloat()
        val y2 = c.y + (bezelR + tlen) * sin(a).toFloat()

        drawLine(color = color, start = Offset(x1, y1), end = Offset(x2, y2), strokeWidth = width, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawCentralCore(c: Offset, r: Float, t: Float, energy: Float, level: Float) {
    val coreR = r * 0.42f

    // 1. Core Void (Pure Deep Dark Indigo Blue)
    drawCircle(
        color = Color(0xFF020714),
        radius = coreR,
        center = c
    )

    // 2. Glowing Concentric Halo Rims
    drawCircle(
        color = Color(0xFF00E5FF).copy(alpha = 0.95f),
        radius = coreR,
        center = c,
        style = Stroke(width = r * 0.018f)
    )
    drawCircle(
        color = Color(0xFF0055FF).copy(alpha = 0.55f),
        radius = coreR * 0.88f,
        center = c,
        style = Stroke(width = r * 0.010f)
    )

    // 3. Electric Cyan Oscillating Soundwave Visualizer
    val wavePath = Path()
    val glowPath = Path()
    val waveHalfWidth = coreR * 0.82f
    val amplitude = coreR * (0.35f + 0.40f * level) * energy
    val speed = t * 6.5f

    var isFirst = true
    val step = 4
    val startX = (c.x - waveHalfWidth).toInt()
    val endX = (c.x + waveHalfWidth).toInt()

    for (x in startX..endX step step) {
        val normX = (x - c.x) / waveHalfWidth // -1.0 to 1.0
        val env = exp(-normX * normX * 3.2f) // Gaussian bell curve keeps ends clamped to center

        // Harmonically rich waveform with multiple frequencies
        val wave = sin(normX * 14f - speed) * 0.65f +
                sin(normX * 26f + speed * 1.4f) * 0.35f

        val y = c.y + wave * amplitude * env

        if (isFirst) {
            wavePath.moveTo(x.toFloat(), y)
            glowPath.moveTo(x.toFloat(), y)
            isFirst = false
        } else {
            wavePath.lineTo(x.toFloat(), y)
            glowPath.lineTo(x.toFloat(), y)
        }
    }

    // Outer soft glow layer
    drawPath(
        glowPath,
        color = Color(0xFF00E5FF).copy(alpha = 0.45f * energy),
        style = Stroke(width = r * 0.024f, cap = StrokeCap.Round)
    )

    // Inner bright white-cyan crisp core
    drawPath(
        wavePath,
        color = Color.White.copy(alpha = 0.95f),
        style = Stroke(width = r * 0.009f, cap = StrokeCap.Round)
    )
}
