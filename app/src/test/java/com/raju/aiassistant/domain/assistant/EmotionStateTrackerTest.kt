package com.raju.aiassistant.domain.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class EmotionStateTrackerTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(h: Int, m: Int = 0, day: Int = 20) = LocalDateTime.of(2026, 11, day, h, m).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun backAfterThreeHoursSheMissedHim() {
        assertEquals(setOf(PresenceCue.MISSED_HIM), EmotionStateTracker.cues(at(17), at(13, 30), zone))
        val line = EmotionStateTracker.promptLine(setOf(PresenceCue.MISSED_HIM), at(17), at(13, 30), bengali = false)!!
        assertTrue(line, line.contains("about 3 hours") && line.contains("तुम कहाँ थे इतनी देर से"))
        assertTrue(EmotionStateTracker.promptLine(setOf(PresenceCue.MISSED_HIM), at(17), at(10), bengali = true)!!.contains("কোথায় ছিলে এতক্ষণ"))
    }

    @Test
    fun aShortGapOrTheFirstEverTalkIsNotAnAbsence() {
        assertTrue(EmotionStateTracker.cues(at(17), at(15, 1), zone).isEmpty())
        assertTrue(EmotionStateTracker.cues(at(17), null, zone).isEmpty())
        assertNull(EmotionStateTracker.promptLine(emptySet(), at(17), null, bengali = false))
    }

    @Test
    fun lateNightCareAfterHalfPastEleven() {
        assertTrue(EmotionStateTracker.cues(at(23, 10), at(23), zone).isEmpty())
        assertEquals(setOf(PresenceCue.LATE_NIGHT), EmotionStateTracker.cues(at(23, 40), at(23, 30), zone))
        assertEquals(setOf(PresenceCue.LATE_NIGHT), EmotionStateTracker.cues(at(1, 15, 21), at(1, 0, 21), zone))
        assertTrue(EmotionStateTracker.cues(at(5, 0, 21), at(4, 50, 21), zone).isEmpty())
        val line = EmotionStateTracker.promptLine(setOf(PresenceCue.LATE_NIGHT), at(23, 40), at(23, 30), bengali = false)!!
        assertTrue(line, line.contains("काम बंद करो अब"))
    }

    @Test
    fun bothAtOnce() {
        assertEquals(setOf(PresenceCue.MISSED_HIM, PresenceCue.LATE_NIGHT), EmotionStateTracker.cues(at(0, 30, 21), at(18), zone))
    }

    @Test
    fun theFeelingReachesTheInstructionsOnlyWhenThereIsOne() {
        val with = buildSystemPrompt(emptyList(), "now", PromptContext(feeling = "FEELING NOW: missed him"))
        assertTrue(with.contains("FEELING NOW: missed him"))
        assertTrue(!buildSystemPrompt(emptyList(), "now").contains("FEELING NOW"))
    }
}
