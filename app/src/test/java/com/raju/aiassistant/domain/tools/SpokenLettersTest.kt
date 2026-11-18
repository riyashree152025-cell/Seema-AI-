package com.raju.aiassistant.domain.tools

import com.raju.aiassistant.domain.payments.PaymentShare
import com.raju.aiassistant.domain.payments.QrRequest
import org.junit.Assert.assertEquals
import org.junit.Test

class SpokenLettersTest {
    @Test
    fun spelledLettersBecomeTheSavedInitials() {
        assertEquals("साजिद शेख NZM", SpokenLetters.collapse("साजिद शेख एन जेड एम"))
        assertEquals("राम जी", SpokenLetters.collapse("राम जी")) // a lone "जी" is "ji"
        assertEquals("मम्मी", SpokenLetters.collapse("मम्मी"))
        assertEquals(listOf("साजिद शेख एन", "साजिद शेख", "साजिद"), SpokenLetters.shorterForms("साजिद शेख एन जेडम"))
    }

    @Test
    fun theUsersOwnQrSentences() {
        // Exactly what was typed on the phone.
        assertEquals(QrRequest("साजिद शेख एन जेड एम", null), PaymentShare.qrRequest("साजिद शेख एन जेड एम उसको क्यू आर भेज दो"))
        assertEquals("साजिद शेख", PaymentShare.nameFromAnswer("Save किया हुआ यह साजिद शेख है नाम"))
        assertEquals("साजिद शेख एन जेडम", PaymentShare.nameFromAnswer("साजिद शेख एन जेडम"))
        assertEquals("Rizwan", PaymentShare.nameFromAnswer("Rizwan ko"))
    }
}
