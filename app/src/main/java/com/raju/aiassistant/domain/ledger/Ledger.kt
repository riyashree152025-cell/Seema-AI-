package com.raju.aiassistant.domain.ledger

/**
 * Client memory and the udhaar book, said out loud: "Rahul se 500 rupaye mile" (he paid me),
 * "Rahul ko 300 rupaye diye" (I gave him), "Rahul ka hisaab batao" (what he owes me now).
 *
 * Balance = what the client still owes me = total I gave − total I got. Nothing stores a running
 * balance, so it cannot drift away from the entries.
 */
enum class LedgerKind { GAVE, GOT }

sealed interface LedgerCommand {
    data class Record(val name: String, val amountPaise: Long, val kind: LedgerKind) : LedgerCommand
    data class Balance(val name: String) : LedgerCommand
    /** "Aakhri entry hatao": remove the newest entry (after a confirm). */
    data object Undo : LedgerCommand
}

/** One saved entry, for the undo question and the screen. */
data class LedgerEntryInfo(val id: Long, val name: String, val amountPaise: Long, val kind: LedgerKind, val at: Long)

/** A client and what they owe now (negative: I owe them). */
data class ClientBalance(val name: String, val balancePaise: Long, val entries: Int)

/** Client's name and the money in one turn, as entries are summed. */
data class LedgerLine(val kind: LedgerKind, val amountPaise: Long)

/** What the client still owes me (positive) or I owe them (negative), paise. */
fun balanceOf(lines: List<LedgerLine>): Long =
    lines.sumOf { if (it.kind == LedgerKind.GAVE) it.amountPaise else -it.amountPaise }

private const val AMOUNT = "(\\d[\\d,]*(?:\\.\\d{1,2})?)\\s*(hazar|hajar|हज़ार|हजार|k)?"
private const val RUPEE_WORD = "(?:rupaye|rupay|rupee|rupees|rs|rupya|रुपये|रुपए|रुपये|रुपया|₹)?"

private val GOT = Regex("^(.+?) (?:se|से) $AMOUNT\\s*$RUPEE_WORD\\s*(?:wapas\\s+)?(?:mil(?:e|a|gaye|gaya|\\s*gaye)?|aaye|aaya|received|वापस\\s*मिले|मिले|मिला|लौटाए|लौटा\\s*दिए|वापस\\s*किए)\\s*$", RegexOption.IGNORE_CASE)
private val GAVE = Regex("^(.+?) (?:ko|को) $AMOUNT\\s*$RUPEE_WORD\\s*(?:(?:de|diye|diya|di)\\b|udhaar\\s+(?:diye|diya|de)|udhar\\s+(?:diye|diya|de)|दिए|दिया|उधार\\s*दिए|उधार\\s*दिया)\\s*$", RegexOption.IGNORE_CASE)
// "Rahul ne mujhe 500 diye" (he paid me) and "Rahul ne mujhse 500 liye" (he took from me).
private val GOT_NE = Regex("^(.+?) (?:ne|ने) (?:mujhe |mujhko |muje |मुझे |मुझको )?$AMOUNT\\s*$RUPEE_WORD\\s*(?:wapas\\s+|वापस\\s*)?(?:diye|diya|de diye|lautaye|lauta diye|दिए|दिया|दे दिए|लौटाए|लौटा\\s*दिए)\\s*$", RegexOption.IGNORE_CASE)
private val GAVE_NE = Regex("^(.+?) (?:ne|ने) (?:mujhse |mujh se |मुझसे |मुझ से )?$AMOUNT\\s*$RUPEE_WORD\\s*(?:udhaar\\s+|udhar\\s+|उधार\\s*)?(?:liye|liya|le liye|लिए|लिया|ले लिए)\\s*$", RegexOption.IGNORE_CASE)
private val UNDO = Regex("^(?:(?:aakhri|akhri|aakhiri|last|pichli|आख़िरी|आखिरी|पिछली) )(?:udhaar |udhar |khata |खाता |उधार )?(?:entry|एंट्री) (?:hatao|hata do|delete karo|delete|undo karo|undo|wapas lo|हटाओ|हटा दो|डिलीट करो)\\s*$", RegexOption.IGNORE_CASE)

private val BALANCE = Regex("^(.+?) (?:ka|ki|का|की) (?:hisaab|hisab|balance|baaki|baki|bakaya|हिसाब|बकाया|बाकी)(?: (?:batao|bata do|kya hai|kitna hai|btao|बताओ|क्या है|कितना है))?\\s*$|^(?:hisaab|hisab|balance)\\s+(.+?)(?: (?:batao|bata do|kya hai|btao))?\\s*$", RegexOption.IGNORE_CASE)

