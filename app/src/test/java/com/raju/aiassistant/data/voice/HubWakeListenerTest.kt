package com.raju.aiassistant.data.voice

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shared-microphone wake path end to end on the JVM: a [MicHub] fed by a scripted microphone,
 * with [HubWakeListener] on top. Same contract as the original wake listener.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HubWakeListenerTest {

    /** Plays [frames] one after another (two half-reads per frame, 20 ms of virtual time each), then ends. */
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

    private fun TestScope.hub(source: () -> FrameSource, consumers: CoroutineDispatcher = StandardTestDispatcher(testScheduler)) =
        MicHub(
            scope = backgroundScope,
            sourceFactory = source,
            ringFrames = 8,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            consumerDispatcher = consumers,
        )

    @Test
    fun aSpokenPhraseIsDeliveredOnceAndTheMicrophoneIsReleasedForTheNextStep() = runTest {
        val silence = frame(0)
        val speech = frame(3_000)
        val script = List(10) { silence } + List(25) { speech } + List(40) { silence }
        val source = ProgramSource(script)
        val hub = hub({ source })
        val delivered = mutableListOf<ByteArray>()
        var errors = 0
        val listener = HubWakeListener(hub)

        listener.start({ delivered += it }, { errors++ })
        advanceTimeBy(2_000)
        runCurrent()

        assertEquals(1, delivered.size)
        assertEquals(68 * MIC_FRAME_BYTES, delivered[0].size)
        assertEquals(0, errors)
        assertEquals(0, hub.stats().leases) // the wake lease ended with the utterance
    }

    @Test
    fun quietAirNeverWakesAndNeverFails() = runTest {
        val source = ProgramSource(List(300) { frame(0) })
        val hub = hub({ source })
        var delivered = 0
        var errors = 0
        val listener = HubWakeListener(hub)

        listener.start({ delivered++ }, { errors++ })
        advanceTimeBy(3_000)
        runCurrent()

        assertEquals(0, delivered)
        assertEquals(0, errors)
        assertTrue(hub.stats().framesRead > 0)
        listener.stop()
    }

    @Test
    fun stopBeforeTheSpeechEndsGivesNoUtteranceAndReleasesTheMic() = runTest {
        val script = List(10) { frame(0) } + List(25) { frame(3_000) } + List(40) { frame(0) }
        val hub = hub({ ProgramSource(script) })
        var delivered = 0
        val listener = HubWakeListener(hub)

        listener.start({ delivered++ }, {})
        advanceTimeBy(300) // ten quiet frames (200 ms) passed and the speech is being recorded
        runCurrent()
        listener.stop()
        advanceTimeBy(3_000)
        runCurrent()

        assertEquals(0, delivered)
        assertEquals(0, hub.stats().leases)
    }

    @Test
    fun aMicrophoneThatFailsReportsOnceAndTheWakeListenerStops() = runTest {
        val hub = hub({ ProgramSource(List(5) { frame(0) }) }) // runs out after 5 frames: a read failure
        var errors = 0
        var delivered = 0
        val listener = HubWakeListener(hub)

        listener.start({ delivered++ }, { errors++ })
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(1, errors)
        assertEquals(0, delivered)
        assertEquals(0, hub.stats().leases)
    }

    @Test
    fun aSecondStartReplacesTheFirstWithoutDoubleDelivery() = runTest {
        val script = List(10) { frame(0) } + List(25) { frame(3_000) } + List(40) { frame(0) }
        val hub = hub({ ProgramSource(script) })
        var first = 0
        var second = 0
        val listener = HubWakeListener(hub)

        listener.start({ first++ }, {})
        advanceTimeBy(40)
        runCurrent()
        listener.start({ second++ }, {}) // the old session is cancelled, not run alongside
        advanceTimeBy(2_000)
        runCurrent()

        assertEquals(0, first)
        assertEquals(1, second)
        assertEquals(0, hub.stats().leases)
    }
}
