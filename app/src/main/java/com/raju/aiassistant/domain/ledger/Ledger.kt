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
}

/** Client's name and the money in one turn, as entries are summed. */
data class LedgerLine(val kind: LedgerKind, val amountPaise: Long)

/** What the client still owes me (positive) or I owe them (negative), paise. */
fun balanceOf(lines: List<LedgerLine>): Long =
    lines.sumOf { if (it.kind == LedgerKind.GAVE) it.amountPaise else -it.amountPaise }

private const val AMOUNT = "(\\d[\\d,]*(?:\\.\\d{1,2})?)\\s*(hazar|hajar|हज़ार|हजार|k)?"
private const val RUPEE_WORD = "(?:rupaye|rupay|rupee|rupees|rs|rupya|रुपये|रुपए|रुपये|रुपया|₹)?"

private val GOT = Regex("^(.+?) (?:se|से) $AMOUNT\\s*$RUPEE_WORD\\s*(?:wapas\\s+)?(?:mil(?:e|a|gaye|gaya|\\s*gaye)?|aaye|aaya|received|वापस\\s*मिले|मिले|मिला|लौटाए|लौटा\\s*दिए|वापस\\s*किए)\\s*$", RegexOption.IGNORE_CASE)
private val GAVE = Regex("^(.+?) (?:ko|को) $AMOUNT\\s*$RUPEE_WORD\\s*(?:(?:de|diye|diya|di)\\b|udhaar\\s+(?:diye|diya|de)|udhar\\s+(?:diye|diya|de)|दिए|दिया|उधार\\s*दिए|उधार\\s*दिया)\\s*$", RegexOption.IGNORE_CASE)
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
    BALANCE.matchEntire(t)?.let { m ->
        val name = (m.groupValues[1].ifEmpty { m.groupValues[2] }).trim()
        return if (isName(name)) LedgerCommand.Balance(name) else null
    }
    GOT.matchEntire(t)?.let { m -> return record(m.groupValues[1], m.groupValues[2], m.groupValues[3], LedgerKind.GOT) }
    GAVE.matchEntire(t)?.let { m -> return record(m.groupValues[1], m.groupValues[2], m.groupValues[3], LedgerKind.GAVE) }
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
