package com.raju.aiassistant.domain.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeCheckGateTest {
    private fun clip(amplitude: Int, samples: Int = 4_800) = ByteArray(samples * 2) { i ->
        if (i % 2 == 0) (amplitude and 0xFF).toByte() else (amplitude shr 8).toByte()
    }

    @Test
    fun faintSoundsAreNeverSentOnline() {
        val gate = WakeCheckGate()
        assertEquals(WakeCheckGate.Decision.TOO_QUIET, gate.decide(clip(100)))
        assertEquals(WakeCheckGate.Decision.TOO_QUIET, gate.decide(ByteArray(0)))
        assertEquals(WakeCheckGate.Decision.CHECK, gate.decide(clip(2_000)))
        // One loud 100 ms burst in a quiet clip counts (a short "Seema").
        val mixed = clip(50) + clip(3_000, samples = 1_600)
        assertTrue(loudestLevel(mixed) > WAKE_ENERGY_THRESHOLD)
    }

    @Test
    fun noiseNeverPausesChecksButRaisesTheBarAboveTheRoom() {
        val gate = WakeCheckGate()
        // A TV / fan at RMS 400 heard again and again: never "paused", a loud call is always checked.
        repeat(20) { gate.decide(clip(400)) }
        assertEquals(WakeCheckGate.Decision.CHECK, gate.decide(clip(400) + clip(3_000, samples = 1_600)))
        // The room is now ~400 RMS (0.08): the steady TV alone no longer passes (bar = room + 35 RMS) ...
        assertEquals(WakeCheckGate.Decision.TOO_QUIET, gate.decide(clip(400)))
        // ... but "Hey Seema" 50 RMS above it does.
        assertEquals(WakeCheckGate.Decision.CHECK, gate.decide(clip(400) + clip(450, samples = 1_600)))
    }

    @Test
    fun aQuietRoomKeepsTheFloorOf110() {
        val gate = WakeCheckGate()
        repeat(10) { gate.decide(clip(20)) }
        assertEquals(WakeCheckGate.Decision.TOO_QUIET, gate.decide(clip(20) + clip(100, samples = 1_600)))
        assertEquals(WakeCheckGate.Decision.CHECK, gate.decide(clip(20) + clip(160, samples = 1_600)))
    }
}
