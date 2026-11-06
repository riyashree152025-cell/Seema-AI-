package com.raju.aiassistant.domain.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SpeechGateTest {
    /** Feeds frames until something other than WAITING/RECORDING happens; returns it and the frame count. */
    private fun WakeEndpointer.run(levels: Sequence<Double>): Pair<WakeEndpointer.Event, Int> {
        var n = 0
        var started = false
        for (l in levels) {
            n++
            when (val e = onFrame(l)) {
                WakeEndpointer.Event.STARTED -> started = true
                WakeEndpointer.Event.DONE, WakeEndpointer.Event.DISCARDED -> return e to n
                else -> Unit
            }
        }
        return (if (started) WakeEndpointer.Event.RECORDING else WakeEndpointer.Event.WAITING) to n
    }

    /** Syllables: loud and soft frames taking turns, like a voice. */
    private fun voice(frames: Int, loud: Double) = (0 until frames).asSequence().map { if ((it / 5) % 2 == 0) loud else loud * 0.3 }

    /** An AC: around [level], flickering a little. */
    private fun ac(frames: Int, level: Double) = (0 until frames).asSequence().map { level * (0.85 + 0.3 * ((it * 7) % 10) / 10.0) }

    @Test
    fun aNameSaidOverAcNoiseEndsSoonAfterTheVoiceStops() {
        val g = WakeEndpointer()
        assertEquals(WakeEndpointer.Event.WAITING, g.run(ac(250, 250.0)).first) // 5 s of AC
        val (event, frames) = g.run(voice(50, 1_500.0) + ac(400, 250.0))
        assertEquals(WakeEndpointer.Event.DONE, event)
        assertTrue("ended after ${frames * 20} ms", frames * 20 < 2_000) // not the 8 s limit
    }

    @Test
    fun steadyAcNoiseIsNotSentAsSpeech() {
        val g = WakeEndpointer()
        // The AC switches on: louder than the room was, but flat.
        val (event, _) = g.run(ac(25, 80.0) + (0 until 500).asSequence().map { 600.0 + (it % 3) * 10 })
        assertEquals(WakeEndpointer.Event.DISCARDED, event)
        // Learnt: the same AC does not start a recording again.
        assertEquals(WakeEndpointer.Event.WAITING, g.run((0 until 200).asSequence().map { 600.0 + (it % 3) * 10 }).first)
    }

    @Test
    fun aQuietRoomAndANormalVoice() {
        val g = WakeEndpointer()
        g.run(ac(50, 60.0))
        assertEquals(WakeEndpointer.Event.DONE, g.run(voice(40, 300.0) + ac(60, 60.0)).first)
    }

    @Test
    fun aKnockIsNotSpeech() {
        val g = WakeEndpointer()
        g.run(ac(50, 60.0))
        assertEquals(WakeEndpointer.Event.DISCARDED, g.run(sequenceOf(3_000.0, 3_000.0, 3_000.0, 3_000.0, 3_000.0) + ac(60, 60.0)).first)
    }

    @Test
    fun quietSpeechIsMadeLouderAndLoudSpeechIsLeftAlone() {
        fun pcm(vararg s: Int) = ByteArray(s.size * 2).also { b -> s.forEachIndexed { i, v -> b[2 * i] = (v and 0xFF).toByte(); b[2 * i + 1] = (v shr 8).toByte() } }
        fun samples(b: ByteArray) = (0 until b.size / 2).map { ((b[2 * it + 1].toInt() shl 8) or (b[2 * it].toInt() and 0xFF)).toShort().toInt() }
        val quiet = pcm(*IntArray(2000) { if (it % 2 == 0) 400 else -400 })
        val louder = samples(normalizePcm16(quiet))
        assertEquals(3200, abs(louder[0])) // at most 8 x
        val loud = pcm(*IntArray(2000) { if (it % 2 == 0) 25_000 else -25_000 })
        assertTrue(normalizePcm16(loud).contentEquals(loud))
    }

    @Test
    fun wrongScriptsAreRecognised() {
        assertTrue(isUnexpectedScript("హిందీ ఇంగ్లీష్ టైపింగ్ దాదా పేమెంట్"))
        assertTrue(isUnexpectedScript("זו קשני ברוך לא ברור זה"))
        assertFalse(isUnexpectedScript("हिंदी वडी English में लिखना है"))
        assertFalse(isUnexpectedScript("Rizwan Travels ko message bhejo"))
        assertTrue(isUnexpectedScript("আমি ভালো আছি"))
        assertFalse(isUnexpectedScript("আমি ভালো আছি", bengali = true))
        assertFalse(isUnexpectedScript(""))
    }
}
