package com.raju.aiassistant.domain.sms

import com.raju.aiassistant.domain.language.SeemaLanguage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Money questions answered from the phone's own bank SMS (and the UPI apps' "Rahul paid you ₹500"
 * notifications Seema noted), for any stretch of time:
 *  - "कल कितना कितना पैसा आया?" → only the amounts: "कल 2 पेमेंट आए: 2,400 और 3,630 रुपये — कुल 6,030।"
 *  - "1 घंटे में / 2 घंटे में / आज सुबह / कल शाम / 15 तारीख को कितना आया?" → the same for that time;
 *  - "1 घंटे में मैंने किसको पैसा भेजा?" → the amounts with the person, when the bank names them;
 *  - "3630 किसका आया?" → who sent it (or "the bank's SMS has no name").
 * Nothing is sent anywhere: SMS bodies stay on the phone, OTP messages are skipped, account numbers are never said.
 */
enum class MoneyFlow { IN, OUT, BOTH }

enum class DayPart { MORNING, AFTERNOON, EVENING, NIGHT }

/** Which stretch of time was asked about. */
sealed interface MoneyTime {
    /** "पिछले 2 घंटे", "आधे घंटे में", "30 मिनट में": the last [minutes] up to now. */
    data class LastMinutes(val minutes: Int) : MoneyTime

    /** A day ("आज", "कल", "15 तारीख") or a part of it ("कल शाम"). */
    data class Day(val date: LocalDate, val part: DayPart? = null) : MoneyTime
}

sealed interface MoneyAsk {
    /** "कितना कितना आया" — amounts only; [withNames] for "किसको भेजा / किसने भेजा". */
    data class Amounts(val flow: MoneyFlow, val time: MoneyTime, val withNames: Boolean = false, val totalOnly: Boolean = false) : MoneyAsk

    /** "3630 किसका आया?" — who that payment was from (or to). [time] null: the last [WHO_SEARCH_DAYS] days. */
    data class Who(val paise: Long, val flow: MoneyFlow, val time: MoneyTime? = null) : MoneyAsk
}

/** How far back "3630 किसका आया?" looks when no day is named. */
const val WHO_SEARCH_DAYS = 7

/** Amounts said one by one; more are counted with the total ("और 4 पेमेंट"). */
const val MAX_SPOKEN_AMOUNTS = 10

/** Parts of the day, in hours of the clock: सुबह 5–12, दोपहर 12–16, शाम 16–20, रात 20–5 (next morning). */
fun hoursOf(part: DayPart): Pair<Int, Int> = when (part) {
    DayPart.MORNING -> 5 to 12
    DayPart.AFTERNOON -> 12 to 16
    DayPart.EVENING -> 16 to 20
    DayPart.NIGHT -> 20 to 29 // until 5 the next morning
}

// ---------------------------------------------------------------------------------------- understanding

private fun word(w: String) = "(?<![\\p{L}\\p{M}])(?:$w)(?![\\p{L}\\p{M}])"

