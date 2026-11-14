package com.raju.aiassistant.domain.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatPeekTest {

    private fun text(t: String, id: String = "") = ScreenElement(text = t, kind = ElementKind.TEXT, id = id)

    @Test
    fun countsAadhaarCardsByTheirFileName() {
        val screen = listOf(text("Aadhaar_front.pdf"), text("Aadhaar_back.pdf"), text("Hello"))
        assertEquals("\"आनंद\" की चैट में अभी दिख रहा है: 2 आधार कार्ड।", ChatPeek.summarize("आनंद", screen))
        assertTrue(ChatPeek.summarize("आनंद", screen).contains("2 आधार कार्ड"))
    }

    @Test
    fun countsPlainAttachmentsWithoutReadingThem() {
        val screen = listOf(text("Photo"), text("Photo"), text("Voice message"), text("report.pdf"), text("VID_20261112.mp4"))
        val s = ChatPeek.summarize("Rizwan", screen)
        assertTrue(s, s.contains("2 फ़ोटो"))
        assertTrue(s, s.contains("1 डॉक्यूमेंट"))
        assertTrue(s, s.contains("1 वॉइस मैसेज"))
        assertTrue(s, s.contains("1 वीडियो"))
    }

    @Test
    fun anUnnamedPhotoIsSaidAsAPhotoAndNeverGuessedAsAnIdCard() {
        val s = ChatPeek.summarize("Riya", listOf(text("IMG_4411.jpg")))
        assertTrue(s, s.contains("1 फ़ोटो"))
        assertTrue(s, s.contains("नाम नहीं दिख रहा"))
        assertTrue(!s.contains("आधार"))
    }

    @Test
    fun textMessagesAreNotCountedAsAttachments() {
        assertNull(ChatPeek.classify(text("Kal milte hain, 5 baje?")))
        val s = ChatPeek.summarize("Riya", listOf(text("Kal milte hain")))
        assertTrue(s, s.contains("कोई फ़ोटो, फ़ाइल या वॉइस मैसेज नहीं"))
    }

    @Test
    fun lastVoicePlayIsTheNewestOneOnScreen() {
        val play1 = ScreenElement(text = "Play voice message", kind = ElementKind.BUTTON, clickable = true)
        val play2 = ScreenElement(text = "Play voice message", kind = ElementKind.BUTTON, clickable = true)
        val snap = ScreenSnapshot("com.whatsapp", "WhatsApp", listOf(play1, text("ok"), play2))
        assertTrue(WhatsAppScreen.lastVoicePlay(snap) === play2)
        assertNull(WhatsAppScreen.lastVoicePlay(ScreenSnapshot("com.whatsapp", "WhatsApp", listOf(text("hi")))))
    }
}
