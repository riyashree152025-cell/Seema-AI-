package com.raju.aiassistant.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LoudnessTest {
    private fun pcm(vararg samples: Int): PcmAudio {
        val b = ByteArray(samples.size * 2)
        samples.forEachIndexed { i, s -> b[i * 2] = (s and 0xFF).toByte(); b[i * 2 + 1] = ((s shr 8) and 0xFF).toByte() }
        return PcmAudio(16_000, 1, b)
    }

    private fun samples(a: PcmAudio) = (0 until a.data.size / 2).map { i ->
        ((a.data[i * 2 + 1].toInt() shl 8) or (a.data[i * 2].toInt() and 0xFF)).toShort().toInt()
    }

    @Test
    fun aQuietVoiceIsRaisedToNearFullScaleWithoutClipping() {
        val out = samples(louder(pcm(0, 10_000, -8_000, 5_000)))
        assertEquals(31_000, out.maxOf { kotlin.math.abs(it) })
        assertTrue(out.all { it in -32_768..32_767 })
        assertEquals(-24_800, out[2]) // same shape, 3.1 x louder
    }

    @Test
    fun loudAudioAndSilenceAreLeftAlone() {
        val loud = pcm(30_000, -31_500)
        assertSame(loud, louder(loud))
        val silent = pcm(0, 0, 0)
        assertSame(silent, louder(silent))
    }

    @Test
    fun nearSilenceIsNotTurnedIntoARoar() {
        val out = samples(louder(pcm(100, -50)))
        assertEquals(600, out[0]) // at most 6 x
    }
}
