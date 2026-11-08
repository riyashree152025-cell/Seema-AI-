package com.raju.aiassistant.domain.assistant

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SpokenClockTest {
    @Test
    fun timeIsSaidWithThePartOfTheDay() {
        assertEquals("अभी सुबह के 9 बजकर 5 मिनट हुए हैं।", SpokenClock.time(9, 5))
        assertEquals("अभी दोपहर के 12 बजे हैं।", SpokenClock.time(12, 0))
        assertEquals("अभी शाम के 6 बजकर 30 मिनट हुए हैं।", SpokenClock.time(18, 30))
        assertEquals("अभी रात के 12 बजे हैं।", SpokenClock.time(0, 0))
        assertEquals("এখন রাত 11টা 45 মিনিট।", SpokenClock.time(23, 45, bengali = true))
    }

    @Test
    fun dateIsSaidWithTheDayOfTheWeek() {
        assertEquals("आज रविवार, 8 नवंबर 2026 है।", SpokenClock.date(LocalDate.of(2026, 11, 8)))
        assertEquals("আজ রবিবার, 8 নভেম্বর 2026।", SpokenClock.date(LocalDate.of(2026, 11, 8), bengali = true))
    }
}
