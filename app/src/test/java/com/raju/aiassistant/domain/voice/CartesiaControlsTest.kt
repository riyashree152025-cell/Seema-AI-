package com.raju.aiassistant.domain.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CartesiaControlsTest {
    private val warm = VoiceMood.WARM.rate

    @Test
    fun sonic3SpeaksSlowerAndAffectionate() {
        val c = CartesiaControls.forModel("sonic-3", warm, VoiceCatalog.cartesiaEmotion(""), VoiceMood.WARM)
        assertEquals(CartesiaControls.Generation(0.85, "affectionate"), c) // -15 %: unhurried, never newsreader-fast
        assertEquals(0.85, (c as CartesiaControls.Generation).speed, 0.001)
    }

    @Test
    fun lateNightAndSadMomentsSoundCalmNotBubbly() {
        assertEquals("calm", (CartesiaControls.forModel("sonic-3.5", VoiceMood.WHISPER.rate, "affectionate", VoiceMood.WHISPER) as CartesiaControls.Generation).emotion)
        assertEquals("sympathetic", (CartesiaControls.forModel("sonic-latest", VoiceMood.CONTROLLED_UPSET.rate, "affectionate", VoiceMood.CONTROLLED_UPSET) as CartesiaControls.Generation).emotion)
    }

    @Test
    fun naturalChosenSendsNoEmotion() {
        val c = CartesiaControls.forModel("sonic-3", warm, VoiceCatalog.cartesiaEmotion("natural"), VoiceMood.WARM) as CartesiaControls.Generation
        assertEquals(null, c.emotion)
    }

    @Test
    fun olderModelsGetTheExperimentalVoiceControls() {
        val c = CartesiaControls.forModel("sonic-2", warm, "affectionate", VoiceMood.WARM)
        assertEquals(CartesiaControls.Experimental(-0.15, listOf("positivity:high")), c) // "speed": -0.15
        assertEquals(listOf("positivity:low"), (CartesiaControls.forModel("sonic-english", VoiceMood.WHISPER.rate, "affectionate", VoiceMood.WHISPER) as CartesiaControls.Experimental).emotions)
        // No "affection" tag exists in this set (the request would fail): never sent.
        assertTrue((c as CartesiaControls.Experimental).emotions.none { it.startsWith("affection") })
    }

    @Test
    fun theModelFamilyIsRecognised() {
        assertTrue(CartesiaControls.isSonic3("sonic-3"))
        assertTrue(CartesiaControls.isSonic3("sonic-3.5"))
        assertTrue(CartesiaControls.isSonic3("sonic-latest"))
        assertFalse(CartesiaControls.isSonic3("sonic-2"))
        assertFalse(CartesiaControls.isSonic3("sonic-multilingual"))
    }

    @Test
    fun speedStaysInsideCartesiasRange() {
        assertEquals(0.6, (CartesiaControls.forModel("sonic-3", 0.3f, null, null) as CartesiaControls.Generation).speed, 0.001)
        assertEquals(1.5, (CartesiaControls.forModel("sonic-3", 3f, null, null) as CartesiaControls.Generation).speed, 0.001)
    }
}
