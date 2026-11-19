package com.raju.aiassistant.domain.voice

import com.raju.aiassistant.core.audio.PcmAudio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class VoiceMoodTest {

    @Test
    fun theLeadingTagChoosesTheMoodAndIsNeverSpoken() {
        val p = VoiceMoodParser.parse("[MOOD:WARM] जान, हो गया…")
        assertEquals(VoiceMood.WARM, p.mood)
        assertEquals("जान, हो गया…", p.text)
        assertEquals(VoiceMood.CONTROLLED_UPSET, VoiceMoodParser.parse("[mood: controlled upset] बस करो").mood)
        assertEquals(VoiceMood.WHISPER, VoiceMoodParser.parse("  [MOOD:WHISPER] सो जाओ").mood)
    }

    @Test
    fun aTagInTheMiddleIsRemovedButDoesNotChooseAndUnknownMoodsAreDropped() {
        val p = VoiceMoodParser.parse("हाँ [MOOD:WHISPER] ठीक है")
        assertNull(p.mood)
        assertFalse(p.text.contains("MOOD"))
        val q = VoiceMoodParser.parse("[MOOD:EXCITED] वाह!")
        assertNull(q.mood)
        assertEquals("वाह!", q.text)
        assertEquals("बिना टैग", VoiceMoodParser.parse("बिना टैग").text)
    }

    @Test
    fun theThreeProfilesAreCalmAndNeverShrill() {
        assertEquals(1.03f, VoiceMood.WARM.pitch, 0f)
        assertEquals(0.93f, VoiceMood.WARM.rate, 0f)
        assertEquals(0.90f, VoiceMood.CONTROLLED_UPSET.pitch, 0f)
        assertEquals(0.88f, VoiceMood.CONTROLLED_UPSET.rate, 0f)
        assertEquals(0.96f, VoiceMood.WHISPER.pitch, 0f)
        assertEquals(0.84f, VoiceMood.WHISPER.rate, 0f)
        assertEquals(0.65f, VoiceMood.WHISPER.gain, 0f)
        // Anger is lower, never higher, than her normal voice: no high-pitched shouting that cracks.
        assertTrue(VoiceMood.CONTROLLED_UPSET.pitch < VoiceMood.WARM.pitch)
    }

    @Test
    fun theDefaultFollowsTheHour() {
        assertEquals(VoiceMood.WHISPER, VoiceMood.defaultFor(23))
        assertEquals(VoiceMood.WHISPER, VoiceMood.defaultFor(2))
        assertEquals(VoiceMood.WARM, VoiceMood.defaultFor(15)) // one warm voice by day, no grounded "Tapori" tone
        assertEquals(VoiceMood.WARM, VoiceMood.defaultFor(10))
    }

    private fun pcm(vararg samples: Int): PcmAudio {
        val b = ByteArray(samples.size * 2)
        samples.forEachIndexed { i, s -> b[2 * i] = (s and 0xFF).toByte(); b[2 * i + 1] = ((s shr 8) and 0xFF).toByte() }
        return PcmAudio(16_000, 1, b)
    }

    private fun samples(a: PcmAudio) = (0 until a.data.size / 2).map { i -> ((a.data[2 * i + 1].toInt() shl 8) or (a.data[2 * i].toInt() and 0xFF)).toShort().toInt() }

    @Test
    fun quietAudioPassesUntouchedAndLoudPeaksNeverReachFullScale() {
        val quiet = pcm(1000, -2000, 15000)
        assertSame(quiet, PcmLimiter.process(quiet))
        val loud = PcmLimiter.process(pcm(32767, -32768, 30000, 1000))
        val out = samples(loud)
        assertTrue(out.toString(), out.all { abs(it) < 32767 }) // never clipped
        assertTrue(abs(out[0]) > 26000) // still loud, just not clipped
        assertEquals(1000, out[3], 1) // quiet samples are not changed
    }

    @Test
    fun aWhisperIsQuieter() {
        val out = samples(PcmLimiter.process(pcm(20000, -20000), gain = 0.65f))
        assertEquals(13000, out[0], 2)
        assertEquals(-13000, out[1], 2)
    }

    private fun assertEquals(expected: Int, actual: Int, delta: Int) = assertTrue("$actual vs $expected", abs(actual - expected) <= delta)
}
