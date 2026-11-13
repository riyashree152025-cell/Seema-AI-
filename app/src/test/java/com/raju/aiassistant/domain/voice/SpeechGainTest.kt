package com.raju.aiassistant.domain.voice

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class SpeechGainTest {
    private fun tone(amplitude: Int, samples: Int = 1_600) = ByteArray(samples * 2).also { b ->
        for (i in 0 until samples) {
            val v = (amplitude * sin(2 * PI * 300 * i / 16_000.0)).toInt()
            b[2 * i] = (v and 0xFF).toByte(); b[2 * i + 1] = (v shr 8).toByte()
        }
    }

    private fun peak(pcm: ByteArray) = (0 until pcm.size / 2).maxOf { i -> abs(((pcm[2 * i + 1].toInt() shl 8) or (pcm[2 * i].toInt() and 0xFF)).toShort().toInt()) }

    @Test
    fun quietSpeechIsRaisedLoudSpeechAndSilenceAreLeftAlone() {
        val quiet = tone(3_000)
        val raised = louderForStt(quiet)
        assertTrue(peak(raised) in 22_000..STT_TARGET_PEAK + 50)
        val loud = tone(26_000)
        assertSame(loud, louderForStt(loud))
        val silent = ByteArray(3_200)
        assertArrayEquals(silent, louderForStt(silent))
    }

    @Test
    fun aNearSilentRoomIsNotTurnedIntoLoudHiss() {
        val hiss = tone(200)
        assertTrue(abs(peak(louderForStt(hiss)) - 200 * 8) <= 20) // at most 8x
    }

    @Test
    fun oneClickDoesNotDecideTheGain() {
        val speech = tone(2_000, samples = 16_000)
        speech[100] = 0xFF.toByte(); speech[101] = 0x7F // one full-scale click
        assertTrue(peak(louderForStt(speech)) == 32_767) // the click clips, the speech is still raised
        val raised = louderForStt(speech)
        val middle = (2_000 until 2_400).maxOf { i -> abs(((raised[2 * i + 1].toInt() shl 8) or (raised[2 * i].toInt() and 0xFF)).toShort().toInt()) }
        assertTrue(middle > 15_000)
    }

    @Test
    fun liveFramesAreRaisedWithoutClippingAndTheLevelIsRemembered() {
        val g = StreamGain(startPeak = STT_TARGET_PEAK / 4f)
        // Quiet speech (as the phone's microphone gives it): raised to the most allowed within two seconds.
        var last = ByteArray(0)
        repeat(100) { last = g.apply(tone(1_500, samples = 320)) }
        assertTrue("peak ${peak(last)}", peak(last) in 10_000..STT_TARGET_PEAK + 50) // 1,500 x 8 at most
        assertTrue(g.current > 6f)
        // A sudden loud sound is never clipped: the gain drops in the same frame.
        val loud = g.apply(tone(20_000, samples = 320))
        assertTrue("peak ${peak(loud)}", peak(loud) <= STT_TARGET_PEAK + 50)
        // A pause does not pump the gain straight back up.
        val before = g.current
        repeat(10) { g.apply(ByteArray(640)) }
        assertTrue(g.current < before * 1.5f)
        // The next turn starts from the level this one learned, not from scratch.
        val next = StreamGain()
        assertTrue(abs(next.current - g.current) < 1.5f)
        // Silence stays silence.
        assertArrayEquals(ByteArray(640), StreamGain().apply(ByteArray(640)))
    }
}
