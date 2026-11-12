package com.raju.aiassistant.ui.home.visuals

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.raju.aiassistant.domain.model.AssistantState
import kotlin.math.min

/** Deep night sky behind the aurora (the eye look keeps its own blue background). */
val GLASS_BACKGROUND = listOf(Color(0xFF040817), Color(0xFF060B1C), Color(0xFF02030A))

/**
 * Home's second look, "Liquid Glass": a realistic glass sphere holding glowing liquid — molten gold over
 * deep sapphire — that sways and rises with Seema's voice and the user's, swirls while she thinks and
 * barely breathes while she sleeps. Light blobs drift inside, small bubbles rise, the glass has a soft
 * inner shadow, a bright rim and a reflection that moves a little when the phone is tilted. One canvas,
 * decorative (hidden from screen readers); [time] and the levels are read only while drawing.
 */
@Composable
fun LiquidGlassVisualizer(
    time: () -> Float,
    speechLevel: () -> Float,
    micLevel: () -> Float,
    state: AssistantState,
    modifier: Modifier = Modifier,
    /** False in battery saver or on a hot phone: fewer lights, no bubbles, no tilt sensor. */
    richEffects: Boolean = true,
    /** Where the sphere sits (Home: the area above the buttons), or null for the canvas centre. */
    sphereArea: () -> Rect? = { null },
) {
    val palette = glassPalette(state)
    val top by animateColorAsState(Color(palette.liquidTop), tween(600), label = "glassTop")
    val bottom by animateColorAsState(Color(palette.liquidBottom), tween(600), label = "glassBottom")
    val glow by animateColorAsState(Color(palette.glow), tween(600), label = "glassGlow")
    val energy = remember { floatArrayOf(AMBIENT_ENERGY) }
    val slope = remember { floatArrayOf(0f) }
    val tilt = rememberTilt(enabled = richEffects)
    val surface = remember { Path() }
    val foam = remember { Path() }
    val clip = remember { Path() }
    val curtain = remember { Path() }
    val caustic = remember { Path() }
    val aurora = auroraColors(state).map { Color(it) }
    val a0 by animateColorAsState(aurora[0], tween(900), label = "aurora0")
    val a1 by animateColorAsState(aurora[1], tween(900), label = "aurora1")
    val a2 by animateColorAsState(aurora[2], tween(900), label = "aurora2")
    val speed = glassSpeed(state)

    Canvas(modifier.clearAndSetSemantics {}) {
        val t = time()
        energy[0] = smoothEnergy(energy[0], waveEnergy(state, speechLevel(), micLevel()))
        val e = energy[0]
        val voice = ((e - AMBIENT_ENERGY) / (1f - AMBIENT_ENERGY)).coerceIn(0f, 1f)
        val life = liquidLife(state, e)
        // The surface eases towards the tilt like a real liquid would.
        slope[0] += (tiltSlope(tilt[0]) - slope[0]) * 0.06f

        // 1. The whole screen: night sky, twinkling stars and northern-lights curtains.
        drawStars(t, if (richEffects) 70 else 30)
        drawAurora(t, listOf(a0, a1, a2), if (richEffects) 3 else 2, voice, curtain, richEffects)

        val area = sphereArea() ?: Rect(Offset.Zero, size)
        val r = min(area.width, area.height) * 0.36f
        if (r <= 1f) return@Canvas // no room yet (first layout pass)
        val c = area.center

        drawAmbience(c, r, glow, voice)
        drawOrbit(c, r, t, speed, glow, back = true)

        // 2. The glass body: dark sapphire with a faint mist of the aurora's colour in its upper half.
        drawCircle(Brush.radialGradient(listOf(Color(0xFF13203A), Color(0xFF060A14)), c, r), r, c)

        clip.reset()
        clip.addOval(Rect(c, r))
        clipPath(clip) {
            drawMist(c, r, t, a0, a2)
            drawLiquid(c, r, t, e, life, speed, slope[0], top, bottom, surface, foam, caustic, richEffects)
            drawLights(c, r, t, speed, voice, top, glow, richEffects)
            if (richEffects) drawBubbles(c, r, t, e, life, speed, slope[0])
            // Inner shadow at the edge: the sphere gets its depth.
            drawCircle(
                Brush.radialGradient(0.68f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f), center = c, radius = r),
                r, c,
            )
        }
        drawGlassRim(c, r, glow)
        drawReflections(c, r, tilt)
        drawOrbit(c, r, t, speed, glow, back = false)
    }
}

