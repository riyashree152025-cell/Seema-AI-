package com.raju.aiassistant.domain.voice

/** How speech recognition writes "Seema" (Hindi, Bengali, and Latin script, common mishearings). */
private val WAKE_WORDS = listOf(
    // 2-word composite wake words (with and without space)
    "hey seema", "heyseema", "hey sima", "heysima", "hi seema", "hiseema", "hello seema", "ok seema",
    "\u0939\u0947 \u0938\u0940\u092e\u093e", // हे सीमा
    "\u0939\u0947\u0938\u0940\u092e\u093e",   // हेसीमा (no space)
    "\u0939\u0947 \u0938\u093f\u092e\u093e", // हे सिमा
    "\u0939\u0947\u0938\u093f\u092e\u093e",   // हेसिमा (no space)
    "\u0939\u093e\u092f \u0938\u0940\u092e\u093e", // हाय सीमा
    "\u0939\u093e\u092f\u0938\u0940\u092e\u093e",   // हायसीमा
    "\u0939\u0947\u0932\u094b \u0938\u0940\u092e\u093e", // हेलो सीमा
    "\u0939\u0947\u0932\u094b\u0938\u0940\u092e\u093e",   // हेलोसीमा
    "\u09b9\u09cd\u09af\u09be\u09b2\u09cb \u09b8\u09c0\u09ae\u09be", // হ্যালো সীমা
    "\u09b9\u09cd\u09af\u09be\u09b2\u09cb\u09b8\u09c0\u09ae\u09be",   // হ্যালোসীমা
    "\u09b6\u09cb\u09a8\u09cb \u09b8\u09c0\u09ae\u09be", // শোনো সীমা
    "\u09b6\u09cb\u09a8\u09cb\u09b8\u09c0\u09ae\u09be",   // শোনোসীমা
    // Hindi composite & single words
    "\u0938\u0940\u092e\u093e \u091c\u0940", // सीमा जी
    "\u0938\u0940\u092e\u093e\u091c\u0940", // सीमाजी
    "\u0938\u0940\u092e\u093e", // सीमा
    "\u0938\u093f\u092e\u093e", // सिमा
    "\u0936\u0940\u092e\u093e", // शीमा
    "\u0938\u0940\u092e\u093e\u0902", // सीमां
    "\u0938\u0940\u0924\u093e", // सीता (frequent STT mishear)
    // English / transliterated single words
    "seema ji", "seemaji", "seema", "sima", "shima", "seemaa", "sema", "sheema", "seem", "simran",
    // Bengali single words
    "\u09b8\u09c0\u09ae\u09be \u09a6\u09bf", // সীমা দি
    "\u09b8\u09c0\u09ae\u09be\u0997\u09cb", // সীমাগো
    "\u09b8\u09c0\u09ae\u09be", // সীমা
    "\u09b8\u09bf\u09ae\u09be", // সিমা
    "\u09b6\u09c0\u09ae\u09be" // শীমা
).sortedByDescending { it.length }

private val LEADING_FILLERS = Regex(
    "^(hey|hi|hello|ok|okay|oye|arre|are|suno|please|pls|well|so|now|ab|dekho|shono|bolchi|" +
    "\\u0939\\u0948\\s*\\u0924\\u094b\\s*(\\u092e\\u0948\\u0902)?|" + // है तो मैं / है तो
    "(\\u092e\\u0948\\u0902|\\u0939\\u092e)\\s*(\\u092c\\u094b\\u0932|\\u0915\\u0939)\\s*\\u0930\\u0939\\u093e\\s*(\\u0939\\u0942\\u0902)?|" + // मैं बोल रहा हूँ
    "\\u0924\\u094b|" + // तो
    "\\u0905\\u092c|" + // अब
    "\\u092f\\u093e\\u0930|" + // यार
    "\\u092d\\u093e\\u0908|" + // भाई
    "\\u0938\\u0941\\u0928\\u093f\\u090f|" + // सुनिए
    "\\u092a\\u094d\\u0932\\u0940\\u091c|" + // प्लीज
    "\\u0939\\u0947|" + // हे
    "\\u0939\\u093e\\u092f|" + // हाय
    "\\u0939\\u0947\\u0932\\u094b|" + // हेलो
    "\\u0905\\u0930\\u0947|" + // अरे
    "\\u0938\\u0941\\u0928\\u094b|" + // सुनो
    "\\u0913\\u090f|" + // ओए
    "\\u098f\\u0987|" + // এই
    "\\u0993\\u09b9\\u09c7|" + // ওহে
    "\\u09b6\\u09cb\\u09a8\\u09cb|" + // শোনো
    "\\u09b9\\u09cd\\u09af\\u09be\\u09b2\\u09cb|" + // হ্যালো
    "\\u09b9\\u09c7|" + // হে
    "\\u0986\\u09b0\\u09c7" + // আরে
    ")($|\\s+)",
    RegexOption.IGNORE_CASE
)

private val TRAILING_HONORIFICS = Regex(
    "^(\\s*(\\u091c\\u0940|ji|jee|di|\\u09a6\\u09bf|didi))(?=$|\\s+|[,!?])",
    RegexOption.IGNORE_CASE
)

private val CALLER_FILLERS = Regex(
    "^(\\s*(\\u092c\\u094b\\u0932|\\u0915\\u0939)\\s*\\u0930\\u0939\\u093e\\s*(\\u0939\\u0942\\u0902|\\u0939\\u0948|\\u0939\\u094b)?\\s*(\\u0915\\u093f)?|bol\\s*raha\\s*hoon\\s*(ki)?|bolchi|shon|shono)\\s*",
    RegexOption.IGNORE_CASE
)

/**
 * In sleep mode Seema only reacts when called by name. Returns what was said after "Seema"
 * ("" if only her name was said), or null if her name was not in [heard] at all.
 * Examples: "Seema, time kya hua?" -> "time kya hua?"; "hey सीमा" -> ""; "TV ki awaaz" -> null.
 */
fun afterWakeWord(heard: String): String? {
    val text = heard.trim()
    val lower = text.lowercase()
    var best: IntRange? = null
    for (w in WAKE_WORDS) {
        val m = Regex("(^|[^\\p{L}\\p{M}])" + Regex.escape(w.lowercase()) + "(?=$|[^\\p{L}\\p{M}])").find(lower) ?: continue
        val range = (m.range.first + m.groupValues[1].length)..m.range.last
        if (best == null || range.first < best.first || (range.first == best.first && range.last > best.last)) {
            best = range
        }
    }
    val hit = best ?: return null
    // The name should open the sentence (allowing fillers/conversational prefixes before it).
    var before = text.substring(0, hit.first).trim()
    while (LEADING_FILLERS.containsMatchIn(before)) {
        before = before.replace(LEADING_FILLERS, "").trim()
    }
    before = before.trimEnd(',', '!', '.', '?')
    val beforeWords = before.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (beforeWords.size > 3) return null

    var after = text.substring(hit.last + 1).trim().trimStart(',', '!', '.', '?', ' ', '-')
    after = after.replace(TRAILING_HONORIFICS, "").trim().trimStart(',', '!', '.', '?', ' ', '-')
    after = after.replace(CALLER_FILLERS, "").trim().trimStart(',', '!', '.', '?', ' ', '-')
    return after
}
