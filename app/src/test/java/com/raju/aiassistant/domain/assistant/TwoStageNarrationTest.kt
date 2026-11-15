package com.raju.aiassistant.domain.assistant

import com.raju.aiassistant.domain.model.Personality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TwoStageNarrationTest {

    @Test
    fun slowReadingTasksGetAPlanFirst() {
        val pnr = TwoStageNarration.plan(AssistantAction.PnrStatus("4512345678"), Personality.COMPANION)!!
        assertTrue(pnr.hindi, pnr.hindi.contains("PNR") && pnr.hindi.contains("बताती हूँ"))
        assertTrue(pnr.bengali.contains("PNR"))
        assertNotNull(TwoStageNarration.plan(AssistantAction.LookCamera(false, "ye kya hai"), Personality.COMPANION))
        assertNotNull(TwoStageNarration.plan(AssistantAction.ReadTicket, Personality.COMPANION))
        assertNotNull(TwoStageNarration.plan(AssistantAction.WhatsAppPeek("Anand"), Personality.COMPANION))
    }

    @Test
    fun taporiKeepsItsStyleAndProStaysShort() {
        assertTrue(TwoStageNarration.plan(AssistantAction.PnrStatus("1"), Personality.TAPORI)!!.hindi.contains("भिड़ू"))
        assertEquals("PNR देख रही हूँ।", TwoStageNarration.plan(AssistantAction.PnrStatus("1"), Personality.PRO)!!.hindi)
        assertNull(TwoStageNarration.checkIn(AssistantAction.PnrStatus("1"), Personality.PRO))
    }

    @Test
    fun quickOrChangingTasksGetNoPlan() {
        // Calls, messages and money ask for confirmation first and say their own result.
        assertNull(TwoStageNarration.plan(AssistantAction.Call("Mummy", null), Personality.COMPANION))
        assertNull(TwoStageNarration.plan(AssistantAction.Battery, Personality.COMPANION))
    }

    @Test
    fun aCaringQuestionFollowsResultsWorthSharing() {
        assertTrue(TwoStageNarration.checkIn(AssistantAction.PnrStatus("1"), Personality.COMPANION)!!.hindi.contains("WhatsApp"))
        assertTrue(TwoStageNarration.checkIn(AssistantAction.ReadTicket, Personality.COMPANION)!!.hindi.contains("WhatsApp"))
        assertNull(TwoStageNarration.checkIn(AssistantAction.WhatsAppPeek("Anand"), Personality.COMPANION))
    }

    @Test
    fun readingATicketIsUnderstoodButSendingOneIsNot() {
        assertEquals(AssistantAction.ReadTicket, LocalCommands.parse("abhi jo ticket download hua hai padh ke sunao"))
        assertEquals(AssistantAction.ReadTicket, LocalCommands.parse("टिकट पढ़ के सुनाओ"))
        assertTrue(LocalCommands.parse("ye ticket Anand ko WhatsApp par bhej do") != AssistantAction.ReadTicket)
        assertEquals(AssistantAction.ReadTicket, parseReply("""<action>{"type":"read_ticket"}</action>""").actions.single())
    }

    @Test
    fun theReplyMoodTagIsTakenOutOfTheText() {
        val r = parseReply("[MOOD:WHISPER] अब सो जाओ जान…")
        assertEquals("अब सो जाओ जान…", r.text)
        assertEquals(com.raju.aiassistant.domain.voice.VoiceMood.WHISPER, r.mood)
    }
}

