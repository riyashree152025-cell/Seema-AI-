package com.raju.aiassistant.domain.voice

/**
 * The short window after Seema's reply in which Raju may answer without "Hey Seema": [WINDOW_MS] (strictly 7 seconds)
 * after she stops speaking (a question of hers can ask for longer). A sentence begun inside the window still counts
 * while it is being recognised ([RESULT_GRACE_MS]). Closed at once by "chup raho" or by talk to another assistant.
 */
class ConversationSessionTimer(private val now: () -> Long) {
    private var openUntil = 0L

    /** Her reply ended: open the window for [WINDOW_MS] (strictly 7 seconds, never shortens a longer one already open). */
    fun openAfterReply() { openUntil = maxOf(openUntil, now() + WINDOW_MS) }

    /** A question of hers that may take longer to answer ("किसको भेजना है?"). */
    fun openFor(ms: Long) { openUntil = maxOf(openUntil, now() + ms) }

    fun close() { openUntil = 0L }

    fun cancel() { close() }

    fun isOpen(): Boolean = now() < openUntil

    /** A result heard now from listening that began at [listenStartedAt]: still for her? */
    fun accepts(listenStartedAt: Long): Boolean = isOpen() || (openUntil > 0 && listenStartedAt <= openUntil && now() < openUntil + RESULT_GRACE_MS)

    /** When the window closes (0 = closed). */
    fun until(): Long = openUntil

    companion object {
        const val WINDOW_MS = 7_000L
        const val RESULT_GRACE_MS = 6_000L
    }
}
