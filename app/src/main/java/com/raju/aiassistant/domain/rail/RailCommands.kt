package com.raju.aiassistant.domain.rail

import java.time.LocalDate

/** A railway question understood on the phone, without the AI (so nothing about it can be made up). */
sealed interface RailCommand {
    /** "kal Katihar se Delhi ki train": [date] null = no day was said. */
    data class Between(val from: String, val to: String, val date: LocalDate?) : RailCommand
    /** "train 12345 kahan hai". */
    data class Live(val train: String) : RailCommand
}

private val DAY = Regex("(?:^|\\s)(aaj|aj|kal|parso|parson|आज|कल|परसों|परसो)(?=\\s|$)", RegexOption.IGNORE_CASE)

private const val TRAIN_WORD = "(?:train|trains|trainen|traine|gaadi|gadi|gaadiyan|ट्रेन|ट्रेनें|ट्रेने|गाड़ी|गाड़ियाँ|गाड़ियां|রেল|ট্রেন)"

private val BETWEEN = Regex(
    "^(.+?) (?:se|say|से) (.+?)(?: (?:ki|ke liye|ka|ko|tak|jane wali|jaane wali|jane vali|की|के लिए|का|को|तक|जाने वाली))?" +
        "(?: (?:kaun si|kaunsi|kaun kaun si|kon si|konsi|koi|sari|saari|कौन सी|कौनसी|कौन कौन सी|कोई|सारी))?" +
        " $TRAIN_WORD(?: (?:hai|hain|he|hai kya|hain kya|batao|bata do|bataiye|dikhao|chahiye|milegi|chalti hai|jati hai|है|हैं|है क्या|हैं क्या|बताओ|बता दो|बताइए|दिखाओ|चाहिए|मिलेगी|चलती है|जाती है)){0,2}$",
    RegexOption.IGNORE_CASE,
)

private val LIVE = Regex(
    "^(?:$TRAIN_WORD )?(?:number |nambar |नंबर )?(\\d{5})(?: (?:number|nambar|नंबर))?(?: $TRAIN_WORD)?" +
        "(?: (?:abhi|अभी))? (?:kahan hai|kaha hai|kahan pahunchi|kahan tak pahunchi|kahan tak aayi|ka live status|live status|running status|ka running status|" +
        "kitni late hai|late hai kya|कहाँ है|कहां है|कहाँ पहुँची|कहां पहुंची|कहाँ तक पहुँची|का लाइव स्टेटस|लाइव स्टेटस|कितनी लेट है|लेट है क्या)(?: .{0,20})?$",
    RegexOption.IGNORE_CASE,
)

/** In a travel question "kal" is tomorrow (trains of yesterday are not asked for). */
private fun dayOf(word: String, today: LocalDate): LocalDate = when (word.lowercase()) {
    "aaj", "aj", "आज" -> today
    "kal", "कल" -> today.plusDays(1)
    else -> today.plusDays(2)
}

fun parseRailCommand(text: String, today: LocalDate): RailCommand? {
    var t = text.trim().trimEnd('?', '.', '।', '!').replace(Regex("\\s+"), " ")
    if (t.isEmpty() || t.length > 90) return null
    LIVE.matchEntire(t.replace(Regex("(?<=\\d) (?=\\d)"), ""))?.let { return RailCommand.Live(it.groupValues[1]) }
    val day = DAY.find(t)
    if (day != null) t = t.removeRange(day.range).replace(Regex("\\s+"), " ").trim()
    val m = BETWEEN.matchEntire(t) ?: return null
    val from = m.groupValues[1].trim()
    val to = m.groupValues[2].trim()
    // Station names are short; a long "from" is some other sentence ("rahul se bolo ki … train …").
    if (from.split(' ').size > 3 || to.split(' ').size > 3 || from.any(Char::isDigit) || to.any(Char::isDigit)) return null
    return RailCommand.Between(from, to, day?.let { dayOf(it.groupValues[1], today) })
}
