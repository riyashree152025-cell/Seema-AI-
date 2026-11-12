package com.raju.aiassistant.domain.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTranscriptTest {
    private fun result(text: String, final: Boolean, confidence: Double = 0.9) =
        """{"type":"Results","is_final":$final,"speech_final":false,"channel":{"alternatives":[{"transcript":"$text","confidence":$confidence}]}}"""

    @Test
    fun finalPiecesAreKeptAndTheLatestGuessIsShown() {
        val t = LiveTranscript()
        assertTrue(t.onMessage(result("delhi ka", final = false)))
        assertEquals("delhi ka", t.shown())
        assertEquals("", t.text())
        assertTrue(t.onMessage(result("Delhi ka mausam", final = true)))
        assertTrue(t.onMessage(result("batao", final = false)))
        assertEquals("Delhi ka mausam batao", t.shown())
        t.onMessage(result("batao.", final = true))
        assertEquals("Delhi ka mausam batao.", t.text())
    }

    @Test
    fun otherMessagesAndEmptyPiecesChangeNothing() {
        val t = LiveTranscript()
        assertFalse(t.onMessage("""{"type":"Metadata","request_id":"x"}"""))
        assertFalse(t.onMessage("""{"type":"UtteranceEnd"}"""))
        assertFalse(t.onMessage("not json"))
        assertFalse(t.onMessage(result("", final = true)))
        assertEquals("", t.text())
        assertEquals(1.0, t.confidence(), 0.0)
    }

    @Test
    fun confidenceIsWeightedByLength() {
        val t = LiveTranscript()
        t.onMessage(result("ek bahut lambi baat", final = true, confidence = 0.9))
        t.onMessage(result("haan", final = true, confidence = 0.3))
        assertTrue(t.confidence() > 0.75)
    }
}
