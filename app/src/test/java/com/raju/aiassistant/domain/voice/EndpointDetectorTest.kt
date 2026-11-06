package com.raju.aiassistant.domain.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointDetectorTest {
    private fun EndpointDetector.feed(rms: Double, frames: Int): EndpointDetector.State {
        var s = state
        repeat(frames) { s = onFrame(rms) }
        return s
    }

    @Test
    fun speechThenPauseEndsTheTurn() {
        val d = EndpointDetector(frameMs = 20, silenceMs = 1_000)
        d.feed(100.0, 15) // calibration: quiet room
        assertEquals(EndpointDetector.State.SPEAKING, d.feed(3_000.0, 10))
        assertEquals(EndpointDetector.State.SPEAKING, d.feed(100.0, 40)) // 800 ms pause: still talking
        assertEquals(EndpointDetector.State.DONE, d.feed(100.0, 10))
    }

    @Test
    fun aNormalVoiceOnAPhoneWithAQuietMicrophoneIsHeard() {
        // Levels seen on a Samsung phone: room ~60, normal speech 200-400.
        val d = EndpointDetector(frameMs = 20)
        d.feed(60.0, 15)
        assertEquals(EndpointDetector.State.SPEAKING, d.feed(250.0, 5))
    }

    @Test
    fun talkingStraightAwayDoesNotRaiseTheBarAboveTheVoice() {
        val d = EndpointDetector(frameMs = 20)
        d.feed(60.0, 3) // a moment of quiet
        d.feed(300.0, 12) // then speech already during calibration
        assertEquals(EndpointDetector.State.SPEAKING, d.feed(300.0, 5))
    }

    @Test
    fun aQuietRoomIsStillNotSpeech() {
        val d = EndpointDetector(frameMs = 20, noSpeechTimeoutMs = 2_000)
        d.feed(60.0, 15)
        assertEquals(EndpointDetector.State.NO_SPEECH, d.feed(120.0, 120)) // fan / hum below the bar
    }

    @Test
    fun aShortNoiseIsNotATurnAndRealSpeechAfterItIs() {
        val d = EndpointDetector(frameMs = 20, silenceMs = 1_000)
        d.feed(60.0, 15)
        d.feed(800.0, 5) // 100 ms: a cough or a door
        assertEquals(EndpointDetector.State.LISTENING, d.feed(60.0, 60)) // not sent as a turn
        assertEquals(EndpointDetector.State.SPEAKING, d.feed(400.0, 20)) // then the user talks
        assertEquals(EndpointDetector.State.DONE, d.feed(60.0, 60))
    }

    @Test
    fun silenceOnlyMeansNoSpeech() {
        val d = EndpointDetector(frameMs = 20, noSpeechTimeoutMs = 2_000)
        assertEquals(EndpointDetector.State.NO_SPEECH, d.feed(100.0, 120))
    }

    @Test
    fun aSingleClickIsNotSpeechAndLongTalkIsCut() {
        val d = EndpointDetector(frameMs = 20, maxMs = 3_000)
        d.feed(100.0, 15)
        assertEquals(EndpointDetector.State.LISTENING, d.feed(5_000.0, 2))
        assertEquals(EndpointDetector.State.DONE, d.feed(5_000.0, 200))
    }
}

class EndpointDetectorNoiseTest {
    @Test
    fun anAcFlickeringAboveTheCalibratedLevelDoesNotKeepTheTurnOpen() {
        val d = EndpointDetector(frameMs = 20)
        repeat(15) { d.onFrame(120.0) } // calibration: quietest moment of the AC
        var s = d.state
        repeat(60) { s = d.onFrame(if ((it / 4) % 2 == 0) 3_000.0 else 900.0) } // the user talks
        assertEquals(EndpointDetector.State.SPEAKING, s)
        // The AC again: 200-450, partly above 2.5 x 120 = 300.
        var frames = 0
        while (s == EndpointDetector.State.SPEAKING && frames < 500) { s = d.onFrame(if (frames % 3 == 0) 450.0 else 200.0); frames++ }
        assertEquals(EndpointDetector.State.DONE, s)
        assertTrue("took ${frames * 20} ms", frames <= 60) // about 1 s after the voice stopped, not the 15 s limit
    }
}
