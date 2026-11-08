package com.raju.aiassistant.data.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Barge-in's exclusive window: the hub closes its recorder (and waits until it has really stopped)
 * before barge-in opens its own call-style recorder, and records again only after it is given back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MicHubExclusiveTest {

    /** Virtual-time source: one frame every 20 ms. */
    private class TimedSource : FrameSource {
        var closed = 0
        override fun open(): Boolean = true
        override suspend fun read(buf: ByteArray, offset: Int, length: Int): Int {
            delay(20)
            for (i in offset until offset + length) buf[i] = 1
            return length
        }
        override fun close() {
            closed++
        }
    }

    private fun TestScope.hub(sources: MutableList<TimedSource>) = MicHub(
        scope = backgroundScope,
        sourceFactory = { TimedSource().also { sources += it } },
        ringFrames = 8,
        ioDispatcher = StandardTestDispatcher(testScheduler),
        consumerDispatcher = StandardTestDispatcher(testScheduler),
    )

    @Test
    fun theHubStopsRecordingForBargeInAndResumesAfter() = runTest {
        val sources = mutableListOf<TimedSource>()
        val hub = hub(sources)
        var frames = 0
        val wake = hub.lease(MicPurpose.WAKE, { frames++ })
        advanceTimeBy(100)
        runCurrent()
        assertTrue(frames > 0)

        var hold: MicExclusive? = null
        launch { hold = hub.takeExclusive() }
        advanceTimeBy(50)
        runCurrent()
        assertTrue(hold != null)
        assertEquals(1, sources[0].closed) // the shared recorder is closed before barge-in records
        assertFalse(hub.stats().recording)
        assertTrue(hub.stats().exclusive)

        val during = frames
        val stt = hub.lease(MicPurpose.STT, {}) // a lease taken now must not open a second recorder
        advanceTimeBy(500)
        runCurrent()
        assertEquals(during, frames)
        assertEquals(1, sources.size)

        hold!!.release()
        advanceTimeBy(100)
        runCurrent()
        assertEquals(2, sources.size) // the hub records again for the leases that waited
        assertTrue(hub.stats().recording)
        assertFalse(hub.stats().exclusive)
        stt.release()
        wake.release()
    }

    @Test
    fun withoutLeasesGivingTheMicBackOpensNothing() = runTest {
        val sources = mutableListOf<TimedSource>()
        val hub = hub(sources)
        var hold: MicExclusive? = null
        launch { hold = hub.takeExclusive() }
        runCurrent()
        hold!!.release()
        hold!!.release() // twice is harmless
        advanceTimeBy(200)
        runCurrent()
        assertEquals(0, sources.size)
        assertFalse(hub.stats().exclusive)
    }

    @Test
    fun twoHoldersMustBothGiveItBack() = runTest {
        val sources = mutableListOf<TimedSource>()
        val hub = hub(sources)
        val lease = hub.lease(MicPurpose.WAKE, {})
        advanceTimeBy(60)
        runCurrent()
        var a: MicExclusive? = null
        var b: MicExclusive? = null
        launch { a = hub.takeExclusive() }
        launch { b = hub.takeExclusive() }
        advanceTimeBy(60)
        runCurrent()
        a!!.release()
        advanceTimeBy(100)
        runCurrent()
        assertEquals(1, sources.size) // still held by b
        b!!.release()
        advanceTimeBy(100)
        runCurrent()
        assertEquals(2, sources.size)
        lease.release()
    }

    @Test
    fun aCallStillHoldsTheMicAfterBargeInGivesItBack() = runTest {
        val sources = mutableListOf<TimedSource>()
        val hub = hub(sources)
        val lease = hub.lease(MicPurpose.WAKE, {})
        advanceTimeBy(60)
        runCurrent()
        var hold: MicExclusive? = null
        launch { hold = hub.takeExclusive() }
        runCurrent()
        hub.pauseForCall()
        hold!!.release()
        advanceTimeBy(200)
        runCurrent()
        assertEquals(1, sources.size) // the call keeps the hub closed
        hub.resumeAfterCall()
        advanceTimeBy(100)
        runCurrent()
        assertEquals(2, sources.size)
        lease.release()
    }

    // ---------------------------------------------------------------- real threads, blocking reads

    /** How many recorders are open at once, and the most ever seen. */
    private class OpenCounter {
        private val now = AtomicInteger()
        val max = AtomicInteger()
        fun opened() = max.accumulateAndGet(now.incrementAndGet()) { a, b -> maxOf(a, b) }
        fun closed() = now.decrementAndGet()
    }

    /** Like AudioRecord.read: blocks, never suspends. */
    private class BlockingSource(private val readMs: Long, private val counter: OpenCounter) : FrameSource {
        @Volatile var closed = false
        val reads = AtomicInteger()
        override fun open(): Boolean {
            counter.opened()
            return true
        }
        override suspend fun read(buf: ByteArray, offset: Int, length: Int): Int {
            Thread.sleep(readMs)
            reads.incrementAndGet()
            return length
        }
        override fun close() {
            if (!closed) counter.closed()
            closed = true
        }
    }

    private suspend fun waitUntil(what: String, timeoutMs: Long = 3_000, condition: () -> Boolean) {
        val until = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > until) fail("timed out waiting: $what")
            delay(10)
        }
    }

    @Test
    fun takeExclusiveReturnsOnlyAfterTheBlockedRecorderHasReallyClosed() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val sources = CopyOnWriteArrayList<BlockingSource>()
        val counter = OpenCounter()
        val hub = MicHub(scope, { BlockingSource(readMs = 15, counter).also { sources += it } }, ringFrames = 8, ioDispatcher = Dispatchers.IO, consumerDispatcher = Dispatchers.Default)
        try {
            val lease = hub.lease(MicPurpose.WAKE, {})
            waitUntil("the shared recorder reads") { sources.isNotEmpty() && sources[0].reads.get() > 2 }

            val hold = hub.takeExclusive()
            assertTrue(sources[0].closed) // never two recorders: closed before barge-in may open its own
            val reads = sources[0].reads.get()
            delay(100)
            assertEquals(reads, sources[0].reads.get())

            hold.release()
            waitUntil("the hub records again") { sources.size == 2 && sources[1].reads.get() > 2 }
            lease.release()
            hub.closeIfIdle()
            waitUntil("the second recorder stops") { sources[1].closed }
            assertEquals(1, counter.max.get()) // never two recorders open at once
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun cancellingWhileWaitingGivesTheMicBack() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val sources = CopyOnWriteArrayList<BlockingSource>()
        // A slow read (300 ms) keeps takeExclusive waiting long enough to cancel it.
        val counter = OpenCounter()
        val hub = MicHub(scope, { BlockingSource(readMs = 300, counter).also { sources += it } }, ringFrames = 8, ioDispatcher = Dispatchers.IO, consumerDispatcher = Dispatchers.Default)
        try {
            val lease = hub.lease(MicPurpose.WAKE, {})
            waitUntil("the shared recorder reads") { sources.isNotEmpty() && sources[0].reads.get() >= 1 }

            val waiter = scope.launch { hub.takeExclusive() }
            delay(50)
            waiter.cancel()
            waiter.join()
            assertFalse(hub.stats().exclusive) // not stuck: the hold was given back
            waitUntil("the hub records again") { sources.size == 2 && sources[1].reads.get() >= 1 }
            lease.release()
            hub.closeIfIdle()
            waitUntil("the recorder stops") { sources.all { it.closed } }
            // The cancelled hand-over reopened the hub only after the slow read had closed the old recorder.
            assertEquals(1, counter.max.get())
        } finally {
            scope.cancel()
        }
    }
}
