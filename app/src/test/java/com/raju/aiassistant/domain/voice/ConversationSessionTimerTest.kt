package com.raju.aiassistant.domain.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationSessionTimerTest {

    @Test
    fun strictlySevenSecondFollowUpWindow() {
        var currentTime = 100_000L
        val timer = ConversationSessionTimer { currentTime }

        // Initially closed
        assertFalse(timer.isOpen())
        assertEquals(0L, timer.until())

        // Reply finishes: opens strictly for 7 seconds (WINDOW_MS)
        timer.openAfterReply()
        assertTrue(timer.isOpen())
        assertEquals(107_000L, timer.until())

        // At 6.999s, still open
        currentTime = 106_999L
        assertTrue(timer.isOpen())

        // At 7.000s, window has closed
        currentTime = 107_000L
        assertFalse(timer.isOpen())

        // At 7.001s, closed
        currentTime = 107_001L
        assertFalse(timer.isOpen())
    }

    @Test
    fun speechStartedInsideWindowAcceptedWithinGracePeriod() {
        var currentTime = 1_000L
        val timer = ConversationSessionTimer { currentTime }
        timer.openAfterReply() // open until 8_000L

        // Speech started at 5_000L (inside 7s window)
        // Recognized at 9_000L (window closed, but within 6s grace period)
        currentTime = 9_000L
        assertFalse(timer.isOpen())
        assertTrue(timer.accepts(listenStartedAt = 5_000L))

        // Speech started at 8_100L (after 7s window closed) -> rejected
        assertFalse(timer.accepts(listenStartedAt = 8_100L))

        // Speech started inside window, but result arrived after grace period (e.g. at 15_000L) -> rejected
        currentTime = 15_000L
        assertFalse(timer.accepts(listenStartedAt = 5_000L))
    }

    @Test
    fun immediateCancellationOnOtherAssistantOrSleepCommand() {
        var currentTime = 10_000L
        val timer = ConversationSessionTimer { currentTime }
        timer.openAfterReply()
        assertTrue(timer.isOpen())

        // Raju speaks to another assistant: "Hey Google", "Gemini", "Alexa", "Siri"
        val checkVerdict = WakeWordGate.check("Gemini", followUpOpen = timer.isOpen())
        assertEquals(WakeWordGate.Verdict.OtherAssistant, checkVerdict)

        // Cancel / close timer immediately
        timer.close()
        assertFalse(timer.isOpen())
        assertEquals(0L, timer.until())
        assertFalse(timer.accepts(listenStartedAt = 10_500L))

        // Re-open and cancel using cancel()
        timer.openAfterReply()
        assertTrue(timer.isOpen())
        val checkGoogle = WakeWordGate.check("Hey Google", followUpOpen = timer.isOpen())
        assertEquals(WakeWordGate.Verdict.OtherAssistant, checkGoogle)
        timer.cancel()
        assertFalse(timer.isOpen())
        assertEquals(0L, timer.until())
    }

    @Test
    fun longerQuestionWindowIsNotShortenedBySevenSeconds() {
        var currentTime = 1_000L
        val timer = ConversationSessionTimer { currentTime }
        timer.openFor(30_000L) // question asking for longer answer, open until 31_000L
        timer.openAfterReply() // 7s from 1_000 is 8_000L, which is less than 31_000L
        currentTime = 25_000L
        assertTrue(timer.isOpen())
    }
}
