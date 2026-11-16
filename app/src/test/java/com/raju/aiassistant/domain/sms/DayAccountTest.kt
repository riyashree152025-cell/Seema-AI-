package com.raju.aiassistant.domain.sms

import com.raju.aiassistant.domain.assistant.AssistantAction
import com.raju.aiassistant.domain.assistant.LocalCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** "15 तारीख का कितना कितना डेबिट हुआ, कितना कितना क्रेडिट हुआ": one day's money in and out, amount by amount. */
class DayAccountTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 11, 16)
    private fun at(month: Int, day: Int, hour: Int) = LocalDate.of(2026, month, day).atStartOfDay(zone).toInstant().toEpochMilli() + hour * 3_600_000L

    private val said = "मेरे मोबाइल से मैसेज को ट्रैक करो, 15 तारीख का कितना कितना डेबिट हुआ कितना कितना क्रेडिट हुआ"

    @Test
    fun theUsersSentenceIsTheDaysAccountNotAPaymentOfFifteenRupees() {
        assertNull(parsePaymentQuestion(said)) // it had been taken as "₹15 aaya?"
        assertEquals(InboxQuery(InboxPeriod.YESTERDAY, InboxKind.ALL, LocalDate.of(2026, 11, 15)), parseInboxQuestion(said, today))
        assertEquals(
            InboxQuery(InboxPeriod.DAY, InboxKind.ALL, LocalDate.of(2026, 11, 12)),
            parseInboxQuestion("12 tarikh ka kitna debit kitna credit hua message dekho", today),
        )
        assertEquals(InboxQuery(InboxPeriod.YESTERDAY, InboxKind.ALL), parseInboxQuestion("कल का कितना कितना डेबिट हुआ कितना कितना क्रेडिट हुआ, मैसेज देखो", today))
        val action = LocalCommands.parse(said)
        assertTrue(action.toString(), action is AssistantAction.InboxCheck && action.date != null)
        // A real refund question with a date in it still finds the amount, not the date.
        assertEquals(PaymentQuery(375000), parsePaymentQuestion("15 तारीख का 3750 का रिफंड आया कि नहीं"))
    }

    @Test
    fun theRouterCanAskForOneDay() {
        assertEquals(
            AssistantAction.InboxCheck(InboxPeriod.DAY, InboxKind.ALL, LocalDate.of(2026, 11, 15)),
            com.raju.aiassistant.domain.assistant.IntentRouter.toAction("""{"type":"inbox_check","period":"day","kind":"all","date":"2026-11-15"}"""),
        )
        // A date without "day" still means that day.
        assertEquals(
            AssistantAction.InboxCheck(InboxPeriod.DAY, InboxKind.DEBIT, LocalDate.of(2026, 11, 15)),
            com.raju.aiassistant.domain.assistant.IntentRouter.toAction("""{"type":"inbox_check","period":"week","kind":"debit","date":"2026-11-15"}"""),
        )
    }

    @Test
    fun yesterdayAsTheRecogniserWritesIt() {
        listOf(
            "कल का एसएमएस ट्रैक करके कितना डेबिट हुआ कितना क्रेडिट हुआ बोलने बोल रहा हूँ",
            "कलका एसएमएस देखो कितना डेबिट हुआ",
            "काल का मैसेज देखो कितना क्रेडिट हुआ",
            "कॉल का SMS चेक करो कितना डेबिट हुआ",
            "call ka sms check karo kitna debit hua",
            "पिछले दिन का बैंक मैसेज देखो",
        ).forEach { assertEquals(it, InboxPeriod.YESTERDAY, parseInboxQuestion(it, today)?.period) }
        assertEquals(InboxPeriod.WEEK, parseInboxQuestion("बैंक के मैसेज देखो कितना डेबिट हुआ", today)?.period)
    }

    @Test
    fun daysAreReadAsDates() {
        assertEquals(LocalDate.of(2026, 11, 15), namedDay("15 तारीख का", today))
        assertEquals(LocalDate.of(2026, 11, 15), namedDay("15th ka", today))
        assertEquals(LocalDate.of(2026, 10, 28), namedDay("28 tarikh", today)) // not reached yet this month
        assertEquals(LocalDate.of(2026, 10, 3), namedDay("3 अक्टूबर को", today))
        assertEquals(LocalDate.of(2026, 11, 14), namedDay("परसों का हिसाब", today))
        assertNull(namedDay("1500 रुपये", today))
        assertNull(namedDay("15 rupaye", today))
    }

    private val inbox = listOf(
        SmsMessage("VM-HDFCBK", "INR 500.00 debited from A/c XX99 to VPA shop@upi. Avl bal INR 2,000", at(11, 15, 9)),
        SmsMessage("AX-SBIINB", "Rs.250 debited from a/c XX12 for UPI. Bal Rs 1,750", at(11, 15, 13)),
        SmsMessage("AX-SBIINB", "Rs.1,200 credited to your a/c XX12 by RAHUL", at(11, 15, 18)),
        SmsMessage("AX-SBIINB", "Rs.9,999 credited to your a/c XX12", at(11, 14, 18)), // another day
        SmsMessage("AX-SBIINB", "Rs.7,777 debited from a/c XX12", at(11, 16, 1)), // another day
    )

    @Test
    fun everyAmountOfThatDayIsSaidWithTheTotals() {
        val q = InboxQuery(InboxPeriod.DAY, InboxKind.ALL, LocalDate.of(2026, 11, 15))
        val s = summarizeInbox(inbox, q, today, zone)
        assertEquals(3, s.total)
        val text = spokenInboxAnswer(q, s, zone)
        assertTrue(text, text.startsWith("15 नवंबर को कुल 3 SMS आए।"))
        assertTrue(text, text.contains("कटे (debit): 2 बार, कुल ₹750 — ₹250 (AX-SBIINB); ₹500 (VM-HDFCBK)।"))
        assertTrue(text, text.contains("जमा (credit): 1 बार, कुल ₹1,200 — ₹1,200 (AX-SBIINB)।"))
        assertFalse(text, text.contains("9,999") || text.contains("7,777") || text.contains("XX12"))
    }
}
