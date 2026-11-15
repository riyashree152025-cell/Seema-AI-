package com.raju.aiassistant.domain.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingLedgerTest {

    @Test
    fun spokenMemosBecomeEntries() {
        assertEquals(LedgerCommand.Record("Mohan bhai", 150_000, LedgerKind.GAVE), parseLedger("yaad rakhna Mohan bhai se 1500 rupaye lene baki hain"))
        assertEquals(LedgerCommand.Record("Ramesh", 50_000, LedgerKind.GOT), parseLedger("Ramesh ko 500 rupaye dene baki hain"))
        assertEquals(LedgerCommand.AllPending, parseLedger("aaj kiska kiska hisab baki hai"))
        assertEquals(LedgerCommand.AllPending, parseLedger("सबका हिसाब बताओ"))
        assertEquals(LedgerCommand.Settle("Mohan"), parseLedger("Mohan ka hisab clear kar do"))
    }

    @Test
    fun thePendingSummaryNamesEveryoneAndTheTotal() {
        val said = pendingSummary(
            listOf(
                ClientBalance("Mohan", 150_000, 2),
                ClientBalance("Ramesh", -50_000, 1),
                ClientBalance("Suresh", 0, 4),
            ),
        )
        assertTrue(said, said.startsWith("2 लोगों का हिसाब बाकी है"))
        assertTrue(said, said.contains("Mohan से ₹1,500 लेने हैं"))
        assertTrue(said, said.contains("Ramesh को ₹500 देने हैं"))
        assertTrue(said, !said.contains("Suresh"))
        assertTrue(said, said.contains("कुल मिलाकर ₹1,500 लेने हैं और ₹500 देने हैं"))
        assertEquals("किसी का कोई हिसाब बाकी नहीं है, सब बराबर है।", pendingSummary(emptyList()))
    }

    @Test
    fun settlingWritesTheOneEntryThatBringsTheBalanceToZero() {
        assertEquals(LedgerLine(LedgerKind.GOT, 150_000), settlingEntry(150_000))
        assertEquals(LedgerLine(LedgerKind.GAVE, 50_000), settlingEntry(-50_000))
        assertNull(settlingEntry(0))
        assertEquals(0L, balanceOf(listOf(LedgerLine(LedgerKind.GAVE, 150_000), settlingEntry(150_000)!!)))
    }
}
