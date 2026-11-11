package com.raju.aiassistant.domain.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationReplyTest {
    @Test
    fun replyToTheNewestMessage() {
        assertEquals(ReplyCommand(null, "main aa raha hoon"), parseReplyCommand("reply karo ki main aa raha hoon"))
        assertEquals(ReplyCommand(null, "theek hai"), parseReplyCommand("isko jawab do theek hai"))
        assertEquals(ReplyCommand(null, "ठीक है"), parseReplyCommand("जवाब दो कि ठीक है"))
    }

    @Test
    fun replyToAPerson() {
        assertEquals(ReplyCommand("Rahul", "kal milte hain"), parseReplyCommand("Rahul ko reply karo ki kal milte hain"))
        assertEquals(ReplyCommand("राहुल", "कल मिलते हैं"), parseReplyCommand("राहुल को रिप्लाई करो कि कल मिलते हैं"))
    }

    @Test
    fun otherSentencesAreNotReplies() {
        assertNull(parseReplyCommand("reply karo"))
        assertNull(parseReplyCommand("Rahul ko message karo ki kal milte hain"))
        assertNull(parseReplyCommand("jawab kya hai"))
    }

    @Test
    fun theTargetIsTheNewestOrTheNamedPerson() {
        val targets = listOf(ReplyTarget("k2", "Mummy", "WhatsApp"), ReplyTarget("k1", "Rahul Sharma", "WhatsApp"))
        assertEquals("k2", pickReplyTarget(null, targets)!!.key)
        assertEquals("k1", pickReplyTarget("rahul", targets)!!.key)
        assertNull(pickReplyTarget("Zaid", targets))
        assertNull(pickReplyTarget(null, emptyList()))
    }
}
