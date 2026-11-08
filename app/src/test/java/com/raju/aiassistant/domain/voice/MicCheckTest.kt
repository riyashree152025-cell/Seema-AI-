package com.raju.aiassistant.domain.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MicCheckTest {
    @Test
    fun theMicTestSaysWhatIsWrong() {
        assertEquals(MicVerdict.NO_AUDIO, micVerdict(0, 0f))
        assertEquals(MicVerdict.VERY_QUIET, micVerdict(150, 0.02f))
        assertEquals(MicVerdict.QUIET, micVerdict(150, 0.08f))
        assertEquals(MicVerdict.GOOD, micVerdict(150, 0.4f))
        assertTrue(micVerdictText(MicVerdict.QUIET, 0.08f).contains("8%"))
        assertTrue(micVerdictText(MicVerdict.NO_AUDIO, 0f).startsWith("✗"))
    }
}
