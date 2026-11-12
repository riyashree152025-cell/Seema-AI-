package com.raju.aiassistant.domain.memory

import com.raju.aiassistant.domain.business.Journey
import com.raju.aiassistant.domain.ledger.ClientBalance
import com.raju.aiassistant.domain.reminder.Reminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class LifeMemoryTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 11, 12)
    private fun at(d: LocalDate, h: Int) = d.atTime(h, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun aQuestionAboutAPersonIsUnderstood() {
        assertEquals("Rajesh", parsePersonQuestion("Rajesh ke baare mein sab batao"))
        assertEquals("राजेश", parsePersonQuestion("राजेश के बारे में सब बताओ"))
        assertNull(parsePersonQuestion("gaane ke baare mein batao")) // a general question for the AI
        assertEquals("Rajesh Singh", parsePersonQuestion("Rajesh Singh ka sab batao"))
        assertNull(parsePersonQuestion("mausam ke baare mein batao kal ka 2 baje"))
        assertNull(parsePersonQuestion("Rajesh ko call karo"))
    }

    @Test
    fun aNameIsMatchedAsAWholeWord() {
        assertTrue(mentions("Rajesh Singh, 2 log", "rajesh"))
        assertFalse(mentions("Rajesh Singh", "Raj"))
        assertTrue(mentions("राजेश का टिकट", "राजेश"))
    }

    @Test
    fun everythingSavedAboutAPersonIsSaidTogether() {
        val f = factsAbout(
            "Rajesh", 300_000,
            listOf(Journey(pnr = "1234567890", date = today.plusDays(1), passenger = "Rajesh Singh", route = "PUNE-HWH"), Journey(pnr = "9", date = today, passenger = "Amit")),
            listOf("Rajesh ka birthday 5 March", "Amit ko chai pasand"),
            listOf(Reminder(1, at(today.plusDays(1), 9), "Rajesh ko ticket bhejna")),
        )
        val said = spokenPersonBrief(f, today, zone)
        assertTrue(said, said.startsWith("Rajesh से ₹3,000 लेने बाकी हैं"))
        assertTrue(said, said.contains("यात्रा: कल") && said.contains("याद: Rajesh ka birthday 5 March") && said.contains("कल 9:00 बजे"))
        assertFalse(said, said.contains("Amit"))
    }

    @Test
    fun nothingSavedIsSaidPlainly() {
        val said = spokenPersonBrief(factsAbout("Sunil", null, emptyList(), emptyList(), emptyList()), today, zone)
        assertTrue(said.contains("कुछ भी सेव नहीं है"))
    }

    @Test
    fun theHeadsUpJoinsTomorrowsJourneyWithThePendingMoney() {
        val journeys = listOf(Journey(pnr = "1234567890", date = today.plusDays(1), passenger = "Rajesh Singh", route = "PUNE-HWH"))
        val balances = listOf(ClientBalance("Rajesh", 300_000, 2), ClientBalance("Amit", 50_000, 1), ClientBalance("Riya", -20_000, 1))
        val reminders = listOf(Reminder(1, at(today, 18), "dawai lena"), Reminder(2, at(today.plusDays(3), 9), "baad mein"))
        val text = headsUp(today, journeys, balances, reminders, zone)!!
        assertTrue(text, text.contains("कल यात्रा:") && text.contains("Rajesh से ₹3,000 बाकी हैं"))
        assertTrue(text, text.contains("बाकी पैसा: Rajesh ₹3,000, Amit ₹500") && !text.contains("Riya"))
        assertTrue(text, text.contains("आज 18:00 बजे: dawai lena") && !text.contains("baad mein"))
        assertNull(headsUp(today, emptyList(), emptyList(), emptyList(), zone))
        val evening = headsUp(today, journeys, balances, reminders, zone, includeTomorrowOnly = true)!!
        assertTrue(evening.startsWith("कल यात्रा") && !evening.contains("बाकी पैसा"))
    }

    @Test
    fun aBriefingQuestionIsUnderstood() {
        assertTrue(isHeadsUpQuestion("aaj ki briefing do"))
        assertTrue(isHeadsUpQuestion("कल का क्या है"))
        assertTrue(isHeadsUpQuestion("आज का प्लान बताओ"))
        assertFalse(isHeadsUpQuestion("aaj ka mausam kya hai"))
    }
}
