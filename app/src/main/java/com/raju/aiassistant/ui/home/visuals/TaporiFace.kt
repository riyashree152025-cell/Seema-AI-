package com.raju.aiassistant.ui.home.visuals

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.raju.aiassistant.domain.model.AssistantState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private val HOT_WHITE = Color(0xFFFFF4F6)
private val FACE_BASE = Color(0xFF0A0F1D)
private const val DOTS = 460

/** What the Tapori face shows for an assistant state (pure, unit-testable). */
enum class TaporiExpression { SMIRK, LISTEN, EYE_ROLL, TALK, WINK, SLEEP, OOPS }

fun taporiExpressionFor(state: AssistantState): TaporiExpression = when (state) {
    AssistantState.Listening -> TaporiExpression.LISTEN
    AssistantState.Thinking, AssistantState.Executing -> TaporiExpression.EYE_ROLL
    AssistantState.Speaking -> TaporiExpression.TALK
    AssistantState.Success -> TaporiExpression.WINK
    AssistantState.Sleeping, AssistantState.Offline -> TaporiExpression.SLEEP
    AssistantState.Error, AssistantState.WaitingPermission -> TaporiExpression.OOPS
    AssistantState.Idle -> TaporiExpression.SMIRK
}

/**
 * Tapori mode: a glowing dotted red sphere that fills the whole area above the buttons (an oval
 * when the phone is tall), with sparkles around it and a cheerful face in front: big round eyes
 * with red pupils and a wide grin. Eyes blink, look around while she thinks, wink when a task is
 * done, close when asleep. The mouth opens with her real voice level. Decorative (hidden from
 * screen readers); [time] and [speechLevel] are read only while drawing.
 */
@Composable
fun TaporiFace(
    time: () -> Float,
    speechLevel: () -> Float,
    state: AssistantState,
    modifier: Modifier = Modifier,
) {
    val expression = taporiExpressionFor(state)
    // Cyan listening, purple thinking, red speaking/default; the colour changes smoothly.
    val accent by animateColorAsState(Color(taporiAccentArgb(state)), tween(400), label = "taporiAccent")
    Canvas(modifier.clearAndSetSemantics {}) {
        val t = time()
        val level = if (expression == TaporiExpression.TALK) speechLevel().coerceIn(0f, 1f) else 0f
        // The whole area: the sphere is an oval with the same proportions as the space.
        val rx = size.width / 2f * 0.94f
        val ry = size.height / 2f * 0.94f
        val c = center
        val unit = minOf(rx, ry)

        // Soft glow behind the sphere.
        drawCircle(
            Brush.radialGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent), c, rx * 1.15f),
            rx * 1.15f, c,
        )

        // Dotted sphere, turning slowly and tilted a little.
        val spin = t * 0.45f
        val tilt = 0.28f
        val golden = PI * (3 - sqrt(5.0))
        for (i in 0 until DOTS) {
            val y0 = 1.0 - 2.0 * (i + 0.5) / DOTS
            val r0 = sqrt(1.0 - y0 * y0)
            val a = golden * i
            val x0 = cos(a) * r0
            val z0 = sin(a) * r0
            // rotate around Y (spin), then tilt around X
            val x1 = x0 * cos(spin) + z0 * sin(spin)
            val z1 = -x0 * sin(spin) + z0 * cos(spin)
            val y2 = y0 * cos(tilt) - z1 * sin(tilt)
            val z2 = y0 * sin(tilt) + z1 * cos(tilt)
            val depth = ((z2 + 1.0) / 2.0).toFloat() // 0 back .. 1 front
            val px = c.x + (x1 * rx).toFloat()
            val py = c.y + (y2 * ry).toFloat()
            drawCircle(
                color = accent.copy(alpha = 0.18f + 0.75f * depth),
                radius = unit * (0.012f + 0.016f * depth),
                center = Offset(px, py),
            )
        }

        // Sparkles around the sphere, twinkling at different times.
        for (i in 0 until 14) {
            val ang = i * 2.399f + t * 0.15f
            val dist = 1.02f + 0.08f * sin(t * 1.3f + i)
            val sx = c.x + cos(ang) * rx * dist
            val sy = c.y + sin(ang) * ry * dist
            val twinkle = 0.4f + 0.6f * (0.5f + 0.5f * sin(t * 3f + i * 1.7f))
            drawCircle(HOT_WHITE.copy(alpha = twinkle), unit * 0.02f, Offset(sx, sy))
        }

        // The face on the front of the sphere.
        drawFace(expression, t, level, c, rx, ry, unit, accent)
    }
}

