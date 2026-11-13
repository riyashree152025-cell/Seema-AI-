package com.raju.aiassistant.domain.language

import org.junit.Assert.assertEquals
import org.junit.Test

class IndicScriptTest {
    @Test
    fun hindiWrittenInAnotherIndianScriptBecomesDevanagari() {
        // What the phone showed in the chat for "गीता कामत्कु".
        assertEquals("गीता कामत्कु", toDevanagari("గీతా కామత్కు"))
        assertEquals("राम", toDevanagari("ರಾಮ")) // Kannada
        assertEquals("नमस्ते", toDevanagari("નમસ્તે")) // Gujarati
    }

    @Test
    fun otherTextIsUnchanged() {
        assertEquals("anand ka tatkal booking", toDevanagari("anand ka tatkal booking"))
        assertEquals("सीमा, टॉर्च जलाओ", toDevanagari("सीमा, टॉर्च जलाओ"))
        assertEquals("আমি ভালো আছি", toDevanagari("আমি ভালো আছি")) // Bengali stays Bengali
    }
}
