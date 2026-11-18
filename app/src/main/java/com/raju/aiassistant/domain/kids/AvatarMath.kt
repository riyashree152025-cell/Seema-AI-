package com.raju.aiassistant.domain.kids

import com.raju.aiassistant.domain.model.AssistantState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The pure logic behind Riya's animated face (RiyaAvatarFace): which mood the face shows, blinking, the
 * mouth that follows her voice, the pupils that follow a touch, where a tap landed (nose, cheek, face, a
 * bubble), and the little particles and bubbles. No Android here, so all of it is unit-tested; the
 * Compose Canvas only draws what these functions say.
 */
enum class AvatarMood { IDLE, LISTENING, THINKING, SPEAKING, TICKLED }

/** Where a tap landed on the face. */
enum class FaceZone { NOSE, CHEEK, FACE, OUTSIDE }

data class Particle(
    val x: Float, val y: Float, val vx: Float, val vy: Float,
    /** Seconds left. */
    val life: Float,
    val kind: Kind,
    val colorArgb: Long,
    val size: Float,
) {
    enum class Kind { HEART, STAR, SPARK }
}

data class Bubble(val id: Long, val x: Float, val y: Float, val r: Float, val vy: Float, val phase: Float, val colorArgb: Long)

object AvatarMath {
    /** One blink every 3.5 s, closed for about 0.16 s. */
    const val BLINK_PERIOD_MS = 3_500L
    const val BLINK_MS = 160L
    /** A tickle shows the happy ^ ^ eyes and brighter blush for this long. */
    const val TICKLE_MS = 1_600L
    /** A boop squishes the nose for this long. */
    const val BOOP_MS = 450L
    /** The giggle line is said at most this often (a toddler taps a lot). */
    const val GIGGLE_GAP_MS = 4_000L
    /** Pupils go back to looking ahead this long after the last touch. */
    const val LOOK_MS = 2_200L
    const val MAX_BUBBLES = 6
    const val MAX_PARTICLES = 80

    /** The face's mood: a recent tickle wins (she is laughing), then what Seema is doing. */
    fun mood(state: AssistantState, now: Long, tickledUntil: Long): AvatarMood = when {
        now < tickledUntil -> AvatarMood.TICKLED
        state == AssistantState.Speaking -> AvatarMood.SPEAKING
        state == AssistantState.Listening -> AvatarMood.LISTENING
        state == AssistantState.Thinking || state == AssistantState.Executing -> AvatarMood.THINKING
        else -> AvatarMood.IDLE
    }

    /** How open the eyes are: 1 = open, 0 = closed (a quick close-and-open every [BLINK_PERIOD_MS]). */
    fun eyeOpen(now: Long): Float {
        val t = now % BLINK_PERIOD_MS
        val start = BLINK_PERIOD_MS - BLINK_MS
        if (t < start) return 1f
        val k = (t - start).toFloat() / BLINK_MS // 0..1 over the blink
        return abs(1f - 2f * k).coerceIn(0f, 1f)
    }

    /**
     * How open the mouth is, 0..1. Speaking: the voice's loudness (her real audio); when the voice engine gives
     * no level, a natural talking rhythm instead. Otherwise closed in a smile (a little open when laughing).
     */
    fun mouthOpen(mood: AvatarMood, level: Float, now: Long): Float = when (mood) {
        AvatarMood.SPEAKING ->
            if (level > 0.02f) (0.12f + level * 1.4f).coerceIn(0f, 1f)
            else (0.35f + 0.3f * sin(now / 95.0).toFloat() * sin(now / 230.0).toFloat()).coerceIn(0.1f, 0.75f)
        AvatarMood.TICKLED -> 0.55f
        else -> 0f
    }

    /** Smooth step toward [target] (the mouth must not jump between frames). */
    fun follow(current: Float, target: Float, dtSec: Float, speed: Float = 18f): Float =
        current + (target - current) * min(1f, dtSec * speed)

    /** Gentle breathing: a few pixels up and down every 4 s. */
    fun breath(now: Long, amplitude: Float): Float = (sin(now / 4_000.0 * 2 * PI) * amplitude).toFloat()

    /**
     * Where the pupils look, as an offset from the eye centre, at most [maxR] away. Toward the last touch
     * (for [LOOK_MS]), up while thinking, ahead otherwise.
     */
    fun pupilOffset(
        eyeX: Float, eyeY: Float, touchX: Float?, touchY: Float?, touchAge: Long, mood: AvatarMood, maxR: Float,
    ): Pair<Float, Float> {
        if (touchX != null && touchY != null && touchAge in 0 until LOOK_MS) {
            val dx = touchX - eyeX
            val dy = touchY - eyeY
            val d = hypot(dx, dy)
            if (d < 1f) return 0f to 0f
            val k = min(1f, d / (maxR * 8f)) * maxR / d
            return dx * k to dy * k
        }
        return if (mood == AvatarMood.THINKING) maxR * 0.25f to -maxR * 0.85f else 0f to 0f
    }

