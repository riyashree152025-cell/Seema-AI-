package com.raju.aiassistant.domain.business

import com.raju.aiassistant.domain.ledger.ClientBalance
import com.raju.aiassistant.domain.rail.IrctcTicket
import com.raju.aiassistant.domain.sms.spokenRupees
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** An IRCTC e-ticket read from its PDF becomes a journey in the PNR diary (so the chart watch and briefing know it). */
object TicketToDiary {
    private val FORMATS = listOf("d-MMM-yyyy", "d MMM yyyy", "d/MMM/yyyy", "d-M-yyyy", "d/M/yyyy", "d.M.yyyy").map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }

    fun date(text: String?): LocalDate? {
        val raw = text?.trim() ?: return null
        val norm = raw.split(Regex("[-/ ]")).joinToString("-") { p -> if (p.length == 3 && p.all { it.isLetter() }) p.lowercase().replaceFirstChar { it.uppercase() } else p }
        val candidates = listOf(raw, norm, norm.replace('-', ' '), norm.replace('-', '/'))
        for (c in candidates) for (f in FORMATS) runCatching { return LocalDate.parse(c, f) }
        return null
    }

    /** The diary entry, or null when the PNR or the date cannot be read. */
    fun journey(t: IrctcTicket): Journey? {
        val pnr = t.pnr?.filter { it.isDigit() }?.takeIf { it.length == 10 } ?: return null
        val date = date(t.date) ?: return null
        val train = listOfNotNull(t.trainNumber, t.trainName).joinToString(" ").ifBlank { null }
        val route = if (t.from != null && t.to != null) "${t.from} → ${t.to}" else null
        val passenger = t.passengers.firstOrNull()?.name?.let { if (t.passengers.size > 1) "$it +${t.passengers.size - 1}" else it }
        return Journey(pnr = pnr, train = train, date = date, route = route, passenger = passenger, note = t.travelClass)
    }
}

/**
 * "Kal subah 8 baje Amit ko message bhejna ki ticket taiyar hai", "शाम 6 बजे राहुल को 'पैसे भेज दो' भेज देना": one of
 * Seema's reminders that, when it comes, sends that WhatsApp through the usual confirm (never by itself).
 */
data class ScheduledMessage(val at: LocalDateTime, val who: String, val text: String)

object ScheduledMessages {
    private const val SEND = "(?:bhej dena|bhej do|bhejna hai|bhejna|send kar dena|भेज देना|भेज दो|भेजना है|भेजना|পাঠিয়ে দিও|পাঠিয়ে দেবে)"
    private val SAY_THAT = Regex(
        "^(.{2,40}?)\\s+((?!(?:baje|बजे|টায়)(?![\\p{L}\\p{M}]))[\\p{L}\\p{M}.]{2,20}(?:\\s+(?:ji|jee|bhai|bhaiya|जी|भाई|भैया|দা|ভাই))?)\\s+(?:ko|को|কে)\\s+(?:whatsapp\\s+|व्हाट्सएप\\s+)?(?:message\\s+|msg\\s+|मैसेज\\s+|মেসেজ\\s+)?$SEND\\s*(?:ki|कि|যে|:)\\s*(.{1,300})$",
        RegexOption.IGNORE_CASE,
    )
    private val QUOTED = Regex(
        "^(.{2,40}?)\\s+((?!(?:baje|बजे|টায়)(?![\\p{L}\\p{M}]))[\\p{L}\\p{M}.]{2,20}(?:\\s+(?:ji|jee|bhai|bhaiya|जी|भाई|भैया|দা|ভাই))?)\\s+(?:ko|को|কে)\\s+[\"'“‘](.{1,300})[\"'”’]\\s+$SEND$",
        RegexOption.IGNORE_CASE,
    )
    private val TIME_CUE = Regex("(?<![\\p{L}\\p{M}])(?:kal|parso|aaj|subah|shaam|sham|raat|dopahar|कल|परसों|आज|सुबह|शाम|रात|दोपहर|কাল|সকালে|সন্ধ্যায়|রাতে)(?![\\p{L}\\p{M}])|\\d{1,2}\\s*(?:baje|बजे|টায়)", RegexOption.IGNORE_CASE)

    fun parse(said: String, now: LocalDateTime): ScheduledMessage? {
        val t = said.trim().trimEnd('.', '!', '।').replace(Regex("\\s+"), " ")
        val m = SAY_THAT.matchEntire(t) ?: QUOTED.matchEntire(t) ?: return null
        val timePart = m.groupValues[1]
        if (!TIME_CUE.containsMatchIn(timePart)) return null
        val at = com.raju.aiassistant.domain.memory.CallMemoryExtractor.dueOf(timePart, now)
        val text = m.groupValues[3].trim().trim('"', '\'', '“', '”')
        if (text.isBlank()) return null
        return ScheduledMessage(at, m.groupValues[2].trim(), text)
    }

    private const val PREFIX = "WhatsApp भेजना है → "
    fun reminderText(m: ScheduledMessage): String = "$PREFIX${m.who}: ${m.text}"

