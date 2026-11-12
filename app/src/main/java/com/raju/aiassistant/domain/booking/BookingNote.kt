package com.raju.aiassistant.domain.booking

import com.raju.aiassistant.domain.rail.railClass
import com.raju.aiassistant.domain.tatkal.Passenger
import java.time.LocalDate

/**
 * "Booking note": Seema asks, step by step, for what the user has just been booked and writes a clear
 * note of it. Everything is said in one or many sentences: "Pune se Howrah AC kal, Rajesh 1986" fills
 * the route, class, date and first passenger at once; each missing piece is asked for.
 *
 * It is a note only. Seema never books a ticket and never guesses: "AC" alone asks 3A or 2A, a
 * passenger's age is this year minus the birth year the user said, and nothing is written that
 * was not said or read from the user's own ID photo.
 */
data class BookingDraft(
    val from: String? = null,
    val to: String? = null,
    val travelClass: String? = null,
    /** The user said just "AC": 3A or 2A still to be chosen. */
    val classAmbiguous: Boolean = false,
    val date: LocalDate? = null,
    val passengers: List<Passenger> = emptyList(),
    /** A name was said and its birth year is still to come. */
    val pendingName: String? = null,
    /** All passengers so far are in; asking whether there is one more. */
    val askingMore: Boolean = false,
)

sealed interface BookingStep {
    /** Not finished: [prompt] is said and shown; [draft] is kept for the next answer. */
    data class Ask(val draft: BookingDraft, val prompt: String) : BookingStep
    /** Finished: [note] is the text to show and save; [spoken] is what Seema says about it. */
    data class Done(val draft: BookingDraft, val note: String, val spoken: String) : BookingStep
    data object Cancelled : BookingStep
}

object BookingNote {
    private val START = Regex("(?:^|\\s)(?:booking|बुकिंग)\\s+(?:ka\\s+|का\\s+)?(?:note|नोट|aaya|aya|aayi|aai|आया|आई|आयी)(?=\\s|$)", RegexOption.IGNORE_CASE)
    private val CANCEL = Regex("(?:^|\\s)(?:cancel|रद्द|rehne do|रहने दो|chhodo|छोड़ो|chhod do|छोड़ दो|band karo|बंद करो)(?=\\s|$)", RegexOption.IGNORE_CASE)
    private val FINISH = Regex("(?:^|\\s)(?:bana do|banao|bana do|bana dijiye|ban gaya|bas|बस|बनाओ|बना दो|बना दीजिए|हो गया|ho gaya|done|bas itna|बस इतना|ok bas)(?=\\s|$)", RegexOption.IGNORE_CASE)
    private val YES = Regex("(?:^|\\s)(?:haan|han|ha|yes|ji|हाँ|हां|जी|aur|और|ek aur|एक और|ok)(?=\\s|$)", RegexOption.IGNORE_CASE)
    private val NO = Regex("(?:^|\\s)(?:nahi|nahin|nai|no|नहीं|नही|nope)(?=\\s|$)", RegexOption.IGNORE_CASE)

    /** "booking note", "booking ka note", "booking aaya", "बुकिंग नोट", "बुकिंग आया" — start a new note. */
    fun isStart(text: String): Boolean = START.containsMatchIn(normalize(text))

    private val MONTHS: Map<String, Int> = mapOf(
        "jan" to 1, "january" to 1, "जनवरी" to 1,
        "feb" to 2, "february" to 2, "फरवरी" to 2,
        "mar" to 3, "march" to 3, "मार्च" to 3,
        "apr" to 4, "april" to 4, "अप्रैल" to 4,
        "may" to 5, "मई" to 5,
        "jun" to 6, "june" to 6, "जून" to 6,
        "jul" to 7, "july" to 7, "जुलाई" to 7,
        "aug" to 8, "august" to 8, "अगस्त" to 8,
        "sep" to 9, "sept" to 9, "september" to 9, "सितंबर" to 9, "सितम्बर" to 9,
        "oct" to 10, "october" to 10, "अक्टूबर" to 10, "अक्तूबर" to 10,
        "nov" to 11, "november" to 11, "नवंबर" to 11, "नवम्बर" to 11,
        "dec" to 12, "december" to 12, "दिसंबर" to 12, "दिसम्बर" to 12,
    )
    private val SE = setOf("se", "से")
    private val CLASS_AC = setOf("ac", "एसी")
    private val RELATIVE = mapOf(
        "aaj" to 0L, "aj" to 0L, "आज" to 0L,
        "kal" to 1L, "कल" to 1L,
        "parso" to 2L, "parson" to 2L, "परसों" to 2L, "परसो" to 2L,
    )
    private val SMALL_WORDS = setOf(
        "ko", "को", "ki", "की", "ke", "के", "ka", "का", "tak", "तक", "aur", "और", "ek", "एक", "do", "दो", "teen", "तीन",
        "passenger", "passengers", "yatri", "yatriyon", "यात्री", "यात्रियों", "ticket", "booking", "note", "नोट",
        "bana", "banao", "बनाओ", "बना", "hai", "है", "ho", "हो", "gaya", "गया", "karo", "करो", "dedo", "de", "do",
        "haan", "han", "हाँ", "हां", "nahi", "नहीं", "ji", "जी", "ok", "theek", "thik", "ठीक", "bas", "बस",
        "ac", "एसी", "sleeper", "slp", "sl", "स्लीपर", "chair", "cc", "ec", "का", "में", "mein", "me", "wala", "wali",
        "kaun", "kon", "si", "सी", "konsi", "kaunsi", "कौन", "class", "क्लास",
        "ya", "या", "pe", "पे", "par", "पर", "se", "से", "ki", "to", "तो",
    )

