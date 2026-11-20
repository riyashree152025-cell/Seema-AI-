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
enum class InboxPeriod(val days: Int) { TODAY(1), YESTERDAY(2), WEEK(7), MONTH(30), DAY(1) }
enum class InboxKind { ALL, CREDIT, DEBIT }

/** [date]: the one day asked for ("15 तारीख का"), with [InboxPeriod.DAY]. */
data class InboxQuery(val period: InboxPeriod, val kind: InboxKind, val date: LocalDate? = null)

private val MONTH_WORDS = listOf(
    "jan|january|जनवरी", "feb|february|फरवरी|फ़रवरी", "mar|march|मार्च", "apr|april|अप्रैल|अप्रेल", "may|मई", "jun|june|जून",
    "jul|july|जुलाई", "aug|august|अगस्त", "sep|sept|september|सितंबर|सितम्बर", "oct|october|अक्टूबर|अक्तूबर",
    "nov|november|नवंबर|नवम्बर", "dec|december|दिसंबर|दिसम्बर",
)
private val DATE_WORD = "(?:tarikh|tareekh|tarik|tareek|tarekh|तारीख|तारीख़|तारिख|तरीख|date|डेट|তারিখ|th|st|nd|rd)"
private val DAY_OF_MONTH = Regex("(?<!\\d)([1-9]|[12]\\d|3[01])\\s*$DATE_WORD(?![a-z])", RegexOption.IGNORE_CASE)
private val DAY_AND_MONTH = Regex(
    "(?<!\\d)([1-9]|[12]\\d|3[01])\\s*(${MONTH_WORDS.joinToString("|")})(?![a-z\\p{M}])",
    RegexOption.IGNORE_CASE,
)

/**
 * The day named in a sentence: "15 तारीख", "15 tarikh", "15th", "15 नवंबर", "परसों". A day of the month
 * not yet reached this month means last month ("28 तारीख" on the 16th = the 28th of last month).
 * null when no single day is named.
 */
fun namedDay(text: String, today: LocalDate): LocalDate? {
    val t = asciiDigitsOf(text).lowercase()
    DAY_AND_MONTH.find(t)?.let { m ->
        val day = m.groupValues[1].toInt()
        val month = MONTH_WORDS.indexOfFirst { Regex("^(?:$it)$").matches(m.groupValues[2]) } + 1
        if (month in 1..12) {
            var d = runCatching { LocalDate.of(today.year, month, day) }.getOrNull() ?: return null
            if (d.isAfter(today)) d = runCatching { LocalDate.of(today.year - 1, month, day) }.getOrNull() ?: return null
            return d
        }
    }
    DAY_OF_MONTH.find(t)?.let { m ->
        val day = m.groupValues[1].toInt()
        var base = today.withDayOfMonth(1)
        if (day > today.dayOfMonth) base = base.minusMonths(1)
        return if (day <= base.lengthOfMonth()) base.withDayOfMonth(day) else null
    }
    if (Regex("\\b(parso|parson|parsoon)\\b|${hindiWord("परसों")}|${hindiWord("परसो")}").containsMatchIn(t)) return today.minusDays(2)
    return null
}

/** A date said as a date ("15 तारीख", "15 नवंबर"): its number is not an amount of money. */
fun withoutNamedDates(text: String): String =
    DAY_AND_MONTH.replace(DAY_OF_MONTH.replace(asciiDigitsOf(text), " "), " ")

private val INBOX_PLACE = Regex(
    "inbox|in box|इनबॉक्स|इनबॉक्स|ইনবক্স|এসএমএস|ব্যাংক|ব্যাঙ্ক|\\bsms\\b|एसएमएस|एस एम एस|bank\\s+(?:ka|ke|से|ke\\s+)?\\s*(?:message|msg|sms)|बैंक",
    RegexOption.IGNORE_CASE,
)
private val MESSAGE_WORD = Regex("message|messages|msg|massage|मैसेज|मेसेज|मेसेज|संदेश|sms|মেসেজ|ম্যাসেজ|বার্তা|এসএমএস", RegexOption.IGNORE_CASE)
private val PHONE_WORD = Regex("mobile|phone|फ़ोन|फोन|मोबाइल|মোবাইল|ফোন", RegexOption.IGNORE_CASE)
private val CREDIT_ASK = Regex("credit|क्रेडिट|ক্রেডিট|জমা|টাকা এসেছে|jama|जमा|paisa aaya|paise aaye|पैसा आया|पैसे आए|received|refund|रिफंड", RegexOption.IGNORE_CASE)
private val DEBIT_ASK = Regex("debit|डेबिट|ডেবিট|কাটা|কেটেছে|খরচ|kata|kate|kaata|कटा|कटे|kharch|खर्च|spent", RegexOption.IGNORE_CASE)
private val ASK = Regex(
    "দেখো|দেখ|বলো|কত|কতো|কতটা|চেক|হিসাব|track|ट्रैक|hisab|hisaab|हिसाब|check|चेक|dekho|dekh|देखो|देख|batao|bata|बताओ|बता|kitna|kitne|kitni|कितना|कितने|कितनी|padho|पढ़ो|पढो|sunao|सुनाओ|aaya|aaye|aayi|आया|आए|आई|kya|क्या",
    RegexOption.IGNORE_CASE,
)
private val SENDING = Regex("bhejo|bhej do|likho|send|भेजो|भेज दो|लिखो|delete|डिलीट|mitao|मिटाओ", RegexOption.IGNORE_CASE)

