package com.raju.aiassistant.domain.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallReplyTest {
    @Test
    fun spokenRepliesBecomeTheSmsText() {
        assertEquals("Abhi busy hoon.", callReplyText("bol do abhi busy hoon"))
        assertEquals("Abhi busy hoon.", callReplyText("usko bol do ki abhi busy hoon"))
        assertEquals("10 minute mein call karta hoon.", callReplyText("10 minute mein call karta hoon"))
        assertEquals("Meeting mein hoon, baad mein baat karte hain.", callReplyText("meeting mein hoon, baad mein baat karte hain likh do"))
        assertEquals("अभी गाड़ी चला रहा हूँ।", callReplyText("बोल दो अभी गाड़ी चला रहा हूँ"))
        // From the phone log: an opener and "usko bol do" in the middle.
        assertEquals("Busy hoon 10 minute mein karta hoon.", callReplyText("theek hai usko bol do busy hoon 10 minute mein karta hoon"))
        assertEquals("Meeting mein hoon.", callReplyText("accha, bol do ki meeting mein hoon"))
        assertEquals("Busy hoon.", callReplyText("haan theek hai busy hoon"))
    }

    @Test
    fun answerAndRejectWordsAreNotReplies() {
        assertNull(callReplyText("haan utha lo"))
        assertNull(callReplyText("kaat do"))
        assertNull(callReplyText("mat uthao"))
        assertNull(callReplyText("haan bolo")) // answering, not a reply
        assertNull(callReplyText(""))
    }

    @Test
    fun withoutCallerDetailsNoIdentityIsInvented() {
        assertEquals("Raju ji, Ek call aa raha hai.", ringingAnnouncementText("Raju ji"))
        assertEquals("Ek call aa raha hai.", ringingAnnouncementText(null))
    }
}