    fun normalize(text: String): String = text.map { ch ->
        when (ch) {
            in '०'..'९' -> '0' + (ch - '०')
            in '০'..'৯' -> '0' + (ch - '০')
            else -> ch
        }
    }.joinToString("").lowercase()

    /** The words as said, with their capitals (names keep them); the same count as [normalize]d words. */
    private fun wordsAsSaid(text: String): List<String> =
        text.replace(Regex("[,.?!।;:()\\[\\]\"']"), " ").split(Regex("\\s+")).filter { it.isNotBlank() }

    private fun words(text: String): List<String> = wordsAsSaid(text).map { normalize(it) }

    private fun isNameWord(w: String): Boolean =
        w.any { it.isLetter() } && w.none { it.isDigit() } && w !in SMALL_WORDS && w !in SE && w !in RELATIVE && w !in CLASS_AC &&
            w !in MONTHS && classOf(w) == null && !w.contains('/') && !w.contains('-')

    /** "3A", "2A", "SL" … when the word is a class; "AC" alone is ambiguous and returns null. */
    private fun classOf(w: String): String? {
        if (w in CLASS_AC) return null
        return railClass(w)
    }

    private fun ageOf(birthYear: Int, today: LocalDate): Int = today.year - birthYear

    /** A year said in the passenger step: "1986", or the year of a full date "15/08/1986". */
    private fun birthYearIn(w: String, today: LocalDate): Int? {
        Regex("^(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4})$").find(w)?.let { return it.groupValues[3].toIntOrNull()?.takeIf { y -> y in 1900..today.year } }
        return w.toIntOrNull()?.takeIf { it.toString() == w && w.length == 4 && it in 1900..today.year }
    }

    /** "12 november", "12 नवंबर", "12-11", "12/11/2026" → the next such day (this year, or next if passed). */
    private fun explicitDate(list: List<String>, today: LocalDate, allowFullDate: Boolean): LocalDate? {
        for (i in list.indices) {
            Regex("^(\\d{1,2})[/.-](\\d{1,2})(?:[/.-](\\d{4}))?$").find(list[i])?.let { m ->
                // Once the trip date is set, a full date in the passenger step is a birth date, not a new trip date.
                if (!allowFullDate && m.groupValues[3].isNotEmpty()) return@let
                val d = m.groupValues[1].toInt()
                val mo = m.groupValues[2].toInt()
                val y = m.groupValues[3].toIntOrNull()
                return dateOn(d, mo, y, today)
            }
            if (list[i].all(Char::isDigit) && list[i].length <= 2 && i + 1 < list.size) {
                MONTHS[list[i + 1]]?.let { mo -> return dateOn(list[i].toInt(), mo, null, today) }
            }
        }
        return null
    }

    private fun dateOn(day: Int, month: Int, year: Int?, today: LocalDate): LocalDate? {
        val first = runCatching { LocalDate.of(year ?: today.year, month, day) }.getOrNull() ?: return null
        if (year != null) return first
        return if (first.isBefore(today)) first.plusYears(1) else first
    }

    private fun relativeDate(list: List<String>, today: LocalDate): LocalDate? =
        list.firstNotNullOfOrNull { w -> RELATIVE[w]?.let { today.plusDays(it) } }

