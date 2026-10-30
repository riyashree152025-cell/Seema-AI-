package com.raju.aiassistant.domain.language

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageTest {
    @Test
    fun scriptsAreDetected() {
        assertEquals(Detection.HINDI, detectScript("आज मौसम कैसा है?"))
        assertEquals(Detection.BENGALI, detectScript("আজ আবহাওয়া কেমন?"))
        assertEquals(Detection.LATIN, detectScript("aaj mausam kaisa hai"))
        assertEquals(Detection.NONE, detectScript("123 ।"))
        // Mostly Bengali with one Hindi word: still Bengali.
        assertEquals(Detection.BENGALI, detectScript("আমার নাম সীমা, আমি তোমাকে সাহায্য করব हाँ"))
        assertEquals(Detection.MIXED, detectScript("नमस्ते নমস্কার"))
    }

    @Test
    fun autoFollowsTheUserAndKeepsTheLanguageForHinglish() {
        var lang = SeemaLanguage.HINDI
        lang = nextConversationLanguage(LANGUAGE_AUTO, lang, "তুমি কেমন আছো?")
        assertEquals(SeemaLanguage.BENGALI, lang)
        lang = nextConversationLanguage(LANGUAGE_AUTO, lang, "ok theek hai")
        assertEquals(SeemaLanguage.BENGALI, lang)
        lang = nextConversationLanguage(LANGUAGE_AUTO, lang, "अब हिंदी में बोलो")
        assertEquals(SeemaLanguage.HINDI, lang)
    }

    @Test
    fun aManualChoiceAlwaysWins() {
        assertEquals(SeemaLanguage.BENGALI, nextConversationLanguage("bn-IN", SeemaLanguage.HINDI, "आप कैसे हैं"))
        assertEquals(SeemaLanguage.HINDI, nextConversationLanguage("hi-IN", SeemaLanguage.BENGALI, "তুমি কেমন আছো"))
    }

    @Test
    fun oldEnglishSettingBecomesHindiAndOnlyHindiBengaliAreOffered() {
        assertEquals("hi-IN", normalizeLanguageSetting("en-IN"))
        assertEquals(LANGUAGE_AUTO, normalizeLanguageSetting(null))
        assertEquals("bn-IN", normalizeLanguageSetting("bn-IN"))
        assertEquals(listOf(LANGUAGE_AUTO, "hi-IN", "bn-IN"), LANGUAGE_CHOICES.map { it.first })
    }

    @Test
    fun recogniserGetsTheOtherLanguageAsExtra() {
        assertArrayEquals(arrayOf("hi-IN"), additionalSpeechLanguages("bn-IN"))
        assertArrayEquals(arrayOf("en-IN", "bn-IN"), additionalSpeechLanguages("hi-IN"))
    }

    @Test
    fun phrasesFollowTheLanguage() {
        assertEquals("হ্যাঁ, বলুন?", phrase(SeemaLanguage.BENGALI, "हाँ जी, बोलिए?", "হ্যাঁ, বলুন?"))
        assertEquals("हाँ जी, बोलिए?", phrase(SeemaLanguage.HINDI, "हाँ जी, बोलिए?", "হ্যাঁ, বলুন?"))
    }
}
