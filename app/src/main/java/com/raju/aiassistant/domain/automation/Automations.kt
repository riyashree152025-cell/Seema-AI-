package com.raju.aiassistant.domain.automation

import com.raju.aiassistant.domain.sms.SmsMessage
import com.raju.aiassistant.domain.sms.spokenRupees
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * "Roz subah 9 baje dawai ki yaad dilana", "har din raat 10 baje paani peene ki yaad dilana", "রোজ সকাল ৯টায় ওষুধের কথা
 * মনে করিয়ে দিও": a daily routine (Automation screen) whose one step is her loving reminder, said at that time.
 */
data class DailyCare(val hour: Int, val minute: Int, val what: String)

object DailyCareReminder {
    private val ASK = Regex(
        "^(?:seema[, ]+)?(?:roz|rozana|har din|daily|रोज़|रोज|रोजाना|हर दिन|রোজ|প্রতিদিন)\\s+" +
            "(?:(subah|savere|dopahar|shaam|sham|raat|सुबह|दोपहर|शाम|रात|সকাল|দুপুর|বিকেল|সন্ধ্যা|রাত)\\s+)?" +
            "(\\d{1,2})(?:[:.](\\d{2}))?\\s*(?:baje|बजे|টায়|টার সময়)\\s+" +
            "(.{2,40}?)\\s*(?:ki|ka|ke|की|का|के|এর|র|ের)?\\s*(?:yaad dilana|yaad dila dena|yaad dilaana|reminder dena|याद दिलाना|याद दिला देना|रिमाइंडर देना|কথা মনে করিয়ে দিও|মনে করিয়ে দিও|মনে করিয়ে দিবে)[?।.!]*$",
        RegexOption.IGNORE_CASE,
    )
    private val PM = Regex("shaam|sham|raat|dopahar|शाम|रात|दोपहर|বিকেল|সন্ধ্যা|রাত|দুপুর", RegexOption.IGNORE_CASE)

    private fun digits(s: String) = s.map { c -> if (c in '०'..'९') '0' + (c - '०') else if (c in '০'..'৯') '0' + (c - '০') else c }.joinToString("")

    fun parse(said: String): DailyCare? {
        val m = ASK.matchEntire(digits(said.trim()).replace(Regex("\\s+"), " ")) ?: return null
        var h = m.groupValues[2].toInt()
        val min = m.groupValues[3].toIntOrNull() ?: 0
        if (h > 23 || min > 59) return null
        val part = m.groupValues[1]
        if (part.isNotEmpty() && PM.containsMatchIn(part) && h in 1..11) h += 12
        if (part.isEmpty() && h in 1..6) h += 12 // "roz 4 baje" in a day's talk is the afternoon
        val what = m.groupValues[4].trim().removeSuffix(" lene").removeSuffix(" khane").removeSuffix(" peene").trim()
        return DailyCare(h, min, m.groupValues[4].trim().ifEmpty { what })
    }

    /** The routine's step (spoken at the time through the "बोलो:" step). */
    fun step(c: DailyCare): String = "बोलो: जान, ${c.what} का समय हो गया… भूलना मत।"

    fun name(c: DailyCare): String = "याद: ${c.what}"

    fun confirmation(c: DailyCare): String = "ठीक है, रोज़ ${c.hour}:${c.minute.toString().padStart(2, '0')} बजे ${c.what} की याद दिला दूँगी।"

    /** "बोलो: …": a routine step that is only her own words. */
    private val SAY = Regex("^(?:बोलो|bolo|বলো)\\s*:\\s*(.{1,200})$", RegexOption.IGNORE_CASE)

    fun sayStep(step: String): String? = SAY.matchEntire(step.trim())?.groupValues?.get(1)?.trim()
}

/**
 * Bills whose last date is near, from the bank / biller SMS on the phone: "Your electricity bill of Rs 1,240 is due on
 * 25-11-2026", "Mobile bill Rs 399 due date 24/11", "EMI of Rs 5,000 due on 05 Dec". Said in the morning briefing three days
 * before; never paid by Seema.
 */
data class BillDue(val what: String, val amountPaise: Long?, val due: LocalDate)

object BillDues {
    private val DUE = Regex("(?:due\\s*(?:date|on|by)?|last\\s*date|pay\\s*by|before|देय तिथि|अंतिम तिथि)\\s*[:\\-]?\\s*(\\d{1,2})[-/. ](\\d{1,2}|[A-Za-z]{3,9})(?:[-/. ](\\d{2,4}))?", RegexOption.IGNORE_CASE)
    private val AMOUNT = Regex("(?:rs\\.?|inr|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)", RegexOption.IGNORE_CASE)
    private val KIND = listOf(
        Regex("electricity|bijli|बिजली|power bill|wbsedcl|bses|tneb|msedcl", RegexOption.IGNORE_CASE) to "बिजली का बिल",
        Regex("emi|loan", RegexOption.IGNORE_CASE) to "EMI",
        Regex("credit card|card statement|total amount due|minimum amount due", RegexOption.IGNORE_CASE) to "क्रेडिट कार्ड का बिल",
        Regex("postpaid|mobile bill|airtel|jio|vi bill|vodafone|bsnl", RegexOption.IGNORE_CASE) to "मोबाइल का बिल",
        Regex("broadband|fiber|wifi|internet bill", RegexOption.IGNORE_CASE) to "इंटरनेट का बिल",
        Regex("gas bill|lpg|indane|bharat gas|hp gas", RegexOption.IGNORE_CASE) to "गैस का बिल",
        Regex("insurance|premium|lic", RegexOption.IGNORE_CASE) to "बीमा की किस्त",
    )
    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    fun parse(sms: SmsMessage, zone: ZoneId): BillDue? {
        val body = sms.body
        val what = KIND.firstOrNull { it.first.containsMatchIn(body) }?.second ?: return null
        val m = DUE.find(body) ?: return null
        val received = Instant.ofEpochMilli(sms.atMillis).atZone(zone).toLocalDate()
        val day = m.groupValues[1].toInt()
        val month = m.groupValues[2].toIntOrNull() ?: (MONTHS.indexOf(m.groupValues[2].take(3).lowercase()) + 1).takeIf { it > 0 } ?: return null
        val year = m.groupValues[3].toIntOrNull()?.let { if (it < 100) 2000 + it else it } ?: received.year
        var due = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return null
        if (m.groupValues[3].isEmpty() && due.isBefore(received.minusDays(1))) due = due.plusYears(1)
        val amount = AMOUNT.find(body)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()?.let { Math.round(it * 100) }
        return BillDue(what, amount, due)
    }

