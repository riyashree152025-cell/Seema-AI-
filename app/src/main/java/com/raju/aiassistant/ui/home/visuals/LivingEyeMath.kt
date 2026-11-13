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
/** The bi-colour iris, in eye radii: pupil, hazel collar, blue outer iris, limbal ring. */
const val PUPIL_SHARE = 0.24f
const val HAZEL_OUTER_SHARE = 0.45f
const val BLUE_INNER_SHARE = 0.42f
const val LIMBUS_SHARE = 0.92f

/** Parallax: the pupil and hazel collar move with the full gaze, the blue outer iris with 0.65 of it. */
const val INNER_PARALLAX = 1.0f
const val OUTER_PARALLAX = 0.65f

fun pupilScale(t: Float, energy: Float, state: AssistantState): Float {
    if (state == AssistantState.Sleeping || state == AssistantState.Offline) return 0.18f
    val micro = 0.012f * sin(t * 1.7f) + 0.006f * sin(t * 3.3f + 1.1f)
    val e = energy.coerceIn(0f, 1f)
    return when (state) {
        // Listening / speaking: the pupil breathes with the voice, 0.94x (quiet) … 1.06x (loud).
        // (Listening starts a little wider: she is paying attention.)
        AssistantState.Listening -> ((0.26f + micro) * pupilDilation(e)).coerceIn(0.15f, 0.32f)
        AssistantState.Speaking -> ((PUPIL_SHARE + micro) * pupilDilation(e)).coerceIn(0.15f, 0.32f)
        else -> (PUPIL_SHARE + micro - 0.04f * e).coerceIn(0.15f, 0.32f)
    }
}

/** Pupil dilation with the voice while listening or speaking: 0.94 at silence, 1.06 at full voice. */
fun pupilDilation(energy: Float): Float = 0.94f + 0.12f * energy.coerceIn(0f, 1f)

/** A stable pseudo-random number in -1..1 for saccade [n] on axis [axis]. */
private fun saccadeNoise(n: Int, axis: Int): Float {
    var h = n * 374_761_393 + axis * 668_265_263
    h = (h xor (h ushr 13)) * 1_274_126_177
    h = h xor (h ushr 16)
    return (h and 0xFFFF) / 32_767.5f - 1f
}

/** Seconds between micro-saccades of the idle eye, and how long each quick jump takes. */
const val SACCADE_EVERY_S = 1.5f
const val SACCADE_JUMP_S = 0.09f

/**
 * Micro-saccades: every [SACCADE_EVERY_S] the eye jumps quickly (in [SACCADE_JUMP_S]) to a new nearby
 * point and rests there — the small, sudden movements that make a real eye look alive.
 */
fun microSaccade(t: Float, amount: Float = 0.03f): Pair<Float, Float> {
    val n = kotlin.math.floor(t / SACCADE_EVERY_S).toInt()
    val since = t - n * SACCADE_EVERY_S
    val k = (since / SACCADE_JUMP_S).coerceIn(0f, 1f)
    val ease = k * k * (3f - 2f * k)
    val x = saccadeNoise(n - 1, 0) + (saccadeNoise(n, 0) - saccadeNoise(n - 1, 0)) * ease
    val y = saccadeNoise(n - 1, 1) + (saccadeNoise(n, 1) - saccadeNoise(n - 1, 1)) * ease
    return (x * amount) to (y * amount * 0.7f)
}

/** How far (in eye radii) the eye turns towards a touch, and for how long it keeps looking there. */
const val TOUCH_GAZE_MAX = 0.12f
const val TOUCH_GAZE_HOLD_S = 2.5f

/**
 * Looking at a touch: [dx], [dy] is the touch relative to the eye centre in eye radii; the eye turns
 * that way (at most [TOUCH_GAZE_MAX]) and drifts back after [TOUCH_GAZE_HOLD_S]. 0..1 weight returned
 * with the offset so it can blend with the idle gaze.
 */
fun touchGaze(dx: Float, dy: Float, secondsSinceTouch: Float): Triple<Float, Float, Float> {
    if (secondsSinceTouch < 0f || secondsSinceTouch > TOUCH_GAZE_HOLD_S + 0.6f) return Triple(0f, 0f, 0f)
    val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1e-3f)
    val reach = minOf(TOUCH_GAZE_MAX, len * 0.12f)
    val w = when {
        secondsSinceTouch < 0.15f -> secondsSinceTouch / 0.15f
        secondsSinceTouch < TOUCH_GAZE_HOLD_S -> 1f
        else -> 1f - (secondsSinceTouch - TOUCH_GAZE_HOLD_S) / 0.6f
    }.coerceIn(0f, 1f)
    return Triple(dx / len * reach, dy / len * reach, w)
}

/**
 * Where the eye looks, as an offset of the iris and pupil in units of the eye radius: a slow, calm
 * look-around while idle (like a real eye, never still), straight at the user while she listens,
 * up and to the sides while she thinks, small movements while she speaks, almost still asleep.
 */
fun eyeGaze(t: Float, state: AssistantState): Pair<Float, Float> = when (state) {
    AssistantState.Listening -> 0f to 0f
    AssistantState.Thinking, AssistantState.Executing -> (0.09f * sin(t * 0.9f)) to (-0.06f + 0.02f * sin(t * 1.7f))
    AssistantState.Speaking -> (0.03f * sin(t * 1.3f)) to (0.02f * sin(t * 1.9f + 0.5f))
    AssistantState.Sleeping, AssistantState.Offline -> 0f to 0.02f
    else -> {
        // A slow drift plus quick micro-saccades.
        val (sx, sy) = microSaccade(t)
        (0.06f * sin(t * 0.37f) + sx) to (0.04f * sin(t * 0.53f + 1f) + sy)
    }
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
fun irisLineCount(richEffects: Boolean): Int = if (richEffects) 120 else 60


/**
 * How open the lids are (0..1): a quick blink about every 5.3 s (0.18 s), half closed while asleep.
 */
fun eyeOpenness(t: Float, asleep: Boolean): Float {
    if (asleep) return 0.38f
    val phase = ((t % BLINK_EVERY_S) + BLINK_EVERY_S) % BLINK_EVERY_S
    if (phase >= BLINK_S) return 1f
    val x = phase / BLINK_S // 0 -> 1 over the blink: closes, then opens
    return (0.08f + 0.92f * kotlin.math.abs(1f - 2f * x)).coerceIn(0.08f, 1f)
}

const val BLINK_EVERY_S = 5.3f
const val BLINK_S = 0.18f
