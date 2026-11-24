package com.raju.aiassistant.domain.voice

import com.raju.aiassistant.domain.assistant.AssistantAction
import com.raju.aiassistant.domain.assistant.LocalCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordGateTest {

    @Test
    fun acceptedWakePhrasesStrictExtractionAndStripping() {
        // "hey seema", "hi seema", "hello seema", "arey seema", "suno seema",
        // "हे सीमा", "अरे सीमा", "सुनो सीमा", "হেই সীমা", "এই সীমা", "শোনো সীমা"
        assertEquals("aaj kitna paisa kata", WakeWordGate.request("Hey Seema aaj kitna paisa kata"))
        assertEquals("aaj kitna paisa kata", WakeWordGate.request("hey seema, aaj kitna paisa kata"))
        assertEquals("aaj kitna paisa kata", WakeWordGate.request("HEY SEEMA aaj kitna paisa kata"))
        assertEquals("time kya hua", WakeWordGate.request("Hi Seema time kya hua"))
        assertEquals("time kya hua", WakeWordGate.request("Hello Seema time kya hua"))
        assertEquals("aaj kitna paisa kata", WakeWordGate.request("arey seema aaj kitna paisa kata"))
        assertEquals("aaj kitna paisa kata", WakeWordGate.request("are seema aaj kitna paisa kata"))
        assertEquals("aaj kitna paisa kata", WakeWordGate.request("suno seema aaj kitna paisa kata"))

        assertEquals("मौसम बताओ", WakeWordGate.request("हे सीमा मौसम बताओ"))
        assertEquals("मौसम बताओ", WakeWordGate.request("अरे सीमा मौसम बताओ"))
        assertEquals("मौसम बताओ", WakeWordGate.request("सुनो सीमा मौसम बताओ"))

        assertEquals("আজ কত টাকা এসেছে", WakeWordGate.request("হেই সীমা আজ কত টাকা এসেছে"))
        assertEquals("আজ কত টাকা এসেছে", WakeWordGate.request("এই সীমা আজ কত টাকা এসেছে"))
        assertEquals("কী খবর", WakeWordGate.request("শোনো সীমা কী খবর"))

        // Wake phrases alone return empty string for greeting / wake-up
        assertEquals("", WakeWordGate.request("Hey Seema"))
        assertEquals("", WakeWordGate.request("hi seema"))
        assertEquals("", WakeWordGate.request("hello seema"))
        assertEquals("", WakeWordGate.request("arey seema"))
        assertEquals("", WakeWordGate.request("suno seema"))
        assertEquals("", WakeWordGate.request("हे सीमा"))
        assertEquals("", WakeWordGate.request("अरे सीमा"))
        assertEquals("", WakeWordGate.request("सुनो सीमा"))
        assertEquals("", WakeWordGate.request("হেই সীমা"))
        assertEquals("", WakeWordGate.request("এই সীমা"))
        assertEquals("", WakeWordGate.request("শোনো সীমা"))
    }

    @Test
    fun ordinaryConversationIsDroppedSilently() {
        for (talk in listOf(
            "haan bhai kal milte hain",
            "chai piyoge",
            "it seems fine",
            "Simran ko bolo aa jaye",
            "दुकान कितने बजे खुलेगी",
            "কাল দেখা হবে",
            "kal seema aayi thi",
            "maine seema ko bola tha",
            "talking to seema right now",
            "aaj kitna paisa kata", // missing wake word while follow-up is closed
        )) {
            assertNull(talk, WakeWordGate.request(talk))
            assertEquals(talk, WakeWordGate.Verdict.Ignore, WakeWordGate.check(talk, followUpOpen = false))
        }
        assertEquals(WakeWordGate.Verdict.Ignore, WakeWordGate.check("   ", followUpOpen = true))
    }

    @Test
    fun insideTheWindowNoNameIsNeededButOtherAssistantsAreNotHer() {
        assertEquals(WakeWordGate.Verdict.FollowUp("aur batao"), WakeWordGate.check("aur batao", followUpOpen = true))
        assertEquals(WakeWordGate.Verdict.Called("time batao"), WakeWordGate.check("Seema time batao", followUpOpen = false))
        for (other in listOf("Hey Google aaj ka mausam", "Ok Google", "Gemini ye kya hai", "Alexa light on karo", "Siri what time is it", "हे गूगल गाना चलाओ", "জেমিনি বলো")) {
            assertEquals(other, WakeWordGate.Verdict.OtherAssistant, WakeWordGate.check(other, followUpOpen = true))
        }
        // Naming Google inside a request to her is still for her.
        assertEquals(WakeWordGate.Verdict.FollowUp("Google pe search karo"), WakeWordGate.check("Google pe search karo", followUpOpen = true))
        assertFalse(WakeWordGate.addressesOther("Google pe search karo"))
    }

    @Test
    fun sevenSecondWindow() {
        var now = 0L
        val t = ConversationSessionTimer { now }
        assertFalse(t.isOpen())
        t.openAfterReply()
        now = 6_900
        assertTrue(t.isOpen())
        now = 7_100
        assertFalse(t.isOpen())
        // A sentence begun inside the window is still being recognised a little later.
        assertTrue(t.accepts(listenStartedAt = 5_000))
        assertFalse(t.accepts(listenStartedAt = 7_050))
        now = 14_000
        assertFalse(t.accepts(listenStartedAt = 5_000))
    }

    @Test
    fun theWindowClosesAtOnceOnGeminiOrChupRaho() {
        var now = 0L
        val t = ConversationSessionTimer { now }
        t.openAfterReply()
        now = 1_000
        assertTrue(t.isOpen())
        assertEquals(WakeWordGate.Verdict.OtherAssistant, WakeWordGate.check("Gemini", followUpOpen = t.isOpen()))
        t.close()
        assertFalse(t.isOpen())
        assertFalse(t.accepts(listenStartedAt = 500))
        // A longer question of hers is never cut short by the next reply's 7 s.
        t.openFor(60_000)
        t.openAfterReply()
        now = 50_000
        assertTrue(t.isOpen())
    }

    @Test
    fun quietCommands() {
        for (q in listOf(
            "Seema chup raho",
            "Seema so jao",
            "Abhi mat suno",
            "সীমা চুপ থাকো",
            "सीमा चुप रहो",
            "अभी मत सुनो",
            "chup raho please",
            "so jao",
        )) {
            assertTrue(q, WakeWordGate.isQuietCommand(q))
            assertEquals(q, AssistantAction.GoToSleep, LocalCommands.parse(q))
        }
        assertFalse(WakeWordGate.isQuietCommand("chup chap kaam karo"))
        assertFalse(WakeWordGate.isQuietCommand("gaana suno"))
    }
}
