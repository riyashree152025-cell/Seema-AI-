package com.raju.aiassistant.domain.kids

import com.raju.aiassistant.domain.model.AssistantState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/** Riya's face: its states, blinking, the voice-driven mouth, eyes that follow a touch, and tap reactions. */
class AvatarMathTest {
    @Test
    fun theFaceShowsWhatSeemaIsDoing() {
        assertEquals(AvatarMood.IDLE, AvatarMath.mood(AssistantState.Idle, 0, 0))
        assertEquals(AvatarMood.LISTENING, AvatarMath.mood(AssistantState.Listening, 0, 0))
        assertEquals(AvatarMood.THINKING, AvatarMath.mood(AssistantState.Thinking, 0, 0))
        assertEquals(AvatarMood.THINKING, AvatarMath.mood(AssistantState.Executing, 0, 0))
        assertEquals(AvatarMood.SPEAKING, AvatarMath.mood(AssistantState.Speaking, 0, 0))
        // A tickle wins for a moment, then the face goes back to what Seema is doing.
        assertEquals(AvatarMood.TICKLED, AvatarMath.mood(AssistantState.Speaking, 1_000, tickledUntil = 1_600))
        assertEquals(AvatarMood.SPEAKING, AvatarMath.mood(AssistantState.Speaking, 1_600, tickledUntil = 1_600))
    }

    @Test
    fun theEyesBlinkEveryThreeAndAHalfSeconds() {
        assertEquals(1f, AvatarMath.eyeOpen(0), 0f)
        assertEquals(1f, AvatarMath.eyeOpen(3_000), 0f)
        val closedAt = AvatarMath.BLINK_PERIOD_MS - AvatarMath.BLINK_MS / 2
        assertTrue(AvatarMath.eyeOpen(closedAt) < 0.05f)
        assertTrue(AvatarMath.eyeOpen(closedAt + AvatarMath.BLINK_PERIOD_MS) < 0.05f) // and again 3.5 s later
        assertEquals(1f, AvatarMath.eyeOpen(AvatarMath.BLINK_PERIOD_MS + 100), 0f)
    }

    @Test
    fun theMouthFollowsHerVoice() {
        assertEquals(0f, AvatarMath.mouthOpen(AvatarMood.IDLE, 0.8f, 0), 0f) // closed smile when not speaking
        assertEquals(0f, AvatarMath.mouthOpen(AvatarMood.LISTENING, 0.8f, 0), 0f)
        val quiet = AvatarMath.mouthOpen(AvatarMood.SPEAKING, 0.1f, 0)
        val loud = AvatarMath.mouthOpen(AvatarMood.SPEAKING, 0.6f, 0)
        assertTrue("louder voice opens the mouth more", loud > quiet)
        assertTrue(AvatarMath.mouthOpen(AvatarMood.SPEAKING, 5f, 0) <= 1f)
        // A voice engine without a level still moves the mouth while she speaks.
        val moves = (0L until 2_000L step 50).map { AvatarMath.mouthOpen(AvatarMood.SPEAKING, 0f, it) }
        assertTrue(moves.all { it in 0.1f..0.75f } && moves.max() - moves.min() > 0.2f)
        assertTrue(AvatarMath.mouthOpen(AvatarMood.TICKLED, 0f, 0) > 0f) // laughing
        // Smooth, never a jump.
        assertEquals(0.5f, AvatarMath.follow(0f, 1f, 0.5f / 18f), 1e-4f)
    }

    @Test
    fun thePupilsLookAtHerFingerThenAhead() {
        val (x, y) = AvatarMath.pupilOffset(100f, 100f, touchX = 400f, touchY = 100f, touchAge = 100, mood = AvatarMood.IDLE, maxR = 10f)
        assertTrue(x > 0f && abs(y) < 0.01f && hypot(x, y) <= 10.001f)
        assertEquals(0f to 0f, AvatarMath.pupilOffset(100f, 100f, 400f, 100f, touchAge = AvatarMath.LOOK_MS + 1, mood = AvatarMood.IDLE, maxR = 10f))
        val (_, up) = AvatarMath.pupilOffset(100f, 100f, null, null, 0, AvatarMood.THINKING, 10f)
        assertTrue("thinking looks up", up < 0f)
    }

    @Test
    fun aTapFindsTheNoseTheCheeksOrTheFace() {
        val cx = 500f; val cy = 800f; val r = 300f
        assertEquals(FaceZone.NOSE, AvatarMath.zone(cx, cy + r * AvatarMath.NOSE_Y, cx, cy, r))
        assertEquals(FaceZone.CHEEK, AvatarMath.zone(cx + r * AvatarMath.CHEEK_X, cy + r * AvatarMath.CHEEK_Y, cx, cy, r))
        assertEquals(FaceZone.CHEEK, AvatarMath.zone(cx - r * AvatarMath.CHEEK_X, cy + r * AvatarMath.CHEEK_Y, cx, cy, r))
        assertEquals(FaceZone.FACE, AvatarMath.zone(cx, cy - r * 0.6f, cx, cy, r))
        assertEquals(FaceZone.OUTSIDE, AvatarMath.zone(cx + r * 1.2f, cy, cx, cy, r))
    }

    @Test
    fun burstsFlyAndFadeAway() {
        val burst = AvatarMath.burst(10f, 10f, Particle.Kind.HEART, 9, seed = 3, scale = 1f)
        assertEquals(9, burst.size)
        val later = AvatarMath.step(burst, 0.5f)
        assertTrue(later.any { it.x != 10f || it.y != 10f })
        assertTrue(AvatarMath.step(later, 2f).isEmpty()) // all gone after their life
        assertTrue(AvatarMath.step(List(200) { burst.first() }, 0.01f).size <= AvatarMath.MAX_PARTICLES)
    }

    @Test
    fun bubblesFloatAroundTheFaceAndPopWhenTapped() {
        val b = AvatarMath.newBubble(1, 1080f, 2000f, faceCx = 540f, faceCy = 840f, faceR = 356f, seed = 5)
        assertTrue(b.y > 2000f) // starts below the screen and rises
        val risen = AvatarMath.stepBubbles(listOf(b), 1f, 2000f).single()
        assertTrue(risen.y < b.y)
        val t = 0L
        assertNotNull(AvatarMath.bubbleAt(listOf(risen), risen.x + AvatarMath.swayOf(risen, t), risen.y, t))
        assertNull(AvatarMath.bubbleAt(listOf(risen), risen.x + risen.r * 3f, risen.y, t))
        // One that left the top comes back at the bottom.
        val gone = risen.copy(y = -risen.r * 2)
        assertTrue(AvatarMath.stepBubbles(listOf(gone), 0.01f, 2000f).single().y > 2000f)
    }

    @Test
    fun theGiggleIsBengaliAndNotTooOften() {
        assertTrue(AvatarMath.GIGGLES.all { line -> line.any { it in 'ঀ'..'৿' } })
        assertTrue(AvatarMath.GIGGLE_GAP_MS >= 3_000)
    }
}
