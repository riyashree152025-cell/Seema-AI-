package com.raju.aiassistant.data.voice

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The offline wake word on the shared microphone: batching (pure) and the listener around it (on a hub). */
@OptIn(ExperimentalCoroutinesApi::class)
class KwsFrameDetectorTest {

    /** Records what the spotter is given; says "heard" on the [hitOn]-th batch (0 = never). */
    private class FakeStream(private val hitOn: Int = 0) : KeywordStream {
        val batchSizes = mutableListOf<Int>()
        val firstSamples = mutableListOf<Float>()
        var released = 0

        override fun accept(samples: FloatArray): Boolean {
            batchSizes += samples.size
            firstSamples += samples[0]
            return hitOn > 0 && batchSizes.size == hitOn
        }

        override fun release() {
            released++
        }
    }

    private fun frame(amplitude: Int): ByteArray = ByteArray(MIC_FRAME_BYTES) { i ->
        if (i % 2 == 0) (amplitude and 0xFF).toByte() else (amplitude shr 8).toByte()
    }

    // ---------------------------------------------------------------- batching (pure)

    @Test
    fun fiveFramesMakeOneHundredMillisecondBatch() {
        val stream = FakeStream()
        var opened = 0
        val d = KwsFrameDetector(openStream = { opened++; stream })

        repeat(4) { assertNull(d.feed(frame(0))) }
        assertEquals(0, stream.batchSizes.size) // no decode on every 20 ms frame
        assertEquals(0, opened) // the model is not touched before the first full batch

        assertNull(d.feed(frame(0)))
        assertEquals(listOf(KWS_BATCH_SAMPLES), stream.batchSizes)
        assertEquals(1, opened)
    }

    @Test
    fun theSpotterIsCalledTenTimesASecondNotFifty() {
        val stream = FakeStream()
        val d = KwsFrameDetector(openStream = { stream })
        repeat(50) { d.feed(frame(0)) } // one second of audio
        assertEquals(10, stream.batchSizes.size)
        assertEquals(10, d.batches)
        assertTrue(stream.batchSizes.all { it == KWS_BATCH_SAMPLES })
    }

    @Test
    fun samplesAreScaledToMinusOneToOne() {
        val stream = FakeStream()
        val d = KwsFrameDetector(openStream = { stream })
        repeat(5) { d.feed(frame(16_384)) }
        assertEquals(0.5f, stream.firstSamples.single(), 0.0001f)
    }

    @Test
    fun theWakeWordEndsTheListenAndCloseReleasesTheStreamOnce() {
        val stream = FakeStream(hitOn = 3)
        val d = KwsFrameDetector(openStream = { stream })
        val results = List(15) { d.feed(frame(0)) }
        assertSame(KwsFrameDetector.HEARD, results[14])
        assertEquals(1, results.count { it != null })

        d.close()
        d.close()
        assertEquals(1, stream.released)
    }

    @Test
    fun closingBeforeAnyBatchTouchesNothing() {
        var opened = 0
        val d = KwsFrameDetector(openStream = { opened++; FakeStream() })
        d.feed(frame(0))
        d.close()
        assertEquals(0, opened)
    }

    @Test
    fun aLoudRoomIsLoggedAtMostEveryThreeSeconds() {
        val logs = mutableListOf<String>()
        var now = 10_000L
        val d = KwsFrameDetector(openStream = { FakeStream() }, clock = { now }, log = { logs += it })
        repeat(50) { now += 20; d.feed(frame(3_000)) } // 1 s of loud audio
        assertEquals(1, logs.count { it.startsWith("kws hears sound") })
        repeat(150) { now += 20; d.feed(frame(3_000)) } // 3 s more
        assertEquals(2, logs.count { it.startsWith("kws hears sound") })
    }

    // ---------------------------------------------------------------- on the shared microphone

    private class SilenceSource(private val frames: Int) : FrameSource {
        private var index = 0
        private var pos = 0
        override fun open(): Boolean = true
        override suspend fun read(buf: ByteArray, offset: Int, length: Int): Int {
            if (index >= frames) return -1
            delay(10)
            val n = minOf(length, MIC_FRAME_BYTES / 2)
            for (i in offset until offset + n) buf[i] = 0
            pos += n
            if (pos == MIC_FRAME_BYTES) {
                pos = 0
                index++
            }
            return n
        }
        override fun close() {}
    }

    @Test
    fun onTheSharedMicTheWakeWordWakesHerAndFreesTheMicAndTheStream() = runTest {
        val hub = MicHub(
            scope = backgroundScope,
            sourceFactory = { SilenceSource(500) },
            ringFrames = 8,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            consumerDispatcher = StandardTestDispatcher(testScheduler),
        )
        val stream = FakeStream(hitOn = 4) // spotted in the 4th 100 ms batch
        val listener = HubWakeListener(hub) { KwsFrameDetector(openStream = { stream }) }
        var woke: ByteArray? = null
        var errors = 0

        listener.start({ woke = it }, { errors++ })
        advanceTimeBy(1_000)
        runCurrent()

        assertNotNull(woke)
        assertEquals(0, woke!!.size) // no audio passed on, as the original KWS listener did
        assertEquals(0, errors)
        assertEquals(4, stream.batchSizes.size) // nothing decoded after the phrase
        assertEquals(1, stream.released)
        assertEquals(0, hub.stats().leases)
        assertFalse(hub.stats().recording)
    }

    @Test
    fun stoppingTheSharedMicListenerReleasesTheNativeStream() = runTest {
        val hub = MicHub(
            scope = backgroundScope,
            sourceFactory = { SilenceSource(500) },
            ringFrames = 8,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            consumerDispatcher = StandardTestDispatcher(testScheduler),
        )
        val stream = FakeStream()
        val listener = HubWakeListener(hub) { KwsFrameDetector(openStream = { stream }) }
        listener.start({}, {})
        advanceTimeBy(300)
        runCurrent()
        assertTrue(stream.batchSizes.isNotEmpty())

        listener.stop()
        advanceTimeBy(300)
        runCurrent()
        assertEquals(1, stream.released)
        val decoded = stream.batchSizes.size
        advanceTimeBy(500)
        runCurrent()
        assertEquals(decoded, stream.batchSizes.size) // nothing reaches a released stream
        assertEquals(0, hub.stats().leases)
    }
}
