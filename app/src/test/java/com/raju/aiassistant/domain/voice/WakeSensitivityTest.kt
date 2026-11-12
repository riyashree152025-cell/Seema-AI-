package com.raju.aiassistant.domain.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeSensitivityTest {
    @Test
    fun higherSensitivityWakesMoreEasily() {
        assertTrue(WakeSensitivity.HIGH.threshold < WakeSensitivity.NORMAL.threshold)
        assertTrue(WakeSensitivity.NORMAL.threshold < WakeSensitivity.LOW.threshold)
        assertTrue(WakeSensitivity.HIGH.boost.toFloat() > WakeSensitivity.LOW.boost.toFloat())
        WakeSensitivity.entries.forEach { assertTrue(it.name, it.threshold in 0.01f..0.5f) }
    }

    @Test
    fun normalIsTheEarlierSetupAndUnknownValuesFallBackToIt() {
        assertEquals(WAKE_WORD_THRESHOLD, WakeSensitivity.NORMAL.threshold)
        assertEquals(WAKE_WORD_KEYWORDS, wakeWordKeywords(WakeSensitivity.NORMAL))
        assertTrue(WAKE_WORD_KEYWORDS.split("/").all { it.endsWith(" :2.0 @HEY_SEEMA") })
        assertEquals(WakeSensitivity.NORMAL, WakeSensitivity.from(null))
        assertEquals(WakeSensitivity.NORMAL, WakeSensitivity.from("loud"))
        assertEquals(WakeSensitivity.HIGH, WakeSensitivity.from("HIGH"))
        assertTrue(wakeWordKeywords(WakeSensitivity.HIGH).split("/").all { it.endsWith(" :3.0 @HEY_SEEMA") })
    }
}
