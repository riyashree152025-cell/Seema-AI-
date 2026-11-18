package com.raju.aiassistant.ui.kids

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import com.raju.aiassistant.domain.kids.AvatarMath
import com.raju.aiassistant.domain.kids.AvatarMood
import com.raju.aiassistant.domain.kids.Bubble
import com.raju.aiassistant.domain.kids.FaceZone
import com.raju.aiassistant.domain.kids.Particle
import com.raju.aiassistant.domain.model.AssistantState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Riya's animated friend: a warm, soft-shaded cartoon face drawn entirely on a Compose Canvas (no image files)
 * over a pastel night sky. It blinks, breathes, follows a touch with its eyes, moves its mouth with Seema's real
 * voice level, listens with glowing rings, thinks with sparkles, laughs when tickled, says "boop" on the nose,
 * and pops the bubbles floating around it. All motion comes from [AvatarMath]; this only draws.
 *
 * [level]: Seema's voice loudness 0..1 (the speaker's level). [onGiggle]: say a short Bengali laugh line.
 */
@Composable
fun RiyaAvatarFace(
    state: AssistantState,
    level: Float,
    onGiggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentState by rememberUpdatedState(state)
    val currentLevel by rememberUpdatedState(level)
    val giggle by rememberUpdatedState(onGiggle)

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var mouth by remember { mutableFloatStateOf(0f) }
    var particles by remember { mutableStateOf(emptyList<Particle>()) }
    var bubbles by remember { mutableStateOf(emptyList<Bubble>()) }
    var tickledUntil by remember { mutableLongStateOf(0L) }
    var boopUntil by remember { mutableLongStateOf(0L) }
    var lastGiggle by remember { mutableLongStateOf(0L) }
    var touch by remember { mutableStateOf<Offset?>(null) }
    var touchAt by remember { mutableLongStateOf(0L) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var nextBubbleAt by remember { mutableLongStateOf(0L) }
    var seed by remember { mutableIntStateOf(1) }
    var bubbleId by remember { mutableLongStateOf(1L) }

    // Short sounds: a soft pop, a "boop" chime, a giggle chime.
    val tones = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 55) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { runCatching { tones?.release() } } }

    // One clock for every motion on the face.
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { nanos ->
                val dt = if (last == 0L) 0.016f else ((nanos - last) / 1e9f).coerceIn(0f, 0.05f)
                last = nanos
                val t = System.currentTimeMillis()
                now = t
                val m = AvatarMath.mood(currentState, t, tickledUntil)
                mouth = AvatarMath.follow(mouth, AvatarMath.mouthOpen(m, currentLevel, t), dt)
                if (particles.isNotEmpty()) particles = AvatarMath.step(particles, dt)
                val sz = canvasSize
                if (sz.width > 0f) {
                    bubbles = AvatarMath.stepBubbles(bubbles, dt, sz.height)
                    if (t >= nextBubbleAt && bubbles.size < AvatarMath.MAX_BUBBLES) {
                        val (cx, cy, r) = faceGeometry(sz)
                        bubbles = bubbles + AvatarMath.newBubble(bubbleId++, sz.width, sz.height, cx, cy, r, seed++)
                        nextBubbleAt = t + 2_400L
                    }
                }
            }
        }
    }

    Canvas(
        modifier.pointerInput(Unit) {
            detectTapGestures { pos ->
                val t = System.currentTimeMillis()
                touch = pos
                touchAt = t
                val scaleK = min(size.width, size.height) / 1080f * 2f
                AvatarMath.bubbleAt(bubbles, pos.x, pos.y, t)?.let { b ->
                    bubbles = bubbles - b
                    particles = particles + AvatarMath.burst(pos.x, pos.y, Particle.Kind.SPARK, 10, seed++, scaleK)
                    runCatching { tones?.startTone(ToneGenerator.TONE_PROP_BEEP, 60) }
                    return@detectTapGestures
                }
                val (cx, cy, r) = faceGeometry(Size(size.width.toFloat(), size.height.toFloat()))
                when (AvatarMath.zone(pos.x, pos.y, cx, cy + AvatarMath.breath(t, r * 0.025f), r)) {
                    FaceZone.NOSE -> {
                        boopUntil = t + AvatarMath.BOOP_MS
                        particles = particles + AvatarMath.burst(pos.x, pos.y, Particle.Kind.STAR, 12, seed++, scaleK)
                        runCatching { tones?.startTone(ToneGenerator.TONE_PROP_ACK, 120) }
                    }
                    FaceZone.CHEEK, FaceZone.FACE -> {
                        tickledUntil = t + AvatarMath.TICKLE_MS
                        particles = particles + AvatarMath.burst(pos.x, pos.y, Particle.Kind.HEART, 9, seed++, scaleK)
                        runCatching { tones?.startTone(ToneGenerator.TONE_PROP_PROMPT, 90) }
                        if (t - lastGiggle > AvatarMath.GIGGLE_GAP_MS && currentState != AssistantState.Listening && currentState != AssistantState.Speaking) {
                            lastGiggle = t
                            giggle(AvatarMath.GIGGLES[(seed % AvatarMath.GIGGLES.size)])
                        }
                    }
                    FaceZone.OUTSIDE -> particles = particles + AvatarMath.burst(pos.x, pos.y, Particle.Kind.STAR, 6, seed++, scaleK)
                }
            }
        },
    ) {
        canvasSize = size
        val t = now
        val mood = AvatarMath.mood(state, t, tickledUntil)
        drawSky(t)
        val (cx, baseCy, r) = faceGeometry(size)
        val cy = baseCy + AvatarMath.breath(t, r * 0.025f)
        if (mood == AvatarMood.LISTENING) drawListeningRings(cx, cy, r, t)
        drawBubbles(bubbles, t)
        drawFace(cx, cy, r, t, mood, mouth, touch, t - touchAt, t < boopUntil)
        if (mood == AvatarMood.THINKING) drawThinkingSparkles(cx, cy, r, t)
        drawParticles(particles)
    }
}

