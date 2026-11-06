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
private const val DOTS = 720
private val PUPIL_DARK = Color(0xFF0A1423)

/** The tone of her last reply, shown by the colour of the glowing rim. */
enum class TaporiMood { CALM, NORMAL, FUNNY, SHARP }

private val FUNNY_MARKS = Regex("haha|hehe|hihi|lol|😂|🤣|😜|😆|😄|😁|हाहा|हेहे|हीही|mazaa|maza aa|मज़ा|मजा", RegexOption.IGNORE_CASE)
private val SHARP_MARKS = Regex(
    "\\b(arre|are|abe|bhai|bhidu|naatak|natak|nautanki|hawabaazi|hawabazi|bakwaas|bakwas|scene mat|seriously)\\b|" +
        "अरे|अबे|भाई|भिड़ू|नाटक|नौटंकी|हवाबाजी|हवाबाज़ी|बकवास|सीन मत",
    RegexOption.IGNORE_CASE,
)

/** Funny (laughs, 😂) or sharp (a roast: "arre bhai", "kya nautanki") reply; NORMAL otherwise, CALM without one. */
fun taporiMoodFor(reply: String?): TaporiMood = when {
    reply.isNullOrBlank() -> TaporiMood.CALM
    FUNNY_MARKS.containsMatchIn(reply) -> TaporiMood.FUNNY
    SHARP_MARKS.containsMatchIn(reply) || reply.count { it == '!' } >= 2 -> TaporiMood.SHARP
    else -> TaporiMood.NORMAL
}

/**
 * Colour of the glowing rim: cyan listening, purple thinking; while speaking it follows the
 * reply's tone (hot orange for a sharp roast, pink for a joke, red otherwise); calm cyan when quiet.
 */
