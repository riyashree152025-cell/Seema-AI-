package com.raju.aiassistant.domain.tatkal

import org.junit.Assert.assertTrue
import org.junit.Test

class TatkalReadyLineTest {
    @Test
    fun saysMinutesLeftAndTheMeasuredPing() {
        val said = tatkalReadyLine("AC", 9, 55, 142)
        assertTrue(said, said.contains("AC Tatkal खुलने में 5 मिनट बचे हैं"))
        assertTrue(said, said.contains("ping 142 ms"))
        assertTrue(said, said.contains("तैयार"))
    }

    @Test
    fun aSlowOrMissingNetworkIsSaidPlainly() {
        assertTrue(tatkalReadyLine("Sleeper", 10, 55, 1500).contains("धीमा"))
        assertTrue(tatkalReadyLine("Sleeper", 10, 55, null).contains("नहीं पहुँच रहा"))
        assertTrue(tatkalReadyLine("AC", 10, 0, 90).contains("अभी खुल रहा है"))
    }
}
