package com.raju.aiassistant.ui.kids

import android.graphics.BlurMaskFilter
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
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.toSize
import com.raju.aiassistant.domain.kids.AvatarMath
import com.raju.aiassistant.domain.kids.AvatarMood
import com.raju.aiassistant.domain.kids.Bubble
import com.raju.aiassistant.domain.kids.FaceZone
import com.raju.aiassistant.domain.kids.Particle
import com.raju.aiassistant.domain.kids.Ripple
import com.raju.aiassistant.domain.kids.SpeechBubble
import com.raju.aiassistant.domain.kids.TapAudioGate
import com.raju.aiassistant.domain.kids.WinkSide
import com.raju.aiassistant.domain.model.AssistantState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val Cyan = Color(0xFF4DF0FF)
private val Gold = Color(0xFFFFD27D)

/**
 * Riya's friend: a 3D-shaded, Pixar-like face with a sci-fi holographic HUD, drawn entirely on a Compose
 * Canvas (no image files). Deep navy background, cyan and gold aura rings around the ears and cheeks, a
 * cyan-white rim light along the jaw and hairline, glossy grey-blue/hazel eyes that blink and follow her
 * finger, soft 3D lips that move with Seema's real voice level.
 *
 * Touch: a cheek winks that eye (the other stays wide open), ripples spread from her fingertip with cyan and
 * gold sparks, and a short Bengali laugh glows beside the cheek. The nose squishes ("boop"), glass bubbles
 * pop. Sounds are spaced by [TapAudioGate] and never play while Seema listens or speaks; the picture always
 * answers every tap. All motion comes from [AvatarMath].
 */
