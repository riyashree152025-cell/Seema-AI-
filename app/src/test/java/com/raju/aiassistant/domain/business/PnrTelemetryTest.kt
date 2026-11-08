package com.raju.aiassistant.domain.business

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PnrTelemetryTest {
    private val camel = """{"data":{"pnrNumber":"4512345678","trainNumber":"12301","trainName":"Rajdhani Express","dateOfJourney":"12-11-2026",
        "chartStatus":"Chart Not Prepared","passengerList":[{"bookingStatus":"WL 12","currentStatus":"WL 3"},{"bookingStatus":"WL 13","currentStatus":"RAC 4"}]}}"""
    private val snake = """{"pnr":"4512345678","train_number":"12301","train_name":"RAJDHANI","doj":"12/11/2026","chart_prepared":true,
        "passengers":[{"booking_status":"WL/12","current_status":"CNF/S4/32"}]}"""

    @Test
    fun commonShapesAreRead() {
        val a = parsePnrJson("4512345678", camel)!!
        assertEquals("12301", a.trainNumber)
        assertEquals("Rajdhani Express", a.trainName)
        assertFalse(a.chartPrepared)
        assertEquals(listOf(PassengerStatus("WL 12", "WL 3"), PassengerStatus("WL 13", "RAC 4")), a.passengers)
        val b = parsePnrJson("4512345678", snake)!!
        assertTrue(b.chartPrepared)
        assertTrue(b.allConfirmed)
        assertNull(parsePnrJson("1", """{"error":"Invalid PNR"}"""))
        assertNull(parsePnrJson("1", "not json"))
    }

    @Test
    fun confirmationAndChartAreAnnouncedOnceNotOnTheFirstCheck() {
        val wl = parsePnrJson("4512345678", camel)!!
        assertEquals(emptyList<PnrChange>(), pnrChanges(null, wl)) // first check: nothing to announce
        assertEquals(emptyList<PnrChange>(), pnrChanges(wl.signature, wl)) // no change
        val cnf = parsePnrJson("4512345678", snake)!!
        assertEquals(listOf(PnrChange.CONFIRMED, PnrChange.CHART_PREPARED), pnrChanges(wl.signature, cnf))
        val moved = wl.copy(passengers = listOf(PassengerStatus("WL 12", "WL 1"), PassengerStatus("WL 13", "RAC 4")))
        assertEquals(listOf(PnrChange.STATUS_CHANGED), pnrChanges(wl.signature, moved))
    }

    @Test
    fun theStatusIsSpokenWithTrainCoachAndBerth() {
        val cnf = parsePnrJson("4512345678", snake)!!
        assertEquals("PNR 45123 45678, 12301 RAJDHANI, 12/11/2026। Yatri 1: CNF/S4/32 (confirm)। Chart ban gaya hai।", spokenPnrStatus(cnf))
        assertTrue(spokenPnrAlert(cnf, listOf(PnrChange.CONFIRMED)).startsWith("Khushkhabri! Ticket confirm ho gaya।"))
        assertTrue(isConfirmed("B1, 23"))
        assertFalse(isConfirmed("RAC 4"))
        assertFalse(isConfirmed("WL 3"))
    }
}
