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
                org.junit.Assert.assertEquals(taporiAccentArgb(st), red.primaryArgb)
                org.junit.Assert.assertEquals(PALETTE_SIZE, red.paletteArgb.size)
                org.junit.Assert.assertEquals(normal.bpm, red.bpm, 0f) // same motion, only the colour changes
            }
        }
    }

    @Test
    fun taporiFaceChangesWithWhatSheIsDoing() {
        assertEquals(TaporiExpression.LISTEN, taporiExpressionFor(AssistantState.Listening))
        assertEquals(TaporiExpression.EYE_ROLL, taporiExpressionFor(AssistantState.Thinking))
        assertEquals(TaporiExpression.TALK, taporiExpressionFor(AssistantState.Speaking))
        assertEquals(TaporiExpression.WINK, taporiExpressionFor(AssistantState.Success))
        assertEquals(TaporiExpression.SLEEP, taporiExpressionFor(AssistantState.Sleeping))
        assertEquals(TaporiExpression.SMIRK, taporiExpressionFor(AssistantState.Idle))
    }

    @Test
    fun taporiColoursFollowWhatSheIsDoing() {
        assertEquals(0xFF00E5FF, taporiAccentArgb(AssistantState.Listening)) // cyan
        assertEquals(0xFF9D00FF, taporiAccentArgb(AssistantState.Thinking)) // purple
        assertEquals(0xFFFF2A2A, taporiAccentArgb(AssistantState.Speaking)) // red
        assertEquals(0x9900E5FF, taporiAccentArgb(AssistantState.Idle)) // calm cyan, 60 %
        assertEquals(0x9900E5FF, taporiAccentArgb(AssistantState.Sleeping))
    }

    @Test
    fun theRimShowsTheMoodOfHerReply() {
        assertEquals(TaporiMood.FUNNY, taporiMoodFor("Haha bhai, kya baat hai 😂"))
        assertEquals(TaporiMood.SHARP, taporiMoodFor("Arre bhai, hawabaazi band kar"))
        assertEquals(TaporiMood.NORMAL, taporiMoodFor("Dilli Bharat ki rajdhani hai."))
        assertEquals(TaporiMood.CALM, taporiMoodFor(null))
        assertEquals(0xFFFF6A00, taporiRimArgb(AssistantState.Speaking, TaporiMood.SHARP))
        assertEquals(0xFFFF4FD8, taporiRimArgb(AssistantState.Speaking, TaporiMood.FUNNY))
        assertEquals(0xFF00E5FF, taporiRimArgb(AssistantState.Listening, TaporiMood.SHARP))
        assertEquals(taporiAccentArgb(AssistantState.Idle), taporiRimArgb(AssistantState.Idle, TaporiMood.FUNNY))
    }
}