private val MONEY = Regex(
    word("paisa|paise|paisa|rupay|rupaye|rupee|rupees|rs|payment|payments|amount|upi|पैसा|पैसे|पेसा|रुपया|रुपये|रुपए|रूपये|पेमेंट|पेमेन्ट|अमाउंट|टाका|টাকা|পেমেন্ট|পয়সা"),
    RegexOption.IGNORE_CASE,
)
private val CAME = Regex(
    word(
        "aaya|aaye|aayi|aya|aye|ayi|aaya hai|jama|credit|credited|received|mila|mile|आया|आए|आये|आई|आयी|जमा|क्रेडिट|मिला|मिले|" +
            "এসেছে|এলো|এল|আসছে|জমা|ক্রেডিট|পেলাম|পেয়েছি",
    ),
    RegexOption.IGNORE_CASE,
)
private val WENT = Regex(
    word(
        "bheja|bheje|gaya|gaye|kata|kate|kaata|debit|debited|sent|paid|diya|diye|भेजा|भेजे|भेजी|गया|गए|गये|कटा|कटे|डेबिट|दिया|दिए|दिये|" +
            "পাঠিয়েছি|পাঠালাম|পাঠিয়েছে|গেছে|গেল|কেটেছে|কাটা|ডেবিট|দিয়েছি",
    ),
    RegexOption.IGNORE_CASE,
)
private val HOW_MUCH = Regex(
    word("kitna|kitne|kitni|kitna kitna|कितना|कितने|कितनी|কত|কতো|কতটা|how much|check|चेक|batao|बताओ|बता|dekho|देखो|হিসাব|हिसाब|hisab|hisaab|বলো|দেখো"),
    RegexOption.IGNORE_CASE,
)
/** "किसने / किसका" — from whom (money in). */
private val WHO_FROM = Regex(word("kisne|kiska|kiske|kiski|kisse|kis se|किसने|किसका|किसके|किसकी|किससे|किस से|कौन|kaun|whose|who|কার|কার থেকে|কে পাঠাল|কে পাঠিয়েছে|কে দিল|কে দিয়েছে"), RegexOption.IGNORE_CASE)
/** Words that are about money by themselves (unlike "आया" or "भेजा"). */
private val MONEY_VERB = Regex(word("jama|credit|credited|debit|debited|kata|kate|kaata|जमा|क्रेडिट|डेबिट|कटा|कटे|জমা|ক্রেডিট|ডেবিট|কেটেছে"), RegexOption.IGNORE_CASE)
/** "किसको / किसे" — to whom (money out). */
private val WHO_TO = Regex(word("kisko|kise|kisku|किसको|किसे|किस को|to whom|কাকে|কাকে কাকে"), RegexOption.IGNORE_CASE)
/** Messages, inbox: the older "SMS count" report answers those (it also names the bank). */
private val INBOX_TALK = Regex("inbox|इनबॉक्स|ইনবক্স|message|messages|msg|मैसेज|मेसेज|संदेश|sms|एसएमएस|মেসেজ|এসএমএস", RegexOption.IGNORE_CASE)
/** "सब मिला के बोलो", "टोटल कितना", "कुल कितना": only the total. */
private val TOTAL_ONLY = Regex(word("sab mila ke|sab milake|sab milakar|sab mila kar|total|kul|सब मिला के|सब मिलाके|सब मिलाकर|सब मिला कर|मिला के|मिलाकर|कुल|टोटल|টোটাল|মোট|সব মিলিয়ে|সব মিলে"), RegexOption.IGNORE_CASE)
/** An order, not a question ("Rahul ko 500 bhej do"). */
private val ORDER = Regex(word("bhej do|bhejo|bhejiye|bhejna hai|भेज दो|भेजो|भेजिए|भेजना है|send karo|पाठाओ|পাঠাও|পাঠিয়ে দাও|de do|दे दो"), RegexOption.IGNORE_CASE)

private val NUMBER_WORDS = mapOf(
    "ek" to 1.0, "एक" to 1.0, "do" to 2.0, "दो" to 2.0, "teen" to 3.0, "तीन" to 3.0, "char" to 4.0, "chaar" to 4.0, "चार" to 4.0,
    "paanch" to 5.0, "panch" to 5.0, "पांच" to 5.0, "पाँच" to 5.0, "chhe" to 6.0, "che" to 6.0, "छह" to 6.0, "छः" to 6.0, "saat" to 7.0, "सात" to 7.0,
    "aath" to 8.0, "आठ" to 8.0, "nau" to 9.0, "नौ" to 9.0, "das" to 10.0, "दस" to 10.0, "barah" to 12.0, "बारह" to 12.0,
    "dedh" to 1.5, "डेढ़" to 1.5, "डेढ" to 1.5, "dhai" to 2.5, "ढाई" to 2.5, "adha" to 0.5, "aadha" to 0.5, "aadhe" to 0.5, "आधा" to 0.5, "आधे" to 0.5,
    "এক" to 1.0, "দুই" to 2.0, "দু" to 2.0, "তিন" to 3.0, "চার" to 4.0, "পাঁচ" to 5.0, "ছয়" to 6.0, "সাত" to 7.0, "আট" to 8.0, "দশ" to 10.0,
    "দেড়" to 1.5, "আড়াই" to 2.5, "আধ" to 0.5, "আধা" to 0.5,
)
private val COUNT = "(\\d+(?:\\.\\d+)?|${NUMBER_WORDS.keys.sortedByDescending { it.length }.joinToString("|")})"
private val HOURS = Regex(
    "(?<![\\p{L}\\p{M}\\d])$COUNT\\s*(?:ghante|ghanta|ghanton|ghante ke|घंटे|घंटा|घंटों|घण्टे|घण्टा|hours|hour|hrs|hr|ঘণ্টা|ঘন্টা|ঘণ্টায়|ঘন্টায়)",
    RegexOption.IGNORE_CASE,
)
private val MINUTES = Regex(
    "(?<![\\p{L}\\p{M}\\d])$COUNT\\s*(?:minute|minutes|mins|min|मिनट|মিনিট|মিনিটে)",
    RegexOption.IGNORE_CASE,
)
/** "पिछले घंटे", "last hour" (no number): one hour. */
private val ONE_HOUR = Regex("(?:pichhle|pichle|पिछले|last|গত)\\s*(?:ghante|घंटे|hour|ঘণ্টা|ঘণ্টায়)", RegexOption.IGNORE_CASE)
/** "3 बजे", "10:30" — a clock time is not an amount. */
private val CLOCK_TIME = Regex("(?<!\\d)\\d{1,2}(?::\\d{2})?\\s*(?:baje|bje|बजे|বাজে|টায়|o'?clock|am|pm)(?![\\p{L}\\p{M}])|(?<!\\d)\\d{1,2}:\\d{2}(?!\\d)", RegexOption.IGNORE_CASE)

