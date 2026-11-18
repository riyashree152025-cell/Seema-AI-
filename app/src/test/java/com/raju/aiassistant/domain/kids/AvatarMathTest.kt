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
        assertTrue(AvatarMath.GIGGLE_GAP_MS >= 4_500)
    }

    @Test
    fun aCheekTapWinksThatEyeAndKeepsTheOtherOpen() {
        assertEquals(WinkSide.LEFT, AvatarMath.winkSide(FaceZone.CHEEK, x = 300f, cx = 500f))
        assertEquals(WinkSide.RIGHT, AvatarMath.winkSide(FaceZone.CHEEK, x = 700f, cx = 500f))
        assertEquals(WinkSide.NONE, AvatarMath.winkSide(FaceZone.FACE, x = 300f, cx = 500f))
        assertEquals(1f, AvatarMath.squint(AvatarMood.TICKLED, WinkSide.LEFT, leftEye = true), 0f)
        assertEquals(0f, AvatarMath.squint(AvatarMood.TICKLED, WinkSide.LEFT, leftEye = false), 0f) // the other stays wide open
        assertEquals(1f, AvatarMath.squint(AvatarMood.TICKLED, WinkSide.NONE, leftEye = false), 0f) // ^ ^ both
        assertEquals(0f, AvatarMath.squint(AvatarMood.IDLE, WinkSide.LEFT, leftEye = true), 0f)
    }

    @Test
    fun ripplesGrowFadeAndAreKeptFew() {
        val r = Ripple(10f, 10f, bornAt = 1_000)
        val (size0, alpha0) = AvatarMath.ripple(r, 1_000)!!
        val (size1, alpha1) = AvatarMath.ripple(r, 1_000 + AvatarMath.RIPPLE_MS / 2)!!
        assertTrue(size1 > size0 && alpha1 < alpha0)
        assertNull(AvatarMath.ripple(r, 1_000 + AvatarMath.RIPPLE_MS))
        val many = List(20) { Ripple(0f, 0f, bornAt = 5_000L + it) }
        assertEquals(AvatarMath.MAX_RIPPLES, AvatarMath.liveRipples(many, 5_100).size)
        assertTrue(AvatarMath.liveRipples(many, 9_000).isEmpty())
    }

    @Test
    fun tenRapidTapsPlayFewSoundsAndNeverOverHerVoiceOrListening() {
        val gate = TapAudioGate()
        // 10 taps in 2 seconds: at most one chime every 600 ms.
        val chimes = (0 until 10).count { gate.sound(now = it * 200L, busy = false) }
        assertEquals(4, chimes) // at 0, 600, 1200, 1800 ms
        // The laugh line at most every 4.5 s.
        val g = TapAudioGate()
        assertTrue(g.giggle(0, busy = false))
        assertTrue(!g.giggle(3_000, busy = false))
        assertTrue(g.giggle(4_600, busy = false))
        // Never while Seema listens or speaks (the faces still react: that is drawn, not gated).
        val busy = TapAudioGate()
        assertTrue(!busy.sound(0, busy = AvatarMath.audioBusy(AssistantState.Listening, capturing = true))) // she is talking
        assertTrue(!busy.giggle(0, busy = AvatarMath.audioBusy(AssistantState.Speaking)))
        assertTrue(busy.sound(0, busy = AvatarMath.audioBusy(AssistantState.Idle)))
        // The microphone merely open (nobody talking): the laugh and chimes are allowed.
        val passive = TapAudioGate()
        assertTrue(passive.giggle(0, busy = AvatarMath.audioBusy(AssistantState.Listening, capturing = false)))
    }

    @Test
    fun riyaModeListensAgainCalmlyAfterSilence() {
        val p = com.raju.aiassistant.domain.voice.TurnPacing
        assertEquals(400L, p.toddlerRelistenMs(0))
        assertEquals(400L, p.toddlerRelistenMs(p.TODDLER_FAST_FAILS - 1))
        assertTrue(p.toddlerRelistenMs(p.TODDLER_FAST_FAILS) >= 2_000L) // instant failures again and again: back off
    }
}
