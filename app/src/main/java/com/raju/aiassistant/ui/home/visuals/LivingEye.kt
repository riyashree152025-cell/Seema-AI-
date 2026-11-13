package com.raju.aiassistant.ui.home.visuals

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.raju.aiassistant.domain.model.AssistantState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val PUPIL_CORE = Color(0xFF010409)
private val PUPIL_SHADOW = Color(0xFF2A1607)
private val CYAN = Color(0xFF00E5FF)

/** The pupil of the eye texture, as a share of its radius (measured on the reference render). */
private const val TEXTURE_PUPIL_SHARE = 0.225f

/**
 * Home's visualizer, one canvas: ambient fluid waves (cyan → neon blue → purple) across the whole
 * area, edge to edge, and a living biometric eye in the middle. The eye breathes, its pupil makes
 * small adjustments; while she listens, thinks or speaks sharp spikes turn around the iris (and a
 * radar sweeps inside it while she thinks). The waves swell with her voice while she speaks and
 * with the microphone while she listens, and keep a gentle motion otherwise. Decorative (hidden
 * from screen readers); [time], [speechLevel] and [micLevel] are read only while drawing.
 */
@Composable
fun LivingEyeVisualizer(
    time: () -> Float,
    speechLevel: () -> Float,
    micLevel: () -> Float,
    state: AssistantState,
    modifier: Modifier = Modifier,
    /** False in battery saver or on a hot phone: fewer wave layers, points and iris lines. */
    richEffects: Boolean = true,
    accentArgb: Long = eyeAccentArgb(state),
    /** Home draws the waves once, full screen behind everything, and the eye on its own layer. */
    drawWaves: Boolean = true,
    drawEye: Boolean = true,
    /** Where the waves run, as a share of the height (the eye's height on Home). */
    waveCenterY: Float = 0.5f,
    /**
     * Where the eye sits on this canvas (Home: the area above the buttons, measured by layout), or
     * null for the canvas centre. Read while drawing, so a layout change needs no recomposition.
     */
    eyeArea: () -> Rect? = { null },
    /** The last touch on the eye (canvas position, and [time] when it happened): the eye looks there. */
    lastTouch: () -> Pair<Offset, Float>? = { null },
) {
    val accent by animateColorAsState(Color(accentArgb), tween(450), label = "eyeAccent")
    // The eye itself: the photo-real iris from the approved reference render (hazel collar, electric
    // blue fibres, wet corneal highlights), brought to life below with gaze, parallax and dilation.
    val eyeTexture = ImageBitmap.imageResource(com.raju.aiassistant.R.drawable.seema_eye)
    // Energy carried between frames so the waves swell and fade instead of jumping.
    val energy = remember { floatArrayOf(AMBIENT_ENERGY) }
    Canvas(modifier.clearAndSetSemantics {}) {
        val t = time()
        val target = waveEnergy(state, speechLevel(), micLevel())
        energy[0] = smoothEnergy(energy[0], target)
        val e = energy[0]
        // One pass: the atmosphere and waves first, then the iris and pupil over them.
        val area = eyeArea()
        val centerY = area?.let { (it.center.y / size.height).coerceIn(0.1f, 0.9f) } ?: waveCenterY
        if (drawWaves) drawAmbientWaves(t, e, accent, richEffects, centerY)
        if (drawEye) drawLivingEye(t, e, state, accent, area, lastTouch(), eyeTexture, richEffects)
    }
}

