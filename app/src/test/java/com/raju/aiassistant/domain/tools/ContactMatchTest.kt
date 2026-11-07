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

    @Test
    fun operatorNamesDoNotMatchServiceEntries() {
        // Seen on the phone: "Airtel 01" was offered "airtel money: *400#".
        val phone = listOf(
            Contact("airtel money", "*400#"),
            Contact("Airtel Care", "121"),
            Contact("Rahul Sharma", "91234 56789"),
        )
        assertTrue(ContactMatch.best("Airtel 01", phone).isEmpty())
        assertTrue(ContactMatch.best("Vodafone 01", listOf(Contact("Vodafone Care", "199"))).isEmpty())
        // A real saved "Airtel 01" is still found.
        assertEquals("Airtel 01", ContactMatch.best("airtel 0 1", phone + Contact("Airtel 01", "98765 00001")).single().name)
    }

    @Test
    fun onlyFullNumbersAreCallable() {
        assertTrue(PhoneNumbers.isCallable("98765 43210"))
        assertTrue(PhoneNumbers.isCallable("+91 98765 43210"))
        assertTrue(PhoneNumbers.isCallable("011 2345 6789"))
        assertTrue(PhoneNumbers.isCallable("९८७६५ ४३२१०"))
        assertTrue(!PhoneNumbers.isCallable("01"))
        assertTrue(!PhoneNumbers.isCallable("121"))
        assertTrue(!PhoneNumbers.isCallable("*400#"))
        assertTrue(!PhoneNumbers.isCallable("12345"))
    }

    @Test
    fun aSpokenChoicePicksTheRightRahul() {
        val rahuls = listOf(
            Contact("Rahul Kumar", "98000 00001"),
            Contact("Rahul Sharma", "98000 00002"),
            Contact("Rahul Verma", "98000 00003"),
        )
        assertEquals("Rahul Sharma", ContactMatch.pick("राहुल शर्मा", rahuls)!!.name)
        assertEquals("Rahul Sharma", ContactMatch.pick("sharma", rahuls)!!.name)
        assertEquals("Rahul Verma", ContactMatch.pick("teesra", rahuls)!!.name)
        assertEquals("Rahul Kumar", ContactMatch.pick("पहला वाला", rahuls)!!.name)
        assertEquals("Rahul Kumar", ContactMatch.pick("1", rahuls)!!.name)
        assertEquals(null, ContactMatch.pick("rahul", rahuls)) // still all three: ask again
        assertEquals(null, ContactMatch.pick("haan kar do", rahuls))
    }
}
