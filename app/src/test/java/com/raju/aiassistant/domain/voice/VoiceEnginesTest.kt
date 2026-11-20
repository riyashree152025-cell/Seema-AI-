package com.raju.aiassistant.domain.voice

import com.raju.aiassistant.domain.model.TtsEngine.CARTESIA
import com.raju.aiassistant.domain.model.TtsEngine.EDGE
import com.raju.aiassistant.domain.model.TtsEngine.SARVAM
import org.junit.Assert.assertEquals
import org.junit.Test

/** Seema's three voices: Sarvam → Cartesia → Edge Neural, the free Edge voice always there as the last one. */
class VoiceEnginesTest {
    @Test
    fun automaticTriesTheTwoPaidVoicesThenEdge() {
        assertEquals(listOf(SARVAM, CARTESIA, EDGE), voiceEngineOrder(true, SARVAM, emptyMap()))
        assertEquals(listOf(SARVAM, CARTESIA, EDGE), voiceEngineOrder(true, EDGE, emptyMap())) // the choice matters only in Manual
    }

    @Test
    fun aVoiceOutOfCreditsMovesBehindTheOtherButEdgeStaysLast() {
        val sarvamFailing = mapOf(SARVAM to EngineStats(recentFailures = MAX_RECENT_FAILURES, lastError = "Quota exceeded"))
        assertEquals(listOf(CARTESIA, SARVAM, EDGE), voiceEngineOrder(true, SARVAM, sarvamFailing))
        val bothFailing = sarvamFailing + (CARTESIA to EngineStats(recentFailures = 3))
        assertEquals(EDGE, voiceEngineOrder(true, SARVAM, bothFailing).last()) // still tried: one success brings them back
    }

    @Test
    fun manualPutsTheChosenVoiceFirst() {
        assertEquals(listOf(EDGE, SARVAM, CARTESIA), voiceEngineOrder(false, EDGE, emptyMap()))
        assertEquals(listOf(CARTESIA, SARVAM, EDGE), voiceEngineOrder(false, CARTESIA, emptyMap()))
    }

    @Test
    fun aSuccessClearsTheFailuresInARow() {
        val s = EngineStats().withFailure("402").withFailure("402").withSuccess(0.5, 800)
        assertEquals(0, s.recentFailures)
        assertEquals(2, s.failures)
    }
}