private fun DrawScope.drawAmbientWaves(t: Float, energy: Float, accent: Color, richEffects: Boolean, centerY: Float) {
    val w = size.width
    val h = size.height
    if (!(w > 1f && h > 1f)) return // nothing to draw on (and no zero-radius glow)
    val layers = waveLayerCount(richEffects)
    val points = wavePointCount(richEffects)
    val gradient = WAVE_GRADIENT_ARGB.map { Color(it) }
    val midY = h * centerY.coerceIn(0.1f, 0.9f)
    for (layer in layers - 1 downTo 0) {
        // The layers spread over the whole height (no band), so the colour fills the screen top to bottom.
        val amp = h * (0.035f + 0.11f * energy) * (1f - layer * 0.12f)
        val offsetY = (layer - (layers - 1) / 2f) * h * (0.6f / layers)
        val top = Path()
        val bottom = Path()
        for (i in 0..points) {
            val xf = i / points.toFloat()
            val y = waveShape(xf, t, layer, energy) * amp
            val x = xf * w
            if (i == 0) {
                top.moveTo(x, midY + offsetY - y)
                bottom.moveTo(x, midY + offsetY + y * 0.6f)
            } else {
                top.lineTo(x, midY + offsetY - y)
                bottom.lineTo(x, midY + offsetY + y * 0.6f)
            }
        }
        val alpha = (0.85f - layer * 0.17f).coerceAtLeast(0.2f)
        val brush = Brush.horizontalGradient(gradient.map { it.copy(alpha = alpha) }, startX = 0f, endX = w)
        if (layer == 0) {
            // The fluid body of the front wave: the band between its two edges, softly filled.
            val band = Path().apply {
                addPath(top)
                for (i in points downTo 0) {
                    val xf = i / points.toFloat()
                    lineTo(xf * w, midY + offsetY + waveShape(xf, t, layer, energy) * amp * 0.6f)
                }
                close()
            }
            drawPath(band, Brush.horizontalGradient(gradient.map { it.copy(alpha = 0.10f + 0.12f * energy) }, startX = 0f, endX = w))
        }
        val width = (2.2f - layer * 0.35f) * density
        drawPath(top, brush, style = Stroke(width, cap = StrokeCap.Round))
        drawPath(bottom, brush, style = Stroke(width * 0.7f, cap = StrokeCap.Round), alpha = 0.55f)
    }
    // Atmosphere over the whole canvas: a cyan → blue → violet wash top to bottom and a glow around
    // the waves' centre, so no part of the screen is a dead black patch.
    drawRect(
        Brush.verticalGradient(
            listOf(
                gradient[0].copy(alpha = 0.07f + 0.05f * energy),
                accent.copy(alpha = 0.06f + 0.10f * energy),
                gradient[2].copy(alpha = 0.08f + 0.05f * energy),
            ),
        ),
    )
    val glowCenter = Offset(w / 2f, midY)
    val glowR = maxOf(w, h) * 0.75f
    drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.10f + 0.12f * energy), Color.Transparent), glowCenter, glowR), glowR, glowCenter)
}

private fun DrawScope.drawLivingEye(
    t: Float, energy: Float, state: AssistantState, accent: Color, area: Rect?, touch: Pair<Offset, Float>?,
    texture: androidx.compose.ui.graphics.ImageBitmap,
    richEffects: Boolean,
) {
    val c = area?.center ?: center
    val asleep = state == AssistantState.Sleeping || state == AssistantState.Offline
    val side = area?.let { minOf(it.width, it.height) } ?: minOf(size.width, size.height)
    val r = side * EYE_RADIUS_SHARE * eyeBreath(t) * (1f + 0.05f * energy)
    // No room for the eye (a very small screen, split screen, or the first frame before the layout is
    // measured): draw nothing. A zero radius made Android throw ("ending radius must be > 0") and the
    // app closed on launch — seen on the CI emulator.
    if (!(r > 1f)) return
    val dim = if (asleep) 0.55f else 1f

    // Glow around the eye in the state colour.
    drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.40f * dim), Color.Transparent), c, r * 1.9f), r * 1.9f, c)

    // Gaze: own drift + micro-saccades, or towards a touch for a moment. The whole eye moves with
    // 0.65 of it; the pupil moves with all of it (parallax: the pupil sits deeper than the iris front).
    val (ix, iy) = eyeGaze(t, state)
    val (tx, ty, tw) = touch?.let { (p, at) -> touchGaze((p.x - c.x) / r, (p.y - c.y) / r, t - at) } ?: Triple(0f, 0f, 0f)
    val gx = ix * (1f - tw) + tx * tw
    val gy = iy * (1f - tw) + ty * tw
    val eyeAt = Offset(c.x + gx * r * OUTER_PARALLAX, c.y + gy * r * OUTER_PARALLAX)
    val pupilAt = Offset(c.x + gx * r * INNER_PARALLAX, c.y + gy * r * INNER_PARALLAX)

    // The user's design: an extremely wide almond, always fully open, filled to both corners with the iris.
    val eyeW = minOf(r * 3.3f, (area?.width ?: size.width) * 0.98f)
    val eyeH = r * 1.78f
    val lids = almondPath(c.x - eyeW / 2f, c.y - eyeH / 2f, eyeW, eyeH)
    clipPath(lids) {
    // One iris image as wide as the almond (plus a margin for the gaze): its own fibres fill the corners,
    // no white, no seam. The almond crops its top and bottom; the pupil stays round in the middle.
    val ri = eyeW * 0.56f
    val d = (2f * ri).toInt().coerceAtLeast(1)
    drawImage(
        texture,
        srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
        srcSize = androidx.compose.ui.unit.IntSize(texture.width, texture.height),
        dstOffset = androidx.compose.ui.unit.IntOffset((eyeAt.x - ri).toInt(), (eyeAt.y - ri).toInt()),
        dstSize = androidx.compose.ui.unit.IntSize(d, d),
        alpha = dim,
        filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
    )

    // Life in the outer blue fibres only (the pupil, the hazel collar and the glass highlight stay as drawn).
    drawFiberLife(t, energy, eyeAt, ri, dim, texture, richEffects)

    // Pupil: the texture's own pupil is its resting size; when she listens or speaks it dilates with the
    // voice — a feathered obsidian ring grows over the hazel collar, with a dark-brown shadow at its
    // edge so it recedes into the eye. It never shrinks below the drawn pupil.
    val grow = (pupilScale(t, energy, state) / PUPIL_SHARE).coerceAtLeast(1f)
    if (grow > 1.005f) {
        val pr = ri * TEXTURE_PUPIL_SHARE * grow
        val feather = 3f * density
        drawCircle(
            Brush.radialGradient(
                0f to PUPIL_CORE, (pr / (pr + feather * 2)) to PUPIL_CORE, ((pr + feather) / (pr + feather * 2)) to PUPIL_SHADOW.copy(alpha = 0.7f), 1f to Color.Transparent,
                center = pupilAt, radius = pr + feather * 2,
            ),
            pr + feather * 2, pupilAt,
        )
    }

    // While she processes, a soft light circles over the blue iris.
    if (eyeRadarActive(state)) {
        val a = t * 3.8f
        val glow = Offset(eyeAt.x + cos(a) * ri * 0.5f, eyeAt.y + sin(a) * eyeH * 0.3f)
        drawCircle(Brush.radialGradient(listOf(CYAN.copy(alpha = 0.35f), Color.Transparent), glow, ri * 0.2f), ri * 0.2f, glow)
    }
    }
    // No lids, no dark line: only a faint glow of the state colour along the edge.
    drawPath(lids, accent.copy(alpha = 0.30f * dim), style = Stroke(width = 3f * density))
}


