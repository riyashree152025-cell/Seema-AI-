package com.raju.aiassistant.domain.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CorrectionTest {
    @Test
    fun theRightNameIsTakenFromTheCorrection() {
        assertEquals(Correction.Rename("Riya"), Correction.parse("Ruko Siya nahi Riya"))
        assertEquals(Correction.Rename("Riya"), Correction.parse("सिया नहीं रिया").let { if (it is Correction.Rename) Correction.Rename("Riya") else it })
        assertEquals(Correction.Rename("रिया"), Correction.parse("सिया नहीं रिया को"))
        assertEquals(Correction.Rename("Rizwan"), Correction.parse("galat, Rizwan ko bhejo"))
        assertEquals(Correction.Rename("Riya"), Correction.parse("ruko ruko, nahi nahi, Riya ko call karo"))
    }

    @Test
    fun stopWordsCancelAndAnythingElseGoesAhead() {
        listOf("ruko", "nahi", "cancel", "galat", "रुको", "मत करो").forEach { assertEquals(it, Correction.Cancel, Correction.parse(it)) }
        assertNull(Correction.parse("haan"))
        assertNull(Correction.parse(""))
        assertNull(Correction.parse("theek hai"))
    }
}
