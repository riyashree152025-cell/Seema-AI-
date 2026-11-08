package com.raju.aiassistant.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RingDuckerTest {
    @Test
    fun theRingGoesToAboutAFifthButNeverToSilent() {
        assertEquals(3, duckedRingVolume(current = 15, max = 15, fraction = 0.2f))
        assertEquals(1, duckedRingVolume(current = 4, max = 4, fraction = 0.2f)) // never 0: that would mean silent mode
        assertNull(duckedRingVolume(current = 2, max = 15, fraction = 0.2f)) // already quiet: left alone
        assertNull(duckedRingVolume(current = 0, max = 15, fraction = 0.2f)) // silent stays silent
        assertNull(duckedRingVolume(current = 15, max = 15, fraction = 0.2f, ringerNormal = false)) // vibrate / silent / DND: untouched
    }
}
