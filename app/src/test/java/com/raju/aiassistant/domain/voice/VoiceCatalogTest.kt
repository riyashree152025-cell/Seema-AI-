package com.raju.aiassistant.domain.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCatalogTest {
    @Test
    fun femaleHindiVoicesAreSuggestedFirst() {
        val voices = listOf(
            CloudVoice("1", "Adam"),
            CloudVoice("2", "Zara", female = true),
            CloudVoice("3", "Meera", female = true, hindi = true),
            CloudVoice("4", "Raj", hindi = true),
        )
        assertEquals(listOf("3", "2", "4", "1"), VoiceCatalog.sortVoices(voices).map { it.id })
        assertEquals("3", VoiceCatalog.bestDefault(voices)?.id)
    }

    @Test
    fun sttOptionSplitsModelAndLanguage() {
        val o = VoiceCatalog.sttOption("nova-3:multi")
        assertEquals("nova-3", o.model)
        assertEquals("multi", o.language)
        assertEquals(VoiceCatalog.deepgramSttModels.first(), VoiceCatalog.sttOption("unknown"))
    }

    @Test
    fun onlyEmotionsCartesiaKnowsAreSent() {
        assertEquals("affectionate", VoiceCatalog.cartesiaEmotion("")) // no choice: her warm voice
        assertEquals(null, VoiceCatalog.cartesiaEmotion("natural")) // "Natural" chosen: no emotion sent
        assertEquals("affectionate", VoiceCatalog.cartesiaEmotion("pyaar_bhari"))
        assertEquals("content", VoiceCatalog.cartesiaEmotion("khush"))
        assertEquals("calm", VoiceCatalog.cartesiaEmotion("shaant"))
        assertEquals("happy", VoiceCatalog.cartesiaEmotion("happy"))
        assertEquals(null, VoiceCatalog.cartesiaEmotion("angry_robot")) // unknown: not sent
    }
}