    /** One step of the note: reads what was said, fills what it can, and asks for the next missing piece. */
    fun step(draft: BookingDraft, text: String, today: LocalDate): BookingStep {
        val n = normalize(text)
        if (CANCEL.containsMatchIn(n)) return BookingStep.Cancelled
        val list = words(text)
        val orig = wordsAsSaid(text)
        var d = draft

        // 1. Route: "Pune se Howrah" fills both; a lone word fills the missing end.
        val se = list.indexOfFirst { it in SE }
        if (se >= 1) {
            val fromWords = orig.subList(0, se).filter { isNameWord(normalize(it)) }
            val toWords = orig.drop(se + 1).takeWhile { val w = normalize(it); w !in SMALL_WORDS && classOf(w) == null && relativeDate(listOf(w), today) == null && w !in CLASS_AC }
                .filter { isNameWord(normalize(it)) }
            if (fromWords.isNotEmpty()) d = d.copy(from = fromWords.joinToString(" "))
            if (toWords.isNotEmpty()) d = d.copy(to = toWords.joinToString(" "))
        }
        val nameWords = orig.filter { isNameWord(normalize(it)) }
        val routeDone = d.from != null && d.to != null
        if (se < 0 && nameWords.isNotEmpty() && !routeDone && d.passengers.isEmpty()) {
            if (d.from == null) d = d.copy(from = nameWords.joinToString(" "))
            else if (d.to == null) d = d.copy(to = nameWords.joinToString(" "))
        }

        // 2. Class: a specific one, or "AC" which asks 3A or 2A.
        val specific = list.firstNotNullOfOrNull { classOf(it) }
        if (specific != null) d = d.copy(travelClass = specific, classAmbiguous = false)
        else if (list.any { it in CLASS_AC } && d.travelClass == null) d = d.copy(classAmbiguous = true)
        if (d.classAmbiguous && d.travelClass == null) {
            when {
                list.any { it == "3" || it == "teen" || it == "तीन" || it == "3rd" } -> d = d.copy(travelClass = "3A", classAmbiguous = false)
                list.any { it == "2" || it == "do" || it == "दो" || it == "2nd" } -> d = d.copy(travelClass = "2A", classAmbiguous = false)
            }
        }

        // 3. Date: "aaj", "kal", "parso", "12 november", "12/11".
        val date = relativeDate(list, today) ?: explicitDate(list, today, allowFullDate = draft.date == null)
        if (date != null) d = d.copy(date = date)

        // 4. Passengers, once the route, class and date are known.
        val complete = d.from != null && d.to != null && d.travelClass != null && d.date != null
        var passengerSaid = false
        if (complete) {
            val year = if (date == null) list.firstNotNullOfOrNull { birthYearIn(it, today) } else null
            val name = nameWords.joinToString(" ").takeIf { it.isNotBlank() }
            val finishing = FINISH.containsMatchIn(n)
            if (d.askingMore) {
                when {
                    name != null -> d = d.copy(askingMore = false)
                    finishing || (NO.containsMatchIn(n) && !YES.containsMatchIn(n)) -> return finish(d, today)
                    YES.containsMatchIn(n) -> d = d.copy(askingMore = false)
                }
            } else if (finishing && d.passengers.isNotEmpty() && name == null && year == null) {
                return finish(d, today)
            }
            if (!d.askingMore) {
                if (name != null && year != null) {
                    d = d.copy(passengers = d.passengers + Passenger(name, year, ageOf(year, today), null), pendingName = null, askingMore = true)
                    passengerSaid = true
                } else if (name != null) {
                    d = d.copy(pendingName = name)
                    passengerSaid = true
                } else if (year != null && d.pendingName != null) {
                    d = d.copy(passengers = d.passengers + Passenger(d.pendingName!!, year, ageOf(year, today), null), pendingName = null, askingMore = true)
                    passengerSaid = true
                } else if (year != null) {
                    return BookingStep.Ask(d, understood(d) + "किसका जन्म का साल है? पहले नाम बताइए।")
                }
            }
        }

        // 5. What is still missing, or the next question.
        val echo = understood(d)
        return when {
            d.from == null -> BookingStep.Ask(d, "कहाँ से? जैसे पुणे।")
            d.to == null -> BookingStep.Ask(d, echo + "कहाँ तक जाना है?")
            d.travelClass == null && d.classAmbiguous -> BookingStep.Ask(d, echo + "AC में 3A या 2A? बोलिए।")
            d.travelClass == null -> BookingStep.Ask(d, echo + "कौन सी class? जैसे 3A, 2A, SL।")
            d.date == null -> BookingStep.Ask(d, echo + "कब की यात्रा है? जैसे कल, या 12 नवंबर।")
            d.askingMore -> BookingStep.Ask(d, echo + "और कोई यात्री है? नाम बताइए, या \"बस\" बोलिए।")
            d.pendingName != null -> BookingStep.Ask(d, echo + "${d.pendingName} का जन्म का साल बताइए, जैसे 1986।")
            d.passengers.isEmpty() -> BookingStep.Ask(d, echo + "यात्री का नाम बताइए।")
            passengerSaid -> BookingStep.Ask(d, echo + "और कोई यात्री है? नाम बताइए, या \"बस\" बोलिए।")
            else -> finish(d, today)
        }
    }

