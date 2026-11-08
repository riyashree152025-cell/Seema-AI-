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
        var now = 0L
        val gate = WakeCheckGate({ now })
        assertEquals(WakeCheckGate.Decision.TOO_QUIET, gate.decide(clip(100)))
        assertEquals(WakeCheckGate.Decision.TOO_QUIET, gate.decide(ByteArray(0)))
        assertEquals(WakeCheckGate.Decision.CHECK, gate.decide(clip(2_000)))
        // One loud 100 ms burst in a quiet clip counts (a short "Seema").
        val mixed = clip(50) + clip(3_000, samples = 1_600)
        assertTrue(loudestLevel(mixed) > WAKE_ENERGY_THRESHOLD)
    }

    @Test
    fun repeatedNoiseBacksOffAndHerNameResetsIt() {
        var now = 0L
        val gate = WakeCheckGate({ now })
        repeat(WAKE_MISSES_BEFORE_BACKOFF) { now += 1_000; gate.onChecked(nameHeard = false) }
        assertEquals(WakeCheckGate.Decision.BACKING_OFF, gate.decide(clip(3_000)))
        now += 5_001
        assertEquals(WakeCheckGate.Decision.CHECK, gate.decide(clip(3_000)))
        gate.onChecked(nameHeard = false) // one more miss in a row: a longer pause
        now += 6_000
        assertEquals(WakeCheckGate.Decision.BACKING_OFF, gate.decide(clip(3_000)))
        now += 5_000
        assertEquals(WakeCheckGate.Decision.CHECK, gate.decide(clip(3_000)))
        gate.onChecked(nameHeard = true)
        gate.onChecked(nameHeard = false)
        assertEquals(WakeCheckGate.Decision.CHECK, gate.decide(clip(3_000))) // the name reset the count
        assertEquals(10_000L, WakeCheckGate.backoffMs(20)) // never longer than 10 s
    }

    @Test
    fun aQuietRoomForgetsOldMisses() {
        var now = 0L
        val gate = WakeCheckGate({ now })
        repeat(WAKE_MISSES_BEFORE_BACKOFF - 1) { now += 1_000; gate.onChecked(false) }
        now += WAKE_MISS_MEMORY_MS + 1
        gate.onChecked(false)
        assertEquals(WakeCheckGate.Decision.CHECK, gate.decide(clip(3_000)))
    }
}
