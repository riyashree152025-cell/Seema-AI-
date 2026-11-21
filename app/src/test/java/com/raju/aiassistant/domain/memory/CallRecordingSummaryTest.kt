package com.raju.aiassistant.domain.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class CallRecordingSummaryTest {
    @Test
    fun asks() {
        assertTrue(CallRecordingSummary.asks("aakhri call recording ka saar batao"))
        assertTrue(CallRecordingSummary.asks("आख़िरी कॉल रिकॉर्डिंग का सार बताओ"))
        assertTrue(CallRecordingSummary.asks("শেষ কলের রেকর্ডিং-এ কী কথা হল"))
        assertFalse(CallRecordingSummary.asks("aakhri call kiski thi"))
        assertFalse(CallRecordingSummary.asks("video recording chalu karo"))
    }

    @Test
    fun gistAndPromisesFromTheReply() {
        val reply = "राहुल ने कॉल किया था, टिकट की बात हुई। तय हुआ कि वो कल पैसे भेजेगा।\nPROMISE: Rahul kal 5000 rupaye dega\nPROMISE: shaam ko Amit ko call karna hai"
        assertEquals("राहुल ने कॉल किया था, टिकट की बात हुई। तय हुआ कि वो कल पैसे भेजेगा।", CallRecordingSummary.gist(reply))
        val promises = CallRecordingSummary.promises(reply)
        assertEquals(2, promises.size)
        val now = LocalDateTime.of(2026, 11, 21, 12, 0)
        val all = promises.flatMap { CallMemoryExtractor.extract(it, now) }
        assertEquals(setOf(CommitmentKind.MONEY_IN, CommitmentKind.CALL_BACK), all.map { it.kind }.toSet())
        assertTrue(CallRecordingSummary.promises("bas baat hui").isEmpty())
        assertTrue(CallRecordingSummary.prompt(bengali = false).contains("PROMISE:"))
    }
}
