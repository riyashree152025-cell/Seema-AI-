package com.raju.aiassistant.data.voice

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Switching the assistant OFF gives every microphone lease back; nothing is read until it is ON again. */
@OptIn(ExperimentalCoroutinesApi::class)
class MicHubMuteTest {
    private class TimedSource : FrameSource {
        var closed = 0
        override fun open(): Boolean = true
        override suspend fun read(buf: ByteArray, offset: Int, length: Int): Int {
            kotlinx.coroutines.delay(20)
            for (i in offset until offset + length) buf[i] = 1
            return length
        }
        override fun close() { closed++ }
    }

    @Test
    fun releaseAllStopsTheRecorderAndNoLeaseGetsAnotherFrame() = runTest {
        val sources = mutableListOf<TimedSource>()
        val hub = MicHub(
            scope = backgroundScope,
            sourceFactory = { TimedSource().also { sources += it } },
            ringFrames = 8,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            consumerDispatcher = StandardTestDispatcher(testScheduler),
        )
        var frames = 0
        val wake = hub.lease(MicPurpose.WAKE, { frames++ })
        advanceTimeBy(100); runCurrent()
        assertTrue(frames > 0)

        hub.releaseAll()
        runCurrent()
        val atMute = frames
        advanceTimeBy(1_000); runCurrent()
        assertEquals(atMute, frames)                  // zero audio after OFF
        assertEquals(1, sources[0].closed)            // the AudioRecord was stopped and released
        assertFalse(hub.stats().recording)
        wake.release()                                // a late release from the old holder is harmless
        advanceTimeBy(100); runCurrent()
        assertEquals(1, sources.size)
    }

    @Test
    fun whileOffNewLeasesWaitAndUnmuteRecordsAgain() = runTest {
        val sources = mutableListOf<TimedSource>()
        val hub = MicHub(
            scope = backgroundScope,
            sourceFactory = { TimedSource().also { sources += it } },
            ringFrames = 8,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            consumerDispatcher = StandardTestDispatcher(testScheduler),
        )
        hub.releaseAll()
        var frames = 0
        val stt = hub.lease(MicPurpose.STT, { frames++ })
        advanceTimeBy(500); runCurrent()
        assertEquals(0, sources.size)                 // OFF: no recorder even for a new holder
        assertEquals(0, frames)

        hub.unmute()
        advanceTimeBy(100); runCurrent()
        assertEquals(1, sources.size)
        assertTrue(frames > 0)
        stt.release()
    }
}
