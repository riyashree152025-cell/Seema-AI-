package com.raju.aiassistant.domain.language

import org.junit.Assert.assertEquals
import org.junit.Test

class TransliterateTest {
    @Test
    fun hindiIsWrittenTheWayHinglishIsTyped() {
        assertEquals("dada pement nahin aaya", Transliterate.toLatin("दादा पेमेंट नहीं आया"))
        assertEquals("ram", Transliterate.toLatin("राम"))
        assertEquals("traivals", Transliterate.toLatin("ट्रैवल्स"))
    }

    @Test
    fun nuktaLettersAndMixedTextWork() {
        assertEquals("zaroorat", Transliterate.toLatin("ज़रूरत"))
        assertEquals("rijavan Travels", Transliterate.toLatin("रिजवान Travels"))
        assertEquals("Hello 123", Transliterate.toLatin("Hello १२३"))
    }
}
