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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.CoroutineContext

/**
 * Pure JVM tests for [MicHub]: no Android, no real microphone. A [ScriptedSource] produces frames
 * whose first byte counts up (1, 2, 3, …), so every test can check order, loss and duplication.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MicHubTest {

    /** Produces one frame every 20 ms of virtual time, in two half-frame reads like a real AudioRecord. */
    private class ScriptedSource(
        private val openOk: Boolean = true,
        private val failAfterFrames: Int? = null,
    ) : FrameSource {
        var opened = 0
        var closed = 0
        private var frameIndex = 0
        private var posInFrame = 0

        override fun open(): Boolean {
            opened++
            return openOk
        }

        override suspend fun read(buf: ByteArray, offset: Int, length: Int): Int {
            if (failAfterFrames != null && frameIndex >= failAfterFrames) return -1
            delay(10)
            val n = minOf(length, MIC_FRAME_BYTES / 2)
            val value = (frameIndex % 100 + 1).toByte()
            for (i in 0 until n) buf[offset + i] = value
            posInFrame += n
            if (posInFrame == MIC_FRAME_BYTES) {
                posInFrame = 0
                frameIndex++
            }
            return n
        }

        override fun close() {
            closed++
        }
    }

    /**
     * A dispatcher that holds every task until the test runs it: a consumer on it is "stuck" for as
     * long as the test wants. (A second StandardTestDispatcher is not allowed inside runTest.)
     */
    private class HeldDispatcher : CoroutineDispatcher() {
        private val held = ArrayDeque<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            held.addLast(block)
        }

        /** Runs everything queued, including what the queued tasks queue in turn. */
        fun runAll() {
            while (held.isNotEmpty()) held.removeFirst().run()
        }
    }

    /** Records what a callback received: the first byte of each frame, and the frame length. */
    private class Received {
        val values = mutableListOf<Int>()
        val sizes = mutableListOf<Int>()
        val callback: (ByteArray) -> Unit = { frame ->
            values += frame[0].toInt()
            sizes += frame.size
        }
    }

    private fun TestScope.hub(
        sources: MutableList<ScriptedSource> = mutableListOf(),
        openOk: Boolean = true,
        failAfterFrames: Int? = null,
        ring: Int = 8,
        consumers: CoroutineDispatcher = StandardTestDispatcher(testScheduler),
    ) = MicHub(
        scope = backgroundScope,
        sourceFactory = { ScriptedSource(openOk, failAfterFrames).also { sources += it } },
        ringFrames = ring,
        ioDispatcher = StandardTestDispatcher(testScheduler),
        consumerDispatcher = consumers,
    )

    @Test
    fun theRingIsAtLeastFourTimesTheAndroidMinimumBuffer() {
        assertEquals(8, ringFramesFor(0))
        assertEquals(8, ringFramesFor(1_000))
        // A 3200-byte minimum buffer (100 ms) needs 4 * 3200 / 640 = 20 frames.
        assertTrue(ringFramesFor(3_200) >= 20)
        assertTrue(ringFramesFor(6_400) >= 40)
    }

    @Test
    fun oneLeaseGetsEveryFrameInOrderFromOneRecorder() = runTest {
        val sources = mutableListOf<ScriptedSource>()
        val hub = hub(sources)
        val got = Received()
        val lease = hub.lease(MicPurpose.WAKE, got.callback)
        advanceTimeBy(100)
        runCurrent()

        assertEquals(1, sources.size)
        assertEquals(1, hub.stats().opens)
        assertTrue(got.values.size >= 4)
        assertEquals((1..got.values.size).toList(), got.values) // no gaps, no duplicates
        assertTrue(got.sizes.all { it == MIC_FRAME_BYTES })
        lease.release()
    }

    @Test
    fun speechRecognitionBeatsTheWakeListenerWhileBothAreActive() = runTest {
        val hub = hub()
        val wake = Received()
        val stt = Received()
        val wakeLease = hub.lease(MicPurpose.WAKE, wake.callback)
        advanceTimeBy(60)
        runCurrent()

        val sttLease = hub.lease(MicPurpose.STT, stt.callback)
        val wakeAtSwitch = wake.values.size
        advanceTimeBy(100)
        runCurrent()
        assertEquals(wakeAtSwitch, wake.values.size) // wake got nothing once STT started
        assertTrue(stt.values.size >= 4)
        assertEquals(1, hub.stats().opens) // the switch did not open a second recorder

        sttLease.release()
        advanceTimeBy(60)
        runCurrent()
        assertTrue(wake.values.size > wakeAtSwitch) // wake gets the frames back
        wakeLease.release()
    }

    @Test
    fun bargeInBeatsTheWakeListenerButNotSpeechRecognition() = runTest {
        val hub = hub()
        val wake = Received()
        val barge = Received()
        val stt = Received()
        val wakeLease = hub.lease(MicPurpose.WAKE, wake.callback)
        val bargeLease = hub.lease(MicPurpose.BARGE_IN, barge.callback)
        advanceTimeBy(100)
        runCurrent()
        assertTrue(barge.values.size >= 3)
        assertTrue(wake.values.isEmpty())

        val sttLease = hub.lease(MicPurpose.STT, stt.callback)
        val bargeAtSwitch = barge.values.size
        advanceTimeBy(100)
        runCurrent()
        assertEquals(bargeAtSwitch, barge.values.size)
        assertTrue(stt.values.size >= 3)
        wakeLease.release()
        bargeLease.release()
        sttLease.release()
    }

    @Test
    fun theRecorderStaysOpenForTheGraceThenCloses() = runTest {
        val sources = mutableListOf<ScriptedSource>()
        val hub = hub(sources)
        val lease = hub.lease(MicPurpose.WAKE, {})
        advanceTimeBy(40)
        runCurrent()
        lease.release()

        advanceTimeBy(1_400)
        runCurrent()
        assertEquals(0, sources[0].closed) // still open inside the 1.5 s window
        assertTrue(hub.stats().recording)

        advanceTimeBy(200)
        runCurrent()
        assertEquals(1, sources[0].closed)
        assertFalse(hub.stats().recording)
    }

    @Test
    fun aLeaseTakenInsideTheGraceReusesTheRecorder() = runTest {
        val sources = mutableListOf<ScriptedSource>()
        val hub = hub(sources)
        val first = hub.lease(MicPurpose.WAKE, {})
        advanceTimeBy(40)
        runCurrent()
        first.release()

        advanceTimeBy(500)
        runCurrent()
        val second = hub.lease(MicPurpose.STT, {})
        advanceTimeBy(2_000)
        runCurrent()

        assertEquals(1, sources.size) // no reopen, so no gap in the microphone
        assertEquals(0, sources[0].closed)
        assertTrue(hub.stats().recording)
        second.release()
    }

    @Test
    fun aStuckCallbackNeverStopsTheMicReader() = runTest {
        // The consumer's dispatcher is never run: its callback is stuck.
        val stuck = HeldDispatcher()
        val hub = hub(consumers = stuck)
        val lease = hub.lease(MicPurpose.WAKE, {})
        advanceTimeBy(20 * 40) // 40 frames = 800 ms of microphone time
        runCurrent()

        val stats = hub.stats()
        assertTrue(stats.framesRead >= 40) // the reader kept draining the microphone
        assertTrue(stats.droppedNoBuffer + stats.droppedSlow > 0) // the overflow was counted, not hidden
        lease.release()
    }

    @Test
    fun whenTheCallbackWakesUpItStillGetsTheFramesThatWereKeptInOrder() = runTest {
        val stuck = HeldDispatcher()
        val hub = hub(ring = 8, consumers = stuck)
        val got = Received()
        val lease = hub.lease(MicPurpose.WAKE, got.callback)
        advanceTimeBy(20 * 40)
        runCurrent()

        // The first 8 frames fit the ring; the rest were dropped (counted), never mixed up.
        stuck.runAll()
        assertEquals((1..8).toList(), got.values)
        assertTrue(got.sizes.all { it == MIC_FRAME_BYTES })
        lease.release()
    }

    @Test
    fun aPhoneCallPausesTheMicAndResumeBringsItBack() = runTest {
        val sources = mutableListOf<ScriptedSource>()
        val hub = hub(sources)
        val got = Received()
        val lease = hub.lease(MicPurpose.STT, got.callback)
        advanceTimeBy(60)
        runCurrent()

        hub.pauseForCall()
        advanceTimeBy(50)
        runCurrent()
        val atPause = got.values.size
        advanceTimeBy(300)
        runCurrent()
        assertEquals(atPause, got.values.size) // nothing new while the call holds the mic
        assertEquals(1, sources[0].closed)
        assertFalse(hub.stats().recording)

        hub.resumeAfterCall()
        advanceTimeBy(100)
        runCurrent()
        assertEquals(2, sources.size) // a fresh recorder after the call
        assertTrue(got.values.size > atPause)
        lease.release()
    }

    @Test
    fun aMicThatCannotOpenSaysSoAndStaysOff() = runTest {
        val hub = hub(openOk = false)
        var error: String? = null
        val lease = hub.lease(MicPurpose.WAKE, {}, { error = it })
        advanceTimeBy(50)
        runCurrent()

        assertEquals("microphone unavailable", error)
        assertFalse(hub.stats().recording)
        assertEquals(0, hub.stats().opens)
        lease.release()
    }

    @Test
    fun aMicThatFailsMidStreamTellsEveryHolderOnce() = runTest {
        val sources = mutableListOf<ScriptedSource>()
        val hub = hub(sources, failAfterFrames = 3)
        var errors = 0
        val lease = hub.lease(MicPurpose.WAKE, {}, { errors++ })
        advanceTimeBy(300)
        runCurrent()

        assertEquals(1, errors)
        assertEquals(1, sources[0].closed) // the broken recorder is released, not left open
        assertFalse(hub.stats().recording)
        lease.release()
    }

    @Test
    fun releasingTwiceIsSafeAndTheOtherHolderKeepsHearing() = runTest {
        val hub = hub()
        val a = Received()
        val b = Received()
        val first = hub.lease(MicPurpose.WAKE, a.callback)
        val second = hub.lease(MicPurpose.WAKE, b.callback)
        advanceTimeBy(60)
        runCurrent()

        first.release()
        first.release()
        val bBefore = b.values.size
        advanceTimeBy(100)
        runCurrent()
        assertTrue(b.values.size > bBefore)
        assertEquals(1, hub.stats().leases)
        second.release()
    }

    @Test
    fun aCallbackThatThrowsDoesNotStopTheOthersOrTheMic() = runTest {
        val hub = hub()
        val healthy = Received()
        val broken = hub.lease(MicPurpose.WAKE, { throw IllegalStateException("bug in a consumer") })
        val good = hub.lease(MicPurpose.WAKE, healthy.callback)
        advanceTimeBy(100)
        runCurrent()

        assertTrue(healthy.values.size >= 4)
        assertTrue(hub.stats().consumerErrors >= 1)
        broken.release()
        good.release()
    }
}
