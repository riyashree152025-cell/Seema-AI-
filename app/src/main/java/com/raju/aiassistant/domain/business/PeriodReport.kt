package com.raju.aiassistant.domain.business

import com.raju.aiassistant.domain.ledger.ClientBalance
import com.raju.aiassistant.domain.sms.spokenRupees
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * "Is hafte ka hisaab", "is mahine ki report", "pichle mahine ka hisaab", "এই মাসের হিসাব": the bank money in and out
 * (from the SMS on the phone), the khata expenses, the journeys booked in the diary, and who still owes — in a few
 * spoken sentences. Commission is not kept by the app, so it is not guessed.
 */
enum class ReportPeriod { THIS_WEEK, LAST_WEEK, THIS_MONTH, LAST_MONTH }

object PeriodReport {
    private val ASK = Regex(
        "^(?:seema[, ]+)?(?:(is|iss|pichle|pichhle|last|this|इस|पिछले|এই|গত)\\s+)(hafte|hafta|week|mahine|mahina|month|हफ्ते|हफ़्ते|हफ्ता|महीने|महीना|সপ্তাহের|সপ্তাহ|মাসের|মাস)" +
            "\\s*(?:ka|ki|के|का|की|এর)?\\s*(?:hisaab|hisab|report|summary|munafa|kamai|हिसाब|रिपोर्ट|मुनाफ़ा|मुनाफा|कमाई|হিসাব|রিপোর্ট|লাভ)" +
            "(?:\\s+(?:batao|bata do|sunao|do|बताओ|सुनाओ|दो|বলো|দাও))?[?।.]*$",
        RegexOption.IGNORE_CASE,
    )

    fun ask(text: String): ReportPeriod? {
        val m = ASK.matchEntire(text.trim()) ?: return null
        val last = m.groupValues[1].lowercase() in setOf("pichle", "pichhle", "last", "पिछले", "গত")
        val month = Regex("mahin|month|महीन|মাস", RegexOption.IGNORE_CASE).containsMatchIn(m.groupValues[2])
        return when {
            month && last -> ReportPeriod.LAST_MONTH
            month -> ReportPeriod.THIS_MONTH
            last -> ReportPeriod.LAST_WEEK
            else -> ReportPeriod.THIS_WEEK
        }
    }

    /** First and last day (inclusive) of [p] as of [today]; the current period ends today. */
    fun range(p: ReportPeriod, today: LocalDate): Pair<LocalDate, LocalDate> = when (p) {
        ReportPeriod.THIS_WEEK -> today.with(DayOfWeek.MONDAY) to today
        ReportPeriod.LAST_WEEK -> today.with(DayOfWeek.MONDAY).minusWeeks(1).let { it to it.plusDays(6) }
        ReportPeriod.THIS_MONTH -> today.withDayOfMonth(1) to today
        ReportPeriod.LAST_MONTH -> today.minusMonths(1).withDayOfMonth(1).let { it to it.withDayOfMonth(it.lengthOfMonth()) }
    }

    private fun name(p: ReportPeriod, bengali: Boolean) = when (p) {
        ReportPeriod.THIS_WEEK -> if (bengali) "এই সপ্তাহে" else "इस हफ़्ते"
        ReportPeriod.LAST_WEEK -> if (bengali) "গত সপ্তাহে" else "पिछले हफ़्ते"
        ReportPeriod.THIS_MONTH -> if (bengali) "এই মাসে" else "इस महीने"
        ReportPeriod.LAST_MONTH -> if (bengali) "গত মাসে" else "पिछले महीने"
    }

    /** [cash] null: the SMS could not be read. */
    fun build(p: ReportPeriod, cash: Cashflow?, expensesPaise: Long, journeys: Int, balances: List<ClientBalance>, bengali: Boolean): String {
        val n = name(p, bengali)
        val parts = mutableListOf<String>()
        parts += when {
            cash == null -> if (bengali) "$n-এর ব্যাংকের হিসাব দেখতে SMS-এর অনুমতি লাগবে।" else "$n का बैंक हिसाब देखने के लिए SMS की अनुमति चाहिए।"
            bengali -> "$n ব্যাংকে ${spokenRupees(cash.inPaise)} টাকা এসেছে (${cash.inCount} বার), ${spokenRupees(cash.outPaise)} টাকা কেটেছে (${cash.outCount} বার)।"
            else -> "$n बैंक में ${spokenRupees(cash.inPaise)} रुपये आए (${cash.inCount} बार), ${spokenRupees(cash.outPaise)} रुपये कटे (${cash.outCount} बार)।"
        }
        if (cash != null) {
            val net = cash.inPaise - cash.outPaise
            parts += if (bengali) (if (net >= 0) "মোট ${spokenRupees(net)} টাকা বেশি এসেছে।" else "মোট ${spokenRupees(-net)} টাকা বেশি গেছে।")
            else (if (net >= 0) "यानी ${spokenRupees(net)} रुपये ज़्यादा आए।" else "यानी ${spokenRupees(-net)} रुपये ज़्यादा गए।")
        }
        if (expensesPaise > 0) parts += if (bengali) "খাতায় খরচ ${spokenRupees(expensesPaise)} টাকা।" else "खाते में खर्च ${spokenRupees(expensesPaise)} रुपये।"
        if (journeys > 0) parts += if (bengali) "ডায়েরিতে ${journeys}টা যাত্রা।" else "डायरी में $journeys यात्राएँ।"
        val due = balances.filter { it.balancePaise > 0 }
        if (due.isNotEmpty()) {
            val total = spokenRupees(due.sumOf { it.balancePaise })
            parts += if (bengali) "${due.size} জনের কাছে মোট $total টাকা বাকি।" else "${due.size} लोगों पर कुल $total रुपये बाकी हैं।"
        }
        return parts.joinToString(" ")
    }
}

/**
 * "Rahul ko bakaya yaad dilao", "Rahul ko payment reminder bhejo", "রাহুলকে বাকি টাকার কথা মনে করিয়ে দাও": a polite
 * WhatsApp to that person with what the khata says they owe — sent only after Raju's yes (the usual confirm).
 */
object DuesReminder {
    private val ASK = Regex(
        "^(?:seema[, ]+)?(.{1,30}?)\\s*(?:ko|को|কে)\\s+(?:(?:uska|unka|उसका|उनका)\\s+)?(?:bakaya|baki|baaki|udhaar|udhar|payment|paisa|paise|बकाया|बाकी|उधार|पेमेंट|पैसे|পাওনা|বাকি টাকা(?:র কথা)?|বাকি)\\s*" +
            "(?:ka\\s+|का\\s+)?(?:yaad dilao|yaad dila do|reminder bhejo|reminder bhej do|याद दिलाओ|याद दिला दो|रिमाइंडर भेजो|মনে করিয়ে দাও|রিমাইন্ডার পাঠাও)[?।.]*$",
        RegexOption.IGNORE_CASE,
    )

    fun who(text: String): String? = ASK.matchEntire(text.trim())?.groupValues?.get(1)?.trim()?.takeIf { it.length >= 2 }

    fun message(name: String, owedPaise: Long): String =
        "नमस्ते $name जी, R Travels की ओर से एक छोटी-सी याद — आपके ₹${String.format(java.util.Locale.US, "%,d", owedPaise / 100)} बाकी हैं। सुविधा हो तो भेज दीजिए। धन्यवाद 🙏"
}
