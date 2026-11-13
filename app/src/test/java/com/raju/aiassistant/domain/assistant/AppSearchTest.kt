package com.raju.aiassistant.domain.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppSearchTest {
    private fun p(t: String) = LocalCommands.parse(t)

    @Test
    fun searchesInsideAppsOpenOnTheirResults() {
        assertEquals(AssistantAction.AppSearch("Chrome", "ChatGPT"), p("Chrome kholo aur usme ChatGPT search karo"))
        assertEquals(AssistantAction.AppSearch("क्रोम", "चैट जीपीटी"), p("क्रोम खोल के चैट जीपीटी सर्च करो"))
        assertEquals(AssistantAction.AppSearch("YouTube", "Kishore Kumar ke gaane"), p("YouTube pe Kishore Kumar ke gaane dhundo"))
        assertEquals(AssistantAction.AppSearch("Play Store", "Paytm"), p("Paytm ko Play Store me search karo"))
        assertEquals(AssistantAction.AppSearch("Google", "aaj ka mausam"), p("Google par aaj ka mausam search karo"))
        assertNull(p("Chrome kholo") as? AssistantAction.AppSearch)
    }

    @Test
    fun eachAppGetsItsOwnResultsLink() {
        assertEquals("https://www.google.com/search?q=ChatGPT", appSearchUrl("Chrome", "ChatGPT"))
        assertEquals("https://www.youtube.com/results?search_query=Kishore+Kumar", appSearchUrl("YouTube", "Kishore Kumar"))
        assertEquals("market://search?q=Paytm", appSearchUrl("Play Store", "Paytm"))
        assertEquals("geo:0,0?q=Howrah+station", appSearchUrl("maps", "Howrah station"))
        assertEquals("https://www.google.com/search?q=x", appSearchUrl(null, "x"))
    }
}
