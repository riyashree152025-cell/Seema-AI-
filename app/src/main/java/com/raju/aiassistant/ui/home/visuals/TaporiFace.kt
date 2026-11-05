package com.raju.aiassistant.ui.home.visuals

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlin.math.sin

private val NEON_RED = Color(0xFFFF2D4A)
private val HOT_WHITE = Color(0xFFFFE3E8)

/**
 * Tapori mode: a cheeky face drawn on top of the red reactor — slanted brows, one eye squinting,
 * a one-sided smirk. The eyes blink now and then, open wider while she listens, and the smirk
 * opens with her voice while she speaks. Purely decorative (hidden from screen readers);
 * reads [time] and [speechLevel] only while drawing, so it does not recompose every frame.
 */
@Composable
fun TaporiFace(
    time: () -> Float,
    speechLevel: () -> Float,
    listening: Boolean,
    speaking: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.clearAndSetSemantics {}) {
        val t = time()
        val level = if (speaking) speechLevel().coerceIn(0f, 1f) else 0f
        val r = size.minDimension / 2f
        val c = center
        val stroke = r * 0.035f

        // Blink for ~0.15 s every 4 s; eyes a bit wider while listening.
        val phase = t % 4f
        val blink = if (phase > 3.85f) ((4f - phase) / 0.15f).coerceIn(0.1f, 1f) else 1f
        val open = blink * (if (listening) 1.25f else 1f)
        val eyeY = c.y - r * 0.12f

        for (side in floatArrayOf(-1f, 1f)) {
            val ex = c.x + side * r * 0.3f
            val w = r * 0.28f
            // The right eye squints a little: that is the mischief.
            val h = r * (if (side > 0f) 0.07f else 0.1f) * open
            drawCircle(
                Brush.radialGradient(listOf(NEON_RED.copy(alpha = 0.5f), Color.Transparent), Offset(ex, eyeY), w),
                w, Offset(ex, eyeY),
            )
            val eye = Path().apply {
                moveTo(ex - w / 2f, eyeY)
                quadraticBezierTo(ex, eyeY - h * 2f, ex + w / 2f, eyeY)
                quadraticBezierTo(ex, eyeY + h * 1.4f, ex - w / 2f, eyeY)
                close()
            }
            drawPath(eye, HOT_WHITE)
            drawPath(eye, NEON_RED, style = Stroke(stroke * 0.6f))
            // Brows slant down towards the middle: devilish, not angry-sad.
            drawLine(
                NEON_RED,
                Offset(ex + side * w * 0.6f, eyeY - r * 0.2f),
                Offset(ex - side * w * 0.5f, eyeY - r * 0.12f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }

        // One-sided smirk; it opens with her voice while she speaks.
        val mouthY = c.y + r * 0.22f
        val left = Offset(c.x - r * 0.26f, mouthY + r * 0.02f)
        val right = Offset(c.x + r * 0.3f, mouthY - r * 0.08f)
        val wobble = if (speaking) 0.02f * sin(t * 18f) else 0f
        val openBy = r * (0.03f + 0.16f * level + wobble)
        val mouth = Path().apply {
            moveTo(left.x, left.y)
            quadraticBezierTo(c.x, mouthY + r * 0.1f, right.x, right.y)
            quadraticBezierTo(c.x, mouthY + r * 0.1f + openBy * 2f, left.x, left.y)
            close()
        }
        drawPath(mouth, NEON_RED.copy(alpha = 0.35f + 0.4f * level))
        drawPath(mouth, HOT_WHITE, style = Stroke(stroke * 0.7f, cap = StrokeCap.Round))
        // A little dimple at the raised end of the smirk.
        drawLine(
            HOT_WHITE,
            Offset(right.x - r * 0.01f, right.y - r * 0.05f),
            Offset(right.x + r * 0.03f, right.y + r * 0.01f),
            strokeWidth = stroke * 0.6f,
            cap = StrokeCap.Round,
        )
    }
}
