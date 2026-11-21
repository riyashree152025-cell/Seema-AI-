package com.raju.aiassistant.domain.memory

import java.time.LocalDate
import java.time.MonthDay

/**
 * "Rahul ka birthday 5 March ko hai", "meri shaadi ki salgirah 12 June", "মিঠুর জন্মদিন ৫ মার্চ": kept in Seema's memory as
 * one line ("खास दिन: Rahul का जन्मदिन — 5 March") and said every year in the morning briefing of that day, so he never
 * forgets to wish them.
 */
enum class DayKind(val hindi: String) { BIRTHDAY("जन्मदिन"), ANNIVERSARY("सालगिरह") }

data class SpecialDay(val who: String, val kind: DayKind, val day: MonthDay)

object SpecialDays {
    private val MONTHS = mapOf(
        "jan" to 1, "january" to 1, "जनवरी" to 1, "জানুয়ারি" to 1, "feb" to 2, "february" to 2, "फरवरी" to 2, "फ़रवरी" to 2, "ফেব্রুয়ারি" to 2,
        "mar" to 3, "march" to 3, "मार्च" to 3, "মার্চ" to 3, "apr" to 4, "april" to 4, "अप्रैल" to 4, "এপ্রিল" to 4,
        "may" to 5, "mai" to 5, "मई" to 5, "মে" to 5, "jun" to 6, "june" to 6, "जून" to 6, "জুন" to 6,
        "jul" to 7, "july" to 7, "जुलाई" to 7, "জুলাই" to 7, "aug" to 8, "august" to 8, "अगस्त" to 8, "আগস্ট" to 8,
        "sep" to 9, "sept" to 9, "september" to 9, "सितंबर" to 9, "सितम्बर" to 9, "সেপ্টেম্বর" to 9,
        "oct" to 10, "october" to 10, "अक्टूबर" to 10, "অক্টোবর" to 10, "nov" to 11, "november" to 11, "नवंबर" to 11, "नवम्बर" to 11, "নভেম্বর" to 11,
        "dec" to 12, "december" to 12, "दिसंबर" to 12, "दिसम्बर" to 12, "ডিসেম্বর" to 12,
    )
    private val NAMES_OF_MONTHS = listOf("", "January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    private const val MONTH = "([\\p{L}\\p{M}]+)"
    private val BIRTHDAY = "(?:birthday|bday|janamdin|janmdin|जन्मदिन|बर्थडे|জন্মদিন|বার্থডে)"
    private val ANNIV = "(?:anniversary|salgirah|saalgirah|shaadi ki salgirah|सालगिरह|शादी की सालगिरह|एनिवर्सरी|বিবাহবার্ষিকী|অ্যানিভার্সারি)"

    // "Rahul ka birthday 5 March ko hai", "मेरी शादी की सालगिरह 12 जून को है", "মিঠুর জন্মদিন ৫ মার্চ"
    private val SAID = Regex(
        "^(.{1,30}?)\\s*(?:ka|ki|ke|का|की|के|র|ের|এর)?\\s+($BIRTHDAY|$ANNIV)\\s+(\\d{1,2})\\s*(?:tarikh|तारीख|তারিখ)?\\s*$MONTH(?:\\s+(?:ko|को|এ|তে))?(?:\\s+(?:hai|है|আছে))?$",
        RegexOption.IGNORE_CASE,
    )
    private val FACT = Regex("^खास दिन: (.+) का (जन्मदिन|सालगिरह) — (\\d{1,2}) ([A-Za-z]+)$")

    private fun digits(s: String) = s.map { c -> if (c in '०'..'९') '0' + (c - '०') else if (c in '০'..'৯') '0' + (c - '০') else c }.joinToString("")

    fun parse(said: String): SpecialDay? {
        val t = digits(said.trim().trimEnd('.', '!', '।', ' ')).replace(Regex("\\s+"), " ")
        val m = SAID.matchEntire(t) ?: return null
        val month = MONTHS[m.groupValues[4].lowercase()] ?: return null
        val day = m.groupValues[3].toInt()
        if (day !in 1..31 || runCatching { MonthDay.of(month, day) }.isFailure) return null
        val raw = m.groupValues[1].trim()
        val who = when (raw.lowercase()) {
            "mera", "meri", "mere", "मेरा", "मेरी", "मेरे", "আমার" -> "Raju"
            "meri shaadi", "मेरी शादी", "shaadi", "शादी" -> "Raju"
            else -> raw.removeSuffix("র").removeSuffix("ের").removePrefix("meri ").removePrefix("mere ").removePrefix("मेरी ").removePrefix("मेरे ").trim()
        }
        val kind = if (Regex(ANNIV, RegexOption.IGNORE_CASE).matches(m.groupValues[2])) DayKind.ANNIVERSARY else DayKind.BIRTHDAY
        return SpecialDay(who.ifBlank { "Raju" }, kind, MonthDay.of(month, day))
    }

    fun factText(d: SpecialDay): String = "खास दिन: ${d.who} का ${d.kind.hindi} — ${d.day.dayOfMonth} ${NAMES_OF_MONTHS[d.day.monthValue]}"

    fun fromFact(text: String): SpecialDay? {
        val m = FACT.matchEntire(text.trim()) ?: return null
        val month = NAMES_OF_MONTHS.indexOf(m.groupValues[4]).takeIf { it > 0 } ?: return null
        val kind = if (m.groupValues[2] == "सालगिरह") DayKind.ANNIVERSARY else DayKind.BIRTHDAY
        return runCatching { SpecialDay(m.groupValues[1], kind, MonthDay.of(month, m.groupValues[3].toInt())) }.getOrNull()
    }

    /** Today's special days from the saved facts, as one line for the morning, or null. */
    fun todayLine(facts: List<String>, today: LocalDate): String? {
        val days = facts.mapNotNull { fromFact(it) }.filter { it.day == MonthDay.from(today) }
        if (days.isEmpty()) return null
        return days.joinToString(" ") { d ->
            if (d.who == "Raju") "आज आपका ${d.kind.hindi} है — बहुत-बहुत मुबारक हो!" else "आज ${d.who} का ${d.kind.hindi} है — बधाई देना मत भूलना।"
        }
    }

    /** "ठीक है, 5 March को Rahul का जन्मदिन याद रखूँगी।" */
    fun confirmation(d: SpecialDay): String =
        if (d.who == "Raju") "ठीक है, ${d.day.dayOfMonth} ${NAMES_OF_MONTHS[d.day.monthValue]} को आपका ${d.kind.hindi} याद रखूँगी।"
        else "ठीक है, ${d.day.dayOfMonth} ${NAMES_OF_MONTHS[d.day.monthValue]} को ${d.who} का ${d.kind.hindi} याद रखूँगी — उस दिन सुबह बता दूँगी।"
}