/** Twinkling stars over the whole screen (fixed places, so they do not jump). */
private fun DrawScope.drawStars(t: Float, count: Int) {
    for (i in 0 until count) {
        val (x, y, b) = star(i, t)
        drawCircle(Color.White.copy(alpha = 0.55f * b), radius = 0.6f + 1.1f * ((i * 0.37f) % 1f), center = Offset(x * size.width, y * size.height))
    }
}

/**
 * Northern lights across the whole screen: each curtain is bright along a slowly waving line and fades
 * upwards, with fine vertical rays shimmering in it (rich effects only). Added light (BlendMode.Plus).
 */
private fun DrawScope.drawAurora(t: Float, colors: List<Color>, bands: Int, voice: Float, path: Path, rich: Boolean) {
    val w = size.width
    val h = size.height
    val steps = 36
    for (i in 0 until bands) {
        val color = colors[i % colors.size]
        val height = h * (0.20f + 0.05f * i)
        path.reset()
        var minY = h
        var maxY = 0f
        for (k in 0..steps) {
            val x = k.toFloat() / steps
            val y = auroraY(i, x, t) * h
            if (k == 0) path.moveTo(0f, y) else path.lineTo(x * w, y)
            minY = min(minY, y)
            maxY = kotlin.math.max(maxY, y)
        }
        for (k in steps downTo 0) {
            val x = k.toFloat() / steps
            path.lineTo(x * w, auroraY(i, x, t) * h - height)
        }
        path.close()
        val alpha = 0.30f + 0.12f * voice - 0.05f * i
        drawPath(
            path,
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.55f to color.copy(alpha = alpha * 0.45f),
                0.92f to color.copy(alpha = alpha),
                1f to color.copy(alpha = alpha * 0.2f),
                startY = minY - height, endY = maxY + h * 0.01f,
            ),
            blendMode = BlendMode.Plus,
        )
        if (rich) {
            // Rays: thin vertical streaks rising from the curtain's bright edge.
            var x = (i * 7f) % 14f
            while (x < w) {
                val xf = x / w
                val y = auroraY(i, xf, t) * h
                val shimmer = 0.5f + 0.5f * kotlin.math.sin(t * 1.3f + x * 0.05f + i)
                drawLine(
                    color.copy(alpha = 0.10f * shimmer),
                    Offset(x, y), Offset(x, y - height * (0.5f + 0.5f * shimmer)),
                    strokeWidth = 2f, blendMode = BlendMode.Plus,
                )
                x += 14f
            }
        }
    }
}

/** A faint, slowly turning mist inside the empty upper glass, so it never looks like a black hole. */
private fun DrawScope.drawMist(c: Offset, r: Float, t: Float, a: Color, b: Color) {
    val p1 = Offset(c.x + r * 0.35f * kotlin.math.cos(t * 0.25f), c.y - r * 0.35f + r * 0.1f * kotlin.math.sin(t * 0.3f))
    val p2 = Offset(c.x - r * 0.30f * kotlin.math.cos(t * 0.2f + 1f), c.y - r * 0.45f)
    drawCircle(Brush.radialGradient(listOf(a.copy(alpha = 0.22f), Color.Transparent), p1, r * 0.8f), r * 0.8f, p1, blendMode = BlendMode.Plus)
    drawCircle(Brush.radialGradient(listOf(b.copy(alpha = 0.18f), Color.Transparent), p2, r * 0.7f), r * 0.7f, p2, blendMode = BlendMode.Plus)
}

/**
 * A thin tilted orbit of light around the sphere with a spark running along it. Drawn in two halves —
 * the back half before the sphere, the front half after — so the ring really goes around it.
 */
private fun DrawScope.drawOrbit(c: Offset, r: Float, t: Float, speed: Float, glow: Color, back: Boolean) {
    val rx = r * 1.32f
    val ry = r * 0.30f
    rotate(-16f, pivot = c) {
        drawArc(
            glow.copy(alpha = if (back) 0.10f else 0.28f), startAngle = if (back) 180f else 0f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(c.x - rx, c.y - ry), size = Size(rx * 2f, ry * 2f), style = Stroke(width = r * 0.008f),
        )
        val a = t * 0.7f * speed
        val front = kotlin.math.sin(a) > 0f
        if (front != back) {
            val p = Offset(c.x + rx * kotlin.math.cos(a), c.y + ry * kotlin.math.sin(a))
            val k = if (back) 0.45f else 1f
            drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.55f * k), Color.Transparent), p, r * 0.12f), r * 0.12f, p, blendMode = BlendMode.Plus)
            drawCircle(Color.White.copy(alpha = 0.9f * k), r * 0.018f, p)
        }
    }
}

