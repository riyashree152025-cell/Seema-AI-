package com.raju.aiassistant.domain.automation

import com.raju.aiassistant.domain.assistant.AssistantAction
import com.raju.aiassistant.domain.assistant.LocalCommands
import com.raju.aiassistant.domain.reminder.Reminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class PlacesTest {
    @Test
    fun savingAPlace() {
        assertEquals("dukaan", Places.saveRequest("is jagah ko dukaan naam se yaad rakho"))
        assertEquals("दुकान", Places.saveRequest("इस जगह को दुकान नाम से याद रखो"))
        assertEquals("ghar", Places.saveRequest("ye jagah mera ghar hai"))
        assertEquals("দোকান", Places.saveRequest("এই জায়গাটা দোকান নামে মনে রাখো"))
        assertNull(Places.saveRequest("ye jagah bahut sundar hai yaar"))
        assertNull(Places.saveRequest("ye jagah sundar hai"))
        assertEquals(AssistantAction.SavePlace("dukaan"), LocalCommands.parse("is jagah ko dukaan naam se yaad rakho"))
    }

    @Test
    fun arrivalLine() {
        val z = ZoneId.of("Asia/Kolkata")
        fun at(h: Int) = LocalDateTime.of(2026, 11, 21, h, 0).atZone(z).toInstant().toEpochMilli()
        val line = Places.arrival("dukaan", listOf(Reminder(1, at(15), "Rahul ko call"), Reminder(2, at(9), "old")), at(10), z)
        assertEquals("dukaan पहुँच गए। आज बाकी काम: 15:00 — Rahul ko call।", line)
        assertTrue(Places.arrival("ghar", emptyList(), at(10), z).contains("कोई काम बाकी नहीं"))
    }
}
