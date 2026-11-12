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

/** Warm black behind the sphere (the eye look keeps its own blue background). */
val GLASS_BACKGROUND = listOf(Color(0xFF07060A), Color(0xFF0B0A10), Color(0xFF020203))

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
    val tilt = rememberTilt(enabled = richEffects)
    val surface = remember { Path() }
    val clip = remember { Path() }
    val speed = glassSpeed(state)

    Canvas(modifier.clearAndSetSemantics {}) {
        val t = time()
        energy[0] = smoothEnergy(energy[0], waveEnergy(state, speechLevel(), micLevel()))
        val e = energy[0]
        val voice = ((e - AMBIENT_ENERGY) / (1f - AMBIENT_ENERGY)).coerceIn(0f, 1f)
        val area = sphereArea() ?: Rect(Offset.Zero, size)
        val r = min(area.width, area.height) * 0.36f
        if (r <= 1f) return@Canvas // no room yet (first layout pass)
        val c = area.center

        drawAmbience(c, r, glow, voice)

        // The glass body: dark, slightly blue, so the liquid's light carries the sphere.
        drawCircle(Brush.radialGradient(listOf(Color(0xFF101A2C), Color(0xFF05080F)), c, r), r, c)

        clip.reset()
        clip.addOval(Rect(c, r))
        clipPath(clip) {
            drawLiquid(c, r, t, e, speed, top, bottom, surface, richEffects)
            drawLights(c, r, t, speed, voice, top, glow, richEffects)
            if (richEffects) drawBubbles(c, r, t, e, speed)
            // Inner shadow at the edge: the sphere gets its depth.
            drawCircle(
                Brush.radialGradient(0.70f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.62f), center = c, radius = r),
                r, c,
            )
        }
        drawGlassRim(c, r, glow)
        drawReflections(c, r, tilt)
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

/** The liquid: a waving surface filled gold → sapphire, with a fainter second wave behind it. */
private fun DrawScope.drawLiquid(
    c: Offset, r: Float, t: Float, e: Float, speed: Float,
    top: Color, bottom: Color, path: Path, rich: Boolean,
) {
    val level = liquidLevel(e, t, speed)
    val restY = c.y + r - 2f * r * level
    fun wave(phase: Float, alpha: Float) {
        path.reset()
        val steps = if (rich) 40 else 20
        path.moveTo(c.x - r, c.y + r)
        for (i in 0..steps) {
            val x = -1f + 2f * i / steps
            path.lineTo(c.x + x * r, restY + surfaceOffset(x, t + phase, e, speed) * r)
        }
        path.lineTo(c.x + r, c.y + r)
        path.close()
        drawPath(path, Brush.verticalGradient(listOf(top.copy(alpha = alpha), bottom.copy(alpha = alpha)), startY = restY - r * 0.16f, endY = c.y + r))
    }
    if (rich) wave(phase = 1.9f, alpha = 0.35f)
    wave(phase = 0f, alpha = 0.95f)
    // A bright line of light along the surface.
    drawLine(
        Brush.horizontalGradient(listOf(Color.Transparent, top.copy(alpha = 0.55f), Color.Transparent), c.x - r, c.x + r),
        Offset(c.x - r, restY), Offset(c.x + r, restY), strokeWidth = r * 0.012f,
    )
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
            Brush.radialGradient(listOf(colors[i].copy(alpha = 0.30f + 0.25f * voice), Color.Transparent), p, br),
            br, p, blendMode = BlendMode.Plus,
        )
    }
    if (voice > 0.02f) {
        val core = r * (0.18f + 0.30f * voice)
        drawCircle(Brush.radialGradient(listOf(top.copy(alpha = 0.55f * voice), Color.Transparent), c, core), core, c, blendMode = BlendMode.Plus)
    }
}

/** Small bubbles rising through the liquid. */
private fun DrawScope.drawBubbles(c: Offset, r: Float, t: Float, e: Float, speed: Float) {
    val level = liquidLevel(e, t, speed)
    val surfaceY = c.y + r - 2f * r * level
    for (i in 0 until 7) {
        val rise = ((t * 0.12f * speed + i / 7f) % 1f)
        val x = c.x + r * (-0.55f + 1.1f * ((i * 0.37f) % 1f)) + r * 0.03f * kotlin.math.sin(t * 2f + i)
        val y = (c.y + r * 0.92f) - rise * ((c.y + r * 0.92f) - surfaceY)
        val br = r * (0.012f + 0.010f * ((i * 0.53f) % 1f))
        drawCircle(Color.White.copy(alpha = 0.22f * (1f - rise)), br, Offset(x, y))
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