/** The face: centred a little above the middle, its radius about a third of the shorter side (≈65 % width). */
private fun faceGeometry(size: Size): Triple<Float, Float, Float> {
    val r = min(size.width * 0.33f, size.height * 0.24f)
    return Triple(size.width / 2f, size.height * 0.42f, r)
}

// ------------------------------------------------------------------ background

private fun DrawScope.drawSky(t: Long) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF2B2A6B), Color(0xFF5B4B9A), Color(0xFFE9A6C9), Color(0xFFFFD6C2))))
    // Twinkling stars (fixed places, each with its own rhythm).
    val rnd = java.util.Random(7)
    repeat(46) { i ->
        val x = rnd.nextFloat() * size.width
        val y = rnd.nextFloat() * size.height * 0.62f
        val tw = 0.35f + 0.65f * ((sin(t / 600.0 + i * 1.7) + 1) / 2).toFloat()
        drawCircle(Color.White.copy(alpha = 0.25f + 0.55f * tw), radius = 1.6f + rnd.nextFloat() * 2.4f, center = Offset(x, y))
    }
    // A soft moon glow.
    drawCircle(
        Brush.radialGradient(listOf(Color(0x66FFF3BF), Color.Transparent), center = Offset(size.width * 0.15f, size.height * 0.12f), radius = size.width * 0.18f),
        radius = size.width * 0.18f, center = Offset(size.width * 0.15f, size.height * 0.12f),
    )
}

// ------------------------------------------------------------------ the face

