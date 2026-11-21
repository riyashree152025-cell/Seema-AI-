package com.raju.aiassistant.domain.automation

import com.raju.aiassistant.domain.assistant.AssistantAction
import com.raju.aiassistant.domain.assistant.LocalCommands
import com.raju.aiassistant.domain.sms.SmsMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AutomationsTest {
    private val zone = ZoneId.of("Asia/Kolkata")

    @Test
    fun dailyCareReminders() {
        assertEquals(DailyCare(9, 0, "dawai"), DailyCareReminder.parse("roz subah 9 baje dawai ki yaad dilana"))
        assertEquals(DailyCare(22, 0, "पानी पीने"), DailyCareReminder.parse("रोज़ रात 10 बजे पानी पीने की याद दिलाना"))
        assertEquals(DailyCare(21, 30, "ওষুধ"), DailyCareReminder.parse("রোজ রাত ৯:৩০ টায় ওষুধের কথা মনে করিয়ে দিও")?.copy(what = "ওষুধ"))
        assertNull(DailyCareReminder.parse("kal 9 baje yaad dilana"))
        val c = DailyCare(9, 0, "dawai")
        assertEquals("जान, dawai का समय हो गया… भूलना मत।", DailyCareReminder.sayStep(DailyCareReminder.step(c)))
        assertEquals(AssistantAction.Greeting("जान, dawai का समय हो गया… भूलना मत।"), LocalCommands.parse(DailyCareReminder.step(c)))
        assertTrue(DailyCareReminder.name(c).startsWith("याद:"))
    }

    @Test
    fun billsDueSoonFromSms() {
        val received = LocalDate.of(2026, 11, 18).atStartOfDay(zone).toInstant().toEpochMilli()
        val sms = listOf(
            SmsMessage("WBSEDC", "Your electricity bill of Rs 1,240.00 is due on 22-11-2026. Pay now.", received),
            SmsMessage("AIRTEL", "Your Airtel postpaid bill Rs 399 due date 30/11/2026", received),
            SmsMessage("HDFCBK", "EMI of Rs. 5,000 due on 21 Nov for loan XX12", received),
            SmsMessage("VM-AMAZON", "Your order is out for delivery", received),
        )
        val line = BillDues.line(sms, LocalDate.of(2026, 11, 20), zone)!!
        assertTrue(line, line.contains("EMI") && line.contains("कल"))
        assertTrue(line, line.contains("बिजली का बिल") && line.contains("22 तारीख"))
        assertTrue(line, !line.contains("मोबाइल")) // 30th: not within three days
        assertNull(BillDues.line(sms.takeLast(1), LocalDate.of(2026, 11, 20), zone))
    }

    @Test
    fun busyModeAndDnd() {
        assertEquals(true, CallAutomation.busyCommand("busy mode on karo"))
        assertEquals(true, CallAutomation.busyCommand("मैं व्यस्त हूँ, कॉल वालों को मैसेज भेज दो"))
        assertEquals(false, CallAutomation.busyCommand("busy mode band karo"))
        assertNull(CallAutomation.busyCommand("main busy hoon"))
        assertEquals(true, CallAutomation.dndCommand("DND chalu karo"))
        assertEquals(false, CallAutomation.dndCommand("do not disturb off"))
        assertEquals(AssistantAction.BusyMode(true), LocalCommands.parse("busy mode on karo"))
        assertEquals(AssistantAction.DoNotDisturb(true), LocalCommands.parse("DND on karo"))
        assertEquals("Rahul का कॉल छूट गया — वापस लगाऊँ?", CallAutomation.missedQuestion("Rahul", bengali = false))
    }
}
