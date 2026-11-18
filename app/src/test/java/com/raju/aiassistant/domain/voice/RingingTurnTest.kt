package com.raju.aiassistant.domain.voice

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** A ringing call: the ringtone is still audible under "uthao", and the answer is one or two words. */
class RingingTurnTest {
    @After
    fun reset() { TurnPacing.quick = false }

    private fun run(detector: EndpointDetector, ringRms: Double, voiceRms: Double): EndpointDetector.State {
        repeat(25) { detector.onFrame(ringRms) } // 0.5 s of ringtone only (also calibrates the room level)
        repeat(15) { detector.onFrame(voiceRms) } // "uthao"
        var last = detector.state
        repeat(45) { last = detector.onFrame(ringRms) } // then the ringtone again
        return last
    }

    @Test
    fun aVoiceOverTheDuckedRingtoneIsHeardAndEndsTheTurn() {
        TurnPacing.quick = true
        assertEquals(EndpointDetector.State.DONE, run(TurnPacing.detector(20), ringRms = 400.0, voiceRms = 900.0))
    }

    @Test
    fun aNormalTurnWouldHaveMissedItBecauseTheRingtoneRaisedTheBar() {
        TurnPacing.quick = false
        // The old behaviour (and the normal pacing): 400 x 2.2 = 880 → a 700 voice is not above the ringtone.
        assertEquals(EndpointDetector.State.NO_SPEECH, run(TurnPacing.detector(20), ringRms = 400.0, voiceRms = 700.0).let {
            if (it == EndpointDetector.State.LISTENING) EndpointDetector.State.NO_SPEECH else it
        })
    }
}
