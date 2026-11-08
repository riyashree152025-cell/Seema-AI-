package com.raju.aiassistant.ui.home.visuals

import com.raju.aiassistant.domain.model.AssistantState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class LivingEyeMathTest {
    @Test
    fun theEyeBreathesEveryTwoToThreeSeconds() {
        assertEquals(1f, eyeBreath(0f), 1e-4f)
        assertEquals(1.035f, eyeBreath(EYE_BREATH_SECONDS / 4), 1e-3f)
        assertEquals(eyeBreath(0.7f), eyeBreath(0.7f + EYE_BREATH_SECONDS), 1e-4f)
        assertTrue(EYE_BREATH_SECONDS in 2f..3f)
    }

    @Test
    fun thePupilMovesALittleAndReactsToSound() {
        val calm = (0 until 50).map { pupilScale(it * 0.1f, 0f, AssistantState.Idle) }
        assertTrue(calm.max() - calm.min() > 0.015f) // micro-adjustments, never frozen
        assertTrue(pupilScale(1f, 1f, AssistantState.Idle) < pupilScale(1f, 0f, AssistantState.Idle))
        assertTrue(pupilScale(1f, 0f, AssistantState.Listening) > pupilScale(1f, 0f, AssistantState.Idle))
        assertEquals(0.18f, pupilScale(3f, 1f, AssistantState.Sleeping), 0f)
        assertEquals(PUPIL_SHARE, pupilScale(0f, 0f, AssistantState.Idle), 0.02f) // pupil ≈ 0.24 of the eye
    }

    @Test
    fun spikesAndRadarOnlyWhileWorking() {
        assertTrue(eyeSpikesActive(AssistantState.Speaking) && eyeSpikesActive(AssistantState.Thinking))
        assertFalse(eyeSpikesActive(AssistantState.Idle) || eyeSpikesActive(AssistantState.Sleeping))
        assertTrue(eyeRadarActive(AssistantState.Thinking))
        assertFalse(eyeRadarActive(AssistantState.Speaking))
        assertTrue(eyeSpinSpeed(AssistantState.Thinking) > eyeSpinSpeed(AssistantState.Listening))
    }

    @Test
    fun wavesFollowTheVoiceAndTheMicAndNeverStop() {
        assertTrue(waveEnergy(AssistantState.Speaking, 1f, 0f) > waveEnergy(AssistantState.Speaking, 0f, 0f))
        assertTrue(waveEnergy(AssistantState.Listening, 0f, 0.8f) > waveEnergy(AssistantState.Listening, 0f, 0f))
        assertEquals(waveEnergy(AssistantState.Speaking, 0.5f, 0f), waveEnergy(AssistantState.Speaking, 0.5f, 1f), 0f) // mic ignored while she speaks
        AssistantState.entries.forEach { assertTrue(it.name, waveEnergy(it, 0f, 0f) > 0f) }
        // The idle wave still moves: the same point differs a moment later.
        assertTrue(abs(waveShape(0.3f, 0f, 0, AMBIENT_ENERGY) - waveShape(0.3f, 0.5f, 0, AMBIENT_ENERGY)) > 0.01f)
        // Reaches the edges and stays in range.
        for (x in listOf(0f, 0.5f, 1f)) for (l in 0 until 4) assertTrue(abs(waveShape(x, 1.2f, l, 1f)) <= 1f)
        assertTrue((0..20).any { abs(waveShape(0f, it * 0.3f, 0, 0.5f)) > 0.1f })
    }

    @Test
    fun energySwellsFastAndFadesSlowly() {
        val up = smoothEnergy(0f, 1f)
        val down = smoothEnergy(1f, 0f)
        assertTrue(up > 1f - down) // rises faster than it falls
    }

    @Test
    fun theEyeFillsTheViewportAndTheWavesReachTheEdges() {
        assertEquals(0.40f, EYE_RADIUS_SHARE, 1e-4f)
        // The edge of the screen still moves with real amplitude, compared with the centre.
        val edge = (0..400).maxOf { abs(waveShape(0f, it * 0.05f, 0, 0.5f)) }
        val centre = (0..400).maxOf { abs(waveShape(0.5f, it * 0.05f, 0, 0.5f)) }
        assertTrue(edge > 0.5f * centre)
    }

    @Test
    fun lessDetailInBatterySaver() {
        assertTrue(waveLayerCount(false) < waveLayerCount(true))
        assertTrue(wavePointCount(false) < wavePointCount(true))
        assertTrue(irisLineCount(false) < irisLineCount(true))
        assertEquals(3, WAVE_GRADIENT_ARGB.size)
    }

    @Test
    fun theEyeLooksAroundButStraightAtYouWhileListening() {
        assertEquals(0f to 0f, eyeGaze(5f, AssistantState.Listening))
        val idle = (0 until 60).map { eyeGaze(it * 0.5f, AssistantState.Idle) }
        assertTrue(idle.distinct().size > 30) // always moving a little, never frozen
        AssistantState.entries.forEach { s ->
            (0 until 40).forEach { k ->
                val (x, y) = eyeGaze(k * 0.7f, s)
                assertTrue(s.name, abs(x) <= 0.1f && abs(y) <= 0.1f) // small: the iris stays inside the eye
            }
        }
    }

    @Test
    fun thePupilBreathesWithTheVoiceBetween094And106() {
        assertEquals(0.94f, pupilDilation(0f), 1e-4f)
        assertEquals(1.06f, pupilDilation(1f), 1e-4f)
        assertTrue(pupilScale(2f, 1f, AssistantState.Speaking) > pupilScale(2f, 0f, AssistantState.Speaking))
        assertTrue(pupilScale(2f, 1f, AssistantState.Listening) > pupilScale(2f, 0f, AssistantState.Listening))
    }

    @Test
    fun microSaccadesJumpQuicklyThenRest() {
        val start = SACCADE_EVERY_S * 3
        val a = microSaccade(start + 0.2f)
        val b = microSaccade(start + 1.0f)
        assertEquals(a.first, b.first, 1e-5f) // resting between jumps
        val before = microSaccade(start - 0.01f)
        val after = microSaccade(start + SACCADE_JUMP_S + 0.01f)
        assertTrue(before != after) // a jump happened in under 0.1 s
        (0 until 200).forEach { k -> val (x, y) = microSaccade(k * 0.05f); assertTrue(abs(x) <= 0.031f && abs(y) <= 0.031f) }
    }

    @Test
    fun theEyeTurnsTowardsATouchThenComesBack() {
        val (x, y, w) = touchGaze(dx = 2f, dy = 0f, secondsSinceTouch = 1f)
        assertTrue(x > 0f && abs(y) < 1e-5f && w == 1f)
        assertTrue(x <= TOUCH_GAZE_MAX)
        assertEquals(0f, touchGaze(2f, 0f, TOUCH_GAZE_HOLD_S + 1f).third, 0f)
        assertTrue(touchGaze(2f, 0f, 0.05f).third < 1f) // turns smoothly, not instantly
    }

    @Test
    fun theBiColourIrisIsLaidOutAsDesigned() {
        assertTrue(PUPIL_SHARE < BLUE_INNER_SHARE && BLUE_INNER_SHARE < HAZEL_OUTER_SHARE && HAZEL_OUTER_SHARE < LIMBUS_SHARE)
        assertEquals(1.0f, INNER_PARALLAX, 0f)
        assertEquals(0.65f, OUTER_PARALLAX, 0f)
        assertEquals(120, irisLineCount(true))
        assertEquals(1.5f, SACCADE_EVERY_S, 0f)
    }
}
