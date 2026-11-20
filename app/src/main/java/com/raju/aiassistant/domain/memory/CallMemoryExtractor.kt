package com.raju.aiassistant.domain.memory

import com.raju.aiassistant.domain.sms.asciiDigitsOf
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * After a call Raju tells Seema what was agreed — "Rahul kal 5000 rupaye dega", "shaam ko Amit ko call karna hai",
 * "মিঠু পরশু দুই হাজার টাকা দেবে". Android does not let apps hear a phone call, so the words come from him, said to
 * Seema in the minutes after the call (or any time as "yaad rakhna: …"). The promises are picked out here; the
 * engine turns each into one of Seema's reminders and says one short confirmation — nothing at all when there is
 * no promise.
 */
enum class CommitmentKind {
    /** Someone will pay Raju. */
    MONEY_IN,
    /** Raju has to pay someone. */
    MONEY_OUT,
    /** A call to make. */
    CALL_BACK,
}

data class Commitment(val kind: CommitmentKind, val person: String?, val amountPaise: Long?, val due: LocalDateTime)

object CallMemoryExtractor {
    private fun word(w: String) = Regex("(?<![\\p{L}\\p{M}])(?:$w)(?![\\p{L}\\p{M}])", RegexOption.IGNORE_CASE)

    // Someone gives / sends Raju money: "dega", "denge", "bhej dega", "दे देगा", "দেবে", "পাঠাবে".
    private val THEY_PAY = word(
        "dega|degi|denge|de dega|de degi|de denge|bhejega|bhejegi|bhejenge|bhej dega|bhej degi|bhej denge|lauta dega|lautayega|" +
            "देगा|देगी|देंगे|दे देगा|दे देगी|दे देंगे|भेजेगा|भेजेगी|भेजेंगे|भेज देगा|भेज देगी|भेज देंगे|लौटा देगा|लौटाएगा|" +
            "দেবে|দিয়ে দেবে|দেবেন|পাঠাবে|পাঠিয়ে দেবে|ফেরত দেবে",
    )
    // Raju pays: "mujhe dena hai", "de dunga", "देना है", "দিতে হবে", "দিয়ে দেব".
    private val I_PAY = word(
        "dena hai|dene hai|de dunga|de dena|bhejna hai|bhej dunga|chukana hai|देना है|देने है|दे दूँगा|दे दूंगा|भेजना है|भेज दूँगा|चुकाना है|" +
            "দিতে হবে|দিয়ে দেব|পাঠাতে হবে|পাঠিয়ে দেব|শোধ করতে হবে",
    )
    // A call to make: "call karna hai", "phone karna", "কল করতে হবে".
    private val CALL = word(
        "call karna hai|call karna|phone karna hai|phone karna|call karunga|call kar lena|कॉल करना है|कॉल करना|फोन करना है|फ़ोन करना है|फोन करना|कॉल करूँगा|" +
            "কল করতে হবে|ফোন করতে হবে|কল করব|ফোন করব",
    )
    private val TOMORROW = word("kal|कल|কাল|কালকে|tomorrow")
    private val DAY_AFTER = word("parso|parson|परसों|परसो|পরশু")
    private val TODAY = word("aaj|आज|আজ|আজকে|today")
    private val MORNING = word("subah|savere|सुबह|সকালে|সকাল|morning")
    private val AFTERNOON = word("dopahar|दोपहर|দুপুরে|দুপুর")
    private val EVENING = word("shaam|sham|शाम|সন্ধ্যায়|সন্ধ্যা|বিকেলে|evening")
    private val NIGHT = word("raat|रात|রাতে|night")
    private val IN_DAYS = Regex("(\\d{1,2})\\s*(?:din|दिन|দিন)\\s*(?:mein|me|में|পরে|bad|baad|बाद)", RegexOption.IGNORE_CASE)
    private val AT_HOUR = Regex("(?<!\\d)(\\d{1,2})(?::(\\d{2}))?\\s*(?:baje|बजे|টায়|টার|o'?clock)", RegexOption.IGNORE_CASE)
    private val AMOUNT = Regex("(?<![\\d.,])(\\d{1,3}(?:,\\d{2,3})+|\\d{2,8})(?![\\d])")
    private val THOUSAND_WORD = Regex("(?<![\\p{L}\\p{M}\\d])(\\d{1,3}|ek|do|teen|char|paanch|das|एक|दो|तीन|चार|पाँच|पांच|दस|এক|দুই|তিন|চার|পাঁচ|দশ)\\s*(?:hazaar|hazar|हज़ार|हजार|হাজার)", RegexOption.IGNORE_CASE)
    private val COUNTS = mapOf(
        "ek" to 1, "do" to 2, "teen" to 3, "char" to 4, "paanch" to 5, "das" to 10,
        "एक" to 1, "दो" to 2, "तीन" to 3, "चार" to 4, "पाँच" to 5, "पांच" to 5, "दस" to 10,
        "এক" to 1, "দুই" to 2, "তিন" to 3, "চার" to 4, "পাঁচ" to 5, "দশ" to 10,
    )
    /** Words that are never the person ("kal", "mujhe", "shaam", "ko" …). */
    private val NOT_A_NAME = setOf(
        "kal", "aaj", "parso", "mujhe", "main", "maine", "wo", "woh", "vo", "usne", "unhone", "subah", "shaam", "sham", "raat", "dopahar",
        "कल", "आज", "परसों", "मुझे", "मैं", "मैंने", "वो", "वह", "उसने", "उन्होंने", "सुबह", "शाम", "रात", "दोपहर",
        "আমি", "আমাকে", "কাল", "আজ", "পরশু", "ও", "সে", "সকালে", "সন্ধ্যায়", "yaad", "याद", "rakhna", "रखना", "note", "नोट", "karo", "करो",
        "seema", "सीमा", "সীমা", "ki", "कि", "ke", "ko", "को", "ne", "ने", "se", "से", "কে", "theek", "ठीक", "hai", "है",
    )
    /** A name is followed by one of these ("Amit ko", "अमित ने", "Rahul se") — or starts the sentence. */
    private val PARTICLES = setOf("ko", "ne", "se", "को", "ने", "से", "কে", "কাছ")
    private val NUMBER_WORDS = Regex("^(?:ek|do|teen|char|paanch|das|hazaar|hazar|rupaye|rupees|एक|दो|तीन|चार|पाँच|पांच|दस|हज़ार|हजार|रुपये|এক|দুই|তিন|চার|পাঁচ|দশ|হাজার|টাকা)$", RegexOption.IGNORE_CASE)

