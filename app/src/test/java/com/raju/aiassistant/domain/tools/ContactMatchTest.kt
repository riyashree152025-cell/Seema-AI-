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

    @Test
    fun theSameNameTwiceIsToldApartByItsLastDigits() {
        val firoz = listOf(Contact("Firoz", "+919604686087"), Contact("Firoz", "+919711945150"))
        assertEquals("पहला Firoz, 6 0 8 7 वाला; दूसरा Firoz, 5 1 5 0 वाला", ContactMatch.spokenChoices(firoz))
        assertEquals("1. Riya; 2. Rahul", ContactMatch.spokenChoices(listOf(Contact("Riya", "9800000001"), Contact("Rahul", "9800000002"))))
        assertEquals(firoz[1], ContactMatch.pick("5150 wala", firoz))
        assertEquals(firoz[0], ContactMatch.pick("६०८७", firoz))
        assertEquals(firoz[1], ContactMatch.pick("doosra", firoz))
        assertEquals(null, ContactMatch.pick("91", firoz)) // too few digits to choose
    }

    @Test
    fun bracketsAndWordsOfAContactNameAreFound() {
        val rizwan = listOf(Contact("Rizwan (Travels)", "99999 11111"), Contact("Rahul Sharma", "91234 56789"))
        assertEquals("Rizwan (Travels)", ContactMatch.best("Rizwan", rizwan).single().name)
        assertEquals("Rizwan (Travels)", ContactMatch.best("rizwan travels", rizwan).single().name)
        assertEquals("Rizwan (Travels)", ContactMatch.best("Travels", rizwan).single().name)
    }

    @Test
    fun numberWordsMeanDigits() {
        val vf = listOf(Contact("Vodafone 01", "98765 00001"), Contact("Vodafone 02", "98765 00002"))
        assertEquals("Vodafone 01", ContactMatch.best("Vodafone One", vf).single().name)
        assertEquals("Vodafone 01", ContactMatch.best("vodafone 1", vf).single().name)
        assertEquals("Vodafone 02", ContactMatch.best("वोडाफोन दो", vf).single().name)
        assertEquals("Vodafone 01", ContactMatch.best("वोडाफोन वन", vf).single().name)
        assertEquals("Vodafone 01", ContactMatch.best("Vodafone ek", vf).single().name)
        assertEquals(2, ContactMatch.best("Vodafone", vf).size) // no number said: ask which one
    }

    @Test
    fun honorificsDoNotStopAMatch() {
        val jalal = listOf(Contact("Jalal", "98765 00011"), Contact("Rahul Sharma", "91234 56789"))
        assertEquals("Jalal", ContactMatch.best("Jalal Bhai", jalal).single().name)
        // Said in Hindi, saved in English with the same words: found by sound.
        val jalalBhai = listOf(Contact("Jalal Bhai", "98765 00013"), Contact("Jalal", "98765 00011"))
        assertEquals("Jalal Bhai", ContactMatch.best("जलाल भाई", jalalBhai).single().name)
        // A contact saved with the honorific is still the one that is found.
        val both = jalal + Contact("Jalal Bhai", "98765 00012")
        assertEquals("Jalal Bhai", ContactMatch.best("Jalal Bhai", both).single().name)
    }

    @Test
    fun aSpellingMistakeFindsTheSavedName() {
        val rizwan = listOf(Contact("Rizwan Travels", "99999 11111"), Contact("Rahul Sharma", "91234 56789"))
        assertEquals("Rizwan Travels", ContactMatch.best("Rizwen Travls", rizwan).single().name)
        assertTrue(ContactMatch.best("Riya", rizwan).isEmpty()) // short names are never guessed
        assertTrue(ContactMatch.best("Rahul", listOf(Contact("Rizwan Travels", "99999 11111"))).isEmpty())
    }

    @Test
    fun jaroWinklerScoresSpellingsAsExpected() {
        assertEquals(0.961, ContactMatch.similarity("martha", "marhta"), 0.001)
        assertEquals(1.0, ContactMatch.similarity("riya", "riya"), 0.0)
        assertTrue(ContactMatch.similarity("riya", "rahul") < ContactMatch.FUZZY_THRESHOLD)
    }

    @Test
    fun aNumberInTheQueryMustBeInTheName() {
        val phone = listOf(Contact("Airtel Money", "*400#"))
        assertTrue(ContactMatch.best("Airtel 01", phone).isEmpty())
    }

    @Test
    fun aSpellingGuessIsToldApartFromANameMatch() {
        val c = listOf(Contact("Rakesh Bhai", "98765 00021"), Contact("Rahul Sharma", "91234 56789"))
        assertTrue(ContactMatch.isSpellingGuess("Ramesh Bhai", c)) // found, but only by spelling: ask first
        assertTrue(!ContactMatch.isSpellingGuess("Rakesh", c))
        assertTrue(!ContactMatch.isSpellingGuess("Rahul", c))
        assertTrue(!ContactMatch.isSpellingGuess("Rahul Sharma", c))
    }

    @Test
    fun aNameIsNotFoundInsideTheMiddleOfAnotherWord() {
        val c = listOf(Contact("Malik Traders", "98765 11111"), Contact("Ali Hospital", "98765 44444"), Contact("Mummy", "98765 55555"))
        assertEquals(listOf("Ali Hospital"), ContactMatch.best("Ali", c).map { it.name }) // not "Malik Traders"
        assertEquals(listOf("Malik Traders"), ContactMatch.best("Traders", c).map { it.name })
        assertEquals(listOf("Mummy"), ContactMatch.best("Mum", c).map { it.name })
    }
}