private val MORNING = Regex(word("subah|subha|sube|savere|morning|सुबह|सवेरे|सुबे|সকাল|সকালে"), RegexOption.IGNORE_CASE)
private val AFTERNOON = Regex(word("dopahar|dopehar|dupahar|afternoon|दोपहर|दुपहर|দুপুর|দুপুরে"), RegexOption.IGNORE_CASE)
private val EVENING = Regex(word("sham|shaam|evening|शाम|सांझ|সন্ধ্যা|সন্ধ্যায়|সন্ধে|বিকেল|বিকেলে|বিকাল"), RegexOption.IGNORE_CASE)
private val NIGHT = Regex(word("raat|rat|night|रात|রাত|রাতে"), RegexOption.IGNORE_CASE)
private val TODAY = Regex(word("aaj|aj|today|आज|আজ|আজকে"), RegexOption.IGNORE_CASE)

private fun countOf(raw: String): Double? = raw.toDoubleOrNull() ?: NUMBER_WORDS[raw.lowercase()]

/** The stretch of time named in [text], or null when none is named. */
fun moneyTimeOf(text: String, today: LocalDate): MoneyTime? {
    val t = asciiDigitsOf(text)
    MINUTES.find(t)?.let { m -> countOf(m.groupValues[1])?.let { n -> if (n > 0) return MoneyTime.LastMinutes(n.toInt().coerceIn(1, 72 * 60)) } }
    HOURS.find(t)?.let { m -> countOf(m.groupValues[1])?.let { n -> if (n > 0) return MoneyTime.LastMinutes((n * 60).toInt().coerceIn(1, 72 * 60)) } }
    if (ONE_HOUR.containsMatchIn(t)) return MoneyTime.LastMinutes(60)
    val part = when {
        MORNING.containsMatchIn(t) -> DayPart.MORNING
        AFTERNOON.containsMatchIn(t) -> DayPart.AFTERNOON
        EVENING.containsMatchIn(t) -> DayPart.EVENING
        NIGHT.containsMatchIn(t) -> DayPart.NIGHT
        else -> null
    }
    val day = namedDay(t, today)
        ?: if (YESTERDAY_SAID.containsMatchIn(t.lowercase())) today.minusDays(1) else null
        ?: if (TODAY.containsMatchIn(t)) today else null
    return when {
        day != null -> MoneyTime.Day(day, part)
        part != null -> MoneyTime.Day(today, part)
        else -> null
    }
}

/** The text without its times and dates, so "1 घंटे" or "15 तारीख" is never taken for an amount. */
private fun withoutTimes(text: String): String =
    withoutNamedDates(CLOCK_TIME.replace(MINUTES.replace(HOURS.replace(asciiDigitsOf(text), " "), " "), " "))

private val AMOUNT = Regex("(?<![\\d.,])(\\d{1,3}(?:,\\d{2,3})+(?:\\.\\d{1,2})?|\\d+(?:\\.\\d{1,2})?)(?![\\d])")

private fun paiseOf(raw: String): Long? {
    val parts = raw.replace(",", "").split('.')
    val r = parts[0].toLongOrNull() ?: return null
    val p = parts.getOrNull(1)?.padEnd(2, '0')?.take(2)?.toLongOrNull() ?: 0L
    return r * 100 + p
}

/**
 * The money question in [text], or null when it is not one. Questions about "messages / inbox" without a
 * time of day or hours are left to the inbox report; orders ("Rahul ko 500 bhej do") are never questions.
 */
