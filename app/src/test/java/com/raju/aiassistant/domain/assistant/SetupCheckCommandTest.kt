package com.raju.aiassistant.domain.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupCheckCommandTest {
    @Test
    fun askingForWhatIsMissingOpensSeemasOwnChecklist() {
        listOf(
            "sab access hai tumhare paas agar jo baki hai uska setting kholo",
            "जो सेटिंग बाकी है खोलो",
            "permission check karo",
            "क्या क्या परमिशन बाकी है",
        ).forEach { assertEquals(it, AssistantAction.OpenSetupCheck, LocalCommands.parse(it)) }
        assertEquals(AssistantAction.OpenSetupCheck, parseReply("""<action>{"type":"open_setup"}</action>""").actions.single())
    }

    @Test
    fun phoneSettingsPagesStillOpenAsBefore() {
        assertTrue(LocalCommands.parse("wifi settings kholo") is AssistantAction.OpenSettings)
    }
}
