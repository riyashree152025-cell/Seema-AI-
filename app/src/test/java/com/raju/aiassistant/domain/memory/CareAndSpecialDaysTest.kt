package com.raju.aiassistant.domain.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.MonthDay

class CareAndSpecialDaysTest {
    @Test
    fun troubleIsNoticedAndAskedAboutHoursLater() {
        assertEquals("सिर दर्द", CareFollowUp.concernOf("aaj subah se sir dard ho raha hai"))
        assertEquals("बुखार", CareFollowUp.concernOf("मुझे बुखार है"))
        assertEquals("तबियत", CareFollowUp.concernOf("শরীর খারাপ লাগছে"))
        assertNull(CareFollowUp.concernOf("Rahul ko call karo"))
        val h = 3_600_000L
        assertNull(CareFollowUp.promptLine("सिर दर्द", 0, 2 * h, bengali = false)) // too soon
        assertTrue(CareFollowUp.promptLine("सिर दर्द", 0, 5 * h, bengali = false)!!.contains("सिर दर्द अब कैसा है"))
        assertNull(CareFollowUp.promptLine("सिर दर्द", 0, 8 * 24 * h, bengali = false)) // old news
    }

    @Test
    fun specialDaysInThreeScripts() {
        assertEquals(SpecialDay("Rahul", DayKind.BIRTHDAY, MonthDay.of(3, 5)), SpecialDays.parse("Rahul ka birthday 5 March ko hai"))
        assertEquals(SpecialDay("अमित", DayKind.BIRTHDAY, MonthDay.of(6, 12)), SpecialDays.parse("अमित का जन्मदिन 12 जून को है"))
        assertEquals(SpecialDay("Raju", DayKind.ANNIVERSARY, MonthDay.of(6, 12)), SpecialDays.parse("मेरी शादी की सालगिरह 12 जून को है"))
        assertEquals(SpecialDay("মিঠু", DayKind.BIRTHDAY, MonthDay.of(3, 5)), SpecialDays.parse("মিঠুর জন্মদিন ৫ মার্চ"))
        assertNull(SpecialDays.parse("Rahul ka birthday kab hai"))
        assertNull(SpecialDays.parse("Rahul ka birthday 31 February"))
    }

    @Test
    fun savedOnceAndSaidEveryYearOnTheDay() {
        val d = SpecialDay("Rahul", DayKind.BIRTHDAY, MonthDay.of(3, 5))
        val fact = SpecialDays.factText(d)
        assertEquals("खास दिन: Rahul का जन्मदिन — 5 March", fact)
        assertEquals(d, SpecialDays.fromFact(fact))
        assertTrue(SpecialDays.todayLine(listOf(fact, "Raju को पसंद है: chai"), LocalDate.of(2027, 3, 5))!!.contains("आज Rahul का जन्मदिन है"))
        assertNull(SpecialDays.todayLine(listOf(fact), LocalDate.of(2027, 3, 6)))
    }
}
