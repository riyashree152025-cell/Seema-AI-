package com.raju.aiassistant.domain.voice

import org.junit.Assert.assertEquals
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

    @Test
    fun aRoomThatGetsLouderAfterTheStartDoesNotHoldTheTurnFor15Seconds() {
        // Phone logs: 750 frames (15 s) and then "no words were recognised". The user spoke, then a
        // fan / TV kept the level above the bar measured in the first 300 ms.
        val d = EndpointDetector(frameMs = 20)
        d.feed(60.0, 15) // quiet calibration
        d.feed(1_500.0, 50) // 1 s of speech
        var frames = 0
        var s = d.state
        while (s == EndpointDetector.State.SPEAKING && frames < 700) { s = d.onFrame(400.0); frames++ } // steady noise
        assertEquals(EndpointDetector.State.DONE, s)
        assert(frames * 20 < 5_000) { "ended after ${frames * 20} ms of steady noise" }
    }

    @Test
    fun longSpeechWithGapsBetweenWordsIsNotCut() {
        val d = EndpointDetector(frameMs = 20)
        d.feed(60.0, 15)
        repeat(30) { // 9 s: 240 ms words, 60 ms gaps
            d.feed(900.0, 12)
            assertEquals(EndpointDetector.State.SPEAKING, d.feed(80.0, 3))
        }
    }

    @Test
    fun noVoiceForFourAndAHalfSecondsIsNoSpeech() {
        val d = EndpointDetector(frameMs = 20)
        d.feed(60.0, 15)
        assertEquals(EndpointDetector.State.LISTENING, d.feed(60.0, 200)) // 4.3 s
        assertEquals(EndpointDetector.State.NO_SPEECH, d.feed(60.0, 15))
    }
}
