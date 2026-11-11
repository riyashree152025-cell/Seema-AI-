package com.raju.aiassistant.domain.tatkal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PassengerAgesTest {
    private val today = LocalDate.of(2026, 11, 11)

    @Test
    fun namesWithBirthYearsBecomeExactAges() {
        val list = parsePassengers("राजेश सिंह 1986 और राकेश 2002", today)!!
        assertEquals(listOf(Passenger("राजेश सिंह", 1986, 40, null), Passenger("राकेश", 2002, 24, null)), list)
        assertEquals("राजेश सिंह | 40 | —\nराकेश | 24 | —", writtenPassengers(list))
        assertTrue(spokenPassengers(list, 2026).contains("2026 − 1986 = 40"))
    }

    @Test
    fun genderIsOnlyWhatWasSaidAndOneNameNeedsAnAgeWord() {
        assertEquals(
            listOf(Passenger("Sunita Devi", 1990, 36, "Female"), Passenger("Amit", 2010, 16, "Male")),
            parsePassengers("Sunita Devi 1990 female, Amit 2010 male", today),
        )
        assertEquals(listOf(Passenger("Rakesh", 2002, 24, null)), parsePassengers("Rakesh 2002 ki umar nikalo", today))
        assertEquals(listOf(Passenger("राकेश", 2002, 24, null)), parsePassengers("राकेश १९८६ की उम्र बताओ".replace("१९८६", "२००२"), today))
        assertNull(parsePassengers("Rakesh 2002", today)) // one name and a number alone could be anything
    }

    @Test
    fun aFullDateOfBirthGivesCompletedYears() {
        assertEquals(listOf(Passenger("Rajesh", 1986, 39, null)), parsePassengers("Rajesh 25/12/1986 age", today))
        assertEquals(listOf(Passenger("Rajesh", 1986, 40, null)), parsePassengers("Rajesh 01/01/1986 age", today))
    }

    @Test
    fun otherSentencesAreNotAges() {
        assertNull(parsePassengers("Rahul ko 2000 diye aur Amit ko 1950 diye", today))
        assertNull(parsePassengers("2002 mein kya hua tha aur 1986 mein", today))
        assertNull(parsePassengers("Rajesh 2090 aur Rakesh 2002", today)) // a year that has not come yet
        assertNull(parsePassengers("kal katihar se delhi ki train", today))
    }

    @Test
    fun linesReadFromAnIdPhotoAreChecked() {
        val list = parsePassengerLines("RAJESH SINGH | 15/08/1986 | M\nSUNITA | 1990 | Female\nsorry, unclear\nX | 1800 | M", today)
        assertEquals(listOf(Passenger("RAJESH SINGH", 1986, 40, "Male"), Passenger("SUNITA", 1990, 36, "Female")), list)
    }
}
