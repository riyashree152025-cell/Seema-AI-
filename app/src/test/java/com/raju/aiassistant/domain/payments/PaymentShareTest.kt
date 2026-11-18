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
}
