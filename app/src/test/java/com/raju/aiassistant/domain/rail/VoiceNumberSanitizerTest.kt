package com.raju.aiassistant.domain.rail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceNumberSanitizerTest {

    @Test
    fun spacesAndHyphensAreRemoved() {
        assertEquals("6211912638", VoiceNumberSanitizer.pnr("6211 9126 38").getOrThrow())
        assertEquals("6211912638", VoiceNumberSanitizer.pnr("6211-9126-38").getOrThrow())
        assertEquals("12345", VoiceNumberSanitizer.trainNumber("12345").getOrThrow())
    }

    @Test
    fun spokenDigitWordsBecomeDigits() {
        assertEquals("6211912638", VoiceNumberSanitizer.pnr("six two one one nine one two six three eight").getOrThrow())
        assertEquals("12345", VoiceNumberSanitizer.trainNumber("एक दो तीन चार पाँच").getOrThrow())
    }

    @Test
    fun doubleAndTripleRepeatTheNextDigit() {
        assertEquals("11", VoiceNumberSanitizer.digits("double one"))
        assertEquals("000", VoiceNumberSanitizer.digits("triple zero"))
        assertEquals("6211912638", VoiceNumberSanitizer.pnr("62 double one 9 one 2 six 3 eight").getOrThrow())
    }

    @Test
    fun devanagariDigitsAreAccepted() {
        assertEquals("12345", VoiceNumberSanitizer.trainNumber("१२३४५").getOrThrow())
    }

    @Test
    fun aWrongCountIsRefusedWithTheCountSaid() {
        val bad = VoiceNumberSanitizer.pnr("6211 9126")
        assertTrue(bad.isFailure)
        assertTrue(bad.exceptionOrNull()!!.message!!.contains("8 अंक"))
        assertFalse(VoiceNumberSanitizer.trainNumber("123456").isSuccess)
    }
}
