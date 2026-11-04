package com.raju.aiassistant.domain.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallCommandTest {
    @Test fun yesAnswersTheCall() {
        listOf("haan", "Haan utha lo", "utha lo na", "pick up", "receive karo", "हाँ उठा लो", "उठाओ", "ok", "হ্যাঁ ধরো").forEach {
            assertEquals(it, CallCommand.ANSWER, callCommand(it))
        }
    }

    @Test fun noRejectsAndNeverAnswers() {
        listOf("nahi", "mat uthao", "kaat do", "cut karo", "baad me", "नहीं", "मत उठाओ", "उठाओ मत", "काट दो", "কেটে দাও").forEach {
            assertEquals(it, CallCommand.REJECT, callCommand(it))
        }
    }

    @Test fun otherTalkIsNotACallCommand() {
        listOf("", "kaun hai", "वो कह रहा था", "ticket book karo", "टिकट").forEach { assertNull(it, callCommand(it)) }
    }
}
