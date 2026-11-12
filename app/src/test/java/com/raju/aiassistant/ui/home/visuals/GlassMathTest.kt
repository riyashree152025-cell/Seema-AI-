package com.raju.aiassistant.ui.home.visuals

import com.raju.aiassistant.domain.model.AssistantState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassMathTest {
    @Test
    fun theLiquidRisesWithTheVoiceAndStaysInsideTheSphere() {
        val quiet = liquidLevel(AMBIENT_ENERGY, 0f, 1f)
        val loud = liquidLevel(1f, 0f, 1f)
        assertTrue(loud > quiet + 0.1f)
        for (t in 0..200) {
            val tt = t * 0.37f
            assertTrue(liquidLevel(1f, tt, 2.2f) in 0.3f..0.66f)
            for (x in -10..10) assertTrue(surfaceOffset(x / 10f, tt, 1f, 2.2f, 0.22f) in -0.3f..0.3f)
        }
    }

    @Test
    fun theLiquidIsAlwaysAliveAndLivelierWithTheVoice() {
        fun maxWave(life: Float) = (0..60).maxOf { t -> (-10..10).maxOf { kotlin.math.abs(surfaceOffset(it / 10f, t * 0.25f, life, 1f)) } }
        // Never a flat line, even with no measured voice (shared microphone off).
        assertTrue(maxWave(liquidLife(AssistantState.Idle, AMBIENT_ENERGY)) > 0.04f)
        assertTrue(maxWave(1f) > maxWave(0.1f) * 1.8f)
        assertTrue(liquidLife(AssistantState.Listening, AMBIENT_ENERGY) > liquidLife(AssistantState.Idle, AMBIENT_ENERGY))
        assertTrue(liquidLife(AssistantState.Sleeping, AMBIENT_ENERGY) < liquidLife(AssistantState.Idle, AMBIENT_ENERGY))
        // Tilting the phone tilts the surface one way, then the other.
        assertTrue(tiltSlope(5f) > 0f && tiltSlope(-5f) < 0f && tiltSlope(50f) == 0.22f)
    }

    @Test
    fun theAuroraAndStarsStayOnScreen() {
        for (i in 0 until 3) for (t in 0..100) for (x in 0..10) assertTrue(auroraY(i, x / 10f, t * 0.7f) in 0.08f..0.70f)
        for (i in 0 until 80) {
            val (x, y, b) = star(i, i * 0.3f)
            assertTrue(x in 0f..1f && y in 0f..0.85f && b in 0.35f..1f)
        }
        assertTrue(auroraColors(AssistantState.Speaking) != auroraColors(AssistantState.Listening))
    }

    @Test
    fun blobsStayInsideAndTheHighlightFollowsTheTilt() {
        for (t in 0..100) for (i in 0 until 4) {
            val (x, y) = blobCenter(i, 4, t * 0.5f, 2.2f)
            assertTrue(x in -0.6f..0.6f && y in -0.6f..0.6f)
        }
        val (flatX, _) = highlightShift(0f, 0f)
        assertEquals(0f, flatX, 1e-6f)
        val (leftX, _) = highlightShift(9.81f, 0f)
        assertTrue(leftX < 0f && leftX >= -0.07f)
        assertEquals(0.07f, highlightShift(-50f, 50f).first, 1e-6f) // clamped
    }

    @Test
    fun eachStateHasItsOwnLook() {
        assertNotEquals(glassPalette(AssistantState.Speaking), glassPalette(AssistantState.Listening))
        assertNotEquals(glassPalette(AssistantState.Idle), glassPalette(AssistantState.Sleeping))
        assertTrue(glassSpeed(AssistantState.Thinking) > glassSpeed(AssistantState.Idle))
        assertTrue(glassSpeed(AssistantState.Sleeping) < glassSpeed(AssistantState.Idle))
    }
}