fun parseMoneyQuestion(text: String, today: LocalDate = LocalDate.now()): MoneyAsk? {
    val t = asciiDigitsOf(text).trim()
    if (t.isEmpty() || t.length > 160 || ORDER.containsMatchIn(t)) return null
    // "सब मिला के बोलो" is "add it all up", not "मिला" (received).
    val came = CAME.containsMatchIn(TOTAL_ONLY.replace(t, " "))
    val went = WENT.containsMatchIn(t)
    val from = WHO_FROM.containsMatchIn(t)
    val to = WHO_TO.containsMatchIn(t)
    val money = MONEY.containsMatchIn(t)
    val time = moneyTimeOf(t, today)
    val amounts = AMOUNT.findAll(withoutTimes(t)).mapNotNull { paiseOf(it.groupValues[1]) }.filter { it >= 100 }.toList()

    // "3630 किसका आया?", "3630 rupaye kisne bheje", "3630 किसको भेजा?"
    if ((from || to) && amounts.size == 1 && (came || went || money)) {
        // "किसने भेजा" = who sent it (to me); "किसको भेजा" = whom I sent it to.
        val flow = when {
            to && !from -> MoneyFlow.OUT
            from && !to -> MoneyFlow.IN
            went && !came -> MoneyFlow.OUT
            came && !went -> MoneyFlow.IN
            else -> MoneyFlow.BOTH
        }
        return MoneyAsk.Who(amounts.single(), flow, time)
    }
    if (amounts.isNotEmpty()) return null // "500 aaya kya?" is the payment check
    if (!(came || went)) return null
    val asks = HOW_MUCH.containsMatchIn(t) || from || to || TOTAL_ONLY.containsMatchIn(t)
    if (!asks) return null
    val partOrHours = time is MoneyTime.LastMinutes || (time as? MoneyTime.Day)?.part != null
    // Clearly about money: a money word ("पैसा", "टाका", "payment"), a money verb ("जमा", "डेबिट"), or "कितना"
    // with hours / a part of the day ("1 घंटे में कितना आया"). "last call kiski aayi thi" is not.
    val aboutMoney = money || MONEY_VERB.containsMatchIn(t) || (partOrHours && HOW_MUCH.containsMatchIn(t)) || TOTAL_ONLY.containsMatchIn(t)
    if (!aboutMoney) return null
    // "मैसेज / इनबॉक्स" questions for a whole day keep the inbox report (SMS count and banks).
    if (INBOX_TALK.containsMatchIn(t) && !partOrHours && !from && !to) return null
    val flow = when {
        to -> MoneyFlow.OUT
        from -> MoneyFlow.IN
        came && !went -> MoneyFlow.IN
        went && !came -> MoneyFlow.OUT
        else -> MoneyFlow.BOTH
    }
    return MoneyAsk.Amounts(flow, time ?: MoneyTime.Day(today), withNames = from || to, totalOnly = TOTAL_ONLY.containsMatchIn(t) && !from && !to)
}

/**
 * "ठीक है रुको, सब मिला के बोलो" said right after a list of amounts: the same question, total only. Short, no
 * amount and no other question in it.
 */
fun asksTotalOnly(text: String): Boolean {
    val t = asciiDigitsOf(text).trim()
    return t.length <= 60 && TOTAL_ONLY.containsMatchIn(t) && AMOUNT.find(t) == null
}

/** "पैसा आए तो बताना", "koi payment aaye to bol dena": turn on the spoken money-in alert. */
fun asksMoneyAlert(text: String): Boolean {
    val t = text.trim()
    if (t.length > 100 || !MONEY.containsMatchIn(t)) return false
    return Regex(
        "(?:aaye|aaya|aye|aa jaye|aata hai|aati hai|ata hai|आए|आये|आया|आ जाए|आता है|आती है|আসে|এলে|আসলে)\\s*(?:to|toh|तो|তো|তাহলে)?\\s*(?:mujhe|मुझे|मेरे को|আমাকে)?\\s*" +
            "(?:bata|bol|batana|bolna|bata dena|bol dena|बताना|बता देना|बोलना|बोल देना|बोल के बताना|बोलकर बताना|बता|বলবে|বলে দিও|বলো|জানিও)",
        RegexOption.IGNORE_CASE,
    ).containsMatchIn(t)
}

// ---------------------------------------------------------------------------------------- the payments

/** One payment, from a bank SMS or a UPI app's notification. [who]: the person's name or UPI ID, when known. */
data class MoneyEvent(val paise: Long, val flow: MoneyFlow, val atMillis: Long, val who: String?)

/** A UPI app's notification, kept on the phone so "3630 किसका आया?" can name the person later. */
data class MoneyNote(val paise: Long, val flow: MoneyFlow, val atMillis: Long, val who: String?)

interface MoneyNotes {
    suspend fun add(note: MoneyNote)
    suspend fun since(atMillis: Long): List<MoneyNote>
}

class InMemoryMoneyNotes : MoneyNotes {
    private val list = mutableListOf<MoneyNote>()
    override suspend fun add(note: MoneyNote) { synchronized(list) { list += note } }
    override suspend fun since(atMillis: Long): List<MoneyNote> = synchronized(list) { list.filter { it.atMillis >= atMillis } }
}

/** A notification and the bank SMS of the same payment: the same amount within this time. */
const val SAME_PAYMENT_MS = 15 * 60_000L

