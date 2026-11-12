package com.raju.aiassistant.ui.home.visuals

import com.raju.aiassistant.domain.model.AssistantState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The numbers behind Home's second look, "Liquid Glass": a glass sphere holding glowing liquid (gold
 * over deep blue) that sways with the voice. Pure functions so they can be tested off the phone.
 */

/** Liquid colours (top of the liquid, bottom of the liquid) and the glow around the sphere, as ARGB. */
data class GlassPalette(val liquidTop: Long, val liquidBottom: Long, val glow: Long)

fun glassPalette(state: AssistantState): GlassPalette = when (state) {
    // Listening: cooler, the blue rises to meet the voice.
    AssistantState.Listening -> GlassPalette(0xFF7FD8FF, 0xFF0B3D91, 0xFF4FC3F7)
    // Thinking: violet swirl.
    AssistantState.Thinking -> GlassPalette(0xFFB79CFF, 0xFF2A1466, 0xFF9575CD)
    // Speaking: warm molten gold.
    AssistantState.Speaking -> GlassPalette(0xFFFFD27A, 0xFF8A4B08, 0xFFFFC857)
    AssistantState.Error -> GlassPalette(0xFFFF8A80, 0xFF5C0A0A, 0xFFFF5252)
    AssistantState.Sleeping, AssistantState.Offline -> GlassPalette(0xFF8C7A5B, 0xFF0D1626, 0xFF4E5D73)
    // Idle, success and the rest: calm gold over sapphire.
    else -> GlassPalette(0xFFF5C26B, 0xFF0E2A66, 0xFFE0B061)
}

/** How fast the liquid moves: slow breathing asleep, a quick swirl while thinking. */
fun glassSpeed(state: AssistantState): Float = when (state) {
    AssistantState.Sleeping, AssistantState.Offline -> 0.35f
    AssistantState.Thinking -> 2.2f
    AssistantState.Speaking, AssistantState.Listening -> 1.3f
    else -> 1f
}

/** The share of the sphere filled with liquid (0..1): breathes gently, rises with the voice. */
fun liquidLevel(energy: Float, t: Float, speed: Float): Float {
    val voice = ((energy - AMBIENT_ENERGY) / (1f - AMBIENT_ENERGY)).coerceIn(0f, 1f)
    val breath = 0.025f * sin(t * 0.9f * speed)
    return (0.46f + 0.12f * voice + breath).coerceIn(0.3f, 0.66f)
}

/**
 * How lively the liquid is, 0..1, even without a measured voice level: it always sloshes a little,
 * more while listening or thinking (the microphone level is 0 when the shared microphone is off).
 */
fun liquidLife(state: AssistantState, energy: Float): Float {
    val voice = ((energy - AMBIENT_ENERGY) / (1f - AMBIENT_ENERGY)).coerceIn(0f, 1f)
    val base = when (state) {
        AssistantState.Listening -> 0.45f
        AssistantState.Thinking, AssistantState.Executing -> 0.55f
        AssistantState.Speaking -> 0.40f
        AssistantState.Sleeping, AssistantState.Offline -> 0.12f
        else -> 0.25f
    }
    return (base + 0.6f * voice).coerceIn(0f, 1f)
}

/**
 * The liquid surface at [x] (-1 left edge … 1 right edge of the sphere), as a share of the radius above
 * or below the resting level: waves that grow with [life], a slow slosh, and the [slope] the phone's tilt
 * gives (real liquid stays level when the glass tilts). Never more than 0.3 of the radius.
 */
fun surfaceOffset(x: Float, t: Float, life: Float, speed: Float, slope: Float = 0f): Float {
    val amp = 0.03f + 0.10f * life
    val w = sin(x * 3.1f + t * 1.9f * speed) * 0.55f +
        sin(x * 5.7f - t * 2.6f * speed + 1.3f) * 0.30f +
        sin(x * 9.3f + t * 3.4f * speed + 0.4f) * 0.15f
    val slosh = 0.06f * life * sin(t * 0.8f * speed) * x
    return (amp * w + slosh + slope * x).coerceIn(-0.3f, 0.3f)
}

/** The surface's tilt from the phone's sideways tilt (accelerometer x, m/s²): at most 0.22 of the radius. */
fun tiltSlope(tiltX: Float): Float = (tiltX / 9.81f * 0.35f).coerceIn(-0.22f, 0.22f)

/**
 * One northern-lights curtain: the height of band [i] at [x] (0 left … 1 right of the screen), as a share
 * of the screen height, drifting with [t]. Bands lie in the upper two thirds (0.08 … 0.70).
 */
fun auroraY(i: Int, x: Float, t: Float): Float {
    val base = 0.20f + 0.13f * i
    val y = base + 0.07f * sin(x * (4.0f + i) + t * (0.18f + 0.05f * i) + i * 1.7f) +
        0.035f * sin(x * (9.0f - i) - t * (0.27f + 0.04f * i))
    return y.coerceIn(0.08f, 0.70f)
}

/** Northern-lights colours for the state (up to three curtains), as ARGB. */
fun auroraColors(state: AssistantState): List<Long> = when (state) {
    AssistantState.Listening -> listOf(0xFF22D3EE, 0xFF3B82F6, 0xFF34D399)
    AssistantState.Thinking, AssistantState.Executing -> listOf(0xFFA78BFA, 0xFF6366F1, 0xFFEC4899)
    AssistantState.Speaking -> listOf(0xFFFBBF24, 0xFFF472B6, 0xFF34D399)
    AssistantState.Error -> listOf(0xFFF87171, 0xFFFB923C, 0xFF9333EA)
    AssistantState.Sleeping, AssistantState.Offline -> listOf(0xFF1E3A5F, 0xFF312E81, 0xFF134E4A)
    else -> listOf(0xFF34D399, 0xFF22D3EE, 0xFF8B5CF6)
}

/** A star's place (0..1 of the screen) and how bright it twinkles now (0..1), the same every frame. */
fun star(i: Int, t: Float): Triple<Float, Float, Float> {
    val x = ((i * 0.618034f) % 1f)
    val y = ((i * 0.381966f * 1.7f) % 1f) * 0.85f
    val twinkle = 0.35f + 0.65f * (0.5f + 0.5f * sin(t * (0.8f + (i % 5) * 0.3f) + i))
    return Triple(x, y, twinkle)
}

/** Where light blob [i] of [count] floats inside the sphere, in radii from the centre (within 0.6). */
fun blobCenter(i: Int, count: Int, t: Float, speed: Float): Pair<Float, Float> {
    val phase = i * (2 * PI.toFloat() / count)
    val a = phase + t * (0.35f + 0.08f * i) * speed
    val rx = 0.42f + 0.12f * sin(t * 0.5f + i)
    val ry = 0.30f + 0.10f * cos(t * 0.4f + 2 * i)
    return (rx * cos(a)).coerceIn(-0.6f, 0.6f) to (ry * sin(a) + 0.08f).coerceIn(-0.6f, 0.6f)
}

/** The bright reflection moves a little with the phone's tilt (accelerometer, m/s²), at most 0.07 radius. */
fun highlightShift(tiltX: Float, tiltY: Float): Pair<Float, Float> =
    (-tiltX / 9.81f * 0.07f).coerceIn(-0.07f, 0.07f) to (tiltY / 9.81f * 0.07f - 0.035f).coerceIn(-0.07f, 0.07f)
