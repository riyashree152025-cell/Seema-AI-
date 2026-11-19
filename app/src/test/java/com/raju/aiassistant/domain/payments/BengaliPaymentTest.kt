package com.raju.aiassistant.domain.payments

import com.raju.aiassistant.domain.assistant.AssistantAction
import com.raju.aiassistant.domain.assistant.LocalCommands
import org.junit.Assert.assertEquals
import org.junit.Test

/** What the Bengali recogniser wrote on the phone (screenshots of 19 Nov). */
class BengaliPaymentTest {
    @Test
    fun qrSaidInHindiAndWrittenInBengaliLetters() {
        assertEquals(QrRequest("জালাল ভাই", null), PaymentShare.qrRequest("জালাল ভাইকে কি অর ভেজ দো"))
        assertEquals(QrRequest("", null), PaymentShare.qrRequest("বারকোড পাতা পাঠিয়ে দাও")) // whom: Seema asks
        assertEquals(AssistantAction.SendPaymentQr("জালাল ভাই", null), LocalCommands.parse("জালাল ভাইকে কি অর ভেজ দো"))
        assertEquals(QrRequest("Rizwan", null), PaymentShare.qrRequest("Rizwan ko barcode bhej do"))
    }

    @Test
    fun aCallAskedInBengaliIsACallWithoutTheAi() {
        assertEquals(AssistantAction.Call("রিয়া", null), LocalCommands.parse("রিয়াকে কল দাও তো"))
        assertEquals(AssistantAction.Call("মা", null), LocalCommands.parse("মাকে ফোন করো"))
    }
}