/** The user's human-eye shape: corners at 52 % height, lids as two cubic curves (a little flat). */
private fun almondPath(left: Float, top: Float, w: Float, h: Float): Path = Path().apply {
    moveTo(left, top + h * 0.52f)
    cubicTo(left + w * 0.25f, top - h * 0.12f, left + w * 0.75f, top - h * 0.12f, left + w, top + h * 0.52f)
    cubicTo(left + w * 0.75f, top + h * 1.12f, left + w * 0.25f, top + h * 1.12f, left, top + h * 0.52f)
    close()
}

private val FIBER_CYAN = Color(0xFF4FF3FF)
private val FIBER_BLUE = Color(0xFF3D8BFF)
private val FIBER_VIOLET = Color(0xFFA57CFF)
private val LIMBAL = Color(0xFF01040F)

/** Where the blue fibres begin (outside the hazel collar) and end, as shares of the eye's radius. */
private const val FIBER_INNER = 0.50f
private const val FIBER_OUTER = 0.985f

/** A fixed pseudo-random number 0..1 for fibre [i] (the same every frame, so fibres do not jump). */
private fun fiberSeed(i: Int, k: Float): Float {
    val x = sin(i * 12.9898f + k * 78.233f) * 43758.547f
    return x - kotlin.math.floor(x)
}

/**
 * The outer iris, alive: the ring of blue fibres breathes (and stretches with the voice), small
 * lights travel outward inside the fibres like nerve pulses, a ripple runs through them while a voice
 * is heard or spoken, and a dark limbal ring at the rim makes them sit deep inside the glass.
 * Everything is drawn only between [FIBER_INNER] and the rim, so the pupil and the collar are untouched.
 */
