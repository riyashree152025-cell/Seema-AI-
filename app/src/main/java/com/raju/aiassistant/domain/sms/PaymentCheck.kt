package com.raju.aiassistant.domain.sms

import java.time.Instant
import java.time.ZoneId

/**
 * "3750.75 ka refund aaya ki nahi?" — looks through the phone's own bank SMS for that exact amount and
 * says whether it came back (credited / refunded / reversed) or only went out. Everything happens on the
 * phone: no SMS text is ever sent to the AI or the internet, and one-time-password messages are skipped.
 */
data class SmsMessage(val sender: String, val body: String, val atMillis: Long)

sealed interface SmsInbox {
    data class Messages(val list: List<SmsMessage>) : SmsInbox
    data class NeedsPermission(val permission: String, val reason: String) : SmsInbox
    data class Failed(val message: String) : SmsInbox
}

enum class MoneyDirection { CREDIT, DEBIT }

data class PaymentHit(val message: SmsMessage, val direction: MoneyDirection)

/** The question: an amount in paise ("3,750.75" → 375075). */
data class PaymentQuery(val amountPaise: Long)

private fun asciiDigits(s: String): String = s.map { ch ->
    when (ch) {
        in '०'..'९' -> '0' + (ch - '०')
        in '০'..'৯' -> '0' + (ch - '০')
        else -> ch
    }
}.joinToString("")

private val ASKS_REFUND = Regex(
    "refund|refnd|रिफंड|রিফান্ড|wapas|vapas|वापस|ফেরত|credit|क्रेडिट|jama|जमा|জমা|" +
        "(?:paisa|paise|पैसा|पैसे|টাকা|amount)\\s+(?:aaya|aya|aayi|आया|आयी|आए|এসেছে)|" +
        "(?:aaya|aya|आया)\\s+(?:ki|kya|कि|क्या)\\s+(?:nahi|nahin|नहीं)|check\\s+karo|चेक\\s+करो",
    RegexOption.IGNORE_CASE,
)
private val AMOUNT_IN_QUESTION = Regex("(?:rs\\.?|inr|₹|रु\\.?|रुपये|रुपया)?\\s*(\\d{1,3}(?:,\\d{2,3})+(?:\\.\\d{1,2})?|\\d+(?:\\.\\d{1,2})?)")

/** "Seema 3750.75 ka refund aaya ki nahi check karo" → 375075 paise; null when it is not this question. */
fun parsePaymentQuestion(text: String): PaymentQuery? {
    val t = asciiDigits(text).trim()
    if (t.length > 120 || !ASKS_REFUND.containsMatchIn(t)) return null
    val amounts = AMOUNT_IN_QUESTION.findAll(t).mapNotNull { toPaise(it.groupValues[1]) }.filter { it >= 100 }.toList()
    // One amount only: "10 baje 3750 ka refund" has two numbers and is not clear enough.
    return amounts.singleOrNull()?.let { PaymentQuery(it) }
}

private fun toPaise(raw: String): Long? {
    val clean = raw.replace(",", "")
    val parts = clean.split('.')
    val rupees = parts[0].toLongOrNull() ?: return null
    val paise = parts.getOrNull(1)?.padEnd(2, '0')?.take(2)?.toLongOrNull() ?: 0L
    return rupees * 100 + paise
}

// Amounts as banks write them: Rs.3,750.75 · INR 3750.75 · ₹3,750.75 · Rs 3750 · 3,750.75 credited.
private val AMOUNT_IN_SMS = Regex("(?:rs\\.?|inr|₹|amt\\.?|amount)\\s*:?\\s*(\\d{1,3}(?:,\\d{2,3})+(?:\\.\\d{1,2})?|\\d+(?:\\.\\d{1,2})?)", RegexOption.IGNORE_CASE)
private val CREDIT = Regex("\\b(credited|credit(?:ed)? to|refund(?:ed)?|reversed|reversal|received|deposited|cr\\.?)\\b|जमा|प्राप्त|রিফান্ড|জমা", RegexOption.IGNORE_CASE)
private val DEBIT = Regex("\\b(debited|debit(?:ed)? from|spent|paid|withdrawn|sent|dr\\.?|purchase)\\b|डेबिट|কাটা", RegexOption.IGNORE_CASE)
private val OTP = Regex("\\b(otp|one[ -]time password|verification code|do not share)\\b|ओटीपी", RegexOption.IGNORE_CASE)

