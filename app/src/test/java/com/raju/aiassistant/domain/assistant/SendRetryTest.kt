package com.raju.aiassistant.domain.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SendRetryTest {
    @Test
    fun pressAgainIsHeardInHindiBengaliAndHinglish() {
        assertTrue(SendRetry.asksToPress("তোমাকে চাপতে হবে তোমাকে পাঠিয়ে দিতে হবে"))
        assertTrue(SendRetry.asksToPress("Send dabao"))
        assertTrue(SendRetry.asksToPress("तुम दबा दो"))
        assertTrue(SendRetry.asksToPress("भेज दो ना"))
        assertTrue(SendRetry.asksToPress("Send চাপো"))
    }

    @Test
    fun questionsAndOtherTalkAreNotAPress() {
        assertFalse(SendRetry.asksToPress("এখন WhatsApp খুলে আমাকে দেখাও তো পাঠিয়েছে কিনা"))
        assertFalse(SendRetry.asksToPress("bheja kya?"))
        assertFalse(SendRetry.asksToPress("आज मौसम कैसा है"))
        assertFalse(SendRetry.asksToPress(""))
    }

    @Test
    fun theUserHimselfIsNotAChatName() {
        assertTrue(SendRetry.meansNoChat("self"))
        assertTrue(SendRetry.meansNoChat("আমাকে"))
        assertTrue(SendRetry.meansNoChat(" "))
        assertFalse(SendRetry.meansNoChat("Jalal Bhai"))
    }
}
