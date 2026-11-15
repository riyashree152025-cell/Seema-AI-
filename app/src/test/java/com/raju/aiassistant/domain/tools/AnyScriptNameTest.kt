package com.raju.aiassistant.domain.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnyScriptNameTest {
    private val contacts = listOf(
        "Riya", "Riya Sharma", "Priya", "Fiya", "Chhaya", "Zandu Balm", "Rizwan Travels", "Mummy", "रमेश भैया", "Khushi",
    ).mapIndexed { i, n -> Contact(n, "98765000${10 + i}") }

    private fun names(q: String) = ContactMatch.best(q, contacts).map { it.name }

    @Test
    fun hindiFindsTheNameSavedInEnglish() {
        assertEquals(listOf("Riya"), names("रिया"))
        assertEquals(listOf("Priya"), names("प्रिया"))
        assertEquals(listOf("Fiya"), names("फिया"))
        assertEquals(listOf("Chhaya"), names("छाया"))
        assertEquals(listOf("Zandu Balm"), names("झंडू बाम"))
        assertEquals(listOf("Khushi"), names("खुशी"))
        assertEquals(listOf("Riya Sharma"), names("रिया शर्मा"))
    }

    @Test
    fun bengaliAndEnglishFindTheOtherScriptToo() {
        assertEquals(listOf("Riya"), names("রিয়া"))
        assertEquals(listOf("रमेश भैया"), names("Ramesh Bhaiya"))
    }

    @Test
    fun similarNamesAreNotMixedUp() {
        assertTrue(!names("रिया").contains("Priya"))
        assertTrue(!names("प्रिया").contains("Riya"))
    }

    @Test
    fun romanizationKeepsTheVowels() {
        assertEquals("riya", Romanize.latin("रिया"))
        assertEquals("jhandu bam", Romanize.latin("झंडू बाम"))
        assertEquals("ram", Romanize.latin("राम"))
        assertEquals("ria", Romanize.phonetic("Riya"))
        assertEquals(Romanize.phonetic("Chhaya"), Romanize.phonetic("छाया"))
    }

    @Test
    fun appNamesSpokenInHindiFindTheApp() {
        assertEquals("Zomato", AppNameMatch.best("ज़ोमैटो", listOf("Zomato", "Swiggy")))
        assertEquals("PhonePe", AppNameMatch.best("फ़ोनपे", listOf("PhonePe", "Paytm")))
    }
}
