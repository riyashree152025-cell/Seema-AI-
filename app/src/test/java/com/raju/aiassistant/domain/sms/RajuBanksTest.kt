package com.raju.aiassistant.domain.sms

import com.raju.aiassistant.domain.language.SeemaLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Raju's own banks (20 Nov): IndusInd (the SMS from his screenshot, word for word), Bank of Baroda and Union Bank
 * of India. Every credit is said out loud at once, in whole rupees; a debit is never announced, only answered when
 * he asks ("आज कितना कितना पैसा कटा", "सब मिला के बोलो").
 */
class RajuBanksTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 11, 20)
    private fun at(hour: Int, minute: Int) = today.atStartOfDay(zone).plusHours(hour.toLong()).plusMinutes(minute.toLong()).toInstant().toEpochMilli()

    private val indusCredit = "A/C *XX3587 credited by Rs 3475.00 from anandkp241089-1@okaxis. RRN:632354656706. Avl Bal:5045.94. Not you? Call 18602677777 - IndusInd bank"
    private val indusCreditPaise = "A/C *XX3587 credited by Rs 4654.01 from poweraccess.ppsl2533@axisbank. RRN:486412633246. Avl Bal:6427.25. Not you? Call 18602677777 - IndusInd bank"
    private val indusDebits = listOf(
        "A/C *XX3587 debited by Rs 1.00 towards paytmqr28100505010113oxzk8ijng1@paytm. RRN:319574775223. Avl Bal:10757.74. Not you? Call 18602677777 - IndusInd bank",
        "A/C *XX3587 debited by Rs 8984.50 towards paytm-862137775@ptybl. RRN:007187131869. Avl Bal:1773.24. Not you? Call 18602677777 - IndusInd bank",
        "A/C *XX3587 debited by Rs 3021.80 towards indianrailwayca848104.rzp@rxairtel. RRN:632411305013. Avl Bal:3405.45. Not you? Call 18602677777 - IndusInd bank",
    )
    private val bobCredit = "Rs.5700.00 Credited to A/c ...3587 thru UPI/632354656706 by anand@okaxis. Total Bal:Rs.9045.94CR. Avlbl Amt:Rs.9045.94(20-11-2026 12:21:10) - Bank of Baroda"
    private val bobDebit = "Rs.2400.00 Dr. from A/C XXXXXX3587 and Cr. to shop@ybl. Ref:632411305013. AvlBal:Rs3405.45(2026:11:20 11:03:12). Not you? Call 18005700-BOB"
    private val unionCredit = "A/c *3587 Credited for Rs:4500.00 on 20-11-2026 10:59:01 by Mob Bk ref no 486412633246 Avl Bal Rs:6427.25.Never Share OTP/PIN/CVV-Union Bank of India"
    private val unionDebit = "A/c *3587 Debited for Rs:2570.00 on 20-11-2026 11:03:01 by Mob Bk ref no 632411305013 Avl Bal Rs:3405.45.Never Share OTP/PIN/CVV-Union Bank of India"

    @Test
    fun everyCreditIsAnnouncedInWholeRupees() {
        assertEquals("आपके IndusInd Bank खाते में अभी 3,475 रुपये आए हैं।", spokenMoneyIn(moneyCreditIn("AD-INDUSB-S", indusCredit)!!, "", SeemaLanguage.HINDI))
        // "4654.01": no "दशमलव", and the UPI ID is not taken for a name ("poweraccess").
        val paise = moneyCreditIn("AD-INDUSB-S", indusCreditPaise)!!
        assertNull(paise.from)
        assertEquals("आपके IndusInd Bank खाते में अभी 4,654 रुपये आए हैं।", spokenMoneyIn(paise, "", SeemaLanguage.HINDI))
        assertEquals("आपके Bank of Baroda खाते में अभी 5,700 रुपये आए हैं।", spokenMoneyIn(moneyCreditIn("BX-BOBTXN", bobCredit)!!, "", SeemaLanguage.HINDI))
        // Union Bank's "Never Share OTP/PIN/CVV" line used to make the whole SMS look like an OTP.
        assertEquals("আপনার Union Bank অ্যাকাউন্টে এইমাত্র 4,500 টাকা এসেছে।", spokenMoneyIn(moneyCreditIn("AD-UNIONB", unionCredit)!!, "", SeemaLanguage.BENGALI))
    }

    @Test
    fun aDebitIsNeverAnnounced() {
        (indusDebits + bobDebit + unionDebit).forEach { assertNull(it, moneyCreditIn("BANK", it)) }
    }

    @Test
    fun aRealOtpIsStillSkipped() {
        assertTrue(isOtp("123456 is your OTP for a transaction of Rs 500. Do not share it with anyone."))
        assertTrue(!isOtp(unionCredit) && !isOtp(unionDebit))
    }

    private val day = listOf(
        SmsMessage("AD-INDUSB-S", indusDebits[0], at(10, 0)),
        SmsMessage("AD-INDUSB-S", indusDebits[1], at(10, 1)),
        SmsMessage("AD-INDUSB-S", indusCreditPaise, at(10, 59)),
        SmsMessage("AD-INDUSB-S", indusDebits[2], at(11, 3)),
        SmsMessage("AD-UNIONB", unionDebit, at(12, 30)),
        SmsMessage("BX-BOBTXN", bobDebit, at(13, 10)),
        SmsMessage("BX-BOBTXN", bobCredit, at(13, 20)),
    )

    @Test
    fun todaysDebitsOneByOneThenTheTotal() {
        val events = moneyEvents(day, emptyList())
        val each = parseMoneyQuestion("आज कितना कितना पैसा कटा है", today) as MoneyAsk.Amounts
        assertEquals(MoneyFlow.OUT, each.flow)
        assertEquals("आज 5 बार पैसा कटा: 1, 8,985, 3,022, 2,570 और 2,400 रुपये — कुल 16,977 रुपये।", spokenMoneyAmounts(each, events, today, SeemaLanguage.HINDI))
        val total = parseMoneyQuestion("ठीक है सब रुको, सब मिला के बोलो आज कितना पैसा कटा है", today) as MoneyAsk.Amounts
        assertTrue(total.totalOnly)
        assertEquals("आज कुल 16,977 रुपये कटे — 5 बार में।", spokenMoneyAmounts(total, events, today, SeemaLanguage.HINDI))
        val credits = parseMoneyQuestion("आज कितना कितना पैसा आया", today) as MoneyAsk.Amounts
        assertEquals("आज 2 पेमेंट आए: 4,654 और 5,700 रुपये — कुल 10,354 रुपये।", spokenMoneyAmounts(credits, events, today, SeemaLanguage.HINDI))
    }

    @Test
    fun debitsOfTheLastTwoHours() {
        val now = at(13, 30)
        val ask = parseMoneyQuestion("2 घंटे में कितना पैसा कटा", today) as MoneyAsk.Amounts
        val w = windowOf(ask.time, now, zone)
        val events = moneyEvents(day.filter { it.atMillis in w.fromMillis until w.untilMillis }, emptyList())
        assertEquals("पिछले 2 घंटे में 2 बार पैसा कटा: 2,570 और 2,400 रुपये — कुल 4,970 रुपये।", spokenMoneyAmounts(ask, events, today, SeemaLanguage.HINDI))
    }

    @Test
    fun whoSentItFindsTheUpiIdAndWholeRupeesAreEnough() {
        val events = moneyEvents(day + SmsMessage("AD-INDUSB-S", indusCredit, at(12, 21)), emptyList())
        assertEquals(
            "3,475 रुपये आज दोपहर 12:21 बजे UPI ID anandkp241089-1 ने भेजे।",
            spokenMoneyWho(parseMoneyQuestion("3475 किसका आया", today) as MoneyAsk.Who, events, today, zone, SeemaLanguage.HINDI),
        )
        // "4654" finds Rs 4654.01.
        assertEquals(
            "4,654 रुपये आज सुबह 10:59 बजे UPI ID poweraccess.ppsl2533 ने भेजे।",
            spokenMoneyWho(parseMoneyQuestion("4654 किसका आया", today) as MoneyAsk.Who, events, today, zone, SeemaLanguage.HINDI),
        )
    }
}
