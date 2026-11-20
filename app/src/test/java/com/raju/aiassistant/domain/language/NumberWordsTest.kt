package com.raju.aiassistant.domain.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Raju, 20 Nov: amounts heard as "सात सौ तीस रुपये", "एक हज़ार दो सौ पचास रुपये" — not digits read one by one. */
class NumberWordsTest {
    @Test
    fun hindi() {
        assertEquals("सात सौ तीस", NumberWords.words(730, bengali = false))
        assertEquals("एक हज़ार दो सौ पचास", NumberWords.words(1_250, bengali = false))
        assertEquals("तीन हज़ार सात सौ छब्बीस", NumberWords.words(3_726, bengali = false))
        assertEquals("आठ हज़ार नौ सौ पचासी", NumberWords.words(8_985, bengali = false))
        assertEquals("बाईस हज़ार सात सौ तिरपन", NumberWords.words(22_753, bengali = false))
        assertEquals("एक लाख पच्चीस हज़ार", NumberWords.words(1_25_000, bengali = false))
        assertEquals("दो करोड़ तीन लाख चार हज़ार पाँच", NumberWords.words(2_03_04_005, bengali = false))
        assertEquals("एक", NumberWords.words(1, bengali = false))
        assertEquals("शून्य", NumberWords.words(0, bengali = false))
    }

    @Test
    fun bengali() {
        assertEquals("সাতশো তিরিশ", NumberWords.words(730, bengali = true))
        assertEquals("এক হাজার দুশো পঞ্চাশ", NumberWords.words(1_250, bengali = true))
        assertEquals("আট হাজার নশো পঁচাশি", NumberWords.words(8_985, bengali = true))
        assertEquals("বাইশ হাজার সাতশো তিপ্পান্ন", NumberWords.words(22_753, bengali = true))
        assertEquals("এক লাখ", NumberWords.words(1_00_000, bengali = true))
    }

    @Test
    fun theyAreUsedForAmountsWithTheirCurrencyWordOnly() {
        assertEquals(
            "आज पाँच बार पैसा कटा: आठ हज़ार नौ सौ पचासी रुपये और सात सौ तीस रुपये — कुल नौ हज़ार सात सौ पंद्रह रुपये।",
            NumberWords.amountsInWords("आज पाँच बार पैसा कटा: 8,985 रुपये और 730 रुपये — कुल 9,715 रुपये।"),
        )
        assertEquals("মোট বাইশ হাজার সাতশো তিপ্পান্ন টাকা কেটেছে।", NumberWords.amountsInWords("মোট 22,753 টাকা কেটেছে।"))
        assertEquals("तीन हज़ार सात सौ पचास रुपये पचहत्तर पैसे", NumberWords.amountsInWords("3,750.75 रुपये"))
        assertEquals("पिछले 2 घंटे में", NumberWords.amountsInWords("पिछले 2 घंटे में")) // not money: left alone
        assertEquals("PNR 4512345678", NumberWords.amountsInWords("PNR 4512345678"))
        assertNull(NumberWords.words(-1, bengali = false))
    }
}