    fun amountPaise(text: String): Long? {
        val t = asciiDigitsOf(text)
        THOUSAND_WORD.find(t)?.let { m ->
            val n = m.groupValues[1].toIntOrNull() ?: COUNTS[m.groupValues[1].lowercase()] ?: return@let
            return n * 1_000L * 100
        }
        return AMOUNT.findAll(withoutTimes(t)).mapNotNull { it.groupValues[1].replace(",", "").toLongOrNull() }
            .firstOrNull { it >= 10 }?.let { it * 100 }
    }

    private fun withoutTimes(t: String) = AT_HOUR.replace(IN_DAYS.replace(t, " "), " ")

    private fun person(text: String): String? {
        val words = text.trim().split(Regex("[\\s,:;]+")).map { it.trim('.', '!', '?', '।') }.filter { it.isNotEmpty() }
        words.forEachIndexed { i, w ->
            val lw = w.lowercase()
            if (lw in NOT_A_NAME || w.any { it.isDigit() } || NUMBER_WORDS.matches(w) || !w.all { it.isLetter() || Character.getType(it).let { t -> t == Character.NON_SPACING_MARK.toInt() || t == Character.COMBINING_SPACING_MARK.toInt() } }) return@forEachIndexed
            val next = words.getOrNull(i + 1)?.lowercase()
            val firstReal = words.take(i).all { it.lowercase() in NOT_A_NAME }
            if (next in PARTICLES || (i == 0) || (firstReal && next != null && TOMORROW.containsMatchIn(next) || firstReal && next != null && DAY_AFTER.containsMatchIn(next))) return w
            return null // the first real word is not a name ("Rahul dega" has one, "theek hai" does not)
        }
        return null
    }

