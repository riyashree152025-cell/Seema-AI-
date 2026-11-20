package com.raju.aiassistant.domain.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PnrCheckPhrasesTest {
    @Test
    fun aPnrNumberIsATicketNeverMoney() {
        assertEquals(AssistantAction.PnrStatus("6840828103"), LocalCommands.parse("6840828103 pnr check karo"))
        assertEquals(AssistantAction.PnrStatus("6640828103"), LocalCommands.parse("6640828103 train pnr check karo"))
        assertEquals(AssistantAction.PnrStatus("6840828103"), LocalCommands.parse("PNR 6840828103 ka status check karo"))
        assertEquals(AssistantAction.PnrStatus("6840828103"), LocalCommands.parse("pnr 6840828103"))
        assertNull(com.raju.aiassistant.domain.sms.parsePaymentQuestion("6840828103 pnr check karo"))
        // A real payment check still works.
        assertEquals(375_000L, com.raju.aiassistant.domain.sms.parsePaymentQuestion("3750 ka refund aaya check karo")?.amountPaise)
    }
}
