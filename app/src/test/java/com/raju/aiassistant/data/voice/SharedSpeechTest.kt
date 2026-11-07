package com.raju.aiassistant.data.voice

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Speech turns on the shared microphone: the endpointing (pure) and the STT / wake hand-over (on a hub). */
@OptIn(ExperimentalCoroutinesApi::class)
class SharedSpeechTest {

    private class ProgramSource(private val frames: List<ByteArray>, private val failAfter: Int? = null) : FrameSource {
        var closed = 0
        private var index = 0
        private var pos = 0

        override fun open(): Boolean = true

        override suspend fun read(buf: ByteArray, offset: Int, length: Int): Int {
            if (failAfter != null && index >= failAfter) return -1
            if (index >= frames.size) return -1
            delay(10)
            val n = minOf(length, MIC_FRAME_BYTES / 2)
            frames[index].copyInto(buf, offset, pos, pos + n)
            pos += n
            if (pos == MIC_FRAME_BYTES) {
                pos = 0
                index++
            }
            return n
        }

        override fun close() {
            closed++
        }
    }

    private fun frame(amplitude: Int): ByteArray = ByteArray(MIC_FRAME_BYTES) { i ->
        if (i % 2 == 0) (amplitude and 0xFF).toByte() else (amplitude shr 8).toByte()
    }

    private val silence = frame(0)
    private val speech = frame(3_000)

    /**
     * 300 ms of room calibration, 600 ms of speech, then the 1.2 s pause that ends the turn: the turn's
     * audio is 105 frames (the 10 frames after the pause are not part of it).
     */
    private val oneTurn = List(15) { silence } + List(30) { speech } + List(70) { silence }

    private fun TestScope.hub(source: () -> FrameSource, consumers: CoroutineDispatcher = StandardTestDispatcher(testScheduler)) =
        MicHub(
            scope = backgroundScope,
            sourceFactory = source,
            ringFrames = 8,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            consumerDispatcher = consumers,
        )

    // ---------------------------------------------------------------- endpointing (pure)

    @Test
    fun aSpokenTurnEndsWithItsAudio() {
        val c = SpeechCapture()
        val outcomes = oneTurn.map { c.feed(it) }
        val done = outcomes.first { it != null }
        assertTrue(done is SpeechCapture.Outcome.Done)
        assertEquals(105 * MIC_FRAME_BYTES, (done as SpeechCapture.Outcome.Done).pcm.size)
        assertEquals(1, outcomes.count { it != null }) // one decision, once
    }

    @Test
    fun silenceAloneEndsWithNoSpeechAfterTheTimeout() {
        val c = SpeechCapture()
        val outcomes = List(360) { c.feed(silence) }
        assertEquals(SpeechCapture.Outcome.NoSpeech, outcomes.first { it != null })
        assertNull(outcomes[299]) // the decision comes at the timeout, not before
    }

    // ---------------------------------------------------------------- hand-over on the hub

    @Test
    fun speechRecognitionTakesTheMicWhileTheWakeListenerWaitsAndGivesItBackAfter() = runTest {
        // A short lead-in of silence: the turn starts while speech recognition is listening to it.
        val source = ProgramSource(List(20) { silence } + oneTurn + List(200) { silence })
        val hub = hub({ source })
        var wakeFrames = 0
        val wake = hub.lease(MicPurpose.WAKE, { wakeFrames++ })
        advanceTimeBy(100)
        runCurrent()
        val beforeTurn = wakeFrames
        assertTrue(beforeTurn > 0)

        var pcm: ByteArray? = null
        val turn = launch { pcm = hub.recordSpeech() }
        advanceTimeBy(1_900) // the turn is still being spoken
        runCurrent()
        assertEquals(beforeTurn, wakeFrames) // the wake listener got nothing during the turn
        assertNull(pcm)

        advanceTimeBy(600) // the turn ends
        runCurrent()
        assertNotNull(pcm)
        assertTrue(turn.isCompleted)

        val afterTurn = wakeFrames
        advanceTimeBy(200)
        runCurrent()
        assertTrue(wakeFrames > afterTurn) // the wake listener resumes when speech recognition is done
        wake.release()
    }

    @Test
    fun cancellingTheTurnReleasesTheMic() = runTest {
        val hub = hub({ ProgramSource(oneTurn) })
        val turn = launch { hub.recordSpeech() }
        advanceTimeBy(100)
        runCurrent()
        assertEquals(1, hub.stats().leases)

        turn.cancel()
        advanceTimeBy(50)
        runCurrent()
        assertEquals(0, hub.stats().leases)
        assertFalse(hub.stats().recording || hub.stats().leases > 0)
    }

    @Test
    fun aMicrophoneFailureDuringTheTurnIsReportedAsSharedMicException() = runTest {
        val hub = hub({ ProgramSource(oneTurn, failAfter = 3) })
        var caught: Throwable? = null
        val turn = launch {
            try {
                hub.recordSpeech()
            } catch (e: Throwable) {
                caught = e
            }
        }
        advanceTimeBy(300)
        runCurrent()
        assertTrue(turn.isCompleted)
        assertTrue(caught is SharedMicException)
        assertEquals(0, hub.stats().leases)
    }

    @Test
    fun silenceOnTheSharedMicGivesNullNotAnError() = runTest {
        val hub = hub({ ProgramSource(List(400) { silence }) })
        var result: ByteArray? = ByteArray(1)
        var finished = false
        launch {
            result = hub.recordSpeech()
            finished = true
        }
        advanceTimeBy(8_000)
        runCurrent()
        assertTrue(finished)
        assertNull(result)
    }
}
