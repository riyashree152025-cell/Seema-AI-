package com.raju.aiassistant.domain.routine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoutinesTest {
    private val night = Routine(1, "Good Night", listOf("torch band karo", "volume 20 karo"))
    private val off = Routine(2, "Office", listOf("aaj ki briefing do"), enabled = false)

    @Test
    fun aRoutineIsRunByItsName() {
        assertEquals(night, routineFor("good night chalao", listOf(night, off)))
        assertEquals(night, routineFor("Good night routine shuru karo", listOf(night)))
        assertEquals(night, routineFor("good night", listOf(night)))
        assertNull(routineFor("good night ka matlab kya hai", listOf(night)))
        assertNull(routineFor("office chalao", listOf(night, off))) // switched off
    }

    @Test
    fun severalCommandsInOneSentenceAreSplit() {
        assertEquals(listOf("torch on karo", "volume 50 karo"), splitChain("torch on karo aur volume 50 karo"))
        assertEquals(listOf("टॉर्च जलाओ", "आवाज़ 50 करो"), splitChain("टॉर्च जलाओ और आवाज़ 50 करो"))
        assertNull(splitChain("torch on karo"))
    }

    @Test
    fun routinesAreSavedAndReadBack() {
        val list = listOf(night, Routine(3, "Subah", listOf("aaj ki briefing do"), 7, 0))
        assertEquals(list, decodeRoutines(encodeRoutines(list)))
        assertEquals(emptyList<Routine>(), decodeRoutines("broken"))
    }

    @Test
    fun stepsAndTimeComeFromTheEditor() {
        assertEquals(listOf("torch band karo", "volume 20 karo"), stepsFrom("- torch band karo\n\n• volume 20 karo\n"))
        assertEquals(7 to 30, parseRoutineTime("7:30"))
        assertEquals(21 to 0, parseRoutineTime("21"))
        assertNull(parseRoutineTime("25:00"))
        assertNull(parseRoutineTime("subah"))
    }
}
