package com.raju.aiassistant.domain.assistant

import com.raju.aiassistant.domain.ledger.LedgerCommand
import com.raju.aiassistant.domain.ledger.LedgerKind
import com.raju.aiassistant.domain.ledger.parseLedger
import com.raju.aiassistant.domain.sms.MoneyAsk
import com.raju.aiassistant.domain.sms.parseMoneyQuestion
import com.raju.aiassistant.domain.sms.parsePaymentQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Validates hardened mobile routing:
 * 1. Keyword collision fixes ("हिसाब", "खाता", "लेना है", "देना है" vs Bank SMS).
 * 2. Ticket counts ("दो ticket", "2 टिकट") never parsed as ₹2 in Ledger.
 * 3. Small amounts < 50 without explicit currency are rejected; with currency allowed.
 * 4. Strict 4-direction Ledger math ("से ... लेना है / बाकी है" -> GAVE / Udhaar).
 */
class HardenedMobileRoutingTest {

    private val today = LocalDate.of(2026, 11, 24)

    // =========================================================================
    // 1. Keyword Collisions: "हिसाब" vs Bank SMS
    // =========================================================================

    @Test
    fun hisabAndKhataDoNotRouteToBankSmsWithoutExplicitBankKeywords() {
        // "आनंद पटेल का हिसाब बताओ" -> Khata / Ledger balance, NOT Bank SMS
        assertNull("Hisab query must not match Bank SMS", parseMoneyQuestion("आनंद पटेल का हिसाब बताओ", today))
        assertNull("Khata query must not match Bank SMS", parseMoneyQuestion("आनंद का खाता दिखाओ", today))
        assertNull("Lena hai must not match Bank SMS", parseMoneyQuestion("राहुल से 500 लेना है", today))
        assertNull("Dena hai must not match Bank SMS", parseMoneyQuestion("मोहन को 500 देना है", today))
        assertNull("PaymentCheck must not match Hisab", parsePaymentQuestion("आनंद का हिसाब बताओ"))

        // Through LocalCommands, these route to Ledger, NOT MoneyQuestion
        val hisabAction = LocalCommands.parse("आनंद पटेल का हिसाब बताओ")
        assertTrue("Expected LedgerBalance, got $hisabAction", hisabAction is AssistantAction.LedgerBalance)

        val lenaAction = LocalCommands.parse("राहुल से 500 रुपये लेना है")
        assertTrue("Expected LedgerRecord, got $lenaAction", lenaAction is AssistantAction.LedgerRecord)
    }

    @Test
    fun explicitBankKeywordsDoRouteToBankSms() {
        // Explicit "पैसा कटा" or "बैंक SMS" or "बैलेंस चेक"
        val q1 = parseMoneyQuestion("आज कितना पैसा कटा है", today)
        assertNotNull("Paisa kata should match Bank SMS", q1)
        assertTrue(q1 is MoneyAsk.Amounts)

        val a1 = LocalCommands.parse("आज कितना पैसा कटा है")
        assertTrue("Paisa kata routes to MoneyQuestion", a1 is AssistantAction.MoneyQuestion)

        val a2 = LocalCommands.parse("आज बैंक SMS में कितना पैसा आया")
        assertTrue("Bank SMS routes to Bank SMS checker (InboxCheck)", a2 is AssistantAction.InboxCheck)
    }

    // =========================================================================
    // 2. Ticket count vs Rupees: "दो ticket" / "2 टिकट" vs ₹2
    // =========================================================================

    @Test
    fun ticketCountsAreNeverParsedAsRupees() {
        assertNull("Do ticket must not be parsed as ₹2", parseLedger("आनंद से दो ticket लेना है"))
        assertNull("2 ticket must not be parsed as ₹2", parseLedger("आनंद से 2 ticket लेना है"))
        assertNull("2 टिकट must not be parsed as ₹2", parseLedger("राहुल से 2 टिकट लेना है"))
        assertNull("Do ticket must not be parsed as ₹2", parseLedger("दो ticket"))
        assertNull("2 टिकट must not be parsed as ₹2", parseLedger("2 टिकट"))
        assertNull("3 pass must not be parsed as ₹3", parseLedger("समीर से 3 pass लेना है"))
        assertNull("Ek seat must not be parsed as ₹1", parseLedger("एक सीट"))
    }

