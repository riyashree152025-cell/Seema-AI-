package com.raju.aiassistant.domain.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HindiNumberParserTest {

    @Test
    fun parseSimpleDevanagariNumbers() {
        assertEquals(0L, HindiNumberParser.parseHindiNumber("शून्य"))
        assertEquals(1L, HindiNumberParser.parseHindiNumber("एक"))
        assertEquals(5L, HindiNumberParser.parseHindiNumber("पाँच"))
        assertEquals(10L, HindiNumberParser.parseHindiNumber("दस"))
        assertEquals(15L, HindiNumberParser.parseHindiNumber("पंद्रह"))
        assertEquals(50L, HindiNumberParser.parseHindiNumber("पचास"))
        assertEquals(85L, HindiNumberParser.parseHindiNumber("पचासी"))
        assertEquals(99L, HindiNumberParser.parseHindiNumber("निन्यानवे"))
        assertEquals(100L, HindiNumberParser.parseHindiNumber("सौ"))
    }

    @Test
    fun parseSimpleLatinNumbers() {
        assertEquals(1L, HindiNumberParser.parseHindiNumber("ek"))
        assertEquals(2L, HindiNumberParser.parseHindiNumber("do"))
        assertEquals(5L, HindiNumberParser.parseHindiNumber("paanch"))
        assertEquals(10L, HindiNumberParser.parseHindiNumber("das"))
        assertEquals(50L, HindiNumberParser.parseHindiNumber("pachaas"))
        assertEquals(85L, HindiNumberParser.parseHindiNumber("pachasi"))
        assertEquals(100L, HindiNumberParser.parseHindiNumber("sau"))
    }

    @Test
    fun parseMultipliersAndComplexNumbers() {
        assertEquals(7050L, HindiNumberParser.parseHindiNumber("सात हज़ार पचास"))
        assertEquals(7085L, HindiNumberParser.parseHindiNumber("सात हज़ार पचासी"))
        assertEquals(2500L, HindiNumberParser.parseHindiNumber("पच्चीस सौ"))
        assertEquals(1500L, HindiNumberParser.parseHindiNumber("डेढ़ हज़ार"))
        assertEquals(2500L, HindiNumberParser.parseHindiNumber("ढाई हज़ार"))
        assertEquals(3500L, HindiNumberParser.parseHindiNumber("साढ़े तीन हज़ार"))
        assertEquals(2250L, HindiNumberParser.parseHindiNumber("सवा दो हज़ार"))
        assertEquals(1750L, HindiNumberParser.parseHindiNumber("पौने दो हज़ार"))
        assertEquals(200000L, HindiNumberParser.parseHindiNumber("दो लाख"))
        assertEquals(10000000L, HindiNumberParser.parseHindiNumber("एक करोड़"))
    }

    @Test
    fun parseLatinComplexNumbers() {
        assertEquals(7050L, HindiNumberParser.parseHindiNumber("saat hazar pachaas"))
        assertEquals(7085L, HindiNumberParser.parseHindiNumber("saat hazar pachasi"))
        assertEquals(2500L, HindiNumberParser.parseHindiNumber("pacchis sau"))
        assertEquals(1500L, HindiNumberParser.parseHindiNumber("dedh hazar"))
        assertEquals(2500L, HindiNumberParser.parseHindiNumber("dhai hazar"))
        assertEquals(3500L, HindiNumberParser.parseHindiNumber("sadhe teen hazar"))
    }

    @Test
    fun replaceHindiNumbersInSentences() {
        val input1 = "राहुल से सात हज़ार पचास रुपये लेने बाकी हैं"
        val replaced1 = HindiNumberParser.replaceHindiNumbers(input1)
        assertEquals("राहुल से 7050 रुपये लेने बाकी हैं", replaced1)

        val input2 = "आनंद से सात हज़ार पचासी लेने हैं"
        val replaced2 = HindiNumberParser.replaceHindiNumbers(input2)
        assertEquals("आनंद से 7085 लेने हैं", replaced2)

        val input3 = "तारीकुल को पच्चीस सौ दे दिए"
        val replaced3 = HindiNumberParser.replaceHindiNumbers(input3)
        assertEquals("तारीकुल को 2500 दे दिए", replaced3)

        val input4 = "डेढ़ हज़ार का रिचार्ज करो"
        val replaced4 = HindiNumberParser.replaceHindiNumbers(input4)
        assertEquals("1500 का रिचार्ज करो", replaced4)
    }

    @Test
    fun ignoreNonNumbers() {
        assertNull(HindiNumberParser.parseHindiNumber("नमस्ते सीमा"))
        assertEquals("नमस्ते सीमा", HindiNumberParser.replaceHindiNumbers("नमस्ते सीमा"))
    }
}