    /** Which part of a face centred at ([cx], [cy]) with radius [r] a tap at ([x], [y]) hit. */
    fun zone(x: Float, y: Float, cx: Float, cy: Float, r: Float): FaceZone {
        val nx = (x - cx) / r
        val ny = (y - cy) / r
        if (hypot(nx, ny) > 1f) return FaceZone.OUTSIDE
        if (hypot(nx, ny - NOSE_Y) < 0.13f) return FaceZone.NOSE
        if (hypot(abs(nx) - CHEEK_X, ny - CHEEK_Y) < 0.22f) return FaceZone.CHEEK
        return FaceZone.FACE
    }

    /** Face layout, as fractions of the face radius from its centre (shared by drawing and hit-testing). */
    const val EYE_X = 0.36f
    const val EYE_Y = -0.02f
    const val NOSE_Y = 0.24f
    const val CHEEK_X = 0.55f
    const val CHEEK_Y = 0.36f
    const val MOUTH_Y = 0.52f

    /** A small burst where she tapped: hearts for a tickle, stars for a boop or a popped bubble. */
    fun burst(x: Float, y: Float, kind: Particle.Kind, count: Int, seed: Int, scale: Float): List<Particle> {
        val rnd = java.util.Random(seed.toLong())
        return List(count) {
            val a = rnd.nextFloat() * 2 * PI
            val speed = (80f + rnd.nextFloat() * 160f) * scale
            Particle(
                x, y,
                vx = (kotlin.math.cos(a) * speed).toFloat(),
                vy = (sin(a) * speed).toFloat() - 60f * scale,
                life = 0.9f + rnd.nextFloat() * 0.6f,
                kind = kind,
                colorArgb = PALETTE[rnd.nextInt(PALETTE.size)],
                size = (10f + rnd.nextFloat() * 10f) * scale,
            )
        }
    }

    /** Particles move, rise a little (hearts float), and fade; spent ones are removed. */
    fun step(particles: List<Particle>, dtSec: Float): List<Particle> = particles.mapNotNull { p ->
        val life = p.life - dtSec
        if (life <= 0f) null
        else p.copy(
            x = p.x + p.vx * dtSec,
            y = p.y + p.vy * dtSec,
            vx = p.vx * (1f - min(1f, dtSec * 1.6f)),
            vy = p.vy * (1f - min(1f, dtSec * 1.6f)) - (if (p.kind == Particle.Kind.HEART) 40f * dtSec else 0f),
            life = life,
        )
    }.takeLast(MAX_PARTICLES)

    /** Bubbles rise slowly with a little sway; one that leaves the top comes back at the bottom. */
    fun stepBubbles(bubbles: List<Bubble>, dtSec: Float, height: Float): List<Bubble> = bubbles.map { b ->
        val y = b.y - b.vy * dtSec
        if (y + b.r < 0f) b.copy(y = height + b.r) else b.copy(y = y)
    }

    /** The bubble a tap hit (the one on top: the last drawn), or null. */
    fun bubbleAt(bubbles: List<Bubble>, x: Float, y: Float, now: Long): Bubble? = bubbles.lastOrNull { b ->
        hypot(x - (b.x + swayOf(b, now)), y - b.y) <= b.r * 1.15f
    }

    /** A bubble's sideways sway at [now] (drawn and hit-tested the same way). */
    fun swayOf(b: Bubble, now: Long): Float = (sin(now / 900.0 + b.phase) * b.r * 0.35).toFloat()

    /** A new bubble somewhere around the face (not over it, so the face stays clear). */
    fun newBubble(id: Long, width: Float, height: Float, faceCx: Float, faceCy: Float, faceR: Float, seed: Int): Bubble {
        val rnd = java.util.Random(seed.toLong())
        var x: Float
        var tries = 0
        do {
            x = rnd.nextFloat() * width
            tries++
        } while (abs(x - faceCx) < faceR * 0.9f && tries < 8)
        val r = max(22f, min(width, height) * (0.045f + rnd.nextFloat() * 0.03f))
        return Bubble(id, x, height + r, r, vy = 40f + rnd.nextFloat() * 50f, phase = rnd.nextFloat() * 6.28f, colorArgb = PALETTE[rnd.nextInt(PALETTE.size)])
    }

    val PALETTE = listOf(0xFFFF6B9A, 0xFFFFD43B, 0xFF69DB7C, 0xFF4DABF7, 0xFFB197FC, 0xFFFFA94D)

    /** What she says when tickled (at most every [GIGGLE_GAP_MS]). */
    val GIGGLES = listOf("হাহা, সুড়সুড়ি লাগছে!", "হিহি, আবার করো!", "হাহাহা, রিয়া সোনা!")
}
