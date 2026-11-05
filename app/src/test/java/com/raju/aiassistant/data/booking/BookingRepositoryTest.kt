package com.raju.aiassistant.data.booking

import com.raju.aiassistant.domain.booking.Booking
import com.raju.aiassistant.domain.booking.BookingStatus
import com.raju.aiassistant.domain.booking.Passenger
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.LocalDate

class BookingRepositoryTest {
    private val dir: File = Files.createTempDirectory("bookings").toFile()
    private val file = File(dir, "bookings.json")
    private val written = mutableMapOf<String, String>()
    private var now = 100L
    private fun repo() = BookingRepository(file, { _, name, text -> written[name] = text }, clock = { now })
    private val date = LocalDate.of(2026, 11, 6)
    private val draft = Booking(journeyDate = date, from = "Delhi", to = "Patna", passengers = listOf(Passenger("Anand", 'M', 34), Passenger("सीता", 'F', null)))

    @Test
    fun savedBookingsSurviveARestartAndKeepTheirNumbers() = runTest {
        val r = repo()
        val a = r.upsert(draft)
        now = 200
        val b = r.upsert(draft.copy(trainNo = "12309", travelClass = "3A", sender = "Rakesh"))
        assertEquals(1L to 2L, a.id to b.id)
        now = 300
        r.upsert(b.copy(status = BookingStatus.COMPLETED))

        val reopened = repo().all()
        assertEquals(2, reopened.size)
        val second = reopened.first { it.id == 2L }
        assertEquals(BookingStatus.COMPLETED, second.status)
        assertEquals(200L to 300L, second.createdAt to second.updatedAt)
        assertEquals(listOf(Passenger("Anand", 'M', 34), Passenger("सीता", 'F', null)), second.passengers)
        assertEquals("Rakesh", second.sender)
        assertEquals(3L, repo().upsert(draft).id)
    }

    @Test
    fun theDayFileIsRewrittenForOldAndNewDates() = runTest {
        val r = repo()
        val a = r.upsert(draft)
        assertTrue(written.getValue("Bookings_06-11-2026.txt").contains("1. Anand | M | 34 Yrs"))
        r.upsert(a.copy(journeyDate = date.plusDays(1)))
        assertTrue(written.getValue("Bookings_06-11-2026.txt").contains("(no bookings)"))
        assertTrue(written.getValue("Bookings_07-11-2026.txt").contains("#1 — PENDING"))
        assertTrue(r.delete(1))
        assertTrue(repo().all().isEmpty())
    }

    @Test
    fun aDamagedEntryDoesNotLoseTheOthers() = runTest {
        file.writeText("""[{"id":1,"date":"2026-11-06","from":"A","to":"B","passengers":[{"name":"X"}]},{"id":2,"date":"bad"}]""")
        assertEquals(listOf(1L), repo().all().map { it.id })
    }
}
