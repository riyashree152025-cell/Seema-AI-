package com.raju.aiassistant.domain.assistant

import com.raju.aiassistant.domain.ai.FailureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlowProviderTest {
    @Test
    fun anAiThatStaysSilentRestsLongerThanAPerMinuteLimit() {
        assertEquals(SLOW_PROVIDER_COOLDOWN_MS, cooldownMsFor(FailureKind.TIMEOUT, "Gemini did not start answering within 6 seconds."))
        assertTrue(SLOW_PROVIDER_COOLDOWN_MS >= 3 * 60_000L)
        assertEquals(60_000L, cooldownMsFor(FailureKind.RATE_LIMITED, "tokens per minute (TPM)"))
    }

    @Test
    fun moreWaysOfAskingToRemember() {
        listOf("ये बात याद रखना", "dhyan rakhna ki kal meeting hai", "भूलना मत, कल 10 बजे", "याद कर लो ये नंबर", "yaad rakhna").forEach {
            assertTrue(it, asksToRemember(it))
        }
        assertTrue(!asksToRemember("aaj mausam kaisa hai"))
    }
}
