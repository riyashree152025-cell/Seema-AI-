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
    /** "Aaj kiska-kiska hisab baki hai?": everyone with money pending, and the grand total. */
    data object AllPending : LedgerCommand
    /** "Mohan ka hisab clear kar do": one entry that brings the balance to zero (after a confirm). */
    data class Settle(val name: String) : LedgerCommand
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

private val GOT = Regex("^(.+?) (?:se|से) $AMOUNT\\s*$RUPEE_WORD\\s*(?:wapas\\s+)?(?:mil(?:e|a|gaye|gaya|\\s*gaye)?|aaye|aaya|aa(?:\\s*gaye|\\s*gaya)?|jama(?:\\s+karo|\\s+kiye|\\s+hua)?|received|वापस\\s*मिले|मिले|मिला|मिल\\s*गए|आए|आ\\s*गए|जमा|लौटाए|लौटा\\s*दिए|वापस\\s*किए)\\s*$", RegexOption.IGNORE_CASE)
private val GAVE = Regex("^(.+?) (?:ko|को) $AMOUNT\\s*$RUPEE_WORD\\s*(?:(?:de|diye|diya|di)\\b|de\\s*diye|de\\s*diya|payment\\s+kar\\s+diya|pay\\s+kar\\s+diya|udhaar\\s+(?:diye|diya|de)|udhar\\s+(?:diye|diya|de)|दिए|दिया|दे\\s*दिए|दे\\s*दिया|पेमेंट\\s*कर\\s*दिया|भुगतान\\s*कर\\s*दिया|उधार\\s*दिए|उधार\\s*दिया)\\s*$", RegexOption.IGNORE_CASE)
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
/** "Yaad rakhna, …" / "Seema, …" in front of a money sentence: the sentence itself is what counts. */
private val MEMO_PREFIX = Regex("^(?:(?:seema|सीमा),? )?(?:yaad rakhna|yaad rakho|yad rakhna|याद रखना|याद रखो),? ", RegexOption.IGNORE_CASE)
private val ALL_PENDING = Regex(
    "^(?:aaj )?(?:kiska kiska|kis kis ka|kiska|sabka|sab ka|किसका किसका|किस किस का|किसका|सबका|सब का) (?:hisab|hisaab|हिसाब) (?:baki|baaki|बाकी|batao|बताओ)(?: (?:hai|hain|है|हैं|batao|बताओ))?$|" +
        "^(?:aaj )?(?:आज )?(?:kitna|कितना) (?:hisab|hisaab|हिसाब|paisa|पैसा) (?:baki|baaki|बाकी) (?:hai|है)$",
    RegexOption.IGNORE_CASE,
)
private val SETTLE = Regex(
    "^(.+?) (?:ka|ki|का|की) (?:hisab|hisaab|udhaar|udhar|हिसाब|उधार) (?:clear|saaf|khatam|barabar|क्लियर|साफ़|साफ|ख़त्म|खत्म|बराबर) (?:kar do|karo|kardo|कर दो|करो)$",
    RegexOption.IGNORE_CASE,
)
/** "Mohan bhai se 1500 rupaye lene baki hain" / "Rahul se 500 lena hai": they owe me (as if I gave it). */
private val OWED_TO_ME = Regex("^(.+?) (?:se|से) $AMOUNT\\s*$RUPEE_WORD\\s*(?:lene|लेने|lena|लेना|baaki|baki|बाकी|dega|देगा)(?:\\s+(?:baki|baaki|बाकी|hai|hain|है|हैं))?(?:\\s+(?:hai|hain|है|हैं))?$", RegexOption.IGNORE_CASE)
private val WILL_PAY = Regex("^(.+?) $AMOUNT\\s*$RUPEE_WORD\\s*(?:dega|देगा|degi|देगी)$", RegexOption.IGNORE_CASE)
/** "Mohan ko 500 rupaye dene baki hain" / "Mohan ko 500 dena hai": I owe them (as if they gave it to me). */
private val I_OWE = Regex("^(.+?) (?:ko|को) $AMOUNT\\s*$RUPEE_WORD\\s*(?:dene|देने|dena|देना|baaki|baki|बाकी)(?:\\s+(?:baki|baaki|बाकी|hai|hain|है|हैं))?(?:\\s+(?:hai|hain|है|हैं))?$", RegexOption.IGNORE_CASE)

private val NON_CURRENCY_ITEMS = Regex("(?<![\\p{L}\\p{M}])(?:ticket|tickets|टिकट|टिकिट|টিকিট|pass|passes|पास|seat|seats|सीट|file|files|फाइल|फ़ाइल|photo|photos|फोटो|डॉक्यूमेंट|document)(?![\\p{L}\\p{M}])", RegexOption.IGNORE_CASE)
private val EXPLICIT_CURRENCY = Regex("(?<![\\p{L}\\p{M}])(?:rupaye|rupay|rupee|rupees|rs|rupya|रुपये|रुपए|रुपया|₹|हज़ार|हजार|hazar|hajar|hazaar|sau|so|सौ|lakh|लाख|crore|करोड़|টাকা|পয়সা)(?![\\p{L}\\p{M}])", RegexOption.IGNORE_CASE)

