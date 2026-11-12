package com.raju.aiassistant.domain.sms

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * "Mobile ka inbox check karo, credit wale kitne message aaye?" — counts the phone's own SMS (and the
 * bank's money-in / money-out messages with their totals) for today, yesterday, the week or the month.
 * Everything is read on the phone; no SMS text goes to the AI or the internet, OTP messages are skipped
 * and message bodies are never read out, only the bank's sender, the amount and the day.
 */
enum class InboxPeriod(val days: Int) { TODAY(1), YESTERDAY(2), WEEK(7), MONTH(30) }
enum class InboxKind { ALL, CREDIT, DEBIT }

data class InboxQuery(val period: InboxPeriod, val kind: InboxKind)

private val INBOX_PLACE = Regex(
    "inbox|in box|इनबॉक्स|इनबॉक्स|\\bsms\\b|एसएमएस|एस एम एस|bank\\s+(?:ka|ke|से|ke\\s+)?\\s*(?:message|msg|sms)|बैंक",
    RegexOption.IGNORE_CASE,
)
private val MESSAGE_WORD = Regex("message|messages|msg|massage|मैसेज|मेसेज|मेसेज|संदेश|sms", RegexOption.IGNORE_CASE)
private val PHONE_WORD = Regex("mobile|phone|फ़ोन|फोन|मोबाइल", RegexOption.IGNORE_CASE)
private val CREDIT_ASK = Regex("credit|क्रेडिट|jama|जमा|paisa aaya|paise aaye|पैसा आया|पैसे आए|received|refund|रिफंड", RegexOption.IGNORE_CASE)
private val DEBIT_ASK = Regex("debit|डेबिट|kata|kate|kaata|कटा|कटे|kharch|खर्च|spent", RegexOption.IGNORE_CASE)
private val ASK = Regex(
    "check|चेक|dekho|dekh|देखो|देख|batao|bata|बताओ|बता|kitna|kitne|kitni|कितना|कितने|कितनी|padho|पढ़ो|पढो|sunao|सुनाओ|aaya|aaye|aayi|आया|आए|आई|kya|क्या",
    RegexOption.IGNORE_CASE,
)
private val SENDING = Regex("bhejo|bhej do|likho|send|भेजो|भेज दो|लिखो|delete|डिलीट|mitao|मिटाओ", RegexOption.IGNORE_CASE)

/** null when the sentence is not a question about the phone's SMS inbox. */
fun parseInboxQuestion(text: String): InboxQuery? {
    val t = asciiDigitsOf(text).trim()
    if (t.isEmpty() || t.length > 160 || SENDING.containsMatchIn(t) || !ASK.containsMatchIn(t)) return null
    val hasMessage = MESSAGE_WORD.containsMatchIn(t)
    val credit = CREDIT_ASK.containsMatchIn(t)
    val debit = DEBIT_ASK.containsMatchIn(t)
    val aboutInbox = INBOX_PLACE.containsMatchIn(t) ||
        (hasMessage && (credit || debit)) ||
        (hasMessage && PHONE_WORD.containsMatchIn(t))
    if (!aboutInbox) return null
    val kind = when {
        credit && !debit -> InboxKind.CREDIT
        debit && !credit -> InboxKind.DEBIT
        else -> InboxKind.ALL
    }
    val l = t.lowercase()
    val period = when {
        Regex("\\b(aaj|today)\\b|${hindiWord("आज")}").containsMatchIn(l) -> InboxPeriod.TODAY
        Regex("\\b(kal|yesterday)\\b|${hindiWord("कल")}").containsMatchIn(l) -> InboxPeriod.YESTERDAY
        Regex("mahin|month|महीन").containsMatchIn(l) -> InboxPeriod.MONTH
        else -> InboxPeriod.WEEK
    }
    return InboxQuery(period, kind)
}

/** A whole Devanagari word: Java's \\b does not see Hindi letters, and "कल" is inside "निकला". */
private fun hindiWord(w: String) = "(?<![\\p{L}\\p{M}])$w(?![\\p{L}\\p{M}])"

