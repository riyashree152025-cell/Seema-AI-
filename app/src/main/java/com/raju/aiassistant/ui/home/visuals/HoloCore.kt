package com.raju.aiassistant.ui.home.visuals

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.raju.aiassistant.domain.model.AssistantState
import kotlin.math.PI

// Neon-blue identity of the holographic core; each state adds its own accent like the Arc Reactor.
private const val NEON = 0xFF2EA8FF
private const val CYAN = 0xFF4DF3FF
private const val ICE = 0xFFB8ECFF
private const val ROYAL = 0xFF1E5BFF
private const val VIOLET = 0xFF9A7BFF
private const val MAGENTA = 0xFFFF4FD8
private const val GREEN = 0xFF4CE0A0
private const val AMBER = 0xFFFFC857
private const val ORANGE = 0xFFFF8A3D
private const val RED = 0xFFFF5C7A
private const val GREY = 0xFF5D7083
private const val GREY_LIGHT = 0xFF8FA9BF

/** How the 3D core looks in each assistant state (same rhythm and effects as the Arc Reactor, blue palette). */
fun holoCoreStyleFor(state: AssistantState): ReactorStyle = when (state) {
    AssistantState.Idle -> ReactorStyle(listOf(NEON, CYAN, ICE, ROYAL, NEON), glow = 0.7f, rotationSpeed = 0.06f, bpm = 64f)
    AssistantState.Listening -> ReactorStyle(listOf(CYAN, NEON, ICE, GREEN, CYAN), glow = 0.85f, rotationSpeed = 0.09f, bpm = 76f, ripples = true)
    AssistantState.Thinking -> ReactorStyle(listOf(VIOLET, NEON, CYAN, MAGENTA, VIOLET), glow = 0.8f, rotationSpeed = 0.25f, bpm = 96f, spinner = true)
    AssistantState.Speaking -> ReactorStyle(listOf(ICE, NEON, CYAN, ROYAL, ICE), glow = 0.9f, rotationSpeed = 0.12f, bpm = 72f, speechReactive = true)
    AssistantState.Executing -> ReactorStyle(listOf(AMBER, NEON, ORANGE, CYAN, AMBER), glow = 0.8f, rotationSpeed = 0.18f, bpm = 90f, sweep = true)
    AssistantState.Success -> ReactorStyle(listOf(GREEN, CYAN, NEON, ICE, GREEN), glow = 0.85f, rotationSpeed = 0.07f, bpm = 68f)
    AssistantState.Error -> ReactorStyle(listOf(RED, ORANGE, MAGENTA, ORANGE, RED), glow = 0.7f, rotationSpeed = 0.03f, bpm = 112f, flicker = true)
    AssistantState.Offline -> ReactorStyle(listOf(GREY, GREY_LIGHT, GREY, GREY_LIGHT, GREY), glow = 0.2f, rotationSpeed = 0f, bpm = 40f)
    AssistantState.Sleeping -> ReactorStyle(listOf(ROYAL, VIOLET, GREY_LIGHT, ROYAL, VIOLET), glow = 0.35f, rotationSpeed = 0.015f, bpm = 34f)
    // Amber, slow and steady: waiting for the user to allow a permission.
    AssistantState.WaitingPermission -> ReactorStyle(listOf(AMBER, ORANGE, AMBER, CYAN, AMBER), glow = 0.6f, rotationSpeed = 0.04f, bpm = 56f)
}

/** Tilt, size and speed of the three holographic rings (radians, units, revolutions per second). */
private class RingSpec(val radius: Float, val tiltX: Float, val tiltZ: Float, val speed: Float, val precession: Float, val dashed: Boolean)

private val RINGS = listOf(
    RingSpec(1.02f, 1.18f, 0.25f, 0.30f, 0.02f, dashed = false),
    RingSpec(1.22f, 1.05f, -0.55f, -0.22f, -0.015f, dashed = true),
    RingSpec(1.42f, 1.32f, 1.1f, 0.15f, 0.01f, dashed = false),
)

/**
 * The modern AI core: a rotating 3D wireframe sphere with a beating energy core, three
 * holographic rings orbiting in 3D (each with a satellite), floating particles and state effects
 * (listening ripples, thinking spinner, working sweep, error flicker). Drawn with real perspective
 * on one Canvas — near parts are brighter and bigger, far parts dimmer — batched into a few paths
 * per frame so it stays light on budget phones.
 *
 * @param time seconds from [rememberAnimationTime]; read during draw only.
 * @param speechLevel 0..1 level of Seema's voice (speaking state).
 */
