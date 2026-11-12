package com.raju.aiassistant.domain.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class InboxCheckTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 11, 12)
    private fun at(day: Int, hour: Int = 10) = LocalDate.of(2026, 11, day).atStartOfDay(zone).toInstant().toEpochMilli() + hour * 3_600_000L

    @Test
    fun theUsersOwnWordsAreUnderstood() {
        // Exactly what was said on the phone, which had gone to the AI before.
        assertEquals(
            InboxQuery(InboxPeriod.WEEK, InboxKind.CREDIT),
            parseInboxQuestion("Mobile का message का inbox check करने बोल रहा हूँ. Credit वाला कितना message आया है वह बताओ."),
        )
        assertEquals(InboxQuery(InboxPeriod.WEEK, InboxKind.ALL), parseInboxQuestion("Notification नहीं बोल रहा हूं. मेरा mobile का inbox check करो, message का."))
        assertEquals(InboxQuery(InboxPeriod.TODAY, InboxKind.DEBIT), parseInboxQuestion("aaj kitne paise kate, bank ke message dekho"))
        assertEquals(InboxQuery(InboxPeriod.MONTH, InboxKind.CREDIT), parseInboxQuestion("is mahine ke credit wale sms batao"))
        assertEquals(InboxQuery(InboxPeriod.YESTERDAY, InboxKind.ALL), parseInboxQuestion("कल के SMS चेक करो"))
    }

    @Test
    fun otherSentencesAreNotInboxQuestions() {
        assertNull(parseInboxQuestion("kitne message aaye")) // notifications, as before
        assertNull(parseInboxQuestion("Rahul ko sms bhejo ki main aa raha hoon"))
        assertNull(parseInboxQuestion("credit card ka bill kab hai"))
        assertNull(parseInboxQuestion("torch jalao"))
    }

    private val inbox = listOf(
        SmsMessage("AX-SBIINB", "Rs.3,750.75 credited to your a/c XX1234 on 11-11-26. Avl Bal Rs.10,000.00", at(11)),
        SmsMessage("VM-HDFCBK", "INR 500.00 debited from A/c XX99; credited to VPA shop@upi. Avl bal INR 2,000", at(12, 9)),
        SmsMessage("VM-HDFCBK", "Rs 1,200 received in your a/c XX99 from RAHUL", at(12, 8)),
        SmsMessage("AD-AMAZON", "Your OTP is 123456 for Rs 999 credited. Do not share", at(12, 7)),
        SmsMessage("JM-JIOINF", "Recharge now and enjoy data", at(12, 6)),
        SmsMessage("AX-SBIINB", "Rs 9,999 credited to a/c", at(1)), // older than a week
    )

    @Test
    fun creditsAreCountedWithTheirTotalAndTheBalanceIsNotTakenForTheAmount() {
        val q = InboxQuery(InboxPeriod.WEEK, InboxKind.CREDIT)
        val s = summarizeInbox(inbox, q, today, zone)
        assertEquals(5, s.total)
        assertEquals(listOf(120000L, 375075L), s.credits.map { it.paise })
        assertEquals(listOf(50000L), s.debits.map { it.paise }) // "credited to VPA" is still money going out
        val said = spokenInboxAnswer(q, s, zone)
        assertTrue(said, said.startsWith("पिछले 7 दिनों में पैसे जमा होने के 2 संदेश आए, कुल ₹4,950.75"))
        assertTrue(said, said.contains("₹1,200 — VM-HDFCBK, 12 नवंबर"))
        assertTrue(said, !said.contains("XX1234") && !said.contains("123456")) // no account numbers or OTPs read out
    }

    @Test
    fun todayAndTheWholeInboxAreSummed() {
        val q = InboxQuery(InboxPeriod.TODAY, InboxKind.ALL)
        val said = spokenInboxAnswer(q, summarizeInbox(inbox, q, today, zone), zone)
        assertTrue(said, said.startsWith("आज कुल 4 SMS आए।"))
        assertTrue(said, said.contains("जमा (credit): 1 संदेश, कुल ₹1,200") && said.contains("कटे (debit): 1 संदेश, कुल ₹500"))
        val none = InboxQuery(InboxPeriod.YESTERDAY, InboxKind.DEBIT)
        assertEquals("कल पैसे कटने (debit) का कोई बैंक संदेश नहीं मिला। कुल 1 SMS देखे।", spokenInboxAnswer(none, summarizeInbox(inbox, none, today, zone), zone))
    }
}
