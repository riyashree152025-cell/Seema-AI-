package com.raju.aiassistant.domain.sms

import com.raju.aiassistant.domain.assistant.AssistantAction
import com.raju.aiassistant.domain.assistant.LocalCommands
import com.raju.aiassistant.domain.language.SeemaLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Raju, 20 Nov: "कल कितना कितना पैसा आया — सिर्फ़ अमाउंट", "3630 किसका आया", "1 घंटे में किसको भेजा", सुबह / शाम / कोई भी तारीख. */
class MoneyQuestionTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 11, 20)
    private fun at(day: Int, hour: Int, minute: Int = 0) =
        LocalDate.of(2026, 11, day).atStartOfDay(zone).plusHours(hour.toLong()).plusMinutes(minute.toLong()).toInstant().toEpochMilli()
    private val now = at(20, 15, 0) // 20 Nov, 3 PM

    private fun ask(s: String) = parseMoneyQuestion(s, today)

    // ------------------------------------------------------------------ understanding the question

    @Test
    fun yesterdaysAmountsInHisOwnWords() {
        val yesterday = MoneyAsk.Amounts(MoneyFlow.IN, MoneyTime.Day(today.minusDays(1)))
        assertEquals(yesterday, ask("अच्छा सीमा चेक करो कल कितना कितना पैसा आया है"))
        assertEquals(yesterday, ask("kal kitna paisa aaya"))
        assertEquals(yesterday, ask("কাল কত টাকা এসেছে"))
        assertEquals(MoneyAsk.Amounts(MoneyFlow.IN, MoneyTime.Day(today)), ask("आज कितना पैसा आया"))
        assertEquals(MoneyAsk.Amounts(MoneyFlow.IN, MoneyTime.Day(today)), ask("कितना पैसा आया")) // no day: today
    }

    @Test
    fun hoursMinutesAndPartsOfTheDay() {
        assertEquals(MoneyAsk.Amounts(MoneyFlow.IN, MoneyTime.LastMinutes(60)), ask("1 घंटे में कितना पैसा आया"))
        assertEquals(MoneyAsk.Amounts(MoneyFlow.IN, MoneyTime.LastMinutes(60)), ask("एक घंटे में कितना आया")) // no money word: hours make it clear
        assertEquals(MoneyAsk.Amounts(MoneyFlow.IN, MoneyTime.LastMinutes(120)), ask("pichle do ghante mein kitna paisa aaya"))
        assertEquals(MoneyAsk.Amounts(MoneyFlow.IN, MoneyTime.LastMinutes(300)), ask("5 घंटे में कितने पैसे आए"))
        assertEquals(MoneyAsk.Amounts(MoneyFlow.IN, MoneyTime.LastMinutes(30)), ask("आधे घंटे में कोई पैसा आया? कितना"))
        assertEquals(MoneyAsk.Amounts(MoneyFlow.IN, MoneyTime.LastMinutes(90)), ask("डेढ़ घंटे में कितना पैसा आया"))
        assertEquals(MoneyAsk.Amounts(MoneyFlow.IN, MoneyTime.LastMinutes(45)), ask("45 मिनट में कितना पैसा आया"))
        assertEquals(MoneyAsk.Amounts(MoneyFlow.IN, MoneyTime.Day(today, DayPart.MORNING)), ask("आज सुबह कितना पैसा आया"))
        assertEquals(MoneyAsk.Amounts(MoneyFlow.IN, MoneyTime.Day(today, DayPart.EVENING)), ask("शाम को कितना आया"))
        assertEquals(MoneyAsk.Amounts(MoneyFlow.IN, MoneyTime.Day(today.minusDays(1), DayPart.EVENING)), ask("कल शाम कितना पैसा आया"))
        assertEquals(MoneyAsk.Amounts(MoneyFlow.IN, MoneyTime.Day(today.minusDays(1), DayPart.NIGHT)), ask("kal raat kitna paisa aaya"))
        assertEquals(MoneyAsk.Amounts(MoneyFlow.IN, MoneyTime.Day(today, DayPart.AFTERNOON)), ask("দুপুরে কত টাকা এসেছে"))
    }

    @Test
    fun anyDate() {
        assertEquals(MoneyAsk.Amounts(MoneyFlow.IN, MoneyTime.Day(LocalDate.of(2026, 11, 15))), ask("15 तारीख को कितना पैसा आया"))
        assertEquals(MoneyAsk.Amounts(MoneyFlow.IN, MoneyTime.Day(LocalDate.of(2026, 10, 3), DayPart.MORNING)), ask("3 अक्टूबर की सुबह कितना पैसा आया"))
        assertEquals(MoneyAsk.Amounts(MoneyFlow.OUT, MoneyTime.Day(today.minusDays(2))), ask("परसों कितना पैसा भेजा"))
    }

    @Test
    fun sentAndToWhom() {
        assertEquals(MoneyAsk.Amounts(MoneyFlow.OUT, MoneyTime.LastMinutes(60), withNames = true), ask("1 घंटे में मैंने किसको पैसा भेजा है"))
        assertEquals(MoneyAsk.Amounts(MoneyFlow.OUT, MoneyTime.Day(today.minusDays(1))), ask("kal kitna paisa kata"))
        assertEquals(MoneyAsk.Amounts(MoneyFlow.IN, MoneyTime.Day(today), withNames = true), ask("आज किस किसने पैसा भेजा"))
        assertEquals(MoneyAsk.Amounts(MoneyFlow.BOTH, MoneyTime.Day(today)), ask("आज कितना पैसा आया और कितना गया"))
    }

    @Test
    fun whoSentThatAmount() {
        assertEquals(MoneyAsk.Who(363000, MoneyFlow.IN), ask("3630 किसका आया"))
        assertEquals(MoneyAsk.Who(363000, MoneyFlow.IN), ask("3630 rupaye kisne bheje"))
        assertEquals(MoneyAsk.Who(240000, MoneyFlow.OUT), ask("2400 किसको भेजा"))
        assertEquals(MoneyAsk.Who(363000, MoneyFlow.IN, MoneyTime.Day(today.minusDays(1))), ask("कल 3630 किसका आया था"))
        assertEquals(MoneyAsk.Who(363000, MoneyFlow.IN, MoneyTime.Day(today, DayPart.MORNING)), ask("सुबह 10 बजे 3630 किसका आया")) // 10 बजे is a time
        assertEquals(MoneyAsk.Who(363000, MoneyFlow.IN), ask("৩৬৩০ টাকা কে পাঠিয়েছে"))
    }

    @Test
    fun otherSentencesAreNotMoneyQuestions() {
        listOf(
            "last call kiski aayi thi", "kitne message aaye", "Rahul ko 500 bhej do", "मेरा QR भेज दो", "torch jalao",
            "2 बजे किसका फोन आया", "कल कौन आया था", "आज मौसम कैसा है", "Jalal bhai ko UPI ID bhejo",
        ).forEach { assertNull(it, ask(it)) }
        assertNull(ask("3750 का रिफंड आया कि नहीं")) // the payment check
        assertNull(ask("कल का कितना कितना डेबिट हुआ कितना कितना क्रेडिट हुआ, मैसेज देखो")) // the inbox report
    }

    @Test
    fun theAlertIsTurnedOnByVoice() {
        assertTrue(asksMoneyAlert("अच्छा ठीक है, कोई पैसा आता है तो मेरे को बोल के बताना"))
        assertTrue(asksMoneyAlert("paisa aaye to bata dena"))
        assertTrue(asksMoneyAlert("টাকা এলে বলে দিও"))
        assertFalse(asksMoneyAlert("कल कितना पैसा आया"))
        assertEquals(AssistantAction.MoneyAlertOn, LocalCommands.parse("कोई पैसा आता है तो मेरे को बोल के बताना"))
    }

    @Test
    fun theyReachTheEngineAsMoneyQuestions() {
        listOf("कल कितना कितना पैसा आया", "3630 किसका आया", "1 घंटे में मैंने किसको पैसा भेजा", "शाम को कितना पैसा आया").forEach {
            assertTrue(it, LocalCommands.parse(it) is AssistantAction.MoneyQuestion)
        }
    }

    // ------------------------------------------------------------------ who, from the bank's SMS

    @Test
    fun theNameIsReadFromTheWaysBanksWriteIt() {
        assertEquals("Rahul Kumar", counterpartyOf("Rs.3,630.00 credited to your A/c XX12 by RAHUL KUMAR on 19-11-26. UPI Ref 123456", MoneyFlow.IN))
        assertEquals("Rahul Kumar", counterpartyOf("INR 3630.00 received from RAHUL KUMAR via UPI. Avl bal INR 9000", MoneyFlow.IN))
        assertEquals("Sunil Das", counterpartyOf("Your A/c XX12 is credited with Rs 3630.00 Info: UPI/CR/432112345678/SUNIL DAS/SBIN/sunil@oksbi", MoneyFlow.IN))
        assertEquals("rahul.k (UPI ID)", counterpartyOf("Rs 3630 credited to a/c XX12 from VPA rahul.k@okaxis on 19-11", MoneyFlow.IN))
        assertEquals("Shree Travels", counterpartyOf("A/c XX12 debited by 2400.0 on 19Nov26 trf to SHREE TRAVELS Refno 4321. -SBI", MoneyFlow.OUT))
        assertEquals("Amit", counterpartyOf("Dear Customer, your A/c X1234 has been debited for Rs.500.00 on 19-11-26; AMIT credited. UPI ref 123", MoneyFlow.OUT))
        assertEquals("shop (UPI ID)", counterpartyOf("INR 500.00 debited from A/c XX99 to VPA shop@upi. Avl bal INR 2,000", MoneyFlow.OUT))
        // The big banks' own formats.
        assertEquals("9876543210 (UPI ID)", counterpartyOf("Money Received - INR 3630.00 in your HDFC Bank A/c xx1234 on 19-11-26 by A/c linked to VPA 9876543210@ybl (UPI Ref No 432112345678).", MoneyFlow.IN))
        assertEquals("Rahul Kumar", counterpartyOf("Sent Rs.500.00 From HDFC Bank A/C x1234 To RAHUL KUMAR On 19/11/26 Ref 432112345678 Not You? Call 18002586161", MoneyFlow.OUT))
        assertEquals("Rahul Kumar", counterpartyOf("ICICI Bank Acct XX123 debited for Rs 500.00 on 19-Nov-26; RAHUL KUMAR credited. UPI:432112345678.", MoneyFlow.OUT))
        assertEquals("Rahul Kumar", counterpartyOf("Dear Customer, Acct XX123 is credited with Rs 3630.00 on 19-Nov-26 from RAHUL KUMAR. UPI:432112345678-ICICI Bank.", MoneyFlow.IN))
        assertEquals("Rahul Kum", counterpartyOf("INR 3630.00 credited to A/c no. XX1234 on 19-11-26 at 18:40:12 IST. Info- UPI/P2A/432112345678/RAHUL KUM/Axis Bank", MoneyFlow.IN))
        assertEquals("rahul (UPI ID)", counterpartyOf("Received Rs.3630.00 in your Kotak Bank a/c XX1234 from rahul@okicici on 19-11-26.UPI Ref:432112345678.", MoneyFlow.IN))
        assertEquals("Rahul Kumar", counterpartyOf("Dear SBI User, your A/c X1234-credited by Rs.3630 on 19Nov26 transfer from RAHUL KUMAR Ref No 432112345678 -SBI", MoneyFlow.IN))
        assertEquals("Rahul Kumar", counterpartyOf("Dear UPI user A/C X1234 debited by 500.0 on date 19Nov26 trf to RAHUL KUMAR Refno 432112345678. -SBI", MoneyFlow.OUT))
        assertEquals("Rahul Kumar", counterpartyOf("Your a/c no. XXXXXXXX1234 is credited by Rs.3630.00 on 19-11-26 by a/c linked to mobile 98XXXXXX10-RAHUL KUMAR (IMPS Ref no 432112345678).", MoneyFlow.IN))
        // Nobody named: no guess.
        assertNull(counterpartyOf("A/c *1234 Debited for Rs:500.00 on 19-11-2026 by Mob Bk ref no 432112345678 Avl Bal Rs:2000.00.", MoneyFlow.OUT))
        assertNull(counterpartyOf("Rs.3630 deposited in your account by cash at branch", MoneyFlow.IN))
        assertNull(counterpartyOf("Rs.9,999 credited to your a/c XX12", MoneyFlow.IN))
        assertNull(counterpartyOf("Rs.250 debited from a/c XX12 for UPI. Bal Rs 1,750", MoneyFlow.OUT))
        assertNull(counterpartyOf("Rs 500 credited to your a/c XX12 by UPI Ref 998877", MoneyFlow.IN))
    }

    private val sms = listOf(
        SmsMessage("AX-SBIINB", "Rs.2,400.00 credited to your A/c XX12 by RAHUL on 19-11", at(19, 10, 5)),
        SmsMessage("AX-SBIINB", "Rs.3,630.00 credited to your A/c XX12. UPI Ref 445566", at(19, 18, 40)), // no name in the SMS
        SmsMessage("VM-HDFCBK", "Rs.500.00 debited from A/c XX99 trf to AMIT Refno 1", at(20, 14, 20)),
        SmsMessage("VM-HDFCBK", "Rs.1,200.00 credited to your A/c XX99 by SUNIL on 20-11", at(20, 14, 40)),
        SmsMessage("VM-HDFCBK", "Your OTP is 123456 for Rs.5,000", at(20, 14, 50)),
    )
    private val notes = listOf(
        MoneyNote(363000, MoneyFlow.IN, at(19, 18, 41), "Pappu Yadav"), // PhonePe: "Pappu Yadav paid you ₹3,630"
        MoneyNote(363000, MoneyFlow.IN, at(19, 18, 41), null), // the SMS app's notification of the same payment
        MoneyNote(70000, MoneyFlow.IN, at(20, 9, 0), "Ravi"), // a bank that sent no SMS
    )

    @Test
    fun smsAndNotesAreOnePaymentEachAndANoteGivesTheMissingName() {
        val e = moneyEvents(sms, notes)
        assertEquals(listOf(240000L, 363000L, 70000L, 50000L, 120000L), e.map { it.paise }) // oldest first, no OTP, no double
        assertEquals("Pappu Yadav", e.first { it.paise == 363000L }.who)
    }

    // ------------------------------------------------------------------ what she says

    private fun events(time: MoneyTime): List<MoneyEvent> {
        val w = windowOf(time, now, zone)
        return moneyEvents(sms.filter { it.atMillis in w.fromMillis until w.untilMillis }, notes.filter { it.atMillis in w.fromMillis until w.untilMillis })
    }

    @Test
    fun onlyTheAmounts() {
        val yesterday = MoneyAsk.Amounts(MoneyFlow.IN, MoneyTime.Day(today.minusDays(1)))
        assertEquals("कल 2 पेमेंट आए: 2,400 और 3,630 रुपये — कुल 6,030 रुपये।", spokenMoneyAmounts(yesterday, events(yesterday.time), today, SeemaLanguage.HINDI))
        assertEquals("গতকাল 2টা পেমেন্ট এসেছে: 2,400 আর 3,630 টাকা — মোট 6,030 টাকা।", spokenMoneyAmounts(yesterday, events(yesterday.time), today, SeemaLanguage.BENGALI))
        val hour = MoneyAsk.Amounts(MoneyFlow.IN, MoneyTime.LastMinutes(60))
        assertEquals("पिछले 1 घंटे में एक पेमेंट आया: 1,200 रुपये।", spokenMoneyAmounts(hour, events(hour.time), today, SeemaLanguage.HINDI))
        val morning = MoneyAsk.Amounts(MoneyFlow.IN, MoneyTime.Day(today, DayPart.MORNING))
        assertEquals("आज सुबह एक पेमेंट आया: 700 रुपये।", spokenMoneyAmounts(morning, events(morning.time), today, SeemaLanguage.HINDI))
        val evening = MoneyAsk.Amounts(MoneyFlow.IN, MoneyTime.Day(LocalDate.of(2026, 11, 15), DayPart.EVENING))
        assertEquals("15 नवंबर की शाम कोई पैसा आने का संदेश नहीं मिला।", spokenMoneyAmounts(evening, events(evening.time), today, SeemaLanguage.HINDI))
    }

    @Test
    fun toWhomItWentInTheLastHour() {
        val ask = MoneyAsk.Amounts(MoneyFlow.OUT, MoneyTime.LastMinutes(60), withNames = true)
        assertEquals("पिछले 1 घंटे में एक पेमेंट गया: 500 रुपये Amit को।", spokenMoneyAmounts(ask, events(ask.time), today, SeemaLanguage.HINDI))
    }

    @Test
    fun manyAmountsAreCountedAfterTen() {
        val many = (1..13).map { MoneyEvent(10_000L * it, MoneyFlow.IN, at(20, 10, it), null) }
        val said = spokenMoneyAmounts(MoneyAsk.Amounts(MoneyFlow.IN, MoneyTime.Day(today)), many, today, SeemaLanguage.HINDI)
        assertTrue(said, said.startsWith("आज 13 पेमेंट आए: 100, 200"))
        assertTrue(said, said.endsWith("और 3 पेमेंट — सब मिलाकर कुल 9,100 रुपये।"))
    }

    @Test
    fun whoSentIt() {
        val all = moneyEvents(sms, notes)
        assertEquals("3,630 रुपये कल शाम 6:40 बजे Pappu Yadav ने भेजे।", spokenMoneyWho(MoneyAsk.Who(363000, MoneyFlow.IN), all, today, zone, SeemaLanguage.HINDI))
        assertEquals("2,400 रुपये कल सुबह 10:05 बजे Rahul ने भेजे।", spokenMoneyWho(MoneyAsk.Who(240000, MoneyFlow.IN), all, today, zone, SeemaLanguage.HINDI))
        assertEquals("500 रुपये आज दोपहर 2:20 बजे Amit को भेजे गए।", spokenMoneyWho(MoneyAsk.Who(50000, MoneyFlow.OUT), all, today, zone, SeemaLanguage.HINDI))
        assertEquals("पिछले 7 दिनों में 9,000 रुपये आने का कोई संदेश नहीं मिला।", spokenMoneyWho(MoneyAsk.Who(900000, MoneyFlow.IN), all, today, zone, SeemaLanguage.HINDI))
        // The SMS names nobody and no app note says who: said so, never guessed.
        val noName = moneyEvents(sms, emptyList())
        assertEquals("3,630 रुपये कल शाम 6:40 बजे आए, पर बैंक के संदेश में भेजने वाले का नाम नहीं है।", spokenMoneyWho(MoneyAsk.Who(363000, MoneyFlow.IN), noName, today, zone, SeemaLanguage.HINDI))
    }

    @Test
    fun theTimeWindows() {
        assertEquals(now - 2 * 3_600_000L, windowOf(MoneyTime.LastMinutes(120), now, zone).fromMillis)
        val evening = windowOf(MoneyTime.Day(today, DayPart.EVENING), now, zone)
        assertEquals(at(20, 16), evening.fromMillis)
        assertEquals(at(20, 20), evening.untilMillis)
        val night = windowOf(MoneyTime.Day(today.minusDays(1), DayPart.NIGHT), now, zone)
        assertEquals(at(19, 20), night.fromMillis)
        assertEquals(at(20, 5), night.untilMillis) // until 5 the next morning
    }

    @Test
    fun upiAppsSentMoneyNotifications() {
        assertEquals(MoneyNote(50000, MoneyFlow.OUT, 0L, "Rahul Kumar"), moneyPaidOut("Payment successful", "You paid ₹500 to Rahul Kumar"))
        assertEquals(MoneyNote(240000, MoneyFlow.OUT, 0L, "Shree Travels"), moneyPaidOut(null, "₹2,400 sent to Shree Travels"))
        assertNull(moneyPaidOut("Rahul paid you ₹500", null)) // money in, not out
        assertNull(moneyPaidOut("Payment failed", "You paid ₹500 to Rahul"))
        assertNull(moneyPaidOut("Request", "Rahul requested ₹500"))
    }
}