    /** (who, text) of a reminder made here, or null for an ordinary reminder. */
    fun fromReminder(text: String): Pair<String, String>? {
        if (!text.startsWith(PREFIX)) return null
        val rest = text.removePrefix(PREFIX)
        val who = rest.substringBefore(": ", "").trim()
        val body = rest.substringAfter(": ", "").trim()
        return if (who.isEmpty() || body.isEmpty()) null else who to body
    }

    fun confirmation(m: ScheduledMessage): String =
        "ठीक है, ${m.at.dayOfMonth}/${m.at.monthValue} को ${m.at.hour}:${m.at.minute.toString().padStart(2, '0')} बजे ${m.who} को यह मैसेज भेजने से पहले आपसे पूछ लूँगी।"
}

/** Pages Seema turns into a PDF to share: the dues list, and a simple bill / receipt. */
data class PdfPage(val fileName: String, val title: String, val lines: List<String>)

object OfficePdfs {
    private val DUES_ASK = Regex(
        "(?:bakaya|baki|baaki|udhaar|udhar|बकाया|बाकी|उधार|বাকি)\\s*(?:ki|ka|की|का|এর)?\\s*(?:list|lisht|suchi|सूची|लिस्ट|তালিকা)\\s*(?:ki\\s+|की\\s+)?(?:pdf|पीडीएफ)",
        RegexOption.IGNORE_CASE,
    )
    fun asksDuesPdf(text: String): Boolean = DUES_ASK.containsMatchIn(text)

    fun dues(balances: List<ClientBalance>, today: LocalDate): PdfPage {
        val owed = balances.filter { it.balancePaise > 0 }.sortedByDescending { it.balancePaise }
        val lines = owed.mapIndexed { i, b -> "${i + 1}. ${b.name} — ₹${rupees(b.balancePaise)}" } +
            listOf("", "कुल बाकी: ₹${rupees(owed.sumOf { it.balancePaise })}  (${owed.size} लोग)")
        return PdfPage("bakaya-${today}.pdf", "R Travels — बकाया सूची (${today.dayOfMonth}/${today.monthValue}/${today.year})", lines)
    }

    // "Rahul ka bill banao 2 ticket 4500", "राहुल का बिल बनाओ 3 टिकट 6000 रुपये"
    private val BILL = Regex(
        "^(?:seema[, ]+)?(.{2,30}?)\\s+(?:ka|की|का|এর)\\s+(?:bill|बिल|রসিদ|বিল|receipt|रसीद)\\s+(?:banao|bana do|बनाओ|बना दो|বানাও)\\s*[,:]?\\s*(\\d{1,3})\\s*(?:ticket|tickets|टिकट|টিকিট)\\s+(?:₹|rs\\.?)?\\s*(\\d{2,7})\\s*(?:rupaye|rupees|रुपये|रुपए|টাকা)?$",
        RegexOption.IGNORE_CASE,
    )

    data class Bill(val customer: String, val tickets: Int, val amountPaise: Long)

    fun parseBill(text: String): Bill? {
        val m = BILL.matchEntire(text.trim().trimEnd('.', '।')) ?: return null
        val n = m.groupValues[2].toInt().takeIf { it in 1..99 } ?: return null
        return Bill(m.groupValues[1].trim(), n, m.groupValues[3].toLong() * 100)
    }

    fun bill(b: Bill, today: LocalDate, number: Long): PdfPage = PdfPage(
        "bill-${b.customer.replace(Regex("[^\\p{L}\\p{N}]+"), "_")}-$today.pdf",
        "R Travels — बिल / रसीद",
        listOf(
            "बिल नं.: $number", "तारीख: ${today.dayOfMonth}/${today.monthValue}/${today.year}", "ग्राहक: ${b.customer}", "",
            "रेल टिकट × ${b.tickets}", "कुल रकम: ₹${rupees(b.amountPaise)}", "", "धन्यवाद 🙏 — R Travels",
        ),
    )

    fun rupees(paise: Long): String = String.format(Locale.US, "%,d", paise / 100)

    fun spokenDone(page: PdfPage): String = "PDF तैयार है — जो खुला है उसमें WhatsApp या कोई भी ऐप चुनकर भेज दीजिए।"
}

/** "Aaj tumne kya kya kiya", "aaj ka kaam batao": what Seema did today, from her own action log, counted by kind. */
object ActionDiary {
    private val ASK = Regex(
        "^(?:seema[, ]+)?(?:aaj|आज|আজ)\\s+(?:tumne|tumne kya|aapne|तुमने|तूने|তুমি)\\s*(?:kya kya|kya|क्या क्या|क्या|কী কী|কি কি)\\s*(?:kiya|kaam kiya|किया|काम किया|করলে|করেছ)[?।.]*$",
        RegexOption.IGNORE_CASE,
    )
    fun asks(text: String): Boolean = ASK.matches(text.trim())

    /** [done]: the labels of today's successful actions. */
    fun summary(done: List<String>): String {
        if (done.isEmpty()) return "आज अभी तक मैंने कोई काम नहीं किया — बस आपका इंतज़ार किया।"
        val counts = done.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(6)
        return "आज मैंने ${done.size} काम किए: " + counts.joinToString(", ") { (k, v) -> if (v > 1) "$k ($v बार)" else k } + "।"
    }
}
