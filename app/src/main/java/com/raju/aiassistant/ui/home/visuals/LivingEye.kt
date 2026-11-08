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
        if (drawEye) drawLivingEye(t, e, state, accent, area, lastTouch(), eyeTexture)
    }
}

private fun DrawScope.drawAmbientWaves(t: Float, energy: Float, accent: Color, richEffects: Boolean, centerY: Float) {
    val w = size.width
    val h = size.height
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
) {
    val c = area?.center ?: center
    val asleep = state == AssistantState.Sleeping || state == AssistantState.Offline
    val side = area?.let { minOf(it.width, it.height) } ?: minOf(size.width, size.height)
    val r = side * EYE_RADIUS_SHARE * eyeBreath(t) * (1f + 0.05f * energy)
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

    val d = (2f * r).toInt().coerceAtLeast(1)
    drawImage(
        texture,
        srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
        srcSize = androidx.compose.ui.unit.IntSize(texture.width, texture.height),
        dstOffset = androidx.compose.ui.unit.IntOffset((eyeAt.x - r).toInt(), (eyeAt.y - r).toInt()),
        dstSize = androidx.compose.ui.unit.IntSize(d, d),
        alpha = dim,
        filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
    )

    // Pupil: the texture's own pupil is its resting size; when she listens or speaks it dilates with the
    // voice — a feathered obsidian ring grows over the hazel collar, with a dark-brown shadow at its
    // edge so it recedes into the eye. It never shrinks below the drawn pupil.
    val grow = (pupilScale(t, energy, state) / PUPIL_SHARE).coerceAtLeast(1f)
    if (grow > 1.005f) {
        val pr = r * TEXTURE_PUPIL_SHARE * grow
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
        val glow = Offset(eyeAt.x + cos(a) * r * 0.66f, eyeAt.y + sin(a) * r * 0.66f)
        drawCircle(Brush.radialGradient(listOf(CYAN.copy(alpha = 0.35f), Color.Transparent), glow, r * 0.28f), r * 0.28f, glow)
    }
}