@Composable
fun HoloCore(
    style: ReactorStyle,
    particleCount: Int,
    richEffects: Boolean,
    time: () -> Float,
    speechLevel: () -> Float,
    modifier: Modifier = Modifier,
) {
    val palette = List(PALETTE_SIZE) { i -> animateColorAsState(Color(style.paletteArgb[i]), tween(600), label = "holoColor$i").value }
    val glow by animateFloatAsState(style.glow, tween(600), label = "holoGlow")
    val sphere = remember { sphereWireframe(latitudes = 5, meridians = 6, segments = 36) }
    val rings = remember { RINGS.map { ringPoints(it.radius, 96) } }
    val shell = remember(particleCount) {
        List(particleCount) { i ->
            val a = pseudoRandom(i * 3 + 11) * 2f * PI.toFloat()
            val y = pseudoRandom(i * 5 + 7) * 1.6f - 0.8f
            val r = 1.55f + pseudoRandom(i * 7 + 3) * 0.3f
            Vec3(r * kotlin.math.cos(a) * kotlin.math.sqrt(1f - y * y / 1.2f), y, r * kotlin.math.sin(a) * kotlin.math.sqrt(1f - y * y / 1.2f))
        }
    }
    val back = remember { Path() }
    val front = remember { Path() }

    Canvas(modifier) {
        val t = time()
        val unit = size.minDimension / 2f / 1.75f // the outer ring fits the canvas
        val c = center
        val beat = if (style.speechReactive) speechLevel().coerceIn(0f, 1f) else heartbeat(t, style.bpm)
        val flicker = if (style.flicker) 0.65f + 0.35f * pseudoRandom((t * 12f).toInt()) else 1f
        val energy = (0.55f + 0.45f * beat) * flicker
        val spin = t * style.rotationSpeed * 2f * PI.toFloat()
        val primary = palette[0]
        val accent = palette[2]

        // Ambient halo
        drawCircle(
            Brush.radialGradient(listOf(primary.copy(alpha = (0.28f + 0.3f * beat) * glow * flicker), palette[3].copy(alpha = 0.08f * glow), Color.Transparent), center = c, radius = unit * 1.9f),
            radius = unit * 1.9f,
        )

        // Listening: 3D ripples spreading out around the core like sound waves.
        if (style.ripples) {
            repeat(3) { i ->
                val progress = ((t * 0.55f) + i / 3f) % 1f
                drawLoop(ringPoints(0.7f + 0.9f * progress, 64).map { it.rotateX(1.25f) }, c, unit, palette[1].copy(alpha = (1f - progress) * 0.55f * flicker), 1.5f * density)
            }
        }

        // Rings: back halves first (behind the core), front halves after it.
        fun ringPaths(index: Int, spec: RingSpec) {
            back.reset(); front.reset()
            val pts = rings[index].map { p ->
                p.rotateZ(t * spec.speed * 2f * PI.toFloat()).rotateX(spec.tiltX).rotateZ(spec.tiltZ + t * spec.precession * 2f * PI.toFloat())
            }
            addLoop(pts, c, unit, back, front)
        }
        RINGS.forEachIndexed { i, spec ->
            ringPaths(i, spec)
            val col = palette[(i + 1) % PALETTE_SIZE]
            drawPath(back, col.copy(alpha = 0.22f * flicker), style = ringStroke(spec.dashed, 1.4f * density))
        }

        // Wireframe sphere (back side), the energy core, then the sphere's front side.
        back.reset(); front.reset()
        val tilt = 0.38f + 0.06f * kotlin.math.sin(t * 0.4f)
        sphere.forEach { loop -> addLoop(loop.map { it.rotateY(spin * 0.8f).rotateX(tilt) }, c, unit * 0.62f * (1f + 0.04f * beat), back, front) }
        drawPath(back, palette[3].copy(alpha = 0.16f * flicker), style = Stroke(1f * density))

        val coreR = unit * (0.30f + 0.08f * beat)
        drawCircle(
            Brush.radialGradient(listOf(Color.White.copy(alpha = 0.95f * energy), primary.copy(alpha = 0.85f * energy), accent.copy(alpha = 0.35f * energy), Color.Transparent), center = c, radius = coreR * 1.8f),
            radius = coreR * 1.8f, center = c,
        )
        drawCircle(Color.White.copy(alpha = 0.9f * energy), radius = coreR * 0.35f, center = c)
        drawPath(front, primary.copy(alpha = 0.55f * flicker), style = Stroke(1.3f * density))

        // Thinking: a fast spinning arc inside the sphere.
        if (style.spinner) {
            val arc = ringPoints(0.82f, 48).map { it.rotateZ(t * 5f).rotateX(0.9f).rotateY(t * 1.3f) }
            back.reset(); front.reset()
            addLoop(arc.take(16), c, unit, front, front, closed = false)
            drawPath(front, palette[1].copy(alpha = 0.9f * flicker), style = Stroke(3f * density, cap = StrokeCap.Round))
        }

        // Front halves of the rings, their satellites, and the working sweep.
        RINGS.forEachIndexed { i, spec ->
            ringPaths(i, spec)
            val col = palette[(i + 1) % PALETTE_SIZE]
            drawPath(front, col.copy(alpha = 0.8f * flicker), style = ringStroke(spec.dashed, 2f * density))
            val sat = rings[i][((t * 18f * (i + 1)).toInt()) % rings[i].size]
                .rotateZ(t * spec.speed * 2f * PI.toFloat()).rotateX(spec.tiltX).rotateZ(spec.tiltZ + t * spec.precession * 2f * PI.toFloat())
            val sp = project(sat, c.x, c.y, unit)
            drawCircle(col.copy(alpha = (0.3f + 0.6f * sp.depth) * flicker), radius = (2f + 5f * sp.depth) * density, center = Offset(sp.x, sp.y))
            drawCircle(Color.White.copy(alpha = 0.4f + 0.5f * sp.depth), radius = (1f + 2f * sp.depth) * density, center = Offset(sp.x, sp.y))
        }
        if (style.sweep) {
            val pts = rings[0].map { it.rotateX(RINGS[0].tiltX).rotateZ(RINGS[0].tiltZ) }
            val n = (pts.size * ((t * 0.35f) % 1f)).toInt().coerceAtLeast(2)
            back.reset(); front.reset()
            addLoop(pts.take(n), c, unit, front, front, closed = false)
            drawPath(front, primary.copy(alpha = 0.95f), style = Stroke(3.5f * density, cap = StrokeCap.Round))
        }

        // Floating particles on an outer shell, slowly turning.
        if (richEffects || particleCount > 0) {
            shell.forEachIndexed { i, p ->
                val q = project(p.rotateY(spin * 0.3f + i * 0.01f).rotateX(0.3f), c.x, c.y, unit)
                val tw = 0.5f + 0.5f * kotlin.math.sin(t * 2f + i)
                drawCircle(palette[i % PALETTE_SIZE].copy(alpha = (0.15f + 0.6f * q.depth) * tw * flicker), radius = (0.8f + 2.2f * q.depth) * density, center = Offset(q.x, q.y))
            }
        }
    }
}

private fun ringStroke(dashed: Boolean, width: Float) =
    Stroke(width, cap = StrokeCap.Round, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(width * 6f, width * 4f)) else null)

/** Adds a loop of 3D points to [back] or [front] depending on which side of the core each segment is. */
private fun addLoop(points: List<Vec3>, c: Offset, unit: Float, back: Path, front: Path, closed: Boolean = true) {
    if (points.size < 2) return
    val proj = points.map { project(it, c.x, c.y, unit) }
    val last = if (closed) proj.size else proj.size - 1
    for (k in 0 until last) {
        val a = proj[k]
        val b = proj[(k + 1) % proj.size]
        val path = if ((a.depth + b.depth) / 2f >= 0.5f) front else back
        path.moveTo(a.x, a.y)
        path.lineTo(b.x, b.y)
    }
}

private fun DrawScope.drawLoop(points: List<Vec3>, c: Offset, unit: Float, color: Color, width: Float) {
    val path = Path()
    points.forEachIndexed { i, p ->
        val q = project(p, c.x, c.y, unit)
        if (i == 0) path.moveTo(q.x, q.y) else path.lineTo(q.x, q.y)
    }
    path.close()
    drawPath(path, color, style = Stroke(width))
}
