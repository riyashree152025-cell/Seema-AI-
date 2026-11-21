package com.raju.aiassistant.domain.business

import com.raju.aiassistant.domain.ledger.ClientBalance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PeriodReportTest {
    @Test
    fun asks() {
        assertEquals(ReportPeriod.THIS_WEEK, PeriodReport.ask("is hafte ka hisaab batao"))
        assertEquals(ReportPeriod.THIS_MONTH, PeriodReport.ask("इस महीने का हिसाब बताओ"))
        assertEquals(ReportPeriod.LAST_MONTH, PeriodReport.ask("pichle mahine ki report do"))
        assertEquals(ReportPeriod.THIS_MONTH, PeriodReport.ask("এই মাসের হিসাব বলো"))
        assertNull(PeriodReport.ask("hisaab kitab kaisa chal raha"))
    }

    @Test
    fun ranges() {
        val thu = LocalDate.of(2026, 11, 19)
        assertEquals(LocalDate.of(2026, 11, 16) to thu, PeriodReport.range(ReportPeriod.THIS_WEEK, thu))
        assertEquals(LocalDate.of(2026, 11, 9) to LocalDate.of(2026, 11, 15), PeriodReport.range(ReportPeriod.LAST_WEEK, thu))
        assertEquals(LocalDate.of(2026, 10, 1) to LocalDate.of(2026, 10, 31), PeriodReport.range(ReportPeriod.LAST_MONTH, thu))
    }

    @Test
    fun theSpokenReport() {
        val text = PeriodReport.build(ReportPeriod.THIS_MONTH, Cashflow(5_000_000, 12, 3_000_000, 20), 250_000, 7, listOf(ClientBalance("Rahul", 500_000, 2)), bengali = false)
        assertTrue(text, text.contains("इस महीने बैंक में"))
        assertTrue(text, text.contains("ज़्यादा आए"))
        assertTrue(text, text.contains("7 यात्राएँ"))
        assertTrue(text, text.contains("1 लोगों पर"))
        assertTrue(PeriodReport.build(ReportPeriod.THIS_WEEK, null, 0, 0, emptyList(), bengali = false).contains("SMS की अनुमति"))
    }

    @Test
    fun duesReminder() {
        assertEquals("Rahul", DuesReminder.who("Rahul ko bakaya yaad dilao"))
        assertEquals("अमित", DuesReminder.who("अमित को बकाया याद दिलाओ"))
        assertEquals("Rahul", DuesReminder.who("Rahul ko payment reminder bhejo"))
        assertNull(DuesReminder.who("Rahul ko call karo"))
        assertEquals("नमस्ते Rahul जी, R Travels की ओर से एक छोटी-सी याद — आपके ₹12,500 बाकी हैं। सुविधा हो तो भेज दीजिए। धन्यवाद 🙏", DuesReminder.message("Rahul", 1_250_000))
    }
}