    /** "पुणे से हावड़ा, 3A, कल। " — what has been understood so far, said back in one line. */
    private fun understood(d: BookingDraft): String {
        val parts = mutableListOf<String>()
        if (d.from != null && d.to != null) parts += "${d.from} से ${d.to}"
        else d.from?.let { parts += "$it से" }
        d.travelClass?.let { parts += it }
        d.date?.let { parts += dayWord(it) }
        return if (parts.isEmpty()) "" else parts.joinToString(", ") + "। "
    }

    private fun finish(d: BookingDraft, today: LocalDate): BookingStep {
        if (d.from == null || d.to == null || d.travelClass == null || d.date == null || d.passengers.isEmpty()) {
            return BookingStep.Ask(d, understood(d) + "यात्री का नाम और जन्म का साल बताइए।")
        }
        val note = note(d, today)
        val spoken = buildString {
            append("बुकिंग नोट बन गया। ")
            append("${d.from} से ${d.to}, ${d.travelClass}, ${dayWord(d.date)}। ")
            append("यात्री: ")
            append(d.passengers.joinToString("; ") { "${it.name}, ${it.age} साल" })
            append("। नोट स्क्रीन पर है, और सेव या शेयर के लिए फ़ाइल का पेज खुला है।")
        }
        return BookingStep.Done(d, note, spoken)
    }

    private val CLASS_NAMES = mapOf(
        "1A" to "AC प्रथम (1A)", "2A" to "AC 2 टियर (2A)", "3A" to "AC 3 टियर (3A)", "3E" to "AC इकॉनमी 3 टियर (3E)",
        "SL" to "स्लीपर (SL)", "2S" to "सेकंड सिटिंग (2S)", "CC" to "चेयर कार (CC)", "EC" to "एग्ज़ीक्यूटिव चेयर (EC)",
    )

    private val WEEKDAYS = listOf("सोमवार", "मंगलवार", "बुधवार", "गुरुवार", "शुक्रवार", "शनिवार", "रविवार")
    private val MONTH_NAMES = listOf("जनवरी", "फरवरी", "मार्च", "अप्रैल", "मई", "जून", "जुलाई", "अगस्त", "सितंबर", "अक्टूबर", "नवंबर", "दिसंबर")

    private fun dayWord(date: LocalDate?): String {
        if (date == null) return ""
        return "${date.dayOfMonth} ${MONTH_NAMES[date.monthValue - 1]} ${date.year} (${WEEKDAYS[date.dayOfWeek.value - 1]})"
    }

    /** The note itself: plain text, readable on the screen and in any text app. */
    fun note(d: BookingDraft, today: LocalDate): String = buildString {
        appendLine("बुकिंग नोट")
        appendLine("बनाया गया: ${dayWord(today)}")
        appendLine("-------------------------------")
        appendLine("रूट: ${d.from} → ${d.to}")
        appendLine("Class: ${CLASS_NAMES[d.travelClass] ?: d.travelClass}")
        appendLine("यात्रा की तारीख: ${dayWord(d.date)}")
        appendLine()
        appendLine("यात्री:")
        d.passengers.forEachIndexed { i, p ->
            appendLine("${i + 1}. ${p.name} — उम्र ${p.age} साल (जन्म ${p.birthYear})")
        }
        appendLine()
        append("यह सिर्फ़ आपकी याद के लिए नोट है। टिकट और PNR की पुष्टि रेलवे की साइट पर अलग से जाँचें।")
    }

    /** A file name that says what the note is: "booking-note-2026-11-12.txt". */
    fun fileName(d: BookingDraft): String {
        val day = d.date?.toString() ?: "undated"
        return "booking-note-$day.txt"
    }
}