private fun DrawScope.drawFace(
    cx: Float, cy: Float, r: Float, t: Long, mood: AvatarMood, mouthOpen: Float,
    touch: Offset?, touchAge: Long, booped: Boolean,
) {
    // Soft shadow under the head.
    drawOval(Color(0x33000000), topLeft = Offset(cx - r * 0.75f, cy + r * 1.02f), size = Size(r * 1.5f, r * 0.22f))
    // Hair behind the face.
    drawCircle(Brush.radialGradient(listOf(Color(0xFF5A3825), Color(0xFF2E1B12)), center = Offset(cx, cy - r * 0.1f), radius = r * 1.18f), radius = r * 1.12f, center = Offset(cx, cy - r * 0.06f))
    // Pigtails.
    for (side in listOf(-1f, 1f)) {
        drawCircle(Brush.radialGradient(listOf(Color(0xFF5A3825), Color(0xFF2E1B12)), center = Offset(cx + side * r * 1.05f, cy - r * 0.35f), radius = r * 0.34f), radius = r * 0.3f, center = Offset(cx + side * r * 1.05f, cy - r * 0.35f))
    }
    // Face: warm skin, soft 3D shading, forehead highlight.
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFFFE3CC), Color(0xFFF9C9A4), Color(0xFFE9A47C)), center = Offset(cx - r * 0.2f, cy - r * 0.3f), radius = r * 1.35f),
        radius = r, center = Offset(cx, cy),
    )
    drawOval(Color.White.copy(alpha = 0.22f), topLeft = Offset(cx - r * 0.42f, cy - r * 0.78f), size = Size(r * 0.62f, r * 0.26f))
    // Bangs: a soft fringe of curves over the forehead.
    val bangs = Path().apply {
        moveTo(cx - r * 0.98f, cy - r * 0.18f)
        cubicTo(cx - r * 0.95f, cy - r * 1.05f, cx + r * 0.95f, cy - r * 1.05f, cx + r * 0.98f, cy - r * 0.18f)
        cubicTo(cx + r * 0.8f, cy - r * 0.55f, cx + r * 0.55f, cy - r * 0.45f, cx + r * 0.42f, cy - r * 0.58f)
        cubicTo(cx + r * 0.3f, cy - r * 0.42f, cx + r * 0.05f, cy - r * 0.5f, cx, cy - r * 0.62f)
        cubicTo(cx - r * 0.12f, cy - r * 0.44f, cx - r * 0.38f, cy - r * 0.46f, cx - r * 0.46f, cy - r * 0.6f)
        cubicTo(cx - r * 0.6f, cy - r * 0.44f, cx - r * 0.84f, cy - r * 0.52f, cx - r * 0.98f, cy - r * 0.18f)
        close()
    }
    drawPath(bangs, Brush.verticalGradient(listOf(Color(0xFF3B2418), Color(0xFF5E3B27)), startY = cy - r, endY = cy - r * 0.4f))
    drawPath(Path().apply {
        moveTo(cx - r * 0.5f, cy - r * 0.86f); quadraticBezierTo(cx - r * 0.1f, cy - r * 0.98f, cx + r * 0.3f, cy - r * 0.88f)
    }, Color.White.copy(alpha = 0.18f), style = Stroke(width = r * 0.05f, cap = StrokeCap.Round))
    // A glowing star hair-clip.
    val clip = Offset(cx + r * 0.6f, cy - r * 0.72f)
    val glow = 0.6f + 0.4f * ((sin(t / 500.0) + 1) / 2).toFloat()
    drawCircle(Brush.radialGradient(listOf(Color(0xAAFFE066).copy(alpha = 0.6f * glow), Color.Transparent), center = clip, radius = r * 0.28f), radius = r * 0.28f, center = clip)
    drawPath(starPath(clip, r * 0.13f, r * 0.06f, -PI.toFloat() / 2), Color(0xFFFFD43B))

    // Cheeks: rosy blush, brighter when tickled.
    val blush = if (mood == AvatarMood.TICKLED) 0.75f else 0.42f
    for (side in listOf(-1f, 1f)) {
        val c = Offset(cx + side * r * AvatarMath.CHEEK_X, cy + r * AvatarMath.CHEEK_Y)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFF8A9B).copy(alpha = blush), Color.Transparent), center = c, radius = r * 0.24f), radius = r * 0.24f, center = c)
    }

    // Eyes.
    val open = AvatarMath.eyeOpen(t)
    for (side in listOf(-1f, 1f)) {
        val ec = Offset(cx + side * r * AvatarMath.EYE_X, cy + r * AvatarMath.EYE_Y)
        drawEye(ec, r, mood, open, touch, touchAge, t)
        // Eyebrows: raised while listening or thinking.
        val lift = when (mood) { AvatarMood.LISTENING -> 0.06f; AvatarMood.THINKING -> 0.1f; else -> 0f }
        drawPath(Path().apply {
            moveTo(ec.x - r * 0.15f, ec.y - r * (0.27f + lift)); quadraticBezierTo(ec.x, ec.y - r * (0.35f + lift), ec.x + r * 0.15f, ec.y - r * (0.27f + lift) + side * r * 0.015f)
        }, Color(0xFF4A2C1D), style = Stroke(width = r * 0.04f, cap = StrokeCap.Round))
    }

    // Nose (squishes on a boop).
    val nose = Offset(cx, cy + r * AvatarMath.NOSE_Y)
    val squish = if (booped) 0.7f else 1f
    scale(1f / squish, squish, pivot = nose) {
        drawOval(Color(0xFFE59A79), topLeft = Offset(nose.x - r * 0.07f, nose.y - r * 0.045f), size = Size(r * 0.14f, r * 0.1f))
        drawCircle(Color.White.copy(alpha = 0.45f), radius = r * 0.02f, center = Offset(nose.x - r * 0.025f, nose.y - r * 0.02f))
    }

    // Mouth.
    drawMouth(Offset(cx, cy + r * AvatarMath.MOUTH_Y), r, mood, mouthOpen)
}

