package com.raju.aiassistant.domain.routine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    private val knows: (String) -> Boolean = { it.contains("torch") || it.contains("volume") }

    @Test
    fun theEditorSavesOnlyWhatSeemaCanDo() {
        val ok = checkRoutine(5, "  Good   Morning ", "torch on karo\n\n- volume 40 karo", "7:05", true, listOf(night), knows)
        assertEquals(RoutineCheck.Ok(Routine(5, "Good Morning", listOf("torch on karo", "volume 40 karo"), 7, 5)), ok)
        assertEquals(RoutineCheck.Ok(Routine(6, "Voice only", listOf("torch on karo"))), checkRoutine(6, "Voice only", "torch on karo", "", true, emptyList(), knows))
        // Editing a routine keeps its own name.
        assertTrue(checkRoutine(1, "good night", "torch band karo", "", true, listOf(night), knows) is RoutineCheck.Ok)
    }

    @Test
    fun theEditorSaysWhatToFix() {
        fun problem(c: RoutineCheck) = (c as RoutineCheck.Problem).message
        assertTrue(problem(checkRoutine(5, " ", "torch on karo", "", true, emptyList(), knows)).contains("नाम"))
        assertTrue(problem(checkRoutine(5, "GOOD NIGHT", "torch on karo", "", true, listOf(night), knows)).contains("पहले से"))
        assertTrue(problem(checkRoutine(5, "X", "\n \n", "", true, emptyList(), knows)).contains("कम से कम एक काम"))
        assertTrue(problem(checkRoutine(5, "X", "torch on karo\ngaana gao", "", true, emptyList(), knows)).contains("gaana gao"))
        assertTrue(problem(checkRoutine(5, "X", "torch on karo", "25:00", true, emptyList(), knows)).contains("07:30"))
    }
}
