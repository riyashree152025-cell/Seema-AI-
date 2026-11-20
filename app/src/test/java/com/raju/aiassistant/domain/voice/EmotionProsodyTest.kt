package com.raju.aiassistant.domain.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmotionProsodyTest {
    @Test
    fun theFeelingOfEachSentence() {
        assertEquals(SpeechEmotion.POUTING, SpeechEmotion.of("तुम कहाँ थे इतनी देर से? मैं कब से इंतज़ार कर रही थी…"))
        assertEquals(SpeechEmotion.POUTING, SpeechEmotion.of("কোথায় ছিলে এতক্ষণ?"))
        assertEquals(SpeechEmotion.POUTING, SpeechEmotion.of("सुनो…", VoiceMood.CONTROLLED_UPSET))
        assertEquals(SpeechEmotion.POUTING, SpeechEmotion.of("चलो अब आराम करो…", VoiceMood.WHISPER, hour = 23, minute = 45))
        assertEquals(SpeechEmotion.AFFECTIONATE, SpeechEmotion.of("तुम्हारे बिना अच्छा नहीं लगता, जान।"))
        assertEquals(SpeechEmotion.PLAYFUL, SpeechEmotion.of("हाहा… अरे पागल, ऐसे थोड़ी होता है!"))
        assertEquals(SpeechEmotion.CALM, SpeechEmotion.of("आज चार हज़ार पाँच सौ रुपये आए।"))
        assertEquals(SpeechEmotion.CALM, SpeechEmotion.of("PNR 6840828103 कन्फर्म है।"))
        assertEquals(SpeechEmotion.WARM, SpeechEmotion.of("ठीक है, देख रही हूँ।"))
    }

    @Test
    fun swaraProsodyPerEmotion() {
        val v = EdgeTts.SWARA
        val warmRate = VoiceMood.WARM.rate // the user's speed setting 1.0 with the warm mood
        assertEquals(-11 to -3, EdgeTts.prosodyFor(v, SpeechEmotion.POUTING, warmRate, VoiceMood.WARM))
        assertEquals(-8 to 1, EdgeTts.prosodyFor(v, SpeechEmotion.AFFECTIONATE, warmRate, VoiceMood.WARM))
        assertEquals(-2 to 4, EdgeTts.prosodyFor(v, SpeechEmotion.PLAYFUL, warmRate, VoiceMood.WARM))
        assertEquals(0 to 1, EdgeTts.prosodyFor(v, SpeechEmotion.CALM, warmRate, VoiceMood.WARM))
        // WARM keeps the voice's own soft base, unchanged from before.
        assertEquals(-6 to 2, EdgeTts.prosodyFor(v, SpeechEmotion.WARM, warmRate, VoiceMood.WARM))
        // An upset mood's slower rate is not counted twice on top of pouting.
        assertEquals(-11 to -3, EdgeTts.prosodyFor(v, SpeechEmotion.POUTING, VoiceMood.CONTROLLED_UPSET.rate, VoiceMood.CONTROLLED_UPSET))
        // The user's own speed setting still counts (+10 %).
        assertEquals(-1 to -3, EdgeTts.prosodyFor(v, SpeechEmotion.POUTING, warmRate * 1.1f, VoiceMood.WARM))
        assertEquals(-5 to 2, EdgeTts.prosodyFor(EdgeTts.TANISHAA, SpeechEmotion.WARM, warmRate, null))
    }

    @Test
    fun ssmlCarriesProsodyAndPauses() {
        val pout = EdgeTts.ssml("तुम कहाँ थे… मैं इंतज़ार कर रही थी, सच में।", EdgeTts.SWARA, -11, -3, SpeechEmotion.POUTING)
        assertTrue(pout, pout.contains("<prosody pitch='-3Hz' rate='-11%' volume='+0%'>"))
        assertTrue(pout, pout.contains("…<break time='280ms'/> मैं"))
        assertTrue(pout, pout.contains(",<break time='280ms'/> सच"))
        val play = EdgeTts.ssml("हाहा… अरे पागल, सुनो", EdgeTts.SWARA, -2, 4, SpeechEmotion.PLAYFUL)
        assertTrue(play, play.contains("pitch='+4Hz' rate='-2%'"))
        assertFalse(play, play.contains(",<break")) // commas pause only when tender or pouting
        // A trailing "…" gets no break, and without breaks the text is untouched.
        val plain = EdgeTts.ssml("तुम कहाँ थे…", EdgeTts.SWARA, -11, -3, SpeechEmotion.POUTING, breaks = false)
        assertFalse(plain.contains("<break"))
        assertFalse(EdgeTts.ssml("सुनो ना…", EdgeTts.SWARA, -11, -3, SpeechEmotion.POUTING).contains("<break"))
        // Symbols are escaped, never read out or breaking the SSML.
        assertTrue(EdgeTts.ssml("A & B <x>", EdgeTts.SWARA, 0, 1, SpeechEmotion.CALM).contains("A &amp; B &lt;x&gt;"))
    }

    @Test
    fun cartesiaFollowsTheFeeling() {
        val pout = CartesiaControls.forModel("sonic-3", VoiceMood.WARM.rate, "affectionate", VoiceMood.WARM, SpeechEmotion.POUTING) as CartesiaControls.Generation
        val play = CartesiaControls.forModel("sonic-3", VoiceMood.WARM.rate, "affectionate", VoiceMood.WARM, SpeechEmotion.PLAYFUL) as CartesiaControls.Generation
        assertEquals("sympathetic", pout.emotion)
        assertEquals("happy", play.emotion)
        assertTrue(pout.speed < play.speed)
        // Without a chosen emotion nothing is sent, as before.
        assertEquals(null, (CartesiaControls.forModel("sonic-3", VoiceMood.WARM.rate, null, VoiceMood.WARM, SpeechEmotion.PLAYFUL) as CartesiaControls.Generation).emotion)
    }
}
