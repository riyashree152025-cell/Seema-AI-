package com.raju.aiassistant.domain.tatkal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDateTime

class TatkalAlarmTest {
    @Test
    fun readsTheTimeAndTheClass() {
        assertEquals(TatkalAlarm(9, 58, "AC"), parseTatkal("tatkal alarm 9:58 AC"))
        assertEquals(TatkalAlarm(10, 58, "Sleeper"), parseTatkal("10:58 baje sleeper ke liye tatkal yaad dilao"))
        assertEquals(TatkalAlarm(9, 58, "AC"), parseTatkal("तत्काल 9:58 एसी"))
        assertEquals(TatkalAlarm(10, 58, "Sleeper"), parseTatkal("तत्काल 10:58 स्लीपर"))
    }

    @Test
    fun withoutTatkalOrWithoutAClearTimeItIsNotAnAlarm() {
        assertNull(parseTatkal("alarm 9:58 AC"))
        assertNull(parseTatkal("tatkal ke baare mein batao"))
        assertNull(parseTatkal("tatkal 10 baje")) // morning or evening? ask the AI, do not guess
    }

    @Test
    fun partsOfTheDayAreRead() {
        assertEquals(TatkalAlarm(21, 58, "AC"), parseTatkal("tatkal 9:58 pm"))
        assertEquals(TatkalAlarm(9, 58, "AC"), parseTatkal("tatkal 9:58 subah"))
        assertEquals(TatkalAlarm(21, 5, "AC"), parseTatkal("tatkal 9:05 raat"))
        assertEquals(TatkalAlarm(12, 30, "AC"), parseTatkal("tatkal 12:30 pm"))
        assertEquals(TatkalAlarm(0, 5, "AC"), parseTatkal("tatkal 12:05 am"))
    }

    @Test
    fun badMinutesAreRefused() {
        assertNull(parseTatkal("tatkal 9:75 AC"))
    }

    @Test
    fun theNextRingIsTodayIfStillAheadElseTomorrow() {
        val ac = TatkalAlarm(9, 58, "AC")
        assertEquals(LocalDateTime.of(2026, 11, 9, 9, 58), nextRing(ac, LocalDateTime.of(2026, 11, 9, 9, 0)))
        assertEquals(LocalDateTime.of(2026, 11, 10, 9, 58), nextRing(ac, LocalDateTime.of(2026, 11, 9, 9, 58)))
        assertEquals(LocalDateTime.of(2026, 11, 10, 9, 58), nextRing(ac, LocalDateTime.of(2026, 11, 9, 10, 0)))
    }

    @Test
    fun acAndSleeperAlarmsNeverShareAnId() {
        assertFalse(TatkalAlarm(9, 58, "AC").id == TatkalAlarm(9, 58, "Sleeper").id)
    }

    @Test
    fun aSentenceCanSetListOrCancel() {
        assertEquals(TatkalCommand.Set(TatkalAlarm(9, 58, "AC")), parseTatkalCommand("tatkal alarm 9:58 AC"))
        assertEquals(TatkalCommand.List, parseTatkalCommand("tatkal alarm list karo"))
        assertEquals(TatkalCommand.List, parseTatkalCommand("mere tatkal alarm batao"))
        assertEquals(TatkalCommand.List, parseTatkalCommand("कितने तत्काल अलार्म लगे हैं"))
        assertEquals(TatkalCommand.Cancel(9, 58, "AC"), parseTatkalCommand("9:58 AC tatkal alarm cancel karo"))
        assertEquals(TatkalCommand.Cancel(10, 58, null), parseTatkalCommand("10:58 wala tatkal alarm band karo"))
        assertEquals(TatkalCommand.CancelAll, parseTatkalCommand("sab tatkal alarm hata do"))
        assertEquals(TatkalCommand.CancelAll, parseTatkalCommand("तत्काल के सारे अलार्म हटाओ"))
        assertNull(parseTatkalCommand("tatkal alarm cancel karo")) // which one? no time, no "sab"
        assertNull(parseTatkalCommand("tatkal ke baare mein batao")) // a question, no time: not a list either? it is a question for the AI
    }
}