/** null when the sentence is not a question about the phone's SMS inbox. */
fun parseInboxQuestion(text: String, today: LocalDate = LocalDate.now()): InboxQuery? {
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
    namedDay(t, today)?.let { d ->
        return when (d) {
            today -> InboxQuery(InboxPeriod.TODAY, kind, d)
            today.minusDays(1) -> InboxQuery(InboxPeriod.YESTERDAY, kind, d)
            else -> InboxQuery(InboxPeriod.DAY, kind, d)
        }
    }
    val period = when {
        Regex("\\b(aaj|today)\\b|${hindiWord("आज")}|${hindiWord("আজ")}|${hindiWord("আজকে")}").containsMatchIn(l) -> InboxPeriod.TODAY
        YESTERDAY_SAID.containsMatchIn(l) -> InboxPeriod.YESTERDAY
        Regex("mahin|month|महीन|মাস").containsMatchIn(l) -> InboxPeriod.MONTH
        else -> InboxPeriod.WEEK
    }
    return InboxQuery(period, kind)
}

/**
 * "Yesterday" as the recogniser writes it: "कल", joined to the next word ("कलका", "कलके"), "काल का" /
 * "कॉल का" / "call ka" (a short "kal" heard long, only when "का/के/की" follows), "kal", "बीते कल",
 * "पिछले दिन", Bengali "গতকাল" / "কাল".
 */
internal val YESTERDAY_SAID = Regex(
    "\\b(kal|kall|yesterday)\\b|\\b(kaal|call)\\s+(ka|ke|ki)\\b|${hindiWord("कल")}|${hindiWord("कल्ह")}|" +
        "(?<![\\p{L}\\p{M}])कल(?:का|के|की)(?![\\p{L}\\p{M}])|(?<![\\p{L}\\p{M}])(?:काल|कॉल|कोल)\\s+(?:का|के|की)(?![\\p{L}\\p{M}])|" +
        "पिछले\\s+दिन|${hindiWord("গতকাল")}|${hindiWord("কাল")}|${hindiWord("কালকের")}",
)

/** A whole Devanagari word: Java's \\b does not see Hindi letters, and "कल" is inside "निकला". */
internal fun hindiWord(w: String) = "(?<![\\p{L}\\p{M}])$w(?![\\p{L}\\p{M}])"

/** Where the search starts: the start of today, of yesterday, or N days back. */
fun inboxSince(period: InboxPeriod, today: LocalDate, zone: ZoneId, date: LocalDate? = null): Long = when (period) {
    InboxPeriod.TODAY -> today.atStartOfDay(zone)
    InboxPeriod.DAY -> (date ?: today).atStartOfDay(zone)
    InboxPeriod.YESTERDAY -> today.minusDays(1).atStartOfDay(zone)
    else -> today.minusDays(period.days.toLong() - 1).atStartOfDay(zone)
}.toInstant().toEpochMilli()

data class BankLine(val sender: String, val paise: Long, val direction: MoneyDirection, val atMillis: Long)

data class InboxSummary(val total: Int, val credits: List<BankLine>, val debits: List<BankLine>)

