package com.raju.aiassistant.data.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * The phone's case, on real threads: AudioRecord.read blocks and never suspends, so a cancelled
 * reader only stops if the hub checks for it. Without that, each close left a reader running and
 * the next turn got two (then three, …) streams mixed together.
 */
class MicHubBlockingReadTest {

    /** Like AudioRecord.read: blocks the thread for a frame's worth of time, never suspends. */
    private class BlockingSource : FrameSource {
        @Volatile var closed = false
        val reads = AtomicInteger()

        override fun open(): Boolean = true

        override suspend fun read(buf: ByteArray, offset: Int, length: Int): Int {
            Thread.sleep(5)
            reads.incrementAndGet()
            for (i in offset until offset + length) buf[i] = 1
            return length
        }

        override fun close() {
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
    fun everyClosedRecorderReallyStopsSoTurnsNeverStack() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val sources = CopyOnWriteArrayList<BlockingSource>()
        val hub = MicHub(
            scope = scope,
            sourceFactory = { BlockingSource().also { sources += it } },
            ringFrames = 8,
            ioDispatcher = Dispatchers.IO,
            consumerDispatcher = Dispatchers.Default,
        )
        try {
            // Five turns, like the phone: lease, a little audio, release and close at once.
            repeat(5) { turn ->
                val lease = hub.lease(MicPurpose.STT, {})
                waitUntil("turn $turn reads audio") { sources.size == turn + 1 && sources[turn].reads.get() > 3 }
                lease.release()
                hub.closeIfIdle()
                waitUntil("turn $turn recorder stops") { sources[turn].closed }
            }
            assertEquals(5, sources.size)
            assertTrue(sources.all { it.closed })

            // Nothing keeps reading after the last close: the read counts stay still.
            val before = sources.sumOf { it.reads.get() }
            delay(200)
            assertEquals(before, sources.sumOf { it.reads.get() })

            // A fresh turn gets one stream only: one recorder reading, all older ones closed.
            val frames = AtomicInteger()
            val last = hub.lease(MicPurpose.STT, { frames.incrementAndGet() })
            waitUntil("the sixth recorder reads") { sources.size == 6 && sources[5].reads.get() > 3 }
            assertTrue(sources.dropLast(1).all { it.closed })
            last.release()
            hub.closeIfIdle()
            waitUntil("the sixth recorder stops") { sources[5].closed }
        } finally {
            scope.cancel()
        }
    }
}