/** A soft halo of the liquid's colour behind the sphere and its warm light on the "floor" below. */
private fun DrawScope.drawAmbience(c: Offset, r: Float, glow: Color, voice: Float) {
    drawCircle(
        Brush.radialGradient(listOf(glow.copy(alpha = 0.16f + 0.14f * voice), Color.Transparent), c, r * 1.75f),
        r * 1.75f, c,
    )
    val floorCenter = Offset(c.x, c.y + r * 1.13f)
    val floor = Size(r * 1.7f, r * 0.30f)
    drawOval(
        Brush.radialGradient(listOf(glow.copy(alpha = 0.30f + 0.20f * voice), Color.Transparent), floorCenter, floor.width / 2f),
        topLeft = Offset(floorCenter.x - floor.width / 2f, floorCenter.y - floor.height / 2f),
        size = floor,
    )
}

/**
 * The liquid: a waving, sloshing surface (level with the ground when the phone tilts) filled gold →
 * deep blue, a second fainter wave behind it, a glow rising from the bottom, moving caustic light lines
 * inside, and a bright foam line along the surface.
 */
private fun DrawScope.drawLiquid(
    c: Offset, r: Float, t: Float, e: Float, life: Float, speed: Float, slope: Float,
    top: Color, bottom: Color, path: Path, foam: Path, caustic: Path, rich: Boolean,
) {
    val level = liquidLevel(e, t, speed)
    val restY = c.y + r - 2f * r * level
    val steps = if (rich) 48 else 24
    fun surfaceAt(x: Float, phase: Float) = restY + surfaceOffset(x, t + phase, life, speed, slope) * r
    fun wave(phase: Float, alpha: Float) {
        path.reset()
        path.moveTo(c.x - r, c.y + r)
        for (i in 0..steps) {
            val x = -1f + 2f * i / steps
            path.lineTo(c.x + x * r, surfaceAt(x, phase))
        }
        path.lineTo(c.x + r, c.y + r)
        path.close()
        drawPath(
            path,
            Brush.verticalGradient(
                0f to top.copy(alpha = alpha),
                0.45f to androidx.compose.ui.graphics.lerp(top, bottom, 0.55f).copy(alpha = alpha),
                1f to bottom.copy(alpha = alpha),
                startY = restY - r * 0.3f, endY = c.y + r,
            ),
        )
    }
    if (rich) wave(phase = 2.1f, alpha = 0.38f)
    wave(phase = 0f, alpha = 0.96f)

    // Light rising from the bottom of the liquid.
    val glowAt = Offset(c.x, c.y + r * 0.75f)
    drawCircle(Brush.radialGradient(listOf(top.copy(alpha = 0.35f + 0.25f * life), Color.Transparent), glowAt, r * 0.9f), r * 0.9f, glowAt, blendMode = BlendMode.Plus)

    // Caustics: soft wavy light lines moving through the liquid.
    if (rich) for (k in 0 until 3) {
        caustic.reset()
        val depth = 0.25f + 0.22f * k
        for (i in 0..steps) {
            val x = -1f + 2f * i / steps
            val y = surfaceAt(x, 0f) + (c.y + r - restY) * depth + r * 0.05f * kotlin.math.sin(x * (6f + k) + t * (1.2f + 0.4f * k) * speed)
            if (i == 0) caustic.moveTo(c.x + x * r, y) else caustic.lineTo(c.x + x * r, y)
        }
        drawPath(caustic, Color.White.copy(alpha = 0.10f + 0.06f * life), style = Stroke(width = r * 0.012f), blendMode = BlendMode.Plus)
    }

    // Foam: the bright edge of the surface.
    foam.reset()
    for (i in 0..steps) {
        val x = -1f + 2f * i / steps
        val y = surfaceAt(x, 0f)
        if (i == 0) foam.moveTo(c.x + x * r, y) else foam.lineTo(c.x + x * r, y)
    }
    drawPath(foam, Color.White.copy(alpha = 0.55f), style = Stroke(width = r * 0.014f))
    drawPath(foam, top.copy(alpha = 0.5f), style = Stroke(width = r * 0.05f), blendMode = BlendMode.Plus)
}