/** Where the search starts: the start of today, of yesterday, or N days back. */
fun inboxSince(period: InboxPeriod, today: LocalDate, zone: ZoneId): Long = when (period) {
    InboxPeriod.TODAY -> today.atStartOfDay(zone)
    InboxPeriod.YESTERDAY -> today.minusDays(1).atStartOfDay(zone)
    else -> today.minusDays(period.days.toLong() - 1).atStartOfDay(zone)
}.toInstant().toEpochMilli()

data class BankLine(val sender: String, val paise: Long, val direction: MoneyDirection, val atMillis: Long)

data class InboxSummary(val total: Int, val credits: List<BankLine>, val debits: List<BankLine>)

fun summarizeInbox(messages: List<SmsMessage>, query: InboxQuery, today: LocalDate, zone: ZoneId): InboxSummary {
    val from = inboxSince(query.period, today, zone)
    val until = if (query.period == InboxPeriod.YESTERDAY) today.atStartOfDay(zone).toInstant().toEpochMilli() else Long.MAX_VALUE
    val inRange = messages.filter { it.atMillis in from until until }
    val bank = inRange.filter { !isOtp(it.body) }.mapNotNull { m ->
        val body = asciiDigitsOf(m.body)
        val paise = transactionPaise(body) ?: return@mapNotNull null
        val dir = directionOf(body) ?: return@mapNotNull null
        BankLine(m.sender, paise, dir, m.atMillis)
    }.sortedByDescending { it.atMillis }
    return InboxSummary(inRange.size, bank.filter { it.direction == MoneyDirection.CREDIT }, bank.filter { it.direction == MoneyDirection.DEBIT })
}

/** What Seema says: counts and totals, then the newest few bank lines (sender, amount, day). */
fun spokenInboxAnswer(query: InboxQuery, s: InboxSummary, zone: ZoneId): String {
    val whenText = when (query.period) {
        InboxPeriod.TODAY -> "आज"
        InboxPeriod.YESTERDAY -> "कल"
        InboxPeriod.WEEK -> "पिछले 7 दिनों में"
        InboxPeriod.MONTH -> "पिछले 30 दिनों में"
    }
    fun day(ms: Long): String {
        val d = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        return "${d.dayOfMonth} ${HINDI_MONTHS[d.monthValue - 1]}"
    }
    fun lines(list: List<BankLine>) = list.take(3).joinToString("; ") { "₹${rupees(it.paise)} — ${it.sender}, ${day(it.atMillis)}" }
    fun sum(list: List<BankLine>) = "₹${rupees(list.sumOf { it.paise })}"
    return when (query.kind) {
        InboxKind.CREDIT ->
            if (s.credits.isEmpty()) "$whenText पैसे जमा होने (credit) का कोई बैंक संदेश नहीं मिला। कुल ${s.total} SMS देखे।"
            else "$whenText पैसे जमा होने के ${s.credits.size} संदेश आए, कुल ${sum(s.credits)}। सबसे नए: ${lines(s.credits)}।"
        InboxKind.DEBIT ->
            if (s.debits.isEmpty()) "$whenText पैसे कटने (debit) का कोई बैंक संदेश नहीं मिला। कुल ${s.total} SMS देखे।"
            else "$whenText पैसे कटने के ${s.debits.size} संदेश आए, कुल ${sum(s.debits)}। सबसे नए: ${lines(s.debits)}।"
        InboxKind.ALL -> buildString {
            append("$whenText कुल ${s.total} SMS आए।")
            if (s.credits.isEmpty() && s.debits.isEmpty()) append(" इनमें पैसे के लेन-देन का कोई बैंक संदेश नहीं है।")
            else {
                append(" जमा (credit): ${s.credits.size} संदेश, कुल ${sum(s.credits)}।")
                append(" कटे (debit): ${s.debits.size} संदेश, कुल ${sum(s.debits)}।")
                s.credits.firstOrNull()?.let { append(" आख़िरी जमा: ₹${rupees(it.paise)} — ${it.sender}, ${day(it.atMillis)}।") }
            }
        }
    }
}
