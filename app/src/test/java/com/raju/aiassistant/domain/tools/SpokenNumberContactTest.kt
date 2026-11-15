package com.raju.aiassistant.domain.tools

import com.raju.aiassistant.domain.tools.Contact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpokenNumberContactTest {
    private val contacts = listOf(
        Contact("Vodafone 01", "9876500001"),
        Contact("Vodafone 02", "9876500002"),
        Contact("Rahul", "9876500003"),
    )

    @Test
    fun spokenDigitsFindTheNumberedContact() {
        assertEquals("Vodafone 01", ContactMatch.best("Vodafone zero one", contacts).first().name)
        assertEquals("Vodafone 02", ContactMatch.best("Vodafone zero two", contacts).first().name)
    }

    @Test
    fun aSmallSpellingSlipStillFindsTheName() {
        val found = ContactMatch.best("Rahool", contacts)
        assertTrue(found.isNotEmpty())
        assertEquals("Rahul", found.first().name)
    }
}
