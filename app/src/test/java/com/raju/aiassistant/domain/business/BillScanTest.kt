package com.raju.aiassistant.domain.business

import com.raju.aiassistant.domain.assistant.AssistantAction
import com.raju.aiassistant.domain.assistant.LocalCommands
import com.raju.aiassistant.domain.language.SpeechCorrections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BillScanTest {
    @Test
    fun asksAndReadsTheBill() {
        assertTrue(BillScan.asks("bill ki photo lo aur kharcha likho"))
        assertTrue(BillScan.asks("इस बिल का खर्चा खाते में डालो"))
        assertEquals(AssistantAction.BillScanAsk, LocalCommands.parse("bill ki photo lo aur kharcha likho"))
        assertEquals(ScannedBill(124_050, "Sharma Store", "food"), BillScan.parse("```json\n{\"total\": 1240.5, \"payee\": \"Sharma Store\", \"category\": \"food\"}\n```"))
        assertEquals(ScannedBill(99_900, null, "other"), BillScan.parse("{\"total\": \"₹999\", \"payee\": null, \"category\": \"misc\"}"))
        assertNull(BillScan.parse("{\"total\": null, \"payee\": \"X\"}"))
        assertNull(BillScan.parse("I cannot read it"))
        assertEquals("₹1,240 का खर्च (Sharma Store) खाते में लिख दूँ?", BillScan.question(ScannedBill(124_000, "Sharma Store", "food")))
    }

    @Test
    fun taughtWords() {
        assertEquals("mitthu" to "Mitu", SpeechCorrections.teach("jab main mitthu bolun to Mitu samajhna"))
        assertEquals("मिट्ठू" to "Mitu", SpeechCorrections.teach("मिट्ठू का मतलब Mitu है"))
        assertNull(SpeechCorrections.teach("iska matlab kya hai"))
        val fact = SpeechCorrections.factText("mitthu", "Mitu")
        assertEquals("mitthu" to "Mitu", SpeechCorrections.fromFact(fact))
        assertEquals("Mitu ko call karo", SpeechCorrections.apply("Mitthu ko call karo", listOf("mitthu" to "Mitu")))
        assertEquals("mitthuwa", SpeechCorrections.apply("mitthuwa", listOf("mitthu" to "Mitu"))) // whole words only
        assertTrue(LocalCommands.parse("jab main mitthu bolun to Mitu samajhna") is AssistantAction.TeachWord)
    }
}