fun parseLedger(text: String): LedgerCommand? {
    val numConverted = com.raju.aiassistant.domain.language.HindiNumberParser.replaceHindiNumbers(text)
    val t = numConverted.trim().replace(Regex("\\s+"), " ").trimEnd('.', '!', '?', '।').replace(MEMO_PREFIX, "")
    if (t.length > 120) return null
    if (NON_CURRENCY_ITEMS.containsMatchIn(text) || NON_CURRENCY_ITEMS.containsMatchIn(t)) return null
    if (UNDO.matches(t)) return LedgerCommand.Undo
    if (ALL_PENDING.matches(t)) return LedgerCommand.AllPending
    SETTLE.matchEntire(t)?.let { m -> val name = m.groupValues[1].trim(); return if (isName(name)) LedgerCommand.Settle(name) else null }
    OWED_TO_ME.matchEntire(t)?.let { m -> return record(m.groupValues[1], m.groupValues[2], m.groupValues[3], LedgerKind.GAVE, t) }
    WILL_PAY.matchEntire(t)?.let { m -> return record(m.groupValues[1], m.groupValues[2], m.groupValues[3], LedgerKind.GAVE, t) }
    I_OWE.matchEntire(t)?.let { m -> return record(m.groupValues[1], m.groupValues[2], m.groupValues[3], LedgerKind.GOT, t) }
    BALANCE.matchEntire(t)?.let { m ->
        val name = (m.groupValues[1].ifEmpty { m.groupValues[2] }).trim()
        return if (isName(name)) LedgerCommand.Balance(name) else null
    }
    GOT.matchEntire(t)?.let { m -> return record(m.groupValues[1], m.groupValues[2], m.groupValues[3], LedgerKind.GOT, t) }
    GAVE.matchEntire(t)?.let { m -> return record(m.groupValues[1], m.groupValues[2], m.groupValues[3], LedgerKind.GAVE, t) }
    GOT_NE.matchEntire(t)?.let { m -> return record(m.groupValues[1], m.groupValues[2], m.groupValues[3], LedgerKind.GOT, t) }
    GAVE_NE.matchEntire(t)?.let { m -> return record(m.groupValues[1], m.groupValues[2], m.groupValues[3], LedgerKind.GAVE, t) }
    return null
}

private fun record(name: String, number: String, multiplier: String, kind: LedgerKind, contextText: String = ""): LedgerCommand? {
    if (NON_CURRENCY_ITEMS.containsMatchIn(contextText)) return null
    val n = name.trim()
    if (!isName(n)) return null
    val paise = parseAmountPaise(number, multiplier.ifEmpty { null }) ?: return null
    val rupees = paise / 100
    if (rupees < 50 && !EXPLICIT_CURRENCY.containsMatchIn(contextText)) return null
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

/**
 * Everyone with money pending, said like a person: who owes how much, who is owed, and the total.
 * Settled names (balance 0) are left out; at most [shown] names are read, the rest are counted.
 */
fun pendingSummary(balances: List<ClientBalance>, shown: Int = 8): String {
    val open = balances.filter { it.balancePaise != 0L }.sortedByDescending { kotlin.math.abs(it.balancePaise) }
    if (open.isEmpty()) return "किसी का कोई हिसाब बाकी नहीं है, सब बराबर है।"
    val toGet = open.filter { it.balancePaise > 0 }.sumOf { it.balancePaise }
    val toGive = open.filter { it.balancePaise < 0 }.sumOf { -it.balancePaise }
    return buildString {
        append("${open.size} लोगों का हिसाब बाकी है। ")
        open.take(shown).forEach { b ->
            val amount = com.raju.aiassistant.domain.business.formatRupees(kotlin.math.abs(b.balancePaise))
            append(if (b.balancePaise > 0) "${b.name} से $amount लेने हैं" else "${b.name} को $amount देने हैं")
            append("। ")
        }
        if (open.size > shown) append("और ${open.size - shown} लोग भी हैं। ")
        append("कुल मिलाकर ${com.raju.aiassistant.domain.business.formatRupees(toGet)} लेने हैं")
        if (toGive > 0) append(" और ${com.raju.aiassistant.domain.business.formatRupees(toGive)} देने हैं")
        append("।")
    }
}

/** The one entry that settles [balancePaise] (they owe me → they paid; I owe them → I paid), or null when already settled. */
fun settlingEntry(balancePaise: Long): LedgerLine? = when {
    balancePaise > 0 -> LedgerLine(LedgerKind.GOT, balancePaise)
    balancePaise < 0 -> LedgerLine(LedgerKind.GAVE, -balancePaise)
    else -> null
}
