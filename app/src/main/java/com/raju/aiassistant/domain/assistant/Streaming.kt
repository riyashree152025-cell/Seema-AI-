package com.raju.aiassistant.domain.assistant

private val SENTENCE_END = Regex("[.!?।](?=\\s)")

/**
 * Words that cannot end a finished sentence: "Rahul ko", "mujhe Delhi se", "ye wala", "call karo aur".
 * A pause after one of them is a breath in the middle of the sentence, not the end of it.
 */
private val UNFINISHED_ENDINGS = Regex(
    "(?:^|\\s)(?:aur|lekin|par|ki|ke|ka|ko|se|mein|me|men|ne|pe|to|toh|matlab|jaise|liye|wala|wali|wale|bhi|kya|kaise|kaisa|kaisi|kab|kahan|" +
        "and|but|or|so|because|the|a|an|for|with|about|of|at|in|on|to|" +
        "और|लेकिन|पर|कि|के|का|की|को|से|में|ने|पे|तो|मतलब|जैसे|लिए|वाला|वाली|वाले|भी|क्या|कैसे|कैसा|कैसी|कब|कहाँ|कहां)[\\s,.…]*$",
    RegexOption.IGNORE_CASE,
)

/** True when [text] ends in a word that asks for more words after it. */
fun isUnfinished(text: String): Boolean = UNFINISHED_ENDINGS.containsMatchIn(text.trim())

private val CLAUSE_END = Regex("[,;:—–](?=\\s)")

/**
 * The first complete sentence of a reply that is still being streamed, so Seema can start speaking
 * before the rest is written. Never reaches into an `<action>` tag, and needs [minChars] so a lone
 * "Haan." does not leave an odd pause. A long first sentence (over [longChars] with no full stop yet)
 * is not waited for: its first clause, or else all its complete words, are spoken at once. Null while
 * there is nothing worth saying yet.
 */
fun speakableHead(partial: CharSequence, minChars: Int = 12, longChars: Int = 40): String? {
    val s = partial.toString()
    val text = s.indexOf('<').let { if (it < 0) s else s.substring(0, it) }
    val end = SENTENCE_END.findAll(text).firstOrNull { it.range.first + 1 >= minChars }
    if (end != null) return text.substring(0, end.range.first + 1).trim().takeIf { it.isNotEmpty() }
    if (s.contains('<') || text.trim().length <= longChars) return null
    // The last word may still be arriving: only words followed by a space count.
    val clause = CLAUSE_END.findAll(text).lastOrNull { it.range.first + 1 >= minChars }
    val cut = clause?.range?.first?.plus(1) ?: text.trimEnd().let { t -> if (t.length < text.length) t.length else t.lastIndexOf(' ') }
    if (cut < minChars) return null
    return text.substring(0, cut).trim().takeIf { it.length >= minChars }
}

/** What is left to say after [head] was already spoken from the start of [full]. */
fun remainderAfter(full: String, head: String): String {
    val i = full.indexOf(head)
    return if (i >= 0) full.substring(i + head.length).trim() else full
}

/** Response time of one reply, measured from the moment the user's message was ready (typed or recognised). */
data class TurnLatency(
    val at: Long,
    val provider: String,
    val model: String,
    /** First text from the AI. */
    val firstTextMs: Long?,
    /** First audible word (null when the reply was not spoken). */
    val firstAudioMs: Long?,
    /** Whole reply received. */
    val totalMs: Long,
    val streamed: Boolean,
    val ok: Boolean,
)

/** Nearest-rank percentile (p in 0..100) of [values]; null when empty. */
fun percentile(values: List<Long>, p: Int): Long? {
    if (values.isEmpty()) return null
    val sorted = values.sorted()
    val rank = kotlin.math.ceil(p / 100.0 * sorted.size).toInt().coerceIn(1, sorted.size)
    return sorted[rank - 1]
}

/** "P50 1.2 s · P95 2.8 s (12 replies)" for a list of milliseconds, or null when there are none. */
fun latencySummary(values: List<Long>): String? {
    val p50 = percentile(values, 50) ?: return null
    val p95 = percentile(values, 95) ?: return null
    fun s(ms: Long) = "%.1f s".format(java.util.Locale.ROOT, ms / 1000.0)
    return "P50 ${s(p50)} · P95 ${s(p95)} (${values.size} replies)"
}
