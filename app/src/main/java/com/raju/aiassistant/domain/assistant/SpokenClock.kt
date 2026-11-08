package com.raju.aiassistant.domain.assistant

import java.time.LocalDate

/**
 * The time and date said the way people say them, for the fast path ("kitne baje hain",
 * "aaj kaun sa din hai"): answered on the phone at once, without an AI request.
 */
object SpokenClock {
    private val HI_DAYS = listOf("सोमवार", "मंगलवार", "बुधवार", "गुरुवार", "शुक्रवार", "शनिवार", "रविवार")
    private val BN_DAYS = listOf("সোমবার", "মঙ্গলবার", "বুধবার", "বৃহস্পতিবার", "শুক্রবার", "শনিবার", "রবিবার")
    private val HI_MONTHS = listOf("जनवरी", "फ़रवरी", "मार्च", "अप्रैल", "मई", "जून", "जुलाई", "अगस्त", "सितंबर", "अक्टूबर", "नवंबर", "दिसंबर")
    private val BN_MONTHS = listOf("জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন", "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর")

    /** "सुबह के 9 बजकर 5 मिनट हुए हैं।" / "এখন সকাল 9টা 5 মিনিট।" ([hour] 0-23). */
    fun time(hour: Int, minute: Int, bengali: Boolean = false): String {
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        return if (bengali) {
            val part = when (hour) { in 4..11 -> "সকাল"; in 12..15 -> "দুপুর"; in 16..18 -> "বিকেল"; else -> "রাত" }
            if (minute == 0) "এখন $part ${h12}টা।" else "এখন $part ${h12}টা $minute মিনিট।"
        } else {
            val part = when (hour) { in 4..11 -> "सुबह"; in 12..15 -> "दोपहर"; in 16..18 -> "शाम"; else -> "रात" }
            if (minute == 0) "अभी $part के $h12 बजे हैं।" else "अभी $part के $h12 बजकर $minute मिनट हुए हैं।"
        }
    }

    /** "आज रविवार, 8 नवंबर 2026 है।" / "আজ রবিবার, 8 নভেম্বর 2026।" */
    fun date(day: LocalDate, bengali: Boolean = false): String {
        val i = day.dayOfWeek.value - 1
        val m = day.monthValue - 1
        return if (bengali) "আজ ${BN_DAYS[i]}, ${day.dayOfMonth} ${BN_MONTHS[m]} ${day.year}।"
        else "आज ${HI_DAYS[i]}, ${day.dayOfMonth} ${HI_MONTHS[m]} ${day.year} है।"
    }
}
