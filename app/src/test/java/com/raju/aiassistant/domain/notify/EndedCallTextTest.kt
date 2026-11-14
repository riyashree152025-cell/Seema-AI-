package com.raju.aiassistant.domain.notify

import org.junit.Assert.assertEquals
import org.junit.Test

class EndedCallTextTest {
    @Test
    fun aCallThatEndedSaysSoAndNeverAsksToAnswer() {
        assertEquals("Mummy ka call aaya tha, ab kat chuka hai.", endedCallText("Mummy"))
        assertEquals("Call aaya tha, ab kat chuka hai.", endedCallText(null))
        assertEquals("Call aaya tha, ab kat chuka hai.", endedCallText("  "))
        assertEquals(false, endedCallText("Mummy").contains(ASK_TO_ANSWER))
    }
}
