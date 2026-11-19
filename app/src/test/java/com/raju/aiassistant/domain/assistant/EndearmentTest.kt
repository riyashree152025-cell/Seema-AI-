package com.raju.aiassistant.domain.assistant

import com.raju.aiassistant.domain.language.SeemaLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EndearmentTest {

    @Test
    fun theNameIsLeftOutOrBecomesAPetName() {
        assertEquals("यूट्यूब पर गाना बजा दिया है, सुनिए!", Endearment.replaceName("राजू, यूट्यूब पर गाना बजा दिया है, सुनिए!", ""))
        assertEquals("गाना रोक दिया है।", Endearment.replaceName("गाना रोक दिया है राजू।", ""))
        assertEquals("हाँ, मैं अपनी स्क्रीन पर वापस आ गई!", Endearment.replaceName("हाँ राजू, मैं अपनी स्क्रीन पर वापस आ गई!", ""))
        assertEquals("बढ़िया, फोन मिल गया!", Endearment.replaceName("बढ़िया राजू जी, फोन मिल गया!", ""))
        assertEquals("हाँ बाबू, मैं अपनी स्क्रीन पर वापस आ गई!", Endearment.replaceName("हाँ राजू, मैं अपनी स्क्रीन पर वापस आ गई!", "बाबू"))
        assertEquals("गाना रोक दिया है सोना।", Endearment.replaceName("गाना रोक दिया है राजू।", "सोना"))
        assertEquals("হয়ে গেছে, মেসেজ পাঠিয়ে দিয়েছি।", Endearment.replaceName("হয়ে গেছে রাজু, মেসেজ পাঠিয়ে দিয়েছি।", ""))
        assertEquals("হ্যাঁ সোনা, পাঠাচ্ছি!", Endearment.replaceName("হ্যাঁ রাজু, পাঠাচ্ছি!", "সোনা"))
        assertEquals("कोई नाम नहीं", Endearment.replaceName("कोई नाम नहीं", ""))
    }

    @Test
    fun mostLinesCarryNoNameAndPetNamesVary() {
        val e = Endearment()
        val lines = (1..40).map { e.personalize("हो गया राजू, भेज दिया।", SeemaLanguage.HINDI) }
        lines.forEach { assertFalse(it, it.contains("राजू")) }
        val pets = lines.filter { it != "हो गया, भेज दिया।" }
        assertEquals("pet names: ${pets.size}", 10, pets.size) // one line in four: never forced
        val words = pets.map { it.removePrefix("हो गया ").substringBefore(",") }
        assertTrue(words.toSet().size >= 3) // जान, सोना, बाबू
        words.zipWithNext().forEach { (a, b) -> assertTrue("$a twice", a != b) }
    }

    @Test
    fun bengaliHasItsOwnWords() {
        val bn = Endearment.petNames(SeemaLanguage.BENGALI)
        assertTrue(bn.contains("সোনা") && bn.contains("বাবু"))
        assertTrue(bn.none { it.any { c -> c in 'ऀ'..'ॿ' } }) // no Hindi letters in Bengali
        val e = Endearment()
        val lines = (1..8).map { e.personalize("হয়ে গেছে রাজু, পাঠিয়ে দিয়েছি।", SeemaLanguage.BENGALI) }
        lines.forEach { assertFalse(it, it.contains("রাজু")) }
        assertTrue(lines.none { it.any { c -> c in 'ऀ'..'ॣ' } }) // (the danda । is shared by both scripts)
    }

    @Test
    fun herInstructionsNoLongerTeachHerToSayTheName() {
        val prompt = buildSystemPrompt(emptyList(), "now")
        assertFalse("the Hindi name is in an example", prompt.contains("राजू"))
        assertFalse("the Bengali name is in an example", prompt.contains("রাজু"))
        assertTrue(prompt.contains("almost never say his name"))
        assertTrue(prompt.contains("সোনা") && prompt.contains("बाबू"))
        assertTrue(prompt.contains("never \"haan babu\" as a habit"))
    }
}