    /** When it is due: the day said (kal / parso / N din mein / aaj) and the hour or part of the day said. */
    fun dueOf(text: String, now: LocalDateTime): LocalDateTime {
        val t = asciiDigitsOf(text)
        val day: LocalDate = when {
            IN_DAYS.containsMatchIn(t) -> now.toLocalDate().plusDays(IN_DAYS.find(t)!!.groupValues[1].toLong())
            DAY_AFTER.containsMatchIn(t) -> now.toLocalDate().plusDays(2)
            TOMORROW.containsMatchIn(t) -> now.toLocalDate().plusDays(1)
            else -> now.toLocalDate()
        }
        val evening = EVENING.containsMatchIn(t) || NIGHT.containsMatchIn(t)
        val time: LocalTime = AT_HOUR.find(t)?.let { m ->
            var h = m.groupValues[1].toInt().coerceIn(0, 23)
            val min = m.groupValues[2].toIntOrNull()?.coerceIn(0, 59) ?: 0
            // "5 baje" in business talk is 5 PM unless the morning was said.
            if (h in 1..7 && !MORNING.containsMatchIn(t)) h += 12
            if (h in 8..11 && evening) h += 12
            LocalTime.of(h, min)
        } ?: when {
            MORNING.containsMatchIn(t) -> LocalTime.of(9, 30)
            AFTERNOON.containsMatchIn(t) -> LocalTime.of(13, 0)
            NIGHT.containsMatchIn(t) -> LocalTime.of(20, 30)
            EVENING.containsMatchIn(t) -> LocalTime.of(18, 0)
            else -> LocalTime.of(10, 0)
        }
        val due = day.atTime(time)
        // Today but the time has passed ("aaj 10 baje" said at noon): in an hour instead.
        return if (due.isBefore(now)) now.plusHours(1).withSecond(0).withNano(0) else due
    }

    /** The promises in what Raju said; empty when there is none (then Seema says nothing about it). */
    fun extract(text: String, now: LocalDateTime): List<Commitment> {
        val t = text.trim()
        if (t.length < 6 || t.length > 300) return emptyList()
        val due = dueOf(t, now)
        val who = person(t)
        val amount = amountPaise(t)
        return buildList {
            when {
                amount != null && THEY_PAY.containsMatchIn(t) -> add(Commitment(CommitmentKind.MONEY_IN, who, amount, due))
                amount != null && I_PAY.containsMatchIn(t) -> add(Commitment(CommitmentKind.MONEY_OUT, who, amount, due))
            }
            if (CALL.containsMatchIn(t)) add(Commitment(CommitmentKind.CALL_BACK, who, null, due))
        }
    }

    /** The reminder text Seema will say at that time. */
    fun reminderText(c: Commitment, bengali: Boolean): String {
        val amount = c.amountPaise?.let { com.raju.aiassistant.domain.sms.spokenRupees(it) }
        val who = c.person
        return when (c.kind) {
            CommitmentKind.MONEY_IN -> if (bengali) "${who ?: "একজনের"} থেকে $amount টাকা আসার কথা" else "${who?.let { "$it से " } ?: ""}$amount रुपये आने हैं"
            CommitmentKind.MONEY_OUT -> if (bengali) "${who?.let { "$it-কে " } ?: ""}$amount টাকা দিতে হবে" else "${who?.let { "$it को " } ?: ""}$amount रुपये देने हैं"
            CommitmentKind.CALL_BACK -> if (bengali) "${who?.let { "$it-কে " } ?: ""}ফোন করতে হবে" else "${who?.let { "$it को " } ?: ""}कॉल करना है"
        }
    }
}
