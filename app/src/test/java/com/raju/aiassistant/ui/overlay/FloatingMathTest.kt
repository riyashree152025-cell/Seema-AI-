package com.raju.aiassistant.ui.overlay

import com.raju.aiassistant.domain.model.AssistantState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingMathTest {

    @Test
    fun appearsOnlyWhenEverythingAllowsIt() {
        assertTrue(mayShowFloating(enabled = true, paused = false, canDrawOverlays = true, micServiceRunning = true, appLocked = false))
        assertFalse(mayShowFloating(false, false, true, true, false)) // the user switched it off
        assertFalse(mayShowFloating(true, true, true, true, false)) // Seema is OFF
        assertFalse(mayShowFloating(true, false, false, true, false)) // no "display over other apps"
        assertFalse(mayShowFloating(true, false, true, false, false)) // no microphone service: she could not hear
        assertFalse(mayShowFloating(true, false, true, true, true)) // app lock not passed
    }

    @Test
    fun theCardOpensWhileSheWorksAndFoldsWhenQuiet() {
        listOf(AssistantState.Listening, AssistantState.Thinking, AssistantState.Executing, AssistantState.Speaking).forEach {
            assertTrue(opensCard(it, false))
            assertNull(foldAfterMs(it, false))
        }
        assertTrue(opensCard(AssistantState.Idle, hasPending = true)) // a question waits for "हाँ / नहीं"
        assertNull(foldAfterMs(AssistantState.Idle, hasPending = true))
        assertEquals(6_000L, foldAfterMs(AssistantState.Idle, false))
        assertEquals(6_000L, foldAfterMs(AssistantState.Success, false))
        assertTrue(foldAfterMs(AssistantState.Error, false)!! > 6_000L) // time to read the problem
    }

    @Test
    fun theLineSaysWhatIsHappening() {
        fun line(s: AssistantState, heard: String = "", pending: String? = null, reply: String? = null, error: String? = null, hideWords: Boolean = false) =
            floatingLine(s, heard, pending, reply, error, hideWords)
        assertEquals("मम्मी को कॉल करो", line(AssistantState.Listening, heard = "मम्मी को कॉल करो"))
        assertEquals("बोलिए, सुन रही हूँ…", line(AssistantState.Listening))
        assertEquals("मम्मी को कॉल करूँ?", line(AssistantState.Idle, pending = "मम्मी को कॉल करूँ?", reply = "पुराना"))
        assertEquals("ठीक है, लगा दिया।", line(AssistantState.Speaking, reply = "ठीक है,\n  लगा दिया।"))
        assertEquals("इंटरनेट नहीं मिला", line(AssistantState.Error, error = "इंटरनेट नहीं मिला", reply = "पुराना"))
        assertEquals("बात करने के लिए 🎤 दबाइए", line(AssistantState.Idle))
        // App lock: no words of the conversation on the screen.
        assertFalse(line(AssistantState.Speaking, reply = "गुप्त बात", hideWords = true).contains("गुप्त"))
        assertFalse(line(AssistantState.Idle, pending = "1500 रुपये भेजूँ?", hideWords = true).contains("1500"))
        val long = line(AssistantState.Speaking, reply = "अ".repeat(500))
        assertEquals(FLOATING_LINE_MAX, long.length)
        assertTrue(long.endsWith("…"))
        AssistantState.entries.forEach { assertTrue(floatingStatus(it).isNotBlank()) }
    }

    @Test
    fun theBubbleRestsOnTheNearerEdgeInsideTheScreen() {
        assertEquals(12, bubbleRestX(centerX = 300, screenWidth = 1080, bubbleWidth = 180, margin = 12))
        assertEquals(1080 - 180 - 12, bubbleRestX(centerX = 800, screenWidth = 1080, bubbleWidth = 180, margin = 12))
        assertEquals(100, bubbleRestY(-50, bubbleHeight = 180, top = 100, bottom = 2000))
        assertEquals(2000 - 180, bubbleRestY(5000, bubbleHeight = 180, top = 100, bottom = 2000))
        assertEquals(700, bubbleRestY(700, bubbleHeight = 180, top = 100, bottom = 2000))
    }
}