    @Test
    fun smallAmountsUnderFiftyRequireExplicitCurrency() {
        // Without currency context ("rupaye", "₹"), small numbers are rejected
        assertNull("2 without currency must be rejected", parseLedger("राहुल से 2 लेना है"))
        assertNull("5 without currency must be rejected", parseLedger("राहुल से पांच लेना है"))
        assertNull("10 without currency must be rejected", parseLedger("मोहन को 10 देना है"))

        // With explicit currency, even small numbers are accepted
        val r1 = parseLedger("राहुल से 2 रुपये लेना है")
        assertNotNull("2 with रुपये must be accepted", r1)
        assertEquals(LedgerCommand.Record("राहुल", 200L, LedgerKind.GAVE), r1)

        val r2 = parseLedger("Rahul se do rupaye lene hain")
        assertNotNull("2 with rupaye must be accepted", r2)
        assertEquals(LedgerCommand.Record("Rahul", 200L, LedgerKind.GAVE), r2)

        val r3 = parseLedger("मोहन को 10 रुपये दे दिए")
        assertNotNull("10 with रुपये must be accepted", r3)
        assertEquals(LedgerCommand.Record("मोहन", 1000L, LedgerKind.GAVE), r3)
    }

    @Test
    fun amountsFiftyAndAboveWorkWithOrWithoutExplicitCurrency() {
        // >= 50 rupees with verb
        val r1 = parseLedger("राहुल से 500 लेना है")
        assertNotNull(r1)
        assertEquals(LedgerCommand.Record("राहुल", 50000L, LedgerKind.GAVE), r1)

        val r2 = parseLedger("आनंद से 50 लेना बाकी है")
        assertNotNull(r2)
        assertEquals(LedgerCommand.Record("आनंद", 5000L, LedgerKind.GAVE), r2)

        val r3 = parseLedger("मोहन को 1200 दे दिए")
        assertNotNull(r3)
        assertEquals(LedgerCommand.Record("मोहन", 120000L, LedgerKind.GAVE), r3)
    }

    // =========================================================================
    // 3. Strict 4-Direction Ledger Math
    // =========================================================================

    @Test
    fun strictDirectionEnforcement() {
        // "से ... लेना है / बाकी है" -> GAVE (Udhaar / Receivable)
        val l1 = parseLedger("राहुल से 500 रुपये लेना है")
        assertEquals(LedgerCommand.Record("राहुल", 50000L, LedgerKind.GAVE), l1)

        val l2 = parseLedger("आनंद से 1500 बाकी है")
        assertEquals(LedgerCommand.Record("आनंद", 150000L, LedgerKind.GAVE), l2)

        val l3 = parseLedger("Mohan se 1500 lene baki hain")
        assertEquals(LedgerCommand.Record("Mohan", 150000L, LedgerKind.GAVE), l3)

        // "से ... मिल गए / मिले" -> GOT (Received)
        val l4 = parseLedger("राहुल से 500 रुपये मिल गए")
        assertEquals(LedgerCommand.Record("राहुल", 50000L, LedgerKind.GOT), l4)

        val l5 = parseLedger("Rahul se 500 mile")
        assertEquals(LedgerCommand.Record("Rahul", 50000L, LedgerKind.GOT), l5)

        // "को ... देना है / बाकी है" -> GOT (Payable)
        val l6 = parseLedger("मोहन को 500 रुपये देना है")
        assertEquals(LedgerCommand.Record("मोहन", 50000L, LedgerKind.GOT), l6)

        val l7 = parseLedger("राकेश को 300 बाकी है")
        assertEquals(LedgerCommand.Record("राकेश", 30000L, LedgerKind.GOT), l7)

        // "को ... दे दिया / दिए" -> GAVE (Paid)
        val l8 = parseLedger("मोहन को 500 दे दिए")
        assertEquals(LedgerCommand.Record("मोहन", 50000L, LedgerKind.GAVE), l8)

        val l9 = parseLedger("Mohan ko 500 diye")
        assertEquals(LedgerCommand.Record("Mohan", 50000L, LedgerKind.GAVE), l9)
    }
}