/** "1,500" → 150000 paise; "5 hazar" → 500000 paise; null when not a sensible amount. */
fun parseAmountPaise(number: String, multiplier: String?): Long? {
    val clean = number.replace(",", "")
    val base = clean.toBigDecimalOrNull() ?: return null
    val factor = if (multiplier.isNullOrEmpty()) 1 else 1000
    val paise = (base * factor.toBigDecimal() * 100.toBigDecimal()).toLong()
    return paise.takeIf { it in 1..100_000_000_00L } // up to one crore rupees
}

/** A udhaar-book sentence, or null: an amount is needed ("itna" is not one) and the name must be a name. */
fun parseLedger(text: String): LedgerCommand? {
    val t = text.trim().replace(Regex("\\s+"), " ").trimEnd('.', '!', '?', '।')
    if (t.length > 120) return null
    if (UNDO.matches(t)) return LedgerCommand.Undo
    BALANCE.matchEntire(t)?.let { m ->
        val name = (m.groupValues[1].ifEmpty { m.groupValues[2] }).trim()
        return if (isName(name)) LedgerCommand.Balance(name) else null
    }
    GOT.matchEntire(t)?.let { m -> return record(m.groupValues[1], m.groupValues[2], m.groupValues[3], LedgerKind.GOT) }
    GAVE.matchEntire(t)?.let { m -> return record(m.groupValues[1], m.groupValues[2], m.groupValues[3], LedgerKind.GAVE) }
    GOT_NE.matchEntire(t)?.let { m -> return record(m.groupValues[1], m.groupValues[2], m.groupValues[3], LedgerKind.GOT) }
    GAVE_NE.matchEntire(t)?.let { m -> return record(m.groupValues[1], m.groupValues[2], m.groupValues[3], LedgerKind.GAVE) }
    return null
}

private fun record(name: String, number: String, multiplier: String, kind: LedgerKind): LedgerCommand? {
    val n = name.trim()
    if (!isName(n)) return null
    val paise = parseAmountPaise(number, multiplier.ifEmpty { null }) ?: return null
    return LedgerCommand.Record(n, paise, kind)
}

/** A name, not a filler word or a number: at least one letter, at most 4 words. */
private fun isName(name: String): Boolean {
    if (name.isBlank() || name.split(' ').size > 4) return false
    if (!name.any { it.isLetter() }) return false
    return name.lowercase() !in setOf("mujhe", "usko", "use", "isko", "mere", "meri", "hum", "main", "mai", "aap", "tum", "ye", "yeh", "woh", "wo", "ek", "itna", "itne")
}

private val SHORT_MONTHS = listOf("जन", "फर", "मार्च", "अप्रै", "मई", "जून", "जुला", "अग", "सित", "अक्टू", "नव", "दिस")

/** "12 नव 2026" for an entry's day. */
fun ledgerDay(atMillis: Long, zone: java.time.ZoneId): String {
    val d = java.time.Instant.ofEpochMilli(atMillis).atZone(zone).toLocalDate()
    return "${d.dayOfMonth} ${SHORT_MONTHS[d.monthValue - 1]} ${d.year}"
}

/**
 * A client's account as text to copy or send (oldest first): each entry with its day, then what is
 * left. Built only from the saved entries.
 */
fun ledgerStatement(name: String, entries: List<LedgerEntryInfo>, zone: java.time.ZoneId): String = buildString {
    val list = entries.filter { it.name == name }.sortedWith(compareBy({ it.at }, { it.id }))
    appendLine("$name का हिसाब")
    list.forEach { e ->
        val what = if (e.kind == LedgerKind.GAVE) "दिए" else "मिले"
        appendLine("${ledgerDay(e.at, zone)} · $what ${com.raju.aiassistant.domain.business.formatRupees(e.amountPaise)}")
    }
    val bal = balanceOf(list.map { LedgerLine(it.kind, it.amountPaise) })
    append(
        when {
            bal > 0 -> "बाकी: ${com.raju.aiassistant.domain.business.formatRupees(bal)} (आपको लेने हैं)"
            bal < 0 -> "बाकी: ${com.raju.aiassistant.domain.business.formatRupees(-bal)} (आपको देने हैं)"
            else -> "हिसाब बराबर है।"
        },
    )
}