// The person in a bank SMS. Banks write it in many ways; only clear patterns are trusted.
private val VPA = Regex("(?<![\\w.@-])([A-Za-z0-9][\\w.-]{1,40}@[A-Za-z][A-Za-z0-9]{1,20})(?![\\w@]|\\.[A-Za-z])")
private val UPI_PATH = Regex("UPI[/-](?:CR|DR|P2A|P2M|P2P)?[/-]?\\d{6,}[/-]([A-Za-z][A-Za-z .]{1,40}?)\\s*(?=[/-]|$)", RegexOption.IGNORE_CASE)
private val NAME_END = "(?=\\s+(?:on|via|to|ref|refno|ref\\s*no|upi|imps|neft|in|at|for|a/?c|thru|through|dated|avl|bal|from|-)\\b|[.,;(\\n]|$)"
private val FROM_PERSON = Regex("\\b(?:from|by|received from)\\s+(?:vpa\\s+)?(?:mr\\.?|ms\\.?|mrs\\.?)?\\s*([A-Za-z][A-Za-z .]{1,40}?)(?![\\w.-]*@)$NAME_END", RegexOption.IGNORE_CASE)
private val TO_PERSON = Regex("\\b(?:trf to|transfer(?:red)? to|paid to|sent to|to)\\s+(?:vpa\\s+)?(?:mr\\.?|ms\\.?|mrs\\.?)?\\s*([A-Za-z][A-Za-z .]{1,40}?)(?![\\w.-]*@)$NAME_END", RegexOption.IGNORE_CASE)
private val CREDITED_PERSON = Regex(";\\s*([A-Z][A-Za-z .]{1,40}?)\\s+credited\\b")
private val NOT_PERSON = Regex(
    "\\b(?:your|you|a/?c|ac|account|acct|bank|card|wallet|beneficiary|vpa|upi|self|xx\\w*|rs|inr|amt|amount|cash|mob|mobile|ref|refno|imps|neft|rtgs|branch|atm|cheque|chq)\\b",
    RegexOption.IGNORE_CASE,
)
/** IMPS: "by a/c linked to mobile 98XXXXXX10-RAHUL KUMAR". */
private val MOBILE_NAME = Regex("linked to mobile\\s*[\\dXx*]+\\s*-\\s*([A-Za-z][A-Za-z .]{1,40}?)\\s*(?=[(.,;\\n]|$)", RegexOption.IGNORE_CASE)

private fun personName(raw: String?): String? {
    val n = raw?.trim()?.trim('.', ',', ':', '-', '/') ?: return null
    if (n.isEmpty() || NOT_PERSON.containsMatchIn(n)) return null
    return cleanName(n)
}

/** "rahul.k@okaxis" is said as "UPI ID rahul.k" — never the bank's handle part. */
private fun upiOf(body: String): String? {
    val id = VPA.find(body)?.groupValues?.get(1) ?: return null
    return "UPI ID " + id.substringBefore('@')
}

/** Who a bank SMS's payment was from ([flow] IN) or to (OUT): a name, a UPI ID, or null when the SMS names nobody. */
fun counterpartyOf(body: String, flow: MoneyFlow): String? {
    val b = asciiDigitsOf(body)
    UPI_PATH.find(b)?.let { m -> personName(m.groupValues[1])?.let { return it } }
    MOBILE_NAME.find(b)?.let { m -> personName(m.groupValues[1])?.let { return it } }
    if (flow == MoneyFlow.OUT) {
        CREDITED_PERSON.find(b)?.let { m -> personName(m.groupValues[1])?.let { return it } }
        TO_PERSON.findAll(b).forEach { m -> personName(m.groupValues[1])?.let { return it } }
    } else {
        FROM_PERSON.findAll(b).forEach { m -> personName(m.groupValues[1])?.let { return it } }
    }
    return upiOf(b)
}

/**
 * Every payment since the SMS were read: the bank SMS first (the bank's word decides), then the UPI notes
 * that no SMS of the same amount and side covers (a bank that sent no SMS). A note's name fills an SMS that
 * names nobody.
 */
