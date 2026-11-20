package com.raju.aiassistant.domain.sms

import com.raju.aiassistant.domain.language.SeemaLanguage

/**
 * "₹500 आया तो बोल दो": a bank SMS ("INR 500.00 credited to your IndusInd Bank A/c…") or a UPI app's
 * notification ("Rahul paid you ₹500", "Received ₹500 from Rahul") is turned into one spoken line:
 * "राजू, आपके IndusInd Bank खाते में अभी 500 रुपये आए हैं — Rahul ने भेजे।"
 * Read on the phone from the notification only; nothing is stored, logged or sent anywhere. OTPs,
 * money requests, cashback offers, loan offers and debits are never announced.
 */
data class MoneyIn(val paise: Long, val from: String?, val bank: String?)

/** The apps whose notifications can say that money came: SMS apps, UPI apps, and banks' own apps. */
fun isMoneyApp(packageName: String): Boolean =
    packageName in MONEY_APPS || Regex("bank|\\.upi|paisa|wallet", RegexOption.IGNORE_CASE).containsMatchIn(packageName)

private val MONEY_APPS = setOf(
    // SMS apps (bank messages)
    "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms", "com.android.messaging",
    "com.miui.mms", "com.oneplus.mms", "com.coloros.mms", "com.truecaller",
    // UPI apps
    "com.phonepe.app", "com.google.android.apps.nbu.paisa.user", "net.one97.paytm", "in.org.npci.upiapp",
    "in.amazon.mShop.android.shopping", "com.mobikwik_new", "com.freecharge.android", "com.myairtelapp",
    "com.dreamplug.androidapp", "money.super.payments",
)

// Not money that came in: offers, requests, loans, bills, OTPs, failures.
private val NOT_MONEY_IN = Regex(
    "\\b(otp|request(?:ed|ing)?|collect|cashback|reward|offer|win|won|loan|eligible|pre-?approved|limit|due|bill|" +
        "expir\\w*|voucher|coupon|will be credited|failed|declined|unsuccessful|pending|emi|insurance|apply|kyc|statement)\\b|" +
        "अनुरोध|ऑफर|लोन|कैशबैक",
    RegexOption.IGNORE_CASE,
)
// UPI apps write the payer first: "Rahul paid you ₹500", "sent you ₹500".
private val PAID_YOU = Regex("\\b(?:paid|sent|transferred)\\s+you\\b", RegexOption.IGNORE_CASE)

/** The money that came in, from a notification's [title] and [text]; null when it is anything else. */
fun moneyCreditIn(title: String?, text: String?): MoneyIn? {
    val raw = listOfNotNull(title, text).joinToString(" \n ").trim()
    if (raw.isEmpty() || raw.length > 600) return null
    val body = asciiDigitsOf(raw)
    if (isOtp(body) || NOT_MONEY_IN.containsMatchIn(body)) return null
    val paidYou = PAID_YOU.find(body)
    val normalized = if (paidYou != null) body.replace(PAID_YOU, "received") else body
    if (directionOf(normalized) != MoneyDirection.CREDIT) return null
    val paise = transactionPaise(normalized)?.takeIf { it >= 100 } ?: return null
    // The payer: "Rahul paid you" (the words before), or "from Rahul" / "by Rahul" (not a VPA or an account).
    val from = (paidYou?.let { cleanName(body.substring(0, it.range.first).substringAfterLast('\n')) }
        ?: FROM_NAME.find(body)?.groupValues?.get(1)?.let { cleanName(it) })
    val bank = BANKS.firstOrNull { (re, _) -> re.containsMatchIn(body) }?.second
    return MoneyIn(paise, from, bank)
}

private val FROM_NAME = Regex("\\b(?:from|by)\\s+(?:mr\\.?|ms\\.?|mrs\\.?)?\\s*([A-Za-z][A-Za-z .]{1,40}?)(?=\\s+(?:on|via|to|ref|upi|in|at|for|a/?c)\\b|[.,;(\\n]|$)", RegexOption.IGNORE_CASE)
private val NOT_A_NAME = Regex("\\b(upi|imps|neft|rtgs|vpa|a/?c|account|bank|ref|txn|transfer|you|your|nach|ach|atm|cash|salary|interest)\\b", RegexOption.IGNORE_CASE)

internal fun cleanName(s: String): String? {
    val n = s.trim().trim('.', ',', ':', '-').replace(Regex("\\s+"), " ")
    if (n.isEmpty() || n.length > 30 || n.any { it.isDigit() || it == '@' || it == '₹' } || NOT_A_NAME.containsMatchIn(n)) return null
    if (n.split(' ').size > 3) return null
    // "RAHUL KUMAR" is said as "Rahul Kumar".
    return n.split(' ').joinToString(" ") { w -> if (w.length > 1 && w.all { it.isUpperCase() }) w.lowercase().replaceFirstChar { it.uppercase() } else w }
}

private val BANKS = listOf(
    "indusind" to "IndusInd Bank", "\\bsbi\\b|state bank" to "SBI", "hdfc" to "HDFC Bank", "icici" to "ICICI Bank",
    "axis" to "Axis Bank", "kotak" to "Kotak Bank", "\\bpnb\\b|punjab national" to "PNB", "bank of baroda|\\bbob\\b" to "Bank of Baroda",
    "canara" to "Canara Bank", "union bank" to "Union Bank", "idfc" to "IDFC First Bank", "yes bank" to "Yes Bank",
    "federal bank" to "Federal Bank", "bank of india|\\bboi\\b" to "Bank of India", "central bank" to "Central Bank",
    "indian bank" to "Indian Bank", "uco bank" to "UCO Bank", "bandhan" to "Bandhan Bank", "airtel payments" to "Airtel Payments Bank",
    "paytm payments" to "Paytm Payments Bank", "india post|ippb" to "India Post Bank", "au small|au bank" to "AU Bank",
).map { (re, name) -> Regex(re, RegexOption.IGNORE_CASE) to name }

/** What Seema says. [name]: the user's name (the line is theirs), or empty. */
fun spokenMoneyIn(m: MoneyIn, name: String, language: SeemaLanguage): String {
    val amount = rupees(m.paise)
    val who = name.trim().takeIf { it.isNotEmpty() }?.let { "$it, " } ?: ""
    return if (language == SeemaLanguage.BENGALI) {
        val where = m.bank?.let { "আপনার $it অ্যাকাউন্টে" } ?: "আপনার অ্যাকাউন্টে"
        "$who$where এইমাত্র $amount টাকা এসেছে" + (m.from?.let { " — $it পাঠিয়েছে।" } ?: "।")
    } else {
        val where = m.bank?.let { "आपके $it खाते में" } ?: "आपके खाते में"
        "$who$where अभी $amount रुपये आए हैं" + (m.from?.let { " — $it ने भेजे।" } ?: "।")
    }
}

/**
 * One payment shows up as a bank SMS and a UPI notification (and the app may post it twice): the same
 * amount within [windowMs] is said once.
 */
class MoneyAlertDeduper(private val windowMs: Long = 5 * 60_000L) {
    private val said = ArrayDeque<Pair<Long, Long>>() // paise to time

    @Synchronized
    fun shouldSay(paise: Long, now: Long): Boolean {
        while (said.isNotEmpty() && now - said.first().second > windowMs) said.removeFirst()
        if (said.any { it.first == paise }) return false
        said.addLast(paise to now)
        return true
    }
}
