package com.raju.aiassistant.domain.language

/**
 * Speech recognition sometimes writes the user's Hindi in another Indian script ("గీతా కామత్కు" for
 * "गीता कामत्कु"). Indian scripts share one Unicode layout, so each letter moves to the same place in
 * Devanagari. Bengali is left alone (Seema speaks Bengali too); Latin and Devanagari are unchanged.
 */
private val OTHER_INDIC = listOf(
    0x0A00, // Gurmukhi
    0x0A80, // Gujarati
    0x0B00, // Odia
    0x0B80, // Tamil
    0x0C00, // Telugu
    0x0C80, // Kannada
    0x0D00, // Malayalam
)

fun toDevanagari(text: String): String {
    if (text.none { ch -> OTHER_INDIC.any { ch.code in it until it + 0x80 } }) return text
    val out = StringBuilder(text.length)
    for (ch in text) {
        val block = OTHER_INDIC.firstOrNull { ch.code in it until it + 0x80 }
        if (block == null) { out.append(ch); continue }
        val offset = ch.code - block
        val dev = 0x0900 + offset
        // Digits and letters that exist in Devanagari move across; anything else stays as it was.
        out.append(if (Character.isDefined(dev) && Character.UnicodeBlock.of(dev) == Character.UnicodeBlock.DEVANAGARI) dev.toChar() else ch)
    }
    return out.toString()
}


/** True when [text] has Bengali letters. */
fun hasBengaliLetters(text: String): Boolean = text.any { it in '\u0980'..'\u09FF' }

/**
 * Bengali letters moved to the same place in Devanagari ("আজ কিতনা ডেবিট" → "आज कितना डेबिट"): for Hindi that a
 * Bengali recogniser wrote in Bengali script. Letters with no Devanagari twin stay as they are.
 */
fun bengaliToDevanagari(text: String): String {
    val out = StringBuilder(text.length)
    for (ch in text) {
        if (ch !in '\u0980'..'\u09FF') { out.append(ch); continue }
        val dev = ch.code - 0x80
        out.append(if (Character.isDefined(dev) && Character.UnicodeBlock.of(dev) == Character.UnicodeBlock.DEVANAGARI) dev.toChar() else ch)
    }
    return out.toString()
}

private val WHATSAPP_HEARD = Regex(
    "(?i)(?<![\\p{L}])(?:what'?s\\s?up|what\\s?sapp|वॉट्स\\s?अप|वाट्स\\s?अप|व्हाट्स\\s?अप|व्हाट्सएप|व्हाट्सऐप|वॉट्सऐप|वॉट्सएप)(?![\\p{L}])",
)

/** Words the recogniser often gets wrong in commands: "What's up में … message करो" is WhatsApp. */
fun fixCommonMishearings(text: String): String = WHATSAPP_HEARD.replace(text, "WhatsApp")
