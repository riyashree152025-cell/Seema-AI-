package com.raju.aiassistant.domain.tools

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneBeaconTest {
    private class FakeHardware(val canSound: Boolean = true) : BeaconHardware {
        var loud = 0
        var restored = 0
        var chimes = 0
        val signals = MutableSharedFlow<BeaconStop>()
        override fun loud(): Boolean { loud++; return canSound }
        override fun restore() { restored++ }
        override suspend fun chime() { chimes++ }
        override fun foundSignals(): Flow<BeaconStop> = signals
    }

    @Test
    fun itChimesWithASilentGapAndSpeaksOnlyAtTheStart() = runTest {
        val hw = FakeHardware()
        val said = mutableListOf<String>()
        val beacon = PhoneBeacon(backgroundScope, { said += it })
        assertTrue(beacon.start(hw, "Raju ji, phone yahan hai!"))
        // Each cycle is a chime then a silent window (~3.8 s): chimes at 0, 3.8, 7.6, 11.4, 15.2 s.
        advanceTimeBy(16_000); runCurrent()
        assertEquals(5, hw.chimes)
        assertEquals(1, said.size) // the line is said once, at the start, so the mic can hear "band karo"
        assertTrue(beacon.active.value)
        beacon.stop()
        runCurrent()
        assertFalse(beacon.active.value)
        assertEquals(1, hw.restored)
    }

    @Test
    fun pickingUpUnlockingOrAVolumeKeyStopsItAndPutsTheVolumeBack() = runTest {
        for (signal in listOf(BeaconStop.PICKED_UP, BeaconStop.UNLOCKED, BeaconStop.VOLUME_KEY)) {
            val hw = FakeHardware()
            var stopped: BeaconStop? = null
            val beacon = PhoneBeacon(backgroundScope, {}, onStopped = { stopped = it })
            beacon.start(hw, "x")
            advanceTimeBy(3_000); runCurrent()
            hw.signals.emit(signal)
            runCurrent()
            val chimes = hw.chimes
            advanceTimeBy(10_000); runCurrent()
            assertEquals(chimes, hw.chimes) // silent from the moment it was found
            assertEquals(signal, stopped)
            assertEquals(1, hw.restored)
            assertFalse(beacon.active.value)
        }
    }

    @Test
    fun itGivesUpAfterThreeMinutesAndNeverRunsTwice() = runTest {
        val hw = FakeHardware()
        var stopped: BeaconStop? = null
        val beacon = PhoneBeacon(backgroundScope, {}, onStopped = { stopped = it })
        beacon.start(hw, "x")
        beacon.start(hw, "x") // a second "phone dhundo" while ringing: still one beacon
        assertEquals(1, hw.loud)
        advanceTimeBy(3 * 60_000 + 1_000); runCurrent()
        assertEquals(BeaconStop.TIMEOUT, stopped)
        assertEquals(1, hw.restored)
    }

    @Test
    fun withoutSoundItDoesNotStart() = runTest {
        val beacon = PhoneBeacon(backgroundScope, {})
        assertFalse(beacon.start(FakeHardware(canSound = false), "x"))
        assertFalse(beacon.active.value)
    }

    @Test
    fun foundPhrases() {
        listOf("mil gaya", "Mil gaya Seema", "मिल गया", "bas ruk jao", "stop", "পেয়েছি").forEach { assertTrue(it, saysPhoneFound(it)) }
        listOf("kahan hai", "phone dhundo", "बजाओ").forEach { assertFalse(it, saysPhoneFound(it)) }
    }
}