/** Glowing blobs drifting inside, added on top of the liquid; a hot core pulses while she speaks. */
private fun DrawScope.drawLights(c: Offset, r: Float, t: Float, speed: Float, voice: Float, top: Color, glow: Color, rich: Boolean) {
    val colors = listOf(top, Color(0xFF2E7BFF), glow, Color(0xFF26D0CE))
    val count = if (rich) 4 else 2
    for (i in 0 until count) {
        val (bx, by) = blobCenter(i, count, t, speed)
        val p = Offset(c.x + bx * r, c.y + by * r)
        val br = r * (0.38f + 0.10f * voice)
        drawCircle(
            Brush.radialGradient(listOf(colors[i].copy(alpha = 0.34f + 0.30f * voice), Color.Transparent), p, br),
            br, p, blendMode = BlendMode.Plus,
        )
    }
    if (voice > 0.02f) {
        val core = r * (0.18f + 0.30f * voice)
        drawCircle(Brush.radialGradient(listOf(top.copy(alpha = 0.55f * voice), Color.Transparent), c, core), core, c, blendMode = BlendMode.Plus)
    }
}

/** Small bubbles rising through the liquid, quicker when it is lively. */
private fun DrawScope.drawBubbles(c: Offset, r: Float, t: Float, e: Float, life: Float, speed: Float, slope: Float) {
    val level = liquidLevel(e, t, speed)
    val restY = c.y + r - 2f * r * level
    for (i in 0 until 9) {
        val rise = ((t * (0.10f + 0.12f * life) * speed + i / 9f) % 1f)
        val xs = -0.6f + 1.2f * ((i * 0.37f) % 1f)
        val x = c.x + r * xs + r * 0.03f * kotlin.math.sin(t * 2f + i)
        val top = restY + surfaceOffset(xs, t, life, speed, slope) * r
        val y = (c.y + r * 0.92f) - rise * ((c.y + r * 0.92f) - top)
        val br = r * (0.012f + 0.012f * ((i * 0.53f) % 1f))
        drawCircle(Color.White.copy(alpha = 0.30f * (1f - rise)), br, Offset(x, y))
    }
}

/** The glass edge: a bright, uneven rim of light and a thin warm inner line. */
private fun DrawScope.drawGlassRim(c: Offset, r: Float, glow: Color) {
    drawCircle(
        Brush.sweepGradient(
            listOf(
                Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.30f),
                Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.55f),
            ),
            c,
        ),
        r, c, style = Stroke(width = r * 0.022f),
    )
    drawCircle(glow.copy(alpha = 0.22f), r * 0.965f, c, style = Stroke(width = r * 0.008f))
}

/** The window reflection (top left, moving a little with the tilt), a sharp glint, and a faint bounce light. */
private fun DrawScope.drawReflections(c: Offset, r: Float, tilt: FloatArray) {
    val (sx, sy) = highlightShift(tilt[0], tilt[1])
    val hc = Offset(c.x + (-0.36f + sx) * r, c.y + (-0.46f + sy) * r)
    val hs = Size(r * 0.78f, r * 0.40f)
    rotate(-32f, pivot = hc) {
        drawOval(
            Brush.radialGradient(listOf(Color.White.copy(alpha = 0.34f), Color.White.copy(alpha = 0.06f), Color.Transparent), hc, hs.width / 2f),
            topLeft = Offset(hc.x - hs.width / 2f, hc.y - hs.height / 2f), size = hs,
        )
    }
    drawCircle(Color.White.copy(alpha = 0.85f), r * 0.045f, Offset(c.x + (-0.52f + sx) * r, c.y + (-0.56f + sy) * r))
    drawArc(
        Color.White.copy(alpha = 0.16f), startAngle = 20f, sweepAngle = 70f, useCenter = false,
        topLeft = Offset(c.x - r * 0.9f, c.y - r * 0.9f), size = Size(r * 1.8f, r * 1.8f), style = Stroke(width = r * 0.03f),
    )
}

/**
 * The phone's tilt (accelerometer x, y in m/s², smoothed), read while drawing. Off when [enabled] is
 * false (battery saver / hot phone) or the phone has no accelerometer: the reflection then stays put.
 */
@Composable
private fun rememberTilt(enabled: Boolean): FloatArray {
    val context = LocalContext.current
    val tilt = remember { floatArrayOf(0f, 0f) }
    DisposableEffect(enabled) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                tilt[0] += (event.values[0] - tilt[0]) * 0.08f
                tilt[1] += (event.values[1] - 9.81f * 0.6f - tilt[1]) * 0.08f
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (enabled && sm != null && sensor != null) runCatching { sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI) }
        onDispose { runCatching { sm?.unregisterListener(listener) } }
    }
    return tilt
}
