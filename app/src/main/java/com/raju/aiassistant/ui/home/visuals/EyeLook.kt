package com.raju.aiassistant.ui.home.visuals

import com.raju.aiassistant.domain.model.AssistantState
import kotlin.math.exp
import kotlin.math.floor

/*
 * Where the Living Eye looks, like a human eye: it fixes on a point, holds it for a moment, then jumps
 * (a saccade) to the next one — never a slow, mechanical sweep. What it looks at follows the
 * conversation: at you while you speak, up and to one side while she thinks, at you with small
 * glances away while she talks, around the room while she is idle.
 *
 * [lookTarget] is the point the eye is fixed on at time t (piecewise constant, in -1..1 of the eye's
 * reach: x left..right, y up..down); [lookStep] is the quick jump towards it, frame by frame.
 * Pure functions, unit-tested without a screen.
 */

/** How a state's glances are chosen: seconds per fixation, and where the eye may look. */
private data class LookStyle(
    val fixationS: Float,
    /** Chance that a fixation goes back to (near) the user, the centre. */
    val atUser: Float,
    /** How far the other fixations go, as a share of the reach. */
    val reachX: Float,
    val reachY: Float,
    /** Vertical bias of the other fixations: negative looks up (thinking), positive down. */
    val biasY: Float = 0f,
    /** Thinking: the other fixations alternate left and right, up high. */
    val sideToSide: Boolean = false,
)

private fun styleFor(state: AssistantState): LookStyle? = when (state) {
    AssistantState.Idle -> LookStyle(fixationS = 1.35f, atUser = 0.40f, reachX = 0.85f, reachY = 0.55f)
    AssistantState.Listening -> LookStyle(fixationS = 1.9f, atUser = 0.82f, reachX = 0.30f, reachY = 0.20f)
    AssistantState.Thinking, AssistantState.Executing ->
        LookStyle(fixationS = 0.85f, atUser = 0.12f, reachX = 0.85f, reachY = 0.35f, biasY = -0.55f, sideToSide = true)
    AssistantState.Speaking -> LookStyle(fixationS = 0.65f, atUser = 0.62f, reachX = 0.60f, reachY = 0.30f, biasY = -0.05f)
    AssistantState.Error -> LookStyle(fixationS = 1.2f, atUser = 0.25f, reachX = 0.35f, reachY = 0.15f, biasY = 0.45f)
    AssistantState.Success, AssistantState.WaitingPermission -> LookStyle(fixationS = 1.6f, atUser = 0.85f, reachX = 0.25f, reachY = 0.15f)
    AssistantState.Sleeping, AssistantState.Offline -> null
}

/** A stable pseudo-random number in 0..1 for fixation [n], [salt]. */
private fun hash01(n: Int, salt: Int): Float {
    var h = n * 668_265_263 + salt * 374_761_393 + 0x5bd1e995
    h = (h xor (h ushr 13)) * 1_274_126_177
    h = h xor (h ushr 16)
    return (h and 0xFFFF) / 65_535f
}

/** Fixation [n] starts at [fixationS] × (n + up to ±0.35): irregular, like a person, but always in order. */
private fun fixationStart(n: Int, fixationS: Float): Float = fixationS * (n + 0.7f * (hash01(n, 7) - 0.5f))

/** Which fixation time [t] falls in (the boundaries are irregular but strictly increasing). */
fun fixationIndex(t: Float, fixationS: Float): Int {
    var n = floor(t / fixationS).toInt()
    if (t >= fixationStart(n + 1, fixationS)) n += 1 else if (t < fixationStart(n, fixationS)) n -= 1
    return n
}

/** Where a sleeping / offline eye rests: low and still, as if half asleep. */
val RESTING_LOOK = 0f to 0.30f

/**
 * The point the eye is fixed on at [t] for [state], in -1..1 of its reach (x: left..right, y: up..down).
 * The same t and state always give the same point, so it can be drawn every frame without memory.
 */
fun lookTarget(t: Float, state: AssistantState): Pair<Float, Float> {
    val style = styleFor(state) ?: return RESTING_LOOK
    val n = fixationIndex(t, style.fixationS)
    // At the user: a small spread around the centre (a real eye never fixes the exact same point twice).
    if (hash01(n, 1) < style.atUser) {
        return (0.10f * (hash01(n, 2) * 2f - 1f)) to (0.08f * (hash01(n, 3) * 2f - 1f))
    }
    val rx = hash01(n, 4) * 2f - 1f
    val ry = hash01(n, 5) * 2f - 1f
    val x = if (style.sideToSide) {
        // Thinking: up-left, then up-right … (sometimes the same side twice).
        val side = if ((n + if (hash01(n, 6) < 0.25f) 1 else 0) % 2 == 0) -1f else 1f
        side * style.reachX * (0.45f + 0.55f * kotlin.math.abs(rx))
    } else {
        style.reachX * rx
    }
    val y = (style.biasY + style.reachY * ry).coerceIn(-1f, 1f)
    return x.coerceIn(-1f, 1f) to y
}

/** Time constant of a jump: a human saccade reaches its point in about 60–100 ms and stops there. */
const val SACCADE_TAU_S = 0.028f

/**
 * One frame of the jump from [current] towards [target]: fast at first, settling softly at the point
 * (critically fast, never overshooting). [dt] is the frame time in seconds (a long pause, e.g. the
 * screen was off, simply arrives).
 */
fun lookStep(current: Float, target: Float, dt: Float): Float {
    if (!(dt > 0f)) return current
    val k = 1f - exp(-dt.coerceAtMost(0.25f) / SACCADE_TAU_S)
    return current + (target - current) * k
}