fun taporiRimArgb(state: AssistantState, mood: TaporiMood): Long = when (state) {
    AssistantState.Listening, AssistantState.Thinking -> taporiAccentArgb(state)
    AssistantState.Speaking -> when (mood) {
        TaporiMood.SHARP -> 0xFFFF6A00
        TaporiMood.FUNNY -> 0xFFFF4FD8
        else -> taporiAccentArgb(state)
    }
    else -> taporiAccentArgb(state)
}

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
    mood: TaporiMood = TaporiMood.CALM,
) {
    val expression = taporiExpressionFor(state)
    val rim by animateColorAsState(Color(taporiRimArgb(state, mood)), tween(400), label = "taporiRim")
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

        // Dotted sphere: every dot stays in its place and twinkles — it swells and shrinks and
        // brightens on its own rhythm, so the ball shimmers without turning.
        val tilt = 0.28f
        val golden = PI * (3 - sqrt(5.0))
        // The ball swells a little with her voice (and breathes slightly at rest).
        val swell = 1f + 0.06f * level + 0.012f * sin(t * 1.6f)
        for (i in 0 until DOTS) {
            val y0 = 1.0 - 2.0 * (i + 0.5) / DOTS
            val r0 = sqrt(1.0 - y0 * y0)
            val a = golden * i
            val x1 = cos(a) * r0
            val z1 = sin(a) * r0
            val y2 = y0 * cos(tilt) - z1 * sin(tilt)
            val z2 = y0 * sin(tilt) + z1 * cos(tilt)
            val depth = ((z2 + 1.0) / 2.0).toFloat() // 0 back .. 1 front
            val px = c.x + (x1 * rx * swell).toFloat()
            val py = c.y + (y2 * ry * swell).toFloat()
            val pulse = 0.5f + 0.5f * sin(t * (2.2f + (i % 7) * 0.35f) + i * 2.399f) // 0..1, own rhythm
            drawCircle(
                color = accent.copy(alpha = (0.25f + 0.55f * depth) * (0.55f + 0.45f * pulse)),
                radius = unit * (0.008f + 0.014f * depth) * (0.6f + 0.9f * pulse),
                center = Offset(px, py),
            )
        }

        // Glowing rim around the ball; its colour shows the mood of her reply.
        val breathe = 0.75f + 0.25f * sin(t * 2.4f)
        val rimSize = androidx.compose.ui.geometry.Size(rx * 2f, ry * 2f)
        val rimTopLeft = Offset(c.x - rx, c.y - ry)
        drawOval(rim.copy(alpha = 0.18f * breathe), rimTopLeft, rimSize, style = Stroke(unit * 0.16f))
        drawOval(rim.copy(alpha = 0.35f * breathe), rimTopLeft, rimSize, style = Stroke(unit * 0.07f))
        drawOval(rim.copy(alpha = 0.95f), rimTopLeft, rimSize, style = Stroke(unit * 0.018f))

        // Sparks that drift out from the edge of the ball and fade away, each on its own cycle.
        for (i in 0 until 24) {
            val life = ((t * 0.35f + i * 0.137f) % 1f) // 0 born at the edge .. 1 gone
            val ang = i * 2.399f
            val dist = 1.0f + 0.22f * life
            val sx = c.x + cos(ang) * rx * dist
            val sy = c.y + sin(ang) * ry * dist
            val fade = (1f - life) * (0.6f + 0.4f * sin(t * 4f + i))
            drawCircle(HOT_WHITE.copy(alpha = fade.coerceIn(0f, 1f)), unit * (0.012f + 0.012f * (1f - life)), Offset(sx, sy))
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
    // The face keeps friendly proportions on a tall ball: its height scale is capped.
    val fy = minOf(ry, rx * 1.2f)
    // Blink for ~0.15 s every 4 s; wider eyes while listening.
    val phase = t % 4f
    val blink = if (phase > 3.85f) ((4f - phase) / 0.15f).coerceIn(0.12f, 1f) else 1f
    val open = blink * (if (expression == TaporiExpression.LISTEN) 1.12f else 1f)

    val eyeY = c.y - fy * 0.16f
    val eyeGap = rx * 0.36f
    val eyeW = rx * 0.26f
    val eyeH = fy * 0.26f * open
    val pupilR = unit * 0.075f

    // Where the pupils look: a slow circle while thinking, a small drift otherwise.
    val look = when (expression) {
        TaporiExpression.EYE_ROLL -> Offset(cos(t * 3f) * 0.45f, sin(t * 3f) * 0.3f)
        TaporiExpression.LISTEN -> Offset(0f, 0f)
        // Quick glances: hold a direction, then dart to the next one (every ~1.7 s).
        else -> {
            val step = (t / 1.7f).toInt()
            Offset(sin(step * 2.3f) * 0.35f, cos(step * 1.7f) * 0.15f)
        }
    }

    for (side in floatArrayOf(-1f, 1f)) {
        val ex = c.x + side * eyeGap
        val closed = expression == TaporiExpression.SLEEP || (expression == TaporiExpression.WINK && side > 0f)
        if (closed) {
            val bend = if (expression == TaporiExpression.WINK) fy * 0.08f else fy * 0.02f
            val lid = Path().apply {
                moveTo(ex - eyeW / 2f, eyeY)
                quadraticBezierTo(ex, eyeY + bend, ex + eyeW / 2f, eyeY)
            }
            drawPath(lid, HOT_WHITE, style = Stroke(unit * 0.035f, cap = StrokeCap.Round))
        } else {
            // Eye white, a coloured iris, a dark pupil and two glossy highlights.
            drawOval(
                color = HOT_WHITE,
                topLeft = Offset(ex - eyeW / 2f, eyeY - eyeH / 2f),
                size = androidx.compose.ui.geometry.Size(eyeW, eyeH),
            )
            if (blink > 0.5f) { // hidden while the lid is down
                val ix = ex + look.x * eyeW * 0.25f
                val iy = eyeY + look.y * eyeH * 0.25f
                val irisR = minOf(pupilR * 1.35f, eyeH * 0.45f)
                drawCircle(accent, irisR, Offset(ix, iy))
                drawCircle(PUPIL_DARK, irisR * 0.55f, Offset(ix, iy))
                drawCircle(HOT_WHITE, irisR * 0.28f, Offset(ix - irisR * 0.32f, iy - irisR * 0.32f))
                drawCircle(HOT_WHITE.copy(alpha = 0.8f), irisR * 0.14f, Offset(ix + irisR * 0.3f, iy + irisR * 0.28f))
            }
        }
    }

    // Wide grin; it opens with her voice while speaking.
    val mouthY = c.y + fy * 0.28f
    val half = rx * 0.42f
    val grin = when (expression) {
        TaporiExpression.SLEEP -> fy * 0.02f
        TaporiExpression.OOPS -> -fy * 0.02f
        TaporiExpression.EYE_ROLL -> fy * 0.05f
        else -> fy * 0.14f
    }
    val openBy = when (expression) {
        TaporiExpression.TALK -> fy * (0.02f + 0.20f * level)
        TaporiExpression.WINK -> fy * 0.05f
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
