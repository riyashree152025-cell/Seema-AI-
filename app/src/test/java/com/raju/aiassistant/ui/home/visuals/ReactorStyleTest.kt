package com.raju.aiassistant.ui.home.visuals

import com.raju.aiassistant.domain.model.AssistantState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactorStyleTest {
    @Test
    fun everyStateHasADistinctLook() {
        val styles = AssistantState.entries.map { reactorStyleFor(it) }
        assertEquals(styles.size, styles.toSet().size)
    }

    @Test
    fun stateSpecificEffects() {
        assertTrue(reactorStyleFor(AssistantState.Listening).ripples)
        assertTrue(reactorStyleFor(AssistantState.Thinking).spinner)
        assertTrue(reactorStyleFor(AssistantState.Speaking).speechReactive)
        assertTrue(reactorStyleFor(AssistantState.Executing).sweep)
        assertTrue(reactorStyleFor(AssistantState.Error).flicker)
    }

    @Test
    fun offlineReactorIsStillAndDim() {
        val offline = reactorStyleFor(AssistantState.Offline)
        assertEquals(0f, offline.rotationSpeed)
        assertTrue(offline.glow < reactorStyleFor(AssistantState.Idle).glow)
    }

    @Test
    fun everyPaletteHasTheExpectedSize() {
        AssistantState.entries.forEach { assertEquals(PALETTE_SIZE, reactorStyleFor(it).paletteArgb.size) }
    }

    @Test
    fun reactorBeatsFasterWhenBusy() {
        assertTrue(reactorStyleFor(AssistantState.Thinking).bpm > reactorStyleFor(AssistantState.Idle).bpm)
        assertTrue(reactorStyleFor(AssistantState.Offline).bpm < reactorStyleFor(AssistantState.Idle).bpm)
    }

    @Test
    fun everyStateHasAHint() {
        AssistantState.entries.forEach { assertTrue(statusHintFor(it).isNotBlank()) }
    }

    @Test
    fun taporiModeTurnsEveryStateRedButOfflineStaysGrey() {
        for (st in AssistantState.entries) {
            val normal = reactorStyleFor(st)
            val red = normal.tapori(st)
            if (st == AssistantState.Offline) {
                org.junit.Assert.assertEquals(normal, red)
            } else {
                org.junit.Assert.assertEquals(0xFFFF1E3C, red.primaryArgb)
                org.junit.Assert.assertEquals(PALETTE_SIZE, red.paletteArgb.size)
                org.junit.Assert.assertEquals(normal.bpm, red.bpm, 0f) // same motion, only the colour changes
            }
        }
    }
}
