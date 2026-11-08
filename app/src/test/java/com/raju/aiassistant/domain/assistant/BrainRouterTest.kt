package com.raju.aiassistant.domain.assistant

import com.raju.aiassistant.domain.ai.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Test

class BrainRouterTest {
    @Test
    fun quickMessagesGoFastReasoningGoesDeep() {
        listOf("kaise ho", "namaste seema", "Bharat ki rajdhani kya hai", "aaj mausam kaisa hai", "ek joke sunao").forEach {
            assertEquals(it, BrainTier.FAST, brainTier(it))
        }
        listOf(
            "PNR 4512345678 ke teeno yatriyon ka status samjhao",
            "is hafte ka kharcha category wise batao",
            "Delhi aur Mumbai mein rehne ka kharcha compare karo",
            "kal ke liye ek plan banao",
            "ye kyun hua?",
            "मेरी यात्रा की योजना बनाओ",
            "kya tum ye kar sakti ho? aur kab tak?",
            "a ".repeat(30),
        ).forEach { assertEquals(it, BrainTier.DEEP, brainTier(it)) }
    }

    @Test
    fun eachTierHasItsBrainAndItsShareOfContext() {
        assertEquals(ProviderId.GROQ, preferredProvider(BrainTier.FAST))
        assertEquals(ProviderId.GEMINI, preferredProvider(BrainTier.DEEP))
        assertEquals(2, historyExchangesFor(BrainTier.FAST, 6))
        assertEquals(1, historyExchangesFor(BrainTier.FAST, 1))
        assertEquals(6, historyExchangesFor(BrainTier.DEEP, 6))
        assertEquals(2, memoryLimitFor(BrainTier.FAST))
        assertEquals(12, memoryLimitFor(BrainTier.DEEP))
    }
}
