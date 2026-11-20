package com.raju.aiassistant.domain.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FactExtractorTest {
    @Test
    fun relations() {
        assertEquals("Raju का bhai: Amit", FactExtractor.extract("mera bhai Amit hai"))
        assertEquals("Raju का wife: Priya", FactExtractor.extract("Seema, meri wife ka naam Priya hai"))
        assertEquals("Raju का पापा: रमेश जी", FactExtractor.extract("मेरे पापा का नाम रमेश जी है।"))
        assertEquals("Raju-র ভাই: অমিত", FactExtractor.extract("আমার ভাইয়ের নাম অমিত"))
        assertEquals("Raju-র বন্ধু: মিঠু", FactExtractor.extract("আমার বন্ধুর নাম মিঠু"))
        assertNull(FactExtractor.extract("আমার মা অসুস্থ"))
    }

    @Test
    fun likesAndDislikes() {
        assertEquals("Raju को पसंद है: adrak wali chai", FactExtractor.extract("mujhe adrak wali chai bahut pasand hai"))
        assertEquals("Raju को पसंद नहीं: karela", FactExtractor.extract("mujhe karela pasand nahi hai"))
        assertEquals("Raju-র ভালো লাগে: চা", FactExtractor.extract("আমার চা খুব ভালো লাগে"))
        assertEquals("Raju-র ভালো লাগে না: করলা", FactExtractor.extract("আমার করলা ভালো লাগে না"))
    }

    @Test
    fun questionsAndOrdinaryTalkAreNotFacts() {
        assertNull(FactExtractor.extract("mera bhai kaun hai?"))
        assertNull(FactExtractor.extract("mujhe kya pasand hai"))
        assertNull(FactExtractor.extract("mera phone kahan hai"))
        assertNull(FactExtractor.extract("Rahul ko call karo"))
        assertNull(FactExtractor.extract("mera bhai ghar nahi aaya hai"))
        assertNull(FactExtractor.extract("mera beta school mein hai"))
        assertNull(FactExtractor.extract("mera dost bimar hai"))
    }

    @Test
    fun subjectsMatchForReplacement() {
        assertEquals(FactExtractor.subject("Raju का bhai: Amit"), FactExtractor.subject("Raju का bhai: Sumit"))
        assertEquals(FactExtractor.subject("Raju को पसंद है: chai"), FactExtractor.subject("Raju को पसंद नहीं: Chai"))
        assertNotEquals(FactExtractor.subject("Raju को पसंद है: chai"), FactExtractor.subject("Raju को पसंद है: coffee"))
    }
}
