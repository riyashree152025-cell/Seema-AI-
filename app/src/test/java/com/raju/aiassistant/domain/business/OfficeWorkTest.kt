package com.raju.aiassistant.domain.business

import com.raju.aiassistant.domain.assistant.AssistantAction
import com.raju.aiassistant.domain.assistant.LocalCommands
import com.raju.aiassistant.domain.ledger.ClientBalance
import com.raju.aiassistant.domain.rail.IrctcTicket
import com.raju.aiassistant.domain.rail.TicketPassenger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class OfficeWorkTest {
    @Test
    fun aTicketBecomesADiaryJourney() {
        assertEquals(LocalDate.of(2026, 12, 15), TicketToDiary.date("15-Dec-2026"))
        assertEquals(LocalDate.of(2026, 12, 15), TicketToDiary.date("15-DEC-2026"))
        assertEquals(LocalDate.of(2026, 12, 5), TicketToDiary.date("05/12/2026"))
        assertNull(TicketToDiary.date("soon"))
        val t = IrctcTicket("6840828103", "12301", "RAJDHANI EXP", "15-Dec-2026", "HWH", "NDLS", "3A",
            listOf(TicketPassenger("RAHUL KUMAR", 34, "M", "CNF/B2/45"), TicketPassenger("PRIYA", 30, "F", "CNF/B2/46")))
        val j = TicketToDiary.journey(t)!!
        assertEquals("6840828103", j.pnr)
        assertEquals(LocalDate.of(2026, 12, 15), j.date)
        assertEquals("HWH → NDLS", j.route)
        assertEquals("RAHUL KUMAR +1", j.passenger)
        assertNull(TicketToDiary.journey(t.copy(pnr = "12")))
    }

    @Test
    fun scheduledMessages() {
        val now = LocalDateTime.of(2026, 11, 21, 12, 0)
        val m = ScheduledMessages.parse("kal subah 8 baje Amit ko message bhejna ki ticket taiyar hai", now)!!
        assertEquals("Amit", m.who)
        assertEquals("ticket taiyar hai", m.text)
        assertEquals(LocalDateTime.of(2026, 11, 22, 8, 0), m.at)
        val q = ScheduledMessages.parse("शाम 6 बजे राहुल को 'पैसे भेज दो' भेज देना", now)!!
        assertEquals("राहुल" to "पैसे भेज दो", q.who to q.text)
        assertEquals(LocalDateTime.of(2026, 11, 21, 18, 0), q.at)
        assertNull(ScheduledMessages.parse("Amit ko message bhejo ki aa jao", now)) // no time: an ordinary WhatsApp
        assertEquals("Amit" to "ticket taiyar hai", ScheduledMessages.fromReminder(ScheduledMessages.reminderText(m)))
        assertNull(ScheduledMessages.fromReminder("dawai lena"))
        assertTrue(LocalCommands.parse("kal subah 8 baje Amit ko message bhejna ki ticket taiyar hai") is AssistantAction.ScheduleMessage)
    }

    @Test
    fun pdfsAndTheDiary() {
        assertTrue(OfficePdfs.asksDuesPdf("bakaya ki list PDF banao"))
        assertTrue(OfficePdfs.asksDuesPdf("बकाया की लिस्ट पीडीएफ में दो"))
        val page = OfficePdfs.dues(listOf(ClientBalance("Rahul", 1_250_000, 2), ClientBalance("Amit", -5_000, 1), ClientBalance("Mitu", 300_000, 1)), LocalDate.of(2026, 11, 21))
        assertEquals(listOf("1. Rahul — ₹12,500", "2. Mitu — ₹3,000", "", "कुल बाकी: ₹15,500  (2 लोग)"), page.lines)
        val bill = OfficePdfs.parseBill("Rahul ka bill banao 2 ticket 4500")!!
        assertEquals(OfficePdfs.Bill("Rahul", 2, 450_000), bill)
        assertTrue(OfficePdfs.bill(bill, LocalDate.of(2026, 11, 21), 7).lines.contains("कुल रकम: ₹4,500"))
        assertEquals(AssistantAction.DuesPdf, LocalCommands.parse("bakaya ki list PDF banao"))
        assertEquals(AssistantAction.ActionDiaryAsk, LocalCommands.parse("aaj tumne kya kya kiya"))
        assertEquals(AssistantAction.SaveCallerNumber, LocalCommands.parse("ye number save karo"))
        assertEquals("आज मैंने 3 काम किए: Call (2 बार), WhatsApp।", ActionDiary.summary(listOf("Call", "WhatsApp", "Call")))
    }
}
