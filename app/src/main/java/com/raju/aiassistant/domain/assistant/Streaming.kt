package com.raju.aiassistant.domain.assistant

private val SENTENCE_END = Regex("[.!?।](?=\\s)")

/**
 * The first complete sentence of a reply that is still being streamed, so Seema can start speaking
 * before the rest is written. Never reaches into an `<action>` tag, and needs [minChars] so a lone
 * "Haan." does not leave an odd pause. Null while no sentence is complete yet.
 */
fun speakableHead(partial: CharSequence, minChars: Int = 12): String? {
    val s = partial.toString()
    val text = s.indexOf('<').let { if (it < 0) s else s.substring(0, it) }
    val end = SENTENCE_END.findAll(text).firstOrNull { it.range.first + 1 >= minChars } ?: return null
    return text.substring(0, end.range.first + 1).trim().takeIf { it.isNotEmpty() }
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
