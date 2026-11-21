package com.raju.aiassistant.domain.business

import com.raju.aiassistant.domain.ledger.ClientBalance
import com.raju.aiassistant.domain.sms.spokenRupees

/**
 * Money came in (bank SMS / UPI notification): when the sender is someone who owes him in the khata, Seema asks
 * "Rahul का 5,000 बाकी था — इस पैसे से खाता घटा दूँ?" and records it only on his yes. And amounts he is waiting for
 * ("3750 ka refund aaye to batana", "Rahul ke 5000 aaye to batana") are watched: when that amount comes, she says so
 * first thing.
 */
object MoneyMatch {
    /** The khata client the payer is (first name or full name, any case), only one who owes something. */
    fun client(payer: String?, balances: List<ClientBalance>): ClientBalance? {
        val p = payer?.trim()?.lowercase()?.takeIf { it.length >= 2 } ?: return null
        val owing = balances.filter { it.balancePaise > 0 }
        owing.firstOrNull { it.name.lowercase() == p }?.let { return it }
        val first = p.split(Regex("\\s+")).first()
        return owing.filter { it.name.lowercase().split(Regex("\\s+")).first() == first }.singleOrNull()
    }

    fun question(c: ClientBalance, paise: Long): String =
        "${c.name} के खाते में ${spokenRupees(c.balancePaise)} रुपये बाकी थे — अभी ${spokenRupees(paise)} रुपये आए हैं। खाते से घटा दूँ?"

    fun done(c: ClientBalance, paise: Long): String {
        val left = (c.balancePaise - paise).coerceAtLeast(0)
        return if (left == 0L) "हो गया, ${c.name} का पूरा हिसाब चुकता।" else "हो गया, अब ${c.name} पर ${spokenRupees(left)} रुपये बाकी हैं।"
    }
}

/** "3750 ka refund aaye to batana", "Rahul ke 5000 aaye to batana", "৩৭৫০ টাকা এলে বলবে". */
data class AmountWatch(val paise: Long, val who: String?)

object AmountWatches {
    private val ASK = Regex(
        "^(?:seema[, ]+)?(?:(.{2,25}?)\\s+(?:ka|ke|ki|का|के|की|এর|র)\\s+)?(?:₹|rs\\.?\\s*)?(\\d{2,8})(?:\\s*(?:rupaye|rupees|रुपये|रुपए|টাকা))?\\s*(?:ka|ki|का|की)?\\s*(?:refund|रिफंड|রিফান্ড|payment|पेमेंट)?\\s*" +
            "(?:aaye|aaye to|aa jaye|aa jaaye to|आए|आए तो|आ जाए तो|এলে|আসলে)\\s*(?:to\\s+|तो\\s+)?(?:batana|bata dena|bolna|बताना|बता देना|बोलना|বলবে|বলো|জানিও)[?।.!]*$",
        RegexOption.IGNORE_CASE,
    )

    fun parse(text: String): AmountWatch? {
        val digits = text.map { c -> if (c in '०'..'९') '0' + (c - '०') else if (c in '০'..'৯') '0' + (c - '০') else c }.joinToString("").replace(",", "")
        val m = ASK.matchEntire(digits.trim()) ?: return null
        val rupees = m.groupValues[2].toLongOrNull() ?: return null
        return AmountWatch(rupees * 100, m.groupValues[1].trim().ifEmpty { null })
    }

    /** The watch this payment answers (same amount, within a rupee), or null. */
    fun match(watches: List<AmountWatch>, paise: Long): AmountWatch? = watches.firstOrNull { kotlin.math.abs(it.paise - paise) <= 100 }

    fun confirmation(w: AmountWatch): String = "ठीक है, ${w.who?.let { "$it के " } ?: ""}${spokenRupees(w.paise)} रुपये आते ही बता दूँगी।"

    fun arrived(w: AmountWatch): String = "खुशख़बरी! जिसका इंतज़ार था — ${w.who?.let { "$it के " } ?: ""}${spokenRupees(w.paise)} रुपये आ गए।"
}
