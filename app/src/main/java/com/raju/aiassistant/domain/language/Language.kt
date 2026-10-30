package com.raju.aiassistant.domain.language

/**
 * Seema speaks Hindi and Bengali. Hinglish (Hindi in Latin letters, with English words) is treated
 * as Hindi, so the user can keep talking the way they do; English is never chosen as a reply language.
 */
enum class SeemaLanguage(val tag: String, val label: String) {
    HINDI("hi-IN", "हिन्दी Hindi"),
    BENGALI("bn-IN", "বাংলা Bengali");

    companion object {
        fun fromTag(tag: String?): SeemaLanguage? = entries.firstOrNull { it.tag == tag }
    }
}

/** Settings value meaning "detect Hindi/Bengali from what the user says". */
const val LANGUAGE_AUTO = "auto"

/** Choices in Settings → Voice → Language (stored value to label). */
val LANGUAGE_CHOICES = listOf(
    LANGUAGE_AUTO to "Auto (हिन्दी / বাংলা)",
    SeemaLanguage.HINDI.tag to SeemaLanguage.HINDI.label,
    SeemaLanguage.BENGALI.tag to SeemaLanguage.BENGALI.label,
)

/** Older versions stored "en-IN": that becomes Hindi/Hinglish; unknown values become Auto. */
fun normalizeLanguageSetting(saved: String?): String = when (saved) {
    null, "" -> LANGUAGE_AUTO
    LANGUAGE_AUTO, SeemaLanguage.HINDI.tag, SeemaLanguage.BENGALI.tag -> saved
    "en-IN" -> SeemaLanguage.HINDI.tag
    else -> LANGUAGE_AUTO
}

private fun isBengali(c: Char) = c in 'ঀ'..'৿'
private fun isDevanagari(c: Char) = c in 'ऀ'..'ॿ'

/** What the script of a text says about its language. */
enum class Detection { HINDI, BENGALI, LATIN, MIXED, NONE }

/**
 * Detects Hindi vs Bengali from the script (Unicode blocks are distinct, so this is reliable for
 * typed text and for recogniser output). Danda (।) is shared and ignored. Both scripts with no
 * clear majority → MIXED (the caller asks the user instead of guessing).
 */
fun detectScript(text: String): Detection {
    var bn = 0; var hi = 0; var latin = 0
    for (c in text) when {
        c == '।' || c == '॥' -> Unit
        isBengali(c) && c.isLetter() -> bn++
        isDevanagari(c) && c.isLetter() -> hi++
        c in 'a'..'z' || c in 'A'..'Z' -> latin++
    }
    return when {
        bn == 0 && hi == 0 -> if (latin > 0) Detection.LATIN else Detection.NONE
        bn > 0 && hi > 0 && maxOf(bn, hi) < 3 * minOf(bn, hi) -> Detection.MIXED
        bn > hi -> Detection.BENGALI
        else -> Detection.HINDI
    }
}

/**
 * The language of the conversation after the user said [text]:
 * a fixed choice in Settings always wins; in Auto, Bengali/Hindi script switches the language,
 * Latin (Hinglish) or mixed text keeps the current one (continuity).
 */
fun nextConversationLanguage(setting: String, current: SeemaLanguage, text: String): SeemaLanguage =
    SeemaLanguage.fromTag(setting) ?: when (detectScript(text)) {
        Detection.BENGALI -> SeemaLanguage.BENGALI
        Detection.HINDI -> SeemaLanguage.HINDI
        else -> current
    }

/** Extra languages the speech recogniser may accept in the same sentence. */
fun additionalSpeechLanguages(primaryTag: String): Array<String> = when (SeemaLanguage.fromTag(primaryTag)) {
    SeemaLanguage.BENGALI -> arrayOf(SeemaLanguage.HINDI.tag)
    // Hinglish: Indian English words inside Hindi sentences are normal.
    else -> arrayOf("en-IN", SeemaLanguage.BENGALI.tag)
}

/** A short line in the conversation language. */
fun phrase(language: SeemaLanguage, hindi: String, bengali: String) = if (language == SeemaLanguage.BENGALI) bengali else hindi
