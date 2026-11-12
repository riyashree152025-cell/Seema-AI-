package com.raju.aiassistant.domain.ledger

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class LedgerStatementTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(day: Int) = LocalDate.of(2026, 11, day).atStartOfDay(zone).toInstant().toEpochMilli() + 3_600_000L

    @Test
    fun aClientsAccountIsWrittenOldestFirstWithWhatIsLeft() {
        val entries = listOf(
            LedgerEntryInfo(3, "Rahul", 20000, LedgerKind.GOT, at(10)),
            LedgerEntryInfo(1, "Rahul", 150000, LedgerKind.GAVE, at(2)),
            LedgerEntryInfo(2, "Zaid", 30000, LedgerKind.GAVE, at(5)),
        )
        assertEquals(
            "Rahul का हिसाब\n2 नव 2026 · दिए ₹1,500\n10 नव 2026 · मिले ₹200\nबाकी: ₹1,300 (आपको लेने हैं)",
            ledgerStatement("Rahul", entries, zone),
        )
        val even = listOf(LedgerEntryInfo(1, "Zaid", 100, LedgerKind.GAVE, at(1)), LedgerEntryInfo(2, "Zaid", 100, LedgerKind.GOT, at(2)))
        assertEquals("हिसाब बराबर है।", ledgerStatement("Zaid", even, zone).lines().last())
        val owe = listOf(LedgerEntryInfo(1, "Asha", 50000, LedgerKind.GOT, at(1)))
        assertEquals("बाकी: ₹500 (आपको देने हैं)", ledgerStatement("Asha", owe, zone).lines().last())
    }
}
