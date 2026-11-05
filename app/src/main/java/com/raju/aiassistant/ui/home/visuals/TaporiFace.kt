package com.raju.aiassistant.ui.home.visuals

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
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
import kotlin.math.cos
import kotlin.math.sin

private val NEON_RED = Color(0xFFFF2D4A)
private val HOT_WHITE = Color(0xFFFFE3E8)
private val PUPIL = Color(0xFF5A0010)

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
 * Tapori mode: a cheeky face drawn on top of the red reactor — slanted brows, a squinting eye,
 * a one-sided smirk. It changes with what she is doing: wide eyes while listening, an eye-roll
 * while thinking, a smirk that opens with her voice while speaking, a wink when a task is done,
 * closed eyes while asleep, raised brows on an error. Eyes blink now and then. Purely decorative
 * (hidden from screen readers); [time] and [speechLevel] are read only while drawing.
 */
@Composable
fun TaporiFace(
    time: () -> Float,
    speechLevel: () -> Float,
    state: AssistantState,
    modifier: Modifier = Modifier,
) {
    val expression = taporiExpressionFor(state)
    Canvas(modifier.clearAndSetSemantics {}) {
        val t = time()
        val level = if (expression == TaporiExpression.TALK) speechLevel().coerceIn(0f, 1f) else 0f
        val r = size.minDimension / 2f
        val c = center
        val stroke = r * 0.035f

        // Blink for ~0.15 s every 4 s; wider eyes while listening.
        val phase = t % 4f
        val blink = if (phase > 3.85f) ((4f - phase) / 0.15f).coerceIn(0.1f, 1f) else 1f
        val open = blink * (if (expression == TaporiExpression.LISTEN) 1.25f else 1f)
        val eyeY = c.y - r * 0.12f

        // Where the pupils look: a slow circle for the eye-roll, a small drift otherwise.
        val look = when (expression) {
            TaporiExpression.EYE_ROLL -> Offset(cos(t * 3f) * 0.35f, -0.35f + sin(t * 3f) * 0.2f)
            TaporiExpression.LISTEN -> Offset(0f, 0f)
            else -> Offset(sin(t * 0.7f) * 0.2f, 0f)
        }

        for (side in floatArrayOf(-1f, 1f)) {
            val ex = c.x + side * r * 0.3f
            val w = r * 0.28f
            val closed = expression == TaporiExpression.SLEEP || (expression == TaporiExpression.WINK && side > 0f)
            drawCircle(
                Brush.radialGradient(listOf(NEON_RED.copy(alpha = if (closed) 0.25f else 0.5f), Color.Transparent), Offset(ex, eyeY), w),
                w, Offset(ex, eyeY),
            )
            if (closed) {
                // A closed eye: a curved line (smiling for the wink, flat for sleep).
                val bend = if (expression == TaporiExpression.WINK) r * 0.06f else r * 0.02f
                val lid = Path().apply {
                    moveTo(ex - w / 2f, eyeY)
                    quadraticBezierTo(ex, eyeY + bend, ex + w / 2f, eyeY)
                }
                drawPath(lid, HOT_WHITE, style = Stroke(stroke, cap = StrokeCap.Round))
            } else {
                // The right eye squints a little: that is the mischief.
                val h = r * (if (side > 0f) 0.07f else 0.1f) * open
                val eye = Path().apply {
                    moveTo(ex - w / 2f, eyeY)
                    quadraticBezierTo(ex, eyeY - h * 2f, ex + w / 2f, eyeY)
                    quadraticBezierTo(ex, eyeY + h * 1.4f, ex - w / 2f, eyeY)
                    close()
                }
                drawPath(eye, HOT_WHITE)
                if (blink > 0.5f) {
                    drawCircle(PUPIL, h * 0.75f, Offset(ex + look.x * w * 0.35f, eyeY - h * 0.25f + look.y * h))
                }
                drawPath(eye, NEON_RED, style = Stroke(stroke * 0.6f))
            }
            drawBrow(expression, side, ex, eyeY, w, r, stroke)
        }

        drawMouth(expression, t, level, c, r, stroke)
    }
}

/** Brows slant down to the middle (devilish); raised on an error. */
private fun DrawScope.drawBrow(expression: TaporiExpression, side: Float, ex: Float, eyeY: Float, w: Float, r: Float, stroke: Float) {
    val (outerY, innerY) = when (expression) {
        TaporiExpression.OOPS -> eyeY - r * 0.14f to eyeY - r * 0.22f
        TaporiExpression.LISTEN -> eyeY - r * 0.23f to eyeY - r * 0.17f
        else -> eyeY - r * 0.2f to eyeY - r * 0.12f
    }
    drawLine(
        NEON_RED,
        Offset(ex + side * w * 0.6f, outerY),
        Offset(ex - side * w * 0.5f, innerY),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
}

/** One-sided smirk; it opens with her voice, grins on a wink, goes small when asleep or on an error. */
private fun DrawScope.drawMouth(expression: TaporiExpression, t: Float, level: Float, c: Offset, r: Float, stroke: Float) {
    val mouthY = c.y + r * 0.22f
    val left = Offset(c.x - r * 0.26f, mouthY + r * 0.02f)
    val right = Offset(c.x + r * 0.3f, mouthY - r * 0.08f)
    val curve = when (expression) {
        TaporiExpression.WINK -> r * 0.16f
        TaporiExpression.SLEEP, TaporiExpression.OOPS -> r * 0.02f
        TaporiExpression.EYE_ROLL -> r * 0.05f
        else -> r * 0.1f
    }
    val openBy = when (expression) {
        TaporiExpression.TALK -> r * (0.03f + 0.16f * level + 0.02f * sin(t * 18f))
        TaporiExpression.WINK -> r * 0.07f
        TaporiExpression.SLEEP -> 0f
        else -> r * 0.03f
    }
    val mouth = Path().apply {
        moveTo(left.x, left.y)
        quadraticBezierTo(c.x, mouthY + curve, right.x, right.y)
        quadraticBezierTo(c.x, mouthY + curve + openBy * 2f, left.x, left.y)
        close()
    }
    drawPath(mouth, NEON_RED.copy(alpha = 0.35f + 0.4f * level))
    drawPath(mouth, HOT_WHITE, style = Stroke(stroke * 0.7f, cap = StrokeCap.Round))
    if (expression != TaporiExpression.SLEEP && expression != TaporiExpression.OOPS) {
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