private fun DrawScope.drawFiberLife(
    t: Float, energy: Float, at: Offset, r: Float, dim: Float,
    texture: androidx.compose.ui.graphics.ImageBitmap, richEffects: Boolean,
) {
    val inner = r * FIBER_INNER
    val outer = r * FIBER_OUTER
    // 0 while calm, up to 1 with a loud voice (hers or the user's).
    val voice = ((energy - AMBIENT_ENERGY) / (1f - AMBIENT_ENERGY)).coerceIn(0f, 1f)

    // 1. Breathing: the fibre ring alone is drawn again a touch larger or smaller. Fibres run outward,
    // so a small radial stretch reads as the fibres themselves expanding, with no visible seam.
    val stretch = 1f + 0.010f * sin(t * 0.9f) + 0.020f * voice * (0.6f + 0.4f * sin(t * 9f))
    val ring = Path().apply {
        fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
        addOval(Rect(at, r))
        addOval(Rect(at, inner))
    }
    val d = (2f * r).toInt().coerceAtLeast(1)
    clipPath(ring) {
        scale(stretch, stretch, pivot = at) {
            drawImage(
                texture,
                srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                srcSize = androidx.compose.ui.unit.IntSize(texture.width, texture.height),
                dstOffset = androidx.compose.ui.unit.IntOffset((at.x - r).toInt(), (at.y - r).toInt()),
                dstSize = androidx.compose.ui.unit.IntSize(d, d),
                alpha = dim,
                filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
            )
        }
    }

    // 2. Neural pulses: short lights moving outward along the fibres, added to the picture's own light.
    val fibres = if (richEffects) 64 else 28
    val span = outer - inner
    for (i in 0 until fibres) {
        val a0 = fiberSeed(i, 1f)
        val a1 = fiberSeed(i, 2f)
        val a2 = fiberSeed(i, 3f)
        val p = (t * (0.09f + 0.13f * a1) + a2).let { it - kotlin.math.floor(it) }
        val angle = ((i + 0.7f * a0) / fibres) * 2f * PI.toFloat() + 0.035f * sin(p * 3f + a0 * 6f)
        // The glass highlight sits on the upper left: the pulses fade out under it.
        val fromHighlight = kotlin.math.abs(kotlin.math.atan2(sin(angle - HIGHLIGHT_ANGLE), cos(angle - HIGHLIGHT_ANGLE)))
        val clear = ((fromHighlight - 0.45f) / 0.45f).coerceIn(0f, 1f)
        val fade = sin(PI.toFloat() * p) // in at the collar, out at the rim
        val alpha = ((0.15f + 0.07f * sin(t * 2.1f + i) + 0.50f * voice) * fade * clear * dim).coerceIn(0f, 0.85f)
        if (alpha < 0.02f) continue
        val from = inner + span * p
        val to = minOf(outer, from + r * (0.06f + 0.06f * a0) * (1f + 0.6f * voice))
        val dx = cos(angle)
        val dy = sin(angle)
        val start = Offset(at.x + dx * from, at.y + dy * from)
        val end = Offset(at.x + dx * to, at.y + dy * to)
        val color = when {
            a1 < 0.45f -> FIBER_CYAN
            a1 < 0.78f -> FIBER_BLUE
            else -> FIBER_VIOLET
        }
        val width = (1.0f + 0.9f * a0) * density
        drawLine(color.copy(alpha = alpha * 0.30f), start, end, width * 3.2f, StrokeCap.Round, blendMode = androidx.compose.ui.graphics.BlendMode.Plus)
        drawLine(color.copy(alpha = alpha), start, end, width, StrokeCap.Round, blendMode = androidx.compose.ui.graphics.BlendMode.Plus)
    }

    // 3. Voice ripple: rings of light run outward through the fibres with the voice.
    if (voice > 0.03f) {
        for (k in 0..1) {
            val q = (t * 0.85f + k * 0.5f).let { it - kotlin.math.floor(it) }
            val alpha = (0.34f * voice * sin(PI.toFloat() * q) * dim).coerceIn(0f, 0.6f)
            if (alpha < 0.02f) continue
            drawCircle(FIBER_CYAN.copy(alpha = alpha), inner + span * q, at, style = Stroke((1.6f + 2.2f * voice) * density), blendMode = androidx.compose.ui.graphics.BlendMode.Plus)
        }
    }

    // 4. Limbal ring: a soft dark inner shadow at the rim, so the fibres lie deep inside the glass ball.
    drawCircle(
        Brush.radialGradient(0.78f to Color.Transparent, 0.92f to LIMBAL.copy(alpha = 0.42f * dim), 1f to LIMBAL.copy(alpha = 0.80f * dim), center = at, radius = r),
        r, at,
    )
}

/** The direction of the glass highlight on the eye picture (upper left), in canvas radians. */
private const val HIGHLIGHT_ANGLE = -2.25f
