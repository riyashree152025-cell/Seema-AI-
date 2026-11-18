package com.raju.aiassistant.domain.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeMemoryTest {
    @Test
    fun theRingVolumeIsSavedBeforeItIsLoweredAndGivenBackOnce() {
        val disk = InMemoryVolumeMemory()
        val p = RingDuckPolicy(disk)
        assertEquals(1, p.beginDuck(current = 12, target = 1))
        assertTrue(p.ducked)
        // A second ring while still lowered keeps the first, real volume (not the lowered one).
        assertNull(p.beginDuck(current = 1, target = 1))
        assertEquals(12, p.endDuck())
        assertNull(p.endDuck()) // given back once
        assertNull(p.beginDuck(current = 1, target = null)) // nothing to lower: nothing saved
        assertFalse(p.ducked)
    }

    @Test
    fun aKilledProcessStillGivesTheRingVolumeBackAtTheNextStart() {
        val disk = InMemoryVolumeMemory() // stands for the preferences file that outlives the process
        RingDuckPolicy(disk).beginDuck(current = 9, target = 1)
        // …the process dies mid-call; the app starts again with a new policy on the same disk:
        assertEquals(9, RingDuckPolicy(disk).endDuck())
    }

    @Test
    fun phoneDhundoGivesAlarmMusicAndRingBack() {
        val disk = InMemoryVolumeMemory()
        val loud = LoudModePolicy(disk)
        loud.remember(alarm = 3, music = 5, ring = 7)
        loud.remember(alarm = 15, music = 15, ring = 15) // already loud: the real values are kept
        assertEquals(Triple(3, 5, 7), LoudModePolicy(disk).restore())
        assertNull(loud.restore())
    }
}
