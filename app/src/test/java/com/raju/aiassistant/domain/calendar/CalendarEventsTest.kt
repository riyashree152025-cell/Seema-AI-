package com.raju.aiassistant.domain.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalendarEventsTest {
    @Test
    fun calendarQuestionsNameTheDay() {
        assertEquals(0, parseCalendarQuestion("aaj ki meetings batao"))
        assertEquals(1, parseCalendarQuestion("kal ka calendar"))
        assertEquals(2, parseCalendarQuestion("parso ka schedule batao"))
        assertEquals(0, parseCalendarQuestion("आज की मीटिंग क्या हैं"))
        assertEquals(0, parseCalendarQuestion("aaj calendar mein kya hai"))
        assertNull(parseCalendarQuestion("kal ka mausam batao"))
        assertNull(parseCalendarQuestion("meeting add karo"))
    }

    @Test
    fun eventsAreSpokenInOrderWithThePartOfTheDay() {
        val text = spokenEvents(listOf(CalEvent(17 * 60, false, "Meeting"), CalEvent(10 * 60 + 30, false, "Doctor"), CalEvent(0, true, "Diwali")), 0)
        assertEquals("आज 3 कार्यक्रम हैं: पूरे दिन Diwali; सुबह 10:30 बजे Doctor; शाम 5:00 बजे Meeting।", text)
        assertEquals("कल calendar में कुछ नहीं है।", spokenEvents(emptyList(), 1))
        assertEquals("आज 1 कार्यक्रम है: रात 9:05 बजे Call।", spokenEvents(listOf(CalEvent(21 * 60 + 5, false, "Call")), 0))
    }
}
