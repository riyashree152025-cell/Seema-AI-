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
        assertTrue(calm.max() - calm.min() > 0.02f) // micro-adjustments, never frozen
        assertTrue(pupilScale(1f, 1f, AssistantState.Idle) < pupilScale(1f, 0f, AssistantState.Idle))
        assertTrue(pupilScale(1f, 0f, AssistantState.Listening) > pupilScale(1f, 0f, AssistantState.Idle))
        assertEquals(0.26f, pupilScale(3f, 1f, AssistantState.Sleeping), 0f)
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
}
