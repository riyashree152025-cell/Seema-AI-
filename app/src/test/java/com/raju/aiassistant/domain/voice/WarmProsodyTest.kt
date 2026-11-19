package com.raju.aiassistant.domain.voice

import com.raju.aiassistant.core.audio.PcmAudio
import org.junit.Assert.assertEquals
import org.junit.Test

class WarmProsodyTest {
    @Test
    fun pausesBecomeRealPausesAndSentencesEndSoftly() {
        assertEquals("अच्छा… सुनो ना, आज बहुत थक गए हो।", WarmProsody.text("अच्छा...सुनो ना,आज बहुत थक गए हो"))
        assertEquals("শোনো না, একটু বিশ্রাম নাও।", WarmProsody.text("শোনো না,একটু বিশ্রাম নাও"))
        assertEquals("Haan, main dekh rahi hoon.", WarmProsody.text("Haan,  main dekh rahi hoon"))
        assertEquals("ठीक है?", WarmProsody.text("ठीक है?")) // a question keeps its rising end
        assertEquals("", WarmProsody.text("   "))
    }

    @Test
    fun aQuietTailKeepsTheLastSyllable() {
        val pcm = PcmAudio(24_000, 1, ByteArray(480))
        val out = WarmProsody.withTail(pcm)
        assertEquals(480 + 24_000 * WarmProsody.TAIL_MS / 1000 * 2, out.data.size)
        assertEquals(24_000, out.sampleRate)
    }
}
