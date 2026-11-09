package com.raju.aiassistant.domain.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LedgerTest {
    @Test
    fun moneyComingInIsRecorded() {
        assertEquals(LedgerCommand.Record("Rahul", 50_000, LedgerKind.GOT), parseLedger("Rahul se 500 rupaye mile"))
        assertEquals(LedgerCommand.Record("राहुल", 50_000, LedgerKind.GOT), parseLedger("राहुल से 500 रुपये मिले"))
        assertEquals(LedgerCommand.Record("Mohan Lal", 150_000, LedgerKind.GOT), parseLedger("Mohan Lal se 1,500 rupay wapas mile"))
    }

    @Test
    fun moneyGoingOutIsRecorded() {
        assertEquals(LedgerCommand.Record("Zaid", 30_000, LedgerKind.GAVE), parseLedger("Zaid ko 300 rupaye diye"))
        assertEquals(LedgerCommand.Record("Zaid", 30_000, LedgerKind.GAVE), parseLedger("Zaid ko 300 udhaar diya"))
        assertEquals(LedgerCommand.Record("zaid", 500_000, LedgerKind.GAVE), parseLedger("zaid ko 5 hazar diye"))
    }

    @Test
    fun aSentenceWithoutAnAmountIsNotAnEntry() {
        assertNull(parseLedger("Zaid ko itna payment diya")) // how much? the AI asks
        assertNull(parseLedger("Zaid ko message karo"))
        assertNull(parseLedger("mujhe 500 rupaye chahiye"))
    }

    @Test
    fun askingForABalance() {
        assertEquals(LedgerCommand.Balance("Rahul"), parseLedger("Rahul ka hisaab batao"))
        assertEquals(LedgerCommand.Balance("Rahul"), parseLedger("Rahul ka balance kya hai"))
        assertEquals(LedgerCommand.Balance("zaid"), parseLedger("hisaab zaid"))
    }

    @Test
    fun theBalanceIsWhatTheClientStillOwes() {
        val lines = listOf(
            LedgerLine(LedgerKind.GAVE, 100_000),
            LedgerLine(LedgerKind.GAVE, 50_000),
            LedgerLine(LedgerKind.GOT, 60_000),
        )
        assertEquals(90_000L, balanceOf(lines))
        assertEquals(-20_000L, balanceOf(listOf(LedgerLine(LedgerKind.GOT, 20_000))))
        assertEquals(0L, balanceOf(emptyList()))
    }

    @Test
    fun amountsAreSanityChecked() {
        assertNull(parseLedger("Rahul se 0 rupaye mile"))
        assertNull(parseLedger("Rahul se 9999999999999 rupaye mile"))
    }

    @Test
    fun neSentencesAreUnderstood() {
        assertEquals(LedgerCommand.Record("Rahul", 50_000, LedgerKind.GOT), parseLedger("Rahul ne mujhe 500 rupaye diye"))
        assertEquals(LedgerCommand.Record("राहुल", 50_000, LedgerKind.GOT), parseLedger("राहुल ने 500 रुपये लौटाए"))
        assertEquals(LedgerCommand.Record("Zaid", 30_000, LedgerKind.GAVE), parseLedger("Zaid ne mujhse 300 udhaar liye"))
        assertEquals(LedgerCommand.Undo, parseLedger("aakhri entry hatao"))
        assertEquals(LedgerCommand.Undo, parseLedger("आखिरी एंट्री हटा दो"))
    }
}
