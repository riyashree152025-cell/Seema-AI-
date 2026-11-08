package com.raju.aiassistant.data.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The loudness the shared microphone reports for Home's waves. */
class MicLevelTest {
    private fun frame(amplitude: Int) = ByteArray(MIC_FRAME_BYTES) { i -> if (i % 2 == 0) (amplitude and 0xFF).toByte() else (amplitude shr 8).toByte() }

    @Test
    fun silenceIsZeroSpeechIsHighAndLoudIsCapped() {
        assertEquals(0f, frameLevel(frame(0)), 0f)
        assertTrue(frameLevel(frame(2_500)) in 0.4f..0.6f)
        assertEquals(1f, frameLevel(frame(20_000)), 0f)
        assertEquals(frameLevel(frame(3_000)), frameLevel(frame(-3_000)), 1e-4f)
    }
}
