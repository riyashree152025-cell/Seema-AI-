package com.raju.aiassistant.domain.rail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The answers below are the shapes RailKit documents (its SDK README), not made-up ones. */
class RailKitTest {
    private val pnrBody = """
        {"success":true,"data":{"pnr":"5827194603","train":{"number":"12987","name":"SAMPURN K RAJDHANI"},
        "journey":{"dateOfJourney":"22 Aug 2026, 04:35:00 pm","class":"3A","quota":"GN",
        "source":{"code":"JP","name":"JAIPUR JN"},"destination":{"code":"NDLS","name":"NEW DELHI"},
        "boardingPoint":{"code":"JP","name":"JAIPUR JN"},"distance":471},
        "chart":{"status":"Chart Prepared"},"booking":{"fare":1845},
        "passengers":[
          {"serialNumber":"Passenger 1","booking":{"status":"CNF","coach":"B5","berthNo":22,"details":"CNF/B5/22/LB"},
           "current":{"status":"CNF","coach":"B5","berthNo":22,"details":"CNF , B5 - 22 [LB]"}},
          {"serialNumber":"Passenger 2","booking":{"status":"RAC","coach":null,"berthNo":7,"details":"RAC/7"},
           "current":{"status":"CNF","coach":"B5","berthNo":31,"details":"CNF , B5 - 31 [UB]"}}]}}
    """.trimIndent()

    @Test
    fun aPnrAnswerIsReadWithTrainChartAndEveryPassenger() {
        val r = (parseRailPnr("5827194603", pnrBody) as RailResult.Ok).value
        assertEquals("12987", r.snapshot.trainNumber)
        assertEquals("SAMPURN K RAJDHANI", r.snapshot.trainName)
        assertTrue(r.snapshot.chartPrepared)
        assertEquals(2, r.snapshot.passengers.size)
        assertEquals("RAC/7", r.snapshot.passengers[1].booking)
        assertTrue(r.snapshot.allConfirmed)
        assertEquals(LocalDate.of(2026, 8, 22), r.journeyDate)
        assertEquals("JP-NDLS", r.route)
    }

    @Test
    fun aWaitingTicketWithNoChartIsNotConfirmed() {
        val body = """{"success":true,"data":{"train":{"number":"12313","name":"RAJDHANI"},"journey":{"dateOfJourney":"15 Nov 2026, 05:00:00 pm"},
            "chart":{"status":"Chart Not Prepared"},
            "passengers":[{"booking":{"status":"GNWL","details":"GNWL/42"},"current":{"status":"GNWL","details":"GNWL/18"}}]}}"""
        val r = (parseRailPnr("1234567890", body) as RailResult.Ok).value
        assertFalse(r.snapshot.chartPrepared)
        assertFalse(r.snapshot.allConfirmed)
        assertEquals("GNWL/18", r.snapshot.passengers[0].current)
        // Waiting with the chart still open: watched until the chart. Confirmed, charted or past: not.
        assertTrue(shouldWatchChart(r, LocalDate.of(2026, 11, 11)))
        assertFalse(shouldWatchChart(r, LocalDate.of(2026, 11, 16)))
        val confirmed = (parseRailPnr("5827194603", pnrBody) as RailResult.Ok).value
        assertFalse(shouldWatchChart(confirmed, LocalDate.of(2026, 8, 1)))
    }

    @Test
    fun theServicesOwnErrorIsPassedOnAndNothingIsInvented() {
        val r = parseRailPnr("123", """{"success":false,"error":"PNR number must be exactly 10 digits"}""")
        assertEquals("PNR number must be exactly 10 digits", (r as RailResult.Failed).message)
        assertTrue(parseTrainsBetween("<html>busy</html>") is RailResult.Failed)
        assertTrue(parseLive("""{"success":true,"data":{}}""") is RailResult.Failed)
        assertTrue(railHttpError(401).contains("key"))
    }

    @Test
    fun trainsBetweenStationsAreListedAndSaidShort() {
        val body = """{"success":true,"data":[
            {"train_no":"12951","train_name":"MUMBAI RAJDHANI","from_stn_code":"NDLS","to_stn_code":"BCT","from_time":"16:55","to_time":"08:35","travel_time":"15:40 hrs","running_days":"1111111"},
            {"train_no":"12953","train_name":"AK RAJDHANI","from_stn_code":"NZM","to_stn_code":"BCT","from_time":"17:10","to_time":"10:05","travel_time":"16:55 hrs","running_days":"1111111"}]}"""
        val trains = (parseTrainsBetween(body) as RailResult.Ok).value
        assertEquals(listOf("12951", "12953"), trains.map { it.number })
        val said = spokenTrainsBetween(Station("NDLS", "NEW DELHI"), Station("BCT", "MUMBAI CENTRAL"), LocalDate.of(2026, 11, 12), trains)
        assertTrue(said, said.contains("2 ट्रेनें") && said.contains("1 2 9 5 1 MUMBAI RAJDHANI, 16:55"))
        val none = spokenTrainsBetween(Station("A", "A"), Station("B", "B"), null, emptyList())
        assertTrue(none.contains("कोई सीधी ट्रेन नहीं"))
        assertTrue(writtenTrainsBetween(Station("NDLS", "NEW DELHI"), Station("BCT", "MUMBAI CENTRAL"), null, trains).contains("12953 AK RAJDHANI · 17:10 → 10:05"))
    }

