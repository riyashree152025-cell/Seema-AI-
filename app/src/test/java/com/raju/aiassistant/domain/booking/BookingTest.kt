package com.raju.aiassistant.domain.booking

import com.raju.aiassistant.domain.assistant.AssistantAction
import com.raju.aiassistant.domain.assistant.LocalCommands
import com.raju.aiassistant.domain.assistant.parseReply
import com.raju.aiassistant.domain.model.Personality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BookingTest {
    private val date = LocalDate.of(2026, 11, 6)
    private val booking = Booking(
        id = 7, journeyDate = date, from = "New Delhi", to = "Patna", trainNo = "12309", travelClass = "3A", sender = "Rakesh",
        passengers = listOf(Passenger("Sanjay", 'M', 34), Passenger("Sita", 'F', 30)), updatedAt = 10,
    )

    @Test
    fun slipUsesTheUsersFormat() {
        val expected = """
            ========================================
            🚆 JOURNEY DETAILS
            ========================================
            📍 FROM: New Delhi
            🏁 TO:   Patna
            📅 DATE: 06-11-2026 (Fri) | 🚂 TRAIN NO: 12309
            💺 CLASS: 3A | 👤 SENDER: Rakesh
            ----------------------------------------
            👥 PASSENGER LIST
            ----------------------------------------
            1. Sanjay | M | 34 Yrs
            2. Sita | F | 30 Yrs
            ========================================
        """.trimIndent()
        assertEquals(expected, formatSlip(booking))
    }

    @Test
    fun heardWordsAreNormalised() {
        assertEquals('M', normalizeGender("purush"))
        assertEquals('F', normalizeGender("महिला"))
        assertEquals('F', normalizeGender("Female"))
        assertNull(normalizeGender("pata nahi"))
        assertEquals(34, normalizeAge("34 saal"))
        assertNull(normalizeAge("bahut"))
        assertEquals("3A", normalizeClass("3 ac"))
        assertEquals("SL", normalizeClass("sleeper"))
        assertEquals("NDLS", normalizeStation("ndls"))
        assertEquals("New Delhi", normalizeStation("new  delhi"))
        assertEquals("Anand Kumar", normalizeName("ANAND kumar"))
    }

    @Test
    fun bookingNeedsDateStationsAndOneToSixPassengers() {
        val p = listOf(Passenger("a"))
        assertNull(buildBooking(null, "A", "B", null, null, null, p))
        assertNull(buildBooking(date, "A", " ", null, null, null, p))
        assertNull(buildBooking(date, "A", "B", null, null, null, emptyList()))
        assertNull(buildBooking(date, "A", "B", null, null, null, List(7) { Passenger("p$it") }))
        assertEquals(6, buildBooking(date, "A", "B", null, null, null, List(6) { Passenger("p$it") })!!.passengers.size)
    }

    @Test
    fun correctionChangesOnlyWhatWasSaid() {
        val fixed = (applyEdit(booking, BookingEdit(passengerNo = 1, name = "anand")) as EditResult.Ok).booking
        assertEquals(listOf("Anand", "Sita"), fixed.passengers.map { it.name })
        assertEquals(34, fixed.passengers[0].age)
        assertEquals(booking.copy(passengers = fixed.passengers), fixed)

        val moved = (applyEdit(booking, BookingEdit(date = date.plusDays(1), to = "pnbe")) as EditResult.Ok).booking
        assertEquals(date.plusDays(1) to "PNBE", moved.journeyDate to moved.to)

        // Two passengers and no number: ask which one instead of guessing.
        assertTrue(applyEdit(booking, BookingEdit(name = "Anand")) is EditResult.Problem)
        assertTrue(applyEdit(booking, BookingEdit(passengerNo = 5, age = 3)) is EditResult.Problem)
        assertTrue(applyEdit(booking, BookingEdit()) is EditResult.Problem)
        val single = booking.copy(passengers = listOf(Passenger("Sanjay", 'M', 34)))
        assertEquals("Anand", (applyEdit(single, BookingEdit(name = "Anand")) as EditResult.Ok).booking.passengers.single().name)
        val added = (applyEdit(booking, BookingEdit(addPassenger = Passenger("ravi", 'M', 5), removePassengerNo = 2)) as EditResult.Ok).booking
        assertEquals(listOf("Sanjay", "Ravi"), added.passengers.map { it.name })
    }

    @Test
    fun listsAreSplitByStatusAndSearchable() {
        val done = booking.copy(id = 8, status = BookingStatus.COMPLETED, passengers = listOf(Passenger("Mohan")), updatedAt = 5)
        val all = listOf(booking, done)
        assertEquals(listOf(7L), searchBookings(all, BookingStatus.PENDING, "").map { it.id })
        assertEquals(listOf(8L), searchBookings(all, null, "mohan").map { it.id })
        assertEquals(listOf(7L), searchBookings(all, null, "#7").map { it.id })
        assertEquals(2, searchBookings(all, null, "06-11-2026").size)
        assertTrue(summarizeBookings(all, null, null).startsWith("बुकिंग: 1 बाकी, 1 हो गईं, 0 रद्द।"))
        assertEquals(booking, lastTouched(all))
    }

    @Test
    fun dayFileAndReminderCoverTheRightDays() {
        val text = daySlipFileText(date, listOf(booking, booking.copy(id = 9, journeyDate = date.plusDays(3))), "now")
        assertTrue(text.contains("#7 — PENDING"))
        assertTrue(!text.contains("#9"))
        assertEquals("Bookings_06-11-2026.txt", daySlipFileName(date))

        val reminder = bookingReminder(date.minusDays(1), listOf(booking))!!
        assertTrue(reminder.startsWith("कल (6 Nov) की 1 बुकिंग बाकी"))
        assertNull(bookingReminder(date.minusDays(1), listOf(booking.copy(status = BookingStatus.COMPLETED))))
        assertNull(bookingReminder(date.minusDays(3), listOf(booking)))
    }

    @Test
    fun aiActionsAreParsed() {
        val save = parseReply("""<action>{"type":"booking_add","date":"2026-11-06","from":"Delhi","to":"Patna","passengers":[{"name":"anand","gender":"male","age":"34"}]}</action>""").actions.single()
        assertEquals("Anand", (save as AssistantAction.BookingSave).booking.passengers.single().name)
        val fix = parseReply("""<action>{"type":"booking_update","passenger_no":2,"age":40}</action>""").actions.single() as AssistantAction.BookingUpdate
        assertEquals(2, fix.edit.passengerNo)
        assertEquals(40, fix.edit.age)
        assertNull(fix.id)
        assertTrue(parseReply("""<action>{"type":"booking_update"}</action>""").actions.isEmpty())
        assertTrue(parseReply("""<action>{"type":"booking_add","date":"2026-11-06","from":"Delhi","to":"Patna","passengers":[]}</action>""").actions.isEmpty())
        val st = parseReply("""<action>{"type":"booking_status","id":7,"status":"cancelled"}</action>""").actions.single() as AssistantAction.BookingSetStatus
        assertEquals(BookingStatus.CANCELLED to 7L, st.status to st.id)
        val mode = parseReply("""<action>{"type":"set_mode","mode":"tapori"}</action>""").actions.single()
        assertEquals(AssistantAction.SetPersonality(Personality.TAPORI), mode)
    }

    @Test
    fun taporiModeVoiceCommands() {
        val c = Personality.COMPANION
        assertEquals(Personality.TAPORI, LocalCommands.personalitySwitch("टपोरी मोड ऑन करो", c))
        assertEquals(Personality.TAPORI, LocalCommands.personalitySwitch("Seema tapori mode on karo", c))
        assertEquals(Personality.TAPORI, LocalCommands.personalitySwitch("शैतानी दोस्त मोड चालू करो", c))
        assertEquals(Personality.COMPANION, LocalCommands.personalitySwitch("tapori mode band karo", Personality.TAPORI))
        assertEquals(Personality.COMPANION, LocalCommands.personalitySwitch("सीमा वापस आओ", Personality.TAPORI))
        // Outside Tapori mode "seema wapas aao" keeps meaning "open Seema".
        assertNull(LocalCommands.personalitySwitch("सीमा वापस आओ", c))
        assertNull(LocalCommands.personalitySwitch("tapori kaun hota hai", c))
    }

    @Test
    fun promptContextNamesTheLastEditedBooking() {
        val ctx = bookingContext(listOf(booking.copy(updatedAt = 1_000)), date, 2_000)!!
        assertTrue(ctx.contains("Last edited #7"))
        assertTrue(ctx.contains("1) Sanjay M 34"))
        assertNull(bookingContext(emptyList(), date, 0))
    }
}