fun summarizeInbox(messages: List<SmsMessage>, query: InboxQuery, today: LocalDate, zone: ZoneId): InboxSummary {
    val from = inboxSince(query.period, today, zone, query.date)
    val until = when (query.period) {
        InboxPeriod.YESTERDAY -> today.atStartOfDay(zone).toInstant().toEpochMilli()
        InboxPeriod.DAY -> (query.date ?: today).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        else -> Long.MAX_VALUE
    }
    val inRange = messages.filter { it.atMillis in from until until }
    val bank = inRange.filter { !isOtp(it.body) }.mapNotNull { m ->
        val body = asciiDigitsOf(m.body)
        val paise = transactionPaise(body) ?: return@mapNotNull null
        val dir = directionOf(body) ?: return@mapNotNull null
        BankLine(m.sender, paise, dir, m.atMillis)
    }.sortedByDescending { it.atMillis }
    return InboxSummary(inRange.size, bank.filter { it.direction == MoneyDirection.CREDIT }, bank.filter { it.direction == MoneyDirection.DEBIT })
}

/**
 * What Seema says: counts and totals, then the amounts themselves. For one day ("15 तारीख का कितना कितना
 * डेबिट, कितना क्रेडिट") every amount of that day is said (up to [MAX_SPOKEN_LINES] each); for longer
 * periods the newest few.
 */
fun spokenInboxAnswer(query: InboxQuery, s: InboxSummary, zone: ZoneId): String {
    val oneDay = query.period == InboxPeriod.TODAY || query.period == InboxPeriod.YESTERDAY || query.period == InboxPeriod.DAY
    val whenText = when (query.period) {
        InboxPeriod.TODAY -> "आज"
        InboxPeriod.YESTERDAY -> "कल"
        InboxPeriod.WEEK -> "पिछले 7 दिनों में"
        InboxPeriod.MONTH -> "पिछले 30 दिनों में"
        InboxPeriod.DAY -> query.date?.let { "${it.dayOfMonth} ${HINDI_MONTHS[it.monthValue - 1]} को" } ?: "उस दिन"
    }
    fun day(ms: Long): String {
        val d = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        return "${d.dayOfMonth} ${HINDI_MONTHS[d.monthValue - 1]}"
    }
    fun lines(list: List<BankLine>): String {
        val n = if (oneDay) MAX_SPOKEN_LINES else 3
        val said = list.take(n).joinToString("; ") { if (oneDay) "₹${rupees(it.paise)} (${it.sender})" else "₹${rupees(it.paise)} — ${it.sender}, ${day(it.atMillis)}" }
        return if (list.size > n) "$said; और ${list.size - n}" else said
    }
    fun sum(list: List<BankLine>) = "₹${rupees(list.sumOf { it.paise })}"
    val label = if (oneDay) "" else "सबसे नए: "
    return when (query.kind) {
        InboxKind.CREDIT ->
            if (s.credits.isEmpty()) "$whenText पैसे जमा होने (credit) का कोई बैंक संदेश नहीं मिला। कुल ${s.total} SMS देखे।"
            else "$whenText पैसे जमा होने के ${s.credits.size} संदेश आए, कुल ${sum(s.credits)}। $label${lines(s.credits)}।"
        InboxKind.DEBIT ->
            if (s.debits.isEmpty()) "$whenText पैसे कटने (debit) का कोई बैंक संदेश नहीं मिला। कुल ${s.total} SMS देखे।"
            else "$whenText पैसे कटने के ${s.debits.size} संदेश आए, कुल ${sum(s.debits)}। $label${lines(s.debits)}।"
        InboxKind.ALL -> buildString {
            append("$whenText कुल ${s.total} SMS आए।")
            if (s.credits.isEmpty() && s.debits.isEmpty()) append(" इनमें पैसे के लेन-देन का कोई बैंक संदेश नहीं है।")
            else if (oneDay) {
                append(" कटे (debit): ${s.debits.size} बार, कुल ${sum(s.debits)}")
                append(if (s.debits.isEmpty()) "।" else " — ${lines(s.debits)}।")
                append(" जमा (credit): ${s.credits.size} बार, कुल ${sum(s.credits)}")
                append(if (s.credits.isEmpty()) "।" else " — ${lines(s.credits)}।")
            } else {
                append(" जमा (credit): ${s.credits.size} संदेश, कुल ${sum(s.credits)}।")
                append(" कटे (debit): ${s.debits.size} संदेश, कुल ${sum(s.debits)}।")
                s.credits.firstOrNull()?.let { append(" आख़िरी जमा: ₹${rupees(it.paise)} — ${it.sender}, ${day(it.atMillis)}।") }
            }
        }
    }
}

/** Amounts said one by one for a single day, per side; more are counted ("और 4"). */
const val MAX_SPOKEN_LINES = 8