    @Test
    fun aCityNameFindsItsMainStation() {
        val body = """{"success":true,"data":{"stations":[{"code":"DEC","name":"DELHI CANTT"},{"code":"DLI","name":"DELHI"},{"code":"NDLS","name":"NEW DELHI"}]}}"""
        val list = (parseStations(body) as RailResult.Ok).value
        assertEquals("delhi", stationQuery("दिल्ली"))
        assertEquals("DLI", pickStation("दिल्ली", list)!!.code)
        assertEquals("NDLS", pickStation("ndls", list)!!.code)
        val kir = listOf(Station("KIR", "KATIHAR JN"), Station("KTHE", "KATIHAR EAST"))
        assertEquals("KIR", pickStation("कटिहार", kir)!!.code)
        assertEquals("KIR", asStationCode("KIR"))
        assertNull(asStationCode("Katihar"))
    }

    @Test
    fun liveStatusSaysWhereTheTrainIsAndItsDelayOnlyWhenGiven() {
        val body = """{"success":true,"data":{"trainNo":"12345","trainName":"SARAIGHAT EXP","statusNote":"Arrived at RAMPUR HAT (RPH)","lastUpdate":"17-Oct-2026 19:19",
            "timeline":[
              {"type":"stoppage","status":"passed","stationCode":"HWH","stationName":"HOWRAH JN","arrival":{"scheduled":"SRC","actual":"SRC","delay":""},"departure":{"scheduled":"16:05 17-Oct","actual":"16:06 17-Oct","delay":"00:01"}},
              {"type":"intermediate","status":"passed","stationCode":"X","stationName":"X","arrival":{"scheduled":"17:12"},"departure":{"scheduled":"17:12"}},
              {"type":"stoppage","status":"current","stationCode":"RPH","stationName":"RAMPUR HAT","arrival":{"scheduled":"19:00 17-Oct","actual":"19:25 17-Oct","delay":"00:25"},"departure":{"scheduled":"19:02 17-Oct","actual":"","delay":""}},
              {"type":"stoppage","status":"upcoming","stationCode":"NFK","stationName":"NEW FARAKKA JN","arrival":{"scheduled":"20:40 17-Oct"},"departure":{"scheduled":"20:42 17-Oct"}}]}}"""
        val s = (parseLive(body) as RailResult.Ok).value
        assertEquals("00:25", s.delay)
        assertEquals("NEW FARAKKA JN", s.nextStop)
        val said = spokenLive(s)
        assertTrue(said, said.contains("Arrived at RAMPUR HAT") && said.contains("25 मिनट देर") && said.contains("अगला स्टेशन NEW FARAKKA JN"))
        assertEquals("1 घंटा 5 मिनट", spokenDelay("01:05"))
        assertNull(spokenDelay("00:00"))
        assertNull(spokenDelay(""))
        assertFalse(spokenLive(s.copy(delay = null)).contains("समय पर")) // no delay given: nothing claimed
    }

    @Test
    fun fareAndSeatsAreRead() {
        val fare = (parseFare("""{"success":true,"data":{"trainNo":"12313","trainName":"RAJDHANI EXPRES","from":"ASN","to":"NDLS","class":"3A","baseFare":1617,"totalFare":2780,"tatkalFare":0}}""") as RailResult.Ok).value
        assertEquals(2780, fare.total)
        assertNull(fare.tatkal)
        assertTrue(spokenFare(fare, "ASN", "NDLS").contains("2780 रुपये"))
        val seats = (parseSeats("""{"success":true,"data":{"train":{"trainNo":"12301","trainName":"RAJDHANI"},"fare":{"totalFare":2900},
            "availability":[{"date":"27-12-2025","availabilityText":"GNWL 12","prediction":"Likely"}]}}""") as RailResult.Ok).value
        assertTrue(spokenSeats(seats, "3A").contains("GNWL 12"))
        assertEquals("3A", railClass("3 ac"))
        assertEquals("SL", railClass("sleeper"))
        assertNull(railClass("business"))
        assertEquals("TQ", railQuota("tatkal"))
        assertEquals("GN", railQuota(null))
        assertEquals("15-11-2026", railDate(LocalDate.of(2026, 11, 15)))
    }

    @Test
    fun railQuestionsAreUnderstoodOnThePhone() {
        val today = LocalDate.of(2026, 11, 11)
        assertEquals(RailCommand.Between("कटिहार", "दिल्ली", today.plusDays(1)), parseRailCommand("कल कटिहार से दिल्ली की कौन सी ट्रेन है?", today))
        assertEquals(RailCommand.Between("katihar", "delhi", today), parseRailCommand("aaj katihar se delhi ki train batao", today))
        assertEquals(RailCommand.Between("KIR", "NDLS", null), parseRailCommand("KIR se NDLS train", today))
        assertEquals(RailCommand.Between("patna", "new delhi", today.plusDays(2)), parseRailCommand("patna se new delhi ke liye parso koi train hai kya", today))
        assertEquals(RailCommand.Live("12345"), parseRailCommand("train 12345 kahan hai", today))
        assertEquals(RailCommand.Live("12345"), parseRailCommand("1 2 3 4 5 कहाँ है", today))
        assertEquals(RailCommand.Live("15707"), parseRailCommand("15707 kitni late hai", today))
        assertNull(parseRailCommand("rahul se 500 mile", today))
        assertNull(parseRailCommand("mummy se bolo ki main kal train se aaunga aur khana bana ke rakhna", today))
        assertNull(parseRailCommand("train ka ticket book karo", today))
    }
}
