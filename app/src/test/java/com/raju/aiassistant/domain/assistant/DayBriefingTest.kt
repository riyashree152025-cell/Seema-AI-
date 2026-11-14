package com.raju.aiassistant.domain.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DayBriefingTest {
    @Test
    fun askingForTheDayIsTheBriefing() {
        listOf("subah ki briefing", "aaj ka haal", "aaj ka hal batao", "सुबह की ब्रीफ़िंग", "आज का हाल बताओ").forEach {
            assertEquals(it, AssistantAction.DayBriefing, LocalCommands.parse(it))
        }
    }

    @Test
    fun otherSentencesAreNotTheBriefing() {
        assertNull(LocalCommands.parse("aaj mausam kaisa hai")?.takeIf { it == AssistantAction.DayBriefing })
    }
}
