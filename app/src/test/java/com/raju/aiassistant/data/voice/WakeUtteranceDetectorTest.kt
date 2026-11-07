package com.raju.aiassistant.data.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure JVM: the sleep-mode voice detector fed with synthetic frames (silence = 0, speech = a loud constant). */
class WakeUtteranceDetectorTest {

    private fun frame(amplitude: Int): ByteArray = ByteArray(MIC_FRAME_BYTES) { i ->
        if (i % 2 == 0) (amplitude and 0xFF).toByte() else (amplitude shr 8).toByte()
    }

    private val silence = frame(0)
    private val speech = frame(3_000)

    /** Feeds [frames] and returns every utterance produced, with the frame index it came at. */
    private fun WakeUtteranceDetector.run(frames: List<ByteArray>): List<ByteArray> =
        frames.mapNotNull { feed(it) }

    @Test
    fun aSpokenPhraseIsDeliveredOnceAfterTheSpeakerPauses() {
        val d = WakeUtteranceDetector()
        // 10 frames of quiet, 25 of speech, then 40 of quiet (more than the 650 ms pause needed).
        val out = d.run(List(10) { silence } + List(25) { speech } + List(40) { silence })

        assertEquals(1, out.size)
        // Pre-roll (10 quiet + 3 loud frames to start) + 22 loud + 33 quiet frames that ended it.
        assertEquals(68 * MIC_FRAME_BYTES, out[0].size)
    }

    @Test
    fun aKnockShorterThanTheMinimumSpeechIsNeverAnUtterance() {
        val d = WakeUtteranceDetector()
        // Three loud frames (60 ms) then a long pause: below the 160 ms minimum, so nothing is delivered.
        val out = d.run(List(10) { silence } + List(3) { speech } + List(60) { silence })
        assertEquals(0, out.size)
    }

    @Test
    fun aKnockThenRealSpeechDeliversOnlyTheRealSpeech() {
        val d = WakeUtteranceDetector()
        val out = d.run(List(10) { silence } + List(2) { speech } + List(10) { silence } + List(25) { speech } + List(40) { silence })
        assertEquals(1, out.size)
    }

    @Test
    fun silenceAloneNeverProducesAnUtterance() {
        val d = WakeUtteranceDetector()
        assertEquals(0, d.run(List(500) { silence }).size)
    }

    @Test
    fun aLongSpeechIsCutAtTheMaximumLength() {
        val d = WakeUtteranceDetector()
        // Quiet first (the room's level is learned from it), then speech without a pause: the utterance
        // is cut at 7.5 s of recording (375 frames after the 3 that started it), plus the 13 pre-roll frames.
        repeat(10) { d.feed(silence) }
        val firstAt = (0 until 600).firstNotNullOfOrNull { i -> d.feed(speech)?.let { i to it } }
        assertNotNull(firstAt)
        assertEquals(388 * MIC_FRAME_BYTES, firstAt!!.second.size)
    }

    @Test
    fun afterAnUtteranceTheDetectorListensAgainForTheNextOne() {
        val d = WakeUtteranceDetector()
        val first = d.run(List(10) { silence } + List(25) { speech } + List(40) { silence })
        val second = d.run(List(25) { speech } + List(40) { silence })
        assertEquals(1, first.size)
        assertEquals(1, second.size)
        assertNull(d.feed(silence))
    }
}
