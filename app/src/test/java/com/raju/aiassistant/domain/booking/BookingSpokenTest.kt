package com.raju.aiassistant.domain.booking

import org.junit.Assert.assertEquals
import org.junit.Test

class BookingSpokenTest {
    @Test
    fun spokenClassesAndYearsAreUnderstood() {
        assertEquals("3A.", BookingNote.spokenClean("3 a."))
        assertEquals("3A", BookingNote.spokenClean("थ्री ए"))
        assertEquals("2A", BookingNote.spokenClean("सेकंड एसी"))
        assertEquals("3A", BookingNote.spokenClean("तीन एसी"))
        assertEquals("आनंद कुमार पटेल, 1986", BookingNote.spokenClean("आनंद कुमार पटेल, उन्नीस सौ छियासी"))
        assertEquals("पुणे से हावड़ा", BookingNote.spokenClean("पुणे से हावड़ा"))
    }
}