    /** Bills due today … in [daysAhead] days, the nearest first, one line; null when none. */
    fun line(messages: List<SmsMessage>, today: LocalDate, zone: ZoneId, daysAhead: Long = 3): String? {
        val due = messages.mapNotNull { parse(it, zone) }
            .filter { !it.due.isBefore(today) && !it.due.isAfter(today.plusDays(daysAhead)) }
            .distinctBy { it.what to it.due }
            .sortedBy { it.due }
        if (due.isEmpty()) return null
        return "ध्यान दो, " + due.joinToString("; ") { b ->
            val whenSaid = when (b.due) { today -> "आज"; today.plusDays(1) -> "कल"; else -> "${b.due.dayOfMonth} तारीख को" }
            "${b.what}${b.amountPaise?.let { " (${spokenRupees(it)} रुपये)" } ?: ""} की आख़िरी तारीख $whenSaid है"
        } + "।"
    }
}

/**
 * Calls: "Rahul ka call chhoot gaya, wapas lagaun?" after a ring nobody answered, and busy mode ("main busy hoon, calls
 * ko message bhej do"): every missed caller gets "अभी व्यस्त हूँ, 10 मिनट में कॉल करता हूँ" by SMS, once an hour per number.
 */
object CallAutomation {
    private val BUSY_ON = Regex(
        "^(?:seema[, ]+)?(?:busy mode (?:on|chalu) karo|busy mode on|busy mode chalu|main busy hoon,? calls? ko (?:message|sms) (?:bhej do|kar do)|" +
            "बिज़ी मोड (?:ऑन|चालू) करो|बिजी मोड (?:ऑन|चालू) करो|मैं व्यस्त हूँ,? कॉल वालों को मैसेज भेज दो|ব্যস্ত মোড চালু করো)[।.!]*$",
        RegexOption.IGNORE_CASE,
    )
    private val BUSY_OFF = Regex(
        "^(?:seema[, ]+)?(?:busy mode (?:off|band) karo|busy mode off|busy mode band|बिज़ी मोड (?:ऑफ|बंद) करो|बिजी मोड (?:ऑफ|बंद) करो|ব্যস্ত মোড বন্ধ করো)[।.!]*$",
        RegexOption.IGNORE_CASE,
    )

    /** true: turn busy mode on; false: off; null: not about it. */
    fun busyCommand(text: String): Boolean? = when {
        BUSY_ON.matches(text.trim()) -> true
        BUSY_OFF.matches(text.trim()) -> false
        else -> null
    }

    const val BUSY_SMS = "अभी व्यस्त हूँ, 10 मिनट में कॉल करता हूँ।"
    const val SMS_GAP_MS = 60 * 60 * 1000L

    fun missedQuestion(name: String?, bengali: Boolean): String =
        if (bengali) "${name ?: "একজনের"} ফোন মিস হয়ে গেল, ফিরিয়ে কল করব?" else "${name ?: "किसी"} का कॉल छूट गया — वापस लगाऊँ?"

    private val DND = Regex(
        "^(?:seema[, ]+)?(?:dnd|do not disturb|डीएनडी|ডিএনডি)\\s+(on|off|chalu|band|चालू|बंद|ऑन|ऑफ|চালু|বন্ধ)\\s*(?:karo|kar do|करो|कर दो|করো|করে দাও)?[।.!]*$",
        RegexOption.IGNORE_CASE,
    )

    /** "DND chalu karo" → true, "DND band karo" → false. */
    fun dndCommand(text: String): Boolean? = DND.matchEntire(text.trim())?.groupValues?.get(1)?.lowercase()?.let { it in setOf("on", "chalu", "चालू", "ऑन", "চালু") }

    /** Battery this low (not charging): she says so once. */
    const val LOW_BATTERY = 15
    fun lowBatteryLine(percent: Int, bengali: Boolean = false): String =
        if (bengali) "ব্যাটারি মাত্র $percent% বাকি আছে, চার্জে বসিয়ে নাও সোনা।"
        else "बैटरी सिर्फ़ $percent प्रतिशत बची है, चार्जर लगा लो जान।"

    fun fullBatteryLine(percent: Int, bengali: Boolean = false): String =
        if (bengali) "ফোন সম্পূর্ণ চার্জ ($percent%) হয়ে গেছে, এবার চার্জার খুলে নাও সোনা।"
        else "फ़ोन पूरा चार्ज ($percent%) हो गया है, अब चार्जर हटा लो जान।"
}
