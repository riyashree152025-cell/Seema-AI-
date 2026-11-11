package com.raju.aiassistant.domain.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class ReminderTest {
    private val now = LocalDateTime.of(2026, 11, 11, 10, 0) // Wednesday 10:00

    private fun add(s: String) = parseReminder(s, now) as? ReminderCommand.Add

    @Test
    fun minutesAndHoursFromNow() {
        assertEquals(ReminderCommand.Add(now.plusMinutes(10), "dawai leni hai"), parseReminder("10 minute baad yaad dilana ki dawai leni hai", now))
        assertEquals(ReminderCommand.Add(now.plusHours(2), "Rahul ko call karna hai"), parseReminder("2 ghante baad yaad dilao Rahul ko call karna hai", now))
        assertEquals(now.plusMinutes(30), add("30 मिनट बाद याद दिलाना कि गैस बंद करनी है")!!.at)
        assertEquals("गैस बंद करनी है", add("30 मिनट बाद याद दिलाना कि गैस बंद करनी है")!!.text)
    }

    @Test
    fun aClockTimeWithThePartOfTheDay() {
        assertEquals(ReminderCommand.Add(LocalDateTime.of(2026, 11, 12, 8, 0), "bill bharna hai"), parseReminder("kal subah 8 baje yaad dilao bill bharna hai", now))
        assertEquals(LocalDateTime.of(2026, 11, 11, 17, 30), add("shaam 5:30 baje meeting yaad dilana")!!.at)
        assertEquals("meeting", add("shaam 5:30 baje meeting yaad dilana")!!.text)
        // Already past today: tomorrow.
        assertEquals(LocalDateTime.of(2026, 11, 12, 9, 0), add("subah 9 baje yaad dilana ki dawai leni hai")!!.at)
        assertEquals(LocalDateTime.of(2026, 11, 11, 18, 45), add("18:45 baje yaad dilana train pakadni hai")!!.at)
    }

    @Test
    fun unclearOrEmptyRemindersAreNotGuessed() {
        assertNull(parseReminder("8 baje yaad dilana dawai", now)) // morning or evening?
        assertNull(parseReminder("10 minute baad yaad dilana", now)) // of what?
        assertNull(parseReminder("aaj subah 8 baje yaad dilana dawai", now)) // already past
        assertNull(parseReminder("mujhe kuch yaad nahi", now))
        assertNull(parseReminder("kal ka mausam batao", now))
    }

    @Test
    fun listAndCancel() {
        assertEquals(ReminderCommand.List, parseReminder("mere reminder batao", now))
        assertEquals(ReminderCommand.CancelAll, parseReminder("sab reminder hata do", now))
        assertEquals(ReminderCommand.CancelLast, parseReminder("aakhri reminder hatao", now))
    }

    @Test
    fun theTimeIsSpokenNaturally() {
        assertEquals("आज शाम 5:30 बजे", spokenReminderTime(LocalDateTime.of(2026, 11, 11, 17, 30), now))
        assertEquals("कल सुबह 8:00 बजे", spokenReminderTime(LocalDateTime.of(2026, 11, 12, 8, 0), now))
    }
}
