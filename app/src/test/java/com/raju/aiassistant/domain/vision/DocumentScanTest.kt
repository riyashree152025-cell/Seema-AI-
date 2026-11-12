package com.raju.aiassistant.domain.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DocumentScanTest {
    @Test
    fun aTicketBecomesAJourney() {
        val scan = parseTicketScan(
            "PNR: 4512 367 890\nTRAIN: 12301 Rajdhani Express\nDATE: 15/11/2026\nFROM: HWH\nTO: NDLS\nPASSENGER: RAJESH SINGH",
        )!!
        assertEquals("4512367890", scan.pnr)
        assertEquals(LocalDate.of(2026, 11, 15), scan.date)
        val j = scan.toJourney()
        assertEquals("HWH → NDLS", j.route)
        assertEquals("12301 Rajdhani Express", j.train)
        assertEquals("RAJESH SINGH", j.passenger)
    }

    @Test
    fun anUnclearTicketIsNotSaved() {
        assertNull(parseTicketScan("PNR: 45123\nDATE: 15/11/2026")) // not 10 digits
        assertNull(parseTicketScan("PNR: 4512367890\nDATE:")) // no date
        assertNull(parseTicketScan("PNR: 4512367890\nDATE: 31/02/2026")) // not a real day
        val partial = parseTicketScan("**PNR:** 4512367890\nTRAIN: empty\nDATE: 15-11-26\nFROM:\nTO: NDLS")!!
        assertEquals(LocalDate.of(2026, 11, 15), partial.date)
        assertNull(partial.train)
        assertEquals("NDLS", partial.toJourney().route)
    }

    @Test
    fun aBillGivesItsTotal() {
        assertEquals(BillScan(125050, "Sharma Medical", LocalDate.of(2026, 11, 10)), parseBillScan("TOTAL: Rs. 1,250.50\nSHOP: Sharma Medical\nDATE: 10/11/2026"))
        assertEquals(BillScan(30000, null, null), parseBillScan("TOTAL: ₹300\nSHOP:\nDATE:"))
        assertEquals(15050L, parseBillScan("TOTAL: 150.5")!!.amountPaise)
        assertNull(parseBillScan("TOTAL:\nSHOP: Sharma Medical"))
        assertNull(parseBillScan("TOTAL: about 300"))
        assertNull(parseBillScan("TOTAL: 0.50"))
    }
}