@Composable
fun RiyaAvatarFace(
    state: AssistantState,
    level: Float,
    onGiggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Riya is saying something right now (words are coming in): no sound over her. */
    capturing: Boolean = false,
) {
    val currentState by rememberUpdatedState(state)
    val currentLevel by rememberUpdatedState(level)
    val giggle by rememberUpdatedState(onGiggle)
    val currentCapturing by rememberUpdatedState(capturing)

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var mouth by remember { mutableFloatStateOf(0f) }
    var particles by remember { mutableStateOf(emptyList<Particle>()) }
    var bubbles by remember { mutableStateOf(emptyList<Bubble>()) }
    var ripples by remember { mutableStateOf(emptyList<Ripple>()) }
    var speech by remember { mutableStateOf<SpeechBubble?>(null) }
    var tickledUntil by remember { mutableLongStateOf(0L) }
    var wink by remember { mutableStateOf(WinkSide.NONE) }
    var boopUntil by remember { mutableLongStateOf(0L) }
    var touch by remember { mutableStateOf<Offset?>(null) }
    var touchAt by remember { mutableLongStateOf(0L) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var nextBubbleAt by remember { mutableLongStateOf(0L) }
    var seed by remember { mutableIntStateOf(1) }
    var bubbleId by remember { mutableLongStateOf(1L) }
    val gate = remember { TapAudioGate() }

    val tones = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 50) }.getOrNull() }
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
                if (ripples.isNotEmpty()) ripples = AvatarMath.liveRipples(ripples, t)
                if (speech?.let { t >= it.until } == true) speech = null
                val sz = canvasSize
                if (sz.width > 0f) {
                    bubbles = AvatarMath.stepBubbles(bubbles, dt, sz.height)
                    if (t >= nextBubbleAt && bubbles.size < AvatarMath.MAX_BUBBLES - 2) {
                        val (cx, cy, r) = faceGeometry(sz)
                        bubbles = bubbles + AvatarMath.newBubble(bubbleId++, sz.width, sz.height, cx, cy, r, seed++)
                        nextBubbleAt = t + 3_200L
                    }
                }
            }
        }
    }

    Canvas(
        modifier.onSizeChanged { canvasSize = it.toSize() }.pointerInput(Unit) {
            detectTapGestures { pos ->
                val t = System.currentTimeMillis()
                touch = pos
                touchAt = t
                ripples = ripples + Ripple(pos.x, pos.y, t)
                val k = min(size.width, size.height) / 1080f * 2f
                val busy = AvatarMath.audioBusy(currentState, currentCapturing)
                AvatarMath.bubbleAt(bubbles, pos.x, pos.y, t)?.let { b ->
                    bubbles = bubbles - b
                    particles = particles + AvatarMath.burst(pos.x, pos.y, Particle.Kind.SPARK, 10, seed++, k, AvatarMath.HOLO_PALETTE)
                    if (gate.sound(t, busy)) runCatching { tones?.startTone(ToneGenerator.TONE_PROP_BEEP, 50) }
                    return@detectTapGestures
                }
                val (cx, baseCy, r) = faceGeometry(Size(size.width.toFloat(), size.height.toFloat()))
                val cy = baseCy + AvatarMath.breath(t, r * 0.02f)
                when (val zone = AvatarMath.zone(pos.x, pos.y, cx, cy, r)) {
                    FaceZone.NOSE -> {
                        boopUntil = t + AvatarMath.BOOP_MS
                        particles = particles + AvatarMath.burst(pos.x, pos.y, Particle.Kind.STAR, 10, seed++, k, AvatarMath.HOLO_PALETTE)
                        if (gate.sound(t, busy)) runCatching { tones?.startTone(ToneGenerator.TONE_PROP_ACK, 90) }
                    }
                    FaceZone.CHEEK, FaceZone.FACE -> {
                        tickledUntil = t + AvatarMath.TICKLE_MS
                        wink = AvatarMath.winkSide(zone, pos.x, cx)
                        particles = particles + AvatarMath.burst(pos.x, pos.y, Particle.Kind.SPARK, 12, seed++, k, AvatarMath.HOLO_PALETTE)
                        val line = AvatarMath.GIGGLES[seed % AvatarMath.GIGGLES.size]
                        // The glowing line always shows; it is spoken only when the gate allows.
                        speech = SpeechBubble(line, if (wink == WinkSide.NONE) WinkSide.RIGHT else wink, t + AvatarMath.SPEECH_BUBBLE_MS)
                        if (gate.giggle(t, busy)) giggle(line)
                        else if (gate.sound(t, busy)) runCatching { tones?.startTone(ToneGenerator.TONE_PROP_PROMPT, 70) }
                    }
                    FaceZone.OUTSIDE -> particles = particles + AvatarMath.burst(pos.x, pos.y, Particle.Kind.SPARK, 6, seed++, k, AvatarMath.HOLO_PALETTE)
                }
            }
        },
    ) {
        val t = now
        val mood = AvatarMath.mood(state, t, tickledUntil)
        val (cx, baseCy, r) = faceGeometry(size)
        val cy = baseCy + AvatarMath.breath(t, r * 0.02f)
        drawBackdrop(t)
        drawAura(cx, cy, r, t, mood)
        drawBubbles(bubbles, t)
        drawHead(cx, cy, r, t, mood, wink, mouth, touch, t - touchAt, t < boopUntil)
        if (mood == AvatarMood.THINKING) drawThinking(cx, cy, r, t)
        drawRipples(ripples, t, r)
        drawParticles(particles)
        speech?.let { drawSpeech(it, cx, cy, r, t) }
    }
}

/** The face: centred a little above the middle, about 65 % of the width. */
private fun faceGeometry(size: Size): Triple<Float, Float, Float> {
    val r = min(size.width * 0.33f, size.height * 0.23f)
    return Triple(size.width / 2f, size.height * 0.42f, r)
}

// ------------------------------------------------------------------ soft glow helpers (BlurMaskFilter)

