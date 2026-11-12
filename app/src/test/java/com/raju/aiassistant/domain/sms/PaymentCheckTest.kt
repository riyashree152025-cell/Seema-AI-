package com.raju.aiassistant.domain.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PaymentCheckTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(day: Int) = LocalDate.of(2026, 11, day).atStartOfDay(zone).toInstant().toEpochMilli() + 10 * 3_600_000L

    @Test
    fun theQuestionIsUnderstoodWithItsAmount() {
        assertEquals(PaymentQuery(375075), parsePaymentQuestion("सीमा 3750.75 का रिफंड आया कि नहीं चेक करो"))
        assertEquals(PaymentQuery(375075), parsePaymentQuestion("3,750.75 refund aaya kya"))
        assertEquals(PaymentQuery(120000), parsePaymentQuestion("Rs 1200 wapas aaya ki nahi"))
        assertEquals(PaymentQuery(50000), parsePaymentQuestion("५०० रुपये जमा हुए क्या"))
        assertNull(parsePaymentQuestion("3750 ka ticket book karo")) // no refund question
        assertNull(parsePaymentQuestion("refund aaya kya")) // no amount
        assertNull(parsePaymentQuestion("10 baje ka 3750 refund aaya kya")) // two numbers: unclear
    }

    @Test
    fun aRefundIsFoundAndSaidWithItsDayAndSender() {
        val inbox = listOf(
            SmsMessage("AX-SBIINB", "Your a/c XX1234 is debited for Rs.3,750.75 on 05-11-26 to IRCTC. Not you? Call 1800", at(5)),
            SmsMessage("AX-SBIINB", "Refund of Rs.3,750.75 from IRCTC has been credited to your a/c XX1234 on 10-11-26.", at(10)),
            SmsMessage("VM-HDFCBK", "Rs 999.00 credited to a/c", at(9)),
        )
        val hits = findPayments(inbox, PaymentQuery(375075))
        assertEquals(listOf(MoneyDirection.CREDIT, MoneyDirection.DEBIT), hits.map { it.direction })
        val said = spokenPaymentAnswer(PaymentQuery(375075), hits, zone, 60)
        assertTrue(said, said.startsWith("हाँ, ₹3,750.75 आपके खाते में आ गए हैं") && said.contains("10 नवंबर") && said.contains("AX-SBIINB"))
    }

    @Test
    fun onlyTheDebitMeansTheRefundHasNotCome() {
        val inbox = listOf(SmsMessage("AX-SBIINB", "INR 3750.75 debited from A/c XX1234 for IRCTC", at(5)))
        val said = spokenPaymentAnswer(PaymentQuery(375075), findPayments(inbox, PaymentQuery(375075)), zone, 60)
        assertTrue(said, said.contains("कटने का संदेश 5 नवंबर") && said.contains("रिफंड या जमा होने का कोई संदेश"))
    }

    @Test
    fun otherAmountsAndOtpMessagesAreNeverUsed() {
        val inbox = listOf(
            SmsMessage("AX-SBIINB", "Rs.3,750.00 credited to your a/c", at(6)), // not the same paise
            SmsMessage("AX-SBIINB", "OTP 482913 for Rs.3,750.75 at IRCTC. Do not share.", at(7)),
            SmsMessage("JD-FRIEND", "bhai 3750.75 bhej dena", at(8)), // not a bank-style amount
        )
        assertTrue(findPayments(inbox, PaymentQuery(375075)).isEmpty())
        assertTrue(spokenPaymentAnswer(PaymentQuery(375075), emptyList(), zone, 60).contains("कोई बैंक संदेश नहीं मिला"))
    }

    @Test
    fun amountsAreWrittenTheIndianWay() {
        assertEquals("3,750.75", rupees(375075))
        assertEquals("1,23,456", rupees(12345600))
        assertEquals("500", rupees(50000))
    }
}
