package com.raju.aiassistant.domain.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactMatchTest {
    private val book = listOf(
        Contact("Vodafone 01", "+91 98765 43210"),
        Contact("Vodafone 01", "098765 43210"), // the same number saved twice
        Contact("Rahul Sharma", "91234 56789"),
        Contact("Mom", "99887 76655"),
        Contact("Mom Office", "011 2345 6789"),
    )

    @Test
    fun spacesCaseAndPunctuationDoNotMatter() {
        assertEquals(listOf(Contact("Vodafone 01", "+91 98765 43210")), ContactMatch.best("Vodafone 0 1", book))
        assertEquals(listOf(Contact("Vodafone 01", "+91 98765 43210")), ContactMatch.best("vodafone-01", book))
        assertEquals(listOf(Contact("Vodafone 01", "+91 98765 43210")), ContactMatch.best("Vodafone ०१", book))
    }

    @Test
    fun wordsInAnotherOrderAndPartOfANameAreFound() {
        assertEquals(listOf(Contact("Rahul Sharma", "91234 56789")), ContactMatch.best("Sharma Rahul", book))
        assertEquals(listOf(Contact("Rahul Sharma", "91234 56789")), ContactMatch.best("sharma", book))
    }

    @Test
    fun theSameNameComesBeforeLongerNamesContainingIt() {
        assertEquals(listOf(Contact("Mom", "99887 76655")), ContactMatch.best("mom", book))
    }

    @Test
    fun aSpokenNumberFindsItsContact() {
        assertEquals("Rahul Sharma", ContactMatch.best("9123456789", book).single().name)
    }

    @Test
    fun unknownNamesFindNothing() {
        assertTrue(ContactMatch.best("Airtel 02", book).isEmpty())
        assertTrue(ContactMatch.best("   ", book).isEmpty())
    }
}