private fun DrawScope.glowCircle(center: Offset, radius: Float, color: Color, blur: Float) = drawIntoCanvas { c ->
    val p = Paint().asFrameworkPaint().apply {
        isAntiAlias = true
        this.color = color.toArgb()
        maskFilter = BlurMaskFilter(blur.coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
    }
    c.nativeCanvas.drawCircle(center.x, center.y, radius, p)
}

private fun DrawScope.glowStroke(path: Path, color: Color, width: Float, blur: Float) = drawIntoCanvas { c ->
    val p = Paint().asFrameworkPaint().apply {
        isAntiAlias = true
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = width
        strokeCap = android.graphics.Paint.Cap.ROUND
        this.color = color.toArgb()
        maskFilter = BlurMaskFilter(blur.coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
    }
    c.nativeCanvas.drawPath(path.asAndroidPath(), p)
}

// ------------------------------------------------------------------ background and aura

private fun DrawScope.drawBackdrop(t: Long) {
    drawRect(Brush.radialGradient(listOf(Color(0xFF122233), Color(0xFF0C1824), Color(0xFF081018)), center = Offset(size.width / 2, size.height * 0.42f), radius = size.maxDimension * 0.75f))
    // Faint HUD scan lines.
    val step = size.height / 60f
    var y = (t / 40 % step.toLong().coerceAtLeast(1)).toFloat()
    while (y < size.height) {
        drawLine(Cyan.copy(alpha = 0.035f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
    }
    // Floating light dust.
    val rnd = java.util.Random(11)
    repeat(28) { i ->
        val x = rnd.nextFloat() * size.width
        val yy = (rnd.nextFloat() * size.height - (t / 60f + i * 37) % size.height + size.height) % size.height
        val a = 0.15f + 0.25f * ((sin(t / 700.0 + i) + 1) / 2).toFloat()
        drawCircle((if (i % 3 == 0) Gold else Cyan).copy(alpha = a), radius = 1.5f + rnd.nextFloat() * 2f, center = Offset(x, yy))
    }
}

/** Concentric cyan and gold energy rings framing the ears and cheeks; brighter and pulsing while listening. */
private fun DrawScope.drawAura(cx: Float, cy: Float, r: Float, t: Long, mood: AvatarMood) {
    val listening = mood == AvatarMood.LISTENING
    val pulse = ((sin(t / (if (listening) 260.0 else 900.0)) + 1) / 2).toFloat()
    glowCircle(Offset(cx, cy), r * 1.32f, Cyan.copy(alpha = if (listening) 0.16f + 0.1f * pulse else 0.08f), r * 0.35f)
    val spin = (t / 60.0 % 360).toFloat()
    listOf(1.18f to Cyan, 1.27f to Gold, 1.36f to Cyan).forEachIndexed { i, (k, col) ->
        val rr = r * k
        val alpha = (if (listening) 0.75f else 0.45f) - i * 0.1f
        for (side in listOf(0f, 180f)) {
            val start = side - 50f + (if (i % 2 == 0) spin * 0.15f else -spin * 0.1f) % 20f
            drawArc(col.copy(alpha = alpha * 0.35f), start, 100f, false, Offset(cx - rr, cy - rr), Size(rr * 2, rr * 2), style = Stroke(width = r * 0.05f, cap = StrokeCap.Round))
            drawArc(col.copy(alpha = alpha), start, 100f, false, Offset(cx - rr, cy - rr), Size(rr * 2, rr * 2), style = Stroke(width = r * 0.012f, cap = StrokeCap.Round))
        }
    }
    if (listening) {
        // Sound waves at both ears.
        for (side in listOf(-1f, 1f)) {
            val ear = Offset(cx + side * r * 0.95f, cy + r * 0.05f)
            repeat(3) { i ->
                val k = ((t / 800.0 + i / 3.0) % 1.0).toFloat()
                val rr = r * (0.12f + k * 0.45f)
                drawArc(Cyan.copy(alpha = (1f - k) * 0.9f), if (side < 0) 140f else -40f, 80f, false, Offset(ear.x - rr, ear.y - rr), Size(rr * 2, rr * 2), style = Stroke(width = r * 0.022f, cap = StrokeCap.Round))
            }
        }
    }
}

// ------------------------------------------------------------------ the 3D head

private fun facePath(cx: Float, cy: Float, r: Float): Path = Path().apply {
    // A soft oval with a gentle chin: wider at the cheeks, rounder at the forehead.
    moveTo(cx, cy - r * 1.02f)
    cubicTo(cx + r * 0.62f, cy - r * 1.02f, cx + r * 0.92f, cy - r * 0.55f, cx + r * 0.9f, cy - r * 0.02f)
    cubicTo(cx + r * 0.88f, cy + r * 0.55f, cx + r * 0.5f, cy + r * 0.98f, cx, cy + r * 1.02f)
    cubicTo(cx - r * 0.5f, cy + r * 0.98f, cx - r * 0.88f, cy + r * 0.55f, cx - r * 0.9f, cy - r * 0.02f)
    cubicTo(cx - r * 0.92f, cy - r * 0.55f, cx - r * 0.62f, cy - r * 1.02f, cx, cy - r * 1.02f)
    close()
}

private fun DrawScope.drawHead(
    cx: Float, cy: Float, r: Float, t: Long, mood: AvatarMood, wink: WinkSide, mouthOpen: Float,
    touch: Offset?, touchAge: Long, booped: Boolean,
) {
    val hairDark = Color(0xFF241510)
    val hairMid = Color(0xFF4A2C1E)
    // Hair volume behind the head.
    drawOval(Brush.radialGradient(listOf(hairMid, hairDark), center = Offset(cx - r * 0.2f, cy - r * 0.5f), radius = r * 1.4f), topLeft = Offset(cx - r * 1.08f, cy - r * 1.22f), size = Size(r * 2.16f, r * 2.05f))
    // Ears.
    for (side in listOf(-1f, 1f)) {
        val ec = Offset(cx + side * r * 0.9f, cy + r * 0.05f)
        drawOval(Brush.radialGradient(listOf(Color(0xFFF2B896), Color(0xFFC98462)), center = ec, radius = r * 0.16f), topLeft = Offset(ec.x - r * 0.1f, ec.y - r * 0.16f), size = Size(r * 0.2f, r * 0.32f))
    }
    val face = facePath(cx, cy, r)
    // Skin: warm base with depth (light from the upper left).
    drawPath(face, Brush.radialGradient(listOf(Color(0xFFFCDCC4), Color(0xFFF2BC98), Color(0xFFD9946F), Color(0xFFB8735A)), center = Offset(cx - r * 0.18f, cy - r * 0.28f), radius = r * 1.45f))
    clipPath(face) {
        // Side shadows and the soft shade under the hairline give the head its roundness.
        drawRect(Brush.horizontalGradient(listOf(Color(0x55704030), Color.Transparent, Color.Transparent, Color(0x66704030)), startX = cx - r, endX = cx + r))
        drawRect(Brush.verticalGradient(listOf(Color(0x44603020), Color.Transparent), startY = cy - r, endY = cy - r * 0.45f))
        // Contoured cheeks: warm and round.
        for (side in listOf(-1f, 1f)) {
            val c = Offset(cx + side * r * AvatarMath.CHEEK_X, cy + r * AvatarMath.CHEEK_Y)
            val blush = if (mood == AvatarMood.TICKLED && (wink == WinkSide.NONE || (wink == WinkSide.LEFT) == (side < 0))) 0.7f else 0.38f
            glowCircle(c, r * 0.2f, Color(0xFFFF8A9B).copy(alpha = blush), r * 0.14f)
            glowCircle(Offset(c.x - side * r * 0.04f, c.y - r * 0.08f), r * 0.07f, Color.White.copy(alpha = 0.18f), r * 0.06f)
        }
        // Forehead and nose-bridge highlights, chin light.
        glowCircle(Offset(cx - r * 0.12f, cy - r * 0.62f), r * 0.22f, Color.White.copy(alpha = 0.22f), r * 0.18f)
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent), startY = cy - r * 0.15f, endY = cy + r * 0.3f),
            topLeft = Offset(cx - r * 0.035f, cy - r * 0.15f), size = Size(r * 0.07f, r * 0.45f),
        )
        glowCircle(Offset(cx, cy + r * 0.86f), r * 0.1f, Color.White.copy(alpha = 0.14f), r * 0.08f)
    }
    // Hairline: a smooth sculpted fringe swept to one side.
    val fringe = Path().apply {
        moveTo(cx - r * 0.95f, cy - r * 0.15f)
        cubicTo(cx - r * 1.0f, cy - r * 1.1f, cx + r * 0.8f, cy - r * 1.25f, cx + r * 0.95f, cy - r * 0.2f)
        cubicTo(cx + r * 0.75f, cy - r * 0.62f, cx + r * 0.3f, cy - r * 0.78f, cx - r * 0.15f, cy - r * 0.66f)
        cubicTo(cx - r * 0.5f, cy - r * 0.6f, cx - r * 0.8f, cy - r * 0.45f, cx - r * 0.95f, cy - r * 0.15f)
        close()
    }
    drawPath(fringe, Brush.linearGradient(listOf(hairMid, hairDark), start = Offset(cx - r, cy - r), end = Offset(cx + r * 0.5f, cy - r * 0.3f)))
    drawPath(Path().apply { moveTo(cx - r * 0.55f, cy - r * 0.88f); quadraticBezierTo(cx, cy - r * 1.02f, cx + r * 0.5f, cy - r * 0.8f) }, Color(0x55FFE2C8), style = Stroke(width = r * 0.035f, cap = StrokeCap.Round))
    // Holographic rim light along forehead, hairline and jaw.
    glowStroke(face, Cyan.copy(alpha = 0.55f), r * 0.03f, r * 0.05f)
    drawPath(face, Color(0xCCE6FDFF).copy(alpha = 0.35f), style = Stroke(width = r * 0.008f))
    // Forehead emblem: a small glowing diamond.
    val em = Offset(cx + r * 0.05f, cy - r * 0.5f)
    val ep = ((sin(t / 600.0) + 1) / 2).toFloat()
    glowCircle(em, r * 0.07f, Cyan.copy(alpha = 0.25f + 0.25f * ep), r * 0.06f)
    drawPath(Path().apply { moveTo(em.x, em.y - r * 0.045f); lineTo(em.x + r * 0.03f, em.y); lineTo(em.x, em.y + r * 0.045f); lineTo(em.x - r * 0.03f, em.y); close() }, Color(0xFFE6FDFF).copy(alpha = 0.7f + 0.3f * ep))

    // Eyes and brows.
    val open = AvatarMath.eyeOpen(t)
    for (side in listOf(-1f, 1f)) {
        val ec = Offset(cx + side * r * AvatarMath.EYE_X, cy + r * AvatarMath.EYE_Y)
        val squint = AvatarMath.squint(mood, wink, leftEye = side < 0)
        drawEye(ec, r, mood, open, squint, touch, touchAge)
        val lift = when (mood) { AvatarMood.LISTENING -> 0.05f; AvatarMood.THINKING -> 0.09f; else -> 0f } - squint * 0.04f
        drawPath(Path().apply {
            moveTo(ec.x - side * r * 0.16f, ec.y - r * (0.25f + lift)); quadraticBezierTo(ec.x, ec.y - r * (0.33f + lift), ec.x + side * r * 0.17f, ec.y - r * (0.27f + lift))
        }, Color(0xFF4A2C1E), style = Stroke(width = r * 0.035f, cap = StrokeCap.Round))
    }

    // Nose: soft 3D shading, squishes on a boop.
    val nose = Offset(cx, cy + r * AvatarMath.NOSE_Y)
    val squish = if (booped) 0.72f else 1f
    scale(1f / squish, squish, pivot = nose) {
        glowCircle(Offset(nose.x, nose.y + r * 0.03f), r * 0.07f, Color(0x55A0583E), r * 0.05f)
        for (side in listOf(-1f, 1f)) drawOval(Color(0x88A0583E), topLeft = Offset(nose.x + side * r * 0.04f - r * 0.022f, nose.y + r * 0.02f), size = Size(r * 0.044f, r * 0.026f))
        glowCircle(Offset(nose.x - r * 0.015f, nose.y - r * 0.02f), r * 0.03f, Color.White.copy(alpha = 0.45f), r * 0.02f)
    }

    drawLips(Offset(cx, cy + r * AvatarMath.MOUTH_Y), r, mood, mouthOpen)
}

private fun DrawScope.drawEye(ec: Offset, r: Float, mood: AvatarMood, open: Float, squint: Float, touch: Offset?, touchAge: Long) {
    val w = r * 0.21f
    val h = r * 0.2f
    if (squint > 0.5f) {
        // A happy squint: a curved ^ lash arch with a few lashes.
        val arch = Path().apply { moveTo(ec.x - w, ec.y + h * 0.2f); quadraticBezierTo(ec.x, ec.y - h * 0.9f, ec.x + w, ec.y + h * 0.2f) }
        drawPath(arch, Color(0xFF2A1810), style = Stroke(width = r * 0.04f, cap = StrokeCap.Round))
        glowCircle(Offset(ec.x, ec.y + h * 0.4f), r * 0.1f, Color(0xFFFF8A9B).copy(alpha = 0.25f), r * 0.08f)
        return
    }
    val eh = h * open.coerceAtLeast(0.05f)
    if (open < 0.12f) {
        drawPath(Path().apply { moveTo(ec.x - w, ec.y); quadraticBezierTo(ec.x, ec.y + h * 0.35f, ec.x + w, ec.y) }, Color(0xFF2A1810), style = Stroke(width = r * 0.03f, cap = StrokeCap.Round))
        return
    }
    // Almond-shaped eye.
    val shape = Path().apply {
        moveTo(ec.x - w, ec.y)
        cubicTo(ec.x - w * 0.6f, ec.y - eh * 1.25f, ec.x + w * 0.6f, ec.y - eh * 1.25f, ec.x + w, ec.y)
        cubicTo(ec.x + w * 0.6f, ec.y + eh * 1.05f, ec.x - w * 0.6f, ec.y + eh * 1.05f, ec.x - w, ec.y)
        close()
    }
    drawPath(shape, Brush.verticalGradient(listOf(Color(0xFFDCE3EA), Color.White, Color(0xFFF2F5F8)), startY = ec.y - eh, endY = ec.y + eh))
    clipPath(shape) {
        val (px, py) = AvatarMath.pupilOffset(ec.x, ec.y, touch?.x, touch?.y, touchAge, mood, w * 0.3f)
        val ic = Offset(ec.x + px, ec.y + py * open)
        val ir = w * 0.62f
        // Iris: hazel centre to grey-blue, a dark limbal ring, fine texture spokes.
        drawCircle(Brush.radialGradient(listOf(Color(0xFFC79A55), Color(0xFF8A7A5C), Color(0xFF5C7E98), Color(0xFF2B3E4E)), center = ic, radius = ir), radius = ir, center = ic)
        repeat(18) { i ->
            val a = i * (2 * PI / 18)
            drawLine(Color(0x33FFFFFF), Offset(ic.x + cos(a).toFloat() * ir * 0.35f, ic.y + sin(a).toFloat() * ir * 0.35f), Offset(ic.x + cos(a).toFloat() * ir * 0.88f, ic.y + sin(a).toFloat() * ir * 0.88f), strokeWidth = r * 0.005f)
        }
        drawCircle(Color(0xFF1B2833), radius = ir, center = ic, style = Stroke(width = ir * 0.12f))
        drawCircle(Color(0xFF07090B), radius = ir * 0.42f, center = ic)
        // Lid shadow over the top of the eye, then two crisp catchlights.
        drawRect(Brush.verticalGradient(listOf(Color(0x66402A20), Color.Transparent), startY = ec.y - eh * 1.2f, endY = ec.y - eh * 0.2f), topLeft = Offset(ec.x - w, ec.y - eh * 1.3f), size = Size(w * 2, eh * 1.2f))
        drawCircle(Color.White, radius = ir * 0.24f, center = Offset(ic.x - ir * 0.33f, ic.y - ir * 0.35f))
        drawCircle(Color.White.copy(alpha = 0.85f), radius = ir * 0.1f, center = Offset(ic.x + ir * 0.32f, ic.y + ir * 0.25f))
    }
    // Upper lid line with lashes.
    val lid = Path().apply { moveTo(ec.x - w * 1.05f, ec.y + eh * 0.05f); cubicTo(ec.x - w * 0.6f, ec.y - eh * 1.3f, ec.x + w * 0.6f, ec.y - eh * 1.3f, ec.x + w * 1.08f, ec.y - eh * 0.05f) }
    drawPath(lid, Color(0xFF2A1810), style = Stroke(width = r * 0.03f, cap = StrokeCap.Round))
    drawLine(Color(0xFF2A1810), Offset(ec.x + w * 1.0f, ec.y - eh * 0.25f), Offset(ec.x + w * 1.22f, ec.y - eh * 0.6f), strokeWidth = r * 0.02f, cap = StrokeCap.Round)
}

/** Soft 3D lips: a closed warm smile, or an open mouth whose size follows the voice. */
private fun DrawScope.drawLips(mc: Offset, r: Float, mood: AvatarMood, openness: Float) {
    val w = r * (if (mood == AvatarMood.TICKLED) 0.27f else 0.22f)
    val lipTop = Color(0xFFD9707A)
    val lipBottom = Color(0xFFE88A90)
    if (openness < 0.05f) {
        val curve = if (mood == AvatarMood.LISTENING) 0.07f else 0.1f
        val upper = Path().apply {
            moveTo(mc.x - w, mc.y - r * 0.01f)
            quadraticBezierTo(mc.x - w * 0.5f, mc.y - r * 0.05f, mc.x, mc.y - r * 0.02f)
            quadraticBezierTo(mc.x + w * 0.5f, mc.y - r * 0.05f, mc.x + w, mc.y - r * 0.01f)
            quadraticBezierTo(mc.x, mc.y + r * curve * 0.6f, mc.x - w, mc.y - r * 0.01f)
            close()
        }
        val lower = Path().apply {
            moveTo(mc.x - w * 0.92f, mc.y)
            quadraticBezierTo(mc.x, mc.y + r * curve * 0.6f, mc.x + w * 0.92f, mc.y)
            quadraticBezierTo(mc.x, mc.y + r * (curve + 0.07f), mc.x - w * 0.92f, mc.y)
            close()
        }
        drawPath(lower, Brush.verticalGradient(listOf(lipBottom, Color(0xFFC75F6B)), startY = mc.y, endY = mc.y + r * 0.15f))
        drawPath(upper, Brush.verticalGradient(listOf(lipTop, Color(0xFFB8525E)), startY = mc.y - r * 0.05f, endY = mc.y + r * 0.04f))
        glowCircle(Offset(mc.x, mc.y + r * (curve + 0.02f)), r * 0.035f, Color.White.copy(alpha = 0.35f), r * 0.025f)
        // Smile corners.
        for (side in listOf(-1f, 1f)) glowCircle(Offset(mc.x + side * w * 1.02f, mc.y - r * 0.02f), r * 0.02f, Color(0x55803A3A), r * 0.02f)
        return
    }
    val h = r * (0.05f + 0.22f * openness)
    val mouth = Path().apply {
        moveTo(mc.x - w, mc.y - r * 0.02f)
        quadraticBezierTo(mc.x, mc.y + r * 0.02f, mc.x + w, mc.y - r * 0.02f)
        cubicTo(mc.x + w * 0.85f, mc.y + h, mc.x - w * 0.85f, mc.y + h, mc.x - w, mc.y - r * 0.02f)
        close()
    }
    drawPath(mouth, Brush.verticalGradient(listOf(Color(0xFF3A0F18), Color(0xFF6A1C2A)), startY = mc.y, endY = mc.y + h))
    clipPath(mouth) {
        drawRect(Color(0xFFF7F3EE), topLeft = Offset(mc.x - w * 0.6f, mc.y - r * 0.02f), size = Size(w * 1.2f, h * 0.18f))
        drawOval(Brush.radialGradient(listOf(Color(0xFFFF9AAA), Color(0xFFD86478)), center = Offset(mc.x, mc.y + h * 0.75f), radius = w * 0.6f), topLeft = Offset(mc.x - w * 0.55f, mc.y + h * 0.4f), size = Size(w * 1.1f, h * 0.7f))
    }
    drawPath(mouth, Brush.verticalGradient(listOf(lipTop, lipBottom), startY = mc.y - r * 0.03f, endY = mc.y + h), style = Stroke(width = r * 0.035f, cap = StrokeCap.Round))
    glowCircle(Offset(mc.x, mc.y + h + r * 0.01f), r * 0.03f, Color.White.copy(alpha = 0.3f), r * 0.02f)
}

// ------------------------------------------------------------------ around the face

private fun DrawScope.drawThinking(cx: Float, cy: Float, r: Float, t: Long) {
    repeat(6) { i ->
        val a = t / 1300.0 + i * (2 * PI / 6)
        val p = Offset(cx + cos(a).toFloat() * r * 0.9f, cy - r * 1.28f + sin(a * 2).toFloat() * r * 0.1f)
        val col = if (i % 2 == 0) Gold else Cyan
        glowCircle(p, r * 0.035f, col.copy(alpha = 0.8f), r * 0.03f)
        drawCircle(Color.White.copy(alpha = 0.9f), radius = r * 0.012f, center = p)
    }
}

private fun DrawScope.drawRipples(ripples: List<Ripple>, t: Long, r: Float) {
    ripples.forEach { rp ->
        val (k, a) = AvatarMath.ripple(rp, t) ?: return@forEach
        repeat(3) { i ->
            val kk = (k - i * 0.18f).coerceAtLeast(0f)
            if (kk <= 0f) return@repeat
            drawCircle(Color(0xFFE6FDFF).copy(alpha = a * 0.6f / (i + 1)), radius = r * 0.45f * kk, center = Offset(rp.x, rp.y), style = Stroke(width = r * 0.012f))
        }
        glowCircle(Offset(rp.x, rp.y), r * 0.06f * (1f - k), Cyan.copy(alpha = a * 0.5f), r * 0.05f)
    }
}

private fun DrawScope.drawBubbles(bubbles: List<Bubble>, t: Long) {
    bubbles.forEach { b ->
        val c = Offset(b.x + AvatarMath.swayOf(b, t), b.y)
        drawCircle(Brush.radialGradient(listOf(Color(0x1A4DF0FF), Color(0x334DF0FF)), center = c, radius = b.r), radius = b.r, center = c)
        drawCircle(Cyan.copy(alpha = 0.6f), radius = b.r, center = c, style = Stroke(width = b.r * 0.06f))
        drawCircle(Color.White.copy(alpha = 0.75f), radius = b.r * 0.16f, center = Offset(c.x - b.r * 0.35f, c.y - b.r * 0.35f))
    }
}

private fun DrawScope.drawParticles(particles: List<Particle>) {
    particles.forEach { p ->
        val a = p.life.coerceIn(0f, 1f)
        val col = Color(p.colorArgb)
        glowCircle(Offset(p.x, p.y), p.size * 0.45f, col.copy(alpha = a * 0.6f), p.size * 0.4f)
        drawCircle(Color.White.copy(alpha = a * 0.9f), radius = p.size * 0.15f, center = Offset(p.x, p.y))
    }
}

/** The spoken line glowing beside the cheek, with a delicate gold underline arc. */
private fun DrawScope.drawSpeech(s: SpeechBubble, cx: Float, cy: Float, r: Float, t: Long) {
    val left = s.side == WinkSide.LEFT
    val fade = ((s.until - t) / 400f).coerceIn(0f, 1f)
    val textSize = r * 0.13f
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = Color.White.copy(alpha = fade).toArgb()
        this.textSize = textSize
        textAlign = if (left) android.graphics.Paint.Align.LEFT else android.graphics.Paint.Align.RIGHT
        setShadowLayer(textSize * 0.5f, 0f, 0f, Cyan.copy(alpha = 0.9f * fade).toArgb())
        isFakeBoldText = true
    }
    val x = if (left) 16f else size.width - 16f
    val y = cy + r * 1.22f
    drawIntoCanvas { it.nativeCanvas.drawText(s.text, x, y, paint) }
    val width = paint.measureText(s.text).coerceAtMost(size.width - 32f)
    val x0 = if (left) x else x - width
    drawPath(
        Path().apply { moveTo(x0, y + textSize * 0.35f); quadraticBezierTo(x0 + width / 2, y + textSize * 0.75f, x0 + width, y + textSize * 0.35f) },
        Gold.copy(alpha = 0.85f * fade), style = Stroke(width = textSize * 0.07f, cap = StrokeCap.Round),
    )
}
