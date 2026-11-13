package com.raju.aiassistant.domain.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ReminderWithoutTimeTest {
    @Test
    fun aTatkalBookingReminderNeedsNoTime() {
        // Exactly what the user said on the phone.
        assertEquals(TatkalReminderAsk("Anand", sleeper = false), parseTatkalReminder("anand ka tatkal booking hai yaad diladena"))
        assertEquals(TatkalReminderAsk("Geeta Kamat", sleeper = true), parseTatkalReminder("Seema geeta kamat ka sleeper tatkal booking yaad dila dena"))
        assertEquals(TatkalReminderAsk(null, sleeper = false), parseTatkalReminder("tatkal booking yaad dilana"))
        assertNull(parseTatkalReminder("tatkal 9:58 ka alarm lagao yaad dila dena")) // has a time: the alarm parsers handle it
        assertNull(parseTatkalReminder("anand ka tatkal booking karo"))
    }

    @Test
    fun theReminderIsFiveMinutesBeforeTheNextOpening() {
        val morning = LocalDateTime.of(2026, 11, 13, 7, 0)
        assertEquals(LocalDateTime.of(2026, 11, 13, 9, 55), tatkalReminderAt(morning, sleeper = false))
        assertEquals(LocalDateTime.of(2026, 11, 13, 10, 55), tatkalReminderAt(morning, sleeper = true))
        val evening = LocalDateTime.of(2026, 11, 13, 19, 30)
        assertEquals(LocalDateTime.of(2026, 11, 14, 9, 55), tatkalReminderAt(evening, sleeper = false))
        val justBefore = LocalDateTime.of(2026, 11, 13, 9, 58)
        assertEquals(justBefore.plusMinutes(1), tatkalReminderAt(justBefore, sleeper = false))
    }

    @Test
    fun otherRemindersWithoutATimeAreAskedAbout() {
        assertTrue(isReminderWithoutTime("Rahul ka paisa lena hai yaad dila dena"))
        assertFalse(isReminderWithoutTime("kal subah yaad dila dena ki dawai leni hai"))
        assertFalse(isReminderWithoutTime("10 minute baad yaad dilana"))
        assertFalse(isReminderWithoutTime("sab reminder batao"))
        assertFalse(isReminderWithoutTime("anand ka tatkal booking hai yaad diladena"))
    }
}
