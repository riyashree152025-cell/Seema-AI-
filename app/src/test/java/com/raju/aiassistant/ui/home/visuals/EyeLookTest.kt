package com.raju.aiassistant.ui.home.visuals

import com.raju.aiassistant.domain.model.AssistantState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class EyeLookTest {

    private fun samples(state: AssistantState, seconds: Int = 120) = (0 until seconds * 20).map { lookTarget(it * 0.05f, state) }

    @Test
    fun theEyeAlwaysStaysInsideItsReach() {
        AssistantState.entries.forEach { s ->
            samples(s).forEach { (x, y) -> assertTrue(s.name, abs(x) <= 1f && abs(y) <= 1f) }
        }
    }

    @Test
    fun itHoldsEachPointThenJumpsLikeAHumanEye() {
        val idle = samples(AssistantState.Idle)
        // Fixed on a point most of the time (piecewise constant), with a new point every second or so.
        val changes = idle.zipWithNext().count { (a, b) -> a != b }
        assertTrue("changes=$changes", changes in 50..140)
        // It really travels around the whole eye, not just a little wobble in the middle.
        assertTrue(idle.maxOf { it.first } > 0.6f && idle.minOf { it.first } < -0.6f)
    }

    @Test
    fun whileListeningItMostlyLooksAtYou() {
        val listening = samples(AssistantState.Listening)
        val atYou = listening.count { (x, y) -> abs(x) <= 0.1f && abs(y) <= 0.1f }
        assertTrue("atYou=$atYou of ${listening.size}", atYou > listening.size * 0.65f)
        // …but it is not frozen: small glances happen too.
        assertTrue(listening.distinct().size > 20)
    }

    @Test
    fun whileThinkingItLooksUpAndToBothSides() {
        val thinking = samples(AssistantState.Thinking)
        val up = thinking.count { it.second < -0.15f }
        assertTrue("up=$up of ${thinking.size}", up > thinking.size * 0.7f)
        assertTrue(thinking.any { it.first < -0.4f && it.second < -0.15f })
        assertTrue(thinking.any { it.first > 0.4f && it.second < -0.15f })
    }

    @Test
    fun whileSpeakingItMovesMoreOftenThanWhileListening() {
        fun changes(s: AssistantState) = samples(s).zipWithNext().count { (a, b) -> a != b }
        assertTrue(changes(AssistantState.Speaking) > changes(AssistantState.Listening))
        // and still comes back to you most of the time
        val speaking = samples(AssistantState.Speaking)
        assertTrue(speaking.count { (x, y) -> abs(x) <= 0.1f && abs(y) <= 0.1f } > speaking.size * 0.4f)
    }

    @Test
    fun asleepItRestsLowAndStill() {
        samples(AssistantState.Sleeping, 10).forEach { assertEquals(RESTING_LOOK, it) }
        samples(AssistantState.Offline, 10).forEach { assertEquals(RESTING_LOOK, it) }
    }

    @Test
    fun theFixationsComeInOrder() {
        // Irregular boundaries, but time never goes back to an earlier fixation.
        var last = Int.MIN_VALUE
        (0 until 20_000).forEach { k ->
            val n = fixationIndex(k * 0.01f, 0.85f)
            assertTrue(n >= last)
            assertTrue(n - last <= 1 || last == Int.MIN_VALUE)
            last = n
        }
    }

    @Test
    fun aJumpIsQuickAndNeverOvershoots() {
        var x = 0f
        var t = 0f
        // 60 frames per second
        while (t < 0.1f) { x = lookStep(x, 1f, 1f / 60f); t += 1f / 60f; assertTrue(x <= 1f) }
        assertTrue("after 100 ms x=$x", x > 0.95f) // a saccade lands within ~100 ms
        assertEquals(0.5f, lookStep(0.5f, 1f, 0f), 0f) // no time, no movement
        assertEquals(1f, lookStep(0f, 1f, 5f), 1e-3f) // after a long pause it simply arrives
    }
}
