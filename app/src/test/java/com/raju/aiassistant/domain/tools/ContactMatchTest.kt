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

    @Test
    fun aHindiNameFindsTheEnglishSavedContact() {
        val rizwan = listOf(
            Contact("Rizwan Travels", "99999 11111"),
            Contact("Rahul Sharma", "91234 56789"),
            Contact("Rizwan Barsoi Forman", "98888 22222"),
        )
        assertEquals(listOf(Contact("Rizwan Travels", "99999 11111")), ContactMatch.best("रिजवान ट्रैवल्स", rizwan))
        assertEquals(listOf(Contact("Rizwan Travels", "99999 11111")), ContactMatch.best("रिज़वान ट्रेवल्स", rizwan))
        // A short first name matches every Rizwan, so the app asks which one.
        assertEquals(2, ContactMatch.best("Rizwan", rizwan).size)
    }

    @Test
    fun skeletonIsTheSameForHindiAndEnglish() {
        assertEquals(ContactMatch.skeleton("Rizwan Travels"), ContactMatch.skeleton("रिजवान ट्रैवल्स"))
        assertEquals(ContactMatch.skeleton("Vodafone 01"), ContactMatch.skeleton("वोडाफोन"))
    }
}

class ContactMatchFuzzyTest {
    private val book = listOf(
        Contact("Rizwan Travels", "98300 11111"),
        Contact("Rizwan Khan", "98300 22222"),
        Contact("Rajesh Cycle Store", "98300 33333"),
        Contact("Anand Patel", "98300 44444"),
        Contact("Jalal Bhai", "98300 55555"),
        Contact("Mom", "99887 76655"),
        Contact("राहुल शर्मा", "91234 56789"),
    )

    @Test
    fun aNameSaidInHindiFindsTheEnglishContact() {
        val ranked = ContactMatch.rank("रिजवान Travels", book)
        assertEquals("Rizwan Travels", ranked.first().contact.name)
        assertTrue(ContactMatch.isClearWinner(ranked))
        assertEquals("Rizwan Travels", ContactMatch.best("रिजवान ट्रैवल्स", book).first().name)
    }

    @Test
    fun smallSpellingDifferencesStillFindTheContact() {
        assertEquals("Rizwan Travels", ContactMatch.best("rizvan travel", book).first().name)
        assertEquals("Anand Patel", ContactMatch.best("आनंद पटेल", book).first().name)
        assertEquals("Jalal Bhai", ContactMatch.best("जलाल भाई", book).first().name)
        assertEquals("राहुल शर्मा", ContactMatch.best("Rahul Sharma", book).first().name)
    }

    @Test
    fun onlyTheFirstNameGivesSeveralCandidates() {
        val ranked = ContactMatch.rank("रिजवान", book).filter { it.score >= ContactMatch.FUZZY_MIN }
        assertEquals(setOf("Rizwan Travels", "Rizwan Khan"), ranked.take(2).map { it.contact.name }.toSet())
        assertTrue(!ContactMatch.isClearWinner(ranked))
    }

    @Test
    fun unrelatedNamesAreNotMatched() {
        assertTrue(ContactMatch.best("Pintu Electricals", book).isEmpty())
        assertTrue(ContactMatch.best("सुरेश", book).isEmpty())
    }
}
