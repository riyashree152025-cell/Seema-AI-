package com.raju.aiassistant.ui.home.visuals

import com.raju.aiassistant.domain.model.AssistantState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * The pure parts of Home's Living Eye and its ambient waves (unit-tested without a screen): colours,
 * breathing, pupil, how strongly the waves move, and the wave shape itself.
 */

/** The eye's radius as a share of the smaller side of its area: it commands the upper viewport. */
const val EYE_RADIUS_SHARE = 0.40f

/** Seconds per breath of the idle eye. */
const val EYE_BREATH_SECONDS = 2.6f

/** Accent of the eye and the strongest wave: cyan ready, neon blue listening, purple thinking. */
fun eyeAccentArgb(state: AssistantState): Long = when (state) {
    AssistantState.Idle -> 0xFF00E5FF
    AssistantState.Listening -> 0xFF2979FF
    AssistantState.Thinking -> 0xFF9A7BFF
    AssistantState.Executing -> 0xFFB388FF
    AssistantState.Speaking -> 0xFF18FFFF
    AssistantState.Success -> 0xFF69F0AE
    AssistantState.Error -> 0xFFFF5C7A
    AssistantState.WaitingPermission -> 0xFFFFC857
    AssistantState.Offline -> 0xFF78909C
    AssistantState.Sleeping -> 0xFF3D5AFE
}

/** The three colours of the waves, left to right: cyan → neon blue → purple. */
val WAVE_GRADIENT_ARGB = listOf(0xFF00E5FF, 0xFF2979FF, 0xFFB388FF)

/** Idle breathing: radius scale 1 ± 3.5 % on a [EYE_BREATH_SECONDS] sine. */
fun eyeBreath(t: Float): Float = 1f + 0.035f * sin(2f * PI.toFloat() * t / EYE_BREATH_SECONDS)

/**
 * Pupil radius as a share of the iris: small restless micro-adjustments always; wider while she
 * listens, narrower on loud sound (a real pupil reacting to intensity), small and still asleep.
 */
fun pupilScale(t: Float, energy: Float, state: AssistantState): Float {
    if (state == AssistantState.Sleeping || state == AssistantState.Offline) return 0.26f
    val micro = 0.025f * sin(t * 1.7f) + 0.015f * sin(t * 3.3f + 1.1f)
    val base = if (state == AssistantState.Listening) 0.42f else 0.36f
    return (base + micro - 0.10f * energy.coerceIn(0f, 1f)).coerceIn(0.2f, 0.5f)
}

/** Sharp spikes turn around the iris while she listens, thinks, works or speaks. */
fun eyeSpikesActive(state: AssistantState): Boolean =
    state == AssistantState.Listening || state == AssistantState.Thinking || state == AssistantState.Executing || state == AssistantState.Speaking

/** Turns per second of the spikes (thinking spins fastest). */
fun eyeSpinSpeed(state: AssistantState): Float = when (state) {
    AssistantState.Thinking -> 0.45f
    AssistantState.Executing -> 0.35f
    AssistantState.Speaking -> 0.18f
    AssistantState.Listening -> 0.12f
    else -> 0f
}

/** The inner radar sweep while she is processing. */
fun eyeRadarActive(state: AssistantState): Boolean = state == AssistantState.Thinking || state == AssistantState.Executing

/**
 * How much the waves move, 0..1: her voice level while she speaks, the microphone while she listens,
 * a calm ambient motion otherwise (never 0, so the screen is never still).
 */
fun waveEnergy(state: AssistantState, speechLevel: Float, micLevel: Float): Float = when (state) {
    AssistantState.Speaking -> 0.15f + 0.85f * speechLevel.coerceIn(0f, 1f)
    AssistantState.Listening -> 0.12f + 0.88f * micLevel.coerceIn(0f, 1f)
    AssistantState.Thinking, AssistantState.Executing -> 0.18f
    AssistantState.Sleeping, AssistantState.Offline -> 0.04f
    else -> AMBIENT_ENERGY
}

const val AMBIENT_ENERGY = 0.07f

/** Follows [target] quickly up and slowly down, so speech peaks swell and fade like a fluid. */
fun smoothEnergy(current: Float, target: Float): Float = current + (target - current) * (if (target > current) 0.35f else 0.08f)

/**
 * Height of wave [layer] at [x] (0 = left edge, 1 = right edge), -1..1 before scaling: two mixed
 * sines whose speed and frequency rise with [energy]; tallest in the middle, still moving at the edges.
 */
fun waveShape(x: Float, t: Float, layer: Int, energy: Float): Float {
    val e = energy.coerceIn(0f, 1f)
    val k = 1.4f + layer * 0.55f + e * 1.6f
    val speed = (0.55f + layer * 0.3f) * (1f + e * 2.2f)
    val twoPi = 2f * PI.toFloat()
    val a = sin(twoPi * k * x + t * speed + layer * 1.3f)
    val b = cos(twoPi * k * 0.53f * x - t * speed * 1.3f + layer * 0.7f)
    // Stays clearly visible at the very edges, so the waves run the full width of the screen.
    val envelope = 0.7f + 0.3f * cos(PI.toFloat() * (x - 0.5f) * 1.2f)
    return (0.7f * a + 0.3f * b) * envelope
}

/** Fewer wave layers, wave points and iris lines in battery saver / on a hot phone. */
fun waveLayerCount(richEffects: Boolean): Int = if (richEffects) 4 else 2
fun wavePointCount(richEffects: Boolean): Int = if (richEffects) 96 else 48
fun irisLineCount(richEffects: Boolean): Int = if (richEffects) 72 else 36
