package com.raju.aiassistant.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.raju.aiassistant.ui.theme.HoloViolet
import kotlin.math.abs
import kotlin.math.sin

/** What the voice HUD shows: resting, the microphone listening, or Seema speaking. */
enum class VoiceHudMode { IDLE, LISTENING, SPEAKING }

/**
 * The futuristic voice emblem used instead of a plain microphone icon: a glowing core with a
 * live waveform, turning segmented rings, and sound rings that ripple outwards while listening
 * or speaking. Purely visual — the button around it keeps its own click action and label.
 *
 * [time] (seconds) lets a screen drive it with its own frame-capped clock; without it a light
 * internal animation runs. [level] (0..1) is the current speech level, if known.
 */
@Composable
fun VoiceHudOrb(
    mode: VoiceHudMode,
    accent: Color,
    modifier: Modifier = Modifier,
    time: (() -> Float)? = null,
    level: () -> Float = { 0f },
) {
    val clock: () -> Float = time ?: run {
        val t = rememberInfiniteTransition(label = "voiceHud")
        val v = t.animateFloat(0f, 60f, infiniteRepeatable(tween(60_000, easing = LinearEasing), RepeatMode.Restart), label = "voiceHudTime")
        val read: () -> Float = { v.value }
        read
    }
    Canvas(modifier) {
        val t = clock()
        val lv = level().coerceIn(0f, 1f)
        val live = mode != VoiceHudMode.IDLE
        val r = size.minDimension / 2f
        val c = center
        val second = if (mode == VoiceHudMode.SPEAKING) HoloViolet else accent

        // Responsive glow: stronger while live, pulsing with the voice level.
        val glow = (if (live) 0.42f else 0.22f) + 0.25f * lv + (if (live) 0.06f * sin(t * 4f) else 0f)
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = glow), second.copy(alpha = glow * 0.35f), Color.Transparent), c, r), r, c)

        // Sound rings rippling outwards.
        if (live) {
            repeat(2) { i ->
                val p = ((t * (if (mode == VoiceHudMode.LISTENING) 0.9f else 1.3f)) + i * 0.5f) % 1f
                drawCircle(second.copy(alpha = (1f - p) * 0.55f), r * (0.48f + 0.5f * p), c, style = Stroke(1.4.dp.toPx()))
            }
        }

        // Turning segmented outer ring and a counter-rotating arc pair.
        val spin = (t * (if (live) 140f else 36f)) % 360f
        val inset = 2.dp.toPx()
        val ringSize = Size(size.width - inset * 2, size.height - inset * 2)
        drawArc(
            accent.copy(alpha = 0.75f), spin, 360f, false, Offset(inset, inset), ringSize,
            style = Stroke(1.6.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx())), cap = StrokeCap.Round),
        )
        val mid = r * 0.72f
        val midTopLeft = Offset(c.x - mid, c.y - mid)
        val midSize = Size(mid * 2, mid * 2)
        drawArc(second, -spin * 1.4f, 70f, false, midTopLeft, midSize, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))
        drawArc(second.copy(alpha = 0.6f), -spin * 1.4f + 180f, 40f, false, midTopLeft, midSize, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))

        // Core disc with the waveform.
        val core = r * 0.5f
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = if (live) 0.22f else 0.12f), accent.copy(alpha = 0.18f), Color.Transparent), c, core), core, c)
        drawCircle(accent.copy(alpha = 0.55f), core, c, style = Stroke(1.dp.toPx()))
        val bars = 7
        val gap = core * 1.5f / bars
        val barW = gap * 0.55f
        for (i in 0 until bars) {
            val x = c.x - core * 0.75f + gap * (i + 0.5f)
            val centerWeight = 1f - abs(i - (bars - 1) / 2f) / bars
            val wave = when (mode) {
                VoiceHudMode.IDLE -> 0.18f + 0.06f * sin(t * 1.5f + i)
                else -> 0.25f + (0.35f + 0.6f * lv) * abs(sin(t * (if (mode == VoiceHudMode.SPEAKING) 9f else 6f) + i * 0.9f))
            }
            val h = core * 1.1f * wave * (0.55f + 0.45f * centerWeight)
            drawLine(Color.White.copy(alpha = if (live) 0.95f else 0.75f), Offset(x, c.y - h / 2), Offset(x, c.y + h / 2), barW, StrokeCap.Round)
        }
    }
}
