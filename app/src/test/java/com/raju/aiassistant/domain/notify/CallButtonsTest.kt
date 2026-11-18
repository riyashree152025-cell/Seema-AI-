package com.raju.aiassistant.domain.notify

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallButtonsTest {
    @Test
    fun theAnswerButtonIsFoundInEnglishHindiAndByViewId() {
        listOf("Answer", "Accept call", "उठाएं", "उत्तर दें", "ধরুন", "Answer call incoming_call_answer", "btn_answer").forEach {
            assertTrue(it, CallButtons.isAnswer(it))
            assertFalse(it, CallButtons.isDecline(it))
        }
    }

    @Test
    fun theDeclineButtonIsFoundInEnglishHindiAndByViewId() {
        listOf("Decline", "Reject", "Hang up", "End call", "अस्वीकार करें", "काटें", "বাতিল", "btn_decline").forEach {
            assertTrue(it, CallButtons.isDecline(it))
            assertFalse(it, CallButtons.isAnswer(it))
        }
    }

    @Test
    fun quickRepliesAndOtherButtonsAreNeverPressed() {
        listOf("Answer with message", "Reply with message", "संदेश के साथ उत्तर दें", "Speaker", "Mute", "Keypad", "", "Add call").forEach {
            assertFalse(it, CallButtons.isAnswer(it))
            assertFalse(it, CallButtons.isDecline(it))
        }
    }
}
