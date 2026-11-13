package com.raju.aiassistant.domain.reminder

import com.raju.aiassistant.domain.tatkal.TatkalAlarm
import com.raju.aiassistant.domain.tatkal.tatkalWindows
import java.time.LocalDateTime

/**
 * "Anand ka tatkal booking hai yaad dila dena" has no time, so the reminder parser cannot set it and the
 * AI used to answer something unrelated. A Tatkal booking has its own time: the reminder is set 5 minutes
 * before the next opening (AC 10:00, or Sleeper 11:00 when "sleeper" is said). Any other "yaad dila dena"
 * without a time gets one short question back instead of a guess.
 */
private val REMIND = Regex("yaad\\s*dila|yad\\s*dila|remind|याद\\s*दिला|रिमाइंड", RegexOption.IGNORE_CASE)
private val TATKAL = Regex("tatkal|tatkaal|tatakal|तत्काल|ततकाल", RegexOption.IGNORE_CASE)
private val SLEEPER = Regex("sleeper|slipar|स्लीपर|non ?ac|नॉन ?एसी", RegexOption.IGNORE_CASE)
private val CLOCK = Regex("\\d{1,2}[:.]\\d{2}|\\d{1,2}\\s*(?:baje|बजे|minute|मिनट|ghante|घंटे|am|pm)", RegexOption.IGNORE_CASE)
private val NAME = Regex("^(?:(?:hey|hi|ok|हे)\\s+)?(?:seema|सीमा)?[,\\s]*(.+?)\\s+(?:ka|ki|ke|का|की|के)\\s+(?:(?:sleeper|slipar|ac|एसी|स्लीपर)\\s+)?(?:tatkal|tatkaal|tatakal|तत्काल|ततकाल)", RegexOption.IGNORE_CASE)

data class TatkalReminderAsk(val name: String?, val sleeper: Boolean)

/** "Anand ka tatkal booking hai yaad dila dena" → (Anand, AC); null when it is not that sentence. */
fun parseTatkalReminder(text: String): TatkalReminderAsk? {
    val t = text.trim().trimEnd('.', '!', '?', '।')
    if (!REMIND.containsMatchIn(t) || !TATKAL.containsMatchIn(t) || CLOCK.containsMatchIn(t)) return null
    val name = NAME.find(t)?.groupValues?.get(1)?.trim()
        ?.takeIf { it.isNotEmpty() && it.length <= 30 && it.split(' ').size <= 3 && it.none(Char::isDigit) }
        ?.split(' ')?.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    return TatkalReminderAsk(name, SLEEPER.containsMatchIn(t))
}

/** When to remind: 5 minutes before the next opening of that class (at least one minute from now). */
fun tatkalReminderAt(now: LocalDateTime, sleeper: Boolean): LocalDateTime {
    val w = tatkalWindows(now).first { (it.label == TatkalAlarm.SLEEPER) == sleeper }
    val opens = if (w.opensAt.isAfter(now)) w.opensAt else w.opensAt.plusDays(1)
    val at = opens.minusMinutes(5)
    return if (at.isAfter(now.plusMinutes(1))) at else now.plusMinutes(1)
}

/** "Anand ka tatkal booking hai yaad dila dena" without a time and not about Tatkal: ask when. */
fun isReminderWithoutTime(text: String): Boolean {
    val t = text.trim()
    return t.length <= 160 && REMIND.containsMatchIn(t) && !CLOCK.containsMatchIn(t) && !TATKAL.containsMatchIn(t) &&
        !Regex("kal|aaj|parso|subah|shaam|raat|dopahar|कल|आज|परसों|सुबह|शाम|रात|दोपहर|batao|dikhao|hatao|बताओ|दिखाओ|हटाओ", RegexOption.IGNORE_CASE).containsMatchIn(t)
}
