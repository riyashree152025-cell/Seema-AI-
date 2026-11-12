package com.raju.aiassistant.domain.vision

import com.raju.aiassistant.domain.business.Journey
import java.time.LocalDate

/**
 * Ticket and bill photos: the vision model only copies what is printed into fixed lines; the app checks
 * every value (a 10-digit PNR, a real date, a sensible amount) and the user confirms before anything is
 * saved. A value that is not clearly printed stays empty — nothing is guessed.
 */
const val TICKET_PROMPT =
    "This is an Indian railway ticket (IRCTC e-ticket, its SMS, or a counter ticket). Write exactly these six lines and nothing else, " +
        "copying only what is printed; leave the value empty when it is not clearly printed:\n" +
        "PNR: <the 10-digit PNR>\nTRAIN: <train number and name>\nDATE: <date of journey as DD/MM/YYYY>\n" +
        "FROM: <boarding station>\nTO: <destination station>\nPASSENGER: <first passenger's name>\nDo not guess."

const val BILL_PROMPT =
    "This is a bill or receipt. Write exactly these three lines and nothing else, copying only what is printed; " +
        "leave the value empty when it is not clearly printed:\n" +
        "TOTAL: <the final amount paid, digits only, e.g. 1250.50>\nSHOP: <shop or company name>\nDATE: <bill date as DD/MM/YYYY>\nDo not guess."

data class TicketScan(val pnr: String, val date: LocalDate, val train: String?, val from: String?, val to: String?, val passenger: String?) {
    fun toJourney(): Journey = Journey(
        pnr = pnr, train = train, date = date,
        route = if (from != null && to != null) "$from → $to" else from ?: to,
        passenger = passenger, note = "Ticket photo se",
    )
}

data class BillScan(val amountPaise: Long, val shop: String?, val date: LocalDate?)

private fun field(text: String, key: String): String? =
    Regex("^[ \\t]*\\**$key\\**[ \\t]*[:：-][ \\t]*(.*)$", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)).find(text)
        ?.groupValues?.get(1)?.trim()?.trim('*', '"', '<', '>')?.trim()
        ?.takeIf { it.isNotEmpty() && !it.equals("empty", true) && !it.equals("n/a", true) && it != "-" }

private fun digits(s: String): String = s.map { ch ->
    when (ch) {
        in '०'..'९' -> '0' + (ch - '०')
        in '০'..'৯' -> '0' + (ch - '০')
        else -> ch
    }
}.joinToString("")

private val DATE = Regex("(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{2,4})")

/** "15/11/2026", "15-11-26" → the date; null when it is not a real day. */
fun scanDate(raw: String?): LocalDate? {
    val m = DATE.find(digits(raw ?: return null)) ?: return null
    var y = m.groupValues[3].toInt()
    if (y < 100) y += 2000
    return runCatching { LocalDate.of(y, m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull()
}

/** The ticket, or null when the PNR or the journey date is not clear (then nothing is saved). */
fun parseTicketScan(text: String): TicketScan? {
    val t = digits(text)
    val pnr = field(t, "PNR")?.filter(Char::isDigit)?.takeIf { it.length == 10 } ?: return null
    val date = scanDate(field(t, "DATE")) ?: return null
    fun short(key: String, max: Int) = field(t, key)?.take(max)
    return TicketScan(pnr, date, short("TRAIN", 60), short("FROM", 40), short("TO", 40), short("PASSENGER", 50))
}

/** The bill, or null when the total is not a clear amount between ₹1 and ₹10,00,000. */
fun parseBillScan(text: String): BillScan? {
    val t = digits(text)
    val raw = field(t, "TOTAL")?.replace(",", "")?.replace(Regex("(?i)rs\\.?|inr|₹"), "")?.trim() ?: return null
    val m = Regex("^(\\d{1,7})(?:\\.(\\d{1,2}))?$").find(raw) ?: return null
    val paise = m.groupValues[1].toLong() * 100 + m.groupValues[2].padEnd(2, '0').ifEmpty { "00" }.toLong()
    if (paise < 100 || paise > 100_000_000L) return null
    return BillScan(paise, field(t, "SHOP")?.take(40), scanDate(field(t, "DATE")))
}
