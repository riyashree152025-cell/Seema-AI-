package com.raju.aiassistant.domain.language

import com.raju.aiassistant.domain.assistant.AssistantAction
import com.raju.aiassistant.domain.assistant.LocalCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bengali spoken to Deepgram (a Hindi recogniser) arrives in Hindi letters; Seema answers in Bengali again. */
class BengaliInDevanagariTest {
    @Test
    fun bengaliInHindiLettersIsBengali() {
        listOf("तुमि केमोन आछो", "आमि एखन बाड़ी जाबो", "आमार एकटु काज आछे, तुमि कोरे दाओ").forEach {
            assertTrue(it, isBengaliInDevanagari(it))
            assertEquals(it, SeemaLanguage.BENGALI, nextConversationLanguage("auto", SeemaLanguage.HINDI, it))
        }
    }

    @Test
    fun hindiStaysHindi() {
        listOf("तुम कैसी हो", "मुझे कल की बुकिंग याद दिलाना", "ठीक है, क्या कर रही हो", "कि नहीं, ऐसा मत करो").forEach {
            assertFalse(it, isBengaliInDevanagari(it))
            assertEquals(it, SeemaLanguage.HINDI, nextConversationLanguage("auto", SeemaLanguage.BENGALI, it))
        }
    }

    @Test
    fun aLongSentenceWithTheSwitchStillSwitches() {
        val said = "देखो तुम पहले कितना अच्छा बंगला बात करती थी, अब से बंगला में बात करो, हिंदी नहीं चाहिए समझी"
        assertTrue(said.length > 80)
        assertEquals(AssistantAction.SetLanguage("bn-IN"), LocalCommands.parse(said))
    }
}
