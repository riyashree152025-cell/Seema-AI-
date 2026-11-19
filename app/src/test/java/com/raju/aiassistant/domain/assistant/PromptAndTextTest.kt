package com.raju.aiassistant.domain.assistant

import com.raju.aiassistant.domain.ai.Turn
import com.raju.aiassistant.domain.ai.TurnRole
import com.raju.aiassistant.domain.model.ChatMessage
import com.raju.aiassistant.domain.model.ChatRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptAndTextTest {
    private fun msg(role: ChatRole, text: String) = ChatMessage(0, 0, role, text, 0)

    @Test
    fun turnsMergeSameRoleAndStartWithUser() {
        val turns = toTurns(
            listOf(msg(ChatRole.ASSISTANT, "old"), msg(ChatRole.USER, "a"), msg(ChatRole.SYSTEM, "done"), msg(ChatRole.ASSISTANT, "b")),
            limit = 10,
        )
        assertEquals(listOf(Turn(TurnRole.USER, "a\n\n[App note] done"), Turn(TurnRole.ASSISTANT, "b")), turns)
    }

    @Test
    fun seemaIsOneSoulfulPartnerWithNoModes() {
        val p = buildSystemPrompt(emptyList(), "today")
        assertTrue(p.lineSequence().first().contains("life partner"))
        assertTrue(p.contains("SOUL:") && p.contains("cared for and at peace"))
        assertTrue(p.contains("a late hour (gently ask him to rest)"))
        assertTrue(p.contains("R Travels day"))
        listOf("Tapori", "Masti", "Professional", "Humsafar", "PERSONALITY:", "gaali", "jealous", "JEALOUSY").forEach {
            assertFalse("still mentions $it", p.contains(it))
        }
        assertTrue(p.contains("never formal or bookish Hindi, robotic"))
        assertTrue(p.contains("\"type\":\"call\"")) // phone tools are still there
    }

    @Test
    fun sheStaysPresentWarmAndSpokenNeverAHelpDesk() {
        val p = buildSystemPrompt(emptyList(), "today")
        assertTrue(p.contains("never bring up being an AI yourself"))
        assertFalse(p.contains("say honestly you are an AI"))
        assertFalse(p.contains("you are an AI without feelings"))
        assertTrue(p.contains("never detached"))
        assertTrue(p.contains("\"हम्म…\"") && p.contains("अच्छा सुनो ना…") && p.contains("अरे पागल…"))
        assertTrue(p.contains("never help-desk lines like \"मैं आपकी कैसे सहायता कर सकती हूँ\""))
        assertTrue(p.contains("1-3 short spoken sentences"))
        assertTrue(p.length < 9_500)
    }

    @Test
    fun sheMirrorsHindiOrBengaliWithoutASwitch() {
        val base = buildSystemPrompt(emptyList(), "today")
        assertTrue(base.contains("mirrored from him every turn without being asked"))
        assertTrue(base.contains("Bengali → natural, homely Bengali in Bengali script"))
        assertTrue(base.contains("Hindi written in Bengali letters by the speech recognizer".replace("recognizer", "recogniser")))
        val bn = buildSystemPrompt(emptyList(), "today", PromptContext(language = com.raju.aiassistant.domain.language.SeemaLanguage.BENGALI))
        assertTrue(bn.contains("LANGUAGE NOW: বাংলা — reply ONLY in Bengali"))
        val hi = buildSystemPrompt(emptyList(), "today", PromptContext(language = com.raju.aiassistant.domain.language.SeemaLanguage.HINDI))
        assertTrue(hi.contains("LANGUAGE NOW: Hindi"))
    }

    @Test
    fun appErrorNoticesAreNotSentBackToTheAi() {
        val turns = toTurns(
            listOf(
                msg(ChatRole.USER, "hi"),
                msg(ChatRole.SYSTEM, "⚠️ Quota exceeded for this key (HTTP 429): " + "x".repeat(300)),
                msg(ChatRole.SYSTEM, "↪ Gemini: busy — answered by Groq instead."),
                msg(ChatRole.ASSISTANT, "Haan ji"),
                msg(ChatRole.SYSTEM, "✅ " + "y".repeat(500)),
            ),
            limit = 10,
        )
        assertEquals("hi", turns[0].text)
        assertEquals("Haan ji", turns[1].text)
        assertTrue(turns[2].text.startsWith("[App note] ✅") && turns[2].text.length < 230)
    }

    @Test
    fun turnsRespectLimitAndCharBudget() {
        val many = (1..50).map { msg(if (it % 2 == 1) ChatRole.USER else ChatRole.ASSISTANT, "x".repeat(100)) }
        assertTrue(toTurns(many, limit = 10).size <= 10)
        val budget = toTurns(many, limit = 50, maxChars = 350)
        assertTrue(budget.sumOf { it.text.length } <= 350)
        assertEquals(TurnRole.USER, budget.first().role)
    }

    @Test
    fun systemPromptIncludesToolsAndMemories() {
        val p = buildSystemPrompt(listOf("Mom's name is Sita"), "today")
        assertTrue(p.contains("\"type\":\"call\""))
        assertTrue(p.contains("Mom's name is Sita"))
        assertTrue(p.contains("Devanagari")) // Hindi in, Hindi script out
        assertTrue(p.contains("ACT, DON'T ASK"))
    }

    @Test
    fun speakableTextRemovesMarkdownLinksAndCitations() {
        val s = speakableText("**Namaste!** Weather is 30° [1].\n- Source: https://example.com/x")
        assertEquals("Namaste! Weather is 30° .\nSource:", s)
    }

    @Test
    fun chunksStayUnderLimitAndKeepAllText() {
        val text = (1..40).joinToString(" ") { "Yeh vakya number $it hai।" }
        val chunks = chunkForSpeech(text, maxChars = 120)
        assertTrue(chunks.all { it.length <= 120 })
        assertEquals(text.replace(" ", ""), chunks.joinToString("").replace(" ", ""))
    }

    @Test
    fun urlSafety() {
        assertTrue(isSafeWebUrl("https://news.example.com/a?b=1"))
        assertFalse(isSafeWebUrl("javascript:alert(1)"))
        assertFalse(isSafeWebUrl("https://user@evil.com"))
        assertFalse(isSafeWebUrl("file:///sdcard/x"))
    }

    @Test
    fun sensitiveDetection() {
        assertTrue(looksSensitive("my password is hunter2"))
        assertTrue(looksSensitive("card 4111 1111 1111 1111"))
        assertTrue(looksSensitive("key AIzaSyDabcdefghijklmnopqrstuv"))
        assertTrue(looksSensitive("मेरा पिन 1234"))
        assertFalse(looksSensitive("My favourite colour is blue"))
    }

    @Test
    fun promptKeepsStablePartFirstAndAddsTurnContext() {
        val p = buildSystemPrompt(emptyList(), "Monday 9 AM", context = PromptContext(voiceTurn = true, recentOpenings = listOf("Arre waah"), partOfDay = "morning"))
        assertTrue(p.indexOf("PHONE TOOLS") < p.indexOf("Monday 9 AM"))
        assertTrue(p.contains("Arre waah") && p.contains("morning"))
        assertEquals("Arre waah ji", openingOf("Arre waah ji, kya baat hai!"))
        assertEquals("morning", partOfDay(9))
    }

    @Test
    fun symbolsAreSpokenAsWords() {
        assertEquals("Price 500 rupees, 20 percent off", spokenSymbols("Price ₹500, 20% off"))
        assertEquals("आज 32 डिग्री है", spokenSymbols("आज 32°C है"))
        assertEquals("Tea and coffee, hot, ready", spokenSymbols("Tea & coffee (hot) ready"))
    }
}
