package com.raju.aiassistant.domain.tatkal

import java.time.LocalDate

/** One passenger for the ticket form. [gender] is only what was said or read — never guessed from the name. */
data class Passenger(val name: String, val birthYear: Int, val age: Int, val gender: String?)

private val SEPARATOR = Regex("\\s*(?:,|;|&|\\n|\\s(?:aur|and|tatha|और|तथा|एवं|আর|এবং)\\s)\\s*", RegexOption.IGNORE_CASE)

// Words that ask for the sum; their presence also tells one "name year" apart from other sentences.
private val ASK = Regex(
    "(?:^|\\s)(?:umar|umr|umra|age|ages|उम्र|उमर|आयु|বয়স|passenger|passengers|यात्री|पैसेंजर)(?:\\s(?:nikalo|nikal do|nikaalo|batao|bata do|calculate karo|kya hai|kitni hai|निकालो|निकाल दो|बताओ|बता दो|क्या है|कितनी है))?(?=\\s|$)|" +
        "(?:^|\\s)(?:ki|ka|ke|की|का|के)(?=\\s*$)",
    RegexOption.IGNORE_CASE,
)
private val MALE = Regex("(?:^|\\s)(?:male|m|purush|ladka|aadmi|mard|पुरुष|लड़का|आदमी|मेल)(?=\\s|$)", RegexOption.IGNORE_CASE)
private val FEMALE = Regex("(?:^|\\s)(?:female|f|mahila|ladki|aurat|stree|महिला|लड़की|औरत|स्त्री|फीमेल)(?=\\s|$)", RegexOption.IGNORE_CASE)
private val YEAR_FILLER = Regex(
    "(?:^|\\s)(?:ka|ki|ke|का|की|के|janm|janam|born|birth|year|saal|sal|san|sun|mein|me|wala|wali|dob|जन्म|साल|सन|सन्|में|वाला|वाली|:|-)(?=\\s|$)",
    RegexOption.IGNORE_CASE,
)
private val NOT_A_NAME = Regex("(?:^|\\s)(?:ko|se|ne|par|pe|diye|diya|mile|liye|hai|tha|kya|को|से|ने|पर|दिए|दिया|मिले|लिए|है|था|क्या)(?=\\s|$)", RegexOption.IGNORE_CASE)
private val FULL_DATE = Regex("(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4})")
private val YEAR = Regex("(?<!\\d)(\\d{4})(?!\\d)")

private fun asciiDigits(s: String): String = s.map { ch ->
    when (ch) {
        in '०'..'९' -> '0' + (ch - '०')
        in '০'..'৯' -> '0' + (ch - '০')
        else -> ch
    }
}.joinToString("")

/**
 * "राजेश सिंह 1986 और राकेश 2002" → each name with the age today's year gives (2026 − 1986 = 40).
 * With a full date of birth ("15/08/1986") the age is the completed years on [today].
 * Null unless the whole sentence is passengers (two or more, or one with a word like "umar"/"age"),
 * so an amount or a year in some other sentence never becomes an age.
 */
fun parsePassengers(text: String, today: LocalDate): List<Passenger>? {
    var t = asciiDigits(text).trim().trimEnd('.', '।', '?', '!')
    if (t.isEmpty() || t.length > 400) return null
    val asked = ASK.containsMatchIn(t)
    t = ASK.replace(t, " ").trim()
    val pieces = t.split(SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }
    if (pieces.isEmpty() || pieces.size > 8) return null
    val out = pieces.map { piece ->
        var p = piece
        val gender = when {
            FEMALE.containsMatchIn(p) -> "Female"
            MALE.containsMatchIn(p) -> "Male"
            else -> null
        }
        p = MALE.replace(FEMALE.replace(p, " "), " ")
        val full = FULL_DATE.find(p)
        val birth: LocalDate?
        val year: Int
        val at: IntRange
        if (full != null) {
            birth = runCatching { LocalDate.of(full.groupValues[3].toInt(), full.groupValues[2].toInt(), full.groupValues[1].toInt()) }.getOrNull() ?: return null
            year = birth.year
            at = full.range
        } else {
            val y = YEAR.findAll(p).toList().singleOrNull() ?: return null
            birth = null
            year = y.groupValues[1].toInt()
            at = y.range
        }
        if (year < 1900 || year > today.year) return null
        fun clean(part: String) = YEAR_FILLER.replace(part, " ").replace(Regex("\\s+"), " ").trim()
        // The name comes first, then the year; words after the year ("… ko 2000 diye") make it another sentence.
        if (clean(p.substring(at.last + 1)).isNotEmpty()) return null
        val name = clean(p.substring(0, at.first))
        if (name.isEmpty() || name.any(Char::isDigit) || name.split(' ').size > 4 || NOT_A_NAME.containsMatchIn(name)) return null
        val age = if (birth != null) java.time.Period.between(birth, today).years else today.year - year
        if (age < 0) return null
        Passenger(name, year, age, gender)
    }
    return out.takeIf { it.size >= 2 || asked }
}

/** One line per passenger for the screen: "Rajesh Singh | 40 | Male". */
fun writtenPassengers(list: List<Passenger>): String =
    list.joinToString("\n") { "${it.name} | ${it.age} | ${it.gender ?: "—"}" }

/** Read out: every name with its age, and the sum it came from when there are few. */
fun spokenPassengers(list: List<Passenger>, currentYear: Int): String =
    list.joinToString("; ") { "${it.name}, ${it.age} साल" } +
        (if (list.size <= 3 && list.all { currentYear - it.birthYear == it.age }) "। (${list.joinToString(", ") { "$currentYear − ${it.birthYear} = ${it.age}" }})" else "।") +
        " कार्ड स्क्रीन पर है — कॉपी बटन दबाकर फ़ॉर्म में पेस्ट कर लीजिए।"

/** Passengers read from an ID photo by the vision model, one a line: "NAME | 1986 | M". Bad lines are dropped. */
fun parsePassengerLines(text: String, today: LocalDate): List<Passenger> = text.lineSequence().mapNotNull { line ->
    val parts = asciiDigits(line).split('|').map { it.trim() }
    if (parts.size < 2) return@mapNotNull null
    val name = parts[0].trim('*', '-', ' ').takeIf { it.isNotEmpty() && it.none(Char::isDigit) && it.length <= 60 } ?: return@mapNotNull null
    val full = FULL_DATE.find(parts[1])
    val birth = full?.let { runCatching { LocalDate.of(it.groupValues[3].toInt(), it.groupValues[2].toInt(), it.groupValues[1].toInt()) }.getOrNull() }
    val year = birth?.year ?: YEAR.find(parts[1])?.groupValues?.get(1)?.toInt() ?: return@mapNotNull null
    if (year < 1900 || year > today.year) return@mapNotNull null
    val gender = when (parts.getOrNull(2)?.uppercase()?.take(1)) {
        "M", "पु" -> "Male"
        "F", "म" -> "Female"
        else -> when {
            parts.getOrNull(2)?.contains("पुरुष") == true -> "Male"
            parts.getOrNull(2)?.contains("महिला") == true -> "Female"
            else -> null
        }
    }
    Passenger(name, year, if (birth != null) java.time.Period.between(birth, today).years else today.year - year, gender)
}.toList()
