package com.raju.aiassistant.domain.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenControlTest {
    private val chat = ScreenSnapshot(
        "com.whatsapp", "WhatsApp",
        listOf(
            ScreenElement("Riya", ElementKind.TEXT),
            ScreenElement("Video call", ElementKind.BUTTON, clickable = true),
            ScreenElement("Voice call", ElementKind.BUTTON, clickable = true),
            ScreenElement("Hello!", ElementKind.TEXT),
            ScreenElement("", ElementKind.INPUT, clickable = true, editable = true, hint = "Message"),
            ScreenElement("Send", ElementKind.BUTTON, clickable = true),
            ScreenElement("Calls", ElementKind.BUTTON, clickable = true),
        ),
    )

    @Test
    fun buttonsAreFoundInHindiEnglishAndBengali() {
        assertEquals("Send", ScreenMatch.candidates(chat, "send").single().text)
        assertEquals("Send", ScreenMatch.candidates(chat, "भेजें").single().text)
        assertEquals("Send", ScreenMatch.candidates(chat, "পাঠান").single().text)
        assertEquals("Video call", ScreenMatch.candidates(chat, "video call").single().text)
        assertTrue(ScreenMatch.candidates(chat, "delete chat").isEmpty())
    }

    @Test
    fun plainTextIsNotTappable() {
        assertTrue(ScreenMatch.candidates(chat, "Hello").isEmpty())
    }

    @Test
    fun theInputBoxIsFoundByItsHint() {
        assertEquals("Message", ScreenMatch.inputFor(chat, null)!!.hint)
        assertEquals("Message", ScreenMatch.inputFor(chat, "message")!!.hint)
        val typed = chat.copy(elements = chat.elements.map { if (it.editable) it.copy(text = "Kal milte hain") else it })
        assertTrue(ScreenMatch.showsTyped(typed, "kal milte hain"))
        assertFalse(ScreenMatch.showsTyped(chat, "kal milte hain"))
        assertTrue(typed.fingerprint != chat.fingerprint)
    }

    @Test
    fun bankingUpiAndPasswordScreensAreOffLimits() {
        assertEquals("बैंक / UPI / पेमेंट ऐप", SensitiveScreens.reason(ScreenSnapshot("com.phonepe.app", "PhonePe", emptyList())))
        assertEquals("बैंक / UPI / पेमेंट ऐप", SensitiveScreens.reason(ScreenSnapshot("com.sbi.lotusintouch", "YONO Lite", emptyList())))
        assertEquals("पासवर्ड वाली स्क्रीन", SensitiveScreens.reason(ScreenSnapshot("com.x", "X", listOf(ScreenElement("", ElementKind.INPUT, editable = true, password = true)))))
        assertEquals("PIN / OTP / पासवर्ड वाली स्क्रीन", SensitiveScreens.reason(ScreenSnapshot("com.x", "X", listOf(ScreenElement("Enter OTP", ElementKind.TEXT)))))
        assertNull(SensitiveScreens.reason(chat))
        assertNull(SensitiveScreens.reason(ScreenSnapshot("com.google.android.apps.maps", "Navigation", emptyList())))
        assertFalse(SensitiveScreens.isFinancialApp("com.google.android.youtube", "YouTube"))
    }

    @Test
    fun theAiNeverSeesAPassword() {
        val s = ScreenSnapshot("com.x", "X", listOf(ScreenElement("", ElementKind.INPUT, editable = true, password = true), ScreenElement("Login", ElementKind.BUTTON, clickable = true)))
        val text = s.describeForAi()
        assertTrue(text.contains("[password box — hidden]"))
        assertTrue(text.contains("[button] Login"))
    }

    @Test
    fun longScreensAreCut() {
        val s = ScreenSnapshot("com.x", "X", List(500) { ScreenElement("line number $it", ElementKind.TEXT) })
        assertTrue(s.describeForAi(300).length <= 330)
    }

    @Test
    fun yesAndNoAreUnderstood() {
        assertEquals(true, confirmReply("haan daba do"))
        assertEquals(true, confirmReply("हाँ"))
        assertEquals(true, confirmReply("ok"))
        assertEquals(true, confirmReply("হ্যাঁ"))
        assertEquals(false, confirmReply("nahi mat dabao"))
        assertEquals(false, confirmReply("नहीं"))
        assertEquals(false, confirmReply("রুকো না")) // "না" wins
        assertNull(confirmReply("mausam kaisa hai"))
        assertNull(confirmReply(""))
    }

    @Test
    fun whatsAppCallButtonsMatchExactly() {
        assertTrue(WhatsAppButtons.isCallButton("Voice call", video = false))
        assertTrue(WhatsAppButtons.isCallButton("वीडियो कॉल", video = true))
        assertFalse(WhatsAppButtons.isCallButton("Calls", video = false)) // the tab
        assertFalse(WhatsAppButtons.isCallButton("Call log", video = false))
        assertFalse(WhatsAppButtons.isCallButton("Voice call", video = true))
        assertFalse(WhatsAppButtons.isCallButton("", video = false))
    }

    @Test
    fun onlyACallDialogIsConfirmed() {
        assertTrue(WhatsAppButtons.isCallDialog(listOf("Start voice call?", "Call", "Cancel")))
        assertFalse(WhatsAppButtons.isCallDialog(listOf("Block Riya?", "Block", "Cancel")))
        assertFalse(WhatsAppButtons.isCallDialog(listOf("Delete message?", "Delete for me", "Cancel")))
        assertFalse(WhatsAppButtons.isCallDialog(listOf("Clear chat?", "Clear", "Cancel")))
    }

    @Test
    fun riskyButtonsAreRecognised() {
        assertTrue(RiskyButtons.isRisky("Delete for everyone"))
        assertTrue(RiskyButtons.isRisky("Block"))
        assertTrue(RiskyButtons.isRisky("भुगतान करें"))
        assertFalse(RiskyButtons.isRisky("Send"))
    }

    @Test
    fun theLocalSummaryReadsInScreenOrderAndMasksCardNumbers() {
        val s = ScreenSnapshot(
            "com.irctc", "IRCTC Rail Connect",
            listOf(
                ScreenElement("IRCTC Rail Connect", ElementKind.TEXT),
                ScreenElement("PNR 4512345678", ElementKind.TEXT),
                ScreenElement("Status: CNF S4 32", ElementKind.TEXT),
                ScreenElement("Card 4111 1111 1111 1234", ElementKind.TEXT),
                ScreenElement("", ElementKind.INPUT, editable = true, hint = "Search"),
                ScreenElement("Book", ElementKind.BUTTON, clickable = true),
            ),
        )
        assertEquals("स्क्रीन पर IRCTC Rail Connect खुला है। लिखा है: PNR 4512345678। Status: CNF S4 32। Card ••••1234। Book", s.localSummary())
        assertTrue(s.describeForAi().contains("Card ••••1234"))
        assertTrue(s.describeForAi().contains("PNR 4512345678")) // a 10-digit PNR is not a card number
        assertEquals("স্ক্রিনে Camera খোলা। পড়ার মতো লেখা নেই।", ScreenSnapshot("x", "Camera", emptyList()).localSummary(bengali = true))
    }
}