fun moneyEvents(sms: List<SmsMessage>, notes: List<MoneyNote>): List<MoneyEvent> {
    val fromSms = sms.filter { !isOtp(it.body) }.mapNotNull { m ->
        val body = asciiDigitsOf(m.body)
        val paise = transactionPaise(body) ?: return@mapNotNull null
        val flow = when (directionOf(body)) {
            MoneyDirection.CREDIT -> MoneyFlow.IN
            MoneyDirection.DEBIT -> MoneyFlow.OUT
            null -> return@mapNotNull null
        }
        MoneyEvent(paise, flow, m.atMillis, counterpartyOf(body, flow))
    }
    // One payment can be noted twice (the SMS app's notification and the UPI app's): one note per payment,
    // keeping the one that names the person.
    val oneEach = mutableListOf<MoneyNote>()
    notes.sortedBy { it.atMillis }.forEach { n ->
        val same = oneEach.indexOfFirst { it.paise == n.paise && it.flow == n.flow && kotlin.math.abs(it.atMillis - n.atMillis) <= SAME_PAYMENT_MS }
        if (same < 0) oneEach += n else if (oneEach[same].who == null && n.who != null) oneEach[same] = oneEach[same].copy(who = n.who)
    }
    val used = mutableSetOf<MoneyNote>()
    val merged = fromSms.map { e ->
        val note = oneEach.firstOrNull { n -> n !in used && n.paise == e.paise && n.flow == e.flow && kotlin.math.abs(n.atMillis - e.atMillis) <= SAME_PAYMENT_MS }
        if (note != null) used += note
        if (e.who == null && note?.who != null) e.copy(who = note.who) else e
    }
    val onlyNotes = oneEach.filter { it !in used }.map { MoneyEvent(it.paise, it.flow, it.atMillis, it.who) }
    return (merged + onlyNotes).sortedBy { it.atMillis }
}

// ---------------------------------------------------------------------------------------- time

data class MoneyWindow(val fromMillis: Long, val untilMillis: Long)

