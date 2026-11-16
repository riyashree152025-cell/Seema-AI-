package com.raju.aiassistant.domain.sms

import com.raju.aiassistant.domain.language.SeemaLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyAlertTest {
    @Test
    fun bankSmsAndUpiNotificationsAreRead() {
        assertEquals(
            MoneyIn(50000, null, "IndusInd Bank"),
            moneyCreditIn("JM-INDUSB", "Your IndusInd Bank A/C 1234 is credited by INR 500.00 on 16-11-26 from VPA rahul@okaxis. Avl Bal INR 2,345.00"),
        )
        assertEquals(MoneyIn(50000, "Rahul Kumar", null), moneyCreditIn("RAHUL KUMAR paid you ₹500.00", "Tap to view"))
        assertEquals(MoneyIn(50000, "Rahul", null), moneyCreditIn("PhonePe", "Received ₹500 from Rahul"))
        assertEquals(MoneyIn(125050, null, "SBI"), moneyCreditIn("AX-SBIINB", "Dear Customer, Rs.1,250.50 credited to a/c XX99 on 16Nov by UPI ref 12345. -SBI"))
    }

    @Test
    fun otherMoneyMessagesAreNotAnnounced() {
        assertNull(moneyCreditIn("AX-HDFCBK", "INR 500.00 debited from A/c XX99 to VPA shop@upi"))
        assertNull(moneyCreditIn("Rahul", "Rahul requested ₹500 from you"))
        assertNull(moneyCreditIn("GPay", "You won ₹50 cashback!"))
        assertNull(moneyCreditIn("AX-HDFCBK", "Pre-approved loan of Rs 5,00,000 credited instantly. Apply now"))
        assertNull(moneyCreditIn("VM-ICICIB", "123456 is your OTP for Rs 500 credit. Do not share"))
        assertNull(moneyCreditIn("Mummy", "500 रुपये भेज देना"))
    }

    @Test
    fun theLineIsSaidOncePerPayment() {
        val m = MoneyIn(50000, "Rahul", "IndusInd Bank")
        assertEquals("राजू, आपके IndusInd Bank खाते में अभी 500 रुपये आए हैं — Rahul ने भेजे।", spokenMoneyIn(m, "राजू", SeemaLanguage.HINDI))
        assertTrue(spokenMoneyIn(m, "রাজু", SeemaLanguage.BENGALI).contains("500 টাকা এসেছে"))
        val d = MoneyAlertDeduper()
        assertTrue(d.shouldSay(50000, 0))
        assertFalse(d.shouldSay(50000, 60_000)) // the UPI app's notification for the same payment
        assertTrue(d.shouldSay(20000, 60_000))
        assertTrue(d.shouldSay(50000, 10 * 60_000))
        assertTrue(isMoneyApp("com.phonepe.app") && isMoneyApp("com.google.android.apps.messaging") && !isMoneyApp("com.instagram.android"))
    }
}
