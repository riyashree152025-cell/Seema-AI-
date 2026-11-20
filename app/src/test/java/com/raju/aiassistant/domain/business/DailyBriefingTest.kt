package com.raju.aiassistant.domain.business

import com.raju.aiassistant.domain.ledger.ClientBalance
import com.raju.aiassistant.domain.reminder.Reminder
import com.raju.aiassistant.domain.sms.MoneyEvent
import com.raju.aiassistant.domain.sms.MoneyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class DailyBriefingTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, 20, h, m).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun cashflowAddsInAndOut() {
        val c = DailyBriefing.cashflow(listOf(MoneyEvent(450_000, MoneyFlow.IN, 0, null), MoneyEvent(73_000, MoneyFlow.OUT, 0, null), MoneyEvent(125_000, MoneyFlow.OUT, 0, null)))
        assertEquals(Cashflow(450_000, 1, 198_000, 2), c)
    }

    @Test
    fun morningBriefingWithMoneyDuesAndTasks() {
        val text = DailyBriefing.build(
            at(9, 5), zone, Cashflow(450_000, 1, 198_000, 2),
            listOf(ClientBalance("Rahul", 500_000, 2), ClientBalance("Amit", -10_000, 1)),
            listOf(Reminder(1, at(17), "Amit ko call"), Reminder(2, at(8), "old")),
            bengali = false,
        )
        assertTrue(text, text.startsWith("सुप्रभात"))
        assertTrue(text, text.contains("रुपये आए"))
        assertTrue(text, text.contains("Rahul से"))
        assertTrue(text, !text.contains("Amit से"))
        assertTrue(text, text.contains("17:00 — Amit ko call"))
        assertTrue(text, !text.contains("old"))
    }

    @Test
    fun withoutSmsPermissionMoneyIsNotClaimed() {
        val text = DailyBriefing.build(at(21, 10), zone, null, emptyList(), emptyList(), bengali = true)
        assertTrue(text, text.contains("SMS"))
        assertTrue(text, text.contains("বাকি টাকা নেই"))
    }

    @Test
    fun checkInOncePerWindow() {
        assertEquals(at(9), DailyBriefing.dueCheckIn(at(9, 30), zone, 0))
        assertNull(DailyBriefing.dueCheckIn(at(9, 40), zone, at(9, 30)))
        assertNull(DailyBriefing.dueCheckIn(at(12), zone, 0))
        assertEquals(at(21), DailyBriefing.dueCheckIn(at(22), zone, at(9, 30)))
    }
}
