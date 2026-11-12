package com.raju.aiassistant.domain.tatkal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class TatkalWindowTest {
    private fun at(h: Int, m: Int, s: Int = 0) = LocalDateTime.of(2026, 11, 12, h, m, s)

    @Test
    fun theWindowsAreForTomorrowsJourneyAndMoveOnAfterOpening() {
        val morning = tatkalWindows(at(8, 50))
        assertEquals(listOf(at(10, 0), at(11, 0)), morning.map { it.opensAt })
        assertEquals(LocalDate.of(2026, 11, 13), morning[0].journeyDate)
        // Just opened: still shown for half an hour, then the next day's.
        assertEquals(at(10, 0), tatkalWindows(at(10, 20))[0].opensAt)
        assertEquals(at(10, 0).plusDays(1), tatkalWindows(at(10, 31))[0].opensAt)
        assertEquals(at(11, 0), tatkalWindows(at(10, 31))[1].opensAt)
    }

    @Test
    fun theCountdownIsSaidInHoursAndMinutes() {
        val ac = TatkalWindow(TatkalAlarm.AC, at(10, 0))
        assertEquals("1 घंटे 10 मिनट में, 10:00 बजे", tatkalCountdown(ac, at(8, 50)))
        assertEquals("2 मिनट में, 10:00 बजे", tatkalCountdown(ac, at(9, 58)))
        assertEquals("1 मिनट में, 10:00 बजे", tatkalCountdown(ac, at(9, 59, 30))) // rounded up
        assertEquals("1 घंटे में, 10:00 बजे", tatkalCountdown(ac, at(9, 0)))
        assertEquals("खुल चुका है (10:00 से)", tatkalCountdown(ac, at(10, 5)))
        val said = spokenTatkalWindows(at(9, 0))
        assertTrue(said, said.startsWith("AC Tatkal (13 नवंबर की यात्रा के लिए): 1 घंटे में, 10:00 बजे।"))
        assertTrue(said, said.contains("Sleeper (non-AC) Tatkal (13 नवंबर की यात्रा के लिए): 2 घंटे में, 11:00 बजे।"))
    }

    @Test
    fun onlyWhenQuestionsAreTaken() {
        assertTrue(isTatkalWhenQuestion("tatkal kab khulega"))
        assertTrue(isTatkalWhenQuestion("तत्काल कितने बजे खुलता है"))
        assertTrue(isTatkalWhenQuestion("Seema tatkal kitni der mein khulega"))
        assertFalse(isTatkalWhenQuestion("tatkal 9:58 ka alarm lagao"))
        assertFalse(isTatkalWhenQuestion("tatkal ke kitne alarm lage hain"))
        assertFalse(isTatkalWhenQuestion("tatkal alarm kab bajega"))
    }
}
