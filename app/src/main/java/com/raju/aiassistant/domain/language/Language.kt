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

private val ROMAN_BENGALI_WORDS = setOf(
    "kemon", "aacho", "acho", "achho", "aami", "ami", "tumi", "tomar", "amader",
    "bhalo", "bhaloachi", "korcho", "korchho", "koro", "koren", "korbo", "korun",
    "shono", "bolchi", "bolun", "bolo", "kothay", "ekhon", "kichu", "kintu", "khobor",
    "valo", "shuncho", "hobe", "cheno", "cheni", "pathao", "pathan", "lagao", "khulo",
    "bondho", "thamo", "eshe", "amar", "apnar", "aache", "ache", "nei", "holo", "gaan"
)

private val ROMAN_HINDI_WORDS = setOf(
    "kaise", "kaisa", "kaisi", "kya", "kar", "karo", "kare", "karein", "karna", "kijiye",
    "rahe", "rahi", "raha", "mera", "meri", "mere", "mujhko", "mujhe", "batao", "suno",
    "bolo", "boliye", "kaha", "kahan", "nahi", "nahin", "ko", "se", "lagao", "laga",
    "bhejo", "bhejna", "kholo", "khol", "band", "rok", "roko", "apna", "apni", "apne",
    "tha", "thi", "the", "hoga", "hogi", "hum", "aap", "tum", "gaana", "gana", "bajao",
    "chalao", "sunao"
)

fun detectLatinLanguage(text: String): SeemaLanguage? {
    val words = text.lowercase().split(Regex("[^a-z]+")).filter { it.isNotBlank() }
    val bnScore = words.count { it in ROMAN_BENGALI_WORDS }
    val hiScore = words.count { it in ROMAN_HINDI_WORDS }
    return when {
        bnScore > hiScore -> SeemaLanguage.BENGALI
        hiScore > bnScore -> SeemaLanguage.HINDI
        else -> null
    }
}

private val ENGLISH_FUNCTION_WORDS = setOf(
    "the", "a", "an", "is", "are", "was", "were", "i", "you", "your", "it", "this", "that", "of", "to",
    "in", "on", "for", "and", "or", "with", "have", "has", "will", "can", "do", "does", "not", "what",
    "how", "my", "me", "we", "be", "at", "from", "please", "thank", "thanks", "there", "here",
)

/**
 * A Roman-script sentence that is plain English (not Hinglish): at least three words, half of them
 * common English words, and no Hindi/Bengali word. Short or doubtful text is not English.
 */
fun isLikelyEnglish(text: String): Boolean {
    val words = text.lowercase().split(Regex("[^a-z']+")).filter { it.isNotBlank() }
    if (words.size < 3) return false
    // "the" is also Hinglish (थे): words that are English as well do not count against English.
    if (words.any { (it in ROMAN_HINDI_WORDS || it in ROMAN_BENGALI_WORDS) && it !in ENGLISH_FUNCTION_WORDS }) return false
    return words.count { it in ENGLISH_FUNCTION_WORDS } * 2 >= words.size
}

/**
 * The language of the conversation after the user said [text]:
 * a fixed choice in Settings always wins; in Auto, Bengali/Hindi script switches the language,
 * Latin (Hinglish/Benglish) switches if distinct keywords match, or keeps the current one.
 */
fun nextConversationLanguage(setting: String, current: SeemaLanguage, text: String): SeemaLanguage =
    SeemaLanguage.fromTag(setting) ?: when (detectScript(text)) {
        Detection.BENGALI -> SeemaLanguage.BENGALI
        // Bengali spoken to a Hindi recogniser (Deepgram) comes back in Hindi letters: "आमि भालो आछि".
        Detection.HINDI -> if (isBengaliInDevanagari(text)) SeemaLanguage.BENGALI else SeemaLanguage.HINDI
        Detection.LATIN -> detectLatinLanguage(text) ?: current
        else -> current
    }

/**
 * Bengali words written in Hindi letters, as a Hindi-only recogniser writes Bengali speech: "तुमि केमोन आछो",
 * "आमि एखन बाड़ी जाबो". Two Bengali-only words, and more than the Hindi-only ones ("है", "नहीं", "क्या").
 */
fun isBengaliInDevanagari(text: String): Boolean {
    val words = text.split(Regex("[^\\p{L}\\p{M}]+")).filter { it.isNotBlank() }
    val bengali = words.count { it in BENGALI_WORDS_IN_DEVANAGARI }
    val hindi = words.count { it in HINDI_ONLY_WORDS }
    return bengali >= 2 && bengali > hindi
}

private val BENGALI_WORDS_IN_DEVANAGARI = setOf(
    "आमि", "आमार", "आमाके", "आमरा", "तुमि", "तोमार", "तोमाके", "तोमरा", "आपनि", "आपनार", 
    "कोरो", "कोरछो", "कोरछि", "कोरबो", "कोरबे", "कोरेछि", "कोरेछो", "कोरे", "कोरते", "भालो", "आछे", "आछो", "आछि",
    "नेई", "होबे", "होये", "होलो", "कोथाय", "केमोन", "केनो", "एखन", "एखोन", "एकटु", "दाओ", "जाबो", 
    "खाबो", "खेये", "पारबो", "पारि", "पारो", "बोलछि", "बोलछो", "बोलबे", "कोथा", "शुनो", "शोनो", "बुझते", "बुझलाम",
    "किछु", "सोब", "शोब", "ताहोले", "आजके", "कालके", "एबार", "बाड़ी", "बाड़ि", 
)
private val HINDI_ONLY_WORDS = setOf(
    "है", "हैं", "हूँ", "हूं", "था", "थी", "नहीं", "क्या", "करो", "करना", "रहा", "रही", "रहे", "मुझे", "मेरा", "मेरी",
    "तुम्हारा", "आप", "का", "की", "के", "को", "में", "से", "और", "लेकिन", "क्यों", "कैसे", "बताओ",
)

/** Extra languages the speech recogniser may accept in the same sentence. */
fun additionalSpeechLanguages(primaryTag: String): Array<String> = when (SeemaLanguage.fromTag(primaryTag)) {
    SeemaLanguage.BENGALI -> arrayOf(SeemaLanguage.HINDI.tag)
    // Hinglish: Indian English words inside Hindi sentences are normal.
    else -> arrayOf("en-IN", SeemaLanguage.BENGALI.tag)
}

/** A short line in the conversation language. */
fun phrase(language: SeemaLanguage, hindi: String, bengali: String) = if (language == SeemaLanguage.BENGALI) bengali else hindi