private fun DrawScope.drawEye(ec: Offset, r: Float, mood: AvatarMood, open: Float, touch: Offset?, touchAge: Long, t: Long) {
    val w = r * 0.22f
    val h = r * 0.27f
    if (mood == AvatarMood.TICKLED) {
        // Happy ^ ^ arches.
        drawPath(Path().apply {
            moveTo(ec.x - w * 0.8f, ec.y + h * 0.15f); quadraticBezierTo(ec.x, ec.y - h * 0.75f, ec.x + w * 0.8f, ec.y + h * 0.15f)
        }, Color(0xFF3A2216), style = Stroke(width = r * 0.05f, cap = StrokeCap.Round))
        return
    }
    val eh = h * open.coerceAtLeast(0.06f)
    if (open < 0.12f) {
        // Closed for a blink: a soft lash line.
        drawPath(Path().apply {
            moveTo(ec.x - w * 0.85f, ec.y); quadraticBezierTo(ec.x, ec.y + h * 0.25f, ec.x + w * 0.85f, ec.y)
        }, Color(0xFF3A2216), style = Stroke(width = r * 0.035f, cap = StrokeCap.Round))
        return
    }
    // White, then a large glossy iris, the pupil, and two catchlights.
    drawOval(Color.White, topLeft = Offset(ec.x - w, ec.y - eh), size = Size(w * 2, eh * 2))
    val (px, py) = AvatarMath.pupilOffset(ec.x, ec.y, touch?.x, touch?.y, touchAge, mood, w * 0.32f)
    val ic = Offset(ec.x + px, ec.y + py * open)
    val ir = w * 0.78f
    drawCircle(Brush.radialGradient(listOf(Color(0xFF8D5A2B), Color(0xFF4A2A12), Color(0xFF2A160A)), center = Offset(ic.x, ic.y + ir * 0.2f), radius = ir), radius = ir, center = ic)
    drawCircle(Color(0xFF0E0704), radius = ir * 0.5f, center = ic)
    drawCircle(Color.White, radius = ir * 0.26f, center = Offset(ic.x - ir * 0.32f, ic.y - ir * 0.34f))
    drawCircle(Color.White.copy(alpha = 0.85f), radius = ir * 0.12f, center = Offset(ic.x + ir * 0.3f, ic.y + ir * 0.28f))
    // Upper lid line and lashes.
    drawArc(Color(0xFF3A2216), 200f, 140f, false, topLeft = Offset(ec.x - w, ec.y - eh), size = Size(w * 2, eh * 2), style = Stroke(width = r * 0.035f, cap = StrokeCap.Round))
}

private fun DrawScope.drawMouth(mc: Offset, r: Float, mood: AvatarMood, openness: Float) {
    val w = r * (if (mood == AvatarMood.TICKLED) 0.3f else 0.24f)
    if (openness < 0.05f) {
        // A sweet closed smile (a little wider while listening: curious and attentive).
        val curve = if (mood == AvatarMood.LISTENING) 0.1f else 0.14f
        drawPath(Path().apply {
            moveTo(mc.x - w, mc.y - r * 0.02f); quadraticBezierTo(mc.x, mc.y + r * curve, mc.x + w, mc.y - r * 0.02f)
        }, Color(0xFFB4485A), style = Stroke(width = r * 0.045f, cap = StrokeCap.Round))
        return
    }
    // Open mouth: smiling top lip, rounded bottom that follows the voice, a soft tongue.
    val h = r * (0.06f + 0.24f * openness)
    val path = Path().apply {
        moveTo(mc.x - w, mc.y - r * 0.03f)
        quadraticBezierTo(mc.x, mc.y + r * 0.03f, mc.x + w, mc.y - r * 0.03f)
        cubicTo(mc.x + w * 0.9f, mc.y + h, mc.x - w * 0.9f, mc.y + h, mc.x - w, mc.y - r * 0.03f)
        close()
    }
    drawPath(path, Color(0xFF7A1F33))
    drawOval(Color(0xFFFF8FA3), topLeft = Offset(mc.x - w * 0.5f, mc.y + h * 0.35f), size = Size(w, h * 0.55f))
    drawPath(path, Color(0xFFB4485A), style = Stroke(width = r * 0.025f))
}

