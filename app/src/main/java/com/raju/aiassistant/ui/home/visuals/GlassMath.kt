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
    val breath = 0.018f * sin(t * 0.9f * speed)
    return (0.46f + 0.12f * voice + breath).coerceIn(0.3f, 0.66f)
}

/**
 * The liquid surface at [x] (-1 left edge … 1 right edge of the sphere), as a share of the radius above
 * or below the resting level. Two waves; taller with the voice, never higher than 0.16 of the radius.
 */
fun surfaceOffset(x: Float, t: Float, energy: Float, speed: Float): Float {
    val voice = ((energy - AMBIENT_ENERGY) / (1f - AMBIENT_ENERGY)).coerceIn(0f, 1f)
    val amp = 0.025f + 0.11f * voice
    val w = sin(x * 3.1f + t * 1.7f * speed) * 0.65f + sin(x * 5.3f - t * 2.3f * speed + 1.3f) * 0.35f
    return (amp * w).coerceIn(-0.16f, 0.16f)
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
