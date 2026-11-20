package com.raju.aiassistant.domain.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppDigestTest {
    private fun wa(sender: String, text: String, group: String? = null) = IncomingItem("k$sender$text", "com.whatsapp", "WhatsApp", sender, group, text)

    @Test
    fun asks() {
        assertTrue(WhatsAppDigest.asks("WhatsApp ka haal batao"))
        assertTrue(WhatsAppDigest.asks("unread messages summarize karo"))
        assertTrue(WhatsAppDigest.asks("হোয়াটসঅ্যাপে কী এসেছে"))
        assertFalse(WhatsAppDigest.asks("WhatsApp kholo"))
    }

    @Test
    fun groupsByPersonBusiestFirstAndHidesCodes() {
        val groups = WhatsAppDigest.group(
            listOf(
                wa("Amit", "kal milte hain"),
                wa("Rahul", "paisa bhej diya"),
                wa("Rahul", "Your OTP is 482913"),
                wa("Rahul", "call karo"),
                IncomingItem("sms", "com.android.messaging", "SMS", "Bank", null, "credited"),
                IncomingItem("c", "com.whatsapp", "WhatsApp", "Mitu", null, null, isCall = true),
            ),
        )
        assertEquals(listOf("Rahul", "Amit"), groups.map { it.who })
        assertEquals(3, groups[0].count)
        assertEquals(listOf("paisa bhej diya", "call karo"), groups[0].previews)
    }

    @Test
    fun localSummaryEndsWithTheReplyQuestion() {
        val text = WhatsAppDigest.localSummary(WhatsAppDigest.group(listOf(wa("Rahul", "call karo"))), bengali = false)
        assertTrue(text, text.contains("कुल 1 संदेश"))
        assertTrue(text, text.endsWith("बोलिए।"))
    }

    @Test
    fun tellHimRepliesToTheLastChat() {
        assertEquals(ReplyCommand(null, "10 minute mein call karta hoon"), parseReplyCommand("unko bol do ki 10 minute mein call karta hoon"))
        assertEquals(ReplyCommand(null, "আসছি"), parseReplyCommand("ওকে বলে দাও আসছি"))
    }
}