// ------------------------------------------------------------------ moods around the face

/** Listening: glowing sound rings spreading from her ears. */
private fun DrawScope.drawListeningRings(cx: Float, cy: Float, r: Float, t: Long) {
    for (side in listOf(-1f, 1f)) {
        val ear = Offset(cx + side * r * 1.0f, cy + r * 0.05f)
        repeat(3) { i ->
            val k = ((t / 900.0 + i / 3.0) % 1.0).toFloat()
            drawArc(
                Color(0xFF8CE99A).copy(alpha = (1f - k) * 0.8f),
                startAngle = if (side < 0) 135f else -45f, sweepAngle = 90f, useCenter = false,
                topLeft = Offset(ear.x - r * (0.15f + k * 0.4f), ear.y - r * (0.15f + k * 0.4f)),
                size = Size(r * (0.3f + k * 0.8f), r * (0.3f + k * 0.8f)),
                style = Stroke(width = r * 0.035f, cap = StrokeCap.Round),
            )
        }
    }
    // A soft halo pulse.
    val p = ((sin(t / 300.0) + 1) / 2).toFloat()
    drawCircle(Color(0xFF8CE99A).copy(alpha = 0.10f + 0.12f * p), radius = r * (1.25f + 0.05f * p), center = Offset(cx, cy), style = Stroke(width = r * 0.04f))
}

/** Thinking: colourful sparkles circling above her head. */
private fun DrawScope.drawThinkingSparkles(cx: Float, cy: Float, r: Float, t: Long) {
    repeat(7) { i ->
        val a = t / 1400.0 + i * (2 * PI / 7)
        val x = cx + cos(a).toFloat() * r * 0.95f
        val y = cy - r * 1.25f + sin(a * 2).toFloat() * r * 0.12f
        val s = r * (0.05f + 0.025f * ((sin(t / 250.0 + i) + 1) / 2).toFloat())
        drawPath(starPath(Offset(x, y), s, s * 0.45f, (t / 600.0 + i).toFloat()), Color(AvatarMath.PALETTE[i % AvatarMath.PALETTE.size]))
    }
}

private fun DrawScope.drawBubbles(bubbles: List<Bubble>, t: Long) {
    bubbles.forEach { b ->
        val c = Offset(b.x + AvatarMath.swayOf(b, t), b.y)
        val col = Color(b.colorArgb)
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.15f), col.copy(alpha = 0.35f)), center = c, radius = b.r), radius = b.r, center = c)
        drawCircle(col.copy(alpha = 0.7f), radius = b.r, center = c, style = Stroke(width = b.r * 0.08f))
        drawCircle(Color.White.copy(alpha = 0.8f), radius = b.r * 0.18f, center = Offset(c.x - b.r * 0.35f, c.y - b.r * 0.35f))
    }
}

private fun DrawScope.drawParticles(particles: List<Particle>) {
    particles.forEach { p ->
        val a = p.life.coerceIn(0f, 1f)
        val col = Color(p.colorArgb).copy(alpha = a)
        when (p.kind) {
            Particle.Kind.HEART -> drawPath(heartPath(Offset(p.x, p.y), p.size), col)
            Particle.Kind.STAR -> drawPath(starPath(Offset(p.x, p.y), p.size, p.size * 0.45f, p.life * 3f), col)
            Particle.Kind.SPARK -> drawCircle(col, radius = p.size * 0.4f, center = Offset(p.x, p.y))
        }
    }
}

// ------------------------------------------------------------------ shapes

private fun starPath(c: Offset, outer: Float, inner: Float, rotation: Float): Path = Path().apply {
    for (i in 0 until 10) {
        val rad = if (i % 2 == 0) outer else inner
        val a = rotation + i * PI.toFloat() / 5f
        val x = c.x + cos(a) * rad
        val y = c.y + sin(a) * rad
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun heartPath(c: Offset, s: Float): Path = Path().apply {
    moveTo(c.x, c.y + s * 0.35f)
    cubicTo(c.x - s, c.y - s * 0.25f, c.x - s * 0.45f, c.y - s * 0.95f, c.x, c.y - s * 0.4f)
    cubicTo(c.x + s * 0.45f, c.y - s * 0.95f, c.x + s, c.y - s * 0.25f, c.x, c.y + s * 0.35f)
    close()
}
