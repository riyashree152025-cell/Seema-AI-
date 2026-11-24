package com.raju.aiassistant.domain.payments

import com.raju.aiassistant.domain.assistant.AssistantAction
import com.raju.aiassistant.domain.assistant.LocalCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentShareTest {
    private val saved = listOf(UpiEntry("Paytm", "raju@paytm"), UpiEntry("PhonePe", "raju@ybl"), UpiEntry("R Travels", "rtravels@okaxis"))

    @Test
    fun qrRequestsFindThePersonAndTheAmount() {
        assertEquals(QrRequest("Rizwan", null), PaymentShare.qrRequest("Mera QR bhej do Rizwan ko"))
        assertEquals(QrRequest("Rizwan", 500), PaymentShare.qrRequest("Rizwan ko payment QR bhejo 500 rupaye"))
        assertEquals(QrRequest("जलाल भाई", null), PaymentShare.qrRequest("जलाल भाई को मेरा क्यूआर कोड भेज दो"))
        assertNull(PaymentShare.qrRequest("QR code kya hota hai"))
        assertEquals(AssistantAction.SendPaymentQr("Rizwan", null), LocalCommands.parse("Mera QR bhej do Rizwan ko"))
        // Exactly what was typed on the phone: a contact saved with digits, and the amount in words.
        assertEquals(QrRequest("Vodafone 0 1", 1000), PaymentShare.qrRequest("Vodafone 0 1 को वह QR भेज दो और हज़ार रुपए का लगा दो उसमें"))
        assertEquals(QrRequest("Rizwan bhai", null), PaymentShare.qrRequest("Paytm ka QR bhej do Rizwan bhai"))
        assertEquals(QrRequest("", null), PaymentShare.qrRequest("mera QR bhej do")) // whom: Seema asks
        assertEquals(2500L, PaymentShare.amount("dhai hazaar rupaye"))
        assertEquals(1500L, PaymentShare.amount("डेढ़ हज़ार"))
    }

    @Test
    fun upiRequestsAndTheInteractiveChoice() {
        assertEquals(UpiRequest("Jalal bhai", null, null), PaymentShare.upiRequest("Jalal bhai ko UPI ID bhej do", saved))
        assertEquals(UpiRequest("Anand", "PhonePe", null), PaymentShare.upiRequest("PhonePe wala UPI Anand ko bhejo", saved))
        assertEquals(AssistantAction.SendUpiId("Jalal bhai", null, null), LocalCommands.parse("Jalal bhai ko UPI ID bhej do"))
        // The answer to "kaun si?"
        assertEquals("Paytm", PaymentShare.pick(saved, "Paytm wala")?.label)
        assertEquals("PhonePe", PaymentShare.pick(saved, "फोनपे भेज दो")?.label)
        assertEquals("R Travels", PaymentShare.pick(saved, "R Travels wala bhejo")?.label)
        assertEquals("PhonePe", PaymentShare.pick(saved, "dusra wala")?.label)
        assertNull(PaymentShare.pick(saved, "kuch nahi"))
        assertEquals("Jalal bhai को कौन सी UPI ID भेजनी है — Paytm, PhonePe या R Travels?", PaymentShare.whichQuestion("Jalal bhai", saved))
    }

    @Test
    fun theMessagesAndStorage() {
        assertEquals("💳 *R Travels - Payment Details*\n*UPI ID:* raju@paytm\n*Name:* Raju Barai", PaymentShare.upiMessage(saved[0], "Raju Barai", null))
        assertTrue(PaymentShare.upiMessage(saved[0], "Raju Barai", 500).endsWith("*Amount:* ₹500"))
        assertEquals("💳 *R Travels - Payment QR*\nPlease scan this QR code to make the payment.", PaymentShare.qrCaption(null))
        assertEquals(saved, PaymentShare.decode(PaymentShare.encode(saved)))
        assertTrue(PaymentShare.isValidUpi("raju.barai@okaxis"))
        assertFalse(PaymentShare.isValidUpi("raju barai"))
    }

    @Test
    fun fourModePaymentDispatchWithHisab() {
        // Mode 1: QR only
        assertEquals(
            AssistantAction.SendPaymentQr("Anand Patel", null, includeHisab = false),
            LocalCommands.parse("Anand Patel ko QR bhejo")
        )
        // Mode 2: UPI ID only
        assertEquals(
            AssistantAction.SendUpiId("Anand Patel", null, null, includeHisab = false),
            LocalCommands.parse("Anand Patel ko UPI ID bhejo")
        )
        // Mode 3: Hisab ke saath QR
        val qrWithHisab = LocalCommands.parse("Anand Patel ko hisaab ke saath QR bhejo")
        assertEquals(
            AssistantAction.SendPaymentQr("Anand Patel", null, includeHisab = true),
            qrWithHisab
        )
        // Mode 4: Hisab ke saath UPI ID
        val upiWithHisab = LocalCommands.parse("Anand Patel ko hisaab ke saath UPI ID bhejo")
        assertEquals(
            AssistantAction.SendUpiId("Anand Patel", null, null, includeHisab = true),
            upiWithHisab
        )

        // Captions with customer name and pending balance
        val qrCaptionWithHisab = PaymentShare.qrCaption(null, "Anand Patel", 150000L)
        assertTrue(qrCaptionWithHisab.contains("*Customer:* Anand Patel"))
        assertTrue(qrCaptionWithHisab.contains("*Pending Hisab / Bakaya:* ₹1500"))

        val upiMsgWithHisab = PaymentShare.upiMessage(saved[0], "Raju Barai", null, "Anand Patel", 150000L)
        assertTrue(upiMsgWithHisab.contains("*Customer:* Anand Patel"))
        assertTrue(upiMsgWithHisab.contains("*Pending Hisab / Bakaya:* ₹1500"))
        assertTrue(upiMsgWithHisab.contains("*UPI ID:* raju@paytm"))
    }

    @Test
    fun interactiveTicketPdfHisabParsing() {
        assertEquals(
            AssistantAction.TicketPdfHisab("Anand", null),
            LocalCommands.parse("Anand ko aaj jo PDF bheja hai uska hisaab jodo")
        )
        assertEquals(
            AssistantAction.TicketPdfHisab("Anand Patel", 100L),
            LocalCommands.parse("Anand Patel ko jo PDF bheja hai uska hisaab jodo 100 rupaye")
        )
        assertEquals(
            AssistantAction.TicketPdfHisab("Jalal Bhai", null),
            LocalCommands.parse("Jalal Bhai ko jo ticket PDF bheja uska hisab jodo")
        )
    }
}