private fun DrawScope.drawFace(
    expression: TaporiExpression,
    t: Float,
    level: Float,
    c: Offset,
    rx: Float,
    ry: Float,
    unit: Float,
    accent: Color,
) {
    // Blink for ~0.15 s every 4 s; wider eyes while listening.
    val phase = t % 4f
    val blink = if (phase > 3.85f) ((4f - phase) / 0.15f).coerceIn(0.12f, 1f) else 1f
    val open = blink * (if (expression == TaporiExpression.LISTEN) 1.12f else 1f)

    val eyeY = c.y - ry * 0.16f
    val eyeGap = rx * 0.36f
    val eyeW = rx * 0.26f
    val eyeH = ry * 0.26f * open
    val pupilR = unit * 0.075f

    // Where the pupils look: a slow circle while thinking, a small drift otherwise.
    val look = when (expression) {
        TaporiExpression.EYE_ROLL -> Offset(cos(t * 3f) * 0.45f, sin(t * 3f) * 0.3f)
        TaporiExpression.LISTEN -> Offset(0f, 0f)
        else -> Offset(sin(t * 0.7f) * 0.25f, 0f)
    }

    for (side in floatArrayOf(-1f, 1f)) {
        val ex = c.x + side * eyeGap
        val closed = expression == TaporiExpression.SLEEP || (expression == TaporiExpression.WINK && side > 0f)
        if (closed) {
            val bend = if (expression == TaporiExpression.WINK) ry * 0.08f else ry * 0.02f
            val lid = Path().apply {
                moveTo(ex - eyeW / 2f, eyeY)
                quadraticBezierTo(ex, eyeY + bend, ex + eyeW / 2f, eyeY)
            }
            drawPath(lid, HOT_WHITE, style = Stroke(unit * 0.035f, cap = StrokeCap.Round))
        } else {
            // Big round white eye with a red pupil.
            drawOval(
                color = HOT_WHITE,
                topLeft = Offset(ex - eyeW / 2f, eyeY - eyeH / 2f),
                size = androidx.compose.ui.geometry.Size(eyeW, eyeH),
            )
            drawOval(
                color = accent,
                topLeft = Offset(ex - pupilR + look.x * eyeW * 0.25f, eyeY - pupilR + look.y * eyeH * 0.25f),
                size = androidx.compose.ui.geometry.Size(pupilR * 2f, pupilR * 2f),
            )
            // Little shine in the eye.
            drawCircle(HOT_WHITE, pupilR * 0.35f, Offset(ex - pupilR * 0.5f + look.x * eyeW * 0.25f, eyeY - pupilR * 0.6f + look.y * eyeH * 0.25f))
        }
    }

    // Wide grin; it opens with her voice while speaking.
    val mouthY = c.y + ry * 0.28f
    val half = rx * 0.42f
    val grin = when (expression) {
        TaporiExpression.SLEEP -> ry * 0.02f
        TaporiExpression.OOPS -> -ry * 0.02f
        TaporiExpression.EYE_ROLL -> ry * 0.05f
        else -> ry * 0.14f
    }
    val openBy = when (expression) {
        TaporiExpression.TALK -> ry * (0.02f + 0.20f * level)
        TaporiExpression.WINK -> ry * 0.05f
        else -> 0f
    }
    val mouth = Path().apply {
        moveTo(c.x - half, mouthY)
        quadraticBezierTo(c.x, mouthY + grin * 2f + openBy, c.x + half, mouthY)
        quadraticBezierTo(c.x, mouthY + grin * 0.3f, c.x - half, mouthY)
        close()
    }
    drawPath(mouth, HOT_WHITE.copy(alpha = 0.9f))
    drawPath(mouth, accent, style = Stroke(unit * 0.03f, cap = StrokeCap.Round))
}
