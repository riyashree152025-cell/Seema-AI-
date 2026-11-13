package com.raju.aiassistant.domain.assistant

import com.raju.aiassistant.domain.language.SeemaLanguage
import com.raju.aiassistant.domain.model.Personality
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
        val lines = (1..30).map { e.personalize("हो गया राजू, भेज दिया।", Personality.COMPANION, SeemaLanguage.HINDI) }
        lines.forEach { assertFalse(it, it.contains("राजू")) }
        val pets = lines.filter { it != "हो गया, भेज दिया।" }
        assertTrue("pet names: ${pets.size}", pets.size in 8..12) // about one line in three
        val words = pets.map { it.removePrefix("हो गया ").substringBefore(",") }
        assertTrue(words.toSet().size >= 4) // बाबू, सोना, जान, जानू, बेबी …
        words.zipWithNext().forEach { (a, b) -> assertTrue("$a twice", a != b) }
    }

    @Test
    fun bengaliHasItsOwnWordsAndProStaysPlain() {
        val bn = Endearment.petNames(Personality.COMPANION, SeemaLanguage.BENGALI)
        assertTrue(bn.contains("সোনা") && bn.contains("বাবু"))
        assertTrue(bn.none { it.any { c -> c in 'ऀ'..'ॿ' } }) // no Hindi letters in Bengali
        assertTrue(Endearment.petNames(Personality.PRO, SeemaLanguage.HINDI).isEmpty())
        val e = Endearment()
        repeat(9) { assertEquals("हो गया, भेज दिया।", e.personalize("हो गया राजू, भेज दिया।", Personality.PRO, SeemaLanguage.HINDI)) }
    }

    @Test
    fun herInstructionsNoLongerTeachHerToSayTheName() {
        Personality.entries.forEach { p ->
            val prompt = buildSystemPrompt(emptyList(), "now", p)
            assertFalse("${p.name} still has the Hindi name in an example", prompt.contains("राजू"))
            assertFalse("${p.name} still has the Bengali name in an example", prompt.contains("রাজু"))
        }
        val gf = buildSystemPrompt(emptyList(), "now", Personality.COMPANION)
        assertTrue(gf.contains("almost never say it"))
        assertTrue(gf.contains("সোনা") && gf.contains("बाबू"))
    }
}
