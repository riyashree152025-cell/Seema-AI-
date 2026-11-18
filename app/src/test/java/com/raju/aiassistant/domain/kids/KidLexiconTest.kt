package com.raju.aiassistant.domain.kids

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KidLexiconTest {
    private val day = 24 * 3_600_000L

    @Test
    fun aParentTeachesAWord() {
        assertEquals("মালু" to "মালাই", KidLexicon.teachCommand("মালু মানে মালাই"))
        assertEquals("मालू" to "मलाई", KidLexicon.teachCommand("Seema, मालू मतलब मलाई"))
        assertEquals("हुसू" to "पेशाब", KidLexicon.teachCommand("रिया हुसू बोले तो मतलब पेशाब", requireName = true))
        assertEquals("गांधी बाबा मोरिया" to "गणपति बप्पा मोरया", KidLexicon.teachCommand("रिया गांधी बाबा मोरिया बोले तो मतलब गणपति बप्पा मोरया", requireName = true))
        // Outside Riya mode a question about a meaning is not a lesson.
        assertNull(KidLexicon.teachCommand("love ka matlab kya hai", requireName = true))
        assertNull(KidLexicon.teachCommand("love matlab pyaar", requireName = true))
        assertNull(KidLexicon.teachCommand("इसका मतलब क्या है"))
    }

    @Test
    fun riyaModeIsTurnedOnByVoice() {
        listOf("रिया मोड चालू करो", "Riya mode on karo", "রিয়া মোড চালু করো", "बेटी वाला मोड", "Seema, riya mode").forEach {
            assertEquals(it, com.raju.aiassistant.domain.assistant.AssistantAction.KidModeOn, com.raju.aiassistant.domain.assistant.LocalCommands.parse(it))
        }
        assertTrue(com.raju.aiassistant.domain.assistant.LocalCommands.parse("रिया को कॉल करो") != com.raju.aiassistant.domain.assistant.AssistantAction.KidModeOn)
    }

    @Test
    fun herYesAndNoAreUnderstood() {
        listOf("হ্যাঁ", "হ্যাঁ খাবো", "হুঁ", "हाँ", "haan").forEach { assertEquals(it, true, KidLexicon.answer(it)) }
        listOf("না", "না খাবো না", "नहीं", "no").forEach { assertEquals(it, false, KidLexicon.answer(it)) }
        assertNull(KidLexicon.answer("খীর মালু খাবো"))
    }

    @Test
    fun theAisNotesAreKeptAndNeverSpoken() {
        val n = KidLexicon.notes("তুমি কি মালাই খাবে? [ASK:মালু=মালাই]")
        assertEquals("তুমি কি মালাই খাবে?", n.text)
        assertEquals("মালু" to "মালাই", n.ask)
        val l = KidLexicon.notes("হ্যাঁ সোনা, খীর মালাই খুব মিষ্টি! [LEARN:মালু=মালাই] <action>{\"type\":\"call\"}</action>")
        assertEquals("হ্যাঁ সোনা, খীর মালাই খুব মিষ্টি!", l.text)
        assertEquals(listOf("মালু" to "মালাই"), l.learned)
    }

    @Test
    fun herWordsAreFoundBySoundAndOldOnesFade() {
        val words = listOf(
            KidWord("মালু", "মালাই", KidWord.Source.CONFIRMED, learnedAt = 0, lastUsedAt = 100 * day, uses = 3),
            KidWord("হুসু", "পেশাব", KidWord.Source.TAUGHT, learnedAt = 0, lastUsedAt = 0),
            KidWord("পাপা", "পানি", KidWord.Source.GUESSED, learnedAt = 0, lastUsedAt = 0, uses = 1),
        )
        assertEquals(listOf("মালু"), KidLexicon.inText("খীর মালু খাবো", words).map { it.heard })
        assertEquals(listOf("মালু"), KidLexicon.inText("खीर मालू खाबो", words).map { it.heard }) // other script, same sound
        val prompt = KidLexicon.forPrompt("হুসু", words, now = 101 * day).map { it.heard }
        assertEquals("হুসু", prompt.first()) // the word she just said comes first
        assertTrue("মালু" in prompt)
        assertTrue("an unused guess fades out", "পাপা" !in prompt)
    }

    @Test
    fun thePromptCarriesHerWordsAndTheToddlerRules() {
        val p = KidPrompt.system("রিয়া", listOf(KidWord("মালু", "মালাই", KidWord.Source.TAUGHT, 0)), "evening")
        assertTrue(p.contains("মালু → মালাই"))
        assertTrue(p.contains("[ASK:") && p.contains("[LEARN:"))
        assertTrue(p.contains("no calls"))
    }
}
