package com.raju.aiassistant.domain.rail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IrctcTicketTest {
    private val ticket = """
        Electronic Reservation Slip (ERS)
        PNR No. : 4512345678    Train No. / Name : 12301 / HOWRAH RAJDHANI    Class : THIRD AC (3A)
        Quota : GENERAL (GN)    Date of Journey : 15-Nov-2026
        From : HOWRAH JN (HWH)    Boarding At : HOWRAH JN (HWH)    To : NEW DELHI (NDLS)
        Passenger Details
        # Name Age Gender Booking Status Current Status
        1 RAJU KUMAR 34 M CNF/B2/45/LOWER CNF/B2/45/LOWER
        2 SITA DEVI 30 F WL/12 WL/5
        Ticket Fare: ₹ 1540.00
    """.trimIndent()

    @Test
    fun readsTheJourneyAndEveryPassenger() {
        val t = IrctcTicketParser.parse(ticket)!!
        assertEquals("4512345678", t.pnr)
        assertEquals("12301", t.trainNumber)
        assertEquals("HOWRAH RAJDHANI", t.trainName)
        assertEquals("15-Nov-2026", t.date)
        assertEquals("HOWRAH JN (HWH)", t.from)
        assertEquals("NEW DELHI (NDLS)", t.to)
        assertEquals("3A", t.travelClass)
        assertEquals(2, t.passengers.size)
        assertEquals("RAJU KUMAR", t.passengers[0].name)
        assertEquals(34, t.passengers[0].age)
        assertTrue(t.passengers[0].confirmed)
        assertEquals(Triple("B2", "45", "LOWER"), t.passengers[0].seat)
        assertEquals("WL/5", t.passengers[1].status) // the current status, not the booking one
    }

    @Test
    fun theReadoutTellsTheJourneyAndEachSeat() {
        val said = spokenTicket(IrctcTicketParser.parse(ticket)!!)
        assertTrue(said, said.contains("PNR 451 234 567 8"))
        assertTrue(said, said.contains("12301 Howrah Rajdhani"))
        assertTrue(said, said.contains("कुल 2 यात्री हैं, 1 की सीट कन्फर्म है"))
        assertTrue(said, said.contains("Raju Kumar, 34 साल, कन्फर्म, कोच B2, सीट 45 लोअर"))
        assertTrue(said, said.contains("Sita Devi, 30 साल, वेटिंग 5"))
        assertTrue(said, !said.contains("साफ़ नहीं मिला"))
    }

    @Test
    fun missingFieldsAreSaidHonestly() {
        val t = IrctcTicketParser.parse("PNR 4512345678\n1 RAJU 34 M CNF/B2/45/LB")!!
        val said = spokenTicket(t)
        assertTrue(said, said.contains("ट्रेन नंबर, यात्रा की तारीख, स्टेशन टिकट में साफ़ नहीं मिला"))
    }

    @Test
    fun anOrdinaryPdfIsNotATicket() {
        assertNull(IrctcTicketParser.parse("Electricity bill for October. Amount due 1450."))
    }

    @Test
    fun extractsTicketFare() {
        val t = IrctcTicketParser.parse(ticket)!!
        assertEquals(1540L, t.totalFare)
        assertEquals(1540L, IrctcTicketParser.extractFare(ticket))
    }
}
