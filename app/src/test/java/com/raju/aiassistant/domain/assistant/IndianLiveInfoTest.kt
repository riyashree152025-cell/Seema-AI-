package com.raju.aiassistant.domain.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IndianLiveInfoTest {

    @Test
    fun cricketScoreQueriesAreRecognized() {
        val q1 = IndianLiveInfo.parse("India ka score kya hai")
        assertNotNull(q1)
        assertTrue(q1 is AssistantAction.SearchWeb)
        assertEquals("news", (q1 as AssistantAction.SearchWeb).topic)
        assertTrue(q1.query.contains("cricket score"))

        val q2 = IndianLiveInfo.parse("cricket score batao")
        assertNotNull(q2)

        val q3 = IndianLiveInfo.parse("স্কোর কত হয়েছে")
        assertNotNull(q3)

        val qLocal = LocalCommands.parse("India ka score kya hai")
        assertNotNull(qLocal)
        assertTrue(qLocal is AssistantAction.SearchWeb)
    }

    @Test
    fun goldAndSilverRatesAreRecognized() {
        val q1 = IndianLiveInfo.parse("aaj sone ka bhav kya hai")
        assertNotNull(q1)
        assertTrue(q1 is AssistantAction.SearchWeb)
        assertEquals("finance", (q1 as AssistantAction.SearchWeb).topic)

        val q2 = IndianLiveInfo.parse("aaj chandi ka rate kitna hai")
        assertNotNull(q2)

        val q3 = IndianLiveInfo.parse("আজ সোনার দাম কত")
        assertNotNull(q3)

        val qLocal = LocalCommands.parse("aaj sone ka bhav kya hai")
        assertNotNull(qLocal)
        assertTrue(qLocal is AssistantAction.SearchWeb)
    }

    @Test
    fun petrolAndDieselPricesAreRecognized() {
        val q1 = IndianLiveInfo.parse("petrol ka rate kya hai")
        assertNotNull(q1)
        assertTrue(q1 is AssistantAction.SearchWeb)

        val q2 = IndianLiveInfo.parse("diesel ka price batao")
        assertNotNull(q2)

        val q3 = IndianLiveInfo.parse("আজ পেট্রোলের দাম কত")
        assertNotNull(q3)

        val qLocal = LocalCommands.parse("petrol ka rate kya hai")
        assertNotNull(qLocal)
        assertTrue(qLocal is AssistantAction.SearchWeb)
    }

    @Test
    fun rainAndWeatherRadarAreRecognized() {
        val q1 = IndianLiveInfo.parse("aaj barish hogi kya")
        assertNotNull(q1)
        assertTrue(q1 is AssistantAction.SearchWeb)
        assertEquals("weather", (q1 as AssistantAction.SearchWeb).topic)

        val q2 = IndianLiveInfo.parse("barsaat hogi kya")
        assertNotNull(q2)

        val q3 = IndianLiveInfo.parse("আজ বৃষ্টি হবে কি")
        assertNotNull(q3)

        val qLocal = LocalCommands.parse("aaj barish hogi kya")
        assertNotNull(qLocal)
        assertTrue(qLocal is AssistantAction.SearchWeb)
    }

    @Test
    fun unrelatedTalkIsNotLiveInfo() {
        assertNull(IndianLiveInfo.parse("kahan ja rahe ho"))
        assertNull(IndianLiveInfo.parse("namaste"))
        assertNull(IndianLiveInfo.parse("gaana sunao"))
    }
}
