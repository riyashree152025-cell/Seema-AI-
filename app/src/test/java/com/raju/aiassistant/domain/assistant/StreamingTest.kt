package com.raju.aiassistant.domain.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamingTest {
    @Test
    fun theFirstCompleteSentenceCanBeSpokenEarly() {
        assertNull(speakableHead("Aaj Delhi mein mausam"))
        assertNull(speakableHead("Haan. Aaj")) // too short to be worth a pause
        assertEquals("Aaj Delhi mein dhoop hai.", speakableHead("Aaj Delhi mein dhoop hai. Shaam ko"))
        assertEquals("आज दिल्ली में धूप है।", speakableHead("आज दिल्ली में धूप है। शाम को"))
        // 3.5 is not a sentence end, and nothing is taken from an action tag.
        assertEquals("Kal 3.5 degree tak thand hogi!", speakableHead("Kal 3.5 degree tak thand hogi! Aur"))
        assertNull(speakableHead("Call lagati hoon <action>{\"type\":\"call\". \"x\"}"))
    }

    @Test
    fun theRestIsWhatComesAfterTheSpokenHead() {
        assertEquals("Shaam ko baarish.", remainderAfter("Aaj dhoop hai. Shaam ko baarish.", "Aaj dhoop hai."))
        assertEquals("", remainderAfter("Aaj dhoop hai.", "Aaj dhoop hai."))
        assertEquals("Naya jawab", remainderAfter("Naya jawab", "Kuch aur."))
    }

    @Test
    fun percentilesUseTheNearestRank() {
        val ms = listOf(900L, 1200L, 800L, 3000L, 1000L, 1100L, 950L, 1050L, 1150L, 2000L)
        assertEquals(1050L, percentile(ms, 50))
        assertEquals(3000L, percentile(ms, 95))
        assertNull(percentile(emptyList(), 50))
        assertEquals("P50 1.1 s · P95 3.0 s (10 replies)", latencySummary(ms))
    }
}
