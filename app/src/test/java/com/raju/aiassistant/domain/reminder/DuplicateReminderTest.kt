package com.raju.aiassistant.domain.reminder

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DuplicateReminderTest {
    private val h = 3_600_000L
    private val set = listOf(Reminder(1, 100 * h, "20 तारीख को पहाड़िया express की बुकिंग करनी है"))

    @Test
    fun theSameReminderSetAgainByTheAiIsFound() {
        assertNotNull(duplicateReminder(set, 100 * h, "पहाड़िया express की बुकिंग"))
        assertNotNull(duplicateReminder(set, 101 * h, "Pahadiya express booking karni hai"))
    }

    @Test
    fun otherRemindersAreNew() {
        assertNull(duplicateReminder(set, 100 * h, "दवाई खानी है"))
        assertNull(duplicateReminder(set, 130 * h, "पहाड़िया express की बुकिंग")) // another day
    }
}