/** The bank messages with exactly this amount, newest first, each marked as money in or out. */
fun findPayments(messages: List<SmsMessage>, query: PaymentQuery): List<PaymentHit> = messages
    .filter { !OTP.containsMatchIn(it.body) }
    .mapNotNull { m ->
        val body = asciiDigits(m.body)
        val has = AMOUNT_IN_SMS.findAll(body).any { toPaise(it.groupValues[1]) == query.amountPaise }
        if (!has) return@mapNotNull null
        directionOf(body)?.let { PaymentHit(m, it) }
    }
    .sortedByDescending { it.message.atMillis }

private val REFUND_WORD = Regex("refund|reversed|reversal|रिफंड", RegexOption.IGNORE_CASE)

/** Money in or out by the bank's words; null for a message that is neither. */
internal fun directionOf(body: String): MoneyDirection? {
    val credit = CREDIT.containsMatchIn(body)
    val debit = DEBIT.containsMatchIn(body)
    return when {
        credit && !debit -> MoneyDirection.CREDIT
        // "Refund of Rs X debited earlier is credited": a refund word wins over "debited".
        credit && REFUND_WORD.containsMatchIn(body) -> MoneyDirection.CREDIT
        // "Rs 500 debited from a/c ..., credited to VPA shop@upi" is money going out.
        debit -> MoneyDirection.DEBIT
        else -> null
    }
}

internal fun isOtp(body: String): Boolean = OTP.containsMatchIn(body)

/** The transaction amount of a bank SMS in paise: the first amount that is not the balance or a limit. */
internal fun transactionPaise(body: String): Long? = AMOUNT_IN_SMS.findAll(body).firstOrNull { m ->
    val before = body.substring((m.range.first - 14).coerceAtLeast(0), m.range.first).lowercase()
    !Regex("bal|limit|बैलेंस|शेष").containsMatchIn(before)
}?.let { toPaise(it.groupValues[1]) }

internal fun asciiDigitsOf(s: String): String = asciiDigits(s)

internal val HINDI_MONTHS get() = MONTHS

/** "3,750.75" for speaking and showing (Indian grouping). */
fun rupees(paise: Long): String {
    val r = paise / 100
    val p = paise % 100
    val s = r.toString()
    val grouped = if (s.length <= 3) s else {
        val last3 = s.takeLast(3)
        val rest = s.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
        "$rest,$last3"
    }
    return if (p == 0L) grouped else "$grouped.${p.toString().padStart(2, '0')}"
}

/** What Seema says: the credit if one came, otherwise only the debit, otherwise nothing found. */
fun spokenPaymentAnswer(query: PaymentQuery, hits: List<PaymentHit>, zone: ZoneId, days: Int): String {
    val amount = "₹${rupees(query.amountPaise)}"
    fun day(m: SmsMessage): String {
        val d = Instant.ofEpochMilli(m.atMillis).atZone(zone).toLocalDate()
        return "${d.dayOfMonth} ${MONTHS[d.monthValue - 1]}"
    }
    val credit = hits.firstOrNull { it.direction == MoneyDirection.CREDIT }
    val debit = hits.firstOrNull { it.direction == MoneyDirection.DEBIT }
    return when {
        credit != null -> "हाँ, $amount आपके खाते में आ गए हैं — ${day(credit.message)} को, ${credit.message.sender} के संदेश में।"
        debit != null -> "$amount कटने का संदेश ${day(debit.message)} का है (${debit.message.sender}), पर इसका रिफंड या जमा होने का कोई संदेश पिछले $days दिनों में नहीं मिला।"
        else -> "पिछले $days दिनों के SMS में $amount का कोई बैंक संदेश नहीं मिला।"
    }
}

private val MONTHS = listOf("जनवरी", "फरवरी", "मार्च", "अप्रैल", "मई", "जून", "जुलाई", "अगस्त", "सितंबर", "अक्टूबर", "नवंबर", "दिसंबर")
