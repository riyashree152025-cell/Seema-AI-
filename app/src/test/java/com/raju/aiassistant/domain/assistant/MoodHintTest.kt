package com.raju.aiassistant.domain.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoodHintTest {
    @Test
    fun theMoodIsReadFromTheWords() {
        assertEquals(Mood.TIRED, moodOf("aaj bahut thak gaya hoon, kal ka plan batao"))
        assertEquals(Mood.STRESSED, moodOf("ticket confirm nahi hua, bahut tension ho rahi hai"))
        assertEquals(Mood.SAD, moodOf("आज मन उदास है"))
        assertEquals(Mood.ANGRY, moodOf("kya bakwas jawab hai ye"))
        assertEquals(Mood.HAPPY, moodOf("ticket confirm ho gaya, maza aa gaya"))
        assertNull(moodOf("kal ka mausam batao"))
        assertNull(moodOf("ustension wire")) // whole words only
    }
}
