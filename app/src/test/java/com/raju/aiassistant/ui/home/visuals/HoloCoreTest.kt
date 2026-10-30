package com.raju.aiassistant.ui.home.visuals

import com.raju.aiassistant.domain.model.AssistantState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class HoloCoreTest {
    private fun Vec3.length() = sqrt(x * x + y * y + z * z)

    @Test
    fun rotationsKeepPointsOnTheirSphere() {
        val p = Vec3(0.3f, -0.5f, 0.8f)
        val q = p.rotateX(0.7f).rotateY(-1.3f).rotateZ(2.1f)
        assertEquals(p.length(), q.length(), 1e-4f)
    }

    @Test
    fun nearerPointsLookBiggerAndBrighter() {
        val front = project(Vec3(0.5f, 0f, 1f), 100f, 100f, 50f)
        val back = project(Vec3(0.5f, 0f, -1f), 100f, 100f, 50f)
        assertTrue(front.scale > back.scale)
        assertTrue(front.depth > back.depth)
        assertTrue(front.x > back.x) // perspective spreads near points further from the centre
        val centre = project(Vec3(0f, 0f, 0f), 100f, 100f, 50f)
        assertEquals(100f, centre.x, 1e-4f)
    }

    @Test
    fun wireframeAndRingsHaveTheRightShape() {
        val loops = sphereWireframe(latitudes = 5, meridians = 6, segments = 36)
        assertEquals(11, loops.size)
        loops.flatten().forEach { assertEquals(1f, it.length(), 1e-4f) }
        ringPoints(1.4f, 48).forEach { assertEquals(1.4f, it.length(), 1e-4f) }
    }

    @Test
    fun bothCoresReactToTheSameStatesTheSameWay() {
        AssistantState.entries.forEach { s ->
            val legacy = reactorStyleFor(s)
            val modern = holoCoreStyleFor(s)
            assertEquals(s.name, legacy.bpm, modern.bpm, 0f)
            assertEquals(s.name, legacy.rotationSpeed, modern.rotationSpeed, 0f)
            assertEquals(s.name, listOf(legacy.ripples, legacy.spinner, legacy.sweep, legacy.flicker, legacy.speechReactive),
                listOf(modern.ripples, modern.spinner, modern.sweep, modern.flicker, modern.speechReactive))
        }
        val styles = AssistantState.entries.map { holoCoreStyleFor(it) }
        assertEquals(styles.size, styles.toSet().size) // every state looks different
    }

    @Test
    fun theLegacyReactorIsTheOriginalOne() {
        // v1.6.0 colours: cyan, blue, violet, magenta while ready; it always turns and beats.
        val idle = reactorStyleFor(AssistantState.Idle)
        assertEquals(listOf(0xFF00E5FF, 0xFF2979FF, 0xFF9A7BFF, 0xFFFF4FD8, 0xFF00E5FF), idle.paletteArgb)
        assertTrue(idle.rotationSpeed > 0f && idle.bpm > 0f)
    }
}
