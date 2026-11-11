package com.raju.aiassistant.domain.vision

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionPromptTest {
    @Test
    fun aQuestionIsPassedOnAndTheLanguageIsSet() {
        val p = visionPrompt(VisionTask.ASK, "  ye kaun si dawai hai?  ", 1, hindi = true)
        assertTrue(p.contains("ye kaun si dawai hai?"))
        assertTrue(p.contains("Hindi"))
        assertTrue(visionPrompt(VisionTask.ASK, "", 1, hindi = false).contains("Bengali"))
    }

    @Test
    fun readingTextMustNotTranslateOrInvent() {
        val p = visionPrompt(VisionTask.READ_TEXT, "", 1, hindi = true)
        assertTrue(p.contains("exactly as written"))
        assertTrue(p.contains("Do not translate"))
        assertTrue(p.contains("If there is no readable text, say so"))
    }

    @Test
    fun comparingNamesTheNumberOfPictures() {
        val p = visionPrompt(VisionTask.COMPARE, "price", 3, hindi = true)
        assertTrue(p.contains("Compare the 3 pictures"))
        assertTrue(p.contains("price"))
    }

    @Test
    fun everyTaskAsksForHonesty() {
        VisionTask.entries.forEach { assertTrue(it.name, visionPrompt(it, "", 2, hindi = true).contains("guess")) }
        assertFalse(visionPrompt(VisionTask.ASK, "", 1, hindi = true).contains("pictures:"))
    }
}
