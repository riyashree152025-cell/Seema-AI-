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
            for (x in -10..10) assertTrue(surfaceOffset(x / 10f, tt, 1f, 2.2f) in -0.16f..0.16f)
        }
        // Calm liquid barely moves; a loud voice makes real waves.
        val calm = (-10..10).maxOf { kotlin.math.abs(surfaceOffset(it / 10f, 1f, AMBIENT_ENERGY, 1f)) }
        val wavy = (-10..10).maxOf { kotlin.math.abs(surfaceOffset(it / 10f, 1f, 1f, 1f)) }
        assertTrue(calm < 0.04f && wavy > 0.06f)
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