fun windowOf(time: MoneyTime, nowMillis: Long, zone: ZoneId): MoneyWindow = when (time) {
    is MoneyTime.LastMinutes -> MoneyWindow(nowMillis - time.minutes * 60_000L, nowMillis + 1)
    is MoneyTime.Day -> {
        val start = time.date.atStartOfDay(zone).toInstant().toEpochMilli()
        val part = time.part
        if (part == null) MoneyWindow(start, time.date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
        else {
            val (a, b) = hoursOf(part)
            MoneyWindow(
                time.date.atStartOfDay(zone).plusHours(a.toLong()).toInstant().toEpochMilli(),
                time.date.atStartOfDay(zone).plusHours(b.toLong()).toInstant().toEpochMilli(),
            )
        }
    }
}

private val BENGALI_MONTHS = listOf("জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন", "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর")

private fun count(n: Double): String = if (n == n.toLong().toDouble()) n.toLong().toString() else n.toString()

/** "पिछले 2 घंटे में", "आज सुबह", "कल शाम", "15 नवंबर को" — and the Bengali. */
fun timeLabel(time: MoneyTime, today: LocalDate, bengali: Boolean): String = when (time) {
    is MoneyTime.LastMinutes -> {
        val m = time.minutes
        when {
            m == 30 -> if (bengali) "গত আধ ঘণ্টায়" else "पिछले आधे घंटे में"
            m % 60 == 0 -> if (bengali) "গত ${m / 60} ঘণ্টায়" else "पिछले ${m / 60} घंटे में"
            m > 60 && m % 30 == 0 -> if (bengali) "গত ${count(m / 60.0)} ঘণ্টায়" else "पिछले ${count(m / 60.0)} घंटे में"
            else -> if (bengali) "গত $m মিনিটে" else "पिछले $m मिनट में"
        }
    }
    is MoneyTime.Day -> {
        val d = time.date
        val dayHi = when (d) {
            today -> "आज"
            today.minusDays(1) -> "कल"
            today.minusDays(2) -> "परसों"
            else -> "${d.dayOfMonth} ${HINDI_MONTHS[d.monthValue - 1]}"
        }
        val dayBn = when (d) {
            today -> "আজ"
            today.minusDays(1) -> "গতকাল"
            today.minusDays(2) -> "পরশু"
            else -> "${d.dayOfMonth} ${BENGALI_MONTHS[d.monthValue - 1]}"
        }
        val named = d != today && d != today.minusDays(1) && d != today.minusDays(2)
        when (time.part) {
            null -> if (bengali) dayBn else if (named) "$dayHi को" else dayHi
            DayPart.MORNING -> if (bengali) "$dayBn সকালে" else if (named) "$dayHi की सुबह" else "$dayHi सुबह"
            DayPart.AFTERNOON -> if (bengali) "$dayBn দুপুরে" else if (named) "$dayHi की दोपहर" else "$dayHi दोपहर"
            DayPart.EVENING -> if (bengali) "$dayBn সন্ধ্যায়" else if (named) "$dayHi की शाम" else "$dayHi शाम"
            DayPart.NIGHT -> if (bengali) "$dayBn রাতে" else if (named) "$dayHi की रात" else "$dayHi रात"
        }
    }
}

/** "कल शाम 6:40 बजे", "आज 10:05 बजे", "15 नवंबर 9:30 बजे". */
private fun momentLabel(atMillis: Long, today: LocalDate, zone: ZoneId, bengali: Boolean): String {
    val t = Instant.ofEpochMilli(atMillis).atZone(zone)
    val d = t.toLocalDate()
    val h = t.hour
    val clock = "${if (h % 12 == 0) 12 else h % 12}:${t.minute.toString().padStart(2, '0')}"
    val part = when (h) {
        in 5..11 -> if (bengali) "সকাল" else "सुबह"
        in 12..15 -> if (bengali) "দুপুর" else "दोपहर"
        in 16..19 -> if (bengali) "সন্ধ্যা" else "शाम"
        else -> if (bengali) "রাত" else "रात"
    }
    val day = when (d) {
        today -> if (bengali) "আজ" else "आज"
        today.minusDays(1) -> if (bengali) "গতকাল" else "कल"
        else -> if (bengali) "${d.dayOfMonth} ${BENGALI_MONTHS[d.monthValue - 1]}" else "${d.dayOfMonth} ${HINDI_MONTHS[d.monthValue - 1]}"
    }
    return if (bengali) "$day $part $clock-এ" else "$day $part $clock बजे"
}

// ---------------------------------------------------------------------------------------- answers

private fun joinAmounts(list: List<String>, bengali: Boolean): String = when (list.size) {
    0 -> ""
    1 -> list[0]
    else -> list.dropLast(1).joinToString(", ") + (if (bengali) " আর " else " और ") + list.last()
}

private fun amountsLine(events: List<MoneyEvent>, flow: MoneyFlow, withNames: Boolean, totalOnly: Boolean, label: String, bengali: Boolean): String {
    val total = spokenRupees(events.sumOf { it.paise })
    if (events.isEmpty()) return when (flow) {
        MoneyFlow.OUT -> if (bengali) "$label কোনো টাকা কাটার বা পাঠানোর মেসেজ পাইনি।" else "$label कोई पैसा कटने या भेजने का संदेश नहीं मिला।"
        else -> if (bengali) "$label কোনো টাকা আসার মেসেজ পাইনি।" else "$label कोई पैसा आने का संदेश नहीं मिला।"
    }
    val n = events.size
    // "सब मिला के बोलो": only the total (and how many times).
    if (totalOnly) return when (flow) {
        MoneyFlow.OUT -> if (bengali) "$label মোট $total টাকা কেটেছে — ${n} বারে।" else "$label कुल $total रुपये कटे — $n बार में।"
        else -> if (bengali) "$label মোট $total টাকা এসেছে — ${n}টা পেমেন্টে।" else "$label कुल $total रुपये आए — $n पेमेंट में।"
    }
    val shown = events.take(MAX_SPOKEN_AMOUNTS)
    val more = n - shown.size
    val items = shown.map { e ->
        val amount = spokenRupees(e.paise)
        if (!withNames) amount
        else when {
            e.who == null -> if (bengali) "$amount টাকা (নাম নেই)" else "$amount रुपये (नाम नहीं)"
            flow == MoneyFlow.OUT -> if (bengali) "$amount টাকা ${e.who}-কে" else "$amount रुपये ${e.who} को"
            else -> if (bengali) "$amount টাকা ${e.who} থেকে" else "$amount रुपये ${e.who} से"
        }
    }
    val unit = if (withNames) "" else if (bengali) " টাকা" else " रुपये"
    val head = when (flow) {
        MoneyFlow.OUT -> if (bengali) "$label ${n} বার টাকা কেটেছে" else if (n == 1) "$label एक बार पैसा कटा" else "$label $n बार पैसा कटा"
        else -> if (bengali) "$label ${n}টা পেমেন্ট এসেছে" else if (n == 1) "$label एक पेमेंट आया" else "$label $n पेमेंट आए"
    }
    val body = joinAmounts(items, bengali) + unit
    val tail = when {
        more > 0 -> if (bengali) "। আরও ${more}টা — সব মিলিয়ে $total টাকা।" else "। और $more — सब मिलाकर कुल $total रुपये।"
        n > 1 -> if (bengali) " — মোট $total টাকা।" else " — कुल $total रुपये।"
        else -> "।"
    }
    return "$head: $body$tail"
}

/** What Seema says for an amounts question. [events]: all payments of the window, oldest first. */
fun spokenMoneyAmounts(ask: MoneyAsk.Amounts, events: List<MoneyEvent>, today: LocalDate, language: SeemaLanguage): String {
    val bn = language == SeemaLanguage.BENGALI
    val label = timeLabel(ask.time, today, bn)
    val ins = events.filter { it.flow == MoneyFlow.IN }
    val outs = events.filter { it.flow == MoneyFlow.OUT }
    return when (ask.flow) {
        MoneyFlow.IN -> amountsLine(ins, MoneyFlow.IN, ask.withNames, ask.totalOnly, label, bn)
        MoneyFlow.OUT -> amountsLine(outs, MoneyFlow.OUT, ask.withNames, ask.totalOnly, label, bn)
        MoneyFlow.BOTH -> amountsLine(ins, MoneyFlow.IN, ask.withNames, ask.totalOnly, label, bn) + " " +
            amountsLine(outs, MoneyFlow.OUT, ask.withNames, ask.totalOnly, if (bn) "আর" else "और", bn)
    }
}

/** What Seema says for "3630 किसका आया?". [events]: the payments looked through. */
fun spokenMoneyWho(ask: MoneyAsk.Who, events: List<MoneyEvent>, today: LocalDate, zone: ZoneId, language: SeemaLanguage): String {
    val bn = language == SeemaLanguage.BENGALI
    val amount = spokenRupees(ask.paise)
    // Said in whole rupees ("4654 किसका आया" finds Rs 4654.01).
    val matches = events.filter { (it.paise + 50) / 100 == (ask.paise + 50) / 100 && (ask.flow == MoneyFlow.BOTH || it.flow == ask.flow) }.sortedByDescending { it.atMillis }
    val scope = ask.time?.let { timeLabel(it, today, bn) } ?: if (bn) "গত $WHO_SEARCH_DAYS দিনে" else "पिछले $WHO_SEARCH_DAYS दिनों में"
    val e = matches.firstOrNull() ?: return when (ask.flow) {
        MoneyFlow.OUT -> if (bn) "$scope $amount টাকা পাঠানোর কোনো মেসেজ পাইনি।" else "$scope $amount रुपये भेजने का कोई संदेश नहीं मिला।"
        MoneyFlow.IN -> if (bn) "$scope $amount টাকা আসার কোনো মেসেজ পাইনি।" else "$scope $amount रुपये आने का कोई संदेश नहीं मिला।"
        MoneyFlow.BOTH -> if (bn) "$scope $amount টাকার কোনো মেসেজ পাইনি।" else "$scope $amount रुपये का कोई संदेश नहीं मिला।"
    }
    val at = momentLabel(e.atMillis, today, zone, bn)
    val line = when {
        e.flow == MoneyFlow.IN && e.who != null -> if (bn) "$amount টাকা $at ${e.who} পাঠিয়েছে।" else "$amount रुपये $at ${e.who} ने भेजे।"
        e.flow == MoneyFlow.IN -> if (bn) "$amount টাকা $at এসেছে, কিন্তু ব্যাংকের মেসেজে পাঠানোর লোকের নাম নেই।" else "$amount रुपये $at आए, पर बैंक के संदेश में भेजने वाले का नाम नहीं है।"
        e.who != null -> if (bn) "$amount টাকা $at ${e.who}-কে পাঠানো হয়েছে।" else "$amount रुपये $at ${e.who} को भेजे गए।"
        else -> if (bn) "$amount টাকা $at গেছে, কিন্তু মেসেজে কাকে গেছে তার নাম নেই।" else "$amount रुपये $at गए, पर संदेश में यह नाम नहीं है कि किसको गए।"
    }
    return if (matches.size > 1) (if (bn) "এমন ${matches.size}টা পেমেন্ট পেয়েছি। সবচেয়ে নতুনটা: " else "ऐसे ${matches.size} पेमेंट मिले। सबसे नया: ") + line else line
}

// ---------------------------------------------------------------------------------------- UPI notifications

/** "You paid ₹500 to Rahul", "₹500 sent to Rahul", "Payment of ₹500 to Rahul successful": money that went out. */
fun moneyPaidOut(title: String?, text: String?): MoneyNote? {
    val raw = listOfNotNull(title, text).joinToString(" \n ").trim()
    if (raw.isEmpty() || raw.length > 600) return null
    val body = asciiDigitsOf(raw)
    if (isOtp(body) || Regex("\\b(?:paid|sent|transferred)\\s+you\\b|request|failed|declined|pending|unsuccessful|cashback|reward|offer", RegexOption.IGNORE_CASE).containsMatchIn(body)) return null
    val m = Regex("(?:you\\s+)?(?:paid|sent|transferred|payment of)\\s+(?:rs\\.?|inr|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)\\s+(?:to|for)\\s+([A-Za-z][A-Za-z .]{1,40}?)$NAME_END", RegexOption.IGNORE_CASE).find(body)
        ?: Regex("(?:rs\\.?|inr|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)\\s+(?:paid|sent)\\s+to\\s+([A-Za-z][A-Za-z .]{1,40}?)$NAME_END", RegexOption.IGNORE_CASE).find(body)
        ?: return null
    val paise = paiseOf(m.groupValues[1])?.takeIf { it >= 100 } ?: return null
    return MoneyNote(paise, MoneyFlow.OUT, 0L, personName(m.groupValues[2]))
}
