package com.raju.aiassistant.domain.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LanguageAndSleepCommandTest {
    @Test
    fun askingForBengaliKeepsBengali() {
        listOf("बंगाली में बोलो", "bangla mein baat karo", "Bengali me jawab do", "বাংলায় কথা বলো", "अब से बांग्ला में बात करना")
            .forEach { assertEquals(it, AssistantAction.SetLanguage("bn-IN"), LocalCommands.parse(it)) }
    }

    @Test
    fun bengaliSaidInBengaliAndWrittenByAHindiRecogniserStillSwitches() {
        // Deepgram (Hindi) writes Bengali speech in Hindi letters.
        listOf("बांग्लाय कथा बोलो", "बांग्लाय कोथा बोलो", "बंगाली में बात करो ना", "bangla te kotha bolo", "बंगला में बोलो")
            .forEach { assertEquals(it, AssistantAction.SetLanguage("bn-IN"), LocalCommands.parse(it)) }
        assertNotEquals(AssistantAction.SetLanguage("bn-IN"), LocalCommands.parse("बंगाल की खाड़ी पर बात करो"))
    }

    @Test
    fun hindiAndBothLanguagesSwitchBack() {
        assertEquals(AssistantAction.SetLanguage("hi-IN"), LocalCommands.parse("हिंदी में बात करो"))
        assertEquals(AssistantAction.SetLanguage("hi-IN"), LocalCommands.parse("হিন্দিতে কথা বলো"))
        assertEquals(AssistantAction.SetLanguage("auto"), LocalCommands.parse("दोनों भाषा में बात करो"))
        assertEquals(AssistantAction.SetLanguage("bn-IN"), parseReply("""<action>{"type":"set_language","language":"bengali"}</action>""").actions.single())
    }

    @Test
    fun soJaoPutsHerToSleep() {
        listOf("सो जाओ", "so jao", "ab so jao", "Seema, so jao", "ঘুমিয়ে পড়ো", "ঘুমাও", "अच्छा सो जाओ अब")
            .forEach { assertEquals(it, AssistantAction.GoToSleep, LocalCommands.parse(it)) }
    }

    @Test
    fun ordinarySentencesAreNotLanguageOrSleepCommands() {
        assertNotEquals(AssistantAction.GoToSleep, LocalCommands.parse("मुझे नींद नहीं आ रही, क्या करूँ"))
        assertNotEquals(AssistantAction.SetLanguage("hi-IN"), LocalCommands.parse("hindi gaane chalao"))
    }
}
